package net.spectre.client.module.impl.render.character.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class Atlas {
   static final int WHITE = 16;
   private static final int PAD = 3;
   private static final int MAX = 2048;
   private static final int LEVELS = 5;
   final int width;
   final int height;
   final int[][] mips;
   private final Map<Canvas, int[]> rects;

   private Atlas(int width, int height, int[][] mips, Map<Canvas, int[]> rects) {
      this.width = width;
      this.height = height;
      this.mips = mips;
      this.rects = rects;
   }

   float whiteU() {
      return 8.0F / this.width;
   }

   float whiteV() {
      return 8.0F / this.height;
   }

   float u(Canvas c, float u) {
      int[] r = this.rects.get(c);
      return (r[0] + u * r[2]) / this.width;
   }

   float v(Canvas c, float v) {
      int[] r = this.rects.get(c);
      return (r[1] + v * r[3]) / this.height;
   }

   static Atlas pack(List<Canvas> input) {
      List<Canvas> canvases = new ArrayList<>();

      for (Canvas c : input) {
         if (!canvases.contains(c)) {
            canvases.add(c);
         }
      }

      canvases.sort((a, b) -> Integer.compare(b.height(), a.height()));
      long area = 484L;
      int widest = 22;

      for (Canvas cx : canvases) {
         area += (long)(cx.width() + 6) * (cx.height() + 6);
         widest = Math.max(widest, cx.width() + 6);
      }

      int width = pow2(Math.max(widest, (int)Math.ceil(Math.sqrt(area))));

      while (true) {
         Map<Canvas, int[]> rects = new IdentityHashMap<>();
         int x = 22;
         int y = 0;
         int shelf = 22;

         for (Canvas cx : canvases) {
            int w = cx.width() + 6;
            int h = cx.height() + 6;
            if (x + w > width) {
               x = 0;
               y += shelf;
               shelf = 0;
            }

            rects.put(cx, new int[]{x + 3, y + 3, cx.width(), cx.height()});
            x += w;
            shelf = Math.max(shelf, h);
         }

         int height = pow2(Math.max(22, y + shelf));
         if (height <= 2048 || width >= 2048) {
            if (height > 2048) {
               throw new IllegalStateException("decals do not fit into a 2048x2048 atlas");
            } else {
               return render(width, height, canvases, rects);
            }
         }

         width *= 2;
      }
   }

   private static Atlas render(int width, int height, List<Canvas> canvases, Map<Canvas, int[]> rects) {
      int[] px = new int[width * height];

      for (int y = 0; y < 19; y++) {
         for (int x = 0; x < 19; x++) {
            px[y * width + x] = -1;
         }
      }

      for (Canvas c : canvases) {
         int[] r = rects.get(c);
         int[] src = dilate(c);

         for (int y = -3; y < c.height() + 3; y++) {
            int sy = Math.max(0, Math.min(c.height() - 1, y));

            for (int x = -3; x < c.width() + 3; x++) {
               int sx = Math.max(0, Math.min(c.width() - 1, x));
               px[(r[1] + y) * width + r[0] + x] = src[sy * c.width() + sx];
            }
         }
      }

      return new Atlas(width, height, mips(px, width, height), rects);
   }

   static int[][] mips(int[] px, int width, int height) {
      int levels = Math.min(5, 1 + (31 - Integer.numberOfLeadingZeros(Math.min(width, height))));
      int[][] mips = new int[levels][];
      mips[0] = px;
      int w = width;
      int h = height;

      for (int l = 1; l < levels; l++) {
         mips[l] = down(mips[l - 1], w, h);
         w = Math.max(1, w / 2);
         h = Math.max(1, h / 2);
      }

      return mips;
   }

   private static int[] dilate(Canvas c) {
      int w = c.width();
      int h = c.height();
      int[] px = Arrays.copyOf(c.pixels, c.pixels.length);
      boolean[] known = new boolean[px.length];

      for (int i = 0; i < px.length; i++) {
         known[i] = px[i] >>> 24 != 0;
      }

      for (int pass = 0; pass < 4; pass++) {
         boolean[] next = (boolean[])known.clone();

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int i = y * w + x;
               if (!known[i]) {
                  int r = 0;
                  int g = 0;
                  int b = 0;
                  int n = 0;

                  for (int dy = -1; dy <= 1; dy++) {
                     for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                           int j = ny * w + nx;
                           if (known[j]) {
                              r += px[j] >> 16 & 0xFF;
                              g += px[j] >> 8 & 0xFF;
                              b += px[j] & 0xFF;
                              n++;
                           }
                        }
                     }
                  }

                  if (n != 0) {
                     px[i] = r / n << 16 | g / n << 8 | b / n;
                     next[i] = true;
                  }
               }
            }
         }

         known = next;
      }

      return px;
   }

   private static int[] down(int[] src, int w, int h) {
      int nw = Math.max(1, w / 2);
      int nh = Math.max(1, h / 2);
      int[] out = new int[nw * nh];

      for (int y = 0; y < nh; y++) {
         for (int x = 0; x < nw; x++) {
            float a = 0.0F;
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            float rr = 0.0F;
            float gg = 0.0F;
            float bb = 0.0F;

            for (int k = 0; k < 4; k++) {
               int sx = Math.min(w - 1, x * 2 + (k & 1));
               int sy = Math.min(h - 1, y * 2 + (k >> 1));
               int p = src[sy * w + sx];
               float pa = (p >>> 24) / 255.0F;
               a += pa;
               r += (p >> 16 & 0xFF) * pa;
               g += (p >> 8 & 0xFF) * pa;
               b += (p & 0xFF) * pa;
               rr += p >> 16 & 0xFF;
               gg += p >> 8 & 0xFF;
               bb += p & 0xFF;
            }

            int oa = Math.round(a / 4.0F * 255.0F);
            int or;
            int og;
            int ob;
            if (a > 1.0E-4F) {
               or = Math.round(r / a);
               og = Math.round(g / a);
               ob = Math.round(b / a);
            } else {
               or = Math.round(rr / 4.0F);
               og = Math.round(gg / 4.0F);
               ob = Math.round(bb / 4.0F);
            }

            out[y * nw + x] = Colors.clamp(oa) << 24 | Colors.clamp(or) << 16 | Colors.clamp(og) << 8 | Colors.clamp(ob);
         }
      }

      return out;
   }

   private static int pow2(int v) {
      int p = 16;

      while (p < v) {
         p <<= 1;
      }

      return p;
   }
}
