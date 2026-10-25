package net.spectre.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.playerbypass.ChunkLightSnapshot;
import net.spectre.client.util.playerbypass.PlayerBypassLightTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class PlayerBypassModule extends ClientModule {
   private static final int RENDER_DISTANCE_CHUNKS = 16;
   private static final double MARKER_Y = 64.0;
   private static final float LINE_WIDTH = 1.5F;
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk Color", -55256));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 140.0F, 10.0F, 255.0F).step(1.0F));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final BooleanSetting showCoordinates = this.addSetting("show-coordinates", new BooleanSetting("Show Coordinates", true));
   private final BooleanSetting disconnect = this.addSetting("disconnect-on-spotted", new BooleanSetting("Auto Disconnect", false));

   public PlayerBypassModule() {
      super("player-bypass", "Player Bypass", "Flags chunks with signs of underground player bases from light data.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.sound.visibleWhen(notifications::get);
      this.showCoordinates.visibleWhen(() -> notifications.get() || this.disconnect.get());
   }

   @Override
   protected void onEnable() {
      PlayerBypassLightTracker.setSpottedListener(this::onChunkSpotted);
   }

   @Override
   protected void onDisable() {
      PlayerBypassLightTracker.setSpottedListener(null);
   }

   private void onChunkSpotted(int chunkX, int chunkZ) {
      Minecraft mc = Minecraft.getInstance();
      int x = (chunkX << 4) + 8;
      int z = (chunkZ << 4) + 8;
      boolean showPos = this.showCoordinates.get();
      if (this.notificationsOn()) {
         ChallengeToast.addOrUpdate(
            new ItemStack(Items.PLAYER_HEAD), "Base Detected", showPos ? Component.literal(x + ", " + z) : Component.empty(), this.sound.get()
         );
      }

      if (this.disconnect.get()) {
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            ModuleChat.send(this, ModuleChat.body().text("Auto Disconnect ").warn("triggered"));
            connection.getConnection().disconnect(Component.literal("[Player Bypass] Base detected" + (showPos ? " at " + x + ", " + z : "")));
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         Long2ObjectMap<ChunkLightSnapshot> snapshots = PlayerBypassLightTracker.snapshots();
         if (!snapshots.isEmpty()) {
            int color = this.chunkColor.argb();
            float colorAlpha = ARGB.alphaFloat(color);
            int lineAlpha = Math.max(20, Math.min(255, this.opacity.getInt()));
            int fillAlpha = Math.max(8, Math.min(255, this.opacity.getInt() / 3));
            int lineColor = ARGB.color(Math.round(lineAlpha * colorAlpha), color);
            GizmoStyle fill = GizmoStyle.fill(ARGB.color(Math.round(fillAlpha * colorAlpha), color));
            FoglessLines lines = FoglessLines.forFrame(event.partialTick);
            LongOpenHashSet flagged = new LongOpenHashSet();
            int centerX = mc.player.blockPosition().getX() >> 4;
            int centerZ = mc.player.blockPosition().getZ() >> 4;

            for (int cx = centerX - 16; cx <= centerX + 16; cx++) {
               for (int cz = centerZ - 16; cz <= centerZ + 16; cz++) {
                  long key = ChunkPos.pack(cx, cz);
                  ChunkLightSnapshot snapshot = (ChunkLightSnapshot)snapshots.get(key);
                  if (snapshot != null && PlayerBypassLightTracker.isFlagged(key, snapshot)) {
                     double x = cx << 4;
                     double z = cz << 4;
                     AABB slab = new AABB(x, 64.0, z, x + 16.0, 65.0, z + 16.0);
                     Gizmos.cuboid(slab, fill).setAlwaysOnTop();
                     lines.box(slab, lineColor, 1.5F);
                     flagged.add(key);
                  }
               }
            }

            if (this.tracers.get()) {
               this.chunkAreas.drawTracers(flagged, 64.5, ARGB.opaque(color));
            }
         }
      }
   }
}
