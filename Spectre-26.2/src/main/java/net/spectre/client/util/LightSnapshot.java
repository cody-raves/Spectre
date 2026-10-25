package net.spectre.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

@Environment(EnvType.CLIENT)
public final class LightSnapshot {
   private static final int MAX_HITS = 250000;
   private final ArrayDeque<ChunkPos> chunks;
   private final Map<BlockPos, Integer> hits = new HashMap<>();
   private final int minLight;

   public LightSnapshot(ChunkPos center, int radius, int minLight) {
      this.minLight = minLight;
      List<ChunkPos> ordered = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            ordered.add(new ChunkPos(center.x() + x, center.z() + z));
         }
      }

      ordered.sort(Comparator.comparingInt(p -> (p.x() - center.x()) * (p.x() - center.x()) + (p.z() - center.z()) * (p.z() - center.z())));
      this.chunks = new ArrayDeque<>(ordered);
   }

   public Map<BlockPos, Integer> hits() {
      return this.hits;
   }

   public boolean collect(ClientLevel level) {
      long start = System.nanoTime();
      int processed = 0;

      while (!this.chunks.isEmpty() && processed++ < 16 && System.nanoTime() - start < 2000000L) {
         ChunkPos pos = this.chunks.removeFirst();
         if (level.getChunkSource().hasChunk(pos.x(), pos.z())) {
            LevelLightEngine engine = level.getChunkSource().getLightEngine();
            LayerLightEventListener blockLight = engine.getLayerListener(LightLayer.BLOCK);
            LayerLightEventListener skyLight = engine.getLayerListener(LightLayer.SKY);
            MutableBlockPos cursor = new MutableBlockPos();

            for (int sy = level.getMinY() >> 4; sy < 0; sy++) {
               DataLayer block = blockLight.getDataLayerData(SectionPos.of(pos.x(), sy, pos.z()));
               if (block != null && !block.isEmpty()) {
                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           int light = block.get(x, y, z);
                           if (light >= this.minLight) {
                              cursor.set((pos.x() << 4) + x, (sy << 4) + y, (pos.z() << 4) + z);
                              if (light > skyLight.getLightValue(cursor)) {
                                 this.hits.put(cursor.immutable(), light);
                              }

                              if (this.hits.size() >= 250000) {
                                 this.chunks.clear();
                                 return true;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return this.chunks.isEmpty();
   }
}
