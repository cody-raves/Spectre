package net.spectre.client.module.impl.misc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.util.LevelUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class RenderUtilModule extends ClientModule {
   private static final double MARKER_Y = 63.0;
   private static final float LINE_WIDTH = 1.5F;
   private static final int CYAN = 65535;
   private static final int SOURCE_FILL_ALPHA = 40;
   private final BooleanSetting clearOnToggle = this.addSetting("clear-chunks-on-toggle", new BooleanSetting("Clear On Toggle", true));
   private final BooleanSetting highlightSources = this.addSetting("render-flagged-chunk", new BooleanSetting("Highlight Source Chunks", true));
   private final ColorSetting fillColor = this.addSetting("side-color", new ColorSetting("Fill Color", 687810600));
   private final ColorSetting lineColor = this.addSetting("line-color", new ColorSetting("Line Color", -55256));
   private final Set<ChunkPos> flaggedChunks = new HashSet<>();
   private final Set<ChunkPos> sourceChunks = new HashSet<>();
   private ClientPacketListener session;

   public RenderUtilModule() {
      super("render-util", "Render Util", "Renders the chunks flagged by Quantum.", ModuleCategory.BASE_RAID);
   }

   @Override
   protected void onEnable() {
      if (this.clearOnToggle.get()) {
         this.flaggedChunks.clear();
         this.sourceChunks.clear();
      }
   }

   private void ensureSession() {
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      if (connection != this.session) {
         this.session = connection;
         this.flaggedChunks.clear();
         this.sourceChunks.clear();
      }
   }

   public boolean hasFlaggedChunks() {
      this.ensureSession();
      return !this.flaggedChunks.isEmpty();
   }

   public boolean isFlagged(ChunkPos pos) {
      this.ensureSession();
      return this.flaggedChunks.contains(pos);
   }

   public void addFlaggedChunks(Collection<ChunkPos> chunks) {
      this.ensureSession();
      this.flaggedChunks.addAll(chunks);
   }

   public void clearFlaggedChunks() {
      this.ensureSession();
      this.flaggedChunks.clear();
   }

   public void addSourceChunk(ChunkPos pos) {
      this.ensureSession();
      this.sourceChunks.add(pos);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.ensureSession();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && LevelUtil.isOverworld(mc.level)) {
         GizmoStyle flagStyle = GizmoStyle.strokeAndFill(this.lineColor.argb(), 1.5F, this.fillColor.argb());
         boolean sources = this.highlightSources.get();

         for (ChunkPos pos : this.flaggedChunks) {
            if (!sources || !this.sourceChunks.contains(pos)) {
               Gizmos.cuboid(marker(pos), flagStyle).setAlwaysOnTop();
            }
         }

         if (sources) {
            GizmoStyle sourceStyle = GizmoStyle.strokeAndFill(-16711681, 1.5F, 671154175);

            for (ChunkPos posx : this.sourceChunks) {
               Gizmos.cuboid(marker(posx), sourceStyle).setAlwaysOnTop();
            }
         }
      }
   }

   private static AABB marker(ChunkPos pos) {
      double x = pos.x() * 16.0;
      double z = pos.z() * 16.0;
      return new AABB(x, 63.0, z, x + 16.0, 63.0, z + 16.0);
   }
}
