package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.BlockUpdateEvent;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.client.PerformanceModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.StringSetting;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.seedscan.AdvancedScanner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.WorldOptions;

@Environment(EnvType.CLIENT)
public final class ChunkScannerModule extends ClientModule {
   private static final int PROBE_CHUNKS = 48;
   private static final byte OTHER = 0;
   private static final byte STONE = 1;
   private static final byte BLOB = 2;
   private static final byte AIR = 3;
   private static final byte MISSING = 4;
   private static final int[] SIDE_X = new int[]{1, -1, 0, 0};
   private static final int[] SIDE_Z = new int[]{0, 0, 1, -1};
   private static final int[] CORNER_X = new int[]{1, 1, -1, -1};
   private static final int[] CORNER_Z = new int[]{1, -1, 1, -1};
   private static final int MIN_Y = 0;
   private static final int MAX_Y = 32;
   private static final int CHUNK_COLOR = 1627389951;
   private static final int OLD_CHUNK_COLOR = 1614011733;
   private static final int SEED_SETTLE_TICKS = 20;
   private final BooleanSetting advancedScanner = this.addSetting("advanced-scanner", new BooleanSetting("Advanced Scanner", false));
   private final StringSetting seed = this.addSetting("seed", new StringSetting("Seed", ""));
   private final NumberSetting stackHeight = this.addSetting("stack-height", new NumberSetting("Sensitivity", 2.0F, 2.0F, 5.0F).step(1.0F));
   private final NumberSetting spreadRadius = this.addSetting("spread-radius", new NumberSetting("Spread Radius", 1.0F, 0.0F, 16.0F).step(1.0F));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk Color", 1627389951));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private static final ChunkScannerModule.Find UNDECIDED = new ChunkScannerModule.Find(0L, 0, new long[0]);
   private final Long2ObjectOpenHashMap<ChunkScannerModule.Find> flagged = new Long2ObjectOpenHashMap();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private final List<ChunkScannerModule.Find> fresh = new ArrayList<>();
   private final Long2IntOpenHashMap heat = new Long2IntOpenHashMap();
   private int appliedRadius;
   private LongOpenHashSet shown = new LongOpenHashSet();
   private ClientLevel lastLevel;
   private boolean scanning;
   private AdvancedScanner advanced;
   private boolean appliedAdvanced;
   private String seedText;
   private int seedStill;
   private boolean seedAsked;
   private ChunkScannerModule.ScanConfig appliedConfig;
   private WeakReference<ClientPacketListener> probeServer = new WeakReference<>(null);
   private boolean probing;
   private final LongOpenHashSet probed = new LongOpenHashSet();
   private final LevelChunk[] around = new LevelChunk[9];
   private final int[] groupX = new int[4];
   private final int[] groupZ = new int[4];
   private int groupSize;
   private boolean guessed;
   private Block wall;
   private Block lastBlock;
   private final int[] stackTop = new int[256];
   private final int[] stackShape = new int[256];
   private final int[] stackAreas = new int[256];
   private final boolean[] stackGuessed = new boolean[256];
   private final Block[] stackWall = new Block[256];
   private boolean sawBlob;
   private int scanX;
   private int scanZ;
   private int keepHeight;
   private long[] keptStacks = new long[16];
   private int keptStackCount;
   private int tallest;
   private long bottom;
   private boolean unsure;

   public ChunkScannerModule() {
      super("chunk-scanner", "Chunk Scanner", "Marks suspicious chunks with signs of player activity.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.sound.visibleWhen(notifications::get);
      this.stackHeight.visibleWhen(() -> !this.advancedScanner.get());
      this.seed.visibleWhen(this.advancedScanner::get);
      this.seed.privateValue();
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1 && this.chunkColor.argb() == 1614011733) {
         this.chunkColor.setArgb(1627389951);
      }
   }

   @Override
   protected void onEnable() {
      this.startLevel(Minecraft.getInstance().level);
   }

   @Override
   protected void onDisable() {
      this.startLevel(null);
   }

   private void startLevel(ClientLevel level) {
      this.closeAdvanced();
      this.appliedAdvanced = this.advancedScanner.get();
      this.flagged.clear();
      this.heat.clear();
      this.appliedRadius = this.spreadRadius.getInt();
      this.queue.clear();
      this.queued.clear();
      this.fresh.clear();
      this.shown = new LongOpenHashSet();
      this.lastLevel = level;
      this.scanning = level != null && level.dimension() != Level.NETHER && level.dimension() != Level.END;
      if (this.scanning) {
         ClientPacketListener connection = Minecraft.getInstance().getConnection();
         if (connection != this.probeServer.get()) {
            this.probeServer = new WeakReference<>(connection);
            this.probing = true;
            this.probed.clear();
         }

         this.appliedConfig = this.currentConfig(level);
         this.queueLoadedChunks(level);
      }
   }

   private ChunkScannerModule.ScanConfig currentConfig(ClientLevel level) {
      return new ChunkScannerModule.ScanConfig(Math.max(0, level.getMinY()), Math.min(32, level.getMaxY()), this.stackHeight.getInt());
   }

   private void queueLoadedChunks(ClientLevel level) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         int radius = mc.options.renderDistance().get() + 1;
         ChunkPos center = mc.player.chunkPosition();

         for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
               if (LevelUtil.isLoaded(level, x, z)) {
                  this.enqueue(ChunkPos.pack(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.scanning && !this.advancedScanner.get() && this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (this.advanced != null && event.packet instanceof ClientboundLevelChunkWithLightPacket arrived) {
         this.advanced.chunkArrived(arrived.getX(), arrived.getZ());
      }

      if (event.packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         this.enqueue(ChunkPos.pack(chunk.getX(), chunk.getZ()));

         for (int var7 = -1; var7 <= 1; var7++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (var7 != 0 || dz != 0) {
                  this.enqueue(ChunkPos.pack(chunk.getX() + var7, chunk.getZ() + dz));
               }
            }
         }
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (this.scanning && event.pos != null) {
         if (this.advanced != null) {
            this.advanced.blockChanged(event.pos);
         }

         int y = event.pos.getY();
         if (y >= this.appliedConfig.minY() - 1 && y <= this.appliedConfig.maxY() + 1) {
            if (matters(event.oldState) || matters(event.newState)) {
               int x = event.pos.getX();
               int z = event.pos.getZ();

               for (int cx = x - 2 >> 4; cx <= x + 1 >> 4; cx++) {
                  for (int cz = z - 2 >> 4; cz <= z + 2 >> 4; cz++) {
                     this.enqueue(ChunkPos.pack(cx, cz));
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != this.lastLevel) {
         this.startLevel(level);
      }

      if (this.scanning) {
         if (this.advancedScanner.get() != this.appliedAdvanced) {
            this.startLevel(level);
         }

         ChunkScannerModule.ScanConfig config = this.currentConfig(level);
         if (!config.equals(this.appliedConfig)) {
            this.appliedConfig = config;
            this.queueLoadedChunks(level);
         }

         int radius = this.spreadRadius.getInt();
         if (radius != this.appliedRadius) {
            this.appliedRadius = radius;
            this.heat.clear();
            ObjectIterator<Entry<ChunkScannerModule.Find>> it = this.flagged.long2ObjectEntrySet().fastIterator();

            while (it.hasNext()) {
               Entry<ChunkScannerModule.Find> entry = (Entry<ChunkScannerModule.Find>)it.next();
               this.spread(entry.getLongKey(), (ChunkScannerModule.Find)entry.getValue(), 1);
            }
         }

         long deadline = System.nanoTime() + PerformanceModule.scanBudgetNanos();
         if (this.advancedScanner.get()) {
            this.tickAdvanced(level, deadline);
         }

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               ChunkScannerModule.Find find = this.scan(level, cx, cz, config);
               if (this.sawBlob) {
                  this.probe(key);
               }

               if (find != UNDECIDED) {
                  ChunkScannerModule.Find was = (ChunkScannerModule.Find)this.flagged.get(key);
                  if (find == null ? was != null : was == null || !was.sameAs(find)) {
                     this.refile(key, was, find);
                     if (was == null) {
                        this.fresh.add(find);
                     }
                  }
               }
            }
         }

         this.announce(this.fresh);
         this.fresh.clear();
         this.shown = this.computeShown(level);
      }
   }

   private void refile(long key, ChunkScannerModule.Find was, ChunkScannerModule.Find find) {
      if (was != null) {
         this.spread(key, was, -1);
      }

      if (find == null) {
         this.flagged.remove(key);
      } else {
         this.flagged.put(key, find);
         this.spread(key, find, 1);
      }
   }

   private void spread(long key, ChunkScannerModule.Find find, int sign) {
      int amount = sign * find.stacks().length;
      int r = this.appliedRadius;
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int x = cx - r; x <= cx + r; x++) {
         for (int z = cz - r; z <= cz + r; z++) {
            long area = ChunkPos.pack(x, z);
            if (this.heat.addTo(area, amount) + amount == 0) {
               this.heat.remove(area);
            }
         }
      }
   }

   private LongOpenHashSet computeShown(ClientLevel level) {
      Long2IntOpenHashMap candidates = new Long2IntOpenHashMap();
      ObjectIterator var3 = Long2IntMaps.fastIterable(this.heat).iterator();

      while (var3.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var3.next();
         long key = entry.getLongKey();
         if (LevelUtil.isLoaded(level, ChunkPos.getX(key), ChunkPos.getZ(key))) {
            candidates.put(key, entry.getIntValue());
         }
      }

      return this.appliedRadius == 0 ? new LongOpenHashSet(candidates.keySet()) : hottestPerPatch(candidates);
   }

   private static LongOpenHashSet hottestPerPatch(Long2IntOpenHashMap candidates) {
      LongOpenHashSet kept = new LongOpenHashSet();
      LongOpenHashSet seen = new LongOpenHashSet();
      LongArrayList patch = new LongArrayList();
      LongArrayFIFOQueue open = new LongArrayFIFOQueue();
      LongIterator var5 = candidates.keySet().iterator();

      while (var5.hasNext()) {
         long start = (Long)var5.next();
         if (seen.add(start)) {
            patch.clear();
            open.enqueue(start);
            int max = 0;

            while (!open.isEmpty()) {
               long key = open.dequeueLong();
               patch.add(key);
               max = Math.max(max, candidates.get(key));
               int cx = ChunkPos.getX(key);
               int cz = ChunkPos.getZ(key);

               for (int side = 0; side < 4; side++) {
                  long next = ChunkPos.pack(cx + SIDE_X[side], cz + SIDE_Z[side]);
                  if (candidates.containsKey(next) && seen.add(next)) {
                     open.enqueue(next);
                  }
               }
            }

            for (int i = 0; i < patch.size(); i++) {
               if (candidates.get(patch.getLong(i)) == max) {
                  kept.add(patch.getLong(i));
               }
            }
         }
      }

      return kept;
   }

   private ChunkScannerModule.Find scan(ClientLevel level, int chunkX, int chunkZ, ChunkScannerModule.ScanConfig config) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            this.around[(dx + 1) * 3 + dz + 1] = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
         }
      }

      this.sawBlob = false;
      this.scanX = chunkX << 4;
      this.scanZ = chunkZ << 4;
      this.keepHeight = config.stackHeight();
      this.keptStackCount = 0;
      this.tallest = 0;
      this.bottom = 0L;
      this.unsure = false;

      ChunkScannerModule.Find var26;
      try {
         LevelChunk chunk = this.around[4];
         LevelChunkSection[] sections = chunk.getSections();
         int minSectionY = chunk.getMinSectionY();
         Arrays.fill(this.stackTop, Integer.MIN_VALUE);
         boolean blank = true;

         for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir()) {
               int baseY = minSectionY + i << 4;
               if (baseY + 15 >= config.minY() && baseY <= config.maxY()) {
                  int fromY = Math.max(config.minY() - baseY, 0);
                  int toY = Math.min(config.maxY() - baseY, 15);
                  if (blank) {
                     blank = !hasBlocks(section, fromY, toY);
                  }

                  if (section.maybeHas(state -> state.is(Blocks.STONE)) && section.maybeHas(state -> isBlob(state.getBlock()))) {
                     for (int y = fromY; y <= toY; y++) {
                        for (int z = 0; z < 16; z++) {
                           boolean afterStone = false;

                           for (int x = 0; x < 16; x++) {
                              Block block = section.getBlockState(x, y, z).getBlock();
                              boolean stone = block == Blocks.STONE;
                              if (stone) {
                                 if (!afterStone && this.isArea(x, baseY + y, z)) {
                                    this.stack(x, baseY + y, z);
                                 }
                              } else if (this.probing && isBlob(block)) {
                                 this.sawBlob = true;
                                 if (this.enclosed(x, baseY + y, z)) {
                                    this.probing = false;
                                    this.probed.clear();
                                 }
                              }

                              afterStone = stone;
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int ix = 0; ix < 256; ix++) {
            this.keep(ix);
         }

         if (this.tallest < config.stackHeight()) {
            return !this.unsure && !blank ? null : UNDECIDED;
         }

         var26 = new ChunkScannerModule.Find(this.bottom, this.tallest, Arrays.copyOf(this.keptStacks, this.keptStackCount));
      } finally {
         Arrays.fill(this.around, null);
      }

      return var26;
   }

   private void stack(int x, int y, int z) {
      int i = z << 4 | x;
      int shape = this.shape(x, z);
      boolean onTop = this.stackTop[i] == y - 1 && this.stackShape[i] == shape && this.stackWall[i] == this.wall;
      if (!onTop) {
         this.keep(i);
      }

      this.stackTop[i] = y;
      this.stackShape[i] = shape;
      this.stackWall[i] = this.wall;
      this.stackAreas[i] = onTop ? this.stackAreas[i] + 1 : 1;
      this.stackGuessed[i] = this.guessed || onTop && this.stackGuessed[i];
   }

   private void keep(int i) {
      int height = this.stackAreas[i];
      int top = this.stackTop[i];
      if (top != Integer.MIN_VALUE && height >= this.keepHeight) {
         int shape = this.stackShape[i];
         if (this.goesOn(i & 15, top + 1, i >> 4, shape) && this.goesOn(i & 15, top - height, i >> 4, shape)) {
            if (this.stackGuessed[i]) {
               this.unsure = true;
            } else {
               int x = this.scanX + (i & 15);
               int z = this.scanZ + (i >> 4);
               long lowest = BlockPos.asLong(x, top - height + 1, z);
               if (height > this.tallest || height == this.tallest && before(lowest, this.bottom)) {
                  this.tallest = height;
                  this.bottom = lowest;
               }

               if (this.keptStackCount == this.keptStacks.length) {
                  this.keptStacks = Arrays.copyOf(this.keptStacks, this.keptStacks.length * 2);
               }

               this.keptStacks[this.keptStackCount++] = lowest;
            }
         }
      }
   }

   private boolean goesOn(int x0, int y, int z0, int shape) {
      for (int bit = 0; bit < 6; bit++) {
         if ((shape >> bit & 1) != 0) {
            this.kind(x0 + bit / 3, y, z0 + bit % 3 - 1);
            if (!carriesStone(this.lastBlock)) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean before(long a, long b) {
      if (BlockPos.getY(a) != BlockPos.getY(b)) {
         return BlockPos.getY(a) < BlockPos.getY(b);
      } else {
         return BlockPos.getZ(a) != BlockPos.getZ(b) ? BlockPos.getZ(a) < BlockPos.getZ(b) : BlockPos.getX(a) < BlockPos.getX(b);
      }
   }

   private int shape(int x0, int z0) {
      int shape = 0;

      for (int i = 0; i < this.groupSize; i++) {
         shape |= 1 << (this.groupX[i] - x0) * 3 + this.groupZ[i] - z0 + 1;
      }

      return shape;
   }

   private boolean isArea(int x0, int y, int z0) {
      this.guessed = false;
      this.wall = null;
      if (this.seen(x0 - 1, y, z0) == 2 && this.sameWall() && this.seen(x0, y, z0 - 1) == 2 && this.sameWall()) {
         this.groupX[0] = x0;
         this.groupZ[0] = z0;
         int size = 1;
         int maxX = x0;
         int minZ = z0;
         int maxZ = z0;

         for (int i = 0; i < size; i++) {
            for (int side = 0; side < 4; side++) {
               int x = this.groupX[i] + SIDE_X[side];
               int z = this.groupZ[i] + SIDE_Z[side];
               if (!this.inGroup(x, z, size)) {
                  byte kind = this.seen(x, y, z);
                  if (kind == 2) {
                     if (!this.sameWall()) {
                        return false;
                     }
                  } else {
                     if (kind != 1) {
                        return false;
                     }

                     if (x < x0 || x == x0 && z < z0) {
                        return false;
                     }

                     maxX = Math.max(maxX, x);
                     minZ = Math.min(minZ, z);
                     maxZ = Math.max(maxZ, z);
                     if (maxX - x0 > 1 || maxZ - minZ > 1) {
                        return false;
                     }

                     this.groupX[size] = x;
                     this.groupZ[size] = z;
                     size++;
                  }
               }
            }
         }

         for (int i = 0; i < size; i++) {
            for (int corner = 0; corner < 4; corner++) {
               int x = this.groupX[i] + CORNER_X[corner];
               int z = this.groupZ[i] + CORNER_Z[corner];
               if (!this.inGroup(x, z, size) && (this.seen(x, y, z) != 2 || !this.sameWall())) {
                  return false;
               }
            }
         }

         for (int i = 0; i < size; i++) {
            if (this.kind(this.groupX[i], y + 1, this.groupZ[i]) == 3 || this.kind(this.groupX[i], y - 1, this.groupZ[i]) == 3) {
               return false;
            }
         }

         this.groupSize = size;
         return true;
      } else {
         return false;
      }
   }

   private boolean inGroup(int x, int z, int size) {
      for (int i = 0; i < size; i++) {
         if (this.groupX[i] == x && this.groupZ[i] == z) {
            return true;
         }
      }

      return false;
   }

   private byte seen(int x, int y, int z) {
      byte kind = this.kind(x, y, z);
      if (kind != 4) {
         return kind;
      } else {
         this.guessed = true;
         return 2;
      }
   }

   private boolean sameWall() {
      if (this.lastBlock == null) {
         return true;
      } else {
         if (this.wall == null) {
            this.wall = this.lastBlock;
         }

         return this.lastBlock == this.wall;
      }
   }

   private byte kind(int x, int y, int z) {
      LevelChunk chunk = this.around[((x >> 4) + 1) * 3 + (z >> 4) + 1];
      this.lastBlock = null;
      if (chunk == null) {
         return 4;
      } else {
         int index = chunk.getSectionIndex(y);
         if (index >= 0 && index < chunk.getSectionsCount()) {
            BlockState state = chunk.getSection(index).getBlockState(x & 15, y & 15, z & 15);
            this.lastBlock = state.getBlock();
            return kindOf(state);
         } else {
            return 0;
         }
      }
   }

   private boolean enclosed(int x, int y, int z) {
      return this.full(x + 1, y, z)
         && this.full(x - 1, y, z)
         && this.full(x, y, z + 1)
         && this.full(x, y, z - 1)
         && this.full(x, y + 1, z)
         && this.full(x, y - 1, z);
   }

   private boolean full(int x, int y, int z) {
      LevelChunk chunk = this.around[((x >> 4) + 1) * 3 + (z >> 4) + 1];
      if (chunk == null) {
         return false;
      } else {
         int index = chunk.getSectionIndex(y);
         return index >= 0 && index < chunk.getSectionsCount() && chunk.getSection(index).getBlockState(x & 15, y & 15, z & 15).isSolidRender();
      }
   }

   private static boolean hasBlocks(LevelChunkSection section, int fromY, int toY) {
      if (!section.maybeHas(state -> !state.isAir())) {
         return false;
      } else {
         for (int y = fromY; y <= toY; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  if (!section.getBlockState(x, y, z).isAir()) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private static byte kindOf(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.STONE) {
         return 1;
      } else if (isBlob(block)) {
         return 2;
      } else {
         return (byte)(state.isAir() ? 3 : 0);
      }
   }

   private static boolean isBlob(Block block) {
      return block == Blocks.DIORITE || block == Blocks.ANDESITE || block == Blocks.GRANITE || block == Blocks.GRAVEL;
   }

   private static boolean carriesStone(Block block) {
      return block == Blocks.STONE || block == Blocks.DEEPSLATE;
   }

   private static boolean matters(BlockState state) {
      return state != null && (kindOf(state) != 0 || carriesStone(state.getBlock()));
   }

   private void probe(long key) {
      if (this.probing && this.probed.add(key) && this.probed.size() >= 48) {
         this.probing = false;
         this.probed.clear();
         ModuleChat.send(this, ModuleChat.body().text("This server ").warn("hides").text(" ores with anti-xray, so patched shafts can't be seen"));
      }
   }

   private void announce(List<ChunkScannerModule.Find> finds) {
      if (this.notificationsOn()) {
         if (!finds.isEmpty()) {
            ItemStack icon = new ItemStack(this.advancedScanner.get() ? Items.DIORITE : Items.SPYGLASS);
            ChallengeToast.addOrUpdate(icon, this.name(), Component.literal("Suspect Chunk Scanned"), this.sound.get());
         }
      }
   }

   private void tickAdvanced(ClientLevel level, long deadline) {
      String text = this.seed.get();
      if (!text.equals(this.seedText)) {
         this.seedStill = this.seedText == null ? 20 : 0;
         this.seedText = text;
      } else if (this.seedStill < 20) {
         this.seedStill++;
      }

      if (this.seedStill >= 20) {
         OptionalLong wanted = WorldOptions.parseSeed(text);
         if (wanted.isEmpty()) {
            if (this.advanced != null) {
               this.startLevel(level);
            }

            if (!this.seedAsked) {
               this.seedAsked = true;
               ModuleChat.send(this, ModuleChat.body().text("Advanced Scanner needs the world seed: ").warn("set Seed"));
            }

            return;
         }

         this.seedAsked = false;
         if (this.advanced == null || this.advanced.seed() != wanted.getAsLong()) {
            if (this.advanced != null) {
               this.startLevel(level);
            }

            this.advanced = AdvancedScanner.start(wanted.getAsLong());
         }
      }

      if (this.advanced != null) {
         for (AdvancedScanner.ChunkResult result : this.advanced.tick(level, deadline)) {
            ChunkScannerModule.Find find = result.flagged() ? new ChunkScannerModule.Find(result.anchors()[0], result.blocks(), result.anchors()) : null;
            ChunkScannerModule.Find was = (ChunkScannerModule.Find)this.flagged.get(result.chunk());
            if (find == null ? was != null : was == null || !was.sameAs(find)) {
               this.refile(result.chunk(), was, find);
               if (was == null && find != null) {
                  this.fresh.add(find);
               }
            }
         }

         for (String notice : this.advanced.drainNotices()) {
            ModuleChat.send(this, notice);
         }
      }
   }

   private void closeAdvanced() {
      if (this.advanced != null) {
         this.advanced.close();
      }

      this.advanced = null;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (Minecraft.getInstance().level != null) {
         ChunkOverlay.draw(this.shown, this.chunkColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.shown, 63.0, ARGB.opaque(this.chunkColor.argb()));
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Find(long pos, int height, long[] stacks) {
      boolean sameAs(ChunkScannerModule.Find other) {
         return this.pos == other.pos && this.height == other.height && Arrays.equals(this.stacks, other.stacks);
      }
   }

   @Environment(EnvType.CLIENT)
   private record ScanConfig(int minY, int maxY, int stackHeight) {
   }
}
