package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Decal {
   final Canvas canvas;
   final boolean wrap;
   final float cx;
   final float cy;
   final float cz;
   final float dx;
   final float dy;
   final float dz;
   final float ux;
   final float uy;
   final float uz;
   final float rx;
   final float ry;
   final float rz;
   final float width;
   final float height;
   float near = -Float.MAX_VALUE;
   float far = Float.MAX_VALUE;
   float cosLimit = (float)Math.cos(Math.toRadians(78.0));
   float offset = 0.035F;
   boolean glow;
   float flat = Float.NaN;
   float occlusion = 1.0F;

   private Decal(
      Canvas canvas, boolean wrap, float cx, float cy, float cz, float dx, float dy, float dz, float ux, float uy, float uz, float width, float height
   ) {
      if (canvas == null) {
         throw new IllegalArgumentException("decal needs a canvas");
      } else if (!(width <= 0.0F) && !(height <= 0.0F)) {
         this.canvas = canvas;
         this.wrap = wrap;
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         float dl = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (dl < 1.0E-6F) {
            throw new IllegalArgumentException("decal direction must not be zero");
         } else {
            this.dx = dx / dl;
            this.dy = dy / dl;
            this.dz = dz / dl;
            float along = ux * this.dx + uy * this.dy + uz * this.dz;
            float px = ux - along * this.dx;
            float py = uy - along * this.dy;
            float pz = uz - along * this.dz;
            float pl = (float)Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1.0E-6F) {
               throw new IllegalArgumentException("decal up/front direction must not be parallel to its axis");
            } else {
               this.ux = px / pl;
               this.uy = py / pl;
               this.uz = pz / pl;
               if (wrap) {
                  this.rx = this.dy * this.uz - this.dz * this.uy;
                  this.ry = this.dz * this.ux - this.dx * this.uz;
                  this.rz = this.dx * this.uy - this.dy * this.ux;
               } else {
                  this.rx = this.uy * this.dz - this.uz * this.dy;
                  this.ry = this.uz * this.dx - this.ux * this.dz;
                  this.rz = this.ux * this.dy - this.uy * this.dx;
               }

               this.width = width;
               this.height = height;
            }
         }
      } else {
         throw new IllegalArgumentException("decal size must be positive");
      }
   }

   public static Decal front(Canvas canvas, float cx, float cy, float cz, float width, float height) {
      return new Decal(canvas, false, cx, cy, cz, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, width, height);
   }

   public static Decal back(Canvas canvas, float cx, float cy, float cz, float width, float height) {
      return new Decal(canvas, false, cx, cy, cz, 0.0F, 0.0F, -1.0F, 0.0F, 1.0F, 0.0F, width, height);
   }

   public static Decal left(Canvas canvas, float cx, float cy, float cz, float width, float height) {
      return new Decal(canvas, false, cx, cy, cz, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, width, height);
   }

   public static Decal right(Canvas canvas, float cx, float cy, float cz, float width, float height) {
      return new Decal(canvas, false, cx, cy, cz, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, width, height);
   }

   public static Decal top(Canvas canvas, float cx, float cy, float cz, float width, float height) {
      return new Decal(canvas, false, cx, cy, cz, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F, width, height);
   }

   public static Decal project(
      Canvas canvas, float cx, float cy, float cz, float dirX, float dirY, float dirZ, float upX, float upY, float upZ, float width, float height
   ) {
      return new Decal(canvas, false, cx, cy, cz, dirX, dirY, dirZ, upX, upY, upZ, width, height);
   }

   public static Decal wrap(
      Canvas canvas, float ax, float ay, float az, float axisX, float axisY, float axisZ, float frontX, float frontY, float frontZ, float degrees, float length
   ) {
      return new Decal(canvas, true, ax, ay, az, axisX, axisY, axisZ, frontX, frontY, frontZ, Math.min(360.0F, degrees), length);
   }

   public Decal depth(float behind, float inFront) {
      this.near = -Math.abs(behind);
      this.far = Math.abs(inFront);
      return this;
   }

   public Decal radius(float min, float max) {
      this.near = min;
      this.far = max;
      return this;
   }

   public Decal maxAngle(float degrees) {
      this.cosLimit = (float)Math.cos(Math.toRadians(Math.max(1.0F, Math.min(89.5F, degrees))));
      return this;
   }

   public Decal offset(float units) {
      this.offset = units;
      return this;
   }

   public Decal glow() {
      this.glow = true;
      return this;
   }

   public Decal flatLight(float amount) {
      this.flat = Math.max(0.0F, Math.min(1.0F, amount));
      return this;
   }

   public Decal occlusion(float amount) {
      this.occlusion = Math.max(0.0F, Math.min(1.0F, amount));
      return this;
   }

   Decal mirrored() {
      Decal m;
      if (this.wrap) {
         m = new Decal(
            this.canvas.mirrored(), true, -this.cx, this.cy, this.cz, -this.dx, this.dy, this.dz, -this.ux, this.uy, this.uz, this.width, this.height
         );
      } else {
         m = new Decal(
            this.canvas.mirrored(), false, -this.cx, this.cy, this.cz, -this.dx, this.dy, this.dz, -this.ux, this.uy, this.uz, this.width, this.height
         );
      }

      m.near = this.near;
      m.far = this.far;
      m.cosLimit = this.cosLimit;
      m.offset = this.offset;
      m.glow = this.glow;
      m.flat = this.flat;
      m.occlusion = this.occlusion;
      return m;
   }
}
