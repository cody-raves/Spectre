package net.spectre.client.util.netherite;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

@Environment(EnvType.CLIENT)
public final class NetheriteChunkTracker {
   static final int MAXIMUM_CANDIDATES = 512;
   private static final int ANCIENT_STATE_ID = Block.getId(Blocks.ANCIENT_DEBRIS.defaultBlockState());
   private static final ResourceKey<Level> DONUT_NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("worlds", "smp_nether"));
   private static final Set<NetheriteChunkTracker.Candidate> CANDIDATES = new LinkedHashSet<>();
   private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
   private static ResourceKey<Level> lastDimension;
   private static NetheriteChunkTracker.CandidateListener listener;
   private static NetheriteChunkTracker.RevealListener revealListener;

   private NetheriteChunkTracker() {
   }

   public static void onPacketApplied(Packet<?> packet) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && isNether(level)) {
         if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            trackLevel(level);

            for (int sectionY : HiddenDebrisScanner.findHiddenSections(chunk.getChunkData(), level.getSectionsCount(), level.getMinSectionY(), ANCIENT_STATE_ID)) {
               add(new NetheriteChunkTracker.Candidate(chunk.getX(), chunk.getZ(), sectionY));
            }
         } else if (packet instanceof ClientboundBlockUpdatePacket update) {
            onBlockUpdate(update.getPos(), update.getBlockState());
         } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates(NetheriteChunkTracker::onBlockUpdate);
         }
      }
   }

   private static void onBlockUpdate(BlockPos pos, BlockState state) {
      if (Block.getId(state) == ANCIENT_STATE_ID) {
         if (revealListener != null) {
            revealListener.onRevealed(pos.immutable());
         }

         CANDIDATES.remove(new NetheriteChunkTracker.Candidate(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4));
      }
   }

   private static void add(NetheriteChunkTracker.Candidate candidate) {
      if (!CANDIDATES.contains(candidate)) {
         if (CANDIDATES.size() >= 512) {
            Iterator<NetheriteChunkTracker.Candidate> oldest = CANDIDATES.iterator();
            oldest.next();
            oldest.remove();
         }

         CANDIDATES.add(candidate);
         if (listener != null) {
            listener.onCandidate(candidate);
         }
      }
   }

   private static void trackLevel(ClientLevel level) {
      ResourceKey<Level> dimension = level.dimension();
      if (level != lastLevel.get() || !dimension.equals(lastDimension)) {
         CANDIDATES.clear();
         lastLevel = new WeakReference<>(level);
         lastDimension = dimension;
      }
   }

   public static boolean isNether(ClientLevel level) {
      ResourceKey<Level> dimension = level.dimension();
      return dimension == Level.NETHER || DONUT_NETHER.equals(dimension) || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER);
   }

   public static List<NetheriteChunkTracker.Candidate> snapshot() {
      return Minecraft.getInstance().level != lastLevel.get() ? List.of() : List.copyOf(CANDIDATES);
   }

   public static void setListener(NetheriteChunkTracker.CandidateListener candidateListener) {
      listener = candidateListener;
   }

   public static void setRevealListener(NetheriteChunkTracker.RevealListener debrisListener) {
      revealListener = debrisListener;
   }

   @Environment(EnvType.CLIENT)
   public record Candidate(int chunkX, int chunkZ, int sectionY) {
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface CandidateListener {
      void onCandidate(NetheriteChunkTracker.Candidate var1);
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface RevealListener {
      void onRevealed(BlockPos var1);
   }
}
