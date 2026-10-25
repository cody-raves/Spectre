package net.spectre.client.gui;

import com.example.client.mixin.AccessorLevelLoadingScreen;
import com.example.client.mixin.AccessorLoadingOverlay;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import java.util.Locale;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class LoadingScreens {
   private static final int BACKGROUND_RGB = 0x080f1d;
   private static final float FADE_OUT_SECONDS = 0.45F;
   private static final long ORIGIN = System.nanoTime();
   private static long lastActiveAt = -1L;
   private static float lastProgress;
   private static float lastAlpha;
   private static String lastStatus = "";
   private static float shownFill;

   private LoadingScreens() {
   }

   public static int backgroundColor(int argb) {
      return !SpectreTitleScreen.enabled() ? argb : argb & 0xFF000000 | 0x080f1d;
   }

   public static boolean hasWork() {
      if (!SpectreTitleScreen.enabled()) {
         return false;
      } else if (lastActiveAt >= 0L) {
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.gui.overlay() instanceof LoadingOverlay
            || mc.gui.screen() instanceof LevelLoadingScreen
            || mc.gui.screen() instanceof GenericMessageScreen
            || mc.gui.screen() instanceof ProgressScreen;
      }
   }

   public static void render() {
      if (SpectreTitleScreen.enabled()) {
         Minecraft mc = Minecraft.getInstance();
         long now = System.nanoTime();
         float alpha;
         float progress;
         String status;
         if (mc.gui.overlay() instanceof LoadingOverlay overlay) {
            AccessorLoadingOverlay state = (AccessorLoadingOverlay)overlay;
            alpha = reloadAlpha(state);
            progress = state.spectre$getProgress();
            status = "Loading";
         } else {
            Screen screen = mc.gui.screen();
            if (screen instanceof LevelLoadingScreen loading) {
               progress = ((AccessorLevelLoadingScreen)loading).spectre$getProgress();
               status = "Entering world";
            } else {
               if (!(screen instanceof GenericMessageScreen) && !(screen instanceof ProgressScreen)) {
                  drawFadingOut(now);
                  return;
               }

               progress = -1.0F;
               String title = screen.getTitle().getString();
               status = title.isBlank() ? "Loading" : title;
            }

            alpha = 1.0F;
         }

         lastActiveAt = now;
         lastProgress = progress;
         lastStatus = status;
         lastAlpha = alpha;
         draw(alpha, progress, status, now);
      }
   }

   private static void drawFadingOut(long now) {
      if (lastActiveAt >= 0L) {
         float elapsed = seconds(now - lastActiveAt);
         if (elapsed >= 0.45F) {
            lastActiveAt = -1L;
         } else {
            draw(lastAlpha * (1.0F - elapsed / 0.45F), lastProgress < 0.0F ? -1.0F : 1.0F, lastStatus, now);
         }
      }
   }

   private static float reloadAlpha(AccessorLoadingOverlay state) {
      long millis = Util.getMillis();
      long fadeOutStart = state.spectre$getFadeOutStart();
      long fadeInStart = state.spectre$getFadeInStart();
      float fadeOut = fadeOutStart > -1L ? (float)(millis - fadeOutStart) / 1000.0F : -1.0F;
      float fadeIn = fadeInStart > -1L ? (float)(millis - fadeInStart) / 500.0F : -1.0F;
      if (fadeOut >= 1.0F) {
         return 1.0F - clamp01(fadeOut - 1.0F);
      } else {
         return state.spectre$isFadeIn() ? clamp01(fadeIn) : 1.0F;
      }
   }

   private static void draw(float a, float progress, String status, long now) {
      if (!(a <= 0.001F)) {
         ImGuiIO io = ImGui.getIO();
         float sw = io.getDisplaySizeX();
         float sh = io.getDisplaySizeY();
         float s = ImGuiManager.getInstance().uiScale();
         float t = seconds(now - ORIGIN);
         float dt = Math.max(0.001F, io.getDeltaTime());
         ImDrawList dl = ImGui.getForegroundDrawList();
         SpectreBrand.backdrop(dl, sw, sh, t, a);
         float headSize = Math.min(sh * 0.32F, 310.0F * s);
         float cx = sw * 0.5F;
         float cy = sh * 0.36F;
         float target = progress >= 0.0F ? clamp01(progress) : (float)(t * 0.45 % 1.0);
         if (!(progress < 0.0F) && !(target < shownFill)) {
            shownFill = shownFill + (target - shownFill) * Math.min(1.0F, dt * 6.0F);
         } else {
            shownFill = target;
         }

         SpectreSplash.draw(dl, cx, cy, headSize, t, a, progress < 0 ? -1 : shownFill);

         ImFont big = ImGuiManager.getInstance().menuFont();
         ImFont small = ImGuiManager.getInstance().menuTagFont();
         float nameY = cy + headSize * 0.78F + 14.0F * s;
         SpectreDraw.drawSpaced(dl, big, cx, nameY, "SPECTRE", 7.0F * s, SpectreBrand.TEXT, a, true);
         float bigSize = big != null ? big.getFontSize() : 24.0F * s;
         float barW = 280.0F * s;
         float barY = nameY + bigSize + 16.0F * s;
         float line = Math.max(1.0F, 1.5F * s);
         float bx = cx - barW * 0.5F;
         dl.addLine(bx, barY, bx + barW, barY, SpectreDraw.col(SpectreBrand.ACCENT, 0.18F * a), line);
         float end = progress >= 0.0F ? bx + barW * shownFill : bx + barW * (0.5F + 0.5F * (float)Math.sin(t * 2.2F));
         float start = progress >= 0.0F ? bx : Math.max(bx, end - barW * 0.25F);
         dl.addLine(start, barY, end, barY, SpectreDraw.col(SpectreBrand.ACCENT, a), line);
         float d = 3.5F * s;
         dl.addQuadFilled(end, barY - d, end + d, barY, end, barY + d, end - d, barY, SpectreDraw.col(SpectreBrand.ACCENT, a));
         String line2 = progress >= 0.0F ? status + "  ·  " + Math.round(clamp01(progress) * 100.0F) + "%" : status;
         SpectreDraw.drawSpaced(dl, small, cx, barY + 12.0F * s, line2.toUpperCase(Locale.ROOT), 2.0F * s, SpectreBrand.MUTED, a, false);
      }
   }

   private static float seconds(long nanos) {
      return (float)nanos / 1.0E9F;
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }
}
