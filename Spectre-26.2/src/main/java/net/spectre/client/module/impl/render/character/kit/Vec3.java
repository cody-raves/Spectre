package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record Vec3(float x, float y, float z) {
   public static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);
   public static final Vec3 UP = new Vec3(0.0F, 1.0F, 0.0F);
   public static final Vec3 FORWARD = new Vec3(0.0F, 0.0F, 1.0F);
   public static final Vec3 LEFT = new Vec3(1.0F, 0.0F, 0.0F);

   public static Vec3 of(float x, float y, float z) {
      return new Vec3(x, y, z);
   }

   public Vec3 add(Vec3 o) {
      return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
   }

   public Vec3 add(float dx, float dy, float dz) {
      return new Vec3(this.x + dx, this.y + dy, this.z + dz);
   }

   public Vec3 sub(Vec3 o) {
      return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z);
   }

   public Vec3 mul(float k) {
      return new Vec3(this.x * k, this.y * k, this.z * k);
   }

   public float dot(Vec3 o) {
      return this.x * o.x + this.y * o.y + this.z * o.z;
   }

   public Vec3 cross(Vec3 o) {
      return new Vec3(this.y * o.z - this.z * o.y, this.z * o.x - this.x * o.z, this.x * o.y - this.y * o.x);
   }

   public float length() {
      return (float)Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
   }

   public float distance(Vec3 o) {
      return this.sub(o).length();
   }

   public Vec3 normalize() {
      float l = this.length();
      return l < 1.0E-8F ? ZERO : this.mul(1.0F / l);
   }

   public Vec3 lerp(Vec3 o, float t) {
      return new Vec3(this.x + (o.x - this.x) * t, this.y + (o.y - this.y) * t, this.z + (o.z - this.z) * t);
   }

   public Vec3 mirrorX() {
      return new Vec3(-this.x, this.y, this.z);
   }

   public Vec3 rotate(float degX, float degY, float degZ) {
      return Mat3.euler(degX, degY, degZ).apply(this);
   }

   public Vec3 rotate(float degX, float degY, float degZ, Vec3 centre) {
      return Mat3.euler(degX, degY, degZ).apply(this.sub(centre)).add(centre);
   }
}
