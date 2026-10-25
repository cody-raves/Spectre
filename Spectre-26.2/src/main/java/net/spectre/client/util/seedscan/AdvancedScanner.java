package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

@Environment(EnvType.CLIENT)
public final class AdvancedScanner implements AutoCloseable {
   public static final int MAX_RADIUS = 8;
   public static final int WORLD_LIMIT = 250000;
   private static final int SETTLE_TICKS = 20;
   private static final int SEED_CHECK_AFTER = 40;
   private static final double SEED_CHECK_FAIL = 0.5;
   private static final int HIDDEN_AFTER = 10;
   private static final int GROUND_CHECK_AFTER = 20;
   private final long seed;
   private final Thread worker;
   private volatile boolean closed;
   private volatile boolean stopped;
   private final ConcurrentLinkedQueue<Object> inbox = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<Object> outbox = new ConcurrentLinkedQueue<>();
   private final Long2ObjectOpenHashMap<List<BlobJudge.Cluster>> clusters = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<AdvancedScanner.ChunkResult> results = new Long2ObjectOpenHashMap();
   private final LongOpenHashSet shownSent = new LongOpenHashSet();
   private final LongArrayFIFOQueue toJudge = new LongArrayFIFOQueue();
   private final LongOpenHashSet judgeQueued = new LongOpenHashSet();
   private final Long2LongOpenHashMap settling = new Long2LongOpenHashMap();
   private final LongOpenHashSet counted = new LongOpenHashSet();
   private final List<String> notices = new ArrayList<>();
   private LongOpenHashSet area = new LongOpenHashSet();
   private int areaX = Integer.MIN_VALUE;
   private int areaZ = Integer.MIN_VALUE;
   private int areaRadius = -1;
   private boolean areaStale;
   private long ticks;
   private int trusted;
   private int mispredicted;
   private int hidden;
   private boolean hashChecked;
   private boolean seedWarned;
   private boolean hiddenWarned;
   private boolean distrusted;
   private boolean wrongHeight;
   private boolean released;

   private AdvancedScanner(long seed) {
      this.seed = seed;
      this.worker = new Thread(this::work, "Spectre Advanced Scanner");
      this.worker.setDaemon(true);
      this.worker.setPriority(2);
      this.worker.start();
   }

   public static AdvancedScanner start(long seed) {
      return new AdvancedScanner(seed);
   }

   public long seed() {
      return this.seed;
   }

   @Override
   public void close() {
      this.closed = true;
      LockSupport.unpark(this.worker);
   }

   public static boolean matchesHashedSeed(long seed, long hashedSeed) {
      return BiomeManager.obfuscateSeed(seed) == hashedSeed;
   }

   static OptionalLong hashedSeed(ClientLevel level) {
      try {
         Field found = null;

         for (Field field : BiomeManager.class.getDeclaredFields()) {
            if (field.getType() == long.class && !Modifier.isStatic(field.getModifiers())) {
               if (found != null) {
                  return OptionalLong.empty();
               }

               found = field;
            }
         }

         if (found == null) {
            return OptionalLong.empty();
         } else {
            found.setAccessible(true);
            return OptionalLong.of(found.getLong(level.getBiomeManager()));
         }
      } catch (RuntimeException | ReflectiveOperationException var6) {
         return OptionalLong.empty();
      }
   }

   public List<String> drainNotices() {
      List<String> out = new ArrayList<>(this.notices);
      this.notices.clear();
      return out;
   }

   public List<AdvancedScanner.ChunkResult> tick(ClientLevel level, long deadline) {
      this.ticks++;

      Object message;
      while ((message = this.outbox.poll()) != null) {
         if (message instanceof PredictionArea.ChunkClusters found) {
            if (found.clusters() == null) {
               this.clusters.remove(found.chunk());
            } else {
               this.clusters.put(found.chunk(), found.clusters());
               this.queueJudge(found.chunk());
            }
         } else if (message instanceof String notice) {
            this.notices.add(notice);
         }
      }

      if (this.stopped) {
         this.release();
         return List.of();
      } else if (level.getMinY() == -64 && level.getHeight() == 384) {
         if (!this.hashChecked) {
            this.hashChecked = true;
            OptionalLong hashed = hashedSeed(level);
            if (hashed.isPresent() && !matchesHashedSeed(this.seed, hashed.getAsLong())) {
               this.notices.add("Seed " + this.seed + " does not match this server. Check the seed.");
            }
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            this.updateArea(level, mc.player.chunkPosition(), Math.min(mc.options.renderDistance().get(), 8));
         }

         this.copyArrived(level, deadline);
         List<AdvancedScanner.ChunkResult> judged = new ArrayList<>();
         BlobJudge.Client client = clientView(level);
         int rounds = this.toJudge.size();

         while (rounds-- > 0 && !this.toJudge.isEmpty() && System.nanoTime() < deadline) {
            long key = this.toJudge.dequeueLong();
            this.judgeQueued.remove(key);
            if (this.settling.containsKey(key)) {
               if (this.settling.get(key) > this.ticks) {
                  this.queueJudge(key);
                  continue;
               }

               this.settling.remove(key);
            }

            List<BlobJudge.Cluster> foundx = (List<BlobJudge.Cluster>)this.clusters.get(key);
            if (foundx != null && this.area.contains(key)) {
               AdvancedScanner.ChunkResult result = this.judge(key, foundx, client);
               if (result != null) {
                  this.results.put(key, result);
                  judged.add(result);
               }
            }
         }

         return judged;
      } else {
         if (!this.wrongHeight) {
            this.wrongHeight = true;
            this.notices.add("Advanced Scanner only works with vanilla world height.");
         }

         return List.of();
      }
   }

   private AdvancedScanner.ChunkResult judge(long key, List<BlobJudge.Cluster> found, BlobJudge.Client client) {
      List<BlobJudge.Verdict> verdicts = new ArrayList<>(found.size());

      for (BlobJudge.Cluster cluster : found) {
         BlobJudge.Verdict verdict = BlobJudge.judge(cluster, client, 1);
         verdicts.add(verdict);
         this.count(verdict);
      }

      return this.distrusted
         ? new AdvancedScanner.ChunkResult(key, 0, new long[0], List.of())
         : decide(key, (AdvancedScanner.ChunkResult)this.results.get(key), verdicts);
   }

   static AdvancedScanner.ChunkResult decide(long chunk, AdvancedScanner.ChunkResult before, List<BlobJudge.Verdict> now) {
      Map<Long, BlobJudge.Verdict> last = new HashMap<>();
      if (before != null) {
         for (BlobJudge.Verdict verdict : before.verdicts()) {
            if (verdict.status() != BlobJudge.Status.INCOMPLETE) {
               last.put(clusterKey(verdict.owner(), verdict.kind()), verdict);
            }
         }
      }

      boolean undecided = false;
      List<BlobJudge.Verdict> verdicts = new ArrayList<>(now.size());
      LongArrayList blocks = new LongArrayList();
      LongArrayList anchors = new LongArrayList();

      for (BlobJudge.Verdict verdictx : now) {
         if (verdictx.status() == BlobJudge.Status.INCOMPLETE) {
            BlobJudge.Verdict earlier = last.get(clusterKey(verdictx.owner(), verdictx.kind()));
            if (earlier != null) {
               verdictx = earlier;
            } else {
               undecided = true;
            }
         }

         verdicts.add(verdictx);
         if (verdictx.alert()) {
            anchors.add(verdictx.changes()[0]);
            blocks.addElements(blocks.size(), verdictx.changes());
         }
      }

      if (undecided && anchors.isEmpty()) {
         return null;
      } else {
         long[] sorted = blocks.toLongArray();
         Arrays.sort(sorted);
         int n = 0;

         for (long pos : sorted) {
            if (n == 0 || sorted[n - 1] != pos) {
               sorted[n++] = pos;
            }
         }

         return new AdvancedScanner.ChunkResult(chunk, n, anchors.toLongArray(), List.copyOf(verdicts));
      }
   }

   static void dropOutside(Long2ObjectOpenHashMap<AdvancedScanner.ChunkResult> results, LongOpenHashSet area) {
      ObjectIterator<Entry<AdvancedScanner.ChunkResult>> it = results.long2ObjectEntrySet().fastIterator();

      while (it.hasNext()) {
         Entry<AdvancedScanner.ChunkResult> entry = (Entry<AdvancedScanner.ChunkResult>)it.next();
         if (!area.contains(entry.getLongKey()) && !((AdvancedScanner.ChunkResult)entry.getValue()).flagged()) {
            it.remove();
         }
      }
   }

   private static long clusterKey(long owner, byte kind) {
      return owner * 31L + kind;
   }

   public void blockChanged(BlockPos pos) {
      if (!this.stopped) {
         if (pos.getY() >= -17 && pos.getY() <= 64) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;

            for (int dx = -2; dx <= 2; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  long key = ChunkPos.pack(cx + dx, cz + dz);
                  if (this.clusters.containsKey(key)) {
                     this.settling.put(key, this.ticks + 20L);
                     this.queueJudge(key);
                  }
               }
            }
         }
      }
   }

   public void chunkArrived(int chunkX, int chunkZ) {
      if (!this.stopped) {
         this.areaStale = true;

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               this.queueJudge(ChunkPos.pack(chunkX + dx, chunkZ + dz));
            }
         }
      }
   }

   static boolean insideWorldLimit(int chunkX, int chunkZ) {
      int chunks = 15625;
      return chunkX >= -chunks && chunkX < chunks && chunkZ >= -chunks && chunkZ < chunks;
   }

   private void queueJudge(long key) {
      if (this.judgeQueued.add(key)) {
         this.toJudge.enqueue(key);
      }
   }

   private void updateArea(ClientLevel level, ChunkPos center, int radius) {
      boolean moved = center.x() != this.areaX || center.z() != this.areaZ || radius != this.areaRadius;
      if (moved || this.areaStale && this.ticks % 20L == 0L) {
         this.areaStale = false;
         this.areaX = center.x();
         this.areaZ = center.z();
         this.areaRadius = radius;
         LongOpenHashSet chunks = new LongOpenHashSet();

         for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
               if (insideWorldLimit(x, z) && level.getChunkSource().getChunkNow(x, z) != null) {
                  chunks.add(ChunkPos.pack(x, z));
               }
            }
         }

         this.area = chunks;
         this.clusters.keySet().retainAll(chunks);
         dropOutside(this.results, chunks);
         int reach = radius + 1 + 1 + 1;
         this.shownSent.removeIf(key -> Math.max(Math.abs(ChunkPos.getX(key) - center.x()), Math.abs(ChunkPos.getZ(key) - center.z())) > reach);
         this.send(new AdvancedScanner.Want(chunks, center.x(), center.z()));
      }
   }

   private void copyArrived(ClientLevel level, long deadline) {
      if (this.areaRadius >= 0) {
         int reach = this.areaRadius + 1 + 1 + 1;

         for (int ring = 0; ring <= reach; ring++) {
            if (System.nanoTime() >= deadline) {
               return;
            }

            for (int x = this.areaX - ring; x <= this.areaX + ring; x++) {
               for (int z = this.areaZ - ring; z <= this.areaZ + ring; z++) {
                  if (Math.max(Math.abs(x - this.areaX), Math.abs(z - this.areaZ)) == ring) {
                     long key = ChunkPos.pack(x, z);
                     if (!this.shownSent.contains(key)) {
                        LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                        if (chunk != null) {
                           LevelChunkSection[] sections = chunk.getSections();
                           PalettedContainer<BlockState>[] copies = new PalettedContainer[sections.length];

                           for (int i = 0; i < sections.length; i++) {
                              if (sections[i] != null && !sections[i].hasOnlyAir()) {
                                 copies[i] = sections[i].getStates().copy();
                              }
                           }

                           this.shownSent.add(key);
                           this.send(new AdvancedScanner.Copied(x, z, copies));
                           if (System.nanoTime() >= deadline) {
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static BlobJudge.Client clientView(ClientLevel level) {
      return new BlobJudge.Client() {
         private int lastX = Integer.MIN_VALUE;
         private int lastZ = Integer.MIN_VALUE;
         private LevelChunk last;

         @Override
         public BlockState state(int x, int y, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != this.lastX || cz != this.lastZ) {
               this.lastX = cx;
               this.lastZ = cz;
               this.last = level.getChunkSource().getChunkNow(cx, cz);
            }

            if (this.last == null) {
               return null;
            } else {
               int index = this.last.getSectionIndex(y);
               return index >= 0 && index < this.last.getSectionsCount()
                  ? this.last.getSection(index).getBlockState(x & 15, y & 15, z & 15)
                  : Blocks.VOID_AIR.defaultBlockState();
            }
         }
      };
   }

   private void count(BlobJudge.Verdict verdict) {
      switch (verdict.status()) {
         case CLEAN:
         case ALERT:
         case MISPREDICTED:
         case HIDDEN:
            if (!this.counted.add(clusterKey(verdict.owner(), verdict.kind()))) {
               return;
            }

            switch (verdict.status()) {
               case CLEAN:
               case ALERT:
                  this.trusted++;
                  break;
               case MISPREDICTED:
                  this.mispredicted++;
                  break;
               default:
                  this.hidden++;
            }

            int seen = this.trusted + this.mispredicted;
            if (!this.seedWarned && seen >= 40 && this.mispredicted > 0.5 * seen) {
               this.seedWarned = true;
               this.distrusted = true;
               this.notices.add("Ores do not match seed " + this.seed + ". Advanced alerts are off until you change the seed.");
            }

            if (!this.hiddenWarned && this.hidden >= 10 && this.hidden > seen) {
               this.hiddenWarned = true;
               this.notices.add("This server hides ores with anti-xray. Advanced Scanner can't check them.");
            }

            return;
      }
   }

   private void send(Object message) {
      if (!this.stopped && !this.closed) {
         this.inbox.add(message);
         LockSupport.unpark(this.worker);
      }
   }

   private void release() {
      this.inbox.clear();
      if (!this.released) {
         this.released = true;
         this.clusters.clear();
         this.toJudge.clear();
         this.judgeQueued.clear();
         this.settling.clear();
         this.shownSent.clear();
         this.area = new LongOpenHashSet();
      }
   }

   private void work() {
      SeedWorldgen world = null;
      Throwable failure = null;

      try {
         world = SeedWorldgen.create(this.seed);
         this.predict(world);
      } catch (Throwable var9) {
         failure = var9;
      } finally {
         this.stopped = true;
         this.inbox.clear();
      }

      if (failure != null) {
         try {
            this.outbox.add((world == null ? "Advanced Scanner can't work out this world: " : "Advanced Scanner stopped: ") + failure);
         } catch (Throwable var8) {
         }
      }
   }

   private void predict(SeedWorldgen world) {
      PredictionArea prediction = new PredictionArea(world);
      boolean groundChecked = false;

      while (!this.closed) {
         Object message;
         while ((message = this.inbox.poll()) != null) {
            if (message instanceof AdvancedScanner.Want want) {
               prediction.want(want.chunks(), want.x(), want.z());
            } else if (message instanceof AdvancedScanner.Copied copied) {
               prediction.offer(ShownChunk.of(copied.x(), copied.z(), copied.sections()));
            }
         }

         if (prediction.idle()) {
            LockSupport.parkNanos(50000000L);
         } else {
            PredictionArea.ChunkClusters found = prediction.step();
            if (found != null) {
               this.outbox.add(found);
            }

            if (!groundChecked && prediction.compared() >= 20) {
               groundChecked = true;
               if (prediction.mismatched() * 2 > prediction.compared()) {
                  this.outbox.add("The terrain here does not match seed " + this.seed + ". Check the seed.");
               }
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public record ChunkResult(long chunk, int blocks, long[] anchors, List<BlobJudge.Verdict> verdicts) {
      public boolean flagged() {
         return this.anchors.length > 0;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Copied(int x, int z, PalettedContainer<BlockState>[] sections) {
   }

   @Environment(EnvType.CLIENT)
   private record Want(LongOpenHashSet chunks, int x, int z) {
   }
}
