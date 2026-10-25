package net.spectre.client.module.impl.render.character.kit;

import java.util.Arrays;
import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Shapes {
   private static final ThreadLocal<Shapes.Scratch> SCRATCH = ThreadLocal.withInitial(Shapes.Scratch::new);

   private Shapes() {
   }

   public static Shape sphere(float cx, float cy, float cz, float r) {
      return new Shapes.Sphere(cx, cy, cz, r);
   }

   public static Shape ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz) {
      return new Shapes.Ellipsoid(cx, cy, cz, rx, ry, rz);
   }

   public static Shape box(float cx, float cy, float cz, float hx, float hy, float hz) {
      return new Shapes.Box(cx, cy, cz, hx, hy, hz, 0.0F);
   }

   public static Shape box(float cx, float cy, float cz, float hx, float hy, float hz, float round) {
      return new Shapes.Box(cx, cy, cz, hx, hy, hz, round);
   }

   public static Shape capsule(float ax, float ay, float az, float bx, float by, float bz, float r) {
      return new Shapes.RoundCone(ax, ay, az, r, bx, by, bz, r);
   }

   public static Shape cone(float ax, float ay, float az, float ra, float bx, float by, float bz, float rb) {
      return new Shapes.RoundCone(ax, ay, az, ra, bx, by, bz, rb);
   }

   public static Shape cylinder(float ax, float ay, float az, float bx, float by, float bz, float r, float round) {
      return new Shapes.Cylinder(ax, ay, az, bx, by, bz, r, round);
   }

   public static Shape torus(float cx, float cy, float cz, float major, float minor) {
      return new Shapes.Torus(cx, cy, cz, major, minor);
   }

   public static Shape plane(float px, float py, float pz, float nx, float ny, float nz) {
      return new Shapes.Plane(px, py, pz, nx, ny, nz);
   }

   public static Shape below(float y) {
      return new Shapes.Plane(0.0F, y, 0.0F, 0.0F, 1.0F, 0.0F);
   }

   public static Shape above(float y) {
      return new Shapes.Plane(0.0F, y, 0.0F, 0.0F, -1.0F, 0.0F);
   }

   public static Strand bezier(float ax, float ay, float az, float cx, float cy, float cz, float bx, float by, float bz, float r0, float r1) {
      return Strand.of(new float[]{ax, ay, az, r0, cx, cy, cz, (r0 + r1) * 0.5F, bx, by, bz, r1}, true);
   }

   public static Strand strand(float r0, float r1, float... xyz) {
      return Strand.tapered(r0, r1, xyz);
   }

   public static Strand tube(float... xyzr) {
      return Strand.of(xyzr, false);
   }

   public static Shape extrude(Shape2D profile, float halfDepth, float round) {
      return new Shapes.Extrude(profile, halfDepth, round);
   }

   public static Shape revolve(Shape2D profile) {
      return new Shapes.Revolve(profile);
   }

   public static Shape field(Shapes.Field field, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return new Shapes.Custom(field, minX, minY, minZ, maxX, maxY, maxZ);
   }

   public static Shape union(Shape... shapes) {
      return (Shape)(shapes.length == 1 ? shapes[0] : new Shapes.Union(0.0F, shapes));
   }

   public static Shape blend(float k, Shape... shapes) {
      return (Shape)(shapes.length == 1 ? shapes[0] : new Shapes.Union(k, shapes));
   }

   public static Shape subtract(Shape base, Shape cutter) {
      return new Shapes.Subtract(0.0F, base, cutter);
   }

   public static Shape subtract(float k, Shape base, Shape cutter) {
      return new Shapes.Subtract(k, base, cutter);
   }

   public static Shape intersect(Shape a, Shape b) {
      return new Shapes.Intersect(0.0F, a, b);
   }

   public static Shape intersect(float k, Shape a, Shape b) {
      return new Shapes.Intersect(k, a, b);
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   static float length(float x, float y, float z) {
      return (float)Math.sqrt(x * x + y * y + z * z);
   }

   static float boxLowerBound(Shape s, float x, float y, float z) {
      float cx = (s.minX + s.maxX) * 0.5F;
      float cy = (s.minY + s.maxY) * 0.5F;
      float cz = (s.minZ + s.maxZ) * 0.5F;
      float qx = Math.abs(x - cx) - (s.maxX - s.minX) * 0.5F;
      float qy = Math.abs(y - cy) - (s.maxY - s.minY) * 0.5F;
      float qz = Math.abs(z - cz) - (s.maxZ - s.minZ) * 0.5F;
      float ox = Math.max(qx, 0.0F);
      float oy = Math.max(qy, 0.0F);
      float oz = Math.max(qz, 0.0F);
      return (float)Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0F);
   }

   @Environment(EnvType.CLIENT)
   static final class Box extends Shape {
      private final float cx;
      private final float cy;
      private final float cz;
      private final float hx;
      private final float hy;
      private final float hz;
      private final float r;

      Box(float cx, float cy, float cz, float hx, float hy, float hz, float round) {
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.r = Math.max(0.0F, Math.min(round, Math.min(hx, Math.min(hy, hz))));
         this.hx = hx - this.r;
         this.hy = hy - this.r;
         this.hz = hz - this.r;
         this.bounds(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
      }

      @Override
      public float distance(float x, float y, float z) {
         float qx = Math.abs(x - this.cx) - this.hx;
         float qy = Math.abs(y - this.cy) - this.hy;
         float qz = Math.abs(z - this.cz) - this.hz;
         float ox = Math.max(qx, 0.0F);
         float oy = Math.max(qy, 0.0F);
         float oz = Math.max(qz, 0.0F);
         return Shapes.length(ox, oy, oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0F) - this.r;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Custom extends Shape {
      private final Shapes.Field field;

      Custom(Shapes.Field field, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
         this.field = field;
         this.bounds(minX, minY, minZ, maxX, maxY, maxZ);
      }

      @Override
      public float distance(float x, float y, float z) {
         return this.field.distance(x, y, z);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Cylinder extends Shape {
      private final float ax;
      private final float ay;
      private final float az;
      private final float bax;
      private final float bay;
      private final float baz;
      private final float baba;
      private final float r;
      private final float round;

      Cylinder(float ax, float ay, float az, float bx, float by, float bz, float r, float round) {
         float dx = bx - ax;
         float dy = by - ay;
         float dz = bz - az;
         float len = Shapes.length(dx, dy, dz);
         float rd = Math.max(0.0F, Math.min(round, Math.min(r, len * 0.5F)));
         float ux = len > 1.0E-6F ? dx / len : 0.0F;
         float uy = len > 1.0E-6F ? dy / len : 1.0F;
         float uz = len > 1.0E-6F ? dz / len : 0.0F;
         this.ax = ax + ux * rd;
         this.ay = ay + uy * rd;
         this.az = az + uz * rd;
         this.bax = bx - ux * rd - this.ax;
         this.bay = by - uy * rd - this.ay;
         this.baz = bz - uz * rd - this.az;
         this.baba = Math.max(1.0E-8F, this.bax * this.bax + this.bay * this.bay + this.baz * this.baz);
         this.r = r - rd;
         this.round = rd;
         float ex = r * (float)Math.sqrt(Math.max(0.0F, 1.0F - ux * ux));
         float ey = r * (float)Math.sqrt(Math.max(0.0F, 1.0F - uy * uy));
         float ez = r * (float)Math.sqrt(Math.max(0.0F, 1.0F - uz * uz));
         this.bounds(Math.min(ax, bx) - ex, Math.min(ay, by) - ey, Math.min(az, bz) - ez, Math.max(ax, bx) + ex, Math.max(ay, by) + ey, Math.max(az, bz) + ez);
      }

      @Override
      public float distance(float x, float y, float z) {
         float pax = x - this.ax;
         float pay = y - this.ay;
         float paz = z - this.az;
         float paba = pax * this.bax + pay * this.bay + paz * this.baz;
         float xx = Shapes.length(pax * this.baba - this.bax * paba, pay * this.baba - this.bay * paba, paz * this.baba - this.baz * paba) - this.r * this.baba;
         float yy = Math.abs(paba - this.baba * 0.5F) - this.baba * 0.5F;
         float x2 = xx * xx;
         float y2 = yy * yy * this.baba;
         float d = Math.max(xx, yy) < 0.0F ? -Math.min(x2, y2) : (xx > 0.0F ? x2 : 0.0F) + (yy > 0.0F ? y2 : 0.0F);
         return Math.signum(d) * (float)Math.sqrt(Math.abs(d)) / this.baba - this.round;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Ellipsoid extends Shape {
      private final float cx;
      private final float cy;
      private final float cz;
      private final float ix;
      private final float iy;
      private final float iz;
      private final float iix;
      private final float iiy;
      private final float iiz;
      private final float rmin;

      Ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz) {
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.ix = 1.0F / rx;
         this.iy = 1.0F / ry;
         this.iz = 1.0F / rz;
         this.iix = this.ix * this.ix;
         this.iiy = this.iy * this.iy;
         this.iiz = this.iz * this.iz;
         this.rmin = Math.min(rx, Math.min(ry, rz));
         this.bounds(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz);
      }

      @Override
      public float distance(float x, float y, float z) {
         float px = x - this.cx;
         float py = y - this.cy;
         float pz = z - this.cz;
         float k0 = Shapes.length(px * this.ix, py * this.iy, pz * this.iz);
         float k1 = Shapes.length(px * this.iix, py * this.iiy, pz * this.iiz);
         return k1 < 1.0E-9F ? -this.rmin : k0 * (k0 - 1.0F) / k1;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Extrude extends Shape {
      private final Shape2D profile;
      private final float h;
      private final float r;

      Extrude(Shape2D profile, float halfDepth, float round) {
         this.profile = profile;
         this.r = Math.max(0.0F, Math.min(round, halfDepth));
         this.h = halfDepth;
         this.bounds(profile.minX, profile.minY, -halfDepth, profile.maxX, profile.maxY, halfDepth);
      }

      @Override
      public float distance(float x, float y, float z) {
         float d2 = this.profile.distance(x, y) + this.r;
         float wz = Math.abs(z) - this.h + this.r;
         float ox = Math.max(d2, 0.0F);
         float oz = Math.max(wz, 0.0F);
         return Math.min(Math.max(d2, wz), 0.0F) + (float)Math.sqrt(ox * ox + oz * oz) - this.r;
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Field {
      float distance(float var1, float var2, float var3);
   }

   @Environment(EnvType.CLIENT)
   static final class Intersect extends Shape {
      private final Shape a;
      private final Shape b;
      private final float k;

      Intersect(float k, Shape a, Shape b) {
         this.k = Math.max(0.0F, k);
         this.a = a;
         this.b = b;
         if (a.bounded() && b.bounded()) {
            this.bounds(
               Math.max(a.minX, b.minX),
               Math.max(a.minY, b.minY),
               Math.max(a.minZ, b.minZ),
               Math.min(a.maxX, b.maxX),
               Math.min(a.maxY, b.maxY),
               Math.min(a.maxZ, b.maxZ)
            );
         } else if (a.bounded()) {
            this.boundsOf(a, 0.0F);
         } else if (b.bounded()) {
            this.boundsOf(b, 0.0F);
         }
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape pa = this.a.prune(x0, y0, z0, x1, y1, z1);
         Shape pb = this.b.prune(x0, y0, z0, x1, y1, z1);
         return pa == this.a && pb == this.b ? this : new Shapes.Intersect(this.k, pa, pb);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape pa = this.a.map(f);
         Shape pb = this.b.map(f);
         return f.apply((Shape)(pa == this.a && pb == this.b ? this : new Shapes.Intersect(this.k, pa, pb).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         float da = this.a.distance(x, y, z);
         float db = this.b.distance(x, y, z);
         if (this.k <= 0.0F) {
            return Math.max(da, db);
         } else {
            float h = Shapes.clamp01(0.5F - 0.5F * (db - da) / this.k);
            return db + (da - db) * h + this.k * h * (1.0F - h);
         }
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         float da = this.a.sample(x, y, z, out);
         if (this.b.paint == null) {
            float db = this.b.distance(x, y, z);
            if (this.k <= 0.0F) {
               return Math.max(da, db);
            } else {
               float h = Shapes.clamp01(0.5F - 0.5F * (db - da) / this.k);
               return db + (da - db) * h + this.k * h * (1.0F - h);
            }
         } else {
            Sample tmp = new Sample();
            float db = this.b.sample(x, y, z, tmp);
            if (this.k <= 0.0F) {
               if (db > da) {
                  out.set(tmp);
                  return db;
               } else {
                  return da;
               }
            } else {
               float h = Shapes.clamp01(0.5F - 0.5F * (db - da) / this.k);
               out.mix(tmp, 1.0F - h);
               return db + (da - db) * h + this.k * h * (1.0F - h);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Mirror extends Shape {
      private final Shape child;
      private final boolean keep;

      Mirror(Shape child, boolean keep) {
         this.child = child;
         this.keep = keep;
         if (child.bounded()) {
            if (keep) {
               this.bounds(Math.min(child.minX, -child.maxX), child.minY, child.minZ, Math.max(child.maxX, -child.minX), child.maxY, child.maxZ);
            } else {
               this.bounds(-child.maxX, child.minY, child.minZ, -child.minX, child.maxY, child.maxZ);
            }
         }
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape p = this.keep ? this.child.prune(Math.min(x0, -x1), y0, z0, Math.max(x1, -x0), y1, z1) : this.child.prune(-x1, y0, z0, -x0, y1, z1);
         return p == this.child ? this : new Shapes.Mirror(p, this.keep);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Mirror(p, this.keep).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         if (!this.keep) {
            return this.child.distance(-x, y, z);
         } else {
            float a = this.child.distance(x, y, z);
            return Shapes.boxLowerBound(this.child, -x, y, z) >= a ? a : Math.min(a, this.child.distance(-x, y, z));
         }
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         if (!this.keep) {
            return this.child.sample(-x, y, z, out);
         } else {
            float a = this.child.distance(x, y, z);
            float b = Shapes.boxLowerBound(this.child, -x, y, z) >= a ? Float.MAX_VALUE : this.child.distance(-x, y, z);
            return b < a ? this.child.sample(-x, y, z, out) : this.child.sample(x, y, z, out);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Plane extends Shape {
      private final float px;
      private final float py;
      private final float pz;
      private final float nx;
      private final float ny;
      private final float nz;

      Plane(float px, float py, float pz, float nx, float ny, float nz) {
         float l = Shapes.length(nx, ny, nz);
         this.px = px;
         this.py = py;
         this.pz = pz;
         this.nx = l < 1.0E-8F ? 0.0F : nx / l;
         this.ny = l < 1.0E-8F ? 1.0F : ny / l;
         this.nz = l < 1.0E-8F ? 0.0F : nz / l;
      }

      @Override
      public float distance(float x, float y, float z) {
         return (x - this.px) * this.nx + (y - this.py) * this.ny + (z - this.pz) * this.nz;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Revolve extends Shape {
      private final Shape2D profile;

      Revolve(Shape2D profile) {
         this.profile = profile;
         float rad = Math.max(Math.abs(profile.minX), Math.abs(profile.maxX));
         this.bounds(-rad, profile.minY, -rad, rad, profile.maxY, rad);
      }

      @Override
      public float distance(float x, float y, float z) {
         return this.profile.distance((float)Math.sqrt(x * x + z * z), y);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Rotate extends Shape {
      private final Shape child;
      private final Mat3 inv;
      private final float cx;
      private final float cy;
      private final float cz;

      Rotate(Shape child, Mat3 m, float cx, float cy, float cz) {
         this.child = child;
         this.inv = m.transpose();
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         if (child.bounded()) {
            float x0 = 100000.0F;
            float y0 = 100000.0F;
            float z0 = 100000.0F;
            float x1 = -100000.0F;
            float y1 = -100000.0F;
            float z1 = -100000.0F;

            for (int i = 0; i < 8; i++) {
               float px = ((i & 1) == 0 ? child.minX : child.maxX) - cx;
               float py = ((i & 2) == 0 ? child.minY : child.maxY) - cy;
               float pz = ((i & 4) == 0 ? child.minZ : child.maxZ) - cz;
               float rx = m.x(px, py, pz) + cx;
               float ry = m.y(px, py, pz) + cy;
               float rz = m.z(px, py, pz) + cz;
               x0 = Math.min(x0, rx);
               y0 = Math.min(y0, ry);
               z0 = Math.min(z0, rz);
               x1 = Math.max(x1, rx);
               y1 = Math.max(y1, ry);
               z1 = Math.max(z1, rz);
            }

            this.bounds(x0, y0, z0, x1, y1, z1);
         }
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         float lx0 = 100000.0F;
         float ly0 = 100000.0F;
         float lz0 = 100000.0F;
         float lx1 = -100000.0F;
         float ly1 = -100000.0F;
         float lz1 = -100000.0F;

         for (int i = 0; i < 8; i++) {
            float px = ((i & 1) == 0 ? x0 : x1) - this.cx;
            float py = ((i & 2) == 0 ? y0 : y1) - this.cy;
            float pz = ((i & 4) == 0 ? z0 : z1) - this.cz;
            float rx = this.inv.x(px, py, pz) + this.cx;
            float ry = this.inv.y(px, py, pz) + this.cy;
            float rz = this.inv.z(px, py, pz) + this.cz;
            lx0 = Math.min(lx0, rx);
            ly0 = Math.min(ly0, ry);
            lz0 = Math.min(lz0, rz);
            lx1 = Math.max(lx1, rx);
            ly1 = Math.max(ly1, ry);
            lz1 = Math.max(lz1, rz);
         }

         Shape p = this.child.prune(lx0, ly0, lz0, lx1, ly1, lz1);
         return p == this.child ? this : new Shapes.Rotate(p, this.inv.transpose(), this.cx, this.cy, this.cz);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Rotate(p, this.inv.transpose(), this.cx, this.cy, this.cz).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         float px = x - this.cx;
         float py = y - this.cy;
         float pz = z - this.cz;
         return this.child.distance(this.inv.x(px, py, pz) + this.cx, this.inv.y(px, py, pz) + this.cy, this.inv.z(px, py, pz) + this.cz);
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         float px = x - this.cx;
         float py = y - this.cy;
         float pz = z - this.cz;
         return this.child.sample(this.inv.x(px, py, pz) + this.cx, this.inv.y(px, py, pz) + this.cy, this.inv.z(px, py, pz) + this.cz, out);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Roughen extends Shape {
      private final Shape child;
      private final float amp;
      private final float freq;

      Roughen(Shape child, float amplitude, float size) {
         this.child = child;
         this.amp = amplitude;
         this.freq = 1.0F / Math.max(0.01F, size);
         this.boundsOf(child, Math.abs(amplitude));
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape p = this.child.prune(x0, y0, z0, x1, y1, z1);
         return p == this.child ? this : new Shapes.Roughen(p, this.amp, 1.0F / this.freq);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Roughen(p, this.amp, 1.0F / this.freq).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         float d = this.child.distance(x, y, z);
         return d > this.amp * 1.5F + 0.5F ? d : d + this.amp * Noise.noise(x * this.freq, y * this.freq, z * this.freq);
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         return this.child.sample(x, y, z, out) + this.amp * Noise.noise(x * this.freq, y * this.freq, z * this.freq);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Round extends Shape {
      private final Shape child;
      private final float r;

      Round(Shape child, float r) {
         this.child = child;
         this.r = r;
         this.boundsOf(child, Math.max(0.0F, r));
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape p = this.child.prune(x0, y0, z0, x1, y1, z1);
         return p == this.child ? this : new Shapes.Round(p, this.r);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Round(p, this.r).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         return this.child.distance(x, y, z) - this.r;
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         return this.child.sample(x, y, z, out) - this.r;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class RoundCone extends Shape {
      private final float ax;
      private final float ay;
      private final float az;
      private final float bax;
      private final float bay;
      private final float baz;
      private final float ra;
      private final float rb;
      private final float l2;
      private final float rr;
      private final float a2;
      private final float il2;
      private final boolean sphere;
      private final float sx;
      private final float sy;
      private final float sz;
      private final float sr;

      RoundCone(float ax, float ay, float az, float ra, float bx, float by, float bz, float rb) {
         this.ax = ax;
         this.ay = ay;
         this.az = az;
         this.ra = ra;
         this.rb = rb;
         this.bax = bx - ax;
         this.bay = by - ay;
         this.baz = bz - az;
         this.l2 = this.bax * this.bax + this.bay * this.bay + this.baz * this.baz;
         this.rr = ra - rb;
         this.a2 = this.l2 - this.rr * this.rr;
         this.il2 = this.l2 > 1.0E-12F ? 1.0F / this.l2 : 0.0F;
         this.sphere = this.a2 <= 1.0E-6F;
         boolean first = ra >= rb;
         this.sx = first ? ax : bx;
         this.sy = first ? ay : by;
         this.sz = first ? az : bz;
         this.sr = Math.max(ra, rb);
         this.bounds(
            Math.min(ax - ra, bx - rb),
            Math.min(ay - ra, by - rb),
            Math.min(az - ra, bz - rb),
            Math.max(ax + ra, bx + rb),
            Math.max(ay + ra, by + rb),
            Math.max(az + ra, bz + rb)
         );
      }

      @Override
      public float distance(float x, float y, float z) {
         if (this.sphere) {
            return Shapes.length(x - this.sx, y - this.sy, z - this.sz) - this.sr;
         } else {
            float pax = x - this.ax;
            float pay = y - this.ay;
            float paz = z - this.az;
            float yy = pax * this.bax + pay * this.bay + paz * this.baz;
            float zz = yy - this.l2;
            float qx = pax * this.l2 - this.bax * yy;
            float qy = pay * this.l2 - this.bay * yy;
            float qz = paz * this.l2 - this.baz * yy;
            float x2 = qx * qx + qy * qy + qz * qz;
            float y2 = yy * yy * this.l2;
            float z2 = zz * zz * this.l2;
            float k = Math.signum(this.rr) * this.rr * this.rr * x2;
            if (Math.signum(zz) * this.a2 * z2 > k) {
               return (float)Math.sqrt(x2 + z2) * this.il2 - this.rb;
            } else {
               return Math.signum(yy) * this.a2 * y2 < k
                  ? (float)Math.sqrt(x2 + y2) * this.il2 - this.ra
                  : ((float)Math.sqrt(x2 * this.a2 * this.il2) + yy * this.rr) * this.il2 - this.ra;
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Scale extends Shape {
      private final Shape child;
      private final float k;
      private final float ik;
      private final float cx;
      private final float cy;
      private final float cz;

      Scale(Shape child, float k, float cx, float cy, float cz) {
         this.child = child;
         this.k = k;
         this.ik = 1.0F / k;
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         if (child.bounded()) {
            this.bounds(
               (child.minX - cx) * k + cx,
               (child.minY - cy) * k + cy,
               (child.minZ - cz) * k + cz,
               (child.maxX - cx) * k + cx,
               (child.maxY - cy) * k + cy,
               (child.maxZ - cz) * k + cz
            );
         }
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         float ax = (x0 - this.cx) * this.ik + this.cx;
         float bx = (x1 - this.cx) * this.ik + this.cx;
         float ay = (y0 - this.cy) * this.ik + this.cy;
         float by = (y1 - this.cy) * this.ik + this.cy;
         float az = (z0 - this.cz) * this.ik + this.cz;
         float bz = (z1 - this.cz) * this.ik + this.cz;
         Shape p = this.child.prune(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
         return p == this.child ? this : new Shapes.Scale(p, this.k, this.cx, this.cy, this.cz);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Scale(p, this.k, this.cx, this.cy, this.cz).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         return this.child.distance((x - this.cx) * this.ik + this.cx, (y - this.cy) * this.ik + this.cy, (z - this.cz) * this.ik + this.cz) * this.k;
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         return this.child.sample((x - this.cx) * this.ik + this.cx, (y - this.cy) * this.ik + this.cy, (z - this.cz) * this.ik + this.cz, out) * this.k;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Scratch {
      final float[][] pool = new float[64][];
      int depth;

      float[] take(int size) {
         int d = this.depth++;
         if (d >= this.pool.length) {
            return new float[size];
         } else {
            float[] a = this.pool[d];
            if (a == null || a.length < size) {
               a = new float[Math.max(size, 16)];
               this.pool[d] = a;
            }

            return a;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Shell extends Shape {
      private final Shape child;
      private final float half;

      Shell(Shape child, float thickness) {
         this.child = child;
         this.half = Math.abs(thickness) * 0.5F;
         this.boundsOf(child, this.half);
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape p = this.child.prune(x0, y0, z0, x1, y1, z1);
         return p == this.child ? this : new Shapes.Shell(p, this.half * 2.0F);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Shell(p, this.half * 2.0F).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         return Math.abs(this.child.distance(x, y, z)) - this.half;
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         return Math.abs(this.child.sample(x, y, z, out)) - this.half;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Sphere extends Shape {
      private final float cx;
      private final float cy;
      private final float cz;
      private final float r;

      Sphere(float cx, float cy, float cz, float r) {
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.r = r;
         this.bounds(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
      }

      @Override
      public float distance(float x, float y, float z) {
         return Shapes.length(x - this.cx, y - this.cy, z - this.cz) - this.r;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Subtract extends Shape {
      private final Shape base;
      private final Shape cutter;
      private final float k;

      Subtract(float k, Shape base, Shape cutter) {
         this.k = Math.max(0.0F, k);
         this.base = base;
         this.cutter = cutter;
         this.boundsOf(base, this.k * 0.3F);
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape a = this.base.prune(x0, y0, z0, x1, y1, z1);
         Shape b = this.cutter.prune(x0, y0, z0, x1, y1, z1);
         return a == this.base && b == this.cutter ? this : new Shapes.Subtract(this.k, a, b);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape a = this.base.map(f);
         Shape b = this.cutter.map(f);
         return f.apply((Shape)(a == this.base && b == this.cutter ? this : new Shapes.Subtract(this.k, a, b).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         float a = this.base.distance(x, y, z);
         if (Shapes.boxLowerBound(this.cutter, x, y, z) >= this.k - a) {
            return a;
         } else {
            float b = this.cutter.distance(x, y, z);
            if (this.k <= 0.0F) {
               return Math.max(a, -b);
            } else {
               float h = Shapes.clamp01(0.5F - 0.5F * (a + b) / this.k);
               return a + (-b - a) * h + this.k * h * (1.0F - h);
            }
         }
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         float a = this.base.sample(x, y, z, out);
         if (Shapes.boxLowerBound(this.cutter, x, y, z) >= this.k - a) {
            return a;
         } else if (this.cutter.paint == null) {
            float b = this.cutter.distance(x, y, z);
            if (this.k <= 0.0F) {
               return Math.max(a, -b);
            } else {
               float h = Shapes.clamp01(0.5F - 0.5F * (a + b) / this.k);
               return a + (-b - a) * h + this.k * h * (1.0F - h);
            }
         } else {
            Sample tmp = new Sample();
            float b = this.cutter.sample(x, y, z, tmp);
            if (this.k <= 0.0F) {
               if (-b > a) {
                  out.set(tmp);
                  return -b;
               } else {
                  return a;
               }
            } else {
               float h = Shapes.clamp01(0.5F - 0.5F * (a + b) / this.k);
               out.mix(tmp, h);
               return a + (-b - a) * h + this.k * h * (1.0F - h);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Torus extends Shape {
      private final float cx;
      private final float cy;
      private final float cz;
      private final float major;
      private final float minor;

      Torus(float cx, float cy, float cz, float major, float minor) {
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.major = major;
         this.minor = minor;
         float e = major + minor;
         this.bounds(cx - e, cy - minor, cz - e, cx + e, cy + minor, cz + e);
      }

      @Override
      public float distance(float x, float y, float z) {
         float px = x - this.cx;
         float pz = z - this.cz;
         float qx = (float)Math.sqrt(px * px + pz * pz) - this.major;
         float qy = y - this.cy;
         return (float)Math.sqrt(qx * qx + qy * qy) - this.minor;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Translate extends Shape {
      private final Shape child;
      private final float dx;
      private final float dy;
      private final float dz;

      Translate(Shape child, float dx, float dy, float dz) {
         this.child = child;
         this.dx = dx;
         this.dy = dy;
         this.dz = dz;
         if (child.bounded()) {
            this.bounds(child.minX + dx, child.minY + dy, child.minZ + dz, child.maxX + dx, child.maxY + dy, child.maxZ + dz);
         }
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         Shape p = this.child.prune(x0 - this.dx, y0 - this.dy, z0 - this.dz, x1 - this.dx, y1 - this.dy, z1 - this.dz);
         return p == this.child ? this : new Shapes.Translate(p, this.dx, this.dy, this.dz);
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Translate(p, this.dx, this.dy, this.dz).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         return this.child.distance(x - this.dx, y - this.dy, z - this.dz);
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         return this.child.sample(x - this.dx, y - this.dy, z - this.dz, out);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Twist extends Shape {
      private final Shape child;
      private final float k;
      private final float cx;
      private final float cz;

      Twist(Shape child, float degreesPerUnit, float cx, float cz) {
         this.child = child;
         this.k = (float)Math.toRadians(degreesPerUnit);
         this.cx = cx;
         this.cz = cz;
         if (child.bounded()) {
            float r = 0.0F;

            for (int i = 0; i < 4; i++) {
               float px = ((i & 1) == 0 ? child.minX : child.maxX) - cx;
               float pz = ((i & 2) == 0 ? child.minZ : child.maxZ) - cz;
               r = Math.max(r, (float)Math.sqrt(px * px + pz * pz));
            }

            this.bounds(cx - r, child.minY, cz - r, cx + r, child.maxY, cz + r);
         }
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape p = this.child.map(f);
         return f.apply((Shape)(p == this.child ? this : new Shapes.Twist(p, (float)Math.toDegrees(this.k), this.cx, this.cz).inherit(this)));
      }

      @Override
      public float distance(float x, float y, float z) {
         float a = -this.k * y;
         float c = (float)Math.cos(a);
         float s = (float)Math.sin(a);
         float px = x - this.cx;
         float pz = z - this.cz;
         return this.child.distance(c * px + s * pz + this.cx, y, -s * px + c * pz + this.cz);
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         float a = -this.k * y;
         float c = (float)Math.cos(a);
         float s = (float)Math.sin(a);
         float px = x - this.cx;
         float pz = z - this.cz;
         return this.child.sample(c * px + s * pz + this.cx, y, -s * px + c * pz + this.cz, out);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Union extends Shape {
      private final Shape[] kids;
      private final float k;

      Union(float k, Shape[] kids) {
         if (kids.length == 0) {
            throw new IllegalArgumentException("union needs at least one shape");
         } else {
            this.k = Math.max(0.0F, k);
            this.kids = (Shape[])kids.clone();
            boolean open = false;
            float x0 = 100000.0F;
            float y0 = 100000.0F;
            float z0 = 100000.0F;
            float x1 = -100000.0F;
            float y1 = -100000.0F;
            float z1 = -100000.0F;

            for (Shape s : this.kids) {
               if (!s.bounded()) {
                  open = true;
               } else {
                  x0 = Math.min(x0, s.minX);
                  y0 = Math.min(y0, s.minY);
                  z0 = Math.min(z0, s.minZ);
                  x1 = Math.max(x1, s.maxX);
                  y1 = Math.max(y1, s.maxY);
                  z1 = Math.max(z1, s.maxZ);
               }
            }

            if (!open) {
               float pad = this.k * 0.3F;
               this.bounds(x0 - pad, y0 - pad, z0 - pad, x1 + pad, y1 + pad, z1 + pad);
            }
         }
      }

      @Override
      Shape map(UnaryOperator<Shape> f) {
         Shape[] m = new Shape[this.kids.length];
         boolean changed = false;

         for (int i = 0; i < this.kids.length; i++) {
            m[i] = this.kids[i].map(f);
            changed |= m[i] != this.kids[i];
         }

         return f.apply((Shape)(changed ? new Shapes.Union(this.k, m).inherit(this) : this));
      }

      @Override
      Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
         float cx = (x0 + x1) * 0.5F;
         float cy = (y0 + y1) * 0.5F;
         float cz = (z0 + z1) * 0.5F;
         float r = Shapes.length(x1 - x0, y1 - y0, z1 - z0) * 0.5F * 1.2F;
         float upper = Float.MAX_VALUE;

         for (Shape s : this.kids) {
            if (!(s.boxGap(x0, y0, z0, x1, y1, z1) >= upper + this.k)) {
               upper = Math.min(upper, s.distance(cx, cy, cz) + r);
            }
         }

         Shape[] kept = new Shape[this.kids.length];
         int n = 0;
         boolean changed = false;

         for (Shape sx : this.kids) {
            if (sx.boxGap(x0, y0, z0, x1, y1, z1) >= upper + this.k) {
               changed = true;
            } else {
               Shape p = sx.prune(x0, y0, z0, x1, y1, z1);
               changed |= p != sx;
               kept[n++] = p;
            }
         }

         if (!changed || n == 0) {
            return this;
         } else {
            return (Shape)(n == 1 ? kept[0] : new Shapes.Union(this.k, Arrays.copyOf(kept, n)));
         }
      }

      private int nearest(float x, float y, float z, float[] bound) {
         int first = 0;
         float min = Float.MAX_VALUE;

         for (int i = 0; i < this.kids.length; i++) {
            float b = Shapes.boxLowerBound(this.kids[i], x, y, z);
            if (bound != null) {
               bound[i] = b;
            }

            if (b < min) {
               min = b;
               first = i;
            }
         }

         return first;
      }

      @Override
      public float distance(float x, float y, float z) {
         Shape[] s = this.kids;
         if (s.length <= 4) {
            return this.distance(x, y, z, null);
         } else {
            Shapes.Scratch scratch = Shapes.SCRATCH.get();
            float[] bound = scratch.take(s.length);

            float var7;
            try {
               var7 = this.distance(x, y, z, bound);
            } finally {
               scratch.depth--;
            }

            return var7;
         }
      }

      @Override
      float distance(float x, float y, float z, float limit) {
         Shape[] s = this.kids;
         if (s.length <= 4) {
            return this.evaluate(x, y, z, null, limit);
         } else {
            Shapes.Scratch scratch = Shapes.SCRATCH.get();
            float[] bound = scratch.take(s.length);

            float var8;
            try {
               var8 = this.evaluate(x, y, z, bound, limit);
            } finally {
               scratch.depth--;
            }

            return var8;
         }
      }

      private float distance(float x, float y, float z, float[] bound) {
         return this.evaluate(x, y, z, bound, Float.MAX_VALUE);
      }

      private float evaluate(float x, float y, float z, float[] bound, float limit) {
         Shape[] s = this.kids;
         int first = this.nearest(x, y, z, bound);
         float firstBound = bound != null ? bound[first] : Shapes.boxLowerBound(s[first], x, y, z);
         if (firstBound >= limit) {
            return firstBound;
         } else {
            float kk = this.k;
            float best = s[first].distance(x, y, z, limit == Float.MAX_VALUE ? Float.MAX_VALUE : limit + kk);

            for (int i = 0; i < s.length; i++) {
               if (i != first) {
                  float b = bound != null ? bound[i] : Shapes.boxLowerBound(s[i], x, y, z);
                  if (!(b >= best + kk)) {
                     float d = s[i].distance(x, y, z, best + kk);
                     if (kk > 0.0F) {
                        float h = Shapes.clamp01(0.5F + 0.5F * (d - best) / kk);
                        best = d + (best - d) * h - kk * h * (1.0F - h);
                     } else if (d < best) {
                        best = d;
                     }
                  }
               }
            }

            return best;
         }
      }

      @Override
      float shade(float x, float y, float z, Sample out) {
         Shape[] s = this.kids;
         int first = this.nearest(x, y, z, null);
         float best = s[first].sample(x, y, z, out);
         Sample tmp = null;
         float kk = this.k;

         for (int i = 0; i < s.length; i++) {
            if (i != first) {
               Shape c = s[i];
               if (!(Shapes.boxLowerBound(c, x, y, z) >= best + kk)) {
                  if (tmp == null) {
                     tmp = new Sample();
                  }

                  float d = c.sample(x, y, z, tmp);
                  if (kk > 0.0F) {
                     float h = Shapes.clamp01(0.5F + 0.5F * (d - best) / kk);
                     best = d + (best - d) * h - kk * h * (1.0F - h);
                     out.mix(tmp, 1.0F - h);
                  } else if (d < best) {
                     best = d;
                     out.set(tmp);
                  }
               }
            }
         }

         return best;
      }
   }
}
