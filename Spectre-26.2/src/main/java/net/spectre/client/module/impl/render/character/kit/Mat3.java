package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Mat3 {
   public static final Mat3 IDENTITY = new Mat3(1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
   public final float m00;
   public final float m01;
   public final float m02;
   public final float m10;
   public final float m11;
   public final float m12;
   public final float m20;
   public final float m21;
   public final float m22;

   public Mat3(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
      this.m00 = m00;
      this.m01 = m01;
      this.m02 = m02;
      this.m10 = m10;
      this.m11 = m11;
      this.m12 = m12;
      this.m20 = m20;
      this.m21 = m21;
      this.m22 = m22;
   }

   public static Mat3 euler(float degX, float degY, float degZ) {
      return radians((float)Math.toRadians(degX), (float)Math.toRadians(degY), (float)Math.toRadians(degZ));
   }

   public static Mat3 radians(float x, float y, float z) {
      float cx = (float)Math.cos(x);
      float sx = (float)Math.sin(x);
      float cy = (float)Math.cos(y);
      float sy = (float)Math.sin(y);
      float cz = (float)Math.cos(z);
      float sz = (float)Math.sin(z);
      return new Mat3(cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx);
   }

   public static Mat3 about(float ax, float ay, float az, float degrees) {
      float l = (float)Math.sqrt(ax * ax + ay * ay + az * az);
      if (l < 1.0E-8F) {
         return IDENTITY;
      } else {
         ax /= l;
         ay /= l;
         az /= l;
         float a = (float)Math.toRadians(degrees);
         float c = (float)Math.cos(a);
         float s = (float)Math.sin(a);
         float t = 1.0F - c;
         return new Mat3(
            t * ax * ax + c,
            t * ax * ay - s * az,
            t * ax * az + s * ay,
            t * ax * ay + s * az,
            t * ay * ay + c,
            t * ay * az - s * ax,
            t * ax * az - s * ay,
            t * ay * az + s * ax,
            t * az * az + c
         );
      }
   }

   public Mat3 mul(Mat3 o) {
      return new Mat3(
         this.m00 * o.m00 + this.m01 * o.m10 + this.m02 * o.m20,
         this.m00 * o.m01 + this.m01 * o.m11 + this.m02 * o.m21,
         this.m00 * o.m02 + this.m01 * o.m12 + this.m02 * o.m22,
         this.m10 * o.m00 + this.m11 * o.m10 + this.m12 * o.m20,
         this.m10 * o.m01 + this.m11 * o.m11 + this.m12 * o.m21,
         this.m10 * o.m02 + this.m11 * o.m12 + this.m12 * o.m22,
         this.m20 * o.m00 + this.m21 * o.m10 + this.m22 * o.m20,
         this.m20 * o.m01 + this.m21 * o.m11 + this.m22 * o.m21,
         this.m20 * o.m02 + this.m21 * o.m12 + this.m22 * o.m22
      );
   }

   public Mat3 transpose() {
      return new Mat3(this.m00, this.m10, this.m20, this.m01, this.m11, this.m21, this.m02, this.m12, this.m22);
   }

   public Vec3 apply(Vec3 v) {
      return new Vec3(this.x(v.x(), v.y(), v.z()), this.y(v.x(), v.y(), v.z()), this.z(v.x(), v.y(), v.z()));
   }

   public float x(float x, float y, float z) {
      return this.m00 * x + this.m01 * y + this.m02 * z;
   }

   public float y(float x, float y, float z) {
      return this.m10 * x + this.m11 * y + this.m12 * z;
   }

   public float z(float x, float y, float z) {
      return this.m20 * x + this.m21 * y + this.m22 * z;
   }
}
