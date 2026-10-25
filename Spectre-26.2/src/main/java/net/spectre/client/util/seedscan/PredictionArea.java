package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

@Environment(EnvType.CLIENT)
public final class PredictionArea {
   public static final int MARGIN = 1;
   public static final int DOUBT_MISMATCH = 8;
   public static final int MISMATCH_COLUMNS = 26;
   private final SeedWorldgen world;
   private final Long2ObjectOpenHashMap<TerrainChunk> terrains = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<ShownChunk> shown = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<ChunkDecoration> decorations = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<PredictionArea.MayWrite> may = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<GateHeights> gateHeights = new Long2ObjectOpenHashMap();
   private final DecorationOrder order = new DecorationOrder();
   private final Long2IntOpenHashMap doubt = new Long2IntOpenHashMap();
   private LongOpenHashSet bandChunks = new LongOpenHashSet();
   private LongOpenHashSet decorationChunks = new LongOpenHashSet();
   private final LongOpenHashSet dirty = new LongOpenHashSet();
   private final LongOpenHashSet built = new LongOpenHashSet();
   private final Long2ObjectOpenHashMap<VanillaBand> bands = new Long2ObjectOpenHashMap();
   private final LongOpenHashSet clustersDue = new LongOpenHashSet();
   private final LongOpenHashSet clustersSent = new LongOpenHashSet();
   private final ArrayDeque<PredictionArea.ChunkClusters> out = new ArrayDeque<>();
   private final LongOpenHashSet collected = new LongOpenHashSet();
   private final Long2LongOpenHashMap signatures = new Long2LongOpenHashMap();
   private int focusX;
   private int focusZ;
   private int compared;
   private int mismatched;

   public PredictionArea(SeedWorldgen world) {
      this.world = world;
      this.doubt.defaultReturnValue(-1);
   }

   public void want(LongCollection judged, int focusChunkX, int focusChunkZ) {
      this.focusX = focusChunkX;
      this.focusZ = focusChunkZ;
      LongOpenHashSet wanted = new LongOpenHashSet(judged);
      LongOpenHashSet decos = new LongOpenHashSet();
      int reach = 2;
      LongIterator it = wanted.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);

         for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
               decos.add(ChunkPos.pack(x + dx, z + dz));
            }
         }
      }

      it = decos.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (!this.decorations.containsKey(key)) {
            this.dirty.add(key);
         }
      }

      this.bandChunks = wanted;
      this.decorationChunks = decos;
      this.built.retainAll(wanted);
      this.bands.keySet().retainAll(wanted);
      this.clustersDue.retainAll(wanted);
      this.clustersSent.retainAll(wanted);
      this.dirty.retainAll(decos);
      this.forgetOutside(decos);
   }

   private void forgetOutside(LongOpenHashSet decos) {
      LongOpenHashSet near = new LongOpenHashSet();
      LongIterator it = decos.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               near.add(ChunkPos.pack(x + dx, z + dz));
            }
         }
      }

      this.terrains.keySet().retainAll(near);
      this.shown.keySet().retainAll(near);
      this.gateHeights.keySet().retainAll(near);
      this.doubt.keySet().retainAll(near);
      this.decorations.keySet().retainAll(decos);
      this.may.keySet().retainAll(decos);
      this.collected.retainAll(decos);
      this.signatures.keySet().retainAll(decos);
   }

   public void offer(ShownChunk chunk) {
      long key = ChunkPos.pack(chunk.chunkX, chunk.chunkZ);
      this.shown.put(key, chunk);
      this.doubt.remove(key);
      this.gateHeights.remove(key);

      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            this.collected.remove(ChunkPos.pack(chunk.chunkX + dx, chunk.chunkZ + dz));
         }
      }

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            long near = ChunkPos.pack(chunk.chunkX + dx, chunk.chunkZ + dz);
            if (this.decorationChunks.contains(near)) {
               this.markDirty(near);
            }
         }
      }
   }

   public boolean idle() {
      if (this.dirty.isEmpty() && this.out.isEmpty() && this.clustersDue.isEmpty()) {
         LongIterator it = this.bandChunks.iterator();

         while (it.hasNext()) {
            if (!this.built.contains(it.nextLong())) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public int compared() {
      return this.compared;
   }

   public int mismatched() {
      return this.mismatched;
   }

   public PredictionArea.ChunkClusters step() {
      if (!this.out.isEmpty()) {
         return this.out.poll();
      } else {
         LongOpenHashSet firstTime = new LongOpenHashSet();
         LongIterator it = this.dirty.iterator();

         while (it.hasNext()) {
            long key = it.nextLong();
            if (!this.decorations.containsKey(key)) {
               firstTime.add(key);
            }
         }

         long next = this.nearest(firstTime.isEmpty() ? this.dirty : firstTime);
         if (next != Long.MIN_VALUE) {
            int x = ChunkPos.getX(next);
            int z = ChunkPos.getZ(next);

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  long key = ChunkPos.pack(x + dx, z + dz);
                  if (!this.terrains.containsKey(key)) {
                     this.terrains.put(key, this.world.terrain(x + dx, z + dz));
                     return null;
                  }
               }
            }

            this.predict(next);
            return null;
         } else {
            LongOpenHashSet waiting = new LongOpenHashSet();
            LongIterator itx = this.bandChunks.iterator();

            while (itx.hasNext()) {
               long key = itx.nextLong();
               if (!this.built.contains(key)) {
                  waiting.add(key);
               }
            }

            while (!waiting.isEmpty()) {
               long key = this.nearest(waiting);
               waiting.remove(key);
               if (this.ready(key)) {
                  int x = ChunkPos.getX(key);
                  int z = ChunkPos.getZ(key);
                  ChunkDecoration[] around = new ChunkDecoration[9];

                  for (int dx = -1; dx <= 1; dx++) {
                     for (int dzx = -1; dzx <= 1; dzx++) {
                        around[(dx + 1) * 3 + dzx + 1] = (ChunkDecoration)this.decorations.get(ChunkPos.pack(x + dx, z + dzx));
                     }
                  }

                  VanillaBand band = VanillaBand.of((TerrainChunk)this.terrains.get(key), around, this.order);
                  this.built.add(key);
                  this.bands.put(key, band);

                  for (int dx = -2; dx <= 2; dx++) {
                     for (int dzx = -2; dzx <= 2; dzx++) {
                        long near = ChunkPos.pack(x + dx, z + dzx);
                        if (this.bandChunks.contains(near)) {
                           this.clustersDue.add(near);
                        }
                     }
                  }

                  return null;
               }
            }

            LongOpenHashSet due = new LongOpenHashSet(this.clustersDue);

            while (!due.isEmpty()) {
               long key = this.nearest(due);
               due.remove(key);
               if (this.clustersReady(key)) {
                  this.clustersDue.remove(key);
                  this.clustersSent.add(key);
                  return new PredictionArea.ChunkClusters(
                     key,
                     List.copyOf(BlobJudge.clusters(ChunkPos.getX(key), ChunkPos.getZ(key), (cx, cz) -> (VanillaBand)this.bands.get(ChunkPos.pack(cx, cz))))
                  );
               }
            }

            return null;
         }
      }
   }

   private boolean clustersReady(long key) {
      if (!this.bands.containsKey(key)) {
         return false;
      } else {
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               long near = ChunkPos.pack(x + dx, z + dz);
               if (this.bandChunks.contains(near) && !this.bands.containsKey(near)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private void unbuild(long key) {
      if (this.built.remove(key)) {
         this.bands.remove(key);
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);

         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               long near = ChunkPos.pack(x + dx, z + dz);
               if (this.clustersSent.remove(near)) {
                  this.out.add(new PredictionArea.ChunkClusters(near, null));
               }

               if (this.bandChunks.contains(near)) {
                  this.clustersDue.add(near);
               }
            }
         }
      }
   }

   private boolean ready(long key) {
      int x = ChunkPos.getX(key);
      int z = ChunkPos.getZ(key);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (!this.decorations.containsKey(ChunkPos.pack(x + dx, z + dz))) {
               return false;
            }
         }
      }

      if (!this.terrains.containsKey(key)) {
         return false;
      } else {
         for (int dx = -3; dx <= 3; dx++) {
            for (int dzx = -3; dzx <= 3; dzx++) {
               if (this.dirty.contains(ChunkPos.pack(x + dx, z + dzx))) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private void markDirty(long key) {
      if (this.decorationChunks.contains(key)) {
         this.dirty.add(key);
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               this.unbuild(ChunkPos.pack(x + dx, z + dz));
            }
         }
      }
   }

   private void predict(long key) {
      int x = ChunkPos.getX(key);
      int z = ChunkPos.getZ(key);
      ChunkDecoration full = OrePredictor.predict(this.world, x, z, this.inputs(x, z));
      this.dirty.remove(key);
      ChunkDecoration decoration = full.trimmed(-16, 63, true);
      ChunkDecoration before = (ChunkDecoration)this.decorations.put(key, decoration);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            this.unbuild(ChunkPos.pack(x + dx, z + dz));
         }
      }

      PredictionArea.MayWrite own = (PredictionArea.MayWrite)this.may.computeIfAbsent(key, k -> new PredictionArea.MayWrite(x, z));
      if (own.absorb(full)) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               if (dx != 0 || dz != 0) {
                  this.markDirty(ChunkPos.pack(x + dx, z + dz));
               }
            }
         }
      }

      long signature = DecorationOrder.evidenceSignature(decoration);
      boolean evidenceChanged = before == null || this.signatures.get(key) != signature || !this.collected.contains(key);
      this.signatures.put(key, signature);
      List<long[]> changed = new ArrayList<>();

      for (int dx = -2; dx <= 2; dx++) {
         for (int dzx = -2; dzx <= 2; dzx++) {
            long near = ChunkPos.pack(x + dx, z + dzx);
            ChunkDecoration d = (ChunkDecoration)this.decorations.get(near);
            if (d != null && this.neighbourhoodGuessed(near) && (evidenceChanged || !this.collected.contains(near))) {
               changed.addAll(this.order.collect(d, this.decorations, this::terrainOrNull, this::shownKind));
               this.collected.add(near);
            }
         }
      }

      for (long[] pair : changed) {
         for (long end : pair) {
            int ex = ChunkPos.getX(end);
            int ez = ChunkPos.getZ(end);

            for (int dx = -2; dx <= 2; dx++) {
               for (int dzxx = -2; dzxx <= 2; dzxx++) {
                  this.markDirty(ChunkPos.pack(ex + dx, ez + dzxx));
               }
            }
         }
      }
   }

   private boolean neighbourhoodGuessed(long key) {
      int x = ChunkPos.getX(key);
      int z = ChunkPos.getZ(key);

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            long near = ChunkPos.pack(x + dx, z + dz);
            if (this.decorationChunks.contains(near) && !this.decorations.containsKey(near)) {
               return false;
            }
         }
      }

      return true;
   }

   private TerrainChunk terrainOrNull(int chunkX, int chunkZ) {
      return (TerrainChunk)this.terrains.get(ChunkPos.pack(chunkX, chunkZ));
   }

   private byte shownKind(int x, int y, int z) {
      ShownChunk chunk = (ShownChunk)this.shown.get(ChunkPos.pack(x >> 4, z >> 4));
      return chunk == null ? 15 : chunk.kind(x, y, z);
   }

   private int heightDoubt(long key, TerrainChunk model, ShownChunk seen) {
      int known = this.doubt.get(key);
      if (known != -1) {
         return known;
      } else {
         int off = 0;
         int columns = 0;
         int chunkX = ChunkPos.getX(key);
         int chunkZ = ChunkPos.getZ(key);

         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               int wx = (chunkX << 4) + x;
               int wz = (chunkZ << 4) + z;
               int top = seen.terrainTop(wx, wz);
               if (top != Integer.MIN_VALUE) {
                  columns++;
                  if (Math.abs(top - model.oceanFloor(wx, wz)) > 2) {
                     off++;
                  }
               }
            }
         }

         int result = columns >= 64 && off * 256 > 26 * columns ? 8 : 0;
         this.doubt.put(key, result);
         if (columns >= 64) {
            this.compared++;
            if (result == 8) {
               this.mismatched++;
            }
         }

         return result;
      }
   }

   private GateHeights gateHeights(int chunkX, int chunkZ) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      GateHeights heights = (GateHeights)this.gateHeights.get(key);
      if (heights != null) {
         return heights;
      } else {
         TerrainChunk model = (TerrainChunk)this.terrains.get(key);
         ShownChunk seen = (ShownChunk)this.shown.get(key);
         heights = GateHeights.of(model, seen, seen == null ? 0 : this.heightDoubt(key, model, seen), this.world.treeAllowance(chunkX, chunkZ));
         this.gateHeights.put(key, heights);
         return heights;
      }
   }

   private OrePredictor.Inputs inputs(int chunkX, int chunkZ) {
      final long self = ChunkPos.pack(chunkX, chunkZ);
      return new OrePredictor.Inputs() {
         @Override
         public TerrainChunk terrain(int x, int z) {
            return (TerrainChunk)PredictionArea.this.terrains.get(ChunkPos.pack(x, z));
         }

         @Override
         public GateHeights gateHeights(int x, int z) {
            return PredictionArea.this.gateHeights(x, z);
         }

         @Override
         public boolean othersMayWrite(int x, int y, int z) {
            int px = x >> 4;
            int pz = z >> 4;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  long writer = ChunkPos.pack(px + dx, pz + dz);
                  if (writer != self && !PredictionArea.this.order.after(writer, self)) {
                     if (!PredictionArea.this.decorationChunks.contains(writer)) {
                        return true;
                     }

                     PredictionArea.MayWrite w = (PredictionArea.MayWrite)PredictionArea.this.may.get(writer);
                     if (w != null && w.nonBase(x, y, z)) {
                        return true;
                     }
                  }
               }
            }

            return false;
         }

         @Override
         public byte shown(int x, int y, int z) {
            return PredictionArea.this.shownKind(x, y, z);
         }

         @Override
         public boolean predictedOre(int x, int y, int z) {
            int px = x >> 4;
            int pz = z >> 4;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  PredictionArea.MayWrite w = (PredictionArea.MayWrite)PredictionArea.this.may.get(ChunkPos.pack(px + dx, pz + dz));
                  if (w != null && w.ore(x, y, z)) {
                     return true;
                  }
               }
            }

            return false;
         }
      };
   }

   private long nearest(LongOpenHashSet keys) {
      long best = Long.MIN_VALUE;
      int bestDistance = Integer.MAX_VALUE;
      LongIterator it = keys.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int distance = Math.max(Math.abs(ChunkPos.getX(key) - this.focusX), Math.abs(ChunkPos.getZ(key) - this.focusZ));
         if (distance < bestDistance) {
            bestDistance = distance;
            best = key;
         }
      }

      return best;
   }

   @Environment(EnvType.CLIENT)
   public record ChunkClusters(long chunk, List<BlobJudge.Cluster> clusters) {
   }

   @Environment(EnvType.CLIENT)
   static final class MayWrite {
      static final int TOP = 200;
      private final int originX;
      private final int originZ;
      private int[] nonBase = new int[0];
      private int[] ores = new int[0];
      private final List<ChunkDecoration.Fog> fogs = new ArrayList<>();

      MayWrite(int chunkX, int chunkZ) {
         this.originX = chunkX - 1 << 4;
         this.originZ = chunkZ - 1 << 4;
      }

      private int index(int x, int y, int z) {
         int lx = x - this.originX;
         int lz = z - this.originZ;
         return lx >= 0 && lx < 48 && lz >= 0 && lz < 48 && y >= -64 && y <= 200 ? ((y - -64) * 48 + lz) * 48 + lx : -1;
      }

      boolean absorb(ChunkDecoration d) {
         IntOpenHashSet addNonBase = new IntOpenHashSet();
         IntOpenHashSet addOres = new IntOpenHashSet();

         for (int i = 0; i < d.size(); i++) {
            long pos = d.key(i);
            int index = this.index(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
            if (index >= 0) {
               for (int n = 0; n < d.writeCount(i); n++) {
                  byte kind = ChunkDecoration.kind(d.write(i, n));
                  if (!VBlock.isBase(kind)) {
                     if (Arrays.binarySearch(this.nonBase, index) < 0) {
                        addNonBase.add(index);
                     }

                     if (kind == 11 && Arrays.binarySearch(this.ores, index) < 0) {
                        addOres.add(index);
                     }
                  }
               }
            }
         }

         boolean grew = !addNonBase.isEmpty() || !addOres.isEmpty();
         this.nonBase = merged(this.nonBase, addNonBase);
         this.ores = merged(this.ores, addOres);

         for (ChunkDecoration.Fog fog : d.fogs()) {
            if (!VBlock.isBase(fog.kind()) && !this.fogs.contains(fog)) {
               this.fogs.add(fog);
               grew = true;
            }
         }

         return grew;
      }

      private static int[] merged(int[] sorted, IntOpenHashSet more) {
         if (more.isEmpty()) {
            return sorted;
         } else {
            int[] out = Arrays.copyOf(sorted, sorted.length + more.size());
            int at = sorted.length;
            IntIterator it = more.iterator();

            while (it.hasNext()) {
               out[at++] = it.nextInt();
            }

            Arrays.sort(out);
            return out;
         }
      }

      boolean nonBase(int x, int y, int z) {
         int index = this.index(x, y, z);
         if (index >= 0 && Arrays.binarySearch(this.nonBase, index) >= 0) {
            return true;
         } else {
            for (ChunkDecoration.Fog fog : this.fogs) {
               if (fog.contains(x, y, z)) {
                  return true;
               }
            }

            return false;
         }
      }

      boolean ore(int x, int y, int z) {
         int index = this.index(x, y, z);
         if (index >= 0 && Arrays.binarySearch(this.ores, index) >= 0) {
            return true;
         } else {
            for (ChunkDecoration.Fog fog : this.fogs) {
               if (fog.kind() == 11 && fog.contains(x, y, z)) {
                  return true;
               }
            }

            return false;
         }
      }
   }
}
