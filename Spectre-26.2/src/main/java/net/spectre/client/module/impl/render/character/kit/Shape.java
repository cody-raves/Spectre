package net.spectre.client.module.impl.render.character.kit;

import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class Shape {
   static final float FAR = 100000.0F;
   float minX = -100000.0F;
   float minY = -100000.0F;
   float minZ = -100000.0F;
   float maxX = 100000.0F;
   float maxY = 100000.0F;
   float maxZ = 100000.0F;
   Paint paint;

   protected Shape() {
   }

   public abstract float distance(float var1, float var2, float var3);

   float distance(float x, float y, float z, float limit) {
      return this.distance(x, y, z);
   }

   float shade(float x, float y, float z, Sample out) {
      float d = this.distance(x, y, z);
      Paint.DEFAULT.apply(x, y, z, out);
      return d;
   }

   public final float sample(float x, float y, float z, Sample out) {
      if (this.paint != null) {
         float d = this.distance(x, y, z);
         this.paint.apply(x, y, z, out);
         return d;
      } else {
         return this.shade(x, y, z, out);
      }
   }

   protected final void bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      this.minX = Math.min(minX, maxX);
      this.minY = Math.min(minY, maxY);
      this.minZ = Math.min(minZ, maxZ);
      this.maxX = Math.max(minX, maxX);
      this.maxY = Math.max(minY, maxY);
      this.maxZ = Math.max(minZ, maxZ);
   }

   final void boundsOf(Shape o, float pad) {
      if (!o.bounded()) {
         this.minX = this.minY = this.minZ = -100000.0F;
         this.maxX = this.maxY = this.maxZ = 100000.0F;
      } else {
         this.bounds(o.minX - pad, o.minY - pad, o.minZ - pad, o.maxX + pad, o.maxY + pad, o.maxZ + pad);
      }
   }

   public final boolean bounded() {
      return this.maxX - this.minX < 100000.0F && this.maxY - this.minY < 100000.0F && this.maxZ - this.minZ < 100000.0F;
   }

   public final float minX() {
      return this.minX;
   }

   public final float minY() {
      return this.minY;
   }

   public final float minZ() {
      return this.minZ;
   }

   public final float maxX() {
      return this.maxX;
   }

   public final float maxY() {
      return this.maxY;
   }

   public final float maxZ() {
      return this.maxZ;
   }

   Shape prune(float x0, float y0, float z0, float x1, float y1, float z1) {
      return this;
   }

   Shape map(UnaryOperator<Shape> f) {
      return f.apply(this);
   }

   final Shape inherit(Shape from) {
      if (from.paint != null && this.paint == null) {
         this.paint = from.paint;
      }

      return this;
   }

   final float boxGap(float x0, float y0, float z0, float x1, float y1, float z1) {
      float dx = Math.max(0.0F, Math.max(this.minX - x1, x0 - this.maxX));
      float dy = Math.max(0.0F, Math.max(this.minY - y1, y0 - this.maxY));
      float dz = Math.max(0.0F, Math.max(this.minZ - z1, z0 - this.maxZ));
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   final float boundDistance(float x, float y, float z) {
      float dx = x < this.minX ? this.minX - x : (x > this.maxX ? x - this.maxX : 0.0F);
      float dy = y < this.minY ? this.minY - y : (y > this.maxY ? y - this.maxY : 0.0F);
      float dz = z < this.minZ ? this.minZ - z : (z > this.maxZ ? z - this.maxZ : 0.0F);
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public Shape color(int argb) {
      this.paint = Paint.of(argb);
      return this;
   }

   public Shape paint(Paint paint) {
      this.paint = paint;
      return this;
   }

   public Shape move(float dx, float dy, float dz) {
      return new Shapes.Translate(this, dx, dy, dz);
   }

   public Shape rotate(float degX, float degY, float degZ) {
      return this.rotate(degX, degY, degZ, 0.0F, 0.0F, 0.0F);
   }

   public Shape rotate(float degX, float degY, float degZ, float cx, float cy, float cz) {
      return new Shapes.Rotate(this, Mat3.euler(degX, degY, degZ), cx, cy, cz);
   }

   public Shape rotateAxis(float ax, float ay, float az, float degrees, float cx, float cy, float cz) {
      return new Shapes.Rotate(this, Mat3.about(ax, ay, az, degrees), cx, cy, cz);
   }

   public Shape scale(float k) {
      return this.scale(k, 0.0F, 0.0F, 0.0F);
   }

   public Shape scale(float k, float cx, float cy, float cz) {
      return new Shapes.Scale(this, k, cx, cy, cz);
   }

   public Shape mirrorX() {
      return new Shapes.Mirror(this, true);
   }

   public Shape mirrored() {
      return new Shapes.Mirror(this, false);
   }

   public Shape inflate(float radius) {
      return new Shapes.Round(this, radius);
   }

   public Shape shell(float thickness) {
      return new Shapes.Shell(this, thickness);
   }

   public Shape roughen(float amplitude, float size) {
      return new Shapes.Roughen(this, amplitude, size);
   }

   public Shape twist(float degreesPerUnit, float cx, float cz) {
      return new Shapes.Twist(this, degreesPerUnit, cx, cz);
   }

   public Shape union(Shape... others) {
      return Shapes.union(this.prepend(others));
   }

   public Shape blend(float k, Shape... others) {
      return Shapes.blend(k, this.prepend(others));
   }

   public Shape subtract(Shape cutter) {
      return Shapes.subtract(this, cutter);
   }

   public Shape subtract(float k, Shape cutter) {
      return Shapes.subtract(k, this, cutter);
   }

   public Shape intersect(Shape other) {
      return Shapes.intersect(this, other);
   }

   public Shape intersect(float k, Shape other) {
      return Shapes.intersect(k, this, other);
   }

   private Shape[] prepend(Shape[] others) {
      Shape[] all = new Shape[others.length + 1];
      all[0] = this;
      System.arraycopy(others, 0, all, 1, others.length);
      return all;
   }
}
