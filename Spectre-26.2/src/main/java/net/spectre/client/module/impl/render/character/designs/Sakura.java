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
public final class Sakura extends CharacterDesign {
   private static final int SKIN = -9016;
   private static final int SKIN_SHADE = -1331558;
   private static final int SKIN_LINE = -5740452;
   private static final int HAIR_ROOT = -2592100;
   private static final int HAIR = -677434;
   private static final int HAIR_TIP = -12321;
   private static final int HAIR_SHADE = -3908470;
   private static final int HAIR_LINE = -6864801;
   private static final int SHINE = -855643406;
   private static final int WHITE = -263689;
   private static final int WHITE_SHADE = -2305053;
   private static final int SLEEVE_IN = -2502430;
   private static final int CLOTH_LINE = -7568744;
   private static final int RED = -2674372;
   private static final int RED_DARK = -5955793;
   private static final int RED_IN = -10087904;
   private static final int RED_LINE = -8514270;
   private static final int CORD = -1816774;
   private static final int GOLD = -1459895;
   private static final int BEAD = -2608577;
   private static final int STRAW = -1320012;
   private static final int SOLE = -10600144;
   private static final int PETAL = -8984;
   private static final int PETAL_CORE = -2078596;
   private static final float HAKAMA_TOP = 20.0F;
   private static final float HAKAMA_HEM = 2.5F;
   private static final float HS = 1.12F;
   private static final float DROP = 0.4F;
   private static final float HX = 0.0F;
   private static final float HY = 28.9F;
   private static final float HZ = -0.3F;
   private static final float PPU = 56.88889F;
   private static final float FACE_TOP = 29.95F;
   private static final float[] PLEATS = new float[]{0.21F, 0.7F, 2.76F};
   private static final float[][] LOCKS = new float[][]{
      {-0.08F, 28.38F, -2.0F, 0.95F, 0.12F, 0.34F},
      {-0.98F, 28.82F, -25.0F, 0.95F, 0.28F, 0.3F},
      {-1.92F, 28.5F, -47.0F, 0.92F, 0.26F, 0.3F},
      {0.92F, 28.9F, 24.0F, 0.95F, 0.28F, 0.3F},
      {1.9F, 28.62F, 46.0F, 0.92F, 0.25F, 0.3F},
      {-0.55F, 28.58F, -13.0F, 0.45F, 0.16F, 0.44F},
      {0.46F, 28.7F, 11.0F, 0.45F, 0.18F, 0.44F},
      {-1.46F, 28.72F, -36.0F, 0.42F, 0.2F, 0.42F},
      {1.42F, 28.8F, 35.0F, 0.42F, 0.2F, 0.42F}
   };
   private static final Shape2D SLEEVE_SIDE = Shape2D.polygon(
         0.85F,
         23.95F,
         0.62F,
         19.0F,
         0.12F,
         15.7F,
         -0.2F,
         14.7F,
         -1.2F,
         14.25F,
         -2.25F,
         14.0F,
         -2.9F,
         14.2F,
         -3.2F,
         14.8F,
         -3.25F,
         16.0F,
         -2.2F,
         22.4F,
         -1.0F,
         24.2F
      )
      .inflate(0.3F);

   public Sakura() {
      super("sakura", "Sakura");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-11914176, 0.12F).occlusion(0.3F).flatLight(0.55F);
      float[][] locks = lockPaths();
      Paint hair = hairPaint();
      head(rig, locks);
      hair(rig, hair);
      fringe(rig, locks);
      kanzashi(rig);
      body(rig);
      hakama(rig);
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
      Shape neck = Shapes.capsule(0.0F, 23.3F, -0.45F, 0.0F, 25.9F, -0.3F, 0.6F);
      float chinY = ty(25.82F);
      float chinZ = tz(1.2F);
      Paint skin = Paint.field((x, y, z) -> {
         float jaw = chinY + Math.max(0.0F, chinZ - z) * 0.62F;
         float t = smooth(jaw + 0.05F, jaw - 0.2F, y) * (0.6F + 0.4F * smooth(23.4F, 24.8F, y));
         return Colors.mix(-9016, -1331558, t);
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

   private static float frontZ(Shape head, float x, float y) {
      float z = 3.5F;

      while (z > -1.0F && head.distance(x, y, z) > 0.0F) {
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
         int c = Colors.mix(Colors.mix(-2592100, -677434, 0.45F), -677434, smooth(30.9F, 29.7F, y));
         c = Colors.mix(c, -12321, 0.5F * smooth(29.5F, 28.5F, y));
         return Colors.mix(c, -3908470, 0.6F * (1.0F - smooth(0.12F, 0.42F, skin.distance(x, y, z))));
      });
      rig.extra("fringe", Bone.HEAD)
         .shape(up(bangs.paint(paint)))
         .resolution(0.45F)
         .detail(1.05F)
         .flatLight(0.7F)
         .occlusion(0.1F)
         .outline(-6864801, 0.09F)
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
         c.fill(Shape2D.curve(140.0F + a0 * 2.0F, ringY(a0), 140.0F + a0 + am, ringY((a0 + am) * 0.5F), 140.0F + am * 2.0F, ringY(am), 1.0F, w), -855643406);
         c.fill(Shape2D.curve(140.0F + am * 2.0F, ringY(am), 140.0F + am + a1, ringY((am + a1) * 0.5F), 140.0F + a1 * 2.0F, ringY(a1), w, 1.0F), -855643406);
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

      Shape faceCut = Shapes.box(0.0F, 26.3F, 2.3F, 2.95F, 3.3F, 2.8F, 0.9F);
      Shape sideFill = Shapes.blend(
            0.3F,
            Shapes.strand(0.62F, 0.44F, -2.3F, 29.6F, -0.05F, -2.48F, 28.1F, -0.12F, -2.45F, 26.5F, -0.22F).flatten(1.6F, 1.0F, 0.0F, 0.0F),
            Shapes.strand(0.68F, 0.44F, -2.25F, 29.5F, -0.95F, -2.46F, 27.9F, -1.05F, -2.45F, 26.3F, -1.12F).flatten(1.6F, 1.0F, 0.0F, 0.0F)
         )
         .mirrorX();
      Shape capShape = Shapes.blend(
         0.25F, Shapes.subtract(Shapes.blend(0.3F, cap.toArray(new Shape[0])), faceCut).paint(paint), sideFill.paint(paint.occlusion(0.35F))
      );
      rig.extra("hair", Bone.HEAD).shape(up(capShape)).detail(1.1F).flatLight(0.55F).occlusion(0.22F).outline(-6864801, 0.09F).decal(shine());
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
            -2.41F,
            25.9F,
            0.72F,
            0.3F,
            -2.3F,
            25.1F,
            0.74F,
            0.36F,
            -2.12F,
            24.7F,
            0.78F,
            0.32F
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
            -2.41F,
            25.9F,
            0.56F,
            0.3F,
            -2.28F,
            25.0F,
            0.54F,
            0.36F,
            -2.2F,
            24.35F,
            0.56F,
            0.32F
         )
         .flatten(1.8F, 1.0F, 0.0F, 0.15F);
      Shape tie = Shapes.cylinder(-2.41F, 25.38F, 0.64F, -2.41F, 25.94F, 0.64F, 0.5F, 0.12F).color(-263689);
      Shape lock = Shapes.union(Shapes.blend(0.25F, front, back).paint(paint), tie);
      Motion sway = new Motion().idle(1.5F, 0.0F, 0.6F, 0.35F).walk(3.0F, 0.0F, 1.0F, 2.0F).drag(8.0F).hang(0.6F).spring(60.0F, 8.0F).bend(1.4F);
      Part lockR = rig.extra("sidelock_r", Bone.HEAD)
         .pivot(tx(-2.3F), ty(30.0F), tz(0.6F))
         .shape(up(lock))
         .motion(sway)
         .resolution(0.7F)
         .detail(0.6F)
         .flatLight(0.5F)
         .occlusion(0.35F)
         .outline(-6864801, 0.09F)
         .decal(tieCord());
      rig.extra("sidelock_l", Bone.HEAD).mirrorOf(lockR);
      backHair(rig, paint);
   }

   private static Decal tieCord() {
      Canvas c = new Canvas(40, 20);
      c.fill(Shape2D.box(20.0F, 10.0F, 20.0F, 2.8F, 0.0F), -1816774);
      c.fill(Shape2D.ellipse(20.0F, 10.0F, 4.0F, 5.0F), -1816774);
      return Decal.wrap(c, tx(-2.41F), ty(25.67F), tz(0.63F), 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.45F, 220.0F, 0.56F).radius(0.2F, 0.9F);
   }

   private static void backHair(Rig rig, Paint paint) {
      List<Shape> crown = new ArrayList<>();
      List<Shape> fall = new ArrayList<>();

      for (int k = 0; k < 7; k++) {
         float s = (k - 3.0F) / 3.0F;
         float phi = 180.0F - s * 72.0F;
         float lift = k % 2 == 0 ? 0.1F : 0.0F;
         float[] a = onHead(26.0F, phi, -0.02F);
         float[] b = onHead(60.0F, phi, 0.22F + lift);
         float[] c = onHead(88.0F, phi, 0.34F + lift);
         float[] d = onHead(114.0F, phi, 0.2F + lift);
         crown.add(Shapes.strand(0.78F, 0.72F, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]).flattenFrom(2.4F, 0.0F, 28.9F, -0.3F));
         float[] e = onHead(104.0F, phi, 0.32F);
         fall.add(
            Shapes.strand(0.8F, 0.64F, e[0], e[1], e[2], s * 2.85F, 25.0F, -2.52F - 0.3F * (1.0F - s * s), s * 1.02F, 21.9F, -2.8F)
               .flatten(2.0F, 0.0F, 0.0F, 1.0F)
         );
      }

      List<Shape> tail = new ArrayList<>();
      float[] tips = new float[]{14.1F, 13.72F, 13.58F, 13.78F, 14.16F};

      for (int m = 0; m < 5; m++) {
         float s = (m - 2.0F) / 2.0F;
         tail.add(
            Shapes.strand(0.8F, 0.14F, s * 0.72F, 22.3F, -2.85F, s * 1.02F, 18.9F, -3.08F, s * 0.84F, 16.0F, -3.25F, s * 0.2F, tips[m], -3.32F)
               .flatten(1.6F, 0.0F, 0.0F, 1.0F)
         );
      }

      Shape backShape = Shapes.union(
            Shapes.blend(0.1F, crown.toArray(new Shape[0])), Shapes.blend(0.2F, fall.toArray(new Shape[0])), Shapes.blend(0.15F, tail.toArray(new Shape[0]))
         )
         .paint(paint);
      Shape wrap = Shapes.box(0.0F, 21.9F, -2.82F, 1.42F, 0.45F, 0.62F, 0.32F).color(-263689);
      Shape cord = Shapes.box(0.0F, 21.9F, -2.82F, 1.5F, 0.2F, 0.7F, 0.17F).color(-1816774);
      Shape loopR = Shapes.ellipsoid(-1.4F, 22.2F, -3.5F, 1.2F, 0.66F, 0.38F)
         .paint(Paint.radial(-2305053, -0.3F, 22.0F, -3.6F, 1.6F, -263689))
         .rotate(0.0F, 0.0F, -16.0F, -1.4F, 22.2F, -3.5F);
      Shape knot = Shapes.ellipsoid(0.0F, 22.0F, -3.58F, 0.48F, 0.52F, 0.36F).color(-1816774);
      Shape tailR = Shapes.strand(0.44F, 0.38F, -0.3F, 21.6F, -3.5F, -0.8F, 20.5F, -3.45F, -1.2F, 19.4F, -3.4F).flatten(2.2F, 0.0F, 0.0F, 1.0F).color(-263689);
      Shape ribbon = Shapes.union(wrap, cord, loopR, loopR.mirrored(), knot, tailR, tailR.mirrored());
      rig.extra("hair_back", Bone.HEAD)
         .pivot(0.0F, ty(29.0F), tz(-1.6F))
         .shape(up(Shapes.union(backShape, ribbon)))
         .motion(Motion.hair().hang(0.0F).follow(Bone.BODY, 0.85F).drag(12.0F))
         .detail(0.58F)
         .flatLight(0.5F)
         .occlusion(0.35F)
         .outline(-6864801, 0.09F)
         .decal(tailLines());
   }

   private static Decal tailLines() {
      Canvas c = new Canvas(48, 108);
      float k = 12.0F;
      float top = 22.0F;
      int ink = 1890076012;

      for (int m = 0; m < 4; m++) {
         float s = (m - 1.5F) / 2.0F;
         float x0 = s * 0.72F;
         float x1 = s * 1.02F;
         float x2 = s * 0.84F;
         float x3 = s * 0.2F;
         float mx = (x1 + x2) * 0.5F;
         float end = 14.6F + Math.abs(s) * 0.4F;
         c.fill(Shape2D.curve(24.0F - k * x0, (top - 21.4F) * k, 24.0F - k * x1, (top - 18.9F) * k, 24.0F - k * mx, (top - 17.45F) * k, 0.4F, 1.5F), ink);
         c.fill(Shape2D.curve(24.0F - k * mx, (top - 17.45F) * k, 24.0F - k * x2, (top - 16.0F) * k, 24.0F - k * x3, (top - end) * k, 1.5F, 0.3F), ink);
      }

      return Decal.back(c, 0.0F, ty(top - 54.0F / k), tz(-3.4F), 4.48F, 10.08F).depth(0.9F, 1.0F).maxAngle(70.0F);
   }

   private static void kanzashi(Rig rig) {
      Shape pin = Shapes.union(
         blossom(1.6F).rotate(-22.0F, 42.0F, 0.0F).move(2.08F, 30.9F, 1.18F), blossom(0.95F).rotate(-12.0F, 70.0F, 0.0F).move(2.66F, 30.02F, 0.12F)
      );
      rig.extra("kanzashi", Bone.HEAD).shape(up(pin)).resolution(0.42F).detail(0.5F).flatLight(0.4F).outline(-6669732, 0.08F);
      List<Shape> bira = new ArrayList<>();
      float[][] strings = new float[][]{{2.86F, 29.84F, 1.06F, 2.0F}, {3.2F, 29.74F, 0.52F, 3.0F}};

      for (int i = 0; i < strings.length; i++) {
         float x = strings[i][0];
         float y = strings[i][1];
         float z = strings[i][2];
         int beads = (int)strings[i][3];
         float end = y - 0.55F * beads;
         bira.add(Shapes.capsule(x, y + 0.05F, z, x + 0.03F, end, z + 0.02F, 0.07F).color(-1459895));

         for (int b = 0; b < beads; b++) {
            float r = b == beads - 1 ? 0.23F : 0.17F;
            bira.add(Shapes.sphere(x + 0.01F * b, y - 0.55F * (b + 1), z, r).color((b + i) % 2 == 0 ? -2608577 : -1459895));
         }
      }

      rig.extra("bira", Bone.HEAD)
         .pivot(tx(3.08F), ty(29.86F), tz(0.8F))
         .shape(up(Shapes.union(bira.toArray(new Shape[0]))))
         .motion(Motion.hair().hang(1.0F).spring(60.0F, 6.0F))
         .resolution(0.4F)
         .detail(1.5F)
         .flatLight(0.5F)
         .outline(-8767416, 0.05F);
   }

   private static Shape blossom(float s) {
      List<Shape> petals = new ArrayList<>();

      for (int k = 0; k < 5; k++) {
         float a = 90.0F + 72.0F * k;
         double r = Math.toRadians(a);
         float c = (float)Math.cos(r);
         float n = (float)Math.sin(r);
         float px = c * 0.47F * s;
         float py = n * 0.47F * s;
         Shape petal = Shapes.ellipsoid(px, py, 0.0F, 0.3F * s, 0.44F * s, 0.2F).rotate(0.0F, 0.0F, a - 90.0F, px, py, 0.0F);
         petals.add(Shapes.subtract(petal, Shapes.sphere(c * 0.93F * s, n * 0.93F * s, 0.0F, 0.12F * s)));
      }

      Shape petalShape = Shapes.union(petals.toArray(new Shape[0])).paint(Paint.radial(-2078596, 0.0F, 0.0F, 0.1F, 0.4F * s, -8984));
      return Shapes.union(petalShape, Shapes.sphere(0.0F, 0.0F, 0.16F, 0.15F * s).color(-1459895));
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
         int c = Colors.mix(-2592100, -677434, smooth(0.8F, 2.9F, d));
         return Colors.mix(c, -12321, smooth(5.5F, 17.5F, d) * 0.9F);
      });
   }

   private static void body(Rig rig) {
      Shape shoulders = Shapes.union(
         Shapes.capsule(0.0F, 23.95F, -0.15F, -2.55F, 23.3F, -0.15F, 0.95F), Shapes.capsule(0.0F, 23.95F, -0.15F, 2.55F, 23.3F, -0.15F, 0.95F)
      );
      Shape chest = Shapes.ellipsoid(0.0F, 21.7F, 0.05F, 2.55F, 2.1F, 1.7F);
      Shape bust = Shapes.ellipsoid(0.86F, 21.6F, 0.6F, 0.92F, 0.82F, 0.98F).mirrorX();
      Shape waist = Shapes.ellipsoid(0.0F, 19.6F, 0.0F, 2.15F, 1.7F, 1.5F);
      Shape hips = Shapes.ellipsoid(0.0F, 17.8F, 0.0F, 2.2F, 1.6F, 1.55F);
      Shape torso = Shapes.blend(1.0F, shoulders, Shapes.blend(0.8F, chest, bust), waist, hips).intersect(Shapes.above(17.2F));
      Shape collarR = Shapes.strand(0.42F, 0.4F, 0.0F, 24.75F, -1.1F, -0.95F, 24.62F, -0.72F, -1.22F, 24.3F, 0.35F, -0.75F, 23.35F, 1.45F, 0.12F, 22.45F, 1.92F)
         .flattenFrom(2.2F, 0.0F, 22.5F, -1.2F);
      Shape collarL = Shapes.strand(
            0.42F, 0.4F, 0.0F, 24.75F, -1.1F, 0.95F, 24.62F, -0.72F, 1.22F, 24.3F, 0.35F, 0.75F, 23.35F, 1.5F, -0.3F, 22.15F, 2.02F, -1.35F, 20.6F, 1.88F
         )
         .flattenFrom(2.2F, 0.0F, 22.5F, -1.2F);
      Paint kosode = Paint.field((x, y, z) -> {
         float under = smooth(0.5F, 1.3F, z) * smooth(19.9F, 20.5F, y) * (1.0F - smooth(20.6F, 21.1F, y));
         return Colors.mix(-263689, -2305053, under * 0.55F);
      });
      rig.part(Bone.BODY)
         .shape(Shapes.blend(0.14F, torso, collarR, collarL).paint(kosode))
         .outline(-7568744, 0.1F)
         .detail(0.85F)
         .occlusion(0.2F)
         .decal(collar());
      Shape band = Shapes.subtract(
         Shapes.ellipsoid(0.0F, 19.2F, 0.0F, 2.82F, 2.6F, 2.32F).intersect(Shapes.box(0.0F, 19.2F, 0.0F, 3.2F, 0.3F, 2.7F, 0.12F)),
         Shapes.ellipsoid(0.0F, 19.2F, 0.0F, 2.4F, 2.6F, 1.9F)
      );
      Shape knot = Shapes.ellipsoid(0.0F, 19.22F, 2.36F, 0.36F, 0.36F, 0.24F);
      Shape loopR = Shapes.ellipsoid(-1.0F, 19.4F, 2.28F, 1.0F, 0.44F, 0.2F).rotate(0.0F, -8.0F, -12.0F, -1.0F, 19.4F, 2.28F);
      Shape tailR = Shapes.strand(0.27F, 0.22F, -0.12F, 19.0F, 2.38F, -0.42F, 18.1F, 2.44F, -0.64F, 17.2F, 2.48F).flatten(2.2F, 0.0F, 0.0F, 1.0F);
      Shape bow = Shapes.union(knot, loopR, loopR.mirrored(), tailR, tailR.mirrored());
      rig.extra("himo", Bone.BODY).shape(Shapes.union(band, bow).color(-263689)).resolution(0.6F).detail(0.36F).outline(-7568744, 0.09F);
   }

   private static Decal collar() {
      Canvas c = new Canvas(92, 78);
      float[] top = uv(0.0F, 25.1F);
      float[] low = uv(0.0F, 23.2F);
      c.fill(
         Shape2D.polygon(uv(-0.95F, 25.1F, -0.84F, 24.49F, -0.41F, 23.59F, 0.02F, 23.16F, 0.4F, 23.59F, 0.84F, 24.49F, 0.95F, 25.1F)),
         Paint2D.linear(0.0F, top[1], 0.0F, low[1], -9016, -9016, -1331558)
      );
      segments(c, 6.6F, -2674372, -0.9F, 25.1F, -0.84F, 24.49F, -0.41F, 23.59F, 0.05F, 23.12F);
      segments(c, 6.6F, -2674372, 0.9F, 25.1F, 0.84F, 24.49F, 0.4F, 23.59F, -0.02F, 23.1F);
      float[] tip = uv(0.02F, 23.12F);
      c.fill(Shape2D.circle(tip[0], tip[1], 4.2F), -2674372);
      int line = 1484418696;
      segments(c, 1.8F, line, -1.65F, 24.25F, -1.6F, 24.11F, -1.13F, 23.16F, -0.6F, 22.6F);
      segments(c, 1.8F, line, 1.65F, 24.25F, 1.6F, 24.11F, 1.09F, 23.12F, 0.55F, 22.45F);
      return Decal.front(c, 0.0F, 23.8F, 2.0F, 3.8333333F, 3.25F).depth(1.6F, 1.2F).maxAngle(86.0F).flatLight(0.9F).occlusion(0.4F);
   }

   private static float[] uv(float... xy) {
      float[] p = new float[xy.length];

      for (int i = 0; i < xy.length; i += 2) {
         p[i] = (xy[i] + 1.9166666F) * 24.0F;
         p[i + 1] = (25.425F - xy[i + 1]) * 24.0F;
      }

      return p;
   }

   private static void segments(Canvas c, float width, int argb, float... xy) {
      float[] p = uv(xy);

      for (int i = 0; i + 3 < p.length; i += 2) {
         c.fill(Shape2D.line(p[i], p[i + 1], p[i + 2], p[i + 3], width), argb);
      }
   }

   private static void hakama(Rig rig) {
      Shape slit = Shapes.extrude(Shape2D.polygon(-0.3F, 20.4F, 0.3F, 20.4F, 0.0F, 16.3F), 0.7F, 0.04F)
         .rotate(0.0F, 90.0F, 0.0F)
         .move(-2.95F, 0.0F, 0.0F)
         .color(-263689);
      Shape half = Shapes.subtract(Shapes.field(Sakura::hakamaField, -5.1F, 2.25F, -3.9F, 2.1F, 20.25F, 3.9F).paint(Paint.field(Sakura::hakamaColor)), slit);
      Motion motion = new Motion().follow(Bone.RIGHT_LEG, 0.62F, 13.0F).drag(3.0F).hang(0.5F).spring(80.0F, 11.0F).bend(1.6F).limit(24.0F);
      Part right = rig.extra("hakama_r", Bone.BODY)
         .pivot(-1.0F, 19.6F, 0.0F)
         .shape(half)
         .motion(motion)
         .resolution(1.1F)
         .detail(0.42F)
         .flatLight(0.35F)
         .occlusion(0.2F)
         .outline(-8514270, 0.12F);
      right.decal(pleatLine(PLEATS[0])).decal(pleatLine(PLEATS[1]));
      rig.extra("hakama_l", Bone.BODY).mirrorOf(right);
   }

   private static Decal pleatLine(float ang) {
      Canvas c = new Canvas(10, 96);
      c.fill(Shape2D.taper(5.0F, 1.0F, 1.2F, 5.0F, 95.0F, 6.0F), Paint2D.linear(0.0F, 0.0F, 0.0F, 40.0F, 8262946, -1719790302));
      float s = (float)Math.sin(ang);
      float k = (float)Math.cos(ang);
      return Decal.wrap(c, -1.14F, 8.0F, 0.0F, 1.15F, 17.5F, 0.0F, -s, 0.0F, k, 3.6F, 11.0F);
   }

   private static float hakamaField(float x, float y, float z) {
      float u = clamp01((20.0F - y) / 17.5F);
      float f = (float)Math.pow(u, 0.8);
      float cx = -(0.35F + 1.15F * u);
      float rx = 2.05F + 1.15F * f;
      float rz = 1.9F + 1.5F * f;
      float px = x - cx;
      float d = ellipse(px, z, rx, rz);
      float ang = (float)Math.atan2(-px, z);
      float depth = 0.05F + 0.17F * u;

      for (float a : PLEATS) {
         d -= depth * pleat(ang - a);
      }

      float cap = Math.max(y - 20.0F, 2.5F - y);
      return smax(d, cap, 0.2F) * 0.9F;
   }

   private static int hakamaColor(float x, float y, float z) {
      float u = clamp01((20.0F - y) / 17.5F);
      float f = (float)Math.pow(u, 0.8);
      float px = x + 0.35F + 1.15F * u;
      if (y < 2.8F && ellipse(px, z, 2.05F + 1.15F * f, 1.9F + 1.5F * f) < -0.2F) {
         return -10087904;
      } else {
         float ang = (float)Math.atan2(-px, z);
         float fold = 0.0F;

         for (float a : PLEATS) {
            float d = ang - a;
            if (d >= 0.0F && d < 0.16F) {
               fold = Math.max(fold, 1.0F - smooth(0.02F, 0.16F, d));
            }
         }

         int base = Colors.mix(-2674372, -5955793, smooth(9.0F, 2.5F, y) * 0.45F);
         return Colors.mix(base, -5955793, fold * 0.35F * smooth(0.02F, 0.2F, u));
      }
   }

   private static float pleat(float d) {
      if (!(d < -0.4F) && !(d > 0.07F)) {
         return d < 0.0F ? smooth(-0.4F, 0.0F, d) : 1.0F - smooth(0.0F, 0.07F, d);
      } else {
         return 0.0F;
      }
   }

   private static float ellipse(float px, float pz, float rx, float rz) {
      float ax = px / rx;
      float az = pz / rz;
      float k0 = (float)Math.sqrt(ax * ax + az * az);
      float bx = px / (rx * rx);
      float bz = pz / (rz * rz);
      float k1 = (float)Math.sqrt(bx * bx + bz * bz);
      return k1 < 1.0E-6F ? -Math.min(rx, rz) : k0 * (k0 - 1.0F) / k1;
   }

   private static void arms(Rig rig) {
      float sx = -3.62F;
      float sy = 23.0F;
      float sz = -0.12F;
      float ex = -4.05F;
      float ey = 19.45F;
      float ez = -0.3F;
      float wx = -4.28F;
      float wy = 15.95F;
      float wz = 0.45F;
      float fl = (float)Math.sqrt((wx - ex) * (wx - ex) + (wy - ey) * (wy - ey) + (wz - ez) * (wz - ez));
      float dx = (wx - ex) / fl;
      float dy = (wy - ey) / fl;
      float dz = (wz - ez) / fl;
      Shape tube = Shapes.blend(0.35F, Shapes.cone(sx, sy, sz, 0.66F, ex, ey, ez, 0.58F), Shapes.cone(ex, ey, ez, 0.58F, wx, wy, wz, 0.66F));
      Shape cavity = Shapes.sphere(wx + 0.3F * dx, wy + 0.3F * dy, wz + 0.3F * dz, 0.46F).color(-2502430);
      Part armR = rig.part(Bone.RIGHT_ARM)
         .pivot(-3.5F, 23.25F, -0.1F)
         .grip(-4.32F, 15.05F, 0.72F)
         .shape(Shapes.subtract(0.08F, tube.color(-263689), cavity))
         .resolution(0.7F)
         .detail(0.7F)
         .outline(-7568744, 0.1F)
         .swing(0.55F)
         .joint(ex, ey, ez, 0.6F);
      rig.part(Bone.LEFT_ARM).mirrorOf(armR);
      Shape bag = Shapes.field(
            (x, y, z) -> {
               float sd = SLEEVE_SIDE.distance(z, y);
               float xc = -4.12F - 0.085F * (23.8F - y);
               float h = 0.3F
                  + 0.16F * clamp01((22.0F - y) / 8.0F)
                  + 0.26F * smooth(0.0F, 1.3F, -sd)
                  + 0.07F * (float)Math.sin(z * 2.3F + 0.6F) * smooth(22.0F, 17.0F, y);
               return smax(sd, Math.abs(x - xc) - h, 0.28F);
            },
            -6.1F,
            13.2F,
            -3.9F,
            -3.3F,
            24.7F,
            1.6F
         )
         .paint(Paint.vertical(-1053966, 13.5F, -263689, 19.0F));
      Shape tassel = Shapes.union(
         Shapes.sphere(-5.0F, 14.14F, -3.42F, 0.23F).color(-1816774),
         Shapes.cone(-5.0F, 13.98F, -3.44F, 0.15F, -5.01F, 13.58F, -3.46F, 0.27F).paint(Paint.vertical(-4705748, 13.3F, -1816774, 13.9F))
      );
      Motion hang = Motion.cloth().hang(0.7F).drag(8.0F).spring(40.0F, 5.0F).bend(1.3F).limit(35.0F);
      Part sleeveR = rig.extra("sleeve_r", Bone.RIGHT_ARM)
         .shape(Shapes.union(bag, tassel))
         .motion(hang)
         .resolution(1.0F)
         .detail(0.7F)
         .outline(-7568744, 0.1F)
         .decal(sleeveTrim());
      rig.extra("sleeve_l", Bone.LEFT_ARM).mirrorOf(sleeveR);
      hands(rig, wx, wy, wz);
   }

   private static void hands(Rig rig, float wx, float wy, float wz) {
      float hx = 4.3F;
      float hy = 15.25F;
      float hz = 0.64F;
      List<Shape> hand = new ArrayList<>();
      hand.add(Shapes.ellipsoid(hx, hy, hz, 0.33F, 0.6F, 0.52F));
      hand.add(Shapes.capsule(hx - 0.02F, hy + 0.45F, hz - 0.12F, -wx, wy + 0.1F, wz, 0.32F));
      hand.add(Shapes.strand(0.15F, 0.13F, hx + 0.02F, hy - 0.4F, hz + 0.36F, hx - 0.02F, hy - 0.86F, hz + 0.44F, hx - 0.2F, hy - 1.1F, hz + 0.4F));
      hand.add(Shapes.strand(0.15F, 0.13F, hx + 0.03F, hy - 0.44F, hz + 0.12F, hx, hy - 0.98F, hz + 0.14F, hx - 0.22F, hy - 1.26F, hz + 0.11F));
      hand.add(Shapes.strand(0.14F, 0.12F, hx + 0.03F, hy - 0.42F, hz - 0.12F, hx, hy - 0.9F, hz - 0.13F, hx - 0.2F, hy - 1.16F, hz - 0.14F));
      hand.add(Shapes.strand(0.13F, 0.11F, hx + 0.02F, hy - 0.36F, hz - 0.34F, hx, hy - 0.74F, hz - 0.36F, hx - 0.16F, hy - 0.95F, hz - 0.35F));
      hand.add(Shapes.strand(0.17F, 0.14F, hx - 0.18F, hy + 0.1F, hz + 0.42F, hx - 0.3F, hy - 0.3F, hz + 0.58F, hx - 0.32F, hy - 0.54F, hz + 0.5F));
      Part left = rig.extra("hand_l", Bone.LEFT_ARM)
         .shape(Shapes.blend(0.12F, hand.toArray(new Shape[0])).color(-9016))
         .resolution(0.45F)
         .detail(0.35F)
         .flatLight(1.0F)
         .occlusion(0.25F)
         .outline(-5740452, 0.1F);
      rig.extra("hand_r", Bone.RIGHT_ARM).mirrorOf(left);
   }

   private static Decal sleeveTrim() {
      Canvas c = new Canvas(64, 24);
      float k = 16.0F;
      float[] trim = new float[]{-0.3F, 14.75F, -1.2F, 14.3F, -2.25F, 14.05F, -2.85F, 14.25F, -3.12F, 14.8F};

      for (int i = 0; i + 3 < trim.length; i += 2) {
         c.fill(Shape2D.line((trim[i] + 3.75F) * k, (15.3F - trim[i + 1]) * k, (trim[i + 2] + 3.75F) * k, (15.3F - trim[i + 3]) * k, 3.0F), -1816774);
      }

      return Decal.right(c, -6.1F, 15.3F - 12.0F / k, -3.75F + 32.0F / k, 64.0F / k, 24.0F / k).depth(1.3F, 1.2F).maxAngle(48.0F);
   }

   private static void legs(Rig rig) {
      float lx = -1.3F;
      Shape tabi = Shapes.blend(
         0.35F,
         Shapes.sphere(lx, 1.62F, -0.12F, 0.52F),
         Shapes.ellipsoid(lx, 0.98F, -0.45F, 0.56F, 0.54F, 0.62F),
         Shapes.ellipsoid(lx, 0.84F, 0.55F, 0.6F, 0.43F, 1.22F),
         Shapes.ellipsoid(lx + 0.33F, 0.76F, 1.57F, 0.25F, 0.31F, 0.43F),
         Shapes.ellipsoid(lx - 0.18F, 0.73F, 1.47F, 0.37F, 0.29F, 0.46F)
      );
      Shape split = Shapes.box(lx + 0.1F, 0.8F, 1.97F, 0.05F, 0.4F, 0.55F, 0.02F).color(-2305053);
      Shape shin = Shapes.cone(lx, 1.7F, -0.1F, 0.5F, lx + 0.02F, 3.3F, -0.08F, 0.54F);
      Shape sock = Shapes.subtract(Shapes.blend(0.3F, tabi, shin).color(-263689), split);
      Shape sole = Shapes.box(lx, 0.22F, 0.52F, 0.74F, 0.22F, 1.64F, 0.2F).paint(Paint.field((x, y, z) -> y > 0.36F ? -1320012 : -10600144));
      Shape straps = Shapes.union(
            Shapes.strand(0.2F, 0.2F, lx + 0.13F, 0.5F, 1.5F, lx + 0.13F, 0.95F, 1.3F),
            Shapes.strand(0.2F, 0.2F, lx + 0.13F, 0.95F, 1.3F, lx - 0.35F, 1.08F, 0.85F, lx - 0.62F, 0.55F, 0.35F),
            Shapes.strand(0.2F, 0.2F, lx + 0.13F, 0.95F, 1.3F, lx + 0.35F, 1.08F, 0.85F, lx + 0.6F, 0.55F, 0.35F)
         )
         .color(-1816774);
      Shape foot = Shapes.union(sock, sole, straps).rotate(0.0F, -7.0F, 0.0F, lx, 0.0F, -0.1F);
      Part legR = rig.part(Bone.RIGHT_LEG)
         .pivot(lx, 14.2F, 0.0F)
         .shape(foot)
         .resolution(0.6F)
         .detail(0.5F)
         .flatLight(0.4F)
         .outline(-10860992, 0.1F)
         .swing(0.38F, 18.0F);
      rig.part(Bone.LEFT_LEG).mirrorOf(legR);
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
      eye.fill(white, Paint2D.linear(ex, ey - 33.0F, ex, ey + 10.0F, -923920, -1));
      eye.fill(Shape2D.ellipse(ex, ey - 34.0F, 34.0F, 22.0F).intersect(white), -1713846054, 2.0F);
      Shape2D irisShape = Shape2D.ellipse(ex, ey + 3.5F, 20.5F, 27.5F);
      Shape2D iris = irisShape.intersect(white);
      eye.fill(iris, Paint2D.linear(ex, ey - 24.0F, ex, ey + 30.0F, -11923926, -2996102, -19508));
      eye.fill(irisShape.outline(2.6F).intersect(white), -1337323990);
      eye.fill(Shape2D.ellipse(ex, ey + 1.5F, 9.0F, 12.5F), -14023146);
      eye.fill(Shape2D.ellipse(ex, ey + 17.0F, 12.0F, 6.5F).intersect(iris), -2130716956, 3.0F);
      eye.fill(Shape2D.curve(ex + 26.5F, ey - 13.0F, ex + 2.0F, ey - 46.0F, ex - 30.0F, ey - 6.0F, 4.2F, 8.4F), -13757408);
      eye.fill(Shape2D.taper(ex - 25.0F, ey - 9.0F, 6.0F, ex - 35.0F, ey - 6.0F, 1.4F), -13757408);
      eye.fill(Shape2D.curve(ex - 5.0F, ey + 32.0F, ex - 20.0F, ey + 31.0F, ex - 26.5F, ey + 16.0F, 1.6F, 2.8F), -864407480);
      eye.fill(Shape2D.curve(ex + 15.0F, ey - 38.0F, ex, ey - 48.0F, ex - 23.0F, ey - 33.0F, 1.5F, 1.5F), 2009102984);
      eye.fill(Shape2D.curve(ex + 20.0F, ey - 48.0F, ex + 2.0F, ey - 57.0F, ex - 22.0F, ey - 51.0F, 4.0F, 2.6F), -4892806);
      eye.fill(Shape2D.ellipse(ex - 4.0F, ey + 46.0F, 21.0F, 9.0F), 1728023196, 10.0F);
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
      c.fill(Shape2D.union(shade.toArray(new Shape2D[0])), -1930845035, 3.0F);
      c.draw(eye, 28, oy);
      c.draw(eye.mirrored(), 140, oy);

      for (int side = -1; side <= 1; side += 2) {
         float cx = 128.0F + side * 60.0F;
         c.fill(Shape2D.ellipse(cx - 7.5F, oy + 60.0F, 7.0F, 8.8F), -1);
         c.fill(Shape2D.circle(cx + 8.0F, oy + 83.0F, 3.0F), -570425345);
         c.fill(Shape2D.circle(cx - 12.0F, oy + 76.0F, 1.6F), -1426063361);
      }

      c.fill(Shape2D.line(130.0F, oy + 119.0F, 127.0F, oy + 124.5F, 2.4F), -1713795968);
      c.fill(Shape2D.curve(119.0F, oy + 157.0F, 128.0F, oy + 163.0F, 137.0F, oy + 157.0F, 2.6F, 2.6F), -4896678);
      return Decal.front(c, 0.0F, ty(28.016407F), tz(2.1F), 5.04F, 4.33125F).depth(2.0F, 1.8F).maxAngle(72.0F).flatLight(1.0F).occlusion(0.3F);
   }

   private static float smax(float a, float b, float k) {
      float h = clamp01(0.5F - 0.5F * (b - a) / k);
      return b + (a - b) * h + k * h * (1.0F - h);
   }

   private static float smooth(float e0, float e1, float x) {
      float t = clamp01((x - e0) / (e1 - e0));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }
}
