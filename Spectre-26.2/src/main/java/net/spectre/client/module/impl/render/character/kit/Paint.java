package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class Paint {
   static final Paint DEFAULT = of(-4934476);

   public abstract void apply(float var1, float var2, float var3, Sample var4);

   public static Paint of(int argb) {
      return new Paint.Solid(argb);
   }

   public static Paint gradient(int from, float ax, float ay, float az, int to, float bx, float by, float bz) {
      return new Paint.Linear(from, ax, ay, az, to, bx, by, bz);
   }

   public static Paint vertical(int bottom, float y0, int top, float y1) {
      return new Paint.Linear(bottom, 0.0F, y0, 0.0F, top, 0.0F, y1, 0.0F);
   }

   public static Paint radial(int inner, float cx, float cy, float cz, float radius, int outer) {
      return new Paint.Radial(inner, cx, cy, cz, radius, outer);
   }

   public static Paint wood(int light, int dark, float ax, float ay, float az, float dx, float dy, float dz, float ringSpacing) {
      return new Paint.Wood(light, dark, ax, ay, az, dx, dy, dz, ringSpacing);
   }

   public static Paint field(Paint.Field field) {
      return new Paint.Custom(field);
   }

   public Paint noise(int other, float size, float amount) {
      return new Paint.Blotch(this, other, size, amount);
   }

   public Paint grain(float size, float amount) {
      return new Paint.Grain(this, size, amount);
   }

   public Paint band(int argb, float px, float py, float pz, float nx, float ny, float nz, float halfWidth, float softness) {
      return new Paint.Band(this, argb, px, py, pz, nx, ny, nz, halfWidth, softness);
   }

   public Paint glow() {
      return this.glow(1.0F);
   }

   public Paint glow(float amount) {
      return new Paint.Glow(this, amount);
   }

   public Paint flatLight(float amount) {
      return new Paint.Shading(this, Math.max(0.0F, Math.min(1.0F, amount)), Float.NaN);
   }

   public Paint occlusion(float strength) {
      return new Paint.Shading(this, Float.NaN, Math.max(0.0F, Math.min(1.0F, strength)));
   }

   static float smooth(float e0, float e1, float x) {
      float t = (x - e0) / (e1 - e0);
      t = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   @Environment(EnvType.CLIENT)
   private static final class Band extends Paint {
      private final Paint base;
      private final int argb;
      private final float px;
      private final float py;
      private final float pz;
      private final float nx;
      private final float ny;
      private final float nz;
      private final float half;
      private final float soft;

      Band(Paint base, int argb, float px, float py, float pz, float nx, float ny, float nz, float half, float soft) {
         this.base = base;
         this.argb = argb;
         this.px = px;
         this.py = py;
         this.pz = pz;
         float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         this.nx = l < 1.0E-6F ? 0.0F : nx / l;
         this.ny = l < 1.0E-6F ? 1.0F : ny / l;
         this.nz = l < 1.0E-6F ? 0.0F : nz / l;
         this.half = half;
         this.soft = Math.max(0.001F, soft);
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         float d = Math.abs((x - this.px) * this.nx + (y - this.py) * this.ny + (z - this.pz) * this.nz);
         float t = 1.0F - smooth(this.half, this.half + this.soft, d);
         if (t > 0.0F) {
            float glow = out.glow;
            out.toward(this.argb, t * ((this.argb >>> 24) / 255.0F));
            out.glow = glow;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Blotch extends Paint {
      private final Paint base;
      private final int other;
      private final float freq;
      private final float amount;

      Blotch(Paint base, int other, float size, float amount) {
         this.base = base;
         this.other = other;
         this.freq = 1.0F / Math.max(0.01F, size);
         this.amount = amount;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         float n = Noise.fbm(x * this.freq, y * this.freq, z * this.freq, 3) * 1.6F + 0.5F;
         float t = (n < 0.0F ? 0.0F : Math.min(n, 1.0F)) * this.amount;
         float glow = out.glow;
         out.toward(this.other, t);
         out.glow = glow;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Custom extends Paint {
      private final Paint.Field field;

      Custom(Paint.Field field) {
         this.field = field;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         out.set(this.field.argb(x, y, z));
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Field {
      int argb(float var1, float var2, float var3);
   }

   @Environment(EnvType.CLIENT)
   private static final class Glow extends Paint {
      private final Paint base;
      private final float amount;

      Glow(Paint base, float amount) {
         this.base = base;
         this.amount = amount;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         out.glow = this.amount;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Grain extends Paint {
      private final Paint base;
      private final float freq;
      private final float amount;

      Grain(Paint base, float size, float amount) {
         this.base = base;
         this.freq = 1.0F / Math.max(0.01F, size);
         this.amount = amount;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         float k = 1.0F + Noise.fbm(x * this.freq, y * this.freq, z * this.freq, 2) * 2.0F * this.amount;
         out.r *= k;
         out.g *= k;
         out.b *= k;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Linear extends Paint {
      private final int from;
      private final int to;
      private final float ax;
      private final float ay;
      private final float az;
      private final float dx;
      private final float dy;
      private final float dz;
      private final float inv;

      Linear(int from, float ax, float ay, float az, int to, float bx, float by, float bz) {
         this.from = from;
         this.to = to;
         this.ax = ax;
         this.ay = ay;
         this.az = az;
         this.dx = bx - ax;
         this.dy = by - ay;
         this.dz = bz - az;
         float l2 = this.dx * this.dx + this.dy * this.dy + this.dz * this.dz;
         this.inv = l2 < 1.0E-8F ? 0.0F : 1.0F / l2;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         float t = ((x - this.ax) * this.dx + (y - this.ay) * this.dy + (z - this.az) * this.dz) * this.inv;
         t = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
         out.set(this.from);
         out.toward(this.to, t);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Radial extends Paint {
      private final int inner;
      private final int outer;
      private final float cx;
      private final float cy;
      private final float cz;
      private final float radius;

      Radial(int inner, float cx, float cy, float cz, float radius, int outer) {
         this.inner = inner;
         this.outer = outer;
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.radius = Math.max(1.0E-4F, radius);
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         float dx = x - this.cx;
         float dy = y - this.cy;
         float dz = z - this.cz;
         float t = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) / this.radius;
         out.set(this.inner);
         out.toward(this.outer, t < 0.0F ? 0.0F : Math.min(t, 1.0F));
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Shading extends Paint {
      private final Paint base;
      private final float flat;
      private final float occlusion;

      Shading(Paint base, float flat, float occlusion) {
         this.base = base;
         this.flat = flat;
         this.occlusion = occlusion;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         if (this.flat == this.flat) {
            out.flat = this.flat;
            out.flatWeight = 1.0F;
         }

         if (this.occlusion == this.occlusion) {
            out.occlusion = this.occlusion;
            out.occlusionWeight = 1.0F;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Solid extends Paint {
      private final int argb;

      Solid(int argb) {
         this.argb = argb;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         out.set(this.argb);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Wood extends Paint {
      private final int light;
      private final int dark;
      private final float ax;
      private final float ay;
      private final float az;
      private final float dx;
      private final float dy;
      private final float dz;
      private final float spacing;

      Wood(int light, int dark, float ax, float ay, float az, float dx, float dy, float dz, float spacing) {
         this.light = light;
         this.dark = dark;
         this.ax = ax;
         this.ay = ay;
         this.az = az;
         float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         this.dx = l < 1.0E-6F ? 0.0F : dx / l;
         this.dy = l < 1.0E-6F ? 1.0F : dy / l;
         this.dz = l < 1.0E-6F ? 0.0F : dz / l;
         this.spacing = Math.max(0.05F, spacing);
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         float px = x - this.ax;
         float py = y - this.ay;
         float pz = z - this.az;
         float t = px * this.dx + py * this.dy + pz * this.dz;
         float rx = px - this.dx * t;
         float ry = py - this.dy * t;
         float rz = pz - this.dz * t;
         float r = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
         float f = 1.0F / this.spacing;
         float warp = Noise.fbm(rx * 0.3F + t * 0.021F, ry * 0.3F + t * 0.017F, rz * 0.3F + t * 0.013F, 2);
         float streak = Noise.fbm(rx * f * 1.6F + warp, ry * f * 1.6F + t * 0.045F, rz * f * 1.6F - warp, 3);
         float v = r * f + warp * 2.2F;
         float ring = v - (float)Math.floor(v);
         float line = smooth(0.7F, 0.85F, ring) * (1.0F - smooth(0.88F, 1.0F, ring));
         float k = 0.18F + streak * 0.9F + line * 0.22F;
         out.set(this.light);
         out.toward(this.dark, k < 0.0F ? 0.0F : Math.min(k, 1.0F));
      }
   }
}
