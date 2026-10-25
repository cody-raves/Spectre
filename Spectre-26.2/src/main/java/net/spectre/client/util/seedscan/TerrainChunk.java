package net.spectre.client.util.seedscan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;

@Environment(EnvType.CLIENT)
public final class TerrainChunk {
   public static final int MIN_Y = -64;
   public static final int HEIGHT = 384;
   public static final int SECTIONS = 24;
   public final int chunkX;
   public final int chunkZ;
   private final byte[][] nibbles = new byte[24][];
   private final byte[] uniform = new byte[24];
   private final short[] oceanFloor = new short[256];

   private TerrainChunk(int chunkX, int chunkZ) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
   }

   static TerrainChunk of(ProtoChunk chunk) {
      TerrainChunk terrain = new TerrainChunk(chunk.getPos().x(), chunk.getPos().z());

      for (int i = 0; i < 24; i++) {
         LevelChunkSection section = chunk.getSection(i);
         if (section.hasOnlyAir()) {
            terrain.uniform[i] = 0;
         } else {
            byte first = VBlock.of(section.getBlockState(0, 0, 0));
            byte[] packed = null;

            for (int index = 0; index < 4096; index++) {
               BlockState state = section.getBlockState(index & 15, index >> 8, index >> 4 & 15);
               byte kind = VBlock.of(state);
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

            terrain.uniform[i] = first;
            terrain.nibbles[i] = packed;
         }
      }

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            terrain.oceanFloor[x + 16 * z] = (short)(chunk.getHeight(Types.OCEAN_FLOOR_WG, x, z) + 1);
         }
      }

      return terrain;
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

   public int oceanFloor(int x, int z) {
      return this.oceanFloor[(x & 15) + 16 * (z & 15)];
   }
}
