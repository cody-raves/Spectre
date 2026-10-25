package net.spectre.client.util.glyph;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GlyphShapes {
   public static final float[] GHOST = SpectralShapes.GHOST;
   public static final float[] CRESCENT = SpectralShapes.MOON;
   public static final float[] SPARK = spark();
   public static final float[] GRAIN = grain();
   public static final float[] DIAMOND = diamond();
   public static final float[] DISK = new GlyphShapes.Builder().disk(0.0F, 0.0F, 1.0F, 1.0F, 12).build();
   public static final float[] OCTAGON = new GlyphShapes.Builder().disk(0.0F, 0.0F, 1.0F, 1.0F, 8).build();
   public static final float[] HALO = new GlyphShapes.Builder().arc(0.0F, 0.0F, 0.84F, 0.84F, 1.0F, 1.0F, 0.0F, (float) (Math.PI * 2), 20).build();
   public static final float[] EYE = SpectralShapes.ORBIT;
   public static final float[] EYE_PUPIL = new GlyphShapes.Builder().offset(-0.14F, 0.04F).disk(-0.08F, 0.07F, 0.19F, 0.19F, 8).build();

   private GlyphShapes() {
   }

   private static float[] spark() {
      GlyphShapes.Builder b = new GlyphShapes.Builder();
      b.quad(0.0F, -1.0F, 0.15F, 0.0F, 0.0F, 1.0F, -0.15F, 0.0F);
      b.quad(-0.62F, 0.0F, 0.0F, -0.13F, 0.62F, 0.0F, 0.0F, 0.13F);
      return b.build();
   }

   private static float[] grain() {
      return new GlyphShapes.Builder().quad(-0.8F, -0.62F, 0.72F, -0.88F, 0.9F, 0.46F, -0.52F, 0.82F).build();
   }

   private static float[] diamond() {
      return new GlyphShapes.Builder().quad(0.0F, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, -1.0F, 0.0F).build();
   }

   @Environment(EnvType.CLIENT)
   private static final class Builder {
      private float[] data = new float[64];
      private int size;
      private float offsetX;
      private float offsetY;

      GlyphShapes.Builder offset(float x, float y) {
         this.offsetX = x;
         this.offsetY = y;
         return this;
      }

      GlyphShapes.Builder quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
         if (this.size + 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.size++] = ax + this.offsetX;
         this.data[this.size++] = ay + this.offsetY;
         this.data[this.size++] = bx + this.offsetX;
         this.data[this.size++] = by + this.offsetY;
         this.data[this.size++] = cx + this.offsetX;
         this.data[this.size++] = cy + this.offsetY;
         this.data[this.size++] = dx + this.offsetX;
         this.data[this.size++] = dy + this.offsetY;
         return this;
      }

      GlyphShapes.Builder arc(float cx, float cy, float innerX, float innerY, float outerX, float outerY, float from, float to, int steps) {
         for (int i = 0; i < steps; i++) {
            double a0 = from + (double)((to - from) * i) / steps;
            double a1 = from + (double)((to - from) * (i + 1)) / steps;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            this.quad(
               cx + c0 * innerX, cy + s0 * innerY, cx + c0 * outerX, cy + s0 * outerY, cx + c1 * outerX, cy + s1 * outerY, cx + c1 * innerX, cy + s1 * innerY
            );
         }

         return this;
      }

      GlyphShapes.Builder disk(float cx, float cy, float rx, float ry, int sides) {
         float[] ring = new float[sides * 2];

         for (int i = 0; i < sides; i++) {
            double a = (Math.PI * 2) * i / sides + Math.PI / sides;
            ring[i * 2] = cx + (float)Math.cos(a) * rx;
            ring[i * 2 + 1] = cy + (float)Math.sin(a) * ry;
         }

         for (int i = 1; i + 1 < sides; i += 2) {
            int j = Math.min(i + 2, sides - 1);
            this.quad(ring[0], ring[1], ring[i * 2], ring[i * 2 + 1], ring[(i + 1) * 2], ring[(i + 1) * 2 + 1], ring[j * 2], ring[j * 2 + 1]);
         }

         return this;
      }

      GlyphShapes.Builder stroke(float width, float... points) {
         int n = points.length / 2;
         if (n < 2) {
            return this;
         } else {
            float half = width * 0.5F;
            float[] nx = new float[n];
            float[] ny = new float[n];

            for (int i = 0; i < n; i++) {
               float sx = 0.0F;
               float sy = 0.0F;
               float px = 0.0F;
               float py = 0.0F;
               if (i > 0) {
                  float dx = points[i * 2] - points[i * 2 - 2];
                  float dy = points[i * 2 + 1] - points[i * 2 - 1];
                  float len = (float)Math.sqrt(dx * dx + dy * dy);
                  if (len > 1.0E-6F) {
                     px = -dy / len;
                     py = dx / len;
                     sx += px;
                     sy += py;
                  }
               }

               if (i < n - 1) {
                  float dx = points[i * 2 + 2] - points[i * 2];
                  float dy = points[i * 2 + 3] - points[i * 2 + 1];
                  float len = (float)Math.sqrt(dx * dx + dy * dy);
                  if (len > 1.0E-6F) {
                     sx += -dy / len;
                     sy += dx / len;
                     if (i == 0) {
                        px = -dy / len;
                        py = dx / len;
                     }
                  }
               }

               float len = (float)Math.sqrt(sx * sx + sy * sy);
               if (len < 1.0E-6F) {
                  sx = px;
                  sy = py;
                  len = 1.0F;
               }

               sx /= len;
               sy /= len;
               float dot = Math.max(0.5F, sx * px + sy * py);
               nx[i] = sx * half / dot;
               ny[i] = sy * half / dot;
            }

            for (int i = 0; i < n - 1; i++) {
               float ax = points[i * 2];
               float ay = points[i * 2 + 1];
               float bx = points[i * 2 + 2];
               float by = points[i * 2 + 3];
               this.quad(ax - nx[i], ay - ny[i], bx - nx[i + 1], by - ny[i + 1], bx + nx[i + 1], by + ny[i + 1], ax + nx[i], ay + ny[i]);
            }

            return this;
         }
      }

      float[] build() {
         return Arrays.copyOf(this.data, this.size);
      }
   }
}
