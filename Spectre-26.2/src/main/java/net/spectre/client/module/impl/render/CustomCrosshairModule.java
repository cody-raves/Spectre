package net.spectre.client.module.impl.render;

import imgui.ImDrawList;
import imgui.ImGui;
import net.spectre.client.gui.SpectreTheme;
import net.spectre.client.gui.SpectreWidgets;
import net.spectre.client.gui.GuiPalette;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class CustomCrosshairModule extends ClientModule implements HudOverlayProvider {
   public static volatile CustomCrosshairModule active;
   private static final String STYLE_CROSS = "Cross";
   private static final String STYLE_T = "T";
   private static final String STYLE_X = "X";
   private static final String STYLE_DOT = "Dot";
   private static final String STYLE_CIRCLE = "Circle";
   private static final String COLOR_CUSTOM = "Custom";
   private static final String COLOR_ACCENT = "Accent";
   private static final int OUTLINE = -1946157056;
   private static final int TRACK_ARGB = -870967267;
   private static final float BAR_HALF_WIDTH = 10.0F;
   private static final float BAR_HEIGHT = 3.0F;
   private static final float DIAGONAL = 0.70710677F;
   private static final double FULL_SPREAD_SPEED = 0.28;
   private static final float TINT_RATE = 14.0F;
   private static final float SPREAD_RATE = 12.0F;
   private static final float FADE_RATE = 18.0F;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Cross", "Cross", "T", "X", "Dot", "Circle"));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 6.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting gap = this.addSetting("gap", new NumberSetting("Gap", 2.0F, 0.0F, 16.0F).step(1.0F));
   private final NumberSetting thickness = this.addSetting("thickness", new NumberSetting("Thickness", 2.0F, 1.0F, 5.0F).step(1.0F));
   private final BooleanSetting centerDot = this.addSetting("center-dot", new BooleanSetting("Center Dot", false));
   private final BooleanSetting outline = this.addSetting("outline", new BooleanSetting("Outline", true));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", "Custom", "Custom", "Accent"));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Custom Color", -657929));
   private final BooleanSetting targetTint = this.addSetting("target-tint", new BooleanSetting("Target Tint", false));
   private final ColorSetting targetColor = this.addSetting("target-color", new ColorSetting("Target Color", -47814));
   private final BooleanSetting dynamicGap = this.addSetting("dynamic-gap", new BooleanSetting("Dynamic Gap", false));
   private final NumberSetting spread = this.addSetting("spread", new NumberSetting("Spread", 4.0F, 1.0F, 12.0F).step(1.0F));
   private float tint;
   private float extraGap;
   private float barAlpha;
   private float barFill;

   public CustomCrosshairModule() {
      super("custom-crosshair", "Custom Crosshair", "Draws a configurable crosshair in place of the vanilla one.", ModuleCategory.VISUALS);
      this.gap.visibleWhen(this::hasArms);
      this.centerDot.visibleWhen(() -> !this.style.is("Dot"));
      this.color.visibleWhen(() -> this.colorMode.is("Custom"));
      this.targetColor.visibleWhen(this.targetTint::get);
      this.spread.visibleWhen(() -> this.dynamicGap.get() && !this.style.is("Dot"));
   }

   @Override
   protected void onEnable() {
      this.tint = 0.0F;
      this.extraGap = 0.0F;
      this.barAlpha = 0.0F;
      this.barFill = 1.0F;
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && isVisible(mc)) {
         float dt = Math.min(Math.max(ImGui.getIO().getDeltaTime(), 0.0F), 0.1F);
         float scale = ImGuiManager.getInstance().uiScale();
         this.updateMotion(mc, player, dt);
         CustomCrosshairModule.Geometry geometry = this.geometry(screenW * 0.5F, screenH * 0.5F, scale);
         int fill = GuiPalette.abgr(this.currentColor());
         float fillAlpha = (fill >>> 24 & 0xFF) / 255.0F;
         float edge = this.outline.get() ? Math.max(1.0F, (float)Math.round(scale)) : 0.0F;
         if (edge > 0.0F) {
            this.drawShape(dl, geometry, SpectreWidgets.withAlpha(-1946157056, fillAlpha), edge);
         }

         this.drawShape(dl, geometry, fill, 0.0F);
         this.drawCooldown(dl, mc, player, geometry, fill, edge, scale, dt);
      }
   }

   private static boolean isVisible(Minecraft mc) {
      if (mc.level == null || mc.gameMode == null || mc.gui.hud.isHidden()) {
         return false;
      } else if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
         return false;
      } else if (!mc.options.getCameraType().isFirstPerson()) {
         return false;
      } else {
         return mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)
            ? false
            : mc.gameMode.getPlayerMode() != GameType.SPECTATOR || spectatorCanInteract(mc);
      }
   }

   private static boolean spectatorCanInteract(Minecraft mc) {
      HitResult hit = mc.hitResult;
      if (hit instanceof EntityHitResult entityHit) {
         return entityHit.getEntity() instanceof MenuProvider;
      } else if (hit instanceof BlockHitResult blockHit && hit.getType() == Type.BLOCK) {
         BlockPos pos = blockHit.getBlockPos();
         return mc.level.getBlockState(pos).getMenuProvider(mc.level, pos) != null;
      } else {
         return false;
      }
   }

   private void updateMotion(Minecraft mc, LocalPlayer player, float dt) {
      boolean onTarget = this.targetTint.get() && mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive();
      this.tint = approach(this.tint, onTarget ? 1.0F : 0.0F, 14.0F, dt);
      float wanted = 0.0F;
      if (this.dynamicGap.get()) {
         double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
         float moving = (float)Math.min(1.0, moved / 0.28);
         float swing = 1.0F - player.getAttackStrengthScale(0.0F);
         wanted = this.spread.get() * Math.max(moving, swing);
      }

      this.extraGap = approach(this.extraGap, wanted, 12.0F, dt);
   }

   private static float approach(float current, float target, float rate, float dt) {
      float next = current + (target - current) * (1.0F - (float)Math.exp(-rate * dt));
      return Math.abs(target - next) < 0.001F ? target : next;
   }

   private int currentColor() {
      int base = this.colorMode.is("Accent") ? CosmeticAccent.argb() : this.color.argb();
      return this.tint <= 0.0F ? base : SpectreTheme.lerpColor(base, this.targetColor.argb(), this.tint);
   }

   private boolean hasArms() {
      return this.style.is("Cross") || this.style.is("T") || this.style.is("X");
   }

   private CustomCrosshairModule.Geometry geometry(float cx, float cy, float scale) {
      float thick = Math.max(1.0F, (float)Math.round(this.thickness.get() * scale));
      float length = Math.max(1.0F, (float)Math.round(this.size.get() * scale));
      float baseGap = this.hasArms() ? this.gap.get() : 0.0F;
      float open = Math.round((baseGap + this.extraGap) * scale);
      float maxExtra = this.dynamicGap.get() && !this.style.is("Dot") ? this.spread.get() : 0.0F;
      float below = this.reachBelow(length, Math.round((baseGap + maxExtra) * scale), thick);
      return new CustomCrosshairModule.Geometry(cx, cy, length, open, thick, below);
   }

   private float reachBelow(float length, float open, float thick) {
      String var5 = this.style.get();

      float below = switch (var5) {
         case "Dot" -> length * 0.5F;
         case "Circle" -> length + open + thick * 0.5F;
         case "X" -> (open + length) * 0.70710677F + thick * 0.5F;
         default -> open + length + thick * 0.5F;
      };
      return this.centerDot.get() ? Math.max(below, thick * 0.5F) : below;
   }

   private boolean armsJoined(CustomCrosshairModule.Geometry g) {
      return (this.style.is("Cross") || this.style.is("T")) && g.open() < 1.0F;
   }

   private void drawShape(ImDrawList dl, CustomCrosshairModule.Geometry g, int color, float grow) {
      String shape = this.style.get();
      switch (shape) {
         case "Dot":
            dl.addCircleFilled(g.cx(), g.cy(), Math.max(1.0F, g.size() * 0.5F) + grow, color, 0);
            break;
         case "Circle":
            dl.addCircle(g.cx(), g.cy(), g.size() + g.open(), color, 0, g.thick() + grow * 2.0F);
            break;
         case "X":
            drawDiagonals(dl, g, color, grow);
            break;
         default:
            drawArms(dl, g, color, grow, !this.style.is("T"));
      }

      if (this.centerDot.get() && !this.style.is("Dot") && !this.armsJoined(g)) {
         float x0 = (float)Math.floor(g.cx() - g.thick() * 0.5F);
         float y0 = (float)Math.floor(g.cy() - g.thick() * 0.5F);
         rect(dl, x0, y0, x0 + g.thick(), y0 + g.thick(), color, grow);
      }
   }

   private static void drawArms(ImDrawList dl, CustomCrosshairModule.Geometry g, int color, float grow, boolean top) {
      float x0 = (float)Math.floor(g.cx() - g.thick() * 0.5F);
      float x1 = x0 + g.thick();
      float y0 = (float)Math.floor(g.cy() - g.thick() * 0.5F);
      float y1 = y0 + g.thick();
      float near = g.open();
      float far = g.open() + g.size();
      if (near < 1.0F) {
         rect(dl, x0 - far, y0, x1 + far, y1, color, grow);
         dl.addRectFilled(x0 - grow, y1 + grow, x1 + grow, y1 + far + grow, color);
         if (top) {
            dl.addRectFilled(x0 - grow, y0 - far - grow, x1 + grow, y0 - grow, color);
         }
      } else {
         rect(dl, x0 - far, y0, x0 - near, y1, color, grow);
         rect(dl, x1 + near, y0, x1 + far, y1, color, grow);
         rect(dl, x0, y1 + near, x1, y1 + far, color, grow);
         if (top) {
            rect(dl, x0, y0 - far, x1, y0 - near, color, grow);
         }
      }
   }

   private static void drawDiagonals(ImDrawList dl, CustomCrosshairModule.Geometry g, int color, float grow) {
      float near = Math.max(0.0F, g.open() - grow) * 0.70710677F;
      float far = (g.open() + g.size() + grow) * 0.70710677F;
      float width = g.thick() + grow * 2.0F;

      for (int sx = -1; sx <= 1; sx += 2) {
         for (int sy = -1; sy <= 1; sy += 2) {
            dl.addLine(g.cx() + sx * near, g.cy() + sy * near, g.cx() + sx * far, g.cy() + sy * far, color, width);
         }
      }
   }

   private static void rect(ImDrawList dl, float x0, float y0, float x1, float y1, int color, float grow) {
      dl.addRectFilled(x0 - grow, y0 - grow, x1 + grow, y1 + grow, color);
   }

   private void drawCooldown(ImDrawList dl, Minecraft mc, LocalPlayer player, CustomCrosshairModule.Geometry g, int fill, float edge, float scale, float dt) {
      boolean wanted = mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR;
      float charge = player.getAttackStrengthScale(0.0F);
      boolean ready = wanted && readyOnTarget(mc, player, charge);
      this.barFill = ready ? 1.0F : Math.min(1.0F, charge);
      this.barAlpha = approach(this.barAlpha, !wanted || !ready && !(charge < 1.0F) ? 0.0F : 1.0F, 18.0F, dt);
      if (!(this.barAlpha < 0.01F)) {
         float w = Math.max(12.0F, 2.0F * Math.round(10.0F * scale));
         float h = Math.max(2.0F, (float)Math.round(3.0F * scale));
         float x0 = (float)Math.floor(g.cx() - w * 0.5F);
         float y0 = Math.round(g.cy() + g.below() + edge + 5.0F * scale);
         float alpha = this.barAlpha * (fill >>> 24 & 0xFF) / 255.0F;
         if (edge > 0.0F) {
            dl.addRectFilled(x0 - edge, y0 - edge, x0 + w + edge, y0 + h + edge, SpectreWidgets.withAlpha(-1946157056, alpha), h * 0.5F + edge);
         }

         dl.addRectFilled(x0, y0, x0 + w, y0 + h, SpectreWidgets.withAlpha(GuiPalette.abgr(-870967267), alpha), h * 0.5F);
         float filled = Math.round(w * this.barFill);
         if (filled >= 1.0F) {
            dl.addRectFilled(x0, y0, x0 + filled, y0 + h, SpectreWidgets.withAlpha(fill | 0xFF000000, alpha), h * 0.5F);
         }
      }
   }

   private static boolean readyOnTarget(Minecraft mc, LocalPlayer player, float charge) {
      if (charge < 1.0F || !(mc.crosshairPickEntity instanceof LivingEntity target && target.isAlive())) {
         return false;
      } else if (player.getCurrentItemAttackStrengthDelay() <= 5.0F) {
         return false;
      } else {
         AttackRange range = player.getActiveItem().get(DataComponents.ATTACK_RANGE);
         return range == null || mc.hitResult == null || range.isInRange(player, mc.hitResult.getLocation());
      }
   }

   @Environment(EnvType.CLIENT)
   private record Geometry(float cx, float cy, float size, float open, float thick, float below) {
   }
}
