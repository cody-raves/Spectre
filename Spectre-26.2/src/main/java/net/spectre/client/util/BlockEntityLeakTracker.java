package net.spectre.client.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

@Environment(EnvType.CLIENT)
public final class BlockEntityLeakTracker {
   private static final Map<BlockPos, BlockEntityLeakTracker.Entry> FOUND = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<BlockPos> ADDED = new ConcurrentLinkedQueue<>();
   private static final AtomicInteger GENERATION = new AtomicInteger();
   private static volatile boolean recordAdditions;

   private BlockEntityLeakTracker() {
   }

   public static Map<BlockPos, BlockEntityLeakTracker.Entry> view() {
      return FOUND;
   }

   public static boolean contains(BlockPos pos) {
      return FOUND.containsKey(pos);
   }

   public static void clear() {
      FOUND.clear();
      ADDED.clear();
      GENERATION.incrementAndGet();
   }

   public static int generation() {
      return GENERATION.get();
   }

   public static void recordAdditions(boolean on) {
      recordAdditions = on;
      if (!on) {
         ADDED.clear();
      }
   }

   public static BlockPos pollAdded() {
      return ADDED.poll();
   }

   public static void ingest(Packet<?> packet) {
      if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
         ClientboundLevelChunkPacketData data = chunkPacket.getChunkData();

         try {
            data.getBlockEntitiesTagsConsumer(chunkPacket.getX(), chunkPacket.getZ()).accept((pos, type, tag) -> {
               if (type != null) {
                  put(pos, type);
               }
            });
         } catch (Throwable var5) {
         }
      } else if (packet instanceof ClientboundBlockEntityDataPacket bePacket) {
         put(bePacket.getPos(), bePacket.getType());
      } else if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
         clear();
      }
   }

   private static void put(BlockPos pos, BlockEntityType<?> type) {
      BlockPos key = pos.immutable();
      if (FOUND.putIfAbsent(key, new BlockEntityLeakTracker.Entry(blockFromType(type))) == null && recordAdditions) {
         ADDED.add(key);
      }
   }

   public static Block blockFromType(BlockEntityType<?> type) {
      Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
      if (id == null) {
         return Blocks.AIR;
      } else {
         String path = id.getPath();

         Block mapped = switch (path) {
            case "mob_spawner" -> Blocks.SPAWNER;
            case "piston" -> Blocks.MOVING_PISTON;
            case "sign" -> Blocks.OAK_SIGN;
            case "hanging_sign" -> Blocks.OAK_HANGING_SIGN;
            case "skull" -> Blocks.SKELETON_SKULL;
            case "banner" -> Blocks.BANNER.white();
            case "bed" -> Blocks.BED.red();
            case "brushable_block" -> Blocks.SUSPICIOUS_SAND;
            default -> null;
         };
         return mapped != null ? mapped : BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(Blocks.AIR);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Entry(Block block) {
   }
}
