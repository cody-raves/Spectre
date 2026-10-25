package net.spectre.client.module.impl.render.character.gltf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class TextureAtlas {
   private static final int SIZE = 2048;
   private static final int PAD = 4;
   private static final int MAX_SIDE = 8192;
   final int width;
   final int height;
   private final int[] pixels;
   private final Map<Integer, float[]> regions = new HashMap<>();
   private final Map<Integer, int[]> tiles = new HashMap<>();

   private TextureAtlas(int width, int height) {
      this.width = width;
      this.height = height;
      this.pixels = new int[width * height];
   }

   static TextureAtlas pack(GltfFile file, Map<Integer, float[]> used, ImageDecoder decoder) throws IOException {
      Map<Integer, ImageDecoder.Picture> crops = new LinkedHashMap<>();
      Map<Integer, float[]> cropUv = new HashMap<>();
      Map<Integer, int[]> tiles = new HashMap<>();

      for (Entry<Integer, float[]> e : used.entrySet()) {
         float[] b = e.getValue();
         int tu = (int)Math.floor(b[0] + 0.001F);
         int tv = (int)Math.floor(b[1] + 0.001F);
         int nu = Math.min(8, Math.max(1, (int)Math.ceil(b[2] - 0.001F) - tu));
         int nv = Math.min(8, Math.max(1, (int)Math.ceil(b[3] - 0.001F) - tv));
         ImageDecoder.Picture src = tile(baseColor(file, e.getKey(), decoder), nu, nv);
         tiles.put(e.getKey(), new int[]{tu, tv, nu, nv});
         float u0 = (b[0] - tu) / nu;
         float v0 = (b[1] - tv) / nv;
         float u1 = (b[2] - tu) / nu;
         float v1 = (b[3] - tv) / nv;
         int x0 = clamp((int)Math.floor(u0 * src.width()) - 2, 0, src.width() - 1);
         int y0 = clamp((int)Math.floor(v0 * src.height()) - 2, 0, src.height() - 1);
         int x1 = clamp((int)Math.ceil(u1 * src.width()) + 2, x0 + 1, src.width());
         int y1 = clamp((int)Math.ceil(v1 * src.height()) + 2, y0 + 1, src.height());
         crops.put(e.getKey(), crop(src, x0, y0, x1, y1));
         cropUv.put(e.getKey(), new float[]{(float)x0 / src.width(), (float)y0 / src.height(), (float)x1 / src.width(), (float)y1 / src.height()});
      }

      double area = 0.0;

      for (ImageDecoder.Picture c : crops.values()) {
         area += (double)(c.width() + 8) * (c.height() + 8);
      }

      double k = Math.min(1.0, Math.sqrt(3858759.68 / Math.max(1.0, area)));

      while (true) {
         TextureAtlas a = tryPack(crops, cropUv, k);
         if (a != null) {
            a.tiles.putAll(tiles);
            return a;
         }

         k *= 0.93;
      }
   }

   private static ImageDecoder.Picture baseColor(GltfFile file, int material, ImageDecoder decoder) throws IOException {
      byte[] encoded = file.baseColorImage(material);
      ImageDecoder.Picture src = encoded == null ? new ImageDecoder.Picture(4, 4, filled(16, -1)) : decoder.decode(encoded);
      float[] f = file.baseColorFactor(material);
      if (f[0] == 1.0F && f[1] == 1.0F && f[2] == 1.0F && f[3] == 1.0F) {
         return src;
      } else {
         for (int c = 0; c < 3; c++) {
            f[c] = (float)Math.pow(f[c], 0.45454545454545453);
         }

         int[] out = new int[src.argb().length];

         for (int i = 0; i < out.length; i++) {
            int p = src.argb()[i];
            int a = Math.round((p >>> 24) * f[3]);
            int r = Math.round((p >> 16 & 0xFF) * f[0]);
            int g = Math.round((p >> 8 & 0xFF) * f[1]);
            int b = Math.round((p & 0xFF) * f[2]);
            out[i] = a << 24 | r << 16 | g << 8 | b;
         }

         return new ImageDecoder.Picture(src.width(), src.height(), out);
      }
   }

   private static int[] filled(int n, int argb) {
      int[] out = new int[n];
      Arrays.fill(out, argb);
      return out;
   }

   private static ImageDecoder.Picture tile(ImageDecoder.Picture src, int nu, int nv) {
      if (nu == 1 && nv == 1) {
         return src;
      } else {
         if (src.width() * nu > 8192 || src.height() * nv > 8192) {
            src = resize(src, Math.max(1, Math.min(src.width(), 8192 / nu)), Math.max(1, Math.min(src.height(), 8192 / nv)));
         }

         int w = src.width() * nu;
         int h = src.height() * nv;
         int[] out = new int[w * h];

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               out[y * w + x] = src.argb()[y % src.height() * src.width() + x % src.width()];
            }
         }

         return new ImageDecoder.Picture(w, h, out);
      }
   }

   private static ImageDecoder.Picture crop(ImageDecoder.Picture src, int x0, int y0, int x1, int y1) {
      int w = x1 - x0;
      int h = y1 - y0;
      int[] out = new int[w * h];

      for (int y = 0; y < h; y++) {
         System.arraycopy(src.argb(), (y0 + y) * src.width() + x0, out, y * w, w);
      }

      return new ImageDecoder.Picture(w, h, out);
   }

   private static TextureAtlas tryPack(Map<Integer, ImageDecoder.Picture> crops, Map<Integer, float[]> cropUv, double k) {
      List<Integer> order = new ArrayList<>(crops.keySet());
      order.sort((a, b) -> Integer.compare(crops.get(b).height(), crops.get(a).height()));
      int x = 0;
      int y = 0;
      int shelf = 0;
      int right = 0;
      Map<Integer, int[]> place = new HashMap<>();

      for (int m : order) {
         ImageDecoder.Picture c = crops.get(m);
         int w = Math.max(1, (int)Math.round(c.width() * k));
         int h = Math.max(1, (int)Math.round(c.height() * k));
         if (x + w + 8 > 2048) {
            x = 0;
            y += shelf;
            shelf = 0;
         }

         if (w + 8 > 2048 || y + h + 8 > 2048) {
            return null;
         }

         place.put(m, new int[]{x + 4, y + 4, w, h});
         x += w + 8;
         shelf = Math.max(shelf, h + 8);
         right = Math.max(right, x);
      }

      TextureAtlas a = new TextureAtlas(pow2(right), pow2(y + shelf));

      for (int m : order) {
         int[] r = place.get(m);
         ImageDecoder.Picture scaled = resize(crops.get(m), r[2], r[3]);

         for (int py = -4; py < r[3] + 4; py++) {
            for (int px = -4; px < r[2] + 4; px++) {
               int sx = clamp(px, 0, r[2] - 1);
               int sy = clamp(py, 0, r[3] - 1);
               a.pixels[(r[1] + py) * a.width + r[0] + px] = scaled.argb()[sy * r[2] + sx];
            }
         }

         float[] cx = cropUv.get(m);
         a.regions.put(m, new float[]{cx[0], cx[1], cx[2], cx[3], r[0], r[1], r[2], r[3]});
      }

      return a;
   }

   float[] map(int material, float u, float v) {
      float[] r = this.regions.get(material);
      int[] t = this.tiles.get(material);
      float tu = (u - t[0]) / t[2];
      float tv = (v - t[1]) / t[3];
      float cu = (tu - r[0]) / (r[2] - r[0]);
      float cv = (tv - r[1]) / (r[3] - r[1]);
      return new float[]{(r[4] + cu * r[6]) / this.width, (r[5] + cv * r[7]) / this.height};
   }

   byte[] packed() throws IOException {
      byte[] raw = new byte[this.pixels.length * 4];

      for (int i = 0; i < this.pixels.length; i++) {
         int left = i % this.width == 0 ? 0 : this.pixels[i - 1];

         for (int c = 0; c < 4; c++) {
            raw[i * 4 + c] = (byte)((this.pixels[i] >>> 24 - c * 8) - (left >>> 24 - c * 8));
         }
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try (DeflaterOutputStream z = new DeflaterOutputStream(out, new Deflater(9))) {
         z.write(raw);
      }

      return out.toByteArray();
   }

   private static ImageDecoder.Picture resize(ImageDecoder.Picture src, int w, int h) {
      ImageDecoder.Picture cur = src;

      while (cur.width() / 2 >= w && cur.height() / 2 >= h) {
         cur = half(cur);
      }

      return cur.width() == w && cur.height() == h ? cur : bilinear(cur, w, h);
   }

   private static ImageDecoder.Picture half(ImageDecoder.Picture src) {
      int w = src.width() / 2;
      int h = src.height() / 2;
      int[] out = new int[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int[] s = src.argb();
            int i = y * 2 * src.width() + x * 2;
            out[y * w + x] = average(s[i], s[i + 1], s[i + src.width()], s[i + src.width() + 1]);
         }
      }

      return new ImageDecoder.Picture(w, h, out);
   }

   private static int average(int a, int b, int c, int d) {
      int out = 0;

      for (int shift = 0; shift < 32; shift += 8) {
         int sum = (a >>> shift & 0xFF) + (b >>> shift & 0xFF) + (c >>> shift & 0xFF) + (d >>> shift & 0xFF);
         out |= (sum + 2) / 4 << shift;
      }

      return out;
   }

   private static ImageDecoder.Picture bilinear(ImageDecoder.Picture src, int w, int h) {
      int[] out = new int[w * h];
      int sw = src.width();
      int sh = src.height();

      for (int y = 0; y < h; y++) {
         float fy = Math.max(0.0F, (y + 0.5F) * sh / h - 0.5F);
         int y0 = Math.min(sh - 1, (int)fy);
         int y1 = Math.min(sh - 1, y0 + 1);
         float ty = fy - y0;

         for (int x = 0; x < w; x++) {
            float fx = Math.max(0.0F, (x + 0.5F) * sw / w - 0.5F);
            int x0 = Math.min(sw - 1, (int)fx);
            int x1 = Math.min(sw - 1, x0 + 1);
            float tx = fx - x0;
            int[] s = src.argb();
            int p = 0;

            for (int shift = 0; shift < 32; shift += 8) {
               float top = (s[y0 * sw + x0] >>> shift & 0xFF) * (1.0F - tx) + (s[y0 * sw + x1] >>> shift & 0xFF) * tx;
               float bottom = (s[y1 * sw + x0] >>> shift & 0xFF) * (1.0F - tx) + (s[y1 * sw + x1] >>> shift & 0xFF) * tx;
               p |= Math.round(top * (1.0F - ty) + bottom * ty) << shift;
            }

            out[y * w + x] = p;
         }
      }

      return new ImageDecoder.Picture(w, h, out);
   }

   private static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   private static int pow2(int v) {
      int p = 16;

      while (p < v) {
         p <<= 1;
      }

      return p;
   }
}
