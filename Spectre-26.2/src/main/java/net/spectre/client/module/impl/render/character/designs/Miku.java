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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Miku extends CharacterDesign {
   private static final int SKIN = -8241;
   private static final int SKIN_SHADE = -1198942;
   private static final int SKIN_LINE = -5740452;
   private static final int HAIR_ROOT = -14705514;
   private static final int HAIR = -12793152;
   private static final int HAIR_TIP = -7936291;
   private static final int HAIR_SHADE = -15368325;
   private static final int HAIR_LINE = -15903662;
   private static final int SHINE = -857866245;
   private static final int SHIRT = -2499360;
   private static final int SHIRT_SHADE = -5525063;
   private static final int SHIRT_LINE = -10788500;
   private static final int BLACK = -13618373;
   private static final int BLACK_SHADE = -14934492;
   private static final int BLACK_IN = -15592681;
   private static final int BLACK_LINE = -16119026;
   private static final int TEAL = -13645638;
   private static final int TEAL_DARK = -14971002;
   private static final int MAGENTA = -1882484;
   private static final int TATTOO = -2608065;
   private static final float SKIRT_TOP = 17.9F;
   private static final float SKIRT_HEM = 13.3F;
   private static final int PLEATS = 18;
   private static final float HS = 1.12F;
   private static final float DROP = 0.4F;
   private static final float HX = 0.0F;
   private static final float HY = 28.9F;
   private static final float HZ = -0.3F;
   private static final float PPU = 56.88889F;
   private static final float FACE_TOP = 29.95F;
   private static final float[][] LOCKS = new float[][]{
      {-0.1F, 28.3F, -2.0F, 0.95F, 0.12F, 0.34F},
      {-1.0F, 28.75F, -25.0F, 0.95F, 0.28F, 0.3F},
      {-1.92F, 28.4F, -47.0F, 0.92F, 0.26F, 0.3F},
      {0.9F, 28.85F, 24.0F, 0.95F, 0.28F, 0.3F},
      {1.9F, 28.5F, 46.0F, 0.92F, 0.25F, 0.3F},
      {-0.55F, 28.5F, -13.0F, 0.45F, 0.16F, 0.44F},
      {0.46F, 28.62F, 11.0F, 0.45F, 0.18F, 0.44F},
      {-1.46F, 28.65F, -36.0F, 0.42F, 0.2F, 0.42F},
      {1.42F, 28.7F, 35.0F, 0.42F, 0.2F, 0.42F}
   };

   public Miku() {
      super("miku", "Miku");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-14011336, 0.12F).occlusion(0.3F).flatLight(0.55F);
      float[][] locks = lockPaths();
      Paint hair = hairPaint();
      head(rig, locks);
      hair(rig, hair);
      fringe(rig, locks);
      twinTails(rig);
      headset(rig);
      body(rig);
      skirt(rig);
      arms(rig);
      legs(rig);
   }

   private static Shape up(Shape s) {
      return s.scale(1.12F, 0.0F, 24.6F, -0.2F).move(0.0F, -0.4F, 0.0F);
   }

   private static float tx(float x) {
      return x * 1.12F;
   }

   private static float ty(float y) {
      return 24.6F + (y - 24.6F) * 1.12F - 0.4F;
   }

   private static float tz(float z) {
      return -0.2F + (z + 0.2F) * 1.12F;
   }

   private static Shape headBase() {
      return Shapes.blend(
         0.85F,
         Shapes.ellipsoid(0.0F, 28.72F, -0.15F, 2.28F, 2.4F, 2.35F),
         Shapes.ellipsoid(0.0F, 27.75F, 0.55F, 2.05F, 1.3F, 1.75F),
         Shapes.cone(0.0F, 27.3F, 0.62F, 1.35F, 0.0F, 26.05F, 1.2F, 0.3F)
      );
   }

   private static void head(Rig rig, float[][] locks) {
      Shape neck = Shapes.capsule(0.0F, 23.3F, -0.45F, 0.0F, 25.9F, -0.3F, 0.58F);
      float chinY = ty(25.82F);
      float chinZ = tz(1.2F);
      Paint skin = Paint.field((x, y, z) -> {
         float jaw = chinY + Math.max(0.0F, chinZ - z) * 0.62F;
         float t = smooth(jaw + 0.05F, jaw - 0.2F, y) * (0.6F + 0.4F * smooth(23.4F, 24.8F, y));
         return Colors.mix(-8241, -1198942, t);
      });
      Shape skull = up(headBase().intersect(Shapes.below(30.2F)));
      rig.part(Bone.HEAD)
         .pivot(0.0F, 24.6F, -0.2F)
         .shape(Shapes.blend(0.45F, skull, neck).paint(skin))
         .detail(2.0F)
         .flatLight(1.0F)
         .occlusion(0.22F)
         .outline(-5740452, 0.1F)
         .decal(face(locks));
   }

   private static float[][] lockPaths() {
      Shape head = headBase();
      float[][] out = new float[LOCKS.length][];

      for (int i = 0; i < LOCKS.length; i++) {
         float[] l = LOCKS[i];
         float[] a = onHead(36.0F, l[2], -0.15F);
         float[] b = onHead(58.0F, l[2], 0.05F);
         float x2 = b[0] + (l[0] - b[0]) * 0.75F;
         float y2 = l[1] + 0.68F;
         float x3 = l[0] - Math.signum(l[0]) * l[4];
         out[i] = new float[]{a[0], a[1], a[2], b[0], b[1], b[2], x2, y2, frontZ(head, x2, y2) + l[5], x3, l[1], frontZ(head, x3, l[1]) + 0.22F, l[3]};
      }

      return out;
   }

   private static float frontZ(Shape s, float x, float y) {
      float z = 3.5F;

      while (z > -1.0F && s.distance(x, y, z) > 0.0F) {
         z -= 0.02F;
      }

      return z;
   }

   private static void fringe(Rig rig, float[][] locks) {
      Shape[] parts = new Shape[locks.length];
      Shape[] blades = new Shape[locks.length];

      for (int i = 0; i < locks.length; i++) {
         float[] p = locks[i];
         float w = p[12];
         Shape s = Shapes.tube(p[0], p[1], p[2], 0.9F * w, p[3], p[4], p[5], 1.15F * w, p[6], p[7], p[8], 0.95F * w, p[9], p[10], p[11], 0.12F)
            .flattenFrom(3.0F, 0.0F, 28.7F, -0.15F);
         blades[i] = Shapes.extrude(Shape2D.polygon(blade(p)), 5.0F, 0.0F);
         parts[i] = Shapes.intersect(s, blades[i]);
      }

      Shape bangs = Shapes.intersect(Shapes.blend(0.15F, parts), Shapes.union(blades));
      Shape skin = headBase();
      Paint paint = Paint.field((x, y, z) -> {
         int c = Colors.mix(Colors.mix(-14705514, -12793152, 0.45F), -12793152, smooth(30.9F, 29.7F, y));
         c = Colors.mix(c, -7936291, 0.45F * smooth(29.5F, 28.5F, y));
         return Colors.mix(c, -15368325, 0.6F * (1.0F - smooth(0.12F, 0.42F, skin.distance(x, y, z))));
      });
      rig.extra("fringe", Bone.HEAD)
         .shape(up(bangs.paint(paint)))
         .resolution(0.45F)
         .detail(1.05F)
         .flatLight(0.7F)
         .occlusion(0.1F)
         .outline(-15903662, 0.09F)
         .decal(shine());
   }

   private static float[] blade(float[] p) {
      float w = p[12];
      boolean right = p[9] < 0.0F;
      float x0 = p[3];
      float y0 = 31.6F;
      float cx = p[6];
      float cy = p[7];
      float x1 = p[9];
      float y1 = p[10];
      int n = 12;
      float[] xy = new float[(2 * n + 1) * 2];

      for (int i = 0; i <= n; i++) {
         float t = (float)i / n;
         float u = 1.0F - t;
         float bx = u * u * x0 + 2.0F * u * t * cx + t * t * x1;
         float by = u * u * y0 + 2.0F * u * t * cy + t * t * y1;
         float tx = u * (cx - x0) + t * (x1 - cx);
         float ty = u * (cy - y0) + t * (y1 - cy);
         float tl = (float)Math.sqrt(tx * tx + ty * ty);
         float nx = -ty / tl;
         float ny = tx / tl;
         if (nx > 0.0F != right) {
            nx = -nx;
            ny = -ny;
         }

         float wi = w * (1.0F - smooth(0.6F, 1.0F, t));
         float wo = w * (1.0F - (float)Math.pow(smooth(0.3F, 1.0F, t), 0.75));
         xy[i * 2] = bx + nx * wi;
         xy[i * 2 + 1] = by + ny * wi;
         if (i < n) {
            xy[(2 * n - i) * 2] = bx - nx * wo;
            xy[(2 * n - i) * 2 + 1] = by - ny * wo;
         }
      }

      return xy;
   }

   private static Decal shine() {
      Canvas c = new Canvas(280, 22);
      float[][] dashes = new float[][]{{-68.0F, -54.0F}, {-45.0F, -25.0F}, {-13.0F, 9.0F}, {20.0F, 38.0F}, {48.0F, 64.0F}};

      for (float[] d : dashes) {
         float a0 = d[0];
         float a1 = d[1];
         float am = (a0 + a1) * 0.5F;
         float w = 6.5F - Math.abs(am) * 0.025F;
         c.fill(Shape2D.curve(140.0F + a0 * 2.0F, ringY(a0), 140.0F + a0 + am, ringY((a0 + am) * 0.5F), 140.0F + am * 2.0F, ringY(am), 1.0F, w), -857866245);
         c.fill(Shape2D.curve(140.0F + am * 2.0F, ringY(am), 140.0F + am + a1, ringY((am + a1) * 0.5F), 140.0F + a1 * 2.0F, ringY(a1), w, 1.0F), -857866245);
      }

      return Decal.wrap(c, 0.0F, ty(30.16F), tz(-0.3F), 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 140.0F, 0.6272F).radius(1.2F, 3.8F).offset(0.03F);
   }

   private static float ringY(float deg) {
      float y = 30.4F - 0.36F * (1.0F - (float)Math.cos(Math.toRadians(deg)));
      return (30.44F - y) * 40.0F;
   }

   private static void hair(Rig rig, Paint paint) {
      List<Shape> cap = new ArrayList<>();
      cap.add(Shapes.ellipsoid(0.0F, 28.9F, -0.3F, 2.58F, 2.62F, 2.7F));

      for (float phi : new float[]{64.0F, 88.0F, 272.0F, 296.0F}) {
         cap.add(capLock(phi, 5.0F, 98.0F, 0.8F, 0.62F));
      }

      for (float phi = 130.0F; phi <= 230.0F; phi += 25.0F) {
         cap.add(capLock(phi, 20.0F, 118.0F, 0.85F, 0.5F));
      }

      Shape faceCut = Shapes.box(0.0F, 26.3F, 2.3F, 2.95F, 3.3F, 2.8F, 0.9F);
      Shape sideFill = Shapes.blend(
            0.3F,
            Shapes.strand(0.62F, 0.44F, -2.3F, 29.6F, -0.05F, -2.48F, 28.1F, -0.12F, -2.45F, 26.5F, -0.22F).flatten(1.6F, 1.0F, 0.0F, 0.0F),
            Shapes.strand(0.68F, 0.44F, -2.25F, 29.5F, -0.95F, -2.46F, 27.9F, -1.05F, -2.45F, 26.3F, -1.12F).flatten(1.6F, 1.0F, 0.0F, 0.0F)
         )
         .mirrorX();
      Shape nape = Shapes.strand(0.9F, 0.3F, 0.0F, 28.0F, -2.35F, 0.0F, 26.6F, -2.2F, 0.0F, 25.6F, -1.95F).flatten(2.2F, 0.0F, 0.0F, 1.0F);
      Shape capShape = Shapes.blend(
         0.25F, Shapes.subtract(Shapes.blend(0.3F, cap.toArray(new Shape[0])), faceCut).paint(paint), sideFill.paint(paint.occlusion(0.35F)), nape.paint(paint)
      );
      rig.extra("hair", Bone.HEAD).shape(up(capShape)).detail(1.1F).flatLight(0.55F).occlusion(0.22F).outline(-15903662, 0.09F).decal(shine());
      Shape front = Shapes.tube(
            -1.95F,
            30.4F,
            0.92F,
            0.55F,
            -2.3F,
            29.3F,
            1.12F,
            0.6F,
            -2.42F,
            27.8F,
            1.0F,
            0.55F,
            -2.4F,
            25.9F,
            0.8F,
            0.46F,
            -2.3F,
            24.0F,
            0.78F,
            0.36F,
            -2.15F,
            22.6F,
            0.82F,
            0.05F
         )
         .flatten(1.8F, 1.0F, 0.0F, 0.3F);
      Shape back = Shapes.tube(
            -2.2F,
            30.2F,
            0.1F,
            0.58F,
            -2.52F,
            28.9F,
            0.24F,
            0.62F,
            -2.58F,
            27.3F,
            0.32F,
            0.55F,
            -2.45F,
            25.6F,
            0.5F,
            0.42F,
            -2.36F,
            23.8F,
            0.52F,
            0.25F,
            -2.3F,
            23.0F,
            0.55F,
            0.04F
         )
         .flatten(1.8F, 1.0F, 0.0F, 0.15F);
      Motion sway = new Motion().idle(1.5F, 0.0F, 0.6F, 0.35F).walk(3.0F, 0.0F, 1.0F, 2.0F).drag(8.0F).hang(0.6F).spring(60.0F, 8.0F).bend(1.4F);
      Part lockR = rig.extra("sidelock_r", Bone.HEAD)
         .pivot(tx(-2.3F), ty(30.0F), tz(0.6F))
         .shape(up(Shapes.blend(0.25F, front, back).paint(paint)))
         .motion(sway)
         .resolution(0.7F)
         .detail(0.6F)
         .flatLight(0.5F)
         .occlusion(0.35F)
         .outline(-15903662, 0.09F);
      rig.extra("sidelock_l", Bone.HEAD).mirrorOf(lockR);
   }

   private static void twinTails(Rig rig) {
      float ax = tx(-2.62F);
      float ay = ty(30.2F);
      float az = tz(-1.35F);
      float[][] paths = new float[][]{
         {
               0.0F,
               0.0F,
               1.0F,
               -0.9F,
               -0.5F,
               1.35F,
               -1.5F,
               -2.6F,
               1.5F,
               -1.75F,
               -6.5F,
               1.45F,
               -1.75F,
               -11.5F,
               1.3F,
               -1.6F,
               -16.5F,
               1.05F,
               -1.45F,
               -20.5F,
               0.7F,
               -1.35F,
               -23.4F,
               0.06F
         },
         {
               0.0F,
               0.1F,
               0.8F,
               -1.0F,
               -0.4F,
               1.0F,
               -1.9F,
               -2.8F,
               1.1F,
               -2.2F,
               -7.0F,
               1.0F,
               -2.25F,
               -12.0F,
               0.9F,
               -2.05F,
               -17.0F,
               0.7F,
               -1.85F,
               -20.8F,
               0.45F,
               -1.95F,
               -22.8F,
               0.04F
         },
         {
               0.0F,
               0.1F,
               0.8F,
               -0.7F,
               -0.6F,
               1.0F,
               -1.0F,
               -2.9F,
               1.05F,
               -1.15F,
               -7.2F,
               1.0F,
               -1.1F,
               -12.2F,
               0.9F,
               -0.95F,
               -16.8F,
               0.7F,
               -0.9F,
               -20.2F,
               0.45F,
               -0.75F,
               -22.2F,
               0.04F
         }
      };
      float[] depth = new float[]{0.0F, -0.25F, 0.2F};
      Shape[] strands = new Shape[paths.length];

      for (int i = 0; i < paths.length; i++) {
         float[] p = paths[i];
         float[] xyzr = new float[p.length / 3 * 4];

         for (int k = 0; k < p.length / 3; k++) {
            float t = k / (p.length / 3.0F - 1.0F);
            xyzr[k * 4] = ax + p[k * 3];
            xyzr[k * 4 + 1] = ay + p[k * 3 + 1] * 1.12F;
            xyzr[k * 4 + 2] = az - 1.15F * (float)Math.sin(Math.min(1.0F, t * 3.0F) * Math.PI * 0.5) + depth[i] * t;
            xyzr[k * 4 + 3] = p[k * 3 + 2];
         }

         strands[i] = Shapes.tube(xyzr).flatten(1.7F, 0.25F, 0.0F, 1.0F);
      }

      float endY = ay - 26.208F;
      Paint paint = Paint.field((x, y, z) -> {
         float t = clamp01((ay - y) / (ay - endY));
         int c = Colors.mix(-14705514, -12793152, smooth(0.0F, 0.18F, t));
         c = Colors.mix(c, -7936291, 0.6F * smooth(0.55F, 1.0F, t));
         float inner = smooth(0.4F, -0.6F, x - ax + 1.6F) * 0.35F;
         return Colors.mix(c, -15368325, inner);
      });
      Shape tie = Shapes.union(
            Shapes.box(ax - 0.3F, ay, az, 0.34F, 1.0F, 0.75F, 0.14F).color(-13618373),
            Shapes.box(ax - 0.62F, ay, az + 0.38F, 0.06F, 0.86F, 0.09F, 0.03F).color(-1882484),
            Shapes.box(ax - 0.62F, ay, az - 0.38F, 0.06F, 0.86F, 0.09F, 0.03F).color(-1882484)
         )
         .rotate(0.0F, 0.0F, -12.0F, ax, ay, az);
      Motion motion = Motion.hair().hang(0.25F).follow(Bone.BODY, 0.8F).drag(12.0F).turn(6.0F, 0.0F).spring(40.0F, 6.0F).bend(1.6F).limit(35.0F);
      Part right = rig.extra("tail_r", Bone.HEAD)
         .pivot(ax, ay, az)
         .shape(Shapes.union(Shapes.blend(0.35F, strands).paint(paint), tie))
         .motion(motion)
         .resolution(1.0F)
         .detail(0.9F)
         .flatLight(0.5F)
         .occlusion(0.3F)
         .outline(-15903662, 0.1F);
      rig.extra("tail_l", Bone.HEAD).mirrorOf(right);
   }

   private static void headset(Rig rig) {
      Shape cupR = Shapes.union(
         Shapes.cylinder(-2.45F, 28.0F, 0.05F, -2.85F, 28.0F, 0.05F, 0.62F, 0.12F).color(-13618373),
         Shapes.cylinder(-2.8F, 28.0F, 0.05F, -2.95F, 28.0F, 0.05F, 0.38F, 0.06F).color(-13645638)
      );
      Shape mic = Shapes.union(
         Shapes.tube(-2.7F, 27.7F, 0.45F, 0.08F, -2.45F, 26.8F, 1.3F, 0.08F, -1.7F, 26.25F, 2.05F, 0.08F, -0.95F, 26.15F, 2.25F, 0.08F).color(-13618373),
         Shapes.ellipsoid(-0.85F, 26.15F, 2.28F, 0.22F, 0.16F, 0.16F).color(-14934492)
      );
      rig.extra("headset", Bone.HEAD)
         .shape(up(Shapes.union(cupR, cupR.mirrored(), mic)))
         .resolution(0.4F)
         .detail(0.6F)
         .flatLight(0.4F)
         .outline(-16119026, 0.08F);
   }

   private static Shape capLock(float phi, float from, float to, float r0, float r1) {
      float[] xyz = new float[15];

      for (int i = 0; i < 5; i++) {
         float[] p = onHead(from + (to - from) * i / 4.0F, phi, 0.02F);
         System.arraycopy(p, 0, xyz, i * 3, 3);
      }

      return Shapes.strand(r0, r1, xyz).flattenFrom(2.8F, 0.0F, 28.9F, -0.3F);
   }

   private static float[] onHead(float alpha, float phi, float lift) {
      double a = Math.toRadians(alpha);
      double p = Math.toRadians(phi);
      float ca = (float)Math.cos(a);
      float sa = (float)Math.sin(a);
      float cp = (float)Math.cos(p);
      float sp = (float)Math.sin(p);
      float dx = sp * sa;
      float dy = 0.97F * ca + 0.24F * cp * sa;
      float dz = -0.24F * ca + 0.97F * cp * sa;
      float t = 1.0F / (float)Math.sqrt(dx * dx / 7.3984003F + dy * dy / 7.3984003F + dz * dz / 8.065599F) + lift;
      return new float[]{0.0F + dx * t, 28.9F + dy * t, -0.3F + dz * t};
   }

   private static Paint hairPaint() {
      return Paint.field((x, y, z) -> {
         float dy = y - 31.2F;
         float dz = z + 0.7F;
         float d = (float)Math.sqrt(x * x + dy * dy + dz * dz);
         int c = Colors.mix(-14705514, -12793152, smooth(0.8F, 2.9F, d));
         return Colors.mix(c, -7936291, smooth(5.5F, 17.5F, d) * 0.9F);
      });
   }

   private static void body(Rig rig) {
      Shape shoulders = Shapes.union(
         Shapes.capsule(0.0F, 23.95F, -0.15F, -2.45F, 23.3F, -0.15F, 0.9F), Shapes.capsule(0.0F, 23.95F, -0.15F, 2.45F, 23.3F, -0.15F, 0.9F)
      );
      Shape chest = Shapes.ellipsoid(0.0F, 21.7F, 0.05F, 2.45F, 2.1F, 1.65F);
      Shape bust = Shapes.ellipsoid(0.84F, 21.6F, 0.6F, 0.9F, 0.8F, 0.95F).mirrorX();
      Shape waist = Shapes.ellipsoid(0.0F, 19.6F, 0.0F, 2.0F, 1.7F, 1.45F);
      Shape hips = Shapes.ellipsoid(0.0F, 17.8F, 0.0F, 2.15F, 1.6F, 1.5F);
      Shape torso = Shapes.blend(1.0F, shoulders, Shapes.blend(0.8F, chest, bust), waist, hips).intersect(Shapes.above(17.2F));
      Paint shirt = Paint.field((x, y, z) -> {
         float under = smooth(0.5F, 1.3F, z) * smooth(19.9F, 20.5F, y) * (1.0F - smooth(20.6F, 21.1F, y));
         float side = smooth(1.6F, 2.3F, Math.abs(x)) * 0.35F;
         return Colors.mix(-2499360, -5525063, Math.max(under * 0.55F, side));
      });
      rig.part(Bone.BODY).shape(torso.paint(shirt)).outline(-10788500, 0.1F).detail(0.85F).occlusion(0.2F);
      Shape2D flap = Shape2D.polygon(0.05F, 24.5F, 1.15F, 24.55F, 1.35F, 23.7F, 0.6F, 23.15F, 0.08F, 23.55F);
      Shape collarR = Shapes.extrude(flap, 0.14F, 0.07F).rotate(-25.0F, -20.0F, 0.0F, 0.5F, 23.9F, 0.0F).move(0.0F, 0.0F, frontZ(torso, 0.6F, 23.8F) + 0.02F);
      Shape collar = Shapes.union(collarR, collarR.mirrored()).color(-2499360);
      rig.extra("collar", Bone.BODY).shape(collar).resolution(0.35F).detail(0.8F).flatLight(0.3F).outline(-13645638, 0.1F);
      float[] ys = new float[]{23.35F, 22.4F, 21.3F, 20.2F, 19.3F};
      float[] rs = new float[]{0.26F, 0.32F, 0.38F, 0.42F, 0.44F};
      float[] p = new float[ys.length * 4];

      for (int i = 0; i < ys.length; i++) {
         p[i * 4 + 1] = ys[i];
         p[i * 4 + 2] = frontZ(torso, 0.0F, ys[i]) + 0.1F;
         p[i * 4 + 3] = rs[i];
      }

      Shape2D tip = Shape2D.polygon(-1.0F, 24.0F, 1.0F, 24.0F, 1.0F, 19.7F, 0.0F, 19.1F, -1.0F, 19.7F);
      Shape blade = Shapes.intersect(Shapes.tube(p).flatten(3.5F, 0.0F, 0.0F, 1.0F), Shapes.extrude(tip, 4.0F, 0.0F));
      Shape knot = Shapes.ellipsoid(0.0F, 23.45F, frontZ(torso, 0.0F, 23.45F) + 0.12F, 0.36F, 0.3F, 0.22F);
      rig.extra("tie", Bone.BODY)
         .pivot(0.0F, 23.45F, 1.6F)
         .shape(Shapes.union(blade, knot).paint(Paint.vertical(-14971002, 19.3F, -13645638, 22.5F)))
         .motion(Motion.none().walk(3.0F, 0.0F, 2.0F, 2.0F).drag(4.0F).spring(80.0F, 8.0F))
         .resolution(0.4F)
         .detail(0.5F)
         .flatLight(0.4F)
         .outline(-15835560, 0.08F);
   }

   private static void skirt(Rig rig) {
      Shape skirt = Shapes.field(Miku::skirtDistance, -3.4F, 13.1F, -3.0F, 3.4F, 18.1F, 3.0F).paint(Paint.field(Miku::skirtColor));
      rig.extra("skirt", Bone.BODY)
         .pivot(0.0F, 17.9F, 0.0F)
         .shape(skirt)
         .resolution(0.9F)
         .detail(0.8F)
         .occlusion(0.35F)
         .outline(-16119026, 0.12F)
         .decal(stripe(13.650001F, 0.26F))
         .motion(Motion.none().walk(0.0F, 8.0F, 0.0F, 1.0F).walkPhase(-90.0F).drag(2.0F).spring(80.0F, 10.0F));
   }

   private static float flare(float y) {
      float t = clamp01((17.6F - y) / 4.299999F);
      return t * (2.0F - t) * 0.8F + t * 0.2F;
   }

   private static float pleat(float x, float z, float t) {
      float rx = 2.25F + 0.95F * t;
      float rz = 1.62F + 0.95F * t;
      double f = Math.atan2(x / rx, z / rz) * 18.0 / (Math.PI * 2);
      f -= Math.floor(f);
      return (float)Math.abs(2.0 * f - 1.0);
   }

   private static float skirtDistance(float x, float y, float z) {
      float t = flare(y);
      float rx = 2.25F + 0.95F * t;
      float rz = 1.62F + 0.95F * t;
      float a = x / rx;
      float b = z / rz;
      float k = (float)Math.sqrt(a * a + b * b);
      float side = (k - 1.0F) * Math.min(rx, rz) * 0.72F - 0.2F * t * (pleat(x, z, t) - 0.5F);
      return Math.max(side, Math.max(y - 17.9F, 13.3F - y));
   }

   private static int skirtColor(float x, float y, float z) {
      float t = flare(y);
      return Colors.mix(-14934492, -13618373, 0.35F + 0.65F * (1.0F - t + t * pleat(x, z, t)));
   }

   private static Decal stripe(float y, float width) {
      Canvas c = new Canvas(512, 24);
      c.fill(Shape2D.box(256.0F, 12.0F, 256.0F, 8.0F, 0.0F), -13645638);
      return Decal.wrap(c, 0.0F, y, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, width).radius(1.8F, 3.4F);
   }

   private static void arms(Rig rig) {
      float sx = -3.5F;
      float sy = 23.1F;
      float sz = -0.12F;
      float ex = -3.95F;
      float ey = 19.5F;
      float ez = -0.3F;
      float wx = -4.2F;
      float wy = 16.0F;
      float wz = 0.4F;
      Shape arm = Shapes.blend(
            0.35F,
            Shapes.sphere(sx + 0.1F, sy, sz, 0.66F),
            Shapes.cone(sx, sy, sz, 0.62F, ex, ey, ez, 0.52F),
            Shapes.cone(ex, ey, ez, 0.52F, wx, wy, wz, 0.44F)
         )
         .paint(Paint.field((x, y, z) -> {
            float t = smooth(23.4F, 21.8F, y);
            return Colors.mix(-1198942, -8241, 0.4F + 0.6F * t);
         }));
      Part armR = rig.part(Bone.RIGHT_ARM)
         .pivot(-3.5F, 23.25F, -0.1F)
         .grip(-4.32F, 15.05F, 0.72F)
         .shape(arm)
         .resolution(0.7F)
         .detail(0.8F)
         .flatLight(0.8F)
         .outline(-5740452, 0.1F)
         .swing(0.55F)
         .joint(ex, ey, ez, 0.55F);
      rig.part(Bone.LEFT_ARM).mirrorOf(armR).decal(tattoo(-sx, sy, sz, -ex, ey, ez));
      float fl = (float)Math.sqrt((wx - ex) * (wx - ex) + (wy - ey) * (wy - ey) + (wz - ez) * (wz - ez));
      float dx = (wx - ex) / fl;
      float dy = (wy - ey) / fl;
      float dz = (wz - ez) / fl;
      float ox = ex + 0.35F * (wx - ex);
      float oy = ey + 0.35F * (wy - ey) + 0.9F;
      float oz = ez + 0.35F * (wz - ez);
      float mx = wx + 1.1F * dx;
      float my = wy + 1.1F * dy;
      float mz = wz + 1.1F * dz;
      Shape outer = Shapes.cone(ox, oy + 0.3F, oz, 0.62F, mx, my, mz, 1.15F);
      Shape cavity = Shapes.sphere(mx + 0.25F * dx, my + 0.25F * dy, mz + 0.25F * dz, 0.95F);
      float ay0 = oy + 0.3F;
      float al = (float)Math.sqrt((mx - ox) * (mx - ox) + (my - ay0) * (my - ay0) + (mz - oz) * (mz - oz));
      Shape sleeve = Shapes.subtract(0.1F, outer, cavity).paint(Paint.field((x, y, z) -> {
         float s = ((x - ox) * (mx - ox) + (y - ay0) * (my - ay0) + (z - oz) * (mz - oz)) / (al * al);
         return Colors.mix(-13618373, -14934492, smooth(0.2F, 1.0F, s) * 0.5F);
      }));
      Canvas band = new Canvas(256, 16);
      band.fill(Shape2D.box(128.0F, 8.0F, 128.0F, 6.0F, 0.0F), -13645638);
      float bx = mx - ox;
      float by = my - ay0;
      float bz = mz - oz;
      Decal cuff = Decal.wrap(band, ox + bx * 0.93F, ay0 + by * 0.93F, oz + bz * 0.93F, bx, by, bz, 0.0F, 0.0F, 1.0F, 360.0F, al * 0.1F).radius(0.7F, 1.4F);
      Decal hem = Decal.wrap(band, ox + bx * 0.05F, ay0 + by * 0.05F, oz + bz * 0.05F, bx, by, bz, 0.0F, 0.0F, 1.0F, 360.0F, al * 0.08F).radius(0.4F, 1.0F);
      Part sleeveR = rig.extra("sleeve_r", Bone.RIGHT_ARM)
         .shape(sleeve)
         .resolution(1.1F)
         .detail(0.8F)
         .flatLight(0.3F)
         .occlusion(0.4F)
         .outline(-16119026, 0.1F)
         .decal(cuff)
         .decal(hem);
      rig.extra("sleeve_l", Bone.LEFT_ARM).mirrorOf(sleeveR);
      hands(rig, wx, wy, wz);
   }

   private static Decal tattoo(float sx, float sy, float sz, float ex, float ey, float ez) {
      Canvas c = new Canvas(64, 40);
      c.fill(Shape2D.ellipse(44.0F, 20.0F, 9.0F, 14.0F).outline(4.5F), -2608065);
      c.fill(Shape2D.line(20.0F, 7.0F, 20.0F, 34.0F, 4.5F), -2608065);
      c.fill(Shape2D.line(20.0F, 7.0F, 14.0F, 13.0F, 4.0F), -2608065);
      float t = 0.22F;
      float x = sx + (ex - sx) * t;
      float y = sy + (ey - sy) * t;
      float z = sz + (ez - sz) * t;
      return Decal.left(c, x + 0.55F, y, z + 0.05F, 1.0F, 0.62F).depth(0.6F, 0.6F).maxAngle(70.0F).flatLight(0.8F);
   }

   private static void hands(Rig rig, float wx, float wy, float wz) {
      float hx = 4.3F;
      float hy = 15.25F;
      float hz = 0.6F;
      List<Shape> hand = new ArrayList<>();
      hand.add(Shapes.ellipsoid(hx, hy, hz, 0.33F, 0.6F, 0.52F));
      hand.add(Shapes.capsule(hx - 0.02F, hy + 0.45F, hz - 0.12F, -wx, wy + 0.1F, wz, 0.32F));
      hand.add(Shapes.strand(0.15F, 0.13F, hx + 0.02F, hy - 0.4F, hz + 0.36F, hx - 0.02F, hy - 0.86F, hz + 0.44F, hx - 0.2F, hy - 1.1F, hz + 0.4F));
      hand.add(Shapes.strand(0.15F, 0.13F, hx + 0.03F, hy - 0.44F, hz + 0.12F, hx, hy - 0.98F, hz + 0.14F, hx - 0.22F, hy - 1.26F, hz + 0.11F));
      hand.add(Shapes.strand(0.14F, 0.12F, hx + 0.03F, hy - 0.42F, hz - 0.12F, hx, hy - 0.9F, hz - 0.13F, hx - 0.2F, hy - 1.16F, hz - 0.14F));
      hand.add(Shapes.strand(0.13F, 0.11F, hx + 0.02F, hy - 0.36F, hz - 0.34F, hx, hy - 0.74F, hz - 0.36F, hx - 0.16F, hy - 0.95F, hz - 0.35F));
      hand.add(Shapes.strand(0.17F, 0.14F, hx - 0.18F, hy + 0.1F, hz + 0.42F, hx - 0.3F, hy - 0.3F, hz + 0.58F, hx - 0.32F, hy - 0.54F, hz + 0.5F));
      Part left = rig.extra("hand_l", Bone.LEFT_ARM)
         .shape(Shapes.blend(0.12F, hand.toArray(new Shape[0])).color(-8241))
         .resolution(0.45F)
         .detail(0.35F)
         .flatLight(1.0F)
         .occlusion(0.25F)
         .outline(-5740452, 0.1F);
      rig.extra("hand_r", Bone.RIGHT_ARM).mirrorOf(left);
   }

   private static void legs(Rig rig) {
      float lx = -1.1F;
      Shape hip = Shapes.sphere(lx, 14.6F, -0.05F, 1.02F);
      Shape thigh = Shapes.cone(lx - 0.05F, 14.3F, 0.0F, 1.02F, lx, 8.4F, 0.08F, 0.76F);
      Shape knee = Shapes.sphere(lx, 7.9F, 0.1F, 0.72F);
      Shape calf = Shapes.tube(lx, 7.6F, 0.08F, 0.72F, lx - 0.02F, 5.8F, -0.12F, 0.8F, lx - 0.04F, 3.6F, -0.02F, 0.56F, lx - 0.05F, 2.0F, 0.0F, 0.48F);
      Shape leg = Shapes.blend(0.45F, hip, thigh, knee, calf);
      Shape skin = leg.intersect(Shapes.above(10.6F)).paint(Paint.vertical(-1198942, 10.8F, -8241, 12.5F));
      Shape cuff = Shapes.cylinder(lx, 11.05F, 0.03F, lx, 10.35F, 0.05F, 0.9F, 0.12F).color(-13645638);
      Shape boot = Shapes.blend(0.3F, leg.intersect(Shapes.below(10.4F)).inflate(0.09F), shoe(lx))
         .paint(Paint.field((x, y, z) -> Colors.mix(-13618373, -14934492, smooth(-0.2F, -0.8F, z) * 0.6F)));
      Part legR = rig.part(Bone.RIGHT_LEG)
         .pivot(lx, 14.2F, 0.0F)
         .shape(skin)
         .detail(0.6F)
         .resolution(1.0F)
         .flatLight(0.8F)
         .outline(-5740452, 0.1F)
         .occlusion(0.3F)
         .swing(0.55F, 30.0F)
         .joint(lx, 7.9F, 0.1F, 0.8F);
      rig.part(Bone.LEFT_LEG).mirrorOf(legR);
      Part bootR = rig.extra("boot_r", Bone.RIGHT_LEG).shape(boot).detail(0.8F).resolution(1.0F).outline(-16119026, 0.11F).occlusion(0.3F);
      rig.extra("boot_l", Bone.LEFT_LEG).mirrorOf(bootR);
      Part trimR = rig.extra("boot_trim_r", Bone.RIGHT_LEG)
         .shape(Shapes.union(cuff, sole(lx)))
         .detail(0.4F)
         .resolution(0.6F)
         .flatLight(0.3F)
         .outline(-15835560, 0.09F);
      rig.extra("boot_trim_l", Bone.LEFT_LEG).mirrorOf(trimR);
   }

   private static Shape shoe(float lx) {
      return Shapes.blend(
         0.4F,
         Shapes.ellipsoid(lx, 1.1F, 0.95F, 0.62F, 0.6F, 1.15F),
         Shapes.ellipsoid(lx, 1.4F, -0.35F, 0.62F, 0.9F, 0.72F),
         Shapes.cone(lx, 2.3F, -0.05F, 0.5F, lx, 1.2F, 0.0F, 0.6F)
      );
   }

   private static Shape sole(float lx) {
      Shape plate = Shapes.box(lx, 0.62F, 0.85F, 0.64F, 0.14F, 1.3F, 0.12F);
      Shape heel = Shapes.box(lx, 0.3F, -0.55F, 0.46F, 0.3F, 0.4F, 0.1F);
      return Shapes.blend(0.12F, plate, heel).intersect(Shapes.above(0.0F)).paint(Paint.vertical(-14971002, 0.0F, -13645638, 0.7F));
   }

   private static float px(float x) {
      return 128.0F + x * 56.88889F;
   }

   private static float py(float y) {
      return (29.95F - y) * 56.88889F;
   }

   private static Decal face(float[][] locks) {
      Canvas eye = new Canvas(88, 136);
      float ex = 40.0F;
      float ey = 70.0F;
      Shape2D white = Shape2D.ellipse(ex, ey, 27.5F, 33.0F);
      eye.fill(white, Paint2D.linear(ex, ey - 33.0F, ex, ey + 10.0F, -1904912, -1));
      eye.fill(Shape2D.ellipse(ex, ey - 34.0F, 34.0F, 22.0F).intersect(white), -1715153190, 2.0F);
      Shape2D irisShape = Shape2D.ellipse(ex, ey + 3.5F, 20.5F, 27.5F);
      Shape2D iris = irisShape.intersect(white);
      eye.fill(iris, Paint2D.linear(ex, ey - 24.0F, ex, ey + 30.0F, -16106432, -14504024, -6491416));
      eye.fill(irisShape.outline(2.6F).intersect(white), -1341506496);
      eye.fill(Shape2D.ellipse(ex, ey + 1.5F, 9.0F, 12.5F), -16439768);
      eye.fill(Shape2D.ellipse(ex, ey + 17.0F, 12.0F, 6.5F).intersect(iris), -2133655560, 3.0F);
      eye.fill(Shape2D.curve(ex + 26.5F, ey - 13.0F, ex + 2.0F, ey - 46.0F, ex - 30.0F, ey - 6.0F, 4.2F, 8.4F), -14800336);
      eye.fill(Shape2D.taper(ex - 25.0F, ey - 9.0F, 6.0F, ex - 35.0F, ey - 6.0F, 1.4F), -14800336);
      eye.fill(Shape2D.curve(ex - 5.0F, ey + 32.0F, ex - 20.0F, ey + 31.0F, ex - 26.5F, ey + 16.0F, 1.6F, 2.8F), -868591010);
      eye.fill(Shape2D.curve(ex + 15.0F, ey - 38.0F, ex, ey - 48.0F, ex - 23.0F, ey - 33.0F, 1.5F, 1.5F), 2005965984);
      eye.fill(Shape2D.curve(ex + 20.0F, ey - 48.0F, ex + 2.0F, ey - 57.0F, ex - 22.0F, ey - 51.0F, 4.0F, 2.6F), -13989243);
      eye.fill(Shape2D.ellipse(ex - 4.0F, ey + 46.0F, 21.0F, 9.0F), 1442810524, 10.0F);
      Canvas c = new Canvas(256, 220);
      int oy = Math.round(py(28.0F)) - 70;
      List<Shape2D> shade = new ArrayList<>();

      for (float[] p : locks) {
         float[] xy = blade(p);

         for (int i = 0; i < xy.length; i += 2) {
            xy[i] = px(xy[i]);
            xy[i + 1] = py(xy[i + 1]) + 14.0F;
         }

         shade.add(Shape2D.polygon(xy));
      }

      shade.add(Shape2D.box(128.0F, py(29.75F), 130.0F, 14.222222F, 0.0F));
      c.fill(Shape2D.union(shade.toArray(new Shape2D[0])), -1931434602, 3.0F);
      c.draw(eye, 28, oy);
      c.draw(eye.mirrored(), 140, oy);

      for (int side = -1; side <= 1; side += 2) {
         float cx = 128.0F + side * 60.0F;
         c.fill(Shape2D.ellipse(cx - 7.5F, oy + 60.0F, 7.0F, 8.8F), -1);
         c.fill(Shape2D.circle(cx + 8.0F, oy + 83.0F, 3.0F), -570425345);
         c.fill(Shape2D.circle(cx - 12.0F, oy + 76.0F, 1.6F), -1426063361);
      }

      c.fill(Shape2D.line(130.0F, oy + 119.0F, 127.0F, oy + 124.5F, 2.4F), -1713795968);
      c.fill(Shape2D.path(3.0F, 3.0F, 118.0F, oy + 156.0F, 123.0F, oy + 161.0F, 128.0F, oy + 157.5F, 133.0F, oy + 161.0F, 138.0F, oy + 156.0F), -6668728);
      return Decal.front(c, 0.0F, ty(28.016407F), tz(2.1F), 5.04F, 4.33125F).depth(2.0F, 1.8F).maxAngle(72.0F).flatLight(1.0F).occlusion(0.3F);
   }

   private static float smooth(float e0, float e1, float x) {
      float t = clamp01((x - e0) / (e1 - e0));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }
}
