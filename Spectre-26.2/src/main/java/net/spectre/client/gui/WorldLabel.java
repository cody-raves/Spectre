package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class WorldLabel {
   private static final int PRIMARY = -1;
   private static final int SECONDARY = -1315861;

   private WorldLabel() {
   }

   public static float[] project(Minecraft mc, Vec3 world, float screenW, float screenH) {
      Camera camera = mc.gameRenderer.mainCamera();
      if (camera != null && camera.isInitialized() && TracerEngine.isInFront(camera, world)) {
         Vec3 ndc = mc.gameRenderer.projectPointToScreen(world);
         if (Double.isFinite(ndc.x) && Double.isFinite(ndc.y)) {
            float x = (float)(ndc.x + 1.0) * 0.5F * screenW;
            float y = (float)(1.0 - ndc.y) * 0.5F * screenH;
            return !(x < -200.0F) && !(x > screenW + 200.0F) && !(y < -100.0F) && !(y > screenH + 100.0F) ? new float[]{x, y} : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void drawText(ImDrawList dl, float cx, float bottom, String primary, String secondary, float alpha) {
      ImGuiManager fonts = ImGuiManager.getInstance();
      ImFont main = fonts.hudTextFont();
      ImFont small = fonts.hudLabelFont();
      int mainPx = fontPx(main);
      int smallPx = fontPx(small);
      float padY = 5.0F;
      float gap = 6.0F;
      float w1 = HudChrome.width(main, mainPx, primary);
      float w2 = secondary == null ? 0.0F : HudChrome.width(small, smallPx, secondary);
      float x = Math.round(cx - (w1 + (secondary == null ? 0.0F : gap + w2)) * 0.5F);
      float y = Math.round(bottom - padY - mainPx);
      HudChrome.text(dl, main, mainPx, x, y, fade(-1, alpha), primary, true);
      if (secondary != null) {
         HudChrome.text(
            dl, small, smallPx, Math.round(x + w1 + gap), y + HudChrome.baselineOffset(main, mainPx, small, smallPx), fade(-1315861, alpha), secondary, true
         );
      }
   }

   private static int fontPx(ImFont font) {
      return font != null ? Math.round(font.getFontSize()) : 13;
   }

   private static int fade(int abgr, float alpha) {
      int a = Math.round((abgr >>> 24) * Math.max(0.0F, Math.min(1.0F, alpha)));
      return a << 24 | abgr & 16777215;
   }
}
