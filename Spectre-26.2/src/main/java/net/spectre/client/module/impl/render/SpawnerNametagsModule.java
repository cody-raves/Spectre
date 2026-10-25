package net.spectre.client.module.impl.render;

import com.example.client.mixin.AccessorSpawnerNametagsBaseSpawner;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import net.spectre.client.esp.ChunkScanner;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.gui.GuiPalette;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.gui.WorldText;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SpawnerNametagsModule extends ClientModule implements HudOverlayProvider, ChunkScanner.Consumer {
   private static final int MAX_LABELS = 48;
   private static final int RING_SEGMENTS = 64;
   private static final float RING_WIDTH = 1.5F;
   private static final float RING_ALPHA_INSIDE = 0.8F;
   private static final float RING_ALPHA_OUTSIDE = 0.4F;
   private static final double LABEL_HEIGHT = 1.2;
   private static final double FADE_SHARE = 0.125;
   private static final String SEPARATOR = "  ·  ";
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 64.0F, 8.0F, 256.0F).step(1.0F));
   private final BooleanSetting sRadius = this.addSetting("activation-radius", new BooleanSetting("Activation Radius", true));
   private final LongOpenHashSet spawners = new LongOpenHashSet();

   public SpawnerNametagsModule() {
      super("spawner-nametags", "Spawner Nametags", "Shows each spawner's mob and activation range over the block.", ModuleCategory.RENDER);
   }

   @Override
   protected void onEnable() {
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.spawners.clear();
   }

   public static String mobName(SpawnerBlockEntity spawner) {
      SpawnData data = ((AccessorSpawnerNametagsBaseSpawner)spawner.getSpawner()).spectre$spawnerNametagsNextSpawnData();
      if (data == null) {
         return null;
      } else {
         String id = data.entityToSpawn().getStringOr("id", "");
         return id.isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.tryParse(id)).map(type -> type.getDescription().getString()).orElse(null);
      }
   }

   public static int activationRange(SpawnerBlockEntity spawner) {
      return ((AccessorSpawnerNametagsBaseSpawner)spawner.getSpawner()).spectre$spawnerNametagsRequiredPlayerRange();
   }

   @Override
   public boolean wants(BlockState state) {
      return state.is(Blocks.SPAWNER);
   }

   @Override
   public void found(long pos, BlockState state) {
      this.spawners.add(pos);
   }

   @Override
   public void removed(long pos) {
      this.spawners.remove(pos);
   }

   @Override
   public void reset() {
      this.spawners.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      LongIterator it = this.spawners.iterator();

      while (it.hasNext()) {
         long pos = it.nextLong();
         if (ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) == chunkKey && !stillThere.test(pos)) {
            it.remove();
         }
      }
   }

   private List<SpawnerNametagsModule.Shown> inRange(Minecraft mc) {
      List<SpawnerNametagsModule.Shown> shown = new ArrayList<>();
      if (!this.spawners.isEmpty() && mc.level != null && mc.player != null) {
         Level level = mc.level;
         Vec3 player = mc.player.position();
         double range = this.sRange.get();
         LongIterator it = this.spawners.iterator();

         while (it.hasNext()) {
            BlockPos pos = BlockPos.of(it.nextLong());
            double distanceSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (!(distanceSq > range * range) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
               int activation = activationRange(spawner);
               boolean inside = activation < 0 || distanceSq < (double)activation * activation;
               shown.add(new SpawnerNametagsModule.Shown(pos, Math.sqrt(distanceSq), mobName(spawner), activation, inside));
            }
         }

         shown.sort(Comparator.comparingDouble(SpawnerNametagsModule.Shown::distance));
         return shown.size() > 48 ? shown.subList(0, 48) : shown;
      } else {
         return shown;
      }
   }

   private float fade(double distance) {
      double range = this.sRange.get();
      return (float)Math.max(0.0, Math.min(1.0, (range - distance) / (range * 0.125)));
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && !this.spawners.isEmpty();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.gui.hud.isHidden()) {
         List<SpawnerNametagsModule.Shown> shown = this.inRange(mc);
         int accent = WorldText.hudAccent();

         for (int i = shown.size() - 1; i >= 0; i--) {
            SpawnerNametagsModule.Shown spawner = shown.get(i);
            float[] screen = labelAnchor(mc, spawner.pos(), screenW, screenH);
            if (screen != null) {
               String name = spawner.mob() == null ? "Spawner" : spawner.mob() + " Spawner";
               String distance = Math.round(spawner.distance()) + "m";
               String[] runs;
               int[] colors;
               if (this.sRadius.get() && spawner.range() > 0) {
                  runs = new String[]{distance, "  ·  ", spawner.range() + "m range"};
                  colors = new int[]{WorldText.LABEL, WorldText.LABEL, spawner.inside() ? accent : WorldText.LABEL};
               } else {
                  runs = new String[]{distance};
                  colors = new int[]{WorldText.LABEL};
               }

               WorldText.draw(dl, screen[0], screen[1], name, runs, colors, this.fade(spawner.distance()));
            }
         }
      }
   }

   public static float[] labelAnchor(Minecraft mc, BlockPos pos, float screenW, float screenH) {
      float[] anchor = WorldLabel.project(mc, new Vec3(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5), screenW, screenH);
      if (anchor == null) {
         return null;
      } else {
         for (int corner = 0; corner < 4; corner++) {
            float[] top = WorldLabel.project(mc, new Vec3(pos.getX() + (corner & 1), pos.getY() + 1.0, pos.getZ() + (corner >> 1)), screenW, screenH);
            if (top != null) {
               anchor[1] = Math.min(anchor[1], top[1]);
            }
         }

         return anchor;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sRadius.get()) {
         Minecraft mc = Minecraft.getInstance();
         List<SpawnerNametagsModule.Shown> shown = this.inRange(mc);
         if (!shown.isEmpty()) {
            int accent = GuiPalette.abgr(WorldText.hudAccent()) & 16777215;

            for (SpawnerNametagsModule.Shown spawner : shown) {
               if (spawner.range() > 0) {
                  float alpha = (spawner.inside() ? 0.8F : 0.4F) * this.fade(spawner.distance());
                  int color = Math.round(alpha * 255.0F) << 24 | accent;
                  if (color >>> 24 >= 4) {
                     drawRing(Vec3.atCenterOf(spawner.pos()), spawner.range(), color);
                  }
               }
            }
         }
      }
   }

   private static void drawRing(Vec3 centre, double radius, int color) {
      Vec3 previous = null;

      for (int i = 0; i <= 64; i++) {
         double angle = (Math.PI * 2) * i / 64.0;
         Vec3 point = centre.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
         if (previous != null) {
            Gizmos.line(previous, point, color, 1.5F).setAlwaysOnTop();
         }

         previous = point;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Shown(BlockPos pos, double distance, String mob, int range, boolean inside) {
   }
}

