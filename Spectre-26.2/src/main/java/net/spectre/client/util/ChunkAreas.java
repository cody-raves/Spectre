package net.spectre.client.util;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class ChunkAreas {
   private final DoubleArrayList areaX = new DoubleArrayList();
   private final DoubleArrayList areaZ = new DoubleArrayList();
   private int areasSize = -1;
   private int areasHash;
   private final LongOpenHashSet seen = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongArrayList area = new LongArrayList();

   public void drawTracers(LongSet chunks, double y, int color) {
      if (!chunks.isEmpty()) {
         this.find(chunks);

         for (int i = 0; i < this.areaX.size(); i++) {
            TracerEngine.draw(new Vec3(this.areaX.getDouble(i), y, this.areaZ.getDouble(i)), color);
         }
      }
   }

   private void find(LongSet chunks) {
      int hash = chunks.hashCode();
      if (chunks.size() != this.areasSize || hash != this.areasHash) {
         this.areasSize = chunks.size();
         this.areasHash = hash;
         this.areaX.clear();
         this.areaZ.clear();
         this.seen.clear();
         LongIterator var3 = chunks.iterator();

         while (var3.hasNext()) {
            long start = (Long)var3.next();
            if (this.seen.add(start)) {
               this.area.clear();
               this.queue.enqueue(start);
               double sumX = 0.0;
               double sumZ = 0.0;

               while (!this.queue.isEmpty()) {
                  long key = this.queue.dequeueLong();
                  this.area.add(key);
                  int cx = ChunkPos.getX(key);
                  int cz = ChunkPos.getZ(key);
                  sumX += (cx << 4) + 8.0;
                  sumZ += (cz << 4) + 8.0;

                  for (int dx = -1; dx <= 1; dx++) {
                     for (int dz = -1; dz <= 1; dz++) {
                        long next = ChunkPos.pack(cx + dx, cz + dz);
                        if (chunks.contains(next) && this.seen.add(next)) {
                           this.queue.enqueue(next);
                        }
                     }
                  }
               }

               double mx = sumX / this.area.size();
               double mz = sumZ / this.area.size();
               if (!chunks.contains(ChunkPos.pack((int)Math.floor(mx) >> 4, (int)Math.floor(mz) >> 4))) {
                  double best = Double.MAX_VALUE;
                  double bx = mx;
                  double bz = mz;

                  for (int i = 0; i < this.area.size(); i++) {
                     long key = this.area.getLong(i);
                     double x = (ChunkPos.getX(key) << 4) + 8.0;
                     double z = (ChunkPos.getZ(key) << 4) + 8.0;
                     double d = (x - mx) * (x - mx) + (z - mz) * (z - mz);
                     if (d < best) {
                        best = d;
                        bx = x;
                        bz = z;
                     }
                  }

                  mx = bx;
                  mz = bz;
               }

               this.areaX.add(mx);
               this.areaZ.add(mz);
            }
         }
      }
   }
}
