package net.spectre.client.module.impl.render;

import imgui.ImDrawList;
import imgui.ImGui;
import net.spectre.client.gui.SpectreDraw;
import net.spectre.client.gui.GuiPalette;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public final class LowHealthWarningModule extends ClientModule implements HudOverlayProvider {
   private static final String PULSE_HEARTBEAT = "Heartbeat";
   private static final String PULSE_BREATH = "Breath";
   private static final String PULSE_STEADY = "Steady";
   private static final String COLOR_THEME = "Theme";
   private static final String COLOR_MANUAL = "Manual";
   private static final int OPAQUE = -16777216;
   private static final int WHITE = -1;
   private static final float EDGE_LIFT = 0.5F;
   private static final float RISE_RATE = 5.0F;
   private static final float FALL_RATE = 2.6F;
   private static final float DEATH_RATE = 12.0F;
   private static final float CALM_BPM = 64.0F;
   private static final float PANIC_BPM = 150.0F;
   private static final float SECOND_BEAT = 0.2F;
   private static final float BEAT_WIDTH = 0.065F;
   private static final long GAP_NANOS = 250000000L;
   private static final float TAU = (float) (Math.PI * 2);
   private static final float[] BAND_REACH = new float[]{1.0F, 0.55F, 0.22F};
   private static final float[] BAND_WEIGHT = new float[]{0.5F, 0.34F, 0.3F};
   private final NumberSetting warnBelow = this.addSetting("warn-below", new NumberSetting("Warn Below", 7.0F, 2.0F, 20.0F).step(1.0F));
   private final BooleanSetting countAbsorption = this.addSetting("count-absorption", new BooleanSetting("Count Absorption", true));
   private final ModeSetting pulse = this.addSetting("pulse", new ModeSetting("Pulse", "Heartbeat", "Heartbeat", "Breath", "Steady"));
   private final NumberSetting intensity = this.addSetting("intensity", new NumberSetting("Intensity", 0.7F, 0.2F, 1.0F).step(0.05F));
   private final NumberSetting reach = this.addSetting("reach", new NumberSetting("Reach", 0.16F, 0.06F, 0.35F).step(0.01F));
   private final BooleanSetting edgeLine = this.addSetting("edge-line", new BooleanSetting("Edge Line", true));
   private final ModeSetting colorMode = this.addSetting("color", new ModeSetting("Color", "Theme", "Theme", "Manual"));
   private final ColorSetting glowColor = this.addSetting("glow-color", new ColorSetting("Glow Color", -13338378));
   private final ColorSetting edgeColor = this.addSetting("edge-color", new ColorSetting("Edge Color", -5650177));
   private float level;
   private float danger;
   private float phase;
   private long lastFrame;

   public LowHealthWarningModule() {
      super("low-health-warning", "Low Health Warning", "Flashes the screen edges when your health is low.", ModuleCategory.RENDER);
      this.glowColor.visibleWhen(this::manual);
      this.edgeColor.visibleWhen(() -> this.edgeLine.get() && this.manual());
   }

   @Override
   protected void onEnable() {
      this.level = 0.0F;
      this.danger = 0.0F;
      this.phase = 0.0F;
      this.lastFrame = 0L;
   }

   @Override
   protected void onDisable() {
      this.level = 0.0F;
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      Minecraft mc = Minecraft.getInstance();
      float dt = Math.min(Math.max(ImGui.getIO().getDeltaTime(), 0.0F), 0.1F);
      long now = System.nanoTime();
      if (now - this.lastFrame > 250000000L) {
         this.level = 0.0F;
         this.phase = 0.0F;
      }

      this.lastFrame = now;
      LocalPlayer player = mc.player;
      boolean dead = player == null || player.isDeadOrDying();
      float want = 0.0F;
      if (!dead && !mc.gui.hud.isHidden() && !player.isSpectator() && !player.getAbilities().invulnerable) {
         float health = player.getHealth() + (this.countAbsorption.get() ? player.getAbsorptionAmount() : 0.0F);
         float limit = this.warnBelow.get();
         if (health < limit) {
            want = 1.0F;
            this.danger = Math.max(0.0F, Math.min(1.0F, (limit - health) / limit));
         }
      }

      float rate = dead ? 12.0F : (want > this.level ? 5.0F : 2.6F);
      this.level = this.level + (want - this.level) * (1.0F - (float)Math.exp(-rate * dt));
      if (this.level < 0.003F && want == 0.0F) {
         this.level = 0.0F;
         this.phase = 0.0F;
      } else {
         float bpm = 64.0F + 86.0F * this.danger;
         if (this.pulse.is("Breath")) {
            bpm *= 0.5F;
         }

         this.phase = (this.phase + dt * bpm / 60.0F) % 1.0F;
         float beat = this.beat();
         float shown = this.level * this.level * (3.0F - 2.0F * this.level);
         float strength = this.intensity.get() * shown * (0.45F + 0.55F * this.danger);
         float depth = this.reach.get() * Math.min(screenW, screenH) * (1.0F + 0.12F * beat);
         boolean manual = this.manual();
         int accent = manual ? 0 : CosmeticAccent.argb();
         int base = GuiPalette.abgr(manual ? this.glowColor.argb() | 0xFF000000 : accent);

         for (int i = 0; i < BAND_REACH.length; i++) {
            float alpha = strength * BAND_WEIGHT[i] * (0.6F + 0.4F * beat);
            frame(dl, screenW, screenH, depth * BAND_REACH[i], withAlpha(base, alpha));
         }

         if (this.edgeLine.get()) {
            float line = Math.max(2.0F, depth * 0.07F);
            float alpha = this.intensity.get() * shown * (0.2F + 0.55F * beat);
            int edge = manual ? this.edgeColor.argb() | 0xFF000000 : ARGB.srgbLerp(0.5F, accent, -1);
            frame(dl, screenW, screenH, line, SpectreDraw.col(edge, alpha));
         }
      }
   }

   private float beat() {
      if (this.pulse.is("Steady")) {
         return 0.35F;
      } else {
         return this.pulse.is("Breath")
            ? 0.5F - 0.5F * (float)Math.cos((float) (Math.PI * 2) * this.phase)
            : Math.min(1.0F, bump(this.phase, 0.0F) + 0.62F * bump(this.phase, 0.2F));
      }
   }

   private static float bump(float at, float center) {
      float d = Math.abs(at - center);
      d = Math.min(d, 1.0F - d) / 0.065F;
      return (float)Math.exp(-d * d);
   }

   private boolean manual() {
      return this.colorMode.is("Manual");
   }

   private static int withAlpha(int abgr, float alpha) {
      int a = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha)));
      return abgr & 16777215 | a << 24;
   }

   private static void frame(ImDrawList dl, float w, float h, float depth, int edge) {
      if (!(depth < 1.0F) && edge >>> 24 != 0) {
         int clear = edge & 16777215;
         dl.addRectFilledMultiColor(0.0F, 0.0F, w, depth, edge, edge, clear, clear);
         dl.addRectFilledMultiColor(0.0F, h - depth, w, h, clear, clear, edge, edge);
         dl.addRectFilledMultiColor(0.0F, 0.0F, depth, h, edge, clear, clear, edge);
         dl.addRectFilledMultiColor(w - depth, 0.0F, w, h, clear, edge, edge, clear);
      }
   }
}
