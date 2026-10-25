package net.spectre.client.util.playerbypass;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlayerBypassLightTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final Long2ObjectOpenHashMap<ChunkLightSnapshot> SNAPSHOTS = new Long2ObjectOpenHashMap();
   private static final LongOpenHashSet ALERTED = new LongOpenHashSet();
   private static final Long2ObjectOpenHashMap<ChunkLightSnapshot> HELD = new Long2ObjectOpenHashMap();
   private static final ChunkEntryGuard ENTRY_GUARD = new ChunkEntryGuard();
   private static final ProtectedChunkStore PROTECTED_CHUNKS = ProtectedChunkStore.get();
   private static ClientLevel lastLevel;
   private static PlayerBypassLightTracker.SpottedListener spottedListener;

   private PlayerBypassLightTracker() {
   }

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      syncLevel(mc);
      LocalPlayer player = mc.player;
      if (lastLevel != null && player != null) {
         ENTRY_GUARD.update(player.chunkPosition().pack(), PlayerBypassLightTracker::isLoaded, PlayerBypassLightTracker::isFlaggedOrHeld, PROTECTED_CHUNKS);
      }

      PROTECTED_CHUNKS.tickAutosave();
   }

   public static void onPacketApplied(Packet<?> packet) {
      int chunkX;
      int chunkZ;
      ClientboundLightUpdatePacketData data;
      if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         chunkX = chunk.getX();
         chunkZ = chunk.getZ();
         data = chunk.getLightData();
      } else {
         if (!(packet instanceof ClientboundLightUpdatePacket light)) {
            return;
         }

         chunkX = light.getX();
         chunkZ = light.getZ();
         data = light.getLightData();
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      syncLevel(mc);
      if (level != null && data != null) {
         if (mc.player != null && mc.player.getY() < 0.0) {
            hold(chunkX, chunkZ, data, level.getSectionsCount(), level.getMinSectionY());
         } else {
            ingest(chunkX, chunkZ, data, level.getSectionsCount(), level.getMinSectionY());
         }
      }
   }

   private static void syncLevel(Minecraft mc) {
      ClientLevel level = mc.level;
      if (level != lastLevel) {
         SNAPSHOTS.clear();
         ALERTED.clear();
         HELD.clear();
         ENTRY_GUARD.reset();
         lastLevel = level;
         PROTECTED_CHUNKS.flush();
         if (level == null) {
            PROTECTED_CHUNKS.select(null, null);
         } else {
            PROTECTED_CHUNKS.select(worldKey(mc), level.dimension().identifier().toString());
         }
      }
   }

   private static String worldKey(Minecraft mc) {
      IntegratedServer local = mc.getSingleplayerServer();
      if (local != null) {
         Path folder = local.getWorldPath(LevelResource.ROOT).normalize().getFileName();
         return ProtectedChunkStore.singleplayerKey(folder == null ? "world" : folder.toString());
      } else {
         ServerData server = mc.getCurrentServer();
         return ProtectedChunkStore.serverKey(server == null ? null : server.ip);
      }
   }

   private static void ingest(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data, int sectionCount, int bottomSectionY) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      if (HELD.containsKey(key)) {
         hold(chunkX, chunkZ, data, sectionCount, bottomSectionY);
      }

      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)SNAPSHOTS.get(key);
      boolean wasFlagged = snapshot != null && isFlagged(key, snapshot);
      if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
         snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
         SNAPSHOTS.put(key, snapshot);
      }

      try {
         LightDataClassifier.apply(data, snapshot);
      } catch (Throwable var10) {
         LOGGER.error("Player Bypass light ingest failed for chunk {}, {}", new Object[]{chunkX, chunkZ, var10});
         return;
      }

      boolean flagged = snapshot.hasBaseSignal();
      if (flagged && !PROTECTED_CHUNKS.flagAllowed(key, wasFlagged)) {
         SNAPSHOTS.remove(key);
      } else {
         if (flagged) {
            if (spottedListener != null && ALERTED.add(key)) {
               spottedListener.onChunkSpotted(chunkX, chunkZ);
            }
         } else if (!ALERTED.contains(key)) {
            SNAPSHOTS.remove(key);
         }
      }
   }

   private static void hold(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data, int sectionCount, int bottomSectionY) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)HELD.get(key);
      if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
         snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
      }

      try {
         LightDataClassifier.apply(data, snapshot);
      } catch (Throwable var9) {
         LOGGER.error("Player Bypass light hold failed for chunk {}, {}", new Object[]{chunkX, chunkZ, var9});
         return;
      }

      if (snapshot.hasBaseSignal() && PROTECTED_CHUNKS.flagAllowed(key, false)) {
         HELD.put(key, snapshot);
      } else {
         HELD.remove(key);
      }
   }

   public static void setSpottedListener(PlayerBypassLightTracker.SpottedListener listener) {
      spottedListener = listener;
   }

   public static Long2ObjectMap<ChunkLightSnapshot> snapshots() {
      return SNAPSHOTS;
   }

   public static boolean isFlagged(long chunkKey, ChunkLightSnapshot snapshot) {
      return ALERTED.contains(chunkKey) || snapshot.hasBaseSignal();
   }

   public static boolean isFlagged(long chunkKey) {
      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)SNAPSHOTS.get(chunkKey);
      return snapshot != null && isFlagged(chunkKey, snapshot);
   }

   private static boolean isFlaggedOrHeld(long chunkKey) {
      return isFlagged(chunkKey) || HELD.containsKey(chunkKey);
   }

   private static boolean isLoaded(long chunkKey) {
      return lastLevel != null && lastLevel.getChunkSource().hasChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface SpottedListener {
      void onChunkSpotted(int var1, int var2);
   }
}
