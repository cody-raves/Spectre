package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

@Environment(EnvType.CLIENT)
public final class ChunkDecoration {
   public final int chunkX;
   public final int chunkZ;
   private final long[] keys;
   private final int[] first;
   private final short[] writes;
   private final ChunkDecoration.Fog[] fogs;

   private ChunkDecoration(int chunkX, int chunkZ, long[] keys, int[] first, short[] writes, ChunkDecoration.Fog[] fogs) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.keys = keys;
      this.first = first;
      this.writes = writes;
      this.fogs = fogs;
   }

   static short pack(int order, byte kind, boolean possible) {
      return (short)(order << 5 | kind << 1 | (possible ? 1 : 0));
   }

   public static byte kind(short write) {
      return (byte)(write >> 1 & 15);
   }

   public static boolean possible(short write) {
      return (write & 1) != 0;
   }

   public int find(long pos) {
      return Arrays.binarySearch(this.keys, pos);
   }

   public int writeCount(int index) {
      return this.first[index + 1] - this.first[index];
   }

   public short write(int index, int n) {
      return this.writes[this.first[index] + n];
   }

   public List<ChunkDecoration.Fog> fogs() {
      return Arrays.asList(this.fogs);
   }

   public int size() {
      return this.keys.length;
   }

   public long key(int index) {
      return this.keys[index];
   }

   public ChunkDecoration trimmed(int minY, int maxY, boolean all) {
      ChunkDecoration.Builder builder = new ChunkDecoration.Builder(this.chunkX, this.chunkZ);

      for (int i = 0; i < this.keys.length; i++) {
         int y = BlockPos.getY(this.keys[i]);
         boolean inBand = y >= minY && y <= maxY;

         for (int n = this.first[i]; n < this.first[i + 1]; n++) {
            if (inBand || !all && !VBlock.isBase(kind(this.writes[n]))) {
               builder.add(this.keys[i], this.writes[n]);
            }
         }
      }

      for (ChunkDecoration.Fog fog : this.fogs) {
         builder.fog(fog);
      }

      return builder.build();
   }

   @Environment(EnvType.CLIENT)
   static final class Builder {
      final int chunkX;
      final int chunkZ;
      private final Long2ObjectOpenHashMap<ShortArrayList> byPos = new Long2ObjectOpenHashMap();
      private final ArrayList<ChunkDecoration.Fog> fogs = new ArrayList<>();

      Builder(int chunkX, int chunkZ) {
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
      }

      void add(long pos, short write) {
         ((ShortArrayList)this.byPos.computeIfAbsent(pos, k -> new ShortArrayList(2))).add(write);
      }

      ShortArrayList at(long pos) {
         return (ShortArrayList)this.byPos.get(pos);
      }

      void fog(ChunkDecoration.Fog fog) {
         this.fogs.add(fog);
      }

      List<ChunkDecoration.Fog> fogList() {
         return this.fogs;
      }

      ChunkDecoration build() {
         long[] keys = this.byPos.keySet().toLongArray();
         Arrays.sort(keys);
         int[] first = new int[keys.length + 1];
         int total = 0;
         ObjectIterator writes = this.byPos.long2ObjectEntrySet().iterator();

         while (writes.hasNext()) {
            Entry<ShortArrayList> entry = (Entry<ShortArrayList>)writes.next();
            total += ((ShortArrayList)entry.getValue()).size();
         }

         short[] writesx = new short[total];
         int at = 0;

         for (int i = 0; i < keys.length; i++) {
            first[i] = at;
            ShortArrayList list = (ShortArrayList)this.byPos.get(keys[i]);

            for (int n = 0; n < list.size(); n++) {
               writesx[at++] = list.getShort(n);
            }
         }

         first[keys.length] = at;
         return new ChunkDecoration(this.chunkX, this.chunkZ, keys, first, writesx, this.fogs.toArray(new ChunkDecoration.Fog[0]));
      }
   }

   @Environment(EnvType.CLIENT)
   public record Fog(int order, byte kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      boolean contains(int x, int y, int z) {
         return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
      }
   }
}
