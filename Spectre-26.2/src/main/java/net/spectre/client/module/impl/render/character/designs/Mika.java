package net.spectre.client.module.impl.render.character.designs;

import java.util.ArrayList;
import java.util.List;
import net.spectre.client.module.impl.render.character.kit.Bone;
import net.spectre.client.module.impl.render.character.kit.Canvas;
import net.spectre.client.module.impl.render.character.kit.CharacterDesign;
import net.spectre.client.module.impl.render.character.kit.Colors;
import net.spectre.client.module.impl.render.character.kit.Decal;
import net.spectre.client.module.impl.render.character.kit.Motion;
import net.spectre.client.module.impl.render.character.kit.Paint;
import net.spectre.client.module.impl.render.character.kit.Paint2D;
import net.spectre.client.module.impl.render.character.kit.Part;
import net.spectre.client.module.impl.render.character.kit.Rig;
import net.spectre.client.module.impl.render.character.kit.Shape;
import net.spectre.client.module.impl.render.character.kit.Shape2D;
import net.spectre.client.module.impl.render.character.kit.Shapes;
import net.spectre.client.module.impl.render.character.kit.Strand;
import net.spectre.client.module.impl.render.character.kit.Vec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Mika extends CharacterDesign {
   private static final int SKIN = -9532;
   private static final int SKIN_SHADE = -1331558;
   private static final int SKIN_LINE = -5740966;
   private static final int HAIR = -681403;
   private static final int HAIR_DARK = -3647457;
   private static final int HAIR_TIP = -15750;
   private static final int HAIR_LINE = -6142180;
   private static final int EAR_TIP = -4697060;
   private static final int EAR_PINK = -22088;
   private static final int TUFT = -5162;
   private static final int HOODIE = -4200487;
   private static final int HOODIE_SHADE = -7092297;
   private static final int RIB = -5710645;
   private static final int CUFF_IN = -9461870;
   private static final int HOODIE_LINE = -10774650;
   private static final int LINING = -744532;
   private static final int SKIRT = -12892555;
   private static final int SKIRT_DARK = -14275250;
   private static final int SKIRT_LINE = -14999232;
   private static final int TIGHTS = -12965330;
   private static final int TIGHTS_SHEEN = -9808288;
   private static final int TIGHTS_LINE = -15068908;
   private static final int SHOE = -542005;
   private static final int SOLE = -264207;
   private static final int MINT = -5839921;
   private static final int FISH = -8398652;
   private static final int FISH_LINE = -12811148;
   private static final int SHOE_LINE = -6659470;
   private static final int GOLD = -867761;
   private static final int TAIL_TIP = -3618;
   private static final int LASH = -13887470;
   private static final int BROW = -4691398;
   private static final float HX = 0.0F;
   private static final float HY = 27.95F;
   private static final float HZ = -0.15F;
   private static final float RX = 2.3F;
   private static final float RY = 2.3F;
   private static final float RZ = 2.35F;
   private static final float DROP = 0.4F;
   private static final float HS = 1.12F;
   private static final float NY = 24.9F;
   private static final float NZ = -0.3F;
   private static final float PPU = 55.652176F;
   private static final float TOP = 29.0F;
   private static final int PLEATS = 16;
   private static final float[][] FRINGE = new float[][]{
      {1.0F, -1.0F, 30.15F, -1.5F, 29.6F, -2.0F, 28.3F, -1.8F, 27.0F, 0.3F},
      {0.95F, -0.42F, 30.25F, -0.75F, 29.75F, -1.05F, 28.6F, -0.85F, 27.9F, -0.35F},
      {1.05F, -0.28F, 30.3F, 0.0F, 29.8F, 0.5F, 28.4F, 0.42F, 27.2F, 0.4F},
      {0.95F, 0.55F, 30.25F, 0.9F, 29.75F, 1.25F, 28.6F, 1.02F, 27.65F, -0.3F},
      {1.0F, 1.1F, 30.15F, 1.6F, 29.6F, 2.05F, 28.3F, 1.85F, 26.75F, 0.35F},
      {0.65F, -0.75F, 30.2F, -1.2F, 29.6F, -1.5F, 28.3F, -1.3F, 27.45F, 0.3F},
      {0.5F, 0.28F, 30.25F, 0.6F, 29.7F, 0.88F, 28.5F, 0.75F, 27.5F, -0.3F},
      {0.5F, -0.45F, 30.25F, -0.4F, 29.7F, -0.3F, 28.3F, -0.18F, 27.6F, 0.3F}
   };
   private static final float[][] CROWN = new float[][]{
      {-0.413F, 0.766F, 0.492F},
      {-0.992F, 0.087F, -0.087F},
      {-0.539F, -0.342F, -0.77F},
      {0.464F, -0.375F, -0.803F},
      {0.975F, 0.139F, -0.172F},
      {0.353F, 0.788F, 0.504F}
   };

   public Mika() {
      super("mika", "Mika");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-12967400, 0.12F).occlusion(0.3F);
      body(rig);
      head(rig);
      hair(rig);
      ears(rig);
      arms(rig);
      legs(rig);
      tail(rig);
   }

   private static Shape toHead(Shape s) {
      return s.move(0.0F, -0.4F, 0.0F).scale(1.12F, 0.0F, 24.9F, -0.3F);
   }

   private static float wx(float x) {
      return x * 1.12F;
   }

   private static float wy(float y) {
      return 24.9F + (y - 0.4F - 24.9F) * 1.12F;
   }

   private static float wz(float z) {
      return -0.3F + (z - -0.3F) * 1.12F;
   }

   private static float u(float x) {
      return 128.0F + x * 55.652176F;
   }

   private static float v(float y) {
      return (29.0F - y) * 55.652176F;
   }

   private static float capZ(float x, float y, float off) {
      float ax = x / (2.72F + off);
      float ay = (y - 28.25F) / (2.8F + off);
      return -0.28F + (2.85F + off) * (float)Math.sqrt(Math.max(0.02F, 1.0F - ax * ax - ay * ay));
   }

   private static float[] onCap(float dx, float dy, float dz, float off) {
      float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      dx /= l;
      dy /= l;
      dz /= l;
      float ax = dx / (2.72F + off);
      float ay = dy / (2.8F + off);
      float az = dz / (2.85F + off);
      float k = 1.0F / (float)Math.sqrt(ax * ax + ay * ay + az * az);
      return new float[]{dx * k, 28.25F + dy * k, -0.28F + dz * k};
   }

   private static Shape crownClump(float[] t, float w) {
      float wx = 0.15F;
      float wy = 0.93F;
      float wz = -0.35F;
      float[] p = new float[16];
      float[] s = new float[]{0.0F, 0.38F, 0.72F, 1.0F};
      float[] r = new float[]{0.2F, 0.85F * w, 0.8F * w, 0.3F};
      float[] off = new float[]{-0.12F, -0.07F, -0.07F, -0.14F};

      for (int i = 0; i < 4; i++) {
         float[] q = onCap(wx + (t[0] - wx) * s[i], wy + (t[1] - wy) * s[i], wz + (t[2] - wz) * s[i], off[i]);
         p[i * 4] = q[0];
         p[i * 4 + 1] = q[1];
         p[i * 4 + 2] = q[2];
         p[i * 4 + 3] = r[i];
      }

      return Shapes.tube(p).flattenFrom(3.2F, 0.0F, 28.25F, -0.28F);
   }

   private static float surf(float x, float y, float off) {
      float ax = x / (2.3F + off);
      float ay = (y - 27.95F) / (2.3F + off);
      return -0.15F + (2.35F + off) * (float)Math.sqrt(Math.max(0.02F, 1.0F - ax * ax - ay * ay));
   }

   private static void head(Rig rig) {
      Shape cranium = Shapes.ellipsoid(0.0F, 27.95F, -0.15F, 2.3F, 2.3F, 2.35F);
      Shape cheeks = Shapes.ellipsoid(0.0F, 26.72F, 0.35F, 1.93F, 1.3F, 1.7F);
      Shape jaw = Shapes.cone(0.0F, 26.2F, 0.45F, 1.2F, 0.0F, 25.25F, 0.98F, 0.3F);
      Shape hidden = Shapes.union(Shapes.box(0.0F, 29.0F, -1.8F, 3.0F, 2.3F, 1.5F), Shapes.box(0.0F, 30.3F, 0.5F, 3.0F, 1.3F, 3.0F));
      Shape skull = Shapes.subtract(Shapes.blend(0.7F, cranium, cheeks, jaw), hidden).color(-9532);
      rig.part(Bone.HEAD).pivot(0.0F, 24.9F, -0.3F).shape(toHead(skull)).detail(1.5F).flatLight(1.0F).occlusion(0.2F).outline(-5740966, 0.1F).decal(face());
      Paint neck = Paint.vertical(-9532, 23.7F, -1331558, 24.3F).flatLight(1.0F).occlusion(0.25F);
      rig.extra("neck", Bone.BODY).shape(Shapes.capsule(0.0F, 23.1F, -0.28F, 0.0F, 25.2F, -0.2F, 0.62F).paint(neck)).outline(-5740966, 0.08F);
   }

   private static void hair(Rig rig) {
      Paint paint = Paint.field(Mika::hairColor);
      Shape cap = Shapes.ellipsoid(0.0F, 28.25F, -0.28F, 2.72F, 2.8F, 2.85F)
         .intersect(Shapes.plane(0.0F, 27.4F, 1.2F, 0.0F, -0.6F, 1.0F))
         .intersect(Shapes.above(26.4F));
      Shape[] clumps = new Shape[CROWN.length];

      for (int i = 0; i < CROWN.length; i++) {
         clumps[i] = crownClump(CROWN[i], i % 2 == 0 ? 1.0F : 0.9F);
      }

      Shape crown = Shapes.blend(0.3F, cap, Shapes.union(clumps));
      Part top = rig.extra("hair", Bone.HEAD)
         .shape(toHead(Shapes.union(crown, backLock(180.0F, 24.95F, 1.05F, 0.25F)).paint(paint)))
         .detail(0.9F)
         .resolution(0.95F)
         .flatLight(0.5F)
         .occlusion(0.3F)
         .outline(-6142180, 0.09F);
      float[][] back = new float[][]{
         {-54.0F, 25.95F, 0.9F, 0.3F},
         {-75.0F, 25.45F, 1.0F, -0.3F},
         {-97.0F, 25.8F, 1.05F, 0.25F},
         {-119.0F, 25.3F, 1.05F, -0.2F},
         {-140.0F, 25.5F, 1.05F, 0.3F},
         {-161.0F, 25.1F, 1.0F, -0.3F}
      };
      List<Shape> half = new ArrayList<>();

      for (float[] b : back) {
         half.add(backLock(b[0], b[1], b[2], b[3]));
      }

      half.add(topLock(-84.0F, 26.25F));
      half.add(topLock(-128.0F, 26.5F));
      Part right = rig.extra("hair_r", Bone.HEAD)
         .pivot(0.0F, wy(29.8F), wz(-0.4F))
         .shape(toHead(Shapes.union(half.toArray(new Shape[0])).paint(paint)))
         .detail(1.0F)
         .resolution(0.85F)
         .flatLight(0.5F)
         .occlusion(0.35F)
         .outline(-6142180, 0.09F)
         .motion(Motion.hair().idle(1.2F, 0.0F, 1.0F, 0.35F).walk(2.5F, 0.0F, 2.0F, 2.0F).drag(6.0F).hang(0.2F).turn(5.0F, 0.0F).spring(60.0F, 8.0F).bend(1.6F));
      rig.extra("hair_l", Bone.HEAD).mirrorOf(right);
      Shape[] locks = new Shape[FRINGE.length];

      for (int i = 0; i < FRINGE.length; i++) {
         locks[i] = fringeLock(FRINGE[i]);
      }

      Shape inner = Shapes.ellipsoid(0.0F, 28.25F, -0.28F, 2.56F, 2.64F, 2.69F).intersect(Shapes.above(29.3F));
      Shape bangs = Shapes.subtract(Shapes.union(locks), inner);
      Part fringe = rig.extra("fringe", Bone.HEAD)
         .shape(toHead(bangs.paint(Paint.vertical(-15750, 26.9F, -681403, 29.2F))))
         .detail(1.3F)
         .resolution(0.5F)
         .flatLight(0.5F)
         .occlusion(0.25F)
         .outline(-6142180, 0.08F);
      shine(top, fringe);
      clip(rig);
      Strand side = Shapes.tube(
            -1.95F,
            29.5F,
            0.85F,
            0.42F,
            -2.36F,
            28.4F,
            1.05F,
            0.56F,
            -2.42F,
            27.1F,
            1.08F,
            0.6F,
            -2.3F,
            25.9F,
            1.02F,
            0.5F,
            -2.26F,
            25.0F,
            0.98F,
            0.34F,
            -2.5F,
            24.45F,
            0.95F,
            0.05F
         )
         .flatten(2.3F, -0.55F, 0.0F, 0.83F);
      Part lock = rig.extra("lock_r", Bone.HEAD)
         .pivot(wx(-1.95F), wy(29.4F), wz(0.85F))
         .shape(toHead(pointed(side, -2.5F, 24.45F, 0.95F, 0.4F, 1.0F, 0.3F).paint(paint)))
         .detail(0.8F)
         .resolution(0.55F)
         .flatLight(0.5F)
         .occlusion(0.35F)
         .outline(-6142180, 0.09F)
         .motion(Motion.hair().drag(10.0F).hang(0.35F));
      rig.extra("lock_l", Bone.HEAD).mirrorOf(lock);
      Strand ahoge = Shapes.tube(
            0.05F,
            30.8F,
            0.1F,
            0.24F,
            0.03F,
            31.45F,
            0.2F,
            0.26F,
            0.3F,
            32.05F,
            0.32F,
            0.22F,
            0.8F,
            32.38F,
            0.42F,
            0.17F,
            1.34F,
            32.35F,
            0.5F,
            0.11F,
            1.72F,
            32.02F,
            0.55F,
            0.05F
         )
         .flatten(1.6F, 0.0F, 0.0F, 1.0F);
      rig.extra("ahoge", Bone.HEAD)
         .pivot(wx(0.05F), wy(30.85F), wz(0.1F))
         .shape(toHead(ahoge.paint(Paint.vertical(-681403, 31.2F, -15750, 32.1F))))
         .resolution(0.5F)
         .detail(0.9F)
         .flatLight(0.5F)
         .occlusion(0.3F)
         .outline(-6142180, 0.08F)
         .motion(Motion.hair().idle(4.0F, 0.0F, 6.0F, 0.5F).walk(8.0F, 0.0F, 8.0F, 2.0F).drag(6.0F).hang(0.3F).spring(70.0F, 5.0F).bend(1.2F));
   }

   private static int hairColor(float x, float y, float z) {
      int base = Colors.mix(-15750, -681403, smooth(25.0F, 27.6F, y));
      float rho = (float)Math.sqrt(x * x + (z + 0.25F) * (z + 0.25F));
      float inner = (1.0F - smooth(1.9F, 2.55F, rho)) * (1.0F - smooth(26.4F, 27.6F, y));
      return Colors.mix(base, -3647457, inner * 0.75F);
   }

   private static Shape fringeLock(float[] f) {
      float lift = f[0] < 0.7F ? 0.52F : (f[9] > 0.0F ? 0.4F : 0.28F);
      Strand lock = Shapes.strand(
            f[0],
            0.05F,
            f[1],
            f[2],
            capZ(f[1], f[2], -0.3F),
            f[3],
            f[4],
            capZ(f[3], f[4], 0.1F),
            f[5],
            f[6],
            surf(f[5], f[6], lift),
            f[7],
            f[8],
            surf(f[7], f[8], 0.12F)
         )
         .flattenFrom(2.8F, 0.0F, 27.95F, -0.15F);
      float dx = f[5] - f[7];
      float dy = f[6] - f[8];
      float l = (float)Math.sqrt(dx * dx + dy * dy);
      dx /= l;
      dy /= l;
      float ra = 0.9F * (1.0F + f[9]);
      float rb = 0.9F * (1.0F - f[9]);
      float s = 0.34F;
      Shape2D v = Shape2D.polygon(
         f[7],
         f[8],
         f[7] + dx * ra - dy * s,
         f[8] + dy * ra + dx * s,
         f[7] + dx * 8.0F - dy * 5.0F,
         f[8] + dy * 8.0F + dx * 5.0F,
         f[7] + dx * 8.0F + dy * 5.0F,
         f[8] + dy * 8.0F - dx * 5.0F,
         f[7] + dx * rb + dy * s,
         f[8] + dy * rb - dx * s
      );
      return lock.intersect(Shapes.extrude(v, 6.0F, 0.0F));
   }

   private static void clip(Rig rig) {
      Shape2D fish = Shape2D.blend(0.08F, Shape2D.ellipse(-0.1F, 0.0F, 0.5F, 0.32F), Shape2D.polygon(0.3F, 0.0F, 0.86F, 0.36F, 0.66F, 0.0F, 0.86F, -0.36F));
      Shape body = Shapes.extrude(fish, 0.15F, 0.11F).scale(1.3F).paint(Paint.gradient(-3869978, 0.0F, 0.35F, 0.0F, -8398652, 0.0F, -0.35F, 0.0F));
      float x = 1.62F;
      float y = 28.7F;
      float off = 0.75F;
      float z = surf(x, y, off);
      float ax = 2.3F + off;
      float ay = 2.3F + off;
      float az = 2.35F + off;
      float nx = x / (ax * ax);
      float ny = (y - 27.95F) / (ay * ay);
      float nz = (z - -0.15F) / (az * az);
      float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      float yaw = (float)Math.toDegrees(Math.atan2(nx, nz));
      float pitch = (float)(-Math.toDegrees(Math.asin(ny / l)));
      Shape placed = body.rotate(0.0F, 0.0F, 35.0F).rotate(pitch, yaw, 0.0F).move(x, y, z);
      Vec3 eye = new Vec3(-0.5F, 0.08F, 0.2F).rotate(0.0F, 0.0F, 35.0F).rotate(pitch, yaw, 0.0F).add(x, y, z);
      Vec3 n = new Vec3(0.0F, 0.0F, 1.0F).rotate(0.0F, 0.0F, 35.0F).rotate(pitch, yaw, 0.0F);
      Canvas c = new Canvas(32, 32);
      c.fill(Shape2D.circle(16.0F, 16.0F, 7.0F), -14796236);
      c.fill(Shape2D.circle(13.5F, 13.5F, 2.2F), -855638017);
      Decal dot = Decal.project(c, wx(eye.x()), wy(eye.y()), wz(eye.z()), n.x(), n.y(), n.z(), 0.0F, 1.0F, 0.0F, 0.33600003F, 0.33600003F).depth(0.25F, 0.25F);
      rig.extra("clip", Bone.HEAD).shape(toHead(placed)).resolution(0.4F).detail(0.5F).flatLight(0.4F).outline(-12811148, 0.1F).decal(dot);
   }

   private static void shine(Part top, Part fringe) {
      Canvas c = new Canvas(96, 24);
      c.fill(Shape2D.union(Shape2D.taper(3.0F, 12.0F, 1.0F, 48.0F, 12.0F, 17.0F), Shape2D.taper(48.0F, 12.0F, 17.0F, 93.0F, 12.0F, 1.0F)), -419442022, 1.5F);
      float[][] dash = new float[][]{
         {-52.0F, 0.72F, 0.12F, 6.0F}, {-24.0F, 0.9F, -0.06F, -4.0F}, {4.0F, 0.66F, 0.08F, 5.0F}, {27.0F, 0.82F, -0.08F, -6.0F}, {57.0F, 0.7F, 0.1F, 3.0F}
      };

      for (float[] d : dash) {
         double a = Math.toRadians(d[0]);
         float sx = (float)Math.sin(a);
         float cz = (float)Math.cos(a);
         float[] p = onCap(sx, 0.36F + 0.3F * (1.0F - cz) + d[2], cz, 0.28F);
         float nx = p[0];
         float ny = p[1] - 28.25F;
         float nz = p[2] + 0.28F;
         Vec3 up = new Vec3(0.0F, 1.0F, 0.0F).rotate(0.0F, 0.0F, d[3]);
         Decal glint = Decal.project(c, wx(p[0]), wy(p[1]), wz(p[2]), nx, ny * 0.6F, nz, up.x(), up.y(), up.z(), d[1] * 1.12F, 0.2464F)
            .depth(0.896F, 0.896F)
            .maxAngle(80.0F)
            .flatLight(0.8F)
            .occlusion(0.0F);
         top.decal(glint);
         fringe.decal(glint);
      }
   }

   private static Shape pointed(Strand lock, float tx, float ty, float tz, float spread, float rise, float skew) {
      float cz = -0.25F;
      float deg = (float)Math.toDegrees(Math.atan2(tx, tz - cz));
      float ra = rise * (1.0F + skew);
      float rb = rise * (1.0F - skew);
      Shape2D v = Shape2D.polygon(-5.0F, ty + 8.0F, 5.0F, ty + 8.0F, spread, ty + ra, 0.0F, ty, -spread, ty + rb);
      return lock.intersect(Shapes.extrude(v, 5.0F, 0.0F).rotate(0.0F, deg, 0.0F, 0.0F, 0.0F, cz));
   }

   private static Shape backLock(float deg, float endY, float width, float skew) {
      double a = Math.toRadians(deg);
      float sx = (float)Math.sin(a);
      float cz = (float)Math.cos(a);
      float back = Math.max(0.0F, Math.min(1.0F, (Math.abs(deg) - 100.0F) / 70.0F));
      float[] ys = new float[]{30.1F, 29.1F, 27.9F, 26.9F, (26.9F + endY) * 0.5F - 0.1F, endY};
      float[] rx = new float[]{2.04F, 2.59F, 2.7F, 2.52F, 2.42F, 2.32F};
      float[] rz = new float[]{2.14F, 2.72F, 2.83F, 2.62F, 2.48F, 2.35F};
      float[] out = new float[]{-0.34F, -0.06F, 0.04F, 0.04F, 0.02F, -0.06F - 0.24F * back};
      float[] rs = new float[]{0.7F, 1.05F, 1.05F, 0.95F, 0.64F, 0.06F};
      float[] p = new float[24];

      for (int i = 0; i < 6; i++) {
         p[i * 4] = sx * (rx[i] + out[i]);
         p[i * 4 + 1] = ys[i];
         p[i * 4 + 2] = -0.28F + cz * (rz[i] + out[i]);
         p[i * 4 + 3] = rs[i] * (i == 5 ? 1.0F : width);
      }

      return pointed(Shapes.tube(p).flattenFrom(2.1F, 0.0F, 27.95F, -0.15F), p[20], endY, p[22], 0.62F, 1.25F, skew);
   }

   private static Shape topLock(float deg, float endY) {
      double a = Math.toRadians(deg);
      float sx = (float)Math.sin(a);
      float cz = (float)Math.cos(a);
      float[] ring = new float[]{2.2F, 2.8F, 2.98F, 3.0F};
      float[] ys = new float[]{29.4F, 28.2F, 27.2F, endY};
      float[] rs = new float[]{0.55F, 0.75F, 0.55F, 0.08F};
      float[] p = new float[16];

      for (int i = 0; i < 4; i++) {
         p[i * 4] = sx * ring[i];
         p[i * 4 + 1] = ys[i];
         p[i * 4 + 2] = -0.25F + cz * ring[i] * 1.05F;
         p[i * 4 + 3] = rs[i];
      }

      return pointed(Shapes.tube(p).flattenFrom(2.2F, 0.0F, 27.95F, -0.15F), p[12], endY, p[14], 0.6F, 1.15F, 0.2F);
   }

   private static void ears(Rig rig) {
      Shape front = Shapes.extrude(Shape2D.polygon(-1.1F, 0.0F, 1.1F, 0.0F, 0.08F, 2.55F), 0.64F, 0.32F);
      Shape profile = Shapes.extrude(Shape2D.polygon(0.6F, -0.3F, -0.62F, -0.3F, -0.15F, 2.7F, 0.2F, 2.7F), 1.3F, 0.2F).rotate(0.0F, 90.0F, 0.0F);
      Shape outer = Shapes.intersect(front, profile).paint(Paint.vertical(-681403, 1.45F, -4697060, 2.25F));
      Shape inner = Shapes.extrude(Shape2D.polygon(-0.66F, 0.3F, 0.68F, 0.3F, 0.07F, 1.95F), 0.5F, 0.14F)
         .move(0.0F, 0.0F, 0.62F)
         .paint(Paint.of(-22088).flatLight(0.7F));
      Shape tuft = Shapes.union(
            Shapes.strand(0.14F, 0.05F, -0.34F, 0.28F, 0.25F, -0.46F, 0.6F, 0.44F, -0.38F, 0.9F, 0.5F),
            Shapes.strand(0.14F, 0.05F, 0.3F, 0.28F, 0.25F, 0.46F, 0.58F, 0.44F, 0.56F, 0.86F, 0.48F)
         )
         .paint(Paint.of(-5162).flatLight(0.7F));
      Shape ear = Shapes.union(Shapes.subtract(0.1F, outer, inner), tuft).scale(1.18F).rotate(-6.0F, -18.0F, 24.0F).move(-1.6F, 29.75F, -0.15F);
      Part right = rig.extra("ear_r", Bone.HEAD)
         .pivot(wx(-1.6F), wy(29.9F), wz(-0.15F))
         .shape(toHead(ear))
         .resolution(0.58F)
         .detail(0.9F)
         .flatLight(0.4F)
         .outline(-6142180, 0.09F)
         .motion(earMotion(0.0F, -1.0F));
      rig.extra("ear_l", Bone.HEAD).mirrorOf(right).motion(earMotion(140.0F, 1.0F));
   }

   private static Motion earMotion(float phase, float side) {
      return Motion.none().idle(2.5F, 0.0F, 5.0F * side, 0.55F).idlePhase(phase).walk(3.0F, 0.0F, 3.0F * side, 2.0F).spring(90.0F, 7.0F);
   }

   private static void body(Rig rig) {
      Shape yoke = Shapes.union(
         Shapes.cone(0.0F, 22.6F, -0.2F, 1.02F, -1.95F, 22.2F, -0.2F, 0.9F), Shapes.cone(0.0F, 22.6F, -0.2F, 1.02F, 1.95F, 22.2F, -0.2F, 0.9F)
      );
      Shape chest = Shapes.ellipsoid(0.0F, 20.5F, -0.1F, 2.3F, 2.4F, 1.72F);
      Shape belly = Shapes.ellipsoid(0.0F, 17.8F, -0.02F, 2.25F, 2.9F, 1.7F);
      Shape blouse = Shapes.ellipsoid(0.0F, 15.25F, -0.05F, 2.45F, 1.25F, 1.86F);
      Shape torso = Shapes.blend(1.0F, yoke, chest, belly, blouse).paint(Paint.field(Mika::hoodie));
      Shape pocket = Shapes.intersect(
            Shapes.ellipsoid(0.0F, 16.3F, -0.02F, 2.6F, 2.9F, 2.02F),
            Shapes.extrude(Shape2D.polygon(-1.6F, 17.3F, 1.6F, 17.3F, 2.15F, 14.75F, -2.15F, 14.75F), 2.0F, 0.0F).move(0.0F, 0.0F, 2.0F)
         )
         .color(-4200487);
      Shape hem = Shapes.extrude(Shape2D.ellipse(0.0F, 0.0F, 2.42F, 1.8F), 0.4F, 0.3F).rotate(90.0F, 0.0F, 0.0F).move(0.0F, 14.2F, -0.05F).color(-5710645);
      rig.part(Bone.BODY)
         .pivot(0.0F, 23.4F, -0.1F)
         .shape(Shapes.union(Shapes.blend(0.25F, torso, pocket), hem))
         .resolution(1.15F)
         .detail(1.0F)
         .outline(-10774650, 0.12F)
         .occlusion(0.3F)
         .flatLight(0.2F)
         .decal(pocketDecal())
         .decal(pawDecal())
         .decal(hemRib());
      rig.extra("collar", Bone.BODY)
         .shape(rim().paint(Paint.field(Mika::rimColor)))
         .resolution(0.7F)
         .detail(0.8F)
         .occlusion(0.3F)
         .flatLight(0.2F)
         .outline(-10774650, 0.1F);
      Shape pouch = Shapes.blend(
         0.8F, Shapes.ellipsoid(0.0F, 21.7F, -1.85F, 2.2F, 2.2F, 0.85F), Shapes.cone(0.0F, 20.6F, -2.3F, 0.8F, 0.0F, 19.1F, -2.2F, 0.28F)
      );
      Shape dip = Shapes.ellipsoid(0.0F, 0.0F, 0.0F, 1.55F, 0.35F, 0.85F).rotate(-26.6F, 0.0F, 0.0F).move(0.0F, 22.95F, -1.95F);
      Shape hood = Shapes.subtract(0.15F, pouch.intersect(Shapes.plane(0.0F, 22.7F, -2.2F, 0.0F, 1.0F, -0.5F)), dip).paint(Paint.field(Mika::hoodColor));
      rig.extra("hood", Bone.BODY).shape(hood).resolution(0.85F).detail(0.6F).occlusion(0.3F).flatLight(0.2F).outline(-10774650, 0.12F);
      rig.extra("skirt", Bone.BODY)
         .pivot(0.0F, 14.6F, 0.0F)
         .shape(skirt())
         .resolution(0.9F)
         .detail(0.8F)
         .occlusion(0.35F)
         .outline(-14999232, 0.12F)
         .decal(skirtStripe())
         .motion(Motion.none().walk(0.0F, 8.0F, 0.0F, 1.0F).walkPhase(-90.0F).drag(2.0F).spring(80.0F, 10.0F));
      Shape cord = Shapes.tube(-0.32F, 23.5F, 1.45F, 0.13F, -0.38F, 22.6F, 1.75F, 0.13F, -0.44F, 21.3F, 1.95F, 0.13F, -0.48F, 20.1F, 2.0F, 0.12F)
         .color(-264984);
      Shape bell = Shapes.sphere(-0.48F, 19.6F, 2.05F, 0.45F).paint(Paint.radial(-3920, -0.62F, 19.8F, 2.35F, 0.6F, -867761));
      Part cordR = rig.extra("cord_r", Bone.BODY)
         .pivot(-0.32F, 23.5F, 1.45F)
         .shape(Shapes.union(cord, bell))
         .resolution(0.5F)
         .detail(0.5F)
         .outline(-10774650, 0.08F)
         .decal(bellDecal(-0.48F))
         .motion(
            Motion.none()
               .rest(-3.0F, 0.0F, 0.0F)
               .idle(1.5F, 0.0F, 2.0F, 0.5F)
               .walk(5.0F, 0.0F, 6.0F, 2.0F)
               .drag(-4.0F)
               .hang(0.9F)
               .spring(60.0F, 6.0F)
               .bend(0.8F)
         );
      rig.extra("cord_l", Bone.BODY).mirrorOf(cordR);
   }

   private static Shape rim() {
      return Shapes.tube(
            0.02F,
            23.64F,
            1.44F,
            0.36F,
            -0.5F,
            23.84F,
            1.3F,
            0.41F,
            -1.12F,
            24.08F,
            0.8F,
            0.43F,
            -1.42F,
            24.2F,
            -0.18F,
            0.43F,
            -1.1F,
            24.3F,
            -1.14F,
            0.43F,
            0.1F,
            24.34F,
            -1.68F,
            0.43F
         )
         .flattenFrom(1.25F, 0.0F, 24.1F, -0.25F)
         .mirrorX();
   }

   private static int hoodie(float x, float y, float z) {
      float rho = (float)Math.sqrt(x * x + (z + 0.2F) * (z + 0.2F));
      float collar = smooth(22.4F, 23.3F, y) * (1.0F - smooth(1.7F, 2.3F, rho)) * 0.6F;
      float fold = (1.0F - smooth(14.6F, 15.3F, y)) * 0.55F;
      return Colors.mix(-4200487, -7092297, Math.max(collar, fold));
   }

   private static int rimColor(float x, float y, float z) {
      float rho = (float)Math.sqrt(x * x + (z + 0.2F) * (z + 0.2F));
      float top = smooth(0.28F, 0.36F, y - 24.0F + 0.22F * z);
      return Colors.mix(-4200487, -744532, Math.max(top, 1.0F - smooth(1.12F, 1.38F, rho)));
   }

   private static int hoodColor(float x, float y, float z) {
      float ex = x / 2.2F;
      float ey = (y - 21.7F) / 2.2F;
      float ez = (z + 1.85F) / 0.85F;
      float cut = (y - 22.7F - 0.5F * (z + 2.2F)) * 0.894F;
      return cut > -0.5F && ex * ex + ey * ey + ez * ez < 0.8F ? -744532 : -4200487;
   }

   private static Shape skirt() {
      return Shapes.field(Mika::skirtDistance, -3.3F, 11.8F, -2.8F, 3.3F, 14.4F, 2.8F).paint(Paint.field(Mika::skirtColor));
   }

   private static float flare(float y) {
      float t = Math.max(0.0F, Math.min(1.0F, (14.0F - y) / 2.1F));
      return t * (2.0F - t);
   }

   private static float pleat(float x, float z, float t) {
      float rx = 2.2F + 0.75F * t;
      float rz = 1.66F + 0.84F * t;
      double f = Math.atan2(x / rx, z / rz) * 16.0 / (Math.PI * 2);
      f -= Math.floor(f);
      return (float)Math.abs(2.0 * f - 1.0);
   }

   private static float skirtDistance(float x, float y, float z) {
      float t = flare(y);
      float rx = 2.2F + 0.75F * t;
      float rz = 1.66F + 0.84F * t;
      float a = x / rx;
      float b = z / rz;
      float k = (float)Math.sqrt(a * a + b * b);
      float side = (k - 1.0F) * Math.min(rx, rz) * 0.72F - 0.2F * t * (pleat(x, z, t) - 0.5F);
      return Math.max(side, Math.max(y - 14.3F, 11.9F - y));
   }

   private static int skirtColor(float x, float y, float z) {
      float t = flare(y);
      return Colors.mix(-14275250, -12892555, 0.45F + 0.55F * (1.0F - t + t * pleat(x, z, t)));
   }

   private static Decal skirtStripe() {
      Canvas c = new Canvas(512, 24);
      c.fill(Shape2D.box(256.0F, 12.0F, 256.0F, 7.0F, 0.0F), -5839921);
      return Decal.wrap(c, 0.0F, 12.35F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.16F).radius(2.2F, 3.2F);
   }

   private static void arms(Rig rig) {
      Shape shoulder = Shapes.sphere(-2.72F, 21.95F, -0.15F, 0.95F);
      Shape upper = Shapes.cone(-2.85F, 21.6F, -0.15F, 0.9F, -3.35F, 18.4F, -0.2F, 0.88F);
      Shape elbow = Shapes.sphere(-3.38F, 18.3F, -0.18F, 0.9F);
      Shape fore = Shapes.cone(-3.38F, 18.2F, -0.18F, 0.9F, -3.72F, 15.0F, 0.45F, 1.12F);
      Shape sleeve = Shapes.blend(0.4F, shoulder, upper, elbow, fore).paint(Paint.field(Mika::sleeve));
      Shape cuff = Shapes.cylinder(-3.74F, 15.05F, 0.47F, -3.8F, 13.4F, 0.69F, 0.9F, 0.28F).paint(Paint.field(Mika::cuff));
      Shape arm = Shapes.blend(0.15F, sleeve, cuff);
      Part right = rig.part(Bone.RIGHT_ARM)
         .pivot(-2.72F, 21.95F, -0.15F)
         .grip(-3.8F, 12.9F, 0.9F)
         .shape(arm)
         .detail(0.9F)
         .resolution(1.05F)
         .outline(-10774650, 0.12F)
         .occlusion(0.3F)
         .flatLight(0.2F)
         .swing(0.65F, 40.0F)
         .joint(-3.38F, 18.3F, -0.18F, 0.7F)
         .decal(cuffRib());
      rig.part(Bone.LEFT_ARM).mirrorOf(right);
      Part hand = rig.extra("hand_r", Bone.RIGHT_ARM).shape(hand()).resolution(0.4F).detail(2.6F).flatLight(1.0F).occlusion(0.25F).outline(-5740966, 0.08F);
      rig.extra("hand_l", Bone.LEFT_ARM).mirrorOf(hand);
   }

   private static int sleeve(float x, float y, float z) {
      float t = Math.max(0.0F, Math.min(1.0F, (21.9F - y) / 6.9F));
      float inner = smooth(0.25F, 0.85F, x + 2.8F + 0.94F * t) * 0.5F;
      float crease = (1.0F - smooth(0.2F, 0.55F, Math.abs(y - 18.25F))) * smooth(0.2F, 0.7F, z) * 0.45F;
      return Colors.mix(-4200487, -7092297, Math.max(inner, crease));
   }

   private static int cuff(float x, float y, float z) {
      float ax = -0.06F;
      float ay = -1.65F;
      float az = 0.22F;
      float l = (float)Math.sqrt(ax * ax + ay * ay + az * az);
      float px = x + 3.8F;
      float py = y - 13.4F;
      float pz = z - 0.69F;
      float along = -(px * ax + py * ay + pz * az) / l;
      float rx = px + along * ax / l;
      float ry = py + along * ay / l;
      float rz = pz + along * az / l;
      float radial = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
      return Colors.mix(-5710645, -9461870, (1.0F - smooth(0.04F, 0.12F, along)) * (1.0F - smooth(0.55F, 0.68F, radial)));
   }

   private static Shape hand() {
      float[][] f = new float[][]{{-3.47F, 0.86F, 13.06F}, {-3.79F, 0.95F, 12.97F}, {-4.1F, 0.86F, 13.04F}};
      Shape[] tips = new Shape[f.length];

      for (int i = 0; i < f.length; i++) {
         tips[i] = Shapes.capsule(f[i][0], 13.5F, f[i][1] - 0.12F, f[i][0], f[i][2], f[i][1], 0.16F);
      }

      Shape thumb = Shapes.capsule(-3.36F, 13.48F, 0.64F, -3.3F, 13.2F, 0.76F, 0.15F);
      return Shapes.union(Shapes.union(tips), thumb).paint(Paint.of(-9532));
   }

   private static void legs(Rig rig) {
      Shape hip = Shapes.sphere(-1.12F, 13.2F, -0.05F, 1.0F);
      Shape thigh = Shapes.cone(-1.18F, 12.8F, 0.0F, 1.13F, -1.1F, 7.9F, 0.1F, 0.88F);
      Shape knee = Shapes.sphere(-1.08F, 7.45F, 0.12F, 0.82F);
      Shape kneecap = Shapes.sphere(-1.07F, 7.5F, 0.48F, 0.5F);
      Shape calf = Shapes.tube(-1.08F, 7.2F, 0.1F, 0.84F, -1.1F, 5.6F, -0.15F, 0.93F, -1.13F, 3.6F, -0.02F, 0.64F, -1.15F, 2.2F, 0.0F, 0.52F);
      Shape leg = Shapes.blend(0.45F, hip, thigh, knee, kneecap, calf).paint(Paint.field(Mika::tights));
      Part right = rig.part(Bone.RIGHT_LEG)
         .pivot(-1.2F, 13.7F, 0.0F)
         .shape(leg)
         .detail(0.85F)
         .resolution(1.1F)
         .outline(-15068908, 0.12F)
         .occlusion(0.35F)
         .swing(0.55F, 30.0F)
         .joint(-1.08F, 7.45F, 0.12F, 0.8F);
      rig.part(Bone.LEFT_LEG).mirrorOf(right);
      Part shoe = rig.extra("shoe_r", Bone.RIGHT_LEG)
         .shape(sneaker().rotate(0.0F, -7.0F, 0.0F, -1.2F, 0.0F, 0.2F))
         .resolution(0.75F)
         .detail(1.0F)
         .outline(-6659470, 0.12F)
         .decal(laces());
      rig.extra("shoe_l", Bone.LEFT_LEG).mirrorOf(shoe);
   }

   private static int tights(float x, float y, float z) {
      double a = Math.atan2(-(x + 1.1F), z - 0.02F) - 0.35;
      float sheen = smooth(0.6F, 0.95F, (float)Math.cos(a)) * smooth(2.4F, 4.5F, y) * 0.5F;
      float dk = (float)Math.sqrt((y - 7.5F) * (y - 7.5F) + (x + 1.07F) * (x + 1.07F) * 1.4F);
      float knee = (1.0F - smooth(0.3F, 0.8F, dk)) * smooth(0.2F, 0.7F, z) * 0.55F;
      return Colors.mix(-12965330, -9808288, Math.max(sheen, knee));
   }

   private static Shape sneaker() {
      Shape sole = Shapes.blend(0.25F, Shapes.box(-1.2F, 0.3F, 0.5F, 0.9F, 0.3F, 1.84F, 0.26F), Shapes.ellipsoid(-1.2F, 0.36F, 2.05F, 0.82F, 0.34F, 0.5F))
         .intersect(Shapes.above(0.0F))
         .paint(Paint.of(-264207).band(-1011800, 0.0F, 0.46F, 0.0F, 0.0F, 1.0F, 0.0F, 0.07F, 0.05F));
      Shape toe = Shapes.ellipsoid(-1.2F, 0.9F, 1.15F, 0.74F, 0.55F, 1.12F);
      Shape heel = Shapes.ellipsoid(-1.2F, 1.28F, -0.48F, 0.75F, 0.95F, 0.84F);
      Shape upper = Shapes.blend(0.45F, toe, heel).color(-542005);
      Shape cap = Shapes.ellipsoid(-1.2F, 0.72F, 1.86F, 0.68F, 0.36F, 0.5F).color(-264207);
      Shape collar = Shapes.torus(-1.2F, 2.1F, -0.4F, 0.6F, 0.26F).color(-5839921);
      Shape tongue = Shapes.box(-1.2F, 1.75F, 0.35F, 0.42F, 0.42F, 0.3F, 0.18F).rotate(-25.0F, 0.0F, 0.0F, -1.2F, 1.75F, 0.35F).color(-207907);
      Shape tab = Shapes.capsule(-1.2F, 1.8F, -1.2F, -1.2F, 2.45F, -1.02F, 0.17F).color(-5839921);
      return Shapes.union(sole, Shapes.blend(0.12F, upper, cap), collar, tongue, tab);
   }

   private static void tail(Rig rig) {
      float[] pts = new float[]{
         0.0F,
         13.95F,
         -1.45F,
         0.58F,
         0.15F,
         13.35F,
         -2.7F,
         0.76F,
         0.6F,
         13.2F,
         -4.1F,
         0.92F,
         1.6F,
         14.0F,
         -5.15F,
         1.06F,
         2.6F,
         15.9F,
         -5.5F,
         1.15F,
         3.2F,
         18.1F,
         -5.15F,
         1.15F,
         3.35F,
         19.9F,
         -4.35F,
         1.04F,
         3.0F,
         21.0F,
         -3.45F,
         0.72F,
         2.55F,
         21.45F,
         -2.85F,
         0.26F
      };
      Mika.Curve curve = new Mika.Curve(pts);
      Shape tufts = Shapes.union(
         Shapes.strand(0.24F, 0.04F, 2.68F, 21.32F, -3.02F, 2.36F, 21.62F, -2.68F, 1.96F, 21.62F, -2.62F),
         Shapes.strand(0.24F, 0.04F, 2.68F, 21.32F, -3.02F, 2.46F, 21.6F, -2.62F, 2.42F, 21.62F, -2.2F),
         Shapes.strand(0.24F, 0.04F, 2.68F, 21.32F, -3.02F, 2.44F, 21.7F, -2.72F, 2.4F, 22.12F, -2.62F)
      );
      Part tail = rig.extra("tail", Bone.BODY)
         .pivot(0.0F, 13.95F, -1.45F)
         .shape(Shapes.union(Shapes.tube(pts), tufts).paint(Paint.field(curve::tailColor)))
         .detail(1.0F)
         .resolution(1.1F)
         .flatLight(0.3F)
         .outline(-6142180, 0.1F)
         .motion(Motion.tail().idle(3.0F, 14.0F, 4.0F, 0.4F).walk(4.0F, 12.0F, 3.0F, 1.0F).spring(30.0F, 4.0F).bend(1.4F));
      Canvas chevron = new Canvas(128, 96);
      chevron.fill(Shape2D.union(Shape2D.line(10.0F, 80.0F, 64.0F, 26.0F, 24.0F), Shape2D.line(64.0F, 26.0F, 118.0F, 80.0F, 24.0F)), -1999082968, 6.0F);

      for (float t : new float[]{0.3F, 0.43F, 0.56F}) {
         tail.decal(curve.chevron(chevron, t));
      }
   }

   private static float smooth(float e0, float e1, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
      return t * t * (3.0F - 2.0F * t);
   }

   private static Decal face() {
      Canvas c = new Canvas(256, 200);
      float sh = 13.356522F;
      Shape2D[] shadow = new Shape2D[FRINGE.length];

      for (int i = 0; i < FRINGE.length; i++) {
         float[] f = FRINGE[i];
         float ex = f[7] + (f[5] - f[7]) * 0.12F;
         float ey = f[8] + (f[6] - f[8]) * 0.12F;
         shadow[i] = Shape2D.path(f[0] * 1.7F * 55.652176F, 9.0F, u(f[3]), v(f[4]) + sh, u(f[5]), v(f[6]) + sh, u(ex), v(ey) + sh);
      }

      c.fill(Shape2D.union(shadow), -1713793398, 3.0F);

      for (int s = -1; s <= 1; s += 2) {
         eye(c, 128.0F + s * 53.0F, 114.0F, s);
      }

      c.fill(Shape2D.curve(125.5F, 146.5F, 128.5F, 150.5F, 131.5F, 149.0F, 2.2F, 1.4F), -1713795968);
      c.fill(Shape2D.path(3.0F, 3.0F, 117.0F, 177.0F, 122.5F, 182.5F, 128.0F, 178.5F, 133.5F, 182.5F, 139.0F, 177.0F), -7455954);
      return Decal.front(c, 0.0F, wy(27.203125F), wz(2.1F), 5.152F, 4.0249996F).depth(1.4F, 1.4F).maxAngle(72.0F).flatLight(1.0F).occlusion(0.3F);
   }

   private static void eye(Canvas c, float ex, float ey, int s) {
      Shape2D lid = Shape2D.ellipse(ex, ey + 10.0F, 31.0F, 40.0F).rotate(s * 12.0F, ex, ey + 10.0F);
      Shape2D white = Shape2D.ellipse(ex, ey + 2.0F, 26.0F, 31.0F).intersect(lid);
      c.fill(white, Paint2D.linear(ex, ey - 28.0F, ex, ey + 4.0F, -2571046, -776));
      Shape2D ring = Shape2D.ellipse(ex + s, ey + 5.0F, 21.0F, 27.0F);
      c.fill(ring.intersect(white), Paint2D.linear(ex, ey - 22.0F, ex, ey + 32.0F, -11920890, -5748206, -1535446, -11414));
      c.fill(Shape2D.ellipse(ex + s, ey + 6.0F, 9.0F, 21.0F).intersect(white), -1711287446, 4.0F);
      c.fill(ring.outline(2.5F).intersect(white), -1338371580);
      float px = ex + s;
      float py = ey + 6.0F;
      c.fill(Shape2D.circle(px - 42.2F, py, 46.7F).intersect(Shape2D.circle(px + 42.2F, py, 46.7F)), -14414846);
      c.fill(Shape2D.ellipse(ex - 8.0F, ey - 8.0F, 7.5F, 9.0F).rotate(-20.0F, ex - 8.0F, ey - 8.0F), -1);
      c.fill(Shape2D.circle(ex + 9.0F, ey + 17.0F, 3.5F), -419430401);
      c.fill(Shape2D.curve(ex - s * 28.0F, ey - 8.0F, ex - s * 2.0F, ey - 42.0F, ex + s * 30.0F, ey - 24.0F, 4.0F, 9.5F), -13887470);
      c.fill(Shape2D.taper(ex + s * 24.0F, ey - 24.0F, 7.0F, ex + s * 40.0F, ey - 36.0F, 1.5F), -13887470);
      c.fill(Shape2D.curve(ex + s * 8.0F, ey + 34.0F, ex + s * 21.0F, ey + 31.0F, ex + s * 26.0F, ey + 17.0F, 1.2F, 3.0F), -866506210);
      c.fill(Shape2D.curve(ex - s * 17.0F, ey - 38.0F, ex + s * 2.0F, ey - 48.0F, ex + s * 24.0F, ey - 38.0F, 1.5F, 2.2F), 1721786442);
      c.fill(Shape2D.curve(ex - s * 16.0F, ey - 50.0F, ex + s * 2.0F, ey - 58.0F, ex + s * 22.0F, ey - 53.0F, 3.0F, 2.0F), -4691398);
      float bx = ex + s * 10.0F;
      float by = ey + 42.0F;
      c.fill(Shape2D.ellipse(bx, by, 20.0F, 9.0F), 1627357068, 9.0F);

      for (int i = 0; i < 3; i++) {
         c.fill(Shape2D.line(bx - 8.0F + i * 7.0F, by - 4.0F, bx - 12.0F + i * 7.0F, by + 4.0F, 1.6F), 1894277230);
      }
   }

   private static Decal pocketDecal() {
      Canvas c = new Canvas(240, 144);
      int stitch = -1722777742;

      for (int i = 0; i < 18; i++) {
         c.fill(Shape2D.box(43.5F + i * 9.0F, 16.5F, 2.6F, 0.75F, 0.7F), stitch);
      }

      for (int i = 0; i < 23; i++) {
         c.fill(Shape2D.box(21.0F + i * 9.0F, 129.0F, 2.6F, 0.75F, 0.7F), stitch);
      }

      for (int s = -1; s <= 1; s += 2) {
         float tx = 120.0F + s * 78.0F;
         float bx = 120.0F + s * 102.75F;
         c.fill(Shape2D.taper(tx, 13.5F, 6.0F, bx, 132.0F, 3.75F), -1872719998);
         c.fill(Shape2D.taper(tx + s * 2.25F, 13.5F, 2.25F, bx + s * 2.25F, 132.0F, 1.5F), -1605733274);
         c.fill(Shape2D.taper(tx - s * 4.5F, 16.5F, 1.9F, bx - s * 4.5F, 129.0F, 1.5F), 1627389951);
      }

      return Decal.front(c, 0.0F, 16.03F, 2.3F, 5.0F, 3.0F).depth(1.2F, 1.2F).maxAngle(75.0F);
   }

   private static Decal pawDecal() {
      Canvas c = new Canvas(128, 128);
      int pink = -877910;
      Shape2D pad = Shape2D.blend(8.0F, Shape2D.ellipse(64.0F, 90.0F, 27.0F, 19.0F), Shape2D.circle(48.0F, 78.0F, 15.0F), Shape2D.circle(80.0F, 78.0F, 15.0F));
      c.fill(pad, pink);
      c.fill(Shape2D.ellipse(28.0F, 52.0F, 11.0F, 14.0F).rotate(-25.0F, 28.0F, 52.0F), pink);
      c.fill(Shape2D.ellipse(51.0F, 33.0F, 11.0F, 14.5F).rotate(-8.0F, 51.0F, 33.0F), pink);
      c.fill(Shape2D.ellipse(77.0F, 33.0F, 11.0F, 14.5F).rotate(8.0F, 77.0F, 33.0F), pink);
      c.fill(Shape2D.ellipse(100.0F, 52.0F, 11.0F, 14.0F).rotate(25.0F, 100.0F, 52.0F), pink);
      return Decal.front(c, 1.2F, 20.6F, 1.6F, 1.5F, 1.5F).depth(1.5F, 1.5F).maxAngle(70.0F);
   }

   private static Canvas ribs(int count, int width) {
      Canvas c = new Canvas(width, 16);
      float step = (float)width / count;

      for (int i = 0; i < count; i++) {
         float x = i * step + step * 0.5F;
         c.fill(Shape2D.box(x, 8.0F, step * 0.18F, 8.0F, 0.0F), 1078491756);
      }

      return c;
   }

   private static Decal hemRib() {
      return Decal.wrap(ribs(64, 506), 0.0F, 14.2F, -0.05F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.62F).radius(1.2F, 3.3F);
   }

   private static Decal cuffRib() {
      return Decal.wrap(ribs(24, 256), -3.77F, 14.3F, 0.57F, 0.06F, 1.65F, -0.22F, 0.0F, 0.0F, 1.0F, 360.0F, 1.2F).radius(0.6F, 1.4F);
   }

   private static Decal bellDecal(float x) {
      Canvas c = new Canvas(64, 64);
      c.fill(Shape2D.box(32.0F, 30.0F, 30.0F, 2.0F, 1.0F), -5737444);
      c.fill(Shape2D.circle(32.0F, 40.0F, 4.5F), -11915254);
      c.fill(Shape2D.line(32.0F, 40.0F, 32.0F, 60.0F, 3.5F), -11915254);
      c.fill(Shape2D.ellipse(24.0F, 20.0F, 5.0F, 3.5F), -1426063361);
      return Decal.front(c, x, 19.6F, 2.5F, 1.0F, 1.0F).depth(0.6F, 0.6F).maxAngle(70.0F);
   }

   private static Decal laces() {
      Canvas c = new Canvas(96, 128);
      int eyelet = -7710102;

      for (int i = 0; i < 4; i++) {
         float y = 30.0F + i * 22.0F;
         c.fill(Shape2D.line(24.0F, y, 72.0F, y + 14.0F, 6.0F), -1);
         c.fill(Shape2D.line(72.0F, y, 24.0F, y + 14.0F, 6.0F), -1);
         c.fill(Shape2D.circle(20.0F, y, 3.5F), eyelet);
         c.fill(Shape2D.circle(76.0F, y, 3.5F), eyelet);
      }

      c.fill(Shape2D.ellipse(36.0F, 14.0F, 11.0F, 6.0F).outline(4.0F), -1);
      c.fill(Shape2D.ellipse(60.0F, 14.0F, 11.0F, 6.0F).outline(4.0F), -1);
      c.fill(Shape2D.circle(48.0F, 16.0F, 4.0F), -1);
      Vec3 pivot = new Vec3(-1.2F, 0.0F, 0.2F);
      Vec3 at = new Vec3(-1.2F, 1.62F, 0.72F).rotate(0.0F, -7.0F, 0.0F, pivot);
      Vec3 dir = new Vec3(0.0F, 0.8F, 0.6F).rotate(0.0F, -7.0F, 0.0F);
      Vec3 up = new Vec3(0.0F, 0.6F, -0.8F).rotate(0.0F, -7.0F, 0.0F);
      return Decal.project(c, at.x(), at.y(), at.z(), dir.x(), dir.y(), dir.z(), up.x(), up.y(), up.z(), 0.95F, 1.25F).depth(0.6F, 0.6F).maxAngle(65.0F);
   }

   @Environment(EnvType.CLIENT)
   private static final class Curve {
      private final float[] xs;
      private final float[] ys;
      private final float[] zs;
      private final float[] len;
      private final int n;

      Curve(float[] p) {
         int count = p.length / 4 - 2;
         int per = 12;
         this.n = count * per + 1;
         this.xs = new float[this.n];
         this.ys = new float[this.n];
         this.zs = new float[this.n];
         this.len = new float[this.n];
         int k = 0;

         for (int i = 1; i <= count; i++) {
            int prev = (i - 1) * 4;
            int cur = i * 4;
            int next = (i + 1) * 4;
            float ax = i == 1 ? p[0] : (p[prev] + p[cur]) * 0.5F;
            float ay = i == 1 ? p[1] : (p[prev + 1] + p[cur + 1]) * 0.5F;
            float az = i == 1 ? p[2] : (p[prev + 2] + p[cur + 2]) * 0.5F;
            float cx = i == count ? p[next] : (p[cur] + p[next]) * 0.5F;
            float cy = i == count ? p[next + 1] : (p[cur + 1] + p[next + 1]) * 0.5F;
            float cz = i == count ? p[next + 2] : (p[cur + 2] + p[next + 2]) * 0.5F;

            for (int j = 0; j < per || i == count && j == per; j++) {
               float t = (float)j / per;
               float u = 1.0F - t;
               this.xs[k] = u * u * ax + 2.0F * u * t * p[cur] + t * t * cx;
               this.ys[k] = u * u * ay + 2.0F * u * t * p[cur + 1] + t * t * cy;
               this.zs[k] = u * u * az + 2.0F * u * t * p[cur + 2] + t * t * cz;
               if (k > 0) {
                  float dx = this.xs[k] - this.xs[k - 1];
                  float dy = this.ys[k] - this.ys[k - 1];
                  float dz = this.zs[k] - this.zs[k - 1];
                  this.len[k] = this.len[k - 1] + (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
               }

               k++;
            }
         }
      }

      float param(float x, float y, float z) {
         int best = 0;
         float bd = Float.MAX_VALUE;

         for (int i = 0; i < this.n; i++) {
            float dx = x - this.xs[i];
            float dy = y - this.ys[i];
            float dz = z - this.zs[i];
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
               bd = d;
               best = i;
            }
         }

         return this.len[best] / this.len[this.n - 1];
      }

      int tailColor(float x, float y, float z) {
         float t = this.param(x, y, z);
         return Colors.mix(Colors.mix(-3647457, -681403, Mika.smooth(0.04F, 0.22F, t)), -3618, Mika.smooth(0.72F, 0.8F, t));
      }

      Decal chevron(Canvas canvas, float t) {
         float target = t * this.len[this.n - 1];
         int i = 1;

         while (i < this.n - 2 && this.len[i] < target) {
            i++;
         }

         float tx = this.xs[i + 1] - this.xs[i - 1];
         float ty = this.ys[i + 1] - this.ys[i - 1];
         float tz = this.zs[i + 1] - this.zs[i - 1];
         return Decal.wrap(canvas, this.xs[i], this.ys[i], this.zs[i], tx, ty, tz, this.xs[i] - 1.8F, this.ys[i] - 17.6F, this.zs[i] + 3.3F, 140.0F, 0.9F)
            .radius(0.3F, 1.6F);
      }
   }
}
