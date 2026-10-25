package net.spectre.client.util.glyph;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class GlyphSwarm {
   public static final byte GHOST = 0;
   public static final byte CRESCENT = 1;
   public static final byte GRAIN = 2;
   public static final byte SPARK = 3;
   public static final int WORST_QUADS = (
         Math.max(
               Math.max(GlyphShapes.GHOST.length, GlyphShapes.CRESCENT.length),
               Math.max(GlyphShapes.SPARK.length, Math.max(GlyphShapes.GRAIN.length, GlyphShapes.DIAMOND.length))
            )
            + GlyphShapes.OCTAGON.length
      )
      / 8;
   private static final double CULL_DISTANCE_SQR = 9216.0;
   private static final double DETAIL_DISTANCE_SQR = 484.0;
   private static final float HALO_SCALE = 2.3F;
   private static final float HALO_ALPHA = 0.17F;
   private final int capacity;
   private final double[] x;
   private final double[] y;
   private final double[] z;
   private final float[] vx;
   private final float[] vy;
   private final float[] vz;
   private final float[] age;
   private final float[] life;
   private final float[] size;
   private final float[] rot;
   private final float[] spin;
   private final float[] seed;
   private final byte[] kind;
   private final int[] color;
   private int count;

   public GlyphSwarm(int capacity) {
      this.capacity = capacity;
      this.x = new double[capacity];
      this.y = new double[capacity];
      this.z = new double[capacity];
      this.vx = new float[capacity];
      this.vy = new float[capacity];
      this.vz = new float[capacity];
      this.age = new float[capacity];
      this.life = new float[capacity];
      this.size = new float[capacity];
      this.rot = new float[capacity];
      this.spin = new float[capacity];
      this.seed = new float[capacity];
      this.kind = new byte[capacity];
      this.color = new int[capacity];
   }

   public int count() {
      return this.count;
   }

   public void clear() {
      this.count = 0;
   }

   public void spawn(
      byte type,
      double px,
      double py,
      double pz,
      float velX,
      float velY,
      float velZ,
      float lifetime,
      float scale,
      float angle,
      float turn,
      float noise,
      int rgb,
      int limit
   ) {
      int cap = Math.min(this.capacity, Math.max(1, limit));
      int slot;
      if (this.count < cap) {
         slot = this.count++;
      } else {
         slot = this.oldest();
      }

      this.x[slot] = px;
      this.y[slot] = py;
      this.z[slot] = pz;
      this.vx[slot] = velX;
      this.vy[slot] = velY;
      this.vz[slot] = velZ;
      this.age[slot] = 0.0F;
      this.life[slot] = Math.max(0.05F, lifetime);
      this.size[slot] = scale;
      this.rot[slot] = angle;
      this.spin[slot] = turn;
      this.seed[slot] = noise;
      this.kind[slot] = type;
      this.color[slot] = rgb;
   }

   public void trim(int limit) {
      while (this.count > Math.max(0, limit)) {
         this.remove(this.oldest());
      }
   }

   public void step(float dt) {
      if (!(dt <= 0.0F)) {
         for (int i = this.count - 1; i >= 0; i--) {
            float a = this.age[i] + dt;
            if (a >= this.life[i]) {
               this.remove(i);
            } else {
               this.age[i] = a;
               float drag;
               float lift;
               switch (this.kind[i]) {
                  case 0:
                     drag = 1.6F;
                     lift = 0.7F;
                     this.vx[i] = this.vx[i] + Mth.sin(a * 2.3F + this.seed[i] * 6.0F) * 0.35F * dt;
                     this.vz[i] = this.vz[i] + Mth.cos(a * 1.9F + this.seed[i] * 6.0F) * 0.35F * dt;
                     break;
                  case 1:
                     drag = 0.8F;
                     lift = -1.1F;
                     float turn = (this.seed[i] > 0.5F ? 1.8F : -1.8F) * dt;
                     float cos = Mth.cos(turn);
                     float sin = Mth.sin(turn);
                     float nx = this.vx[i] * cos - this.vz[i] * sin;
                     this.vz[i] = this.vx[i] * sin + this.vz[i] * cos;
                     this.vx[i] = nx;
                     break;
                  case 2:
                     drag = 0.9F;
                     lift = -6.5F;
                     break;
                  default:
                     drag = 3.4F;
                     lift = -1.6F;
               }

               float keep = Math.max(0.0F, 1.0F - drag * dt);
               this.vx[i] = this.vx[i] * keep;
               this.vy[i] = this.vy[i] * keep + lift * dt;
               this.vz[i] = this.vz[i] * keep;
               this.x[i] = this.x[i] + this.vx[i] * dt;
               this.y[i] = this.y[i] + this.vy[i] * dt;
               this.z[i] = this.z[i] + this.vz[i] * dt;
               this.rot[i] = this.rot[i] + this.spin[i] * dt;
            }
         }
      }
   }

   public void draw(GlyphCanvas canvas, float opacity, boolean halo) {
      for (int i = 0; i < this.count; i++) {
         double px = this.x[i];
         double py = this.y[i];
         double pz = this.z[i];
         if (!(canvas.depth(px, py, pz) < 0.05)) {
            double distSqr = canvas.distanceSqr(px, py, pz);
            if (!(distSqr > 9216.0)) {
               float t = this.age[i] / this.life[i];
               float fade = Math.min(1.0F, t * 9.0F) * (1.0F - t * t);
               float scale = this.size[i];
               float squash = 1.0F;
               float[] shape;
               switch (this.kind[i]) {
                  case 0:
                     shape = GlyphShapes.GHOST;
                     scale *= bloom(Math.min(1.0F, t * 5.0F)) * (1.0F - 0.35F * t);
                     break;
                  case 1:
                     shape = GlyphShapes.CRESCENT;
                     squash = 0.72F + 0.28F * Math.abs(Mth.sin(this.age[i] * 15.0F + this.seed[i] * 9.0F));
                     break;
                  case 2:
                     shape = GlyphShapes.GRAIN;
                     break;
                  default:
                     shape = GlyphShapes.SPARK;
                     scale *= 1.0F - 0.6F * t;
                     fade *= 0.75F + 0.25F * Mth.sin(this.age[i] * 23.0F + this.seed[i] * 12.0F);
               }

               float alpha = fade * opacity;
               if (!(alpha < 0.012F)) {
                  int rgb = this.color[i];
                  boolean detailed = distSqr < 484.0;
                  if (halo && detailed) {
                     canvas.sprite(GlyphShapes.OCTAGON, px, py, pz, scale * 2.3F, 0.0F, GlyphTint.alpha(rgb, alpha * 0.17F));
                  }

                  if (!detailed && shape != GlyphShapes.GRAIN) {
                     shape = GlyphShapes.DIAMOND;
                  }

                  canvas.sprite(shape, px, py, pz, scale * squash, scale, this.rot[i], GlyphTint.alpha(rgb, alpha));
               }
            }
         }
      }
   }

   private static float bloom(float t) {
      float u = t - 1.0F;
      return 1.0F + 2.2F * u * u * u + 1.2F * u * u;
   }

   private int oldest() {
      int best = 0;
      float worst = -1.0F;

      for (int i = 0; i < this.count; i++) {
         float progress = this.age[i] / this.life[i];
         if (progress > worst) {
            worst = progress;
            best = i;
         }
      }

      return best;
   }

   private void remove(int i) {
      int last = --this.count;
      if (i != last) {
         this.x[i] = this.x[last];
         this.y[i] = this.y[last];
         this.z[i] = this.z[last];
         this.vx[i] = this.vx[last];
         this.vy[i] = this.vy[last];
         this.vz[i] = this.vz[last];
         this.age[i] = this.age[last];
         this.life[i] = this.life[last];
         this.size[i] = this.size[last];
         this.rot[i] = this.rot[last];
         this.spin[i] = this.spin[last];
         this.seed[i] = this.seed[last];
         this.kind[i] = this.kind[last];
         this.color[i] = this.color[last];
      }
   }
}
