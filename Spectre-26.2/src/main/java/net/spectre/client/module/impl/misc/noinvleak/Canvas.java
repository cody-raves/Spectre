package net.spectre.client.module.impl.misc.noinvleak;

import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class Canvas implements DigitFont.Sink {
   private IntBuffer pixels;
   private int stride;
   private int width;
   private int height;

   void wrap(IntBuffer pixels, int stride, int width, int height) {
      this.pixels = pixels;
      this.stride = stride;
      this.width = width;
      this.height = height;
   }

   @Override
   public void rect(int x, int y, int w, int h, int argb) {
      int a = argb >>> 24;
      if (a != 0) {
         int x0 = Math.max(x, 0);
         int y0 = Math.max(y, 0);
         int x1 = Math.min(x + w, this.width);
         int y1 = Math.min(y + h, this.height);
         if (x0 < x1 && y0 < y1) {
            int src = a == 255 ? argb : premultiply(argb, a);

            for (int py = y0; py < y1; py++) {
               int row = py * this.stride;

               for (int px = x0; px < x1; px++) {
                  this.pixels.put(row + px, a == 255 ? src : over(src, this.pixels.get(row + px)));
               }
            }
         }
      }
   }

   void copy(int[] source, int sw, int sh, int x, int y) {
      for (int sy = 0; sy < sh; sy++) {
         int py = y + sy;
         if (py >= 0 && py < this.height) {
            int from = Math.max(0, -x);
            int to = Math.min(sw, this.width - x);
            if (from < to) {
               this.pixels.put(py * this.stride + x + from, source, sy * sw + from, to - from);
            }
         }
      }
   }

   void blend(int[] source, int sw, int sh, int x, int y, int scale) {
      for (int sy = 0; sy < sh; sy++) {
         for (int sx = 0; sx < sw; sx++) {
            this.blendPixel(source[sy * sw + sx], x + sx * scale, y + sy * scale, scale);
         }
      }
   }

   void blendPixel(int src, int x, int y, int scale) {
      if (src >>> 24 != 0) {
         int x0 = Math.max(x, 0);
         int y0 = Math.max(y, 0);
         int x1 = Math.min(x + scale, this.width);
         int y1 = Math.min(y + scale, this.height);

         for (int py = y0; py < y1; py++) {
            int row = py * this.stride;

            for (int px = x0; px < x1; px++) {
               this.pixels.put(row + px, over(src, this.pixels.get(row + px)));
            }
         }
      }
   }

   static int premultiply(int argb, int a) {
      return a << 24 | (argb >>> 16 & 0xFF) * a / 255 << 16 | (argb >>> 8 & 0xFF) * a / 255 << 8 | (argb & 0xFF) * a / 255;
   }

   static int over(int src, int dst) {
      int a = src >>> 24;
      if (a == 255) {
         return src;
      } else if (a == 0) {
         return dst;
      } else {
         int keep = 255 - a;
         return a + (dst >>> 24) * keep / 255 << 24
            | (src >>> 16 & 0xFF) + (dst >>> 16 & 0xFF) * keep / 255 << 16
            | (src >>> 8 & 0xFF) + (dst >>> 8 & 0xFF) * keep / 255 << 8
            | (src & 0xFF) + (dst & 0xFF) * keep / 255;
      }
   }
}
