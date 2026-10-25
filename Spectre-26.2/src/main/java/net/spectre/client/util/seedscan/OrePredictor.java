package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

@Environment(EnvType.CLIENT)
public final class OrePredictor {
   private static final ThreadLocal<OrePredictor.Answers> ANSWERS = ThreadLocal.withInitial(OrePredictor.Answers::new);

   private OrePredictor() {
   }

   static boolean baseish(byte kind) {
      return VBlock.isBase(kind) || kind == 11;
   }

   static boolean disagreesOnBase(OrePredictor.Inputs inputs, byte model, int x, int y, int z) {
      byte shown = inputs.shown(x, y, z);
      if (shown == 15) {
         return true;
      } else {
         boolean modelBase = baseish(model);
         return shown != 11 ? modelBase != VBlock.isBase(shown) : !modelBase || model != 11 && !inputs.predictedOre(x, y, z);
      }
   }

   static boolean disagreesOnAir(OrePredictor.Inputs inputs, byte model, int x, int y, int z) {
      byte shown = inputs.shown(x, y, z);
      return shown == 15 ? true : model == 0 != (shown == 0) || disagreesOnBase(inputs, model, x, y, z);
   }

   public static ChunkDecoration predict(SeedWorldgen world, int chunkX, int chunkZ, OrePredictor.Inputs inputs) {
      OrePredictor.View view = new OrePredictor.View(world, chunkX, chunkZ, inputs);

      for (OreSpec spec : world.specs()) {
         view.spec = spec;
         OreSim.Chain chain = OreSim.run(spec, chunkX, chunkZ, view);
         ObjectIterator<Entry> it = chain.writes.long2ByteEntrySet().fastIterator();

         while (it.hasNext()) {
            Entry entry = (Entry)it.next();
            view.builder.add(entry.getLongKey(), ChunkDecoration.pack(spec.order(), spec.kind(), entry.getByteValue() == 2));
         }

         if (chain.fogged()) {
            int[] box = chain.fogBox();
            view.builder.fog(new ChunkDecoration.Fog(spec.order(), spec.kind(), box[0], box[1], box[2], box[3], box[4], box[5]));
         }
      }

      return view.builder.build();
   }

   @Environment(EnvType.CLIENT)
   private static final class Answers {
      static final int SIZE = 884736;
      final int[] stamp = new int[884736];
      final byte[] answer = new byte[884736];
      int generation;
   }

   @Environment(EnvType.CLIENT)
   public interface Inputs {
      TerrainChunk terrain(int var1, int var2);

      GateHeights gateHeights(int var1, int var2);

      boolean othersMayWrite(int var1, int var2, int var3);

      byte shown(int var1, int var2, int var3);

      boolean predictedOre(int var1, int var2, int var3);
   }

   @Environment(EnvType.CLIENT)
   private static final class View implements OreSim.World {
      final SeedWorldgen world;
      final int chunkX;
      final int chunkZ;
      final OrePredictor.Inputs inputs;
      final TerrainChunk[] terrains = new TerrainChunk[9];
      final GateHeights[] heights = new GateHeights[9];
      final ChunkDecoration.Builder builder;
      final OrePredictor.Answers answers = OrePredictor.ANSWERS.get();
      final int generation;
      final int minX;
      final int minZ;
      OreSpec spec;

      View(SeedWorldgen world, int chunkX, int chunkZ, OrePredictor.Inputs inputs) {
         this.world = world;
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
         this.inputs = inputs;
         this.builder = new ChunkDecoration.Builder(chunkX, chunkZ);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               this.terrains[(dx + 1) * 3 + dz + 1] = inputs.terrain(chunkX + dx, chunkZ + dz);
               this.heights[(dx + 1) * 3 + dz + 1] = inputs.gateHeights(chunkX + dx, chunkZ + dz);
            }
         }

         this.generation = ++this.answers.generation;
         this.minX = chunkX - 1 << 4;
         this.minZ = chunkZ - 1 << 4;
      }

      private int around(int x, int z) {
         int dx = (x >> 4) - this.chunkX;
         int dz = (z >> 4) - this.chunkZ;
         if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
            return (dx + 1) * 3 + dz + 1;
         } else {
            throw new IllegalStateException("read outside the 3x3 chunks at " + x + ", " + z);
         }
      }

      private TerrainChunk terrain(int x, int z) {
         return this.terrains[this.around(x, z)];
      }

      private byte answers(int x, int y, int z) {
         int lx = x - this.minX;
         int lz = z - this.minZ;
         int index = ((y - -64) * 48 + lz) * 48 + lx;
         if (this.answers.stamp[index] == this.generation) {
            return this.answers.answer[index];
         } else {
            byte model = this.terrain(x, z).kind(x, y, z);
            byte base = (byte)(VBlock.isBase(model) ? 1 : 0);
            boolean disagrees = OrePredictor.disagreesOnBase(this.inputs, model, x, y, z);
            byte forAir = disagrees ? 2 : base;
            byte forDraws = forAir;
            if (forAir == 1 && this.inputs.othersMayWrite(x, y, z)) {
               forDraws = 2;
            }

            byte air = (byte)(OrePredictor.disagreesOnAir(this.inputs, model, x, y, z) ? 2 : (model == 0 ? 1 : 0));
            byte packed = (byte)(forDraws | forAir << 2 | air << 4);
            this.answers.stamp[index] = this.generation;
            this.answers.answer[index] = packed;
            return packed;
         }
      }

      @Override
      public long seed() {
         return this.world.seed;
      }

      @Override
      public int gateLow(int x, int z) {
         return this.heights[this.around(x, z)].low(x, z);
      }

      @Override
      public int gateHigh(int x, int z) {
         return this.heights[this.around(x, z)].high(x, z);
      }

      @Override
      public byte base(int x, int y, int z) {
         byte base;
         if (this.spec.drawsOnTerrain()) {
            base = (byte)(this.answers(x, y, z) & 3);
            if (base == 1 && this.ownFogCovers(x, y, z)) {
               base = 2;
            }
         } else if (this.spec.discard() > 0.0F) {
            base = (byte)(this.answers(x, y, z) >> 2 & 3);
         } else {
            base = (byte)(VBlock.isBase(this.terrain(x, z).kind(x, y, z)) ? 1 : 0);
         }

         ShortArrayList own = this.builder.at(BlockPos.asLong(x, y, z));
         if (own != null) {
            for (int n = 0; n < own.size(); n++) {
               short write = own.getShort(n);
               if (!VBlock.isBase(ChunkDecoration.kind(write))) {
                  base = (byte)(ChunkDecoration.possible(write) && base != 0 ? 2 : 0);
               }
            }
         }

         return base;
      }

      private boolean ownFogCovers(int x, int y, int z) {
         for (ChunkDecoration.Fog fog : this.builder.fogList()) {
            if (!VBlock.isBase(fog.kind()) && fog.contains(x, y, z)) {
               return true;
            }
         }

         return false;
      }

      @Override
      public byte air(int x, int y, int z) {
         if (y >= -64 && y <= 319) {
            if (this.spec.discard() > 0.0F) {
               return (byte)(this.answers(x, y, z) >> 4 & 3);
            } else {
               return (byte)(this.terrain(x, z).kind(x, y, z) == 0 ? 1 : 0);
            }
         } else {
            return 1;
         }
      }

      @Override
      public boolean inBiome(OreSpec spec, int x, int y, int z) {
         return spec.biomes() == null || spec.inBiome(this.world.biomeAt(x, y, z));
      }
   }
}
