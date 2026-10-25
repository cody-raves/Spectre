package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImGui;
import net.spectre.client.config.Surface;
import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SurfaceBackground {
   private SurfaceBackground() {
   }

   public static float bannerHeight(Surface surface) {
      return surface.isBanner() && isVisible(surface) ? SpectreWidgets.s(surface.headerHeight.get()) : 0.0F;
   }

   public static boolean hasImageBanner(Surface surface) {
      return surface.isImage() && bannerHeight(surface) > 0.0F;
   }

   public static void draw(ImDrawList dl, Surface surface, float x, float y, float w, float h, float rounding) {
      draw(dl, surface, x, y, w, h, rounding, 1.0F);
   }

   public static void draw(ImDrawList dl, Surface surface, float x, float y, float w, float h, float rounding, float alpha) {
      if (!(alpha <= 0.001F) && isVisible(surface)) {
         boolean banner = surface.isBanner();
         float areaH = banner ? Math.min(h, SpectreWidgets.s(surface.headerHeight.get())) : h;
         if (surface.isImage()) {
            drawImage(dl, surface, x, y, w, areaH, rounding, banner, alpha, surface.imageScale.get());
         } else {
            drawPattern(dl, surface, x, y, w, areaH, rounding, banner, alpha);
         }
      }
   }

   public static void drawDisc(ImDrawList dl, Surface surface, float cx, float cy, float r, float alpha) {
      if (!(alpha <= 0.001F) && isVisible(surface)) {
         float x = cx - r;
         float y = cy - r;
         float size = 2.0F * r;
         if (surface.isImage()) {
            drawImage(dl, surface, x, y, size, size, r, false, alpha, Math.max(1.0F, surface.imageScale.get()));
         } else {
            drawPattern(dl, surface, x, y, size, size, r, false, alpha);
         }
      }
   }

   private static boolean isVisible(Surface surface) {
      return surface.background.is("None") ? false : !surface.isImage() || ThemeImages.get().texture(surface.image.get()) != null;
   }

   private static void drawImage(ImDrawList dl, Surface surface, float x, float y, float w, float h, float rounding, boolean banner, float alpha, float scale) {
      ThemeImages.Texture texture = ThemeImages.get().texture(surface.image.get());
      if (texture != null) {
         float[] image = surface.isScreenMapped()
            ? place(texture, surface, 0.0F, 0.0F, ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY(), scale)
            : place(texture, surface, x, y, w, h, scale);
         drawClipped(
            dl, texture.id(), image, x, y, w, h, SpectreWidgets.c(SpectreWidgets.withAlpha(-1, surface.imageOpacity.get() * alpha)), rounding, banner ? 48 : 240
         );
         if (banner) {
            int fill = HudChrome.scaleAlpha(GuiPalette.fill(surface), alpha);
            int clear = fill & 16777215;
            dl.addRectFilledMultiColor(
               x, y + h * 0.3F, x + w, y + h + 1.0F, SpectreWidgets.c(clear), SpectreWidgets.c(clear), SpectreWidgets.c(fill), SpectreWidgets.c(fill)
            );
         }
      }
   }

   public static float[] coverUv(int imageW, int imageH, float boxW, float boxH, float focus) {
      float imageAspect = (float)imageW / Math.max(1, imageH);
      float boxAspect = boxW / Math.max(1.0F, boxH);
      if (imageAspect > boxAspect) {
         float span = boxAspect / imageAspect;
         float u0 = (1.0F - span) * 0.5F;
         return new float[]{u0, 0.0F, u0 + span, 1.0F};
      } else {
         float span = imageAspect / boxAspect;
         float v0 = (1.0F - span) * Math.max(0.0F, Math.min(1.0F, focus));
         return new float[]{0.0F, v0, 1.0F, v0 + span};
      }
   }

   public static float[] place(int imageW, int imageH, float frameX, float frameY, float frameW, float frameH, float scale, float fx, float fy) {
      float k = Math.max(frameW / Math.max(1, imageW), frameH / Math.max(1, imageH)) * scale;
      float w = imageW * k;
      float h = imageH * k;
      float x0 = frameX + (frameW - w) * fx;
      float y0 = frameY + (frameH - h) * fy;
      return new float[]{x0, y0, x0 + w, y0 + h};
   }

   private static float[] place(ThemeImages.Texture texture, Surface surface, float x, float y, float w, float h) {
      return place(texture, surface, x, y, w, h, surface.imageScale.get());
   }

   private static float[] place(ThemeImages.Texture texture, Surface surface, float x, float y, float w, float h, float scale) {
      return place(texture.width(), texture.height(), x, y, w, h, scale, surface.imageX.get(), surface.imageY.get());
   }

   private static void drawClipped(ImDrawList dl, int textureId, float[] image, float x, float y, float w, float h, int tint, float rounding, int corners) {
      float x0 = Math.max(x, image[0]);
      float y0 = Math.max(y, image[1]);
      float x1 = Math.min(x + w, image[2]);
      float y1 = Math.min(y + h, image[3]);
      if (!(x1 <= x0) && !(y1 <= y0)) {
         float iw = image[2] - image[0];
         float ih = image[3] - image[1];
         float u0 = (x0 - image[0]) / iw;
         float v0 = (y0 - image[1]) / ih;
         float u1 = (x1 - image[0]) / iw;
         float v1 = (y1 - image[1]) / ih;
         boolean left = x0 - x <= rounding;
         boolean top = y0 - y <= rounding;
         boolean right = x + w - x1 <= rounding;
         boolean bottom = y + h - y1 <= rounding;
         int reached = (left && top ? 16 : 0) | (right && top ? 32 : 0) | (left && bottom ? 64 : 0) | (right && bottom ? 128 : 0);
         int rounded = corners & reached;
         if (rounding > 0.0F && rounded != 0) {
            dl.addImageRounded(textureId, x0, y0, x1, y1, u0, v0, u1, v1, tint, rounding, rounded);
         } else {
            dl.addImage(textureId, x0, y0, x1, y1, u0, v0, u1, v1, tint);
         }
      }
   }

   public static void drawPart(
      ImDrawList dl,
      Surface surface,
      float boxX,
      float boxY,
      float boxW,
      float boxH,
      float x,
      float y,
      float w,
      float h,
      float alpha,
      float rounding,
      int cornerFlags
   ) {
      if (!(alpha <= 0.001F) && !(w <= 0.0F) && !(h <= 0.0F) && !(boxW <= 0.0F) && !(boxH <= 0.0F) && isVisible(surface)) {
         float screenW = ImGui.getIO().getDisplaySizeX();
         float screenH = ImGui.getIO().getDisplaySizeY();
         boolean screen = surface.isScreenMapped();
         if (surface.isImage()) {
            ThemeImages.Texture texture = ThemeImages.get().texture(surface.image.get());
            if (texture != null) {
               float[] image = screen ? place(texture, surface, 0.0F, 0.0F, screenW, screenH) : place(texture, surface, boxX, boxY, boxW, boxH);
               drawClipped(
                  dl, texture.id(), image, x, y, w, h, SpectreWidgets.c(SpectreWidgets.withAlpha(-1, surface.imageOpacity.get() * alpha)), rounding, cornerFlags
               );
            }
         } else {
            SurfaceBackground.Lines lines = new SurfaceBackground.Lines(
               dl, x, y, w, h, 0.0F, surface.lineOpacity.get() * alpha, false, screen ? x : x - boxX, screen ? y : y - boxY, screen ? screenW : boxW, false
            );
            dl.pushClipRect(x, y, x + w, y + h, true);
            drawLines(surface, lines);
            dl.popClipRect();
         }
      }
   }

   private static void drawPattern(ImDrawList dl, Surface surface, float x, float y, float w, float h, float rounding, boolean banner, float alpha) {
      boolean screen = surface.isScreenMapped();
      SurfaceBackground.Lines lines = new SurfaceBackground.Lines(
         dl,
         x,
         y,
         w,
         h,
         rounding,
         surface.lineOpacity.get() * alpha,
         banner,
         screen ? x : 0.0F,
         screen ? y : 0.0F,
         screen ? ImGui.getIO().getDisplaySizeX() : w,
         true
      );
      drawLines(surface, lines);
   }

   private static void drawLines(Surface surface, SurfaceBackground.Lines lines) {
      ThemeStore theme = ThemeStore.get();
      float time = theme.animateLines.get() ? (float)ImGui.getTime() * GuiPalette.animationSpeed() : 0.0F;
      String var4 = surface.background.get();
      switch (var4) {
         case "Flow":
            flow(lines, time);
            break;
         case "Topographic":
            topographic(lines, time);
            break;
         case "Rings":
            rings(lines, time);
            break;
         case "Grid":
            grid(lines, time);
      }
   }

   private static void flow(SurfaceBackground.Lines lines, float time) {
      float step = SpectreWidgets.s(6.0F);
      float gap = SpectreWidgets.s(10.0F);
      float phase = time * 0.25F;
      int first = (int)Math.floor((lines.originY - SpectreWidgets.s(40.0F)) / gap);
      int last = (int)Math.ceil((lines.originY + lines.h + SpectreWidgets.s(40.0F)) / gap);

      for (int i = first; i <= last; i++) {
         float prevX = 0.0F;
         float prevY = 0.0F;

         for (float lx = 0.0F; lx <= lines.w + step; lx += step) {
            float u = (lines.originX + lx) / SpectreWidgets.s(1.0F);
            float worldY = i * gap
               + (float)Math.sin(u * 0.018F + i * 0.35F + phase) * SpectreWidgets.s(14.0F)
               + (float)Math.sin(u * 0.007F - i * 0.2F - phase * 0.6F) * SpectreWidgets.s(10.0F);
            float ly = worldY - lines.originY;
            if (lx > 0.0F) {
               lines.segment(prevX, prevY, lx, ly);
            }

            prevX = lx;
            prevY = ly;
         }
      }
   }

   private static void rings(SurfaceBackground.Lines lines, float time) {
      float cx = lines.worldW + SpectreWidgets.s(10.0F) - lines.originX;
      float cy = -SpectreWidgets.s(30.0F) - lines.originY;
      float gap = SpectreWidgets.s(14.0F);
      float offset = time * SpectreWidgets.s(4.0F) % gap;
      float nearest = (float)Math.hypot(Math.max(0.0F, Math.max(-cx, cx - lines.w)), Math.max(0.0F, Math.max(-cy, cy - lines.h)));
      float farthest = (float)Math.hypot(Math.max(Math.abs(cx), Math.abs(cx - lines.w)), Math.max(Math.abs(cy), Math.abs(cy - lines.h)));
      float start = SpectreWidgets.s(24.0F) + offset;
      if (nearest > start) {
         start += (float)Math.floor((nearest - start) / gap) * gap;
      }

      for (float r = start; r <= farthest; r += gap) {
         int segments = Math.max(24, (int)(r * 0.35F));
         float prevX = cx + r;
         float prevY = cy;

         for (int i = 1; i <= segments; i++) {
            double a = (Math.PI * 2) * i / segments;
            float px = cx + (float)Math.cos(a) * r;
            float py = cy + (float)Math.sin(a) * r;
            lines.segment(prevX, prevY, px, py);
            prevX = px;
            prevY = py;
         }
      }
   }

   private static void grid(SurfaceBackground.Lines lines, float time) {
      float cell = SpectreWidgets.s(16.0F);
      float step = SpectreWidgets.s(8.0F);
      float drift = time * SpectreWidgets.s(3.0F) % cell;
      float startX = floorMod(drift - lines.originX, cell);
      float startY = floorMod(drift - lines.originY, cell);

      for (float gx = startX; gx <= lines.w; gx += cell) {
         for (float gy = 0.0F; gy < lines.h; gy += step) {
            lines.segment(gx, gy, gx, gy + step);
         }
      }

      for (float gy = startY; gy <= lines.h; gy += cell) {
         for (float gx = 0.0F; gx < lines.w; gx += step) {
            lines.segment(gx, gy, gx + step, gy);
         }
      }
   }

   private static void topographic(SurfaceBackground.Lines lines, float time) {
      float cell = SpectreWidgets.s(6.0F);
      int cols = (int)Math.ceil(lines.w / cell) + 1;
      int rows = (int)Math.ceil(lines.h / cell) + 1;
      float[] field = new float[cols * rows];
      float drift = time * 0.15F;

      for (int j = 0; j < rows; j++) {
         for (int i = 0; i < cols; i++) {
            float u = (lines.originX + i * cell) / SpectreWidgets.s(1.0F);
            float v = (lines.originY + j * cell) / SpectreWidgets.s(1.0F);
            field[j * cols + i] = (float)(
               Math.sin(u * 0.021 + Math.sin(v * 0.03 + drift) * 1.6)
                  + Math.cos(v * 0.028 + Math.sin(u * 0.013 - drift) * 2.1)
                  + 0.6 * Math.sin((u + v) * 0.017 + drift)
            );
         }
      }

      for (float level = -2.4F; level <= 2.4F; level += 0.4F) {
         for (int j = 0; j < rows - 1; j++) {
            for (int i = 0; i < cols - 1; i++) {
               contourCell(lines, field, cols, i, j, cell, level);
            }
         }
      }
   }

   private static void contourCell(SurfaceBackground.Lines lines, float[] f, int cols, int i, int j, float cell, float level) {
      float a = f[j * cols + i];
      float b = f[j * cols + i + 1];
      float d = f[(j + 1) * cols + i];
      float cc = f[(j + 1) * cols + i + 1];
      int index = (a > level ? 8 : 0) | (b > level ? 4 : 0) | (cc > level ? 2 : 0) | (d > level ? 1 : 0);
      if (index != 0 && index != 15) {
         float x = i * cell;
         float y = j * cell;
         float tx = x + cell * t(a, b, level);
         float rx = x + cell;
         float ry = y + cell * t(b, cc, level);
         float bx = x + cell * t(d, cc, level);
         float by = y + cell;
         float ly = y + cell * t(a, d, level);
         switch (index) {
            case 1:
            case 14:
               lines.segment(x, ly, bx, by);
               break;
            case 2:
            case 13:
               lines.segment(bx, by, rx, ry);
               break;
            case 3:
            case 12:
               lines.segment(x, ly, rx, ry);
               break;
            case 4:
            case 11:
               lines.segment(tx, y, rx, ry);
               break;
            case 5:
               lines.segment(x, ly, tx, y);
               lines.segment(bx, by, rx, ry);
               break;
            case 6:
            case 9:
               lines.segment(tx, y, bx, by);
               break;
            case 7:
            case 8:
               lines.segment(x, ly, tx, y);
               break;
            case 10:
               lines.segment(tx, y, rx, ry);
               lines.segment(x, ly, bx, by);
         }
      }
   }

   private static float t(float from, float to, float level) {
      float span = to - from;
      return Math.abs(span) < 1.0E-6F ? 0.5F : (level - from) / span;
   }

   private static float floorMod(float value, float mod) {
      float r = value % mod;
      return r < 0.0F ? r + mod : r;
   }

   @Environment(EnvType.CLIENT)
   private static final class Lines {
      final ImDrawList dl;
      final float x;
      final float y;
      final float w;
      final float h;
      final float edge;
      final float originX;
      final float originY;
      final float worldW;
      final boolean banner;
      final int color;

      Lines(
         ImDrawList dl,
         float x,
         float y,
         float w,
         float h,
         float rounding,
         float opacity,
         boolean banner,
         float originX,
         float originY,
         float worldW,
         boolean softEdges
      ) {
         this.dl = dl;
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.banner = banner;
         this.originX = originX;
         this.originY = originY;
         this.worldW = worldW;
         this.edge = softEdges ? Math.max(SpectreWidgets.s(8.0F), rounding) : 0.0F;
         float strength = banner ? 0.24F : 0.16F;
         this.color = SpectreWidgets.withAlpha(SpectreTheme.lerpColor(-1, GuiPalette.accent(), 0.25F), strength * opacity);
      }

      void segment(float ax, float ay, float bx, float by) {
         float mx = (ax + bx) * 0.5F;
         float my = (ay + by) * 0.5F;
         if (!(my < 0.0F) && !(my > this.h) && !(mx < 0.0F) && !(mx > this.w)) {
            float fade = 1.0F;
            if (this.banner) {
               fade = 1.0F - my / this.h;
               fade *= fade;
            } else if (this.edge > 0.0F) {
               fade *= Math.min(1.0F, Math.min(my, this.h - my) / this.edge);
            }

            if (this.edge > 0.0F) {
               fade *= Math.min(1.0F, Math.min(mx, this.w - mx) / this.edge);
            }

            if (!(fade <= 0.02F)) {
               this.dl.addLine(this.x + ax, this.y + ay, this.x + bx, this.y + by, SpectreWidgets.c(SpectreWidgets.withAlpha(this.color, fade)), 1.0F);
            }
         }
      }
   }
}
