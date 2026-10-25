package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.spectre.client.config.Surface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class HudChrome {
   static final int TEXT_VALUE = GuiPalette.abgr(-657929);
   static final int TEXT_NAME = GuiPalette.abgr(-1447442);
   static final int TEXT_LABEL = GuiPalette.abgr(-6052949);
   static final int WARN = GuiPalette.abgr(-47814);
   static final int BORDER = 402653183;
   static final int DIVIDER = 536870911;
   static final int TRACK = 452984831;
   static final int CHIP_FILL = GuiPalette.abgr(-350873571);
   private static final int TEXT_GUARD = -1946157056;
   private static final Long2FloatOpenHashMap DIGIT_CELLS = new Long2FloatOpenHashMap();
   private static final String[] ASCII = new String[128];

   private HudChrome() {
   }

   static void card(ImDrawList dl, Surface surface, float x, float y, float w, float h, float r, float alpha, boolean shadow, float scale) {
      if (!(alpha <= 0.001F)) {
         if (shadow) {
            shadow(dl, x, y, w, h, r, alpha, scale);
         }

         dl.addRectFilled(x, y, x + w, y + h, scaleAlpha(GuiPalette.fill(surface), alpha), r);
         SurfaceBackground.draw(dl, surface, x, y, w, h, r, alpha);
         dl.addRect(x, y, x + w, y + h, scaleAlpha(402653183, alpha), r, 0, 1.0F);
      }
   }

   static void shadow(ImDrawList dl, float x, float y, float w, float h, float r, float alpha, float scale) {
      float s = Math.max(1.0F, scale);
      dl.addRectFilled(x - s, y - 0.5F * s, x + w + s, y + h + 1.5F * s, scaleAlpha(771751936, alpha), r + s);
      dl.addRectFilled(x - 2.0F * s, y + 0.5F * s, x + w + 2.0F * s, y + h + 3.5F * s, scaleAlpha(385875968, alpha), r + 2.0F * s);
      dl.addRectFilled(x - 3.0F * s, y + 1.5F * s, x + w + 3.0F * s, y + h + 6.0F * s, scaleAlpha(201326592, alpha), r + 3.0F * s);
   }

   static void divider(ImDrawList dl, float x, float centerY, float height, float alpha) {
      float px = Math.round(x);
      float top = Math.round(centerY - height * 0.5F);
      dl.addRectFilled(px, top, px + 1.0F, top + Math.round(height), scaleAlpha(536870911, alpha));
   }

   static boolean needsTextGuard(Surface surface) {
      return surface != null && (surface.opacity.get() < 0.55F || surface.isImage());
   }

   static int px(float base, float scale) {
      return Math.max(6, Math.round(base * scale));
   }

   static float width(ImFont font, int size, String text) {
      return font != null && !text.isEmpty() ? font.calcTextSizeA(size, Float.MAX_VALUE, 0.0F, text).x : text.length() * size * 0.55F;
   }

   static float ascent(ImFont font, int size) {
      return font != null && !(font.getFontSize() <= 0.0F) ? font.getAscent() * size / font.getFontSize() : size * 0.77F;
   }

   static float baselineOffset(ImFont big, int bigSize, ImFont small, int smallSize) {
      return Math.round(ascent(big, bigSize) - ascent(small, smallSize));
   }

   static void text(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text, boolean guard) {
      if (!text.isEmpty() && (color >>> 24 & 0xFF) != 0) {
         if (guard) {
            raw(dl, font, size, x + 1.0F, y + 1.0F, scaleAlpha(-1946157056, (color >>> 24 & 0xFF) / 255.0F), text);
         }

         raw(dl, font, size, x, y, color, text);
      }
   }

   private static void raw(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text) {
      if (font != null) {
         dl.addText(font, size, x, y, color, text);
      } else {
         dl.addText(x, y, color, text);
      }
   }

   private static float digitCell(ImFont font, int size) {
      if (font == null) {
         return size * 0.6F;
      } else {
         long key = (long)System.identityHashCode(font) << 16 ^ size;
         float cached = DIGIT_CELLS.get(key);
         if (!Float.isNaN(cached)) {
            return cached;
         } else {
            float cell = 0.0F;

            for (char c = '0'; c <= '9'; c++) {
               cell = Math.max(cell, advance(font, size, c));
            }

            if (DIGIT_CELLS.size() > 64) {
               DIGIT_CELLS.clear();
            }

            DIGIT_CELLS.put(key, cell);
            return cell;
         }
      }
   }

   private static float advance(ImFont font, int size, char c) {
      return font.getCharAdvance(c) * size / font.getFontSize();
   }

   static float tabularWidth(ImFont font, int size, String text) {
      if (font == null) {
         return width(null, size, text);
      } else {
         float cell = digitCell(font, size);
         float w = 0.0F;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += Character.isDigit(c) ? cell : advance(font, size, c);
         }

         return w;
      }
   }

   static void tabular(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text, boolean guard) {
      if (font == null) {
         text(dl, null, size, x, y, color, text, guard);
      } else {
         float cell = digitCell(font, size);
         float cx = x;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float adv = advance(font, size, c);
            if (Character.isDigit(c)) {
               text(dl, font, size, cx + (cell - adv) * 0.5F, y, color, glyph(c), guard);
               cx += cell;
            } else {
               if (c != ' ') {
                  text(dl, font, size, cx, y, color, glyph(c), guard);
               }

               cx += adv;
            }
         }
      }
   }

   private static String glyph(char c) {
      return c < ASCII.length ? ASCII[c] : String.valueOf(c);
   }

   static int scaleAlpha(int color, float alphaScale) {
      int alpha = color >>> 24 & 0xFF;
      int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, alphaScale)));
      return color & 16777215 | scaled << 24;
   }

   static {
      DIGIT_CELLS.defaultReturnValue(Float.NaN);

      for (char c = 0; c < ASCII.length; c++) {
         ASCII[c] = String.valueOf(c);
      }
   }
}
