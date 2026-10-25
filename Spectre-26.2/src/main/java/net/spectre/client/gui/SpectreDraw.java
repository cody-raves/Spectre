package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SpectreDraw {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   public static final int GOLD = -871878;
   public static final int GOLD_LIGHT = -11654;
   public static final int GOLD_TEXT = -1326998;
   public static final int LACQUER = -16120059;
   public static final int SAND = -2513573;
   public static final int SAND_PALE = -1454184;
   public static final int SAND_DARK = -4618945;
   public static final int LAPIS = -13738055;
   public static final int LAPIS_LIGHT = -12750874;
   public static final int LAPIS_DEEP = -14994064;
   public static final int TURQUOISE = -12794702;
   public static final int MALACHITE = -12600956;
   public static final int CARNELIAN = -2537681;
   public static final int TEXT = -923950;
   public static final int MUTED = -7372954;
   public static final int BLACK = -16777216;
   private static final Map<String, ThemeImages.Texture> TEXTURES = new HashMap<>();

   private SpectreDraw() {
   }

   public static int col(int argb, float alpha) {
      int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
      return GuiPalette.abgr(a << 24 | argb & 16777215);
   }

   public static int mix(int from, int to, float k) {
      k = Math.max(0.0F, Math.min(1.0F, k));
      int a = Math.round((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * k);
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * k);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * k);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void glow(ImDrawList dl, float cx, float cy, float radius, int argb, float alpha) {
      int layers = 18;

      for (int i = 0; i < layers; i++) {
         float f = 1.0F - (float)i / layers;
         dl.addCircleFilled(cx, cy, radius * f, col(argb, alpha / layers * 2.2F), 48);
      }
   }

   public static void sunDisk(ImDrawList dl, float cx, float cy, float r, float spin, float s, float a) {
      glow(dl, cx, cy, r * 1.9F, -1466054, 0.16F * a);
      dl.addCircleFilled(cx, cy, r, col(-14018806, 0.55F * a), 96);
      dl.addCircle(cx, cy, r, col(-871878, 0.55F * a), 96, Math.max(1.0F, 1.4F * s));
      dl.addCircle(cx, cy, r * 0.93F, col(-871878, 0.22F * a), 96, Math.max(1.0F, s));
      int rays = 48;

      for (int i = 0; i < rays; i++) {
         float ang = spin + (float)(i * Math.PI * 2.0 / rays);
         float inner = r * 1.07F;
         float outer = r * (i % 2 == 0 ? 1.34F : 1.2F);
         float cos = (float)Math.cos(ang);
         float sin = (float)Math.sin(ang);
         dl.addLine(
            cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, col(-871878, (i % 2 == 0 ? 0.28F : 0.16F) * a), Math.max(1.0F, 1.3F * s)
         );
      }
   }

   public static void text(ImDrawList dl, ImFont font, float x, float y, String text, int abgr) {
      if (font != null) {
         dl.addText(font, Math.round(font.getFontSize()), x, y, abgr, text);
      } else {
         dl.addText(x, y, abgr, text);
      }
   }

   public static float textWidth(ImFont font, String text) {
      return font == null ? text.length() * 7.0F : font.calcTextSizeAX(font.getFontSize(), Float.MAX_VALUE, 0.0F, text);
   }

   public static float spacedWidth(ImFont font, String text, float spacing) {
      float w = 0.0F;

      for (int i = 0; i < text.length(); i++) {
         w += textWidth(font, String.valueOf(text.charAt(i)));
      }

      return w + spacing * Math.max(0, text.length() - 1);
   }

   public static void drawSpaced(ImDrawList dl, ImFont font, float cx, float y, String text, float spacing, int argb, float a, boolean shadow) {
      float x = cx - spacedWidth(font, text, spacing) * 0.5F;

      for (int i = 0; i < text.length(); i++) {
         String glyph = String.valueOf(text.charAt(i));
         if (shadow) {
            text(dl, font, x + 1.0F, y + 1.0F, glyph, col(-16777216, 0.7F * a));
         }

         text(dl, font, x, y, glyph, col(argb, a));
         x += textWidth(font, glyph) + spacing;
      }
   }

   public static ThemeImages.Texture texture(String resource) {
      if (TEXTURES.containsKey(resource)) {
         return TEXTURES.get(resource);
      } else {
         ThemeImages.Texture texture = null;

         try (InputStream in = SpectreDraw.class.getResourceAsStream(resource)) {
            if (in != null) {
               texture = ThemeImages.decode(in.readAllBytes(), resource, false);
            } else {
               LOGGER.warn("[Spectre+] Missing texture {}", resource);
            }
         } catch (RuntimeException | IOException var7) {
            LOGGER.warn("[Spectre+] Failed to load {}", resource, var7);
         }

         TEXTURES.put(resource, texture);
         return texture;
      }
   }

   public static float intro(float t, float delay) {
      float x = Math.max(0.0F, Math.min(1.0F, (t - delay) / 0.6F));
      float inv = 1.0F - x;
      return 1.0F - inv * inv * inv;
   }
}
