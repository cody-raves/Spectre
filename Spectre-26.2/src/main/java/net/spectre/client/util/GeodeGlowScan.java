package net.spectre.client.util;

import java.util.function.LongConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;

@Environment(EnvType.CLIENT)
public final class GeodeGlowScan {
   public static final int MIN_Y = -58;
   public static final int MAX_Y = 30;
   private static final int EDGE_LIGHT = 4;
   private static final int LOW_SECTION = -4;
   private static final int HIGH_SECTION = 1;
   private static final int FIRST_CACHED = -5;
   private static final int CACHED = 8;
   private static final Direction[] SIDES = Direction.values();
   private final DataLayer[] layers = new DataLayer[72];
   private final MutableBlockPos cursor = new MutableBlockPos();
   private int chunkX;
   private int chunkZ;

   public int scan(ClientLevel level, LevelChunk chunk, LongConsumer hit) {
      this.chunkX = chunk.getPos().x();
      this.chunkZ = chunk.getPos().z();
      if (!this.cacheLight(level)) {
         return 0;
      } else {
         LevelChunkSection[] sections = chunk.getSections();
         int minX = this.chunkX << 4;
         int minZ = this.chunkZ << 4;
         int count = 0;

         for (int sy = -4; sy <= 1; sy++) {
            int index = chunk.getSectionIndexFromSectionY(sy);
            if (index >= 0 && index < sections.length && sections[index] != null && this.nearLight(sy)) {
               LevelChunkSection section = sections[index];
               int from = Math.max(-58, sy << 4);
               int to = Math.min(30, (sy << 4) + 15);

               for (int y = from; y <= to; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        if (open(section.getBlockState(x, y & 15, z))) {
                           int wx = minX + x;
                           int wz = minZ + z;
                           if (this.lightAt(wx, y, wz) == 0 && this.edge(level, wx, y, wz)) {
                              count++;
                              if (hit != null) {
                                 hit.accept(BlockPos.asLong(wx, y, wz));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return count;
      }
   }

   private boolean cacheLight(ClientLevel level) {
      LayerLightEventListener light = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
      boolean any = false;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int s = 0; s < 8; s++) {
               DataLayer layer = light.getDataLayerData(SectionPos.of(this.chunkX + dx, -5 + s, this.chunkZ + dz));
               if (layer != null && layer.isEmpty()) {
                  layer = null;
               }

               this.layers[slot(dx, dz, s)] = layer;
               any |= layer != null;
            }
         }
      }

      return any;
   }

   private boolean nearLight(int sy) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int s = sy - 1; s <= sy + 1; s++) {
               if (this.layers[slot(dx, dz, s - -5)] != null) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean edge(ClientLevel level, int x, int y, int z) {
      boolean litOpening = false;
      int brightest = 0;

      for (Direction side : SIDES) {
         int nx = x + side.getStepX();
         int ny = y + side.getStepY();
         int nz = z + side.getStepZ();
         int value = this.lightAt(nx, ny, nz);
         if (value > 4) {
            return false;
         }

         brightest = Math.max(brightest, value);
         if (value == 4 && !litOpening && open(level.getBlockState(this.cursor.set(nx, ny, nz)))) {
            litOpening = true;
         }
      }

      return brightest == 4 && litOpening;
   }

   private int lightAt(int x, int y, int z) {
      int s = (y >> 4) - -5;
      if (s >= 0 && s < 8) {
         DataLayer layer = this.layers[slot((x >> 4) - this.chunkX, (z >> 4) - this.chunkZ, s)];
         return layer == null ? 0 : layer.get(x & 15, y & 15, z & 15);
      } else {
         return 0;
      }
   }

   private static int slot(int dx, int dz, int s) {
      return ((dx + 1) * 3 + dz + 1) * 8 + s;
   }

   private static boolean open(BlockState state) {
      return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER);
   }
}
