package net.spectre.client.util.seedscan;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

@Environment(EnvType.CLIENT)
public final class SeedWorldgen {
   public static final int MIN_Y = -64;
   public static final int HEIGHT = 384;
   public static final int MAX_Y = 319;
   public final long seed;
   final NoiseBasedChunkGenerator generator;
   final RandomState randomState;
   final MultiNoiseBiomeSource biomeSource;
   final NoiseGeneratorSettings settings;
   private final List<OreSpec> specs;
   private final PalettedContainerFactory containers;
   private final StructureManager noStructures;
   private final LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
   private final long zoomSeed;
   private final Map<Long, BiomeGenerationSettings> carverBiomes = new ConcurrentHashMap<>();
   private final Map<Long, short[]> storedBiomes = new ConcurrentHashMap<>();
   private final List<Holder<Biome>> biomeIds;
   private final Map<Holder<Biome>, Integer> biomeIndex = new IdentityHashMap<>();
   private static final int MIN_QUART = -16;
   private static final int QUARTS_Y = 96;
   private static final int MAX_STORED_BIOMES = 4096;
   private static final int MAX_CARVER_BIOMES = 65536;
   private final BiomeManager biomeManager;
   private static final RegistryAccess CARVER_REGISTRIES = new ImmutableRegistryAccess(
      List.of(new MappedRegistry<>(Registries.BIOME, Lifecycle.stable()).freeze())
   );
   private static final Set<ResourceKey<Biome>> TALL_TREES = Set.of(
      Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.MANGROVE_SWAMP
   );

   private SeedWorldgen(long seed) {
      this.seed = seed;
      Provider lookup = new RegistrySetBuilder()
         .add(Registries.CONFIGURED_CARVER, Carvers::bootstrap)
         .add(Registries.PROCESSOR_LIST, ProcessorLists::bootstrap)
         .add(Registries.CONFIGURED_FEATURE, FeatureUtils::bootstrap)
         .add(Registries.PLACED_FEATURE, PlacementUtils::bootstrap)
         .add(Registries.BIOME, BiomeData::bootstrap)
         .add(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterLists::bootstrap)
         .add(Registries.NOISE, NoiseData::bootstrap)
         .add(Registries.DENSITY_FUNCTION, NoiseRouterData::bootstrap)
         .add(Registries.NOISE_SETTINGS, NoiseGeneratorSettings::bootstrap)
         .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
      Holder<NoiseGeneratorSettings> settingsHolder = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
      this.settings = settingsHolder.value();
      this.randomState = RandomState.create(this.settings, lookup.lookupOrThrow(Registries.NOISE), seed);
      Holder<MultiNoiseBiomeSourceParameterList> parameters = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
         .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
      this.biomeSource = MultiNoiseBiomeSource.createFromPreset(parameters);
      this.generator = new NoiseBasedChunkGenerator(this.biomeSource, settingsHolder);
      List<Holder<Biome>> biomes = List.copyOf(this.biomeSource.possibleBiomes());
      List<StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(biomes, holder -> this.generator.getBiomeGenerationSettings(holder).features(), true);
      Map<PlacedFeature, String> names = new IdentityHashMap<>();
      lookup.lookupOrThrow(Registries.PLACED_FEATURE).listElements().forEach(reference -> names.put(reference.value(), reference.key().identifier().getPath()));
      this.specs = OreSpec.derive(steps, biomes, this.generator, placed -> names.getOrDefault(placed, "?"), -64, 319);
      Holder<Biome> plains = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
      List<Holder<Biome>> ids = new ArrayList<>(biomes);
      if (!ids.contains(plains)) {
         ids.add(plains);
      }

      this.biomeIds = List.copyOf(ids);

      for (int i = 0; i < this.biomeIds.size(); i++) {
         this.biomeIndex.put(this.biomeIds.get(i), i);
      }

      this.containers = new PalettedContainerFactory(
         Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState(), null, Strategy.createForBiomes(listIds(ids)), plains, null
      );
      this.noStructures = new StructureManager(emptyLevel(this.containers, this.height), new WorldOptions(seed, false, false), null);
      this.zoomSeed = BiomeManager.obfuscateSeed(seed);
      this.biomeManager = new BiomeManager(this::storedBiome, this.zoomSeed);
   }

   public static SeedWorldgen create(long seed) {
      return new SeedWorldgen(seed);
   }

   public List<OreSpec> specs() {
      return this.specs;
   }

   public int treeAllowance(int chunkX, int chunkZ) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            int x = (chunkX + dx << 4) + 8;
            int z = (chunkZ + dz << 4) + 8;

            for (int y = 60; y <= 130; y += 10) {
               if (this.biomeAt(x, y, z).unwrapKey().filter(TALL_TREES::contains).isPresent()) {
                  return GateHeights.TALL_TREE_ALLOWANCE;
               }
            }
         }
      }

      return GateHeights.TREE_ALLOWANCE;
   }

   public Holder<Biome> biomeAt(int x, int y, int z) {
      return this.biomeManager.getBiome(new BlockPos(x, y, z));
   }

   Holder<Biome> storedBiome(int qx, int qy, int qz) {
      int clamped = Math.max(-16, Math.min(qy, 79));
      short[] quarts = this.storedBiomes(QuartPos.toSection(qx), QuartPos.toSection(qz));
      return this.biomeIds.get(quarts[((clamped - -16) * 4 + (qz & 3)) * 4 + (qx & 3)]);
   }

   private short[] storedBiomes(int chunkX, int chunkZ) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      short[] quarts = this.storedBiomes.get(key);
      if (quarts != null) {
         return quarts;
      } else {
         ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, this.height, this.containers, null);
         return this.fillBiomes(chunk);
      }
   }

   private short[] fillBiomes(ProtoChunk chunk) {
      this.generator.createBiomes(this.randomState, Blender.empty(), this.noStructures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.BIOMES);
      int x0 = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
      int z0 = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
      short[] quarts = new short[1536];

      for (int qy = 0; qy < 96; qy++) {
         for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
               Integer id = this.biomeIndex.get(chunk.getNoiseBiome(x0 + qx, -16 + qy, z0 + qz));
               quarts[(qy * 4 + qz) * 4 + qx] = (short)(id == null ? 0 : id);
            }
         }
      }

      if (this.storedBiomes.size() > 4096) {
         this.storedBiomes.clear();
      }

      this.storedBiomes.put(chunk.getPos().pack(), quarts);
      return quarts;
   }

   public ProtoChunk generateTerrain(int chunkX, int chunkZ) {
      ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, this.height, this.containers, null);
      this.fillBiomes(chunk);
      this.generator.fillFromNoise(Blender.empty(), this.randomState, this.noStructures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.NOISE);
      this.generator
         .buildSurface(chunk, new WorldGenerationContext(this.generator, chunk), this.randomState, this.noStructures, this.biomeManager, null, java.util.Set.of());
      chunk.setPersistedStatus(ChunkStatus.SURFACE);
      this.carve(chunk);
      chunk.setPersistedStatus(ChunkStatus.CARVERS);
      return chunk;
   }

   public TerrainChunk terrain(int chunkX, int chunkZ) {
      return TerrainChunk.of(this.generateTerrain(chunkX, chunkZ));
   }

   void carve(ProtoChunk chunk) {
      if (this.carverBiomes.size() > 65536) {
         this.carverBiomes.clear();
      }

      BiomeManager carverBiomeManager = new BiomeManager((x, y, z) -> this.biomeSource.getNoiseBiome(x, y, z, this.randomState.sampler()), this.zoomSeed);
      WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
      ChunkPos pos = chunk.getPos();
      NoiseChunk noise = chunk.getOrCreateNoiseChunk(c -> {
         throw new IllegalStateException("carving before the noise fill");
      });
      Aquifer aquifer = noise.aquifer();
      CarvingContext context = new CarvingContext(
         this.generator, CARVER_REGISTRIES, chunk.getHeightAccessorForGeneration(), noise, this.randomState, this.settings.surfaceRule()
      );
      CarvingMask mask = chunk.getOrCreateCarvingMask();

      for (int dx = -8; dx <= 8; dx++) {
         for (int dz = -8; dz <= 8; dz++) {
            ChunkPos from = new ChunkPos(pos.x() + dx, pos.z() + dz);
            BiomeGenerationSettings biome = this.carverBiomes
               .computeIfAbsent(
                  from.pack(),
                  k -> this.generator
                     .getBiomeGenerationSettings(
                        this.biomeSource
                           .getNoiseBiome(QuartPos.fromBlock(from.getMinBlockX()), 0, QuartPos.fromBlock(from.getMinBlockZ()), this.randomState.sampler())
                     )
               );
            int index = 0;

            for (Holder<ConfiguredWorldCarver<?>> holder : biome.getCarvers()) {
               random.setLargeFeatureSeed(this.seed + index, from.x(), from.z());
               if (holder.value().isStartChunk(random)) {
                  holder.value().carve(context, chunk, carverBiomeManager::getBiome, random, aquifer, from, mask);
               }

               index++;
            }
         }
      }
   }

   private static IdMap<Holder<Biome>> listIds(List<Holder<Biome>> list) {
      final Map<Holder<Biome>, Integer> ids = new IdentityHashMap<>();

      for (int i = 0; i < list.size(); i++) {
         ids.put(list.get(i), i);
      }

      return new IdMap<Holder<Biome>>() {
         public int getId(Holder<Biome> biome) {
            Integer id = ids.get(biome);
            return id == null ? -1 : id;
         }

         public Holder<Biome> byId(int id) {
            return id >= 0 && id < list.size() ? list.get(id) : null;
         }

         @Override
         public int size() {
            return list.size();
         }

         @Override
         public Iterator<Holder<Biome>> iterator() {
            return list.iterator();
         }
      };
   }

   private static LevelAccessor emptyLevel(PalettedContainerFactory containers, LevelHeightAccessor height) {
      ProtoChunk empty = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height, containers, null);
      InvocationHandler handler = (proxy, method, args) -> {
         if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
               case "equals" -> proxy == args[0];
               case "hashCode" -> System.identityHashCode(proxy);
               default -> "SeedWorldgen.emptyLevel";
            };
         }
         if (ChunkAccess.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 4) return empty;
         if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
         throw new UnsupportedOperationException("structure-free level: " + method);
      };
      return (LevelAccessor)Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class[]{LevelAccessor.class}, handler);
   }
}
