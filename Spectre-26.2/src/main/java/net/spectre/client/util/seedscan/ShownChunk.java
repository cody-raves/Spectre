package net.spectre.client.util.seedscan;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

@Environment(EnvType.CLIENT)
public final class ShownChunk {
   static final int KEEP_TOP = 207;
   public final int chunkX;
   public final int chunkZ;
   private final byte[][] nibbles = new byte[24][];
   private final byte[] uniform = new byte[24];
   private final short[] terrainTop = new short[256];
   private final boolean[] iceTop = new boolean[256];
   private final short[] motionTop = new short[256];

   private ShownChunk(int chunkX, int chunkZ) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      Arrays.fill(this.terrainTop, (short)-64);
      Arrays.fill(this.motionTop, (short)-64);
   }

   public static ShownChunk of(int chunkX, int chunkZ, PalettedContainer<BlockState>[] sections) {
      ShownChunk shown = new ShownChunk(chunkX, chunkZ);

      for (int i = 0; i < 24; i++) {
         PalettedContainer<BlockState> states = i < sections.length ? sections[i] : null;
         if (states == null) {
            shown.uniform[i] = 0;
         } else {
            boolean keep = -64 + (i << 4) <= 207;
            byte first = VBlock.of(states.get(0, 0, 0));
            byte[] packed = null;

            for (int index = 0; index < 4096; index++) {
               int x = index & 15;
               int y = index >> 8;
               int z = index >> 4 & 15;
               BlockState state = states.get(x, y, z);
               byte kind = VBlock.of(state);
               int top = -64 + (i << 4) + y + 1;
               int column = x + 16 * z;
               if (kind != 0 && state.blocksMotion() && top > shown.motionTop[column]) {
                  shown.motionTop[column] = (short)top;
               }

               if (isTerrain(state, kind) && top > shown.terrainTop[column]) {
                  shown.terrainTop[column] = (short)top;
                  shown.iceTop[column] = isIce(state);
               }

               if (keep) {
                  if (packed == null) {
                     if (kind == first) {
                        continue;
                     }

                     packed = new byte[2048];

                     for (int before = 0; before < index; before++) {
                        put(packed, before, first);
                     }
                  }

                  put(packed, index, kind);
               }
            }

            shown.uniform[i] = keep ? first : 15;
            shown.nibbles[i] = packed;
         }
      }

      return shown;
   }

   static boolean isTerrain(BlockState state, byte kind) {
      if (kind == 0 || kind == 1 || kind == 2) {
         return false;
      } else if (kind != 12) {
         return true;
      } else if (!state.blocksMotion()) {
         return false;
      } else {
         String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
         return path.endsWith("dirt")
            || path.equals("grass_block")
            || path.equals("podzol")
            || path.equals("mycelium")
            || path.endsWith("sand")
            || path.endsWith("sandstone")
            || path.endsWith("terracotta")
            || path.equals("snow_block")
            || path.equals("packed_ice")
            || path.equals("blue_ice")
            || path.equals("mud")
            || path.equals("calcite")
            || path.equals("powder_snow")
            || path.equals("moss_block")
            || path.equals("clay")
            || path.equals("dripstone_block")
            || path.equals("magma_block")
            || path.equals("smooth_basalt");
      }
   }

   static boolean isIce(BlockState state) {
      return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SNOW_BLOCK);
   }

   private static void put(byte[] packed, int index, byte kind) {
      int b = index >> 1;
      packed[b] = (index & 1) == 0 ? (byte)(packed[b] & 240 | kind) : (byte)(packed[b] & 15 | kind << 4);
   }

   public byte kind(int x, int y, int z) {
      if (y >= -64 && y < 320) {
         int section = y - -64 >> 4;
         byte[] packed = this.nibbles[section];
         if (packed == null) {
            return this.uniform[section];
         } else {
            int index = (y & 15) << 8 | (z & 15) << 4 | x & 15;
            int b = packed[index >> 1];
            return (byte)((index & 1) == 0 ? b & 15 : b >> 4 & 15);
         }
      } else {
         return 0;
      }
   }

   public int terrainTop(int x, int z) {
      int column = (x & 15) + 16 * (z & 15);
      return this.iceTop[column] ? Integer.MIN_VALUE : this.terrainTop[column];
   }

   public int motionTop(int x, int z) {
      return this.motionTop[(x & 15) + 16 * (z & 15)];
   }
}
