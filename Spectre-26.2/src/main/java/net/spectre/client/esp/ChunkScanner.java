package net.spectre.client.esp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;
import net.spectre.client.event.impl.BlockUpdateEvent;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ChunkScanner {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final Object OWNER = new Object();
   private static final ChunkScanner.Consumer[] NONE = new ChunkScanner.Consumer[0];
   private static final List<ChunkScanner.Consumer> consumers = new ArrayList<>();
   private static ChunkScanner.Consumer[] snapshot = NONE;
   private static final ArrayDeque<Long> queue = new ArrayDeque<>();
   private static final Set<Long> queued = new HashSet<>();
   private static volatile boolean resetPending;

   private ChunkScanner() {
   }

   public static void init() {
      ClientChunkEvents.CHUNK_LOAD.register((Load)(level, chunk) -> enqueue(chunk.getPos().pack()));
      ClientTickEvents.END_CLIENT_TICK.register(ChunkScanner::tick);
      BlockUpdateEvent.TYPE.listen(OWNER, ChunkScanner::onBlockUpdate);
      PacketReceiveNettyEvent.TYPE.listen(OWNER, event -> {
         if (event.packet instanceof ClientboundLoginPacket) {
            resetPending = true;
         }
      });
   }

   public static void register(ChunkScanner.Consumer consumer) {
      if (!consumers.contains(consumer)) {
         consumers.add(consumer);
         snapshot = consumers.toArray(NONE);
         queueLoadedChunks();
      }
   }

   public static void unregister(ChunkScanner.Consumer consumer) {
      consumers.remove(consumer);
      snapshot = consumers.toArray(NONE);
      if (consumers.isEmpty()) {
         queue.clear();
         queued.clear();
      }
   }

   public static int pending() {
      return queue.size();
   }

   public static void rescan() {
      queueLoadedChunks();
   }

   private static void queueLoadedChunks() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int cx = mc.player.getBlockX() >> 4;
         int cz = mc.player.getBlockZ() >> 4;
         int radius = mc.options.renderDistance().get() + 1;
         List<long[]> loaded = new ArrayList<>();

         for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
               if (mc.level.getChunkSource().hasChunk(x, z)) {
                  int dx = x - cx;
                  int dz = z - cz;
                  loaded.add(new long[]{ChunkPos.pack(x, z), dx * dx + dz * dz});
               }
            }
         }

         loaded.sort(Comparator.comparingLong(a -> a[1]));

         for (long[] entry : loaded) {
            enqueue(entry[0]);
         }
      }
   }

   private static void enqueue(long chunkKey) {
      if (!consumers.isEmpty() && queued.add(chunkKey)) {
         queue.add(chunkKey);
      }
   }

   private static void tick(Minecraft mc) {
      if (resetPending) {
         resetPending = false;
         queue.clear();
         queued.clear();

         for (ChunkScanner.Consumer consumer : snapshot) {
            call(consumer, ChunkScanner.Consumer::reset);
         }

         queueLoadedChunks();
      }

      if (mc.level != null && !consumers.isEmpty()) {
         long deadline = System.nanoTime() + PerformanceModule.scanBudgetNanos();

         Long key;
         while ((key = queue.poll()) != null) {
            queued.remove(key);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
               scan(mc, chunk);
            }

            if (System.nanoTime() >= deadline) {
               break;
            }
         }
      }
   }

   private static void scan(Minecraft mc, LevelChunk chunk) {
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      int minSection = mc.level.dimensionType().minY() >> 4;
      LevelChunkSection[] sections = chunk.getSections();
      List<ChunkScanner.Consumer> interested = new ArrayList<>(consumers.size());

      for (int si = 0; si < sections.length; si++) {
         LevelChunkSection section = sections[si];
         if (section != null && !section.hasOnlyAir()) {
            interested.clear();

            for (ChunkScanner.Consumer consumer : consumers) {
               if (section.maybeHas(consumer::wants)) {
                  interested.add(consumer);
               }
            }

            if (!interested.isEmpty()) {
               int baseY = minSection + si << 4;

               for (int x = 0; x < 16; x++) {
                  for (int z = 0; z < 16; z++) {
                     for (int y = 0; y < 16; y++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.isAir()) {
                           long pos = BlockPos.asLong(minX + x, baseY + y, minZ + z);

                           for (ChunkScanner.Consumer consumerx : interested) {
                              if (consumerx.wants(state)) {
                                 found(consumerx, pos, state);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      long chunkKey = chunk.getPos().pack();
      MutableBlockPos probe = new MutableBlockPos();

      for (ChunkScanner.Consumer consumerxx : snapshot) {
         call(consumerxx, c -> c.chunkScanned(chunkKey, posx -> {
            BlockState state = chunk.getBlockState(probe.set(posx));
            return !state.isAir() && c.wants(state);
         }));
      }
   }

   private static void onBlockUpdate(BlockUpdateEvent event) {
      ChunkScanner.Consumer[] current = snapshot;
      if (current.length != 0) {
         long pos = event.pos.asLong();
         BlockState state = event.newState;

         for (ChunkScanner.Consumer consumer : current) {
            if (!state.isAir() && consumer.wants(state)) {
               found(consumer, pos, state);
            } else {
               removed(consumer, pos);
            }
         }
      }
   }

   private static void found(ChunkScanner.Consumer consumer, long pos, BlockState state) {
      try {
         consumer.found(pos, state);
      } catch (RuntimeException var5) {
         drop(consumer, var5);
      }
   }

   private static void removed(ChunkScanner.Consumer consumer, long pos) {
      try {
         consumer.removed(pos);
      } catch (RuntimeException var4) {
         drop(consumer, var4);
      }
   }

   private static void call(ChunkScanner.Consumer consumer, java.util.function.Consumer<ChunkScanner.Consumer> action) {
      try {
         action.accept(consumer);
      } catch (RuntimeException var3) {
         drop(consumer, var3);
      }
   }

   private static void drop(ChunkScanner.Consumer consumer, RuntimeException e) {
      LOGGER.error("[Spectre] Chunk scan consumer {} failed; disabling it", consumer.getClass().getSimpleName(), e);
      consumers.remove(consumer);
      snapshot = consumers.toArray(NONE);
   }

   @Environment(EnvType.CLIENT)
   public interface Consumer {
      boolean wants(BlockState var1);

      void found(long var1, BlockState var3);

      void removed(long var1);

      void reset();

      default void chunkScanned(long chunkKey, LongPredicate stillThere) {
      }
   }
}
