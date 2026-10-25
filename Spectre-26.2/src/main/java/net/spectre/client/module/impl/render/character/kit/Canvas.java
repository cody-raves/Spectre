package net.spectre.client.module.impl.render.character.kit;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Canvas {
   private final int width;
   private final int height;
   final int[] pixels;

   public Canvas(int width, int height) {
      if (width >= 1 && height >= 1 && width <= 2048 && height <= 2048) {
         this.width = width;
         this.height = height;
         this.pixels = new int[width * height];
      } else {
         throw new IllegalArgumentException("canvas size must be between 1 and 2048 pixels");
      }
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   public int get(int x, int y) {
      return x >= 0 && y >= 0 && x < this.width && y < this.height ? this.pixels[y * this.width + x] : 0;
   }

   public int[] pixels() {
      return (int[])this.pixels.clone();
   }

   public Canvas clear(int argb) {
      Arrays.fill(this.pixels, argb);
      return this;
   }

   public Canvas fill(Shape2D shape, int argb) {
      return this.fill(shape, Paint2D.solid(argb), 0.0F);
   }

   public Canvas fill(Shape2D shape, int argb, float feather) {
      return this.fill(shape, Paint2D.solid(argb), feather);
   }

   public Canvas fill(Shape2D shape, Paint2D paint) {
      return this.fill(shape, paint, 0.0F);
   }

   public Canvas fill(Shape2D shape, Paint2D paint, float feather) {
      float w = Math.max(1.0F, feather);
      int x0 = Math.max(0, (int)Math.floor(shape.minX - w - 1.0F));
      int y0 = Math.max(0, (int)Math.floor(shape.minY - w - 1.0F));
      int x1 = Math.min(this.width - 1, (int)Math.ceil(shape.maxX + w + 1.0F));
      int y1 = Math.min(this.height - 1, (int)Math.ceil(shape.maxY + w + 1.0F));

      for (int y = y0; y <= y1; y++) {
         float fy = y + 0.5F;

         for (int x = x0; x <= x1; x++) {
            float fx = x + 0.5F;
            float cover = 0.5F - shape.distance(fx, fy) / w;
            if (!(cover <= 0.0F)) {
               if (cover > 1.0F) {
                  cover = 1.0F;
               }

               this.blend(y * this.width + x, paint.argb(fx, fy), cover);
            }
         }
      }

      return this;
   }

   public Canvas fillAll(Paint2D paint) {
      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            this.blend(y * this.width + x, paint.argb(x + 0.5F, y + 0.5F), 1.0F);
         }
      }

      return this;
   }

   public Canvas erase(Shape2D shape) {
      return this.erase(shape, 0.0F);
   }

   public Canvas erase(Shape2D shape, float feather) {
      float w = Math.max(1.0F, feather);
      int x0 = Math.max(0, (int)Math.floor(shape.minX - w - 1.0F));
      int y0 = Math.max(0, (int)Math.floor(shape.minY - w - 1.0F));
      int x1 = Math.min(this.width - 1, (int)Math.ceil(shape.maxX + w + 1.0F));
      int y1 = Math.min(this.height - 1, (int)Math.ceil(shape.maxY + w + 1.0F));

      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            float cover = 0.5F - shape.distance(x + 0.5F, y + 0.5F) / w;
            if (!(cover <= 0.0F)) {
               int i = y * this.width + x;
               int a = Math.round((this.pixels[i] >>> 24) * (1.0F - Math.min(1.0F, cover)));
               this.pixels[i] = a << 24 | this.pixels[i] & 16777215;
            }
         }
      }

      return this;
   }

   public Canvas draw(Canvas src, int dx, int dy) {
      for (int y = 0; y < src.height; y++) {
         int ty = y + dy;
         if (ty >= 0 && ty < this.height) {
            for (int x = 0; x < src.width; x++) {
               int tx = x + dx;
               if (tx >= 0 && tx < this.width) {
                  this.blend(ty * this.width + tx, src.pixels[y * src.width + x], 1.0F);
               }
            }
         }
      }

      return this;
   }

   public Canvas mirrored() {
      Canvas out = new Canvas(this.width, this.height);

      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            out.pixels[y * this.width + x] = this.pixels[y * this.width + this.width - 1 - x];
         }
      }

      return out;
   }

   public Canvas copy() {
      Canvas out = new Canvas(this.width, this.height);
      System.arraycopy(this.pixels, 0, out.pixels, 0, this.pixels.length);
      return out;
   }

   private void blend(int i, int src, float cover) {
      float sa = (src >>> 24) / 255.0F * cover;
      if (!(sa <= 0.0F)) {
         int dst = this.pixels[i];
         float da = (dst >>> 24) / 255.0F;
         float oa = sa + da * (1.0F - sa);
         if (oa <= 0.0F) {
            this.pixels[i] = 0;
         } else {
            float kd = da * (1.0F - sa) / oa;
            float ks = sa / oa;
            int r = Math.round((src >> 16 & 0xFF) * ks + (dst >> 16 & 0xFF) * kd);
            int g = Math.round((src >> 8 & 0xFF) * ks + (dst >> 8 & 0xFF) * kd);
            int b = Math.round((src & 0xFF) * ks + (dst & 0xFF) * kd);
            int a = Math.round(oa * 255.0F);
            this.pixels[i] = Colors.clamp(a) << 24 | Colors.clamp(r) << 16 | Colors.clamp(g) << 8 | Colors.clamp(b);
         }
      }
   }
}
