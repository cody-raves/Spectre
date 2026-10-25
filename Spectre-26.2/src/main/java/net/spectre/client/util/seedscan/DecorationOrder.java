package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

@Environment(EnvType.CLIENT)
public final class DecorationOrder {
   public static final int MIN_VOTES = 3;
   private final Map<DecorationOrder.Pair, int[]> votes = new HashMap<>();
   private final Long2ObjectOpenHashMap<Map<DecorationOrder.Pair, int[]>> bySource = new Long2ObjectOpenHashMap();

   public int compare(long a, long b) {
      if (a == b) {
         return 0;
      } else {
         int[] v = this.votes.get(DecorationOrder.Pair.of(a, b));
         if (v == null) {
            return 0;
         } else {
            boolean aIsFirst = a < b;
            if (v[0] >= 3 && v[1] == 0) {
               return aIsFirst ? 1 : -1;
            } else if (v[1] >= 3 && v[0] == 0) {
               return aIsFirst ? -1 : 1;
            } else {
               return 0;
            }
         }
      }
   }

   public boolean after(long a, long b) {
      return this.compare(a, b) > 0;
   }

   public Set<long[]> collect(
      ChunkDecoration own, Long2ObjectMap<ChunkDecoration> decorations, DecorationOrder.TerrainLookup terrain, DecorationOrder.Shown shown
   ) {
      long ownKey = ChunkPos.pack(own.chunkX, own.chunkZ);
      Map<DecorationOrder.Pair, int[]> fresh = new HashMap<>();

      for (int i = 0; i < own.size(); i++) {
         long pos = own.key(i);
         byte ownKind = lastStone(own, i);
         if (ownKind >= 0) {
            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);
            byte otherKind = -1;
            long other = 0L;
            boolean usable = true;
            boolean found = false;

            for (int dx = -1; dx <= 1 && usable; dx++) {
               for (int dz = -1; dz <= 1 && usable; dz++) {
                  long key = ChunkPos.pack((x >> 4) + dx, (z >> 4) + dz);
                  ChunkDecoration d = (ChunkDecoration)decorations.get(key);
                  if (d != null) {
                     for (ChunkDecoration.Fog fog : d.fogs()) {
                        if (fog.contains(x, y, z)) {
                           usable = false;
                        }
                     }

                     if (key != ownKey) {
                        int index = d.find(pos);
                        if (index >= 0) {
                           if (found) {
                              usable = false;
                           } else {
                              found = true;
                              otherKind = lastStone(d, index);
                              other = key;
                              if (otherKind < 0) {
                                 usable = false;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (usable && found && otherKind != ownKind && other >= ownKey) {
               TerrainChunk chunk = terrain.terrain(x >> 4, z >> 4);
               if (chunk != null && VBlock.isBase(chunk.kind(x, y, z))) {
                  byte kind = shown.kind(x, y, z);
                  int[] v = fresh.computeIfAbsent(DecorationOrder.Pair.of(ownKey, other), k -> new int[2]);
                  if (kind == ownKind) {
                     v[0]++;
                  } else if (kind == otherKind) {
                     v[1]++;
                  }
               }
            }
         }
      }

      Set<DecorationOrder.Pair> touched = new HashSet<>(fresh.keySet());
      Map<DecorationOrder.Pair, int[]> old = (Map<DecorationOrder.Pair, int[]>)this.bySource.put(ownKey, fresh);
      if (old != null) {
         touched.addAll(old.keySet());
      }

      Set<long[]> changed = new HashSet<>();

      for (DecorationOrder.Pair pair : touched) {
         int before = this.compare(pair.first(), pair.second());
         int[] total = this.votes.computeIfAbsent(pair, k -> new int[2]);
         int[] was = old == null ? null : old.get(pair);
         int[] now = fresh.get(pair);
         if (was != null) {
            total[0] -= was[0];
            total[1] -= was[1];
         }

         if (now != null) {
            total[0] += now[0];
            total[1] += now[1];
         }

         if (total[0] == 0 && total[1] == 0) {
            this.votes.remove(pair);
         }

         if (this.compare(pair.first(), pair.second()) != before) {
            changed.add(new long[]{pair.first(), pair.second()});
         }
      }

      return changed;
   }

   static long evidenceSignature(ChunkDecoration d) {
      long hash = 1125899906842597L;

      for (int i = 0; i < d.size(); i++) {
         byte kind = lastStone(d, i);
         if (kind >= 0) {
            hash = 31L * hash + d.key(i);
            hash = 31L * hash + kind;
         }
      }

      return 31L * hash + d.fogs().hashCode();
   }

   private static byte lastStone(ChunkDecoration d, int index) {
      byte last = -1;

      for (int n = 0; n < d.writeCount(index); n++) {
         short write = d.write(index, n);
         byte kind = ChunkDecoration.kind(write);
         if (ChunkDecoration.possible(write) || !VBlock.isBase(kind)) {
            return -1;
         }

         last = kind;
      }

      return last;
   }

   @Environment(EnvType.CLIENT)
   private record Pair(long first, long second) {
      static DecorationOrder.Pair of(long a, long b) {
         return a < b ? new DecorationOrder.Pair(a, b) : new DecorationOrder.Pair(b, a);
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Shown {
      byte kind(int var1, int var2, int var3);
   }

   @Environment(EnvType.CLIENT)
   public interface TerrainLookup {
      TerrainChunk terrain(int var1, int var2);
   }
}
