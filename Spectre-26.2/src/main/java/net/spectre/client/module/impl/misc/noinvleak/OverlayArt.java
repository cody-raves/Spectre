package net.spectre.client.module.impl.misc.noinvleak;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

@Environment(EnvType.CLIENT)
final class OverlayArt {
   private static final int GLYPH = 8;
   private static final int SHADOW_MASK = 16579836;
   private static OverlayArt.Image selection;
   private static OverlayArt.Slice tooltipBackground;
   private static OverlayArt.Slice tooltipFrame;
   private static boolean[] glyphs;
   private static boolean loaded;

   private OverlayArt() {
   }

   static void reset() {
      selection = null;
      tooltipBackground = null;
      tooltipFrame = null;
      glyphs = null;
      loaded = false;
   }

   static OverlayArt.Image selection() {
      load();
      return selection;
   }

   static OverlayArt.Slice tooltipBackground() {
      load();
      return tooltipBackground;
   }

   static OverlayArt.Slice tooltipFrame() {
      load();
      return tooltipFrame;
   }

   private static void load() {
      if (!loaded) {
         loaded = true;
         selection = image("textures/gui/sprites/hud/hotbar_selection.png");
         tooltipBackground = slice("textures/gui/sprites/tooltip/background.png", 9, false);
         tooltipFrame = slice("textures/gui/sprites/tooltip/frame.png", 10, true);
         OverlayArt.Image font = image("textures/font/ascii.png");
         if (font != null) {
            glyphs = new boolean[font.width() * font.height()];

            for (int i = 0; i < glyphs.length; i++) {
               glyphs[i] = font.pixels()[i] >>> 24 != 0;
            }
         }
      }
   }

   private static OverlayArt.Image image(String path) {
      Resource resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(path)).orElse(null);
      if (resource == null) {
         return null;
      } else {
         try {
            OverlayArt.Image var13;
            try (
               InputStream in = resource.open();
               NativeImage image = NativeImage.read(in);
            ) {
               int[] abgr = image.getPixelsABGR();
               int[] out = new int[abgr.length];

               for (int i = 0; i < abgr.length; i++) {
                  int a = abgr[i] >>> 24;
                  out[i] = Canvas.premultiply(a << 24 | (abgr[i] & 0xFF) << 16 | (abgr[i] >>> 8 & 0xFF) << 8 | abgr[i] >>> 16 & 0xFF, a);
               }

               var13 = new OverlayArt.Image(out, image.getWidth(), image.getHeight());
            }

            return var13;
         } catch (RuntimeException | IOException var12) {
            return null;
         }
      }
   }

   private static OverlayArt.Slice slice(String path, int border, boolean stretch) {
      OverlayArt.Image image = image(path);
      if (image == null) {
         return null;
      } else {
         Resource meta = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(path + ".mcmeta")).orElse(null);
         if (meta != null) {
            try (Reader reader = meta.openAsReader()) {
               JsonObject scaling = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
               if (scaling.has("border")) {
                  border = scaling.get("border").getAsInt();
               }

               stretch = scaling.has("stretch_inner") && scaling.get("stretch_inner").getAsBoolean();
            } catch (RuntimeException | IOException var10) {
            }
         }

         return new OverlayArt.Slice(image, border, stretch);
      }
   }

   static void slice(Canvas canvas, OverlayArt.Slice slice, int x, int y, int width, int height, int scale) {
      if (slice != null) {
         OverlayArt.Image img = slice.image();
         int b = slice.border();
         int innerW = img.width() - 2 * b;
         int innerH = img.height() - 2 * b;
         if (innerW > 0 && innerH > 0) {
            int[] column = new int[width];
            int[] row = new int[height];

            for (int dx = 0; dx < width; dx++) {
               column[dx] = sourceAt(dx, width, b, innerW, slice.stretch());
            }

            for (int dy = 0; dy < height; dy++) {
               row[dy] = sourceAt(dy, height, b, innerH, slice.stretch());
            }

            for (int dy = 0; dy < height; dy++) {
               for (int dx = 0; dx < width; dx++) {
                  int src = img.pixels()[row[dy] * img.width() + column[dx]];
                  if (src >>> 24 != 0) {
                     canvas.blendPixel(src, (x + dx) * scale, (y + dy) * scale, scale);
                  }
               }
            }
         }
      }
   }

   private static int sourceAt(int d, int size, int b, int inner, boolean stretch) {
      if (d < b) {
         return d;
      } else if (d >= size - b) {
         return b + inner + (d - (size - b));
      } else {
         int into = d - b;
         int middle = size - 2 * b;
         return b + (stretch ? into * inner / Math.max(middle, 1) : into % inner);
      }
   }

   static void text(Canvas canvas, Component text, int x, int y, int scale, int argb, boolean shadow) {
      load();
      if (glyphs != null) {
         int[] cursor = new int[]{x};
         text.visit((style, piece) -> {
            int color = style.getColor() != null ? argb & 0xFF000000 | style.getColor().getValue() : argb;
            int i = 0;

            while (i < piece.length()) {
               int cp = piece.codePointAt(i);
               i += Character.charCount(cp);
               int advance = advance(cp, style);
               if (cp < 128 && cp > 32 && !style.isObfuscated()) {
                  if (shadow) {
                     glyph(canvas, cp, cursor[0] + 1, y + 1, scale, color & 0xFF000000 | (color & 16579836) >> 2, style);
                  }

                  glyph(canvas, cp, cursor[0], y, scale, color, style);
               }

               if (style.isStrikethrough()) {
                  canvas.rect(cursor[0] * scale, (y + 4) * scale, advance * scale, scale, color);
               }

               if (style.isUnderlined()) {
                  canvas.rect(cursor[0] * scale, (y + 8) * scale, advance * scale, scale, color);
               }

               cursor[0] += advance;
            }

            return Optional.empty();
         }, Style.EMPTY);
      }
   }

   private static int advance(int cp, Style style) {
      return Minecraft.getInstance().font.width(FormattedText.of(new String(Character.toChars(cp)), style));
   }

   private static void glyph(Canvas canvas, int cp, int x, int y, int scale, int argb, Style style) {
      int cellX = cp % 16 * 8;
      int cellY = cp / 16 * 8;
      int stride = 128;

      for (int row = 0; row < 8; row++) {
         int shear = style.isItalic() && row < 4 ? 1 : 0;
         int col = 0;

         while (col < 8) {
            if (!glyphs[(cellY + row) * stride + cellX + col]) {
               col++;
            } else {
               int start = col;

               while (col < 8 && glyphs[(cellY + row) * stride + cellX + col]) {
                  col++;
               }

               canvas.rect((x + start + shear) * scale, (y + row) * scale, (col - start) * scale, scale, argb);
               if (style.isBold()) {
                  canvas.rect((x + start + shear + 1) * scale, (y + row) * scale, (col - start) * scale, scale, argb);
               }
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   record Image(int[] pixels, int width, int height) {
   }

   @Environment(EnvType.CLIENT)
   record Slice(OverlayArt.Image image, int border, boolean stretch) {
   }
}
