package net.spectre.client.util.seedscan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

@Environment(EnvType.CLIENT)
public final class VanillaBand {
   public static final int MIN_Y = -16;
   public static final int MAX_Y = 63;
   public static final int LAYERS = 80;
   private static final int MAX_CHUNKS = 4;
   private static final int MAX_POSSIBLE = 10;
   private static final int[][][] PERMUTATIONS = permutations(4);
   public final int chunkX;
   public final int chunkZ;
   private final byte[] cells;

   private VanillaBand(int chunkX, int chunkZ, byte[] cells) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.cells = cells;
   }

   private static int index(int x, int y, int z) {
      return y - -16 << 8 | (z & 15) << 4 | x & 15;
   }

   public static boolean inBand(int y) {
      return y >= -16 && y <= 63;
   }

   public byte certain(int x, int y, int z) {
      return (byte)(this.cells[index(x, y, z)] & 15);
   }

   public int families(int x, int y, int z) {
      return this.cells[index(x, y, z)] >> 4 & 15;
   }

   public static VanillaBand of(TerrainChunk terrain, ChunkDecoration[] around, DecorationOrder order) {
      byte[] cells = new byte[20480];
      List<short[]> sequences = new ArrayList<>(9);
      long[] writers = new long[9];
      int x0 = terrain.chunkX << 4;
      int z0 = terrain.chunkZ << 4;

      for (int y = -16; y <= 63; y++) {
         for (int z = z0; z < z0 + 16; z++) {
            for (int x = x0; x < x0 + 16; x++) {
               byte model = terrain.kind(x, y, z);
               sequences.clear();
               long key = BlockPos.asLong(x, y, z);

               for (ChunkDecoration decoration : around) {
                  if (decoration != null) {
                     short[] sequence = sequence(decoration, key, x, y, z);
                     if (sequence != null) {
                        writers[sequences.size()] = ChunkPos.pack(decoration.chunkX, decoration.chunkZ);
                        sequences.add(sequence);
                     }
                  }
               }

               cells[index(x, y, z)] = play(model, sequences, writers, order);
            }
         }
      }

      return new VanillaBand(terrain.chunkX, terrain.chunkZ, cells);
   }

   private static short[] sequence(ChunkDecoration decoration, long key, int x, int y, int z) {
      int index = decoration.find(key);
      int count = index >= 0 ? decoration.writeCount(index) : 0;
      List<ChunkDecoration.Fog> fogs = null;

      for (ChunkDecoration.Fog fog : decoration.fogs()) {
         if (fog.contains(x, y, z)) {
            if (fogs == null) {
               fogs = new ArrayList<>(2);
            }

            fogs.add(fog);
         }
      }

      if (count == 0 && fogs == null) {
         return null;
      } else {
         short[] out = new short[count + (fogs == null ? 0 : fogs.size())];

         for (int n = 0; n < count; n++) {
            out[n] = decoration.write(index, n);
         }

         if (fogs != null) {
            int at = count;

            for (ChunkDecoration.Fog fogx : fogs) {
               out[at++] = ChunkDecoration.pack(fogx.order(), fogx.kind(), true);
            }

            Arrays.sort(out, 0, out.length);
         }

         return out;
      }
   }

   private static byte play(byte model, List<short[]> sequences, long[] writers, DecorationOrder known) {
      if (sequences.isEmpty()) {
         return (byte)(VBlock.familyBit(model) << 4 | model);
      } else {
         int possible = 0;

         for (short[] sequence : sequences) {
            for (short write : sequence) {
               if (ChunkDecoration.possible(write)) {
                  possible++;
               }
            }
         }

         if (sequences.size() <= 4 && possible <= 10) {
            int ends = 0;

            for (int[] order : PERMUTATIONS[sequences.size()]) {
               if (known == null || fits(order, writers, known)) {
                  for (int choice = 0; choice < 1 << possible; choice++) {
                     byte state = model;
                     int bit = 0;

                     for (int c : order) {
                        for (short writex : sequences.get(c)) {
                           boolean taken = true;
                           if (ChunkDecoration.possible(writex)) {
                              taken = (choice >> bit++ & 1) != 0;
                           }

                           if (taken && VBlock.isBase(state)) {
                              state = ChunkDecoration.kind(writex);
                           }
                        }
                     }

                     ends |= 1 << state;
                  }
               }
            }

            if (ends == 0) {
               return play(model, sequences, writers, null);
            } else {
               int families = 0;

               for (byte kind = 0; kind < 16; kind++) {
                  if ((ends >> kind & 1) != 0) {
                     families |= VBlock.familyBit(kind);
                  }
               }

               byte certain = Integer.bitCount(ends) == 1 ? (byte)Integer.numberOfTrailingZeros(ends) : 15;
               return (byte)(families << 4 | certain);
            }
         } else {
            int families = VBlock.familyBit(model);

            for (short[] sequence : sequences) {
               for (short writex : sequence) {
                  families |= VBlock.familyBit(ChunkDecoration.kind(writex));
               }
            }

            return (byte)(families << 4 | 15);
         }
      }
   }

   private static boolean fits(int[] order, long[] writers, DecorationOrder known) {
      for (int i = 0; i < order.length; i++) {
         for (int j = i + 1; j < order.length; j++) {
            if (known.after(writers[order[i]], writers[order[j]])) {
               return false;
            }
         }
      }

      return true;
   }

   private static int[][][] permutations(int max) {
      int[][][] all = new int[max + 1][][];

      for (int n = 0; n <= max; n++) {
         List<int[]> list = new ArrayList<>();
         permute(new int[n], new boolean[n], 0, list);
         all[n] = list.toArray(new int[0][]);
      }

      return all;
   }

   private static void permute(int[] current, boolean[] used, int depth, List<int[]> out) {
      if (depth == current.length) {
         out.add((int[])current.clone());
      } else {
         for (int i = 0; i < current.length; i++) {
            if (!used[i]) {
               used[i] = true;
               current[depth] = i;
               permute(current, used, depth + 1, out);
               used[i] = false;
            }
         }
      }
   }
}
