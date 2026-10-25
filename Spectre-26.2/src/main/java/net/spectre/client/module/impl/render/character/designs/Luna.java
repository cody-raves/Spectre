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
import net.spectre.client.module.impl.render.character.kit.Vec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Luna extends CharacterDesign {
   private static final int SKIN = -6958;
   private static final int SKIN_SHADE = -1331558;
   private static final int SKIN_LINE = -5737368;
   private static final int HAIR = -1912065;
   private static final int HAIR_MID = -4940554;
   private static final int HAIR_TIP = -7706904;
   private static final int HAIR_ROOT = -6519080;
   private static final int HAIR_LIGHT = -1647873;
   private static final int HAIR_LINE = -10794346;
   private static final int NAVY = -14603682;
   private static final int NAVY_DARK = -15460295;
   private static final int NAVY_LINE = -15921370;
   private static final int BODICE = -13945223;
   private static final int TIER = -12693598;
   private static final int LINING = -7571752;
   private static final int RIBBON = -4544530;
   private static final int PETTICOAT = -4611086;
   private static final int LACE = -1036;
   private static final int GOLD = -1326758;
   private static final int GOLD_DARK = -5011921;
   private static final int GOLD_PALE = -2868;
   private static final int GOLD_LINE = -8760296;
   private static final int TIGHTS = -12959880;
   private static final int TIGHTS_SHEEN = -10656082;
   private static final int BOOT = -7710146;
   private static final int BOOT_DARK = -9550799;
   private static final int BOOT_CUFF = -6395064;
   private static final int LEATHER = -8762574;
   private static final int LEATHER_DARK = -11653344;
   private static final int MOON = -4688;
   private static final float HEAD_K = 1.05F;
   private static final float NECK_Y = 24.7F;
   private static final float NECK_Z = -0.1F;
   private static final float AXIS_Z = -0.3F;
   private static final float HAT_TILT_X = -8.0F;
   private static final float HAT_TILT_Z = 4.0F;
   private static final Vec3 HAT_CENTRE = new Vec3(0.0F, 29.15F, -0.25F);
   private static final Shape2D BRIM = Shape2D.taper(2.9F, 29.25F, 0.82F, 5.72F, 29.0F, 0.5F);
   private static final float BRIM_WAVE = 0.3F;
   private static final float RIM_R = 5.86F;
   private static final Vec3 ELBOW = new Vec3(-3.72F, 18.4F, -0.05F);
   private static final float ELBOW_PITCH = -12.0F;
   private static final float ELBOW_ROLL = 3.0F;
   private static final float FACE_PPU = 41.48148F;
   private static final float FACE_LEFT = -2.3142858F;
   private static final float FACE_TOP = 28.810715F;
   private static final float[] CAPE_Y = new float[]{7.0F, 10.0F, 13.5F, 17.0F, 20.5F, 23.4F};
   private static final float[] CAPE_RX = new float[]{5.15F, 4.85F, 4.15F, 3.45F, 3.2F, 3.15F};
   private static final float[] CAPE_RZ = new float[]{4.45F, 4.2F, 3.5F, 2.3F, 1.62F, 1.3F};
   private static final float[] CAPE_CZ = new float[]{-0.8F, -0.85F, -0.85F, -0.6F, -0.45F, -0.35F};
   private static final float[] CAPE_WRAP = new float[]{104.0F, 102.0F, 100.0F, 105.0F, 118.0F, 132.0F};
   private static final int CAPE_STEPS = 96;
   private static final float[] CAPE_LUT = capeTable();
   private static final float[] CURTAIN_Y = new float[]{17.0F, 20.0F, 22.5F, 24.5F, 27.6F, 29.3F};
   private static final float[] CURTAIN_RX = new float[]{3.2F, 3.05F, 2.95F, 2.95F, 3.12F, 2.35F};
   private static final float[] CURTAIN_RZ = new float[]{3.0F, 2.65F, 2.25F, 2.55F, 3.05F, 2.35F};
   private static final float[] CURTAIN_CZ = new float[]{-0.95F, -0.95F, -0.9F, -0.65F, -0.3F, -0.3F};

   public Luna() {
      super("luna", "Luna");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-15396047, 0.15F).occlusion(0.35F);
      Canvas band = new Canvas(128, 12);
      paintBand(band);
      Shape face = faceShape();
      Shape cap = Shapes.ellipsoid(0.0F, 27.55F, -0.35F, 2.82F, 2.94F, 2.88F).intersect(Shapes.plane(0.0F, 24.6F, -2.7F, 0.0F, -1.0F, 0.86F));
      List<Luna.Lock> bangs = bangs(face);
      head(rig, face, bangs);
      hair(rig, cap, bangs);
      hat(rig);
      body(rig);
      cape(rig);
      skirt(rig, band);
      arms(rig, band);
      legs(rig);
   }

   private static Shape faceShape() {
      Shape skull = Shapes.ellipsoid(0.0F, 27.5F, -0.3F, 2.38F, 2.5F, 2.42F);
      Shape cheeks = Shapes.ellipsoid(0.0F, 26.3F, 0.3F, 1.84F, 1.32F, 1.84F);
      Shape jaw = Shapes.cone(0.0F, 25.75F, 0.4F, 1.28F, 0.0F, 24.74F, 1.02F, 0.34F);
      return Shapes.blend(1.0F, skull, cheeks, jaw);
   }

   private static void head(Rig rig, Shape face, List<Luna.Lock> bangs) {
      rig.part(Bone.HEAD)
         .pivot(0.0F, 24.7F, -0.1F)
         .shape(hs(face.paint(Paint.of(-6958))))
         .resolution(0.9F)
         .detail(1.5F)
         .flatLight(1.0F)
         .occlusion(0.2F)
         .outline(-5737368, 0.08F)
         .decal(faceDecal(bangs));
   }

   private static Shape hs(Shape s) {
      return s.scale(1.05F, 0.0F, 24.7F, -0.1F);
   }

   private static Vec3 hp(float x, float y, float z) {
      return new Vec3(x * 1.05F, 24.7F + (y - 24.7F) * 1.05F, -0.1F + (z - -0.1F) * 1.05F);
   }

   private static Part at(Part part, Vec3 p) {
      return part.pivot(p.x(), p.y(), p.z());
   }

   private static List<Luna.Lock> bangs(Shape face) {
      float[][] spec = new float[][]{
         {-50.0F, 26.5F, 0.75F, 0.3F, 0.0F},
         {-24.0F, 27.6F, 0.72F, 0.25F, 0.0F},
         {4.0F, 26.9F, 0.8F, -0.2F, 0.0F},
         {27.0F, 27.8F, 0.7F, -0.25F, 0.0F},
         {52.0F, 26.62F, 0.75F, -0.3F, 0.0F},
         {-37.0F, 27.2F, 0.36F, 0.2F, 1.0F},
         {40.0F, 27.02F, 0.36F, -0.2F, 1.0F}
      };
      List<Luna.Lock> locks = new ArrayList<>();

      for (float[] s : spec) {
         float[] ys = new float[10];
         float[] rs = new float[10];

         for (int i = 0; i < 10; i++) {
            ys[i] = s[1] - 0.3F + (29.6F - s[1] + 0.3F) * i / 9.0F;
            rs[i] = surfaceR(face, s[0], ys[i]);
         }

         locks.add(
            s[4] > 0.0F
               ? new Luna.Lock(s[0], 29.4F, s[1], s[2], s[3], 0.7F, 0.75F, 0.1F, 0.3F, 0.2F, ys, rs)
               : new Luna.Lock(s[0], 29.4F, s[1], s[2], s[3], 0.5F, 0.45F, 0.05F, 0.4F, 0.22F, ys, rs)
         );
      }

      return locks;
   }

   private static float interp(float[] xs, float[] vs, float x) {
      int n = xs.length;
      if (x <= xs[0]) {
         return vs[0];
      } else if (x >= xs[n - 1]) {
         return vs[n - 1];
      } else {
         int i = 0;

         while (x > xs[i + 1]) {
            i++;
         }

         return vs[i] + (vs[i + 1] - vs[i]) * (x - xs[i]) / (xs[i + 1] - xs[i]);
      }
   }

   private static float surfaceR(Shape s, float deg, float y) {
      double t = Math.toRadians(deg);
      float dx = (float)Math.sin(t);
      float dz = (float)Math.cos(t);
      float r = 4.5F;

      for (int i = 0; i < 80; i++) {
         float d = s.distance(dx * r, y, -0.3F + dz * r);
         if (Math.abs(d) < 0.001F) {
            break;
         }

         r -= d;
      }

      return r;
   }

   private static void hair(Rig rig, Shape cap, List<Luna.Lock> bangs) {
      List<Shape> side = new ArrayList<>();
      side.add(cap);

      for (int s = -1; s <= 1; s += 2) {
         side.add(
            Shapes.strand(0.74F, 0.24F, s * 2.35F, 29.2F, -0.9F, s * 3.04F, 27.6F, -0.72F, s * 3.02F, 26.0F, -0.5F, s * 2.78F, 24.5F, -0.35F)
               .flattenFrom(1.5F, 0.0F, 26.8F, -0.6F)
         );
      }

      Paint sidePaint = Paint.field((x, y, z) -> shine(Colors.mix(-4940554, -1912065, clamp01((y - 24.6F) / 3.6F)), x, y, z, 28.1F, 0.26F, 7, 0.3F));
      rig.extra("hair", Bone.HEAD)
         .shape(hs(Shapes.union(side.toArray(new Shape[0])).paint(sidePaint)))
         .resolution(1.3F)
         .detail(0.6F)
         .flatLight(0.4F)
         .noOutline();
      List<Shape> fringe = new ArrayList<>();

      for (Luna.Lock l : bangs) {
         fringe.add(l.shape());
      }

      Paint fringePaint = Paint.field((x, y, z) -> shine(Colors.mix(-1647873, -6519080, clamp01((y - 27.1F) / 1.9F)), x, y, z, 28.45F, 0.14F, 11, 0.9F));
      rig.extra("bangs", Bone.HEAD)
         .shape(hs(Shapes.union(fringe.toArray(new Shape[0])).paint(fringePaint)))
         .resolution(0.45F)
         .detail(1.3F)
         .flatLight(0.7F)
         .occlusion(0.15F)
         .outline(-10794346, 0.08F);
      Luna.Lock frame = new Luna.Lock(
         -72.0F,
         29.4F,
         24.3F,
         0.62F,
         0.9F,
         0.0F,
         0.0F,
         0.0F,
         0.45F,
         0.3F,
         new float[]{24.0F, 25.0F, 26.2F, 27.6F, 28.8F, 29.6F},
         new float[]{2.26F, 2.54F, 2.72F, 2.7F, 2.42F, 2.0F}
      );
      Paint framePaint = Paint.field(
         (x, y, z) -> y > 27.2F ? Colors.mix(-1647873, -6519080, clamp01((y - 27.2F) / 1.8F)) : Colors.mix(-4940554, -1647873, clamp01((y - 24.3F) / 2.9F))
      );
      rig.extra("locks", Bone.HEAD)
         .shape(hs(frame.shape().mirrorX().paint(framePaint)))
         .resolution(0.45F)
         .detail(0.7F)
         .flatLight(0.5F)
         .occlusion(0.3F)
         .outline(-10794346, 0.08F);
      List<Shape> under = new ArrayList<>();
      List<Shape> top = new ArrayList<>();

      for (int i = 0; i < 5; i++) {
         under.add(backLock(118.0F + 31.0F * i, -0.22F, 18.2F + i % 2 * 0.35F, 1.25F, 0.5F, 2.6F, i * 1.7F));
      }

      for (int i = 0; i < 7; i++) {
         float f = i / 6.0F;
         float tipY = 16.9F + 1.4F * (float)Math.pow(Math.abs(f - 0.5F) * 2.0F, 1.5) + i % 3 * 0.3F;
         top.add(backLock(116.0F + 128.0F * f, i % 2 == 0 ? 0.14F : 0.06F, tipY, 0.66F, 0.12F, 1.7F, i * 2.3F));
      }

      Shape underHair = Shapes.union(under.toArray(new Shape[0])).paint(Paint.vertical(-9152312, 18.0F, -5993238, 26.0F));
      Shape topHair = Shapes.union(top.toArray(new Shape[0])).paint(Paint.field((x, y, z) -> shine(backHair(y), x, y, z, 27.25F, 0.3F, 6, 1.1F)));
      at(rig.extra("hair_back", Bone.HEAD), hp(0.0F, 28.2F, -1.6F))
         .shape(hs(Shapes.union(underHair, topHair)))
         .motion(Motion.hair().hang(0.8F).drag(18.0F))
         .resolution(1.15F)
         .detail(0.85F)
         .flatLight(0.35F)
         .outline(-10794346, 0.1F);
      Shape ribbon = Shapes.tube(
            -2.3F,
            29.0F,
            -0.2F,
            0.52F,
            -2.8F,
            27.4F,
            -0.05F,
            0.58F,
            -2.95F,
            25.6F,
            0.25F,
            0.56F,
            -2.8F,
            23.8F,
            1.0F,
            0.5F,
            -2.66F,
            22.2F,
            1.7F,
            0.46F,
            -2.62F,
            20.8F,
            2.02F,
            0.42F
         )
         .flattenFrom(1.45F, 0.0F, 22.0F, -0.5F);
      int n = 20;
      float[] helix = new float[n * 4];

      for (int k = 0; k < n; k++) {
         float t = k / (n - 1.0F);
         double a = Math.PI + t * Math.PI * 5.0;
         float r = 0.8F - 0.22F * t;
         helix[k * 4] = -1.82F + r * (float)Math.cos(a);
         helix[k * 4 + 1] = 20.8F - 3.3F * t;
         helix[k * 4 + 2] = 2.02F + r * (float)Math.sin(a);
         helix[k * 4 + 3] = 0.42F - 0.18F * t;
      }

      Shape lock = Shapes.union(ribbon, Shapes.tube(helix)).paint(Paint.vertical(-7706904, 17.4F, -1912065, 25.0F));
      Part curlR = at(rig.extra("curl_r", Bone.HEAD), hp(-2.35F, 28.7F, -0.15F))
         .shape(hs(lock))
         .resolution(0.9F)
         .motion(Motion.hair().drag(4.0F).walk(3.0F, 0.0F, 3.5F, 2.0F).hang(0.7F).turn(10.0F, 0.0F).bend(1.4F))
         .detail(0.9F)
         .flatLight(0.4F)
         .outline(-10794346, 0.1F);
      rig.extra("curl_l", Bone.HEAD).mirrorOf(curlR);
   }

   private static int backHair(float y) {
      if (y > 26.2F) {
         return Colors.mix(-1912065, -6519080, clamp01((y - 26.2F) / 1.9F));
      } else {
         return y > 22.0F ? Colors.mix(-4940554, -1912065, clamp01((y - 22.0F) / 4.2F)) : Colors.mix(-7706904, -4940554, clamp01((y - 17.2F) / 4.8F));
      }
   }

   private static int shine(int c, float x, float y, float z, float ringY, float width, int dashes, float phase) {
      float phi = (float)Math.atan2(x, z - -0.3F);
      float d = Math.abs(y - ringY + 0.25F * (1.0F - (float)Math.cos(phi)));
      if (d > width) {
         return c;
      } else {
         float gap = (float)Math.sin(phi * dashes + phase) + 0.2F;
         if (gap <= 0.0F) {
            return c;
         } else {
            float k = (1.0F - d / width) * Math.min(1.0F, gap * 3.0F) * 0.8F;
            return Colors.mix(c, Colors.mix(c, -1, 0.5F), Math.min(1.0F, k * 1.6F));
         }
      }
   }

   private static Shape backLock(float deg, float lift, float tipY, float r0, float r1, float flat, float phase) {
      double a = Math.toRadians(deg);
      float sa = (float)Math.sin(a);
      float ca = (float)Math.cos(a);
      float[] ys = new float[]{29.3F, 27.6F, 25.3F, 23.0F, 20.8F, 18.9F, tipY};
      float[] pts = new float[ys.length * 3];

      for (int k = 0; k < ys.length; k++) {
         float y = ys[k];
         float rx = spline(CURTAIN_Y, CURTAIN_RX, y) + lift;
         float rz = spline(CURTAIN_Y, CURTAIN_RZ, y) + lift;
         float cz = spline(CURTAIN_Y, CURTAIN_CZ, y);
         float wave = 0.32F * clamp01((26.5F - y) / 5.0F) * (float)Math.sin(y * 0.95F + phase);
         float flick = k == ys.length - 1 ? 0.16F : 0.0F;
         pts[k * 3] = (rx + flick) * sa + wave * ca;
         pts[k * 3 + 1] = y;
         pts[k * 3 + 2] = cz + (rz + flick) * ca - wave * sa;
      }

      return Shapes.strand(r0, r1, pts).flatten(flat, sa, 0.0F, ca);
   }

   private static void hat(Rig rig) {
      Paint felt = Paint.vertical(-15460295, 28.6F, -14603682, 31.0F);
      Shape brim = Shapes.field(Luna::brimDistance, -6.1F, 28.1F, -6.35F, 6.1F, 30.1F, 5.85F).paint(felt);
      rig.extra("brim", Bone.HEAD).shape(worn(brim)).resolution(1.2F).detail(0.65F).outline(-15921370, 0.12F);
      Shape rim = Shapes.field(Luna::rimDistance, -6.2F, 28.35F, -6.45F, 6.2F, 29.65F, 5.95F).paint(Paint.of(-1326758).grain(0.6F, 0.08F));
      Shape band = Shapes.cone(0.0F, 28.7F, -0.25F, 3.43F, 0.0F, 31.2F, -0.3F, 3.08F)
         .intersect(Shapes.box(0.0F, 29.95F, -0.27F, 3.6F, 0.5F, 3.6F))
         .subtract(Shapes.cone(0.0F, 28.7F, -0.25F, 2.75F, 0.0F, 31.2F, -0.3F, 2.4F))
         .paint(Paint.vertical(-6650664, 29.45F, -4544530, 30.3F));
      rig.extra("hat_trim", Bone.HEAD).shape(worn(Shapes.union(rim, band))).resolution(0.95F).detail(0.35F).noOutline();
      Shape base = Shapes.cone(0.0F, 28.7F, -0.25F, 3.2F, 0.0F, 31.2F, -0.3F, 2.85F).intersect(Shapes.box(0.0F, 29.6F, -0.27F, 3.5F, 0.75F, 3.5F));
      rig.extra("hat", Bone.HEAD).shape(worn(base.paint(felt))).resolution(1.2F).detail(0.3F).outline(-15921370, 0.15F);
      rig.extra("hat_pin", Bone.HEAD).shape(worn(pin())).resolution(0.4F).detail(0.9F).outline(-8760296, 0.06F);
      Shape tip = Shapes.tube(
            0.0F,
            30.2F,
            -0.29F,
            2.8F,
            0.0F,
            32.2F,
            -0.42F,
            2.24F,
            0.0F,
            34.1F,
            -0.74F,
            1.56F,
            0.04F,
            35.3F,
            -1.42F,
            1.02F,
            0.14F,
            36.0F,
            -2.45F,
            0.64F,
            0.3F,
            36.15F,
            -3.65F,
            0.36F
         )
         .intersect(Shapes.above(30.15F))
         .subtract(0.25F, Shapes.ellipsoid(0.0F, 34.45F, -2.3F, 0.9F, 0.35F, 0.45F))
         .paint(Paint.vertical(-14603682, 30.0F, -14011792, 37.0F));
      Vec3 joint = hatPoint(0.0F, 30.4F, -0.3F);
      Part hatTip = at(rig.extra("hat_tip", rig.part(Bone.HEAD)), hp(joint.x(), joint.y(), joint.z()))
         .shape(worn(tip))
         .resolution(1.25F)
         .motion(
            new Motion()
               .idle(2.0F, 0.0F, 2.0F, 0.3F)
               .walk(6.0F, 0.0F, 4.0F, 2.0F)
               .drag(7.0F)
               .hang(0.25F)
               .turn(10.0F, 0.0F)
               .spring(38.0F, 4.5F)
               .bend(1.7F)
               .limit(35.0F)
         )
         .detail(0.75F)
         .outline(-15921370, 0.15F);
      Vec3 end = hatPoint(0.28F, 35.9F, -3.5F);
      Shape cord = Shapes.strand(0.08F, 0.08F, end.x(), end.y(), end.z(), end.x(), end.y() - 0.8F, end.z()).color(-5011921);
      Shape charm = Shapes.extrude(Shape2D.star(0.0F, 0.0F, 0.62F, 0.27F, 5).rotate(180.0F, 0.0F, 0.0F), 0.14F, 0.05F)
         .rotate(0.0F, 90.0F, 0.0F)
         .move(end.x(), end.y() - 1.3F, end.z())
         .paint(Paint.of(-9872).glow());
      at(rig.extra("hat_charm", hatTip), hp(end.x(), end.y(), end.z()))
         .shape(hs(Shapes.union(cord, charm)))
         .motion(Motion.hair().hang(0.9F).spring(60.0F, 6.0F))
         .resolution(0.45F)
         .detail(1.2F)
         .outline(-8760296, 0.05F);
   }

   private static float brimDistance(float x, float y, float z) {
      float dz = z + 0.25F;
      float rho = (float)Math.sqrt(x * x + dz * dz);
      return BRIM.distance(rho, y - wave(x, dz, rho, clamp01((rho - 2.9F) / 2.9F)));
   }

   private static float rimDistance(float x, float y, float z) {
      float dz = z + 0.25F;
      float rho = (float)Math.sqrt(x * x + dz * dz);
      float a = rho - 5.86F;
      float b = y - 29.0F - wave(x, dz, rho, 1.0F);
      return (float)Math.sqrt(a * a + b * b) - 0.31F;
   }

   private static float wave(float x, float dz, float rho, float s) {
      return rho < 0.001F ? 0.0F : 0.3F * s * s * (x * x - dz * dz) / (rho * rho);
   }

   private static Shape pin() {
      Shape2D moon = Shape2D.circle(0.0F, 0.0F, 0.68F).subtract(Shape2D.circle(0.34F, 0.18F, 0.52F));
      Shape2D star = Shape2D.star(0.46F, 0.14F, 0.38F, 0.17F, 5).rotate(180.0F, 0.46F, 0.14F);
      Shape badge = Shapes.union(
         Shapes.extrude(moon, 0.14F, 0.06F).paint(Paint.radial(-4688, -0.3F, -0.2F, 0.0F, 0.8F, -1326758).glow()),
         Shapes.extrude(star, 0.12F, 0.05F).move(0.0F, 0.0F, 0.04F).paint(Paint.of(-9872).glow())
      );
      double a = Math.toRadians(38.0);
      float r = 3.38F;
      return badge.rotate(-8.0F, 38.0F, 0.0F).move(r * (float)Math.sin(a), 29.95F, -0.25F + r * (float)Math.cos(a));
   }

   private static Shape worn(Shape s) {
      return hs(s.rotate(-8.0F, 0.0F, 4.0F, HAT_CENTRE.x(), HAT_CENTRE.y(), HAT_CENTRE.z()));
   }

   private static Vec3 hatPoint(float x, float y, float z) {
      return new Vec3(x, y, z).rotate(-8.0F, 0.0F, 4.0F, HAT_CENTRE);
   }

   private static void body(Rig rig) {
      Shape chest = Shapes.ellipsoid(0.0F, 21.35F, 0.05F, 2.2F, 1.75F, 1.4F);
      Shape shoulders = Shapes.capsule(0.0F, 22.95F, -0.15F, -2.45F, 22.4F, -0.15F, 0.72F).mirrorX();
      Shape waist = Shapes.ellipsoid(0.0F, 19.0F, 0.0F, 1.62F, 1.5F, 1.15F);
      Shape hips = Shapes.ellipsoid(0.0F, 16.8F, -0.05F, 2.2F, 1.75F, 1.5F);
      Shape torso = Shapes.blend(1.1F, chest, shoulders, waist, hips);
      Shape bodice = torso.paint(Paint.vertical(-14933168, 16.5F, -13945223, 22.5F));
      Shape neck = Shapes.capsule(0.0F, 22.9F, -0.15F, 0.0F, 25.1F, -0.05F, 0.6F)
         .paint(Paint.vertical(-6958, 23.7F, -1331558, 24.2F).flatLight(1.0F).occlusion(0.25F));
      Shape collar = Shapes.cone(0.0F, 22.5F, -0.12F, 1.02F, 0.0F, 24.3F, -0.1F, 0.78F).intersect(Shapes.box(0.0F, 23.45F, -0.1F, 1.3F, 0.63F, 1.3F));
      Shape belt = torso.inflate(0.22F).intersect(Shapes.box(0.0F, 18.45F, 0.0F, 3.0F, 0.6F, 3.0F)).paint(Paint.vertical(-11653344, 17.9F, -8762574, 18.9F));
      rig.part(Bone.BODY)
         .pivot(0.0F, 24.5F, 0.0F)
         .shape(Shapes.union(bodice, neck, belt))
         .resolution(1.2F)
         .detail(0.9F)
         .occlusion(0.3F)
         .outline(-15921370, 0.14F)
         .decal(bodiceTrim());
      rig.extra("collar", Bone.BODY)
         .shape(collar.paint(Paint.vertical(-15460295, 22.9F, -13945223, 24.05F)))
         .resolution(0.6F)
         .detail(0.35F)
         .occlusion(0.3F)
         .outline(-15921370, 0.08F);
      List<Shape> trim = new ArrayList<>();
      float zb = surfaceZ(torso, 0.0F, 23.1F, 4.0F);
      float zc = Math.max(zb, surfaceZ(collar, 0.0F, 23.1F, 4.0F));
      trim.add(Shapes.ellipsoid(0.0F, 23.08F, zc + 0.12F, 0.3F, 0.26F, 0.2F).color(-4544530));
      trim.add(Shapes.ellipsoid(0.6F, 23.14F, zc + 0.02F, 0.5F, 0.3F, 0.18F).rotate(0.0F, -18.0F, -14.0F, 0.6F, 23.14F, zc).mirrorX().color(-4544530));

      for (int s = -1; s <= 1; s += 2) {
         float z1 = surfaceZ(torso, s * 0.35F, 22.3F, 4.0F);
         float z2 = surfaceZ(torso, s * 0.55F, 21.6F, 4.0F);
         trim.add(
            Shapes.strand(0.22F, 0.16F, s * 0.12F, 22.95F, zc + 0.08F, s * 0.35F, 22.3F, z1 + 0.14F, s * 0.55F, 21.6F, z2 + 0.12F)
               .flatten(1.7F, 0.0F, 0.0F, 1.0F)
               .color(-4544530)
         );
      }

      trim.add(Shapes.sphere(0.0F, 23.08F, zc + 0.3F, 0.17F).paint(Paint.of(-4678401).glow()));
      float zs = surfaceZ(belt, 0.0F, 18.45F, 4.0F);
      trim.add(
         Shapes.extrude(Shape2D.star(0.0F, 0.0F, 0.56F, 0.25F, 5).rotate(180.0F, 0.0F, 0.0F), 0.17F, 0.05F).move(0.0F, 18.45F, zs + 0.08F).color(-1326758)
      );
      rig.extra("trim", Bone.BODY).shape(Shapes.union(trim.toArray(new Shape[0]))).resolution(0.5F).detail(0.55F).noOutline();
   }

   private static void cape(Rig rig) {
      Shape cape = Shapes.field(Luna::capeDistance, -6.0F, 6.3F, -6.2F, 6.0F, 25.4F, 1.3F).paint(Paint.field(Luna::capeColor));
      rig.extra("cape", Bone.BODY)
         .pivot(0.0F, 23.0F, -1.0F)
         .shape(cape)
         .resolution(1.35F)
         .motion(Motion.cloth().drag(12.0F).hang(0.6F))
         .detail(0.45F)
         .occlusion(0.3F)
         .noOutline()
         .decal(capeStars())
         .decal(capeHem());
   }

   private static float capeAt(int k, float y) {
      float f = (y - CAPE_Y[0]) / (CAPE_Y[CAPE_Y.length - 1] - CAPE_Y[0]) * 96.0F;
      f = f < 0.0F ? 0.0F : Math.min(f, 96.0F);
      int i = Math.min((int)f, 95);
      float a = CAPE_LUT[i * 4 + k];
      return a + (CAPE_LUT[i * 4 + 4 + k] - a) * (f - i);
   }

   private static float[] capeTable() {
      float[] t = new float[388];

      for (int i = 0; i <= 96; i++) {
         float y = CAPE_Y[0] + (CAPE_Y[CAPE_Y.length - 1] - CAPE_Y[0]) * i / 96.0F;
         t[i * 4] = spline(CAPE_Y, CAPE_RX, y);
         t[i * 4 + 1] = spline(CAPE_Y, CAPE_RZ, y);
         t[i * 4 + 2] = spline(CAPE_Y, CAPE_CZ, y);
         t[i * 4 + 3] = spline(CAPE_Y, CAPE_WRAP, y);
      }

      return t;
   }

   private static float capeDistance(float x, float y, float z) {
      float rx = capeAt(0, y);
      float rz = capeAt(1, y);
      float cz = capeAt(2, y);
      float d = Math.abs(ellipse(x, z - cz, rx, rz)) - 0.43F;
      if (d < 0.6F) {
         float ang = (float)Math.toDegrees(Math.atan2(Math.abs(x) / rx, -(z - cz) / rz));
         d = Math.max(d, (ang - capeAt(3, y)) * 0.0174533F * (rx + rz) * 0.5F);
      }

      d = Math.max(d, Math.max(y - 23.4F, 7.0F - y));
      if (y < 22.5F) {
         return d;
      } else {
         float cu = clamp01((y - 22.9F) / 2.3F);
         float crx = 1.45F + 0.35F * cu;
         float crz = 1.3F + 0.3F * cu;
         float cd = Math.abs(ellipse(x, z + 0.3F, crx, crz)) - 0.38F;
         if (cd < 0.6F) {
            float cang = (float)Math.toDegrees(Math.atan2(Math.abs(x) / crx, -(z + 0.3F) / crz));
            cd = Math.max(cd, (cang - 118.0F) * 0.0174533F * 1.5F);
         }

         cd = Math.max(cd, Math.max(y - 25.2F, 22.8F - y));
         return Math.min(d, cd);
      }
   }

   private static int capeColor(float x, float y, float z) {
      float rx = capeAt(0, y);
      float rz = capeAt(1, y);
      float cz = capeAt(2, y);
      if (y > 22.9F) {
         float cu = clamp01((y - 22.9F) / 2.3F);
         float ce = ellipse(x, z + 0.3F, 1.45F + 0.35F * cu, 1.3F + 0.3F * cu);
         if (Math.abs(ce) < Math.abs(ellipse(x, z - cz, rx, rz))) {
            return ce > 0.0F ? -14603682 : -7571752;
         }
      }

      float e = ellipse(x, z - cz, rx, rz);
      return e < 0.0F ? -7571752 : Colors.mix(-15460295, -14603682, clamp01((y - 7.0F) / 14.0F));
   }

   private static Decal capeStars() {
      Canvas c = new Canvas(176, 176);
      long seed = 7L;

      for (int i = 0; i < 36; i++) {
         long var9 = seed * 6364136223846793005L + 1442695040888963407L;
         float x = 7.0F + (float)((var9 >>> 33) % 162L);
         long var10 = var9 * 6364136223846793005L + 1442695040888963407L;
         float y = 8.0F + (float)((var10 >>> 33) % 160L);
         seed = var10 * 6364136223846793005L + 1442695040888963407L;
         float r = 2.8F + (float)((seed >>> 33) % 3L);
         boolean sparkle = (seed >>> 40) % 3L == 0L;
         Shape2D s = sparkle ? Shape2D.star(x, y, r * 1.3F, r * 0.3F, 4) : Shape2D.star(x, y, r, r * 0.46F, 5);
         c.fill(s, sparkle ? -598638 : -1326758);
      }

      c.fill(Shape2D.circle(88.0F, 92.0F, 19.0F).subtract(Shape2D.circle(97.0F, 86.0F, 16.0F)), -1326758);
      c.fill(Shape2D.star(97.0F, 88.0F, 7.5F, 3.2F, 5), -598638);
      return Decal.back(c, 0.0F, 12.5F, -3.0F, 11.0F, 11.0F).depth(4.0F, 4.0F).maxAngle(62.0F);
   }

   private static Decal capeHem() {
      Canvas c = new Canvas(192, 12);
      paintBand(c);
      return Decal.wrap(c, 0.0F, 7.27F, -0.8F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 230.0F, 0.48F).offset(0.03F);
   }

   private static void paintBand(Canvas c) {
      float h = c.height();
      float step = h * 2.0F;
      c.fillAll((x, y) -> {
         float v = y / h;
         if (v < 0.1F || v > 0.9F) {
            return 0;
         } else if (!(v < 0.24F) && !(v > 0.76F)) {
            float cx = ((float)Math.floor(x / step) + 0.5F) * step;
            return Math.abs(x - cx) + Math.abs(y - h * 0.5F) < h * 0.22F ? -2868 : -1326758;
         } else {
            return -5011921;
         }
      });
   }

   private static Canvas lace(int width, int scallops) {
      Canvas c = new Canvas(width, 16);
      float step = (float)width / scallops;
      c.fill(Shape2D.box(width * 0.5F, 3.0F, width * 0.5F, 3.0F, 0.0F), -1036);

      for (int i = 0; i < scallops; i++) {
         float x = (i + 0.5F) * step;
         c.fill(Shape2D.circle(x, 6.5F, step * 0.52F), -1036);
         c.erase(Shape2D.circle(x, 6.8F, step * 0.16F));
      }

      return c;
   }

   private static void skirt(Rig rig, Canvas band) {
      Canvas lace = lace(448, 34);

      for (int s = -1; s <= 1; s += 2) {
         Bone leg = s < 0 ? Bone.RIGHT_LEG : Bone.LEFT_LEG;
         Shape half = Shapes.union(
            tier(s, 18.5F, 12.4F, 1.75F, 3.45F, 2.5F, 11.0F, 0.24F, 0.78F, 6.0F, 16.0F).shape(-13945223, -15460295),
            tier(s, 13.4F, 9.1F, 3.1F, 3.95F, 1.3F, 14.0F, 0.28F, 0.74F, 14.0F, 22.0F).shape(-12693598, -14603682),
            tier(s, 9.55F, 8.6F, 3.7F, 4.05F, 1.0F, 20.0F, 0.16F, 0.74F, 20.0F, 24.0F).shape(-4611086, -7571752)
         );
         rig.extra(s < 0 ? "skirt_r" : "skirt_l", Bone.BODY)
            .pivot(0.0F, 18.2F, 0.0F)
            .shape(half)
            .resolution(1.15F)
            .motion(Motion.skirt(leg).follow(leg, 0.3F, 12.0F))
            .detail(0.4F)
            .occlusion(0.3F)
            .noOutline()
            .decal(hemBand(band, 12.58F, s * 76.0F, 140.0F, 0.36F))
            .decal(hemBand(band, 9.28F, s * 79.0F, 134.0F, 0.36F))
            .decal(hemBand(lace, 8.8F, s * 80.0F, 132.0F, 0.34F));
      }

      Luna.Tier under = new Luna.Tier(18.2F, 8.7F, 1.6F, 2.9F, 1.6F, 16.0F, 0.12F, 0.66F, -42.0F, -42.0F, 42.0F, 42.0F);
      rig.extra("underskirt", Bone.BODY)
         .pivot(0.0F, 18.2F, 0.2F)
         .shape(under.shape(-4611086, -7571752))
         .resolution(1.1F)
         .motion(new Motion().drag(-8.0F).walk(-5.0F, 0.0F, 0.0F, 2.0F).walkPhase(90.0F).spring(120.0F, 14.0F).bend(1.0F))
         .detail(0.4F)
         .occlusion(0.3F)
         .noOutline()
         .decal(underskirtStars());
   }

   private static Luna.Tier tier(
      int s, float yTop, float yBot, float rTop, float rBot, float flare, float folds, float amp, float thick, float edgeTop, float edgeBot
   ) {
      return s > 0
         ? new Luna.Tier(yTop, yBot, rTop, rBot, flare, folds, amp, thick, edgeTop, edgeBot, 188.0F, 188.0F)
         : new Luna.Tier(yTop, yBot, rTop, rBot, flare, folds, amp, thick, -188.0F, -188.0F, -edgeTop, -edgeBot);
   }

   private static Decal hemBand(Canvas c, float y, float centreDeg, float degrees, float height) {
      double a = Math.toRadians(centreDeg);
      return Decal.wrap(c, 0.0F, y, 0.0F, 0.0F, 1.0F, 0.0F, (float)Math.sin(a), 0.0F, (float)Math.cos(a), degrees, height).offset(0.03F);
   }

   private static Decal underskirtStars() {
      Canvas c = new Canvas(192, 288);
      long seed = 3L;

      for (int i = 0; i < 20; i++) {
         long var6 = seed * 6364136223846793005L + 1442695040888963407L;
         float x = 12.0F + (float)((var6 >>> 33) % 168L);
         seed = var6 * 6364136223846793005L + 1442695040888963407L;
         float y = 88.0F + (float)((seed >>> 33) % 170L);
         c.fill(Shape2D.star(x, y, 5.4F, 1.7F, 4), -1326758);
      }

      for (int i = 0; i < 18; i++) {
         float x = (i + 0.5F) * 192.0F / 18.0F;
         c.fill(Shape2D.circle(x, 278.0F, 6.2F), -1036);
         c.erase(Shape2D.circle(x, 278.5F, 1.9F));
      }

      c.fill(Shape2D.box(96.0F, 272.0F, 96.0F, 4.0F, 0.0F), -1036);
      return Decal.front(c, 0.0F, 13.5F, 3.0F, 6.4F, 9.6F).depth(3.5F, 3.0F).maxAngle(66.0F);
   }

   private static Decal bodiceTrim() {
      Canvas c = new Canvas(64, 96);

      for (int s = -1; s <= 1; s += 2) {
         c.fill(Shape2D.path(2.0F, 2.0F, 32.0F + s * 10.5F, 2.0F, 32.0F + s * 9.0F, 48.0F, 32.0F + s * 9.8F, 94.0F), -1326758);
      }

      for (int i = 0; i < 4; i++) {
         float y = 13.0F + i * 21.0F;
         c.fill(Shape2D.circle(32.0F, y, 5.0F), -5011921);
         c.fill(Shape2D.circle(32.0F, y, 4.0F), -1326758);
         c.fill(Shape2D.circle(30.8F, y - 1.3F, 1.5F), -2868);
      }

      return Decal.front(c, 0.0F, 20.6F, 3.0F, 2.0F, 3.0F).depth(2.5F, 2.0F).maxAngle(70.0F);
   }

   private static Shape fore(Shape s, int side) {
      return s.rotate(-12.0F, 0.0F, side * 3.0F, side * ELBOW.x(), ELBOW.y(), ELBOW.z());
   }

   private static Vec3 fore(float x, float y, float z) {
      return new Vec3(x, y, z).rotate(-12.0F, 0.0F, 3.0F, ELBOW);
   }

   private static void arms(Rig rig, Canvas band) {
      Paint skin = Paint.of(-6958).flatLight(1.0F).occlusion(0.25F);
      Shape puff = Shapes.ellipsoid(-3.5F, 22.0F, -0.1F, 0.92F, 1.0F, 0.9F);
      Shape upper = Shapes.cone(-3.55F, 21.5F, -0.1F, 0.66F, -3.72F, 18.45F, -0.05F, 0.55F);
      Shape elbow = Shapes.sphere(-3.72F, 18.4F, -0.05F, 0.55F);
      Shape lower = fore(Shapes.cone(-3.72F, 18.3F, -0.05F, 0.54F, -3.84F, 15.6F, 0.12F, 0.5F), 1);
      Shape sleeve = Shapes.blend(0.35F, puff, upper, elbow, lower).paint(Paint.vertical(-14867372, 15.0F, -13945223, 22.0F));
      Shape bell = fore(Shapes.cone(-3.84F, 16.1F, 0.12F, 0.52F, -3.92F, 14.3F, 0.2F, 1.02F).intersect(Shapes.above(14.3F)), 1).color(-13945223);
      Shape hollow = fore(Shapes.cone(-3.84F, 15.7F, 0.12F, 0.2F, -3.93F, 14.0F, 0.2F, 0.5F), 1).color(-7571752);
      Shape arm = Shapes.union(sleeve, Shapes.subtract(bell, hollow));
      Vec3 grip = fore(-4.0F, 13.3F, 0.42F);
      Vec3 cuff = fore(-3.91F, 14.52F, 0.2F);
      Vec3 axis = new Vec3(-0.04F, 1.0F, 0.05F).rotate(-12.0F, 0.0F, 3.0F);
      Vec3 front = new Vec3(0.0F, 0.0F, 1.0F).rotate(-12.0F, 0.0F, 3.0F);
      Part right = rig.part(Bone.RIGHT_ARM)
         .pivot(-3.5F, 22.3F, -0.1F)
         .grip(grip.x(), grip.y(), grip.z())
         .shape(arm)
         .resolution(0.95F)
         .detail(0.75F)
         .occlusion(0.3F)
         .outline(-15921370, 0.13F)
         .swing(0.8F)
         .joint(ELBOW.x(), ELBOW.y(), ELBOW.z(), 0.55F)
         .decal(Decal.wrap(band, cuff.x(), cuff.y(), cuff.z(), axis.x(), axis.y(), axis.z(), front.x(), front.y(), front.z(), 360.0F, 0.36F).offset(0.03F));
      rig.part(Bone.LEFT_ARM).mirrorOf(right);
      List<Shape> hand = new ArrayList<>();
      hand.add(Shapes.cone(3.92F, 14.6F, 0.22F, 0.33F, 3.97F, 13.8F, 0.28F, 0.33F));
      hand.add(Shapes.ellipsoid(4.0F, 13.25F, 0.32F, 0.27F, 0.6F, 0.52F));
      float[] curl = new float[]{-24.0F, -19.0F, -14.0F, -8.0F};

      for (int f = 0; f < 4; f++) {
         float z = 0.02F + f * 0.21F;
         float len = f != 0 && f != 3 ? 0.8F : 0.62F;
         hand.add(Shapes.capsule(4.02F, 12.8F, z, 3.94F, 12.8F - len, z + 0.1F, 0.12F).rotate(0.0F, 0.0F, curl[f], 4.02F, 12.85F, z));
      }

      hand.add(Shapes.capsule(3.86F, 13.35F, 0.76F, 3.74F, 12.75F, 0.96F, 0.14F));
      Part left = rig.extra("hand_l", Bone.LEFT_ARM)
         .shape(fore(Shapes.blend(0.18F, hand.toArray(new Shape[0])).paint(skin), -1))
         .resolution(0.5F)
         .detail(0.6F)
         .outline(-5737368, 0.08F);
      rig.extra("hand_r", Bone.RIGHT_ARM).mirrorOf(left);
   }

   private static void legs(Rig rig) {
      Shape thigh = Shapes.cone(-1.3F, 15.3F, 0.0F, 1.18F, -1.1F, 8.6F, 0.08F, 0.82F);
      Shape knee = Shapes.sphere(-1.08F, 8.25F, 0.14F, 0.78F);
      Shape kneecap = Shapes.sphere(-1.08F, 8.3F, 0.45F, 0.55F);
      Shape shin = Shapes.cone(-1.08F, 8.0F, 0.1F, 0.8F, -1.14F, 2.1F, -0.1F, 0.5F);
      Shape calf = Shapes.ellipsoid(-1.12F, 5.9F, -0.3F, 0.85F, 1.5F, 0.9F);
      Shape leg = Shapes.blend(0.45F, thigh, knee, kneecap, shin, calf).paint(Paint.field(Luna::tights));
      Shape shaft = Shapes.cone(-1.12F, 4.9F, -0.08F, 0.8F, -1.1F, 1.5F, -0.1F, 0.62F).intersect(Shapes.below(5.0F));
      Shape toe = Shapes.ellipsoid(-1.1F, 0.48F, 1.0F, 0.6F, 0.48F, 0.82F);
      Shape instep = Shapes.ellipsoid(-1.1F, 1.0F, 0.25F, 0.64F, 0.62F, 0.9F);
      Shape boot = Shapes.blend(0.35F, shaft, toe, instep).paint(Paint.vertical(-9550799, 0.5F, -7710146, 4.6F));
      Shape heel = Shapes.box(-1.1F, 0.42F, -0.5F, 0.4F, 0.42F, 0.36F, 0.14F).color(-12704232);
      Shape sole = Shapes.box(-1.1F, 0.12F, 0.97F, 0.64F, 0.12F, 0.8F, 0.1F).color(-12966888);
      Shape strap = Shapes.torus(-1.1F, 1.75F, -0.05F, 0.62F, 0.26F).color(-11653344);
      Shape buckle = Shapes.ellipsoid(-1.72F, 1.75F, 0.57F, 0.24F, 0.3F, 0.14F).rotate(0.0F, -45.0F, 0.0F, -1.72F, 1.75F, 0.57F).color(-1326758);
      Shape foot = Shapes.union(boot, heel, sole, strap, buckle).rotate(0.0F, -7.0F, 0.0F, -1.12F, 0.0F, -0.1F);
      Shape all = Shapes.union(leg, foot).intersect(Shapes.above(0.0F));
      Part right = rig.part(Bone.RIGHT_LEG)
         .pivot(-1.3F, 15.3F, 0.0F)
         .shape(all)
         .resolution(1.2F)
         .detail(0.75F)
         .occlusion(0.3F)
         .outline(-14411226, 0.13F)
         .swing(0.55F, 30.0F)
         .joint(-1.08F, 8.25F, 0.14F, 0.8F);
      rig.part(Bone.LEFT_LEG).mirrorOf(right);
      Shape cuff = Shapes.cylinder(-1.12F, 4.45F, -0.08F, -1.12F, 5.18F, -0.08F, 0.94F, 0.18F).paint(Paint.vertical(-7710146, 4.5F, -6395064, 5.1F));
      Part cuffR = rig.extra("cuff_r", Bone.RIGHT_LEG).shape(cuff).resolution(0.7F).detail(0.9F).occlusion(0.3F).outline(-12966888, 0.08F);
      rig.extra("cuff_l", Bone.LEFT_LEG).mirrorOf(cuffR);
   }

   private static int tights(float x, float y, float z) {
      float cx = -1.1F - 0.2F * clamp01((y - 8.0F) / 7.0F);
      float cz = 0.05F;
      float a = (float)Math.atan2(x - cx, z - cz);
      float sheen = (float)Math.exp(-(a + 0.35F) * (a + 0.35F) / 0.16F);
      int base = Colors.mix(Colors.shade(-12959880, 0.82F), -12959880, clamp01((y - 3.0F) / 6.0F));
      return Colors.mix(base, -10656082, 0.55F * sheen);
   }

   private static Decal faceDecal(List<Luna.Lock> bangs) {
      Canvas canvas = new Canvas(192, 158);
      Shape2D shadow = null;

      for (Luna.Lock l : bangs) {
         Shape2D s = l.shadow(0.28F).inflate(3.7333333F);
         shadow = shadow == null ? s : shadow.union(s);
      }

      canvas.fill(shadow, Colors.withAlpha(-1199194, 0.55F), 4.0F);
      canvas.draw(features(), 12, 44);
      Vec3 c = hp(0.0F, 26.90625F, 3.0F);
      return Decal.front(canvas, c.x(), c.y(), c.z(), 4.8599997F, 3.9993749F).depth(3.0F, 3.0F).maxAngle(74.0F).occlusion(0.3F).flatLight(1.0F);
   }

   private static Canvas features() {
      float k = 0.5833333F;
      Canvas face = new Canvas(168, 114);
      Canvas eye = new Canvas(64, 80);
      eye(new Luna.Pen(eye, k, 60.0F, 96.0F), 122.0F, 181.0F, -1);
      face.draw(eye, 7, 0);
      face.draw(eye.mirrored(), 97, 0);
      Luna.Pen c = new Luna.Pen(face, k, 48.0F, 96.0F);

      for (int s = -1; s <= 1; s += 2) {
         float ex = 192.0F + s * 70.0F;
         float ey = 181.0F;
         c.fill(Shape2D.star(ex - 10.0F, ey - 13.0F, 14.0F, 3.8F, 4), -1);
         c.fill(Shape2D.circle(ex - 10.0F, ey - 13.0F, 4.8F), -1);
         c.fill(Shape2D.star(ex + 12.0F, ey + 17.0F, 7.0F, 2.1F, 4), -285212673);
         c.fill(Shape2D.circle(ex + 10.0F, ey - 2.0F, 2.3F), -1191182337);
         c.fill(Shape2D.ellipse(192.0F + s * 96.0F, 226.0F, 27.0F, 12.0F), 1895793815, 12.0F);
      }

      c.fill(Shape2D.curve(191.0F, 232.0F, 195.0F, 238.0F, 190.0F, 242.0F, 2.4F, 1.6F), -2135459206);
      c.fill(Shape2D.curve(180.0F, 275.0F, 192.0F, 284.0F, 204.0F, 275.0F, 3.2F, 3.2F), -7060390);
      c.fill(Shape2D.curve(188.0F, 285.0F, 192.0F, 287.0F, 196.0F, 285.0F, 1.8F, 1.8F), 1438670960);
      return face;
   }

   private static void eye(Luna.Pen c, float ex, float ey, int s) {
      Shape2D white = Shape2D.ellipse(ex, ey + 2.0F, 37.0F, 46.0F);
      c.fill(white, Paint2D.linear(ex, ey - 44.0F, ex, ey - 8.0F, -3160852, -1));
      Paint2D base = Paint2D.linear(ex, ey - 36.0F, ex, ey + 44.0F, -14282676, -11194192, -6656782, -2044673);
      c.fill(Shape2D.ellipse(ex, ey + 4.0F, 29.0F, 41.0F).intersect(white), (x, y) -> {
         float lx = (x - ex) / 32.0F;
         float ly = (y - ey + 28.0F) / 19.0F;
         float gx = (x - ex) / 18.0F;
         float gy = (y - ey - 27.0F) / 10.0F;
         float rx = (x - ex) / 29.0F;
         float ry = (y - ey - 4.0F) / 41.0F;
         float lid = clamp01((1.0F - (float)Math.sqrt(lx * lx + ly * ly)) * 8.0F) * 0.47F;
         float glow = clamp01((1.0F - (float)Math.sqrt(gx * gx + gy * gy)) * 5.0F) * 0.5F;
         float rim = clamp01(((float)Math.sqrt(rx * rx + ry * ry) - 0.88F) / 0.06F) * 0.82F;
         int col = Colors.mix(Colors.mix(base.argb(x, y), -14676416, lid), -726785, glow);
         return Colors.mix(col, -14479041, rim);
      });
      c.fill(Shape2D.ellipse(ex, ey + 5.0F, 12.0F, 17.0F), -15136210);
      c.fill(
         Shape2D.path(
            6.0F,
            12.0F,
            ex - s * 37.0F,
            ey - 6.0F,
            ex - s * 24.0F,
            ey - 36.0F,
            ex + s * 2.0F,
            ey - 48.0F,
            ex + s * 25.0F,
            ey - 42.0F,
            ex + s * 39.0F,
            ey - 26.0F,
            ex + s * 50.0F,
            ey - 32.0F
         ),
         -14938064
      );
      c.fill(Shape2D.curve(ex + s * 40.0F, ey - 28.0F, ex + s * 52.0F, ey - 40.0F, ex + s * 58.0F, ey - 50.0F, 5.0F, 1.0F), -14938064);
      c.fill(Shape2D.curve(ex + s * 30.0F, ey - 38.0F, ex + s * 38.0F, ey - 50.0F, ex + s * 40.0F, ey - 58.0F, 3.5F, 1.0F), -14938064);
      c.fill(Shape2D.curve(ex + s * 6.0F, ey + 48.0F, ex + s * 26.0F, ey + 44.0F, ex + s * 35.0F, ey + 28.0F, 1.5F, 4.0F), -1069931696);
      c.fill(Shape2D.path(2.0F, 2.0F, ex - s * 18.0F, ey - 55.0F, ex + s * 4.0F, ey - 60.0F, ex + s * 27.0F, ey - 53.0F), 1883909461);
      c.fill(Shape2D.path(5.5F, 2.0F, ex - s * 28.0F, ey - 70.0F, ex + s * 2.0F, ey - 82.0F, ex + s * 36.0F, ey - 76.0F), -7703368);
   }

   private static float ellipse(float x, float z, float rx, float rz) {
      float px = x / rx;
      float pz = z / rz;
      float k0 = (float)Math.sqrt(px * px + pz * pz);
      float k1 = (float)Math.sqrt(px * px / (rx * rx) + pz * pz / (rz * rz));
      return k1 < 1.0E-6F ? -Math.min(rx, rz) : k0 * (k0 - 1.0F) / k1;
   }

   private static float spline(float[] ks, float[] vs, float y) {
      int n = ks.length;
      if (y <= ks[0]) {
         return vs[0];
      } else if (y >= ks[n - 1]) {
         return vs[n - 1];
      } else {
         int i = 0;

         while (i < n - 2 && y > ks[i + 1]) {
            i++;
         }

         float t = (y - ks[i]) / (ks[i + 1] - ks[i]);
         float h = ks[i + 1] - ks[i];
         float m0 = i == 0 ? (vs[1] - vs[0]) / (ks[1] - ks[0]) : (vs[i + 1] - vs[i - 1]) / (ks[i + 1] - ks[i - 1]);
         float m1 = i + 2 >= n ? (vs[n - 1] - vs[n - 2]) / (ks[n - 1] - ks[n - 2]) : (vs[i + 2] - vs[i]) / (ks[i + 2] - ks[i]);
         float t2 = t * t;
         float t3 = t2 * t;
         return (2.0F * t3 - 3.0F * t2 + 1.0F) * vs[i] + (t3 - 2.0F * t2 + t) * h * m0 + (-2.0F * t3 + 3.0F * t2) * vs[i + 1] + (t3 - t2) * h * m1;
      }
   }

   private static float surfaceZ(Shape s, float x, float y, float start) {
      float z = start;

      for (int i = 0; i < 80; i++) {
         float d = s.distance(x, y, z);
         if (Math.abs(d) < 0.001F) {
            break;
         }

         z -= d;
      }

      return z;
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   @Environment(EnvType.CLIENT)
   private static final class Lock {
      private static final float REF = 2.6F;
      private final float rad;
      private final float top;
      private final float tip;
      private final float width;
      private final float hook;
      private final float g0;
      private final float lift;
      private final float g1;
      private final float t0;
      private final float t1;
      private final float[] ys;
      private final float[] rs;
      private final float[] poly;
      private final Shape2D outline;

      Lock(float deg, float top, float tip, float width, float hook, float g0, float lift, float g1, float t0, float t1, float[] ys, float[] rs) {
         this.rad = (float)Math.toRadians(deg);
         this.top = top;
         this.tip = tip;
         this.width = width;
         this.hook = hook;
         this.g0 = g0;
         this.lift = lift;
         this.g1 = g1;
         this.t0 = t0;
         this.t1 = t1;
         this.ys = ys;
         this.rs = rs;
         int n = 7;
         float so = hook > 0.0F ? -1.0F : 1.0F;
         this.poly = new float[(2 * n + 3) * 2];
         int k = 0;
         this.poly[k++] = so * width;
         this.poly[k++] = top + 0.5F;

         for (int i = 0; i <= n; i++) {
            float s = 1.0F - (float)Math.pow(1.0F - (float)i / n, 1.4F);
            this.poly[k++] = hook * s * s + so * width * (float)Math.pow(1.0F - s, 0.8F);
            this.poly[k++] = top + (tip - top) * s;
         }

         for (int i = n - 1; i >= 0; i--) {
            float s = 1.0F - (float)Math.pow(1.0F - (float)i / n, 1.4F);
            this.poly[k++] = hook * s * s - so * width * (float)Math.pow(1.0F - s, 1.25);
            this.poly[k++] = top + (tip - top) * s;
         }

         this.poly[k++] = -so * width;
         this.poly[k] = top + 0.5F;
         this.outline = Shape2D.polygon(this.poly);
      }

      private float gap(float s) {
         float r = 1.0F - s;
         return this.g0 * r * r + 2.0F * this.lift * s * r + this.g1 * s * s;
      }

      float distance(float x, float y, float z) {
         float dz = z - -0.3F;
         float rho = (float)Math.sqrt(x * x + dz * dz);
         float s = Luna.clamp01((this.top - y) / (this.top - this.tip));
         float in = Luna.interp(this.ys, this.rs, y) + this.gap(s);
         float t = this.t0 + (this.t1 - this.t0) * s;
         float dw = Math.max(in - rho, rho - in - t);
         if (dw > 0.8F) {
            return dw;
         } else {
            float u = 2.6F * ((float)Math.atan2(x, dz) - this.rad);
            float d2 = this.outline.distance(u, y);
            if (d2 > 0.8F) {
               return d2;
            } else {
               float q = Math.min(1.0F, Math.abs(u - this.hook * s * s) / (this.width * (1.0F - s) + 0.05F));
               dw = Math.max(in - rho, rho - in - t * (1.0F - 0.35F * q * q));
               float h = Luna.clamp01(0.5F - 0.5F * (dw - d2) / 0.07F);
               return dw + (d2 - dw) * h + 0.07F * h * (1.0F - h);
            }
         }
      }

      Shape shape() {
         float u0 = Float.MAX_VALUE;
         float u1 = -Float.MAX_VALUE;
         float r0 = Float.MAX_VALUE;
         float r1 = 0.0F;

         for (int i = 0; i < this.poly.length; i += 2) {
            u0 = Math.min(u0, this.poly[i]);
            u1 = Math.max(u1, this.poly[i]);
         }

         for (float r : this.rs) {
            r0 = Math.min(r0, r);
            r1 = Math.max(r1, r);
         }

         r0 = Math.max(0.2F, r0 + Math.min(this.g0, this.g1) - 0.15F);
         r1 += Math.max(this.g0, Math.max(this.lift, this.g1)) + this.t0 + 0.15F;
         float x0 = Float.MAX_VALUE;
         float x1 = -Float.MAX_VALUE;
         float z0 = Float.MAX_VALUE;
         float z1 = -Float.MAX_VALUE;

         for (int i = 0; i <= 12; i++) {
            double a = this.rad + (u0 + (u1 - u0) * i / 12.0F) / 2.6F;

            for (float r : new float[]{r0, r1}) {
               x0 = Math.min(x0, r * (float)Math.sin(a));
               x1 = Math.max(x1, r * (float)Math.sin(a));
               z0 = Math.min(z0, -0.3F + r * (float)Math.cos(a));
               z1 = Math.max(z1, -0.3F + r * (float)Math.cos(a));
            }
         }

         return Shapes.field(this::distance, x0 - 0.1F, this.tip - 0.15F, z0 - 0.1F, x1 + 0.1F, this.top + 0.6F, z1 + 0.1F);
      }

      Shape2D shadow(float drop) {
         float[] p = (float[])this.poly.clone();

         for (int i = 0; i < p.length; i += 2) {
            float y = p[i + 1];
            float r = Luna.interp(this.ys, this.rs, y) + this.gap(Luna.clamp01((this.top - y) / (this.top - this.tip))) + this.t0 * 0.5F;
            p[i] = (r * (float)Math.sin(this.rad + p[i] / 2.6F) - -2.3142858F) * 41.48148F;
            p[i + 1] = (28.810715F - y + drop) * 41.48148F;
         }

         return Shape2D.polygon(p);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Pen {
      private final Canvas canvas;
      private final float k;
      private final float ox;
      private final float oy;

      Pen(Canvas canvas, float k, float ox, float oy) {
         this.canvas = canvas;
         this.k = k;
         this.ox = ox;
         this.oy = oy;
      }

      private Shape2D map(Shape2D shape) {
         float f = this.k / (this.k - 1.0F);
         return shape.scale(this.k, this.ox * f, this.oy * f);
      }

      void fill(Shape2D shape, int argb) {
         this.canvas.fill(this.map(shape), argb);
      }

      void fill(Shape2D shape, int argb, float feather) {
         this.canvas.fill(this.map(shape), argb, feather * this.k);
      }

      void fill(Shape2D shape, Paint2D paint) {
         this.canvas.fill(this.map(shape), (x, y) -> paint.argb(x / this.k + this.ox, y / this.k + this.oy));
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Tier {
      private static final int STEPS = 48;
      private final float yTop;
      private final float yBot;
      private final float rTop;
      private final float rBot;
      private final float folds;
      private final float amp;
      private final float half;
      private final float a0Top;
      private final float a0Bot;
      private final float a1Top;
      private final float a1Bot;
      private final float[] shape = new float[98];

      Tier(float yTop, float yBot, float rTop, float rBot, float flare, float folds, float amp, float thick, float a0Top, float a0Bot, float a1Top, float a1Bot) {
         this.yTop = yTop;
         this.yBot = yBot;
         this.rTop = rTop;
         this.rBot = rBot;
         this.folds = folds;
         this.amp = amp;
         this.half = thick * 0.5F;
         this.a0Top = a0Top;
         this.a0Bot = a0Bot;
         this.a1Top = a1Top;
         this.a1Bot = a1Bot;

         for (int i = 0; i <= 48; i++) {
            float u = Math.min(i / 48.0F, 0.999F);
            this.shape[i * 2] = 1.0F - (float)Math.pow(1.0F - u, flare);
            this.shape[i * 2 + 1] = flare * (float)Math.pow(1.0F - u, flare - 1.0F);
         }
      }

      private float u(float y) {
         return Luna.clamp01((this.yTop - y) / (this.yTop - this.yBot));
      }

      private float table(int k, float u) {
         float f = u * 48.0F;
         int i = Math.min((int)f, 47);
         float a = this.shape[i * 2 + k];
         return a + (this.shape[i * 2 + 2 + k] - a) * (f - i);
      }

      float radius(float x, float y, float z) {
         float u = this.u(y);
         return this.rTop + (this.rBot - this.rTop) * this.table(0, u) + this.amp * u * (float)Math.cos(this.folds * Math.atan2(x, z));
      }

      float distance(float x, float y, float z) {
         float u = this.u(y);
         float h = this.yTop - this.yBot;
         float rho = (float)Math.sqrt(x * x + z * z);
         float base = this.rTop + (this.rBot - this.rTop) * this.table(0, u);
         float far = Math.abs(rho - base) - this.half - this.amp;
         if (far > 0.6F) {
            return Math.max(far * 0.7F, Math.max(y - this.yTop, this.yBot - y));
         } else {
            double th = Math.atan2(x, z);
            float c = (float)Math.cos(this.folds * th);
            float s = (float)Math.sin(this.folds * th);
            float r = base + this.amp * u * c;
            float dry = ((this.rBot - this.rTop) * this.table(1, u) + this.amp * c) / h;
            float drt = this.amp * u * this.folds * s / Math.max(rho, 0.5F);
            float d = (Math.abs(rho - r) - this.half) / (float)Math.sqrt(1.0F + dry * dry + drt * drt);
            d = Math.max(d, Math.max(y - this.yTop, this.yBot - y));
            double a0 = Math.toRadians(this.a0Top + (this.a0Bot - this.a0Top) * u);
            double a1 = Math.toRadians(this.a1Top + (this.a1Bot - this.a1Top) * u);
            double dt = th - (a0 + a1) * 0.5;

            while (dt > Math.PI) {
               dt -= Math.PI * 2;
            }

            while (dt < -Math.PI) {
               dt += Math.PI * 2;
            }

            return Math.max(d, (float)(Math.abs(dt) - (a1 - a0) * 0.5) * rho);
         }
      }

      Shape shape(int outer, int inner) {
         float reach = Math.max(this.rTop, this.rBot) + this.amp + this.half + 0.2F;
         float lo = Math.min(this.a0Top, this.a0Bot);
         float hi = Math.max(this.a1Top, this.a1Bot);
         float x0 = 0.0F;
         float x1 = 0.0F;
         float z0 = 0.0F;
         float z1 = 0.0F;

         for (int i = 0; i <= 64; i++) {
            double a = Math.toRadians(lo + (hi - lo) * i / 64.0F);
            x0 = Math.min(x0, (float)Math.sin(a) * reach);
            x1 = Math.max(x1, (float)Math.sin(a) * reach);
            z0 = Math.min(z0, (float)Math.cos(a) * reach);
            z1 = Math.max(z1, (float)Math.cos(a) * reach);
         }

         Shape s = Shapes.field(this::distance, x0 - 0.3F, this.yBot - 0.2F, z0 - 0.3F, x1 + 0.3F, this.yTop + 0.2F, z1 + 0.3F);
         return s.paint(
            Paint.field(
               (x, y, z) -> {
                  float rho = (float)Math.sqrt(x * x + z * z);
                  return rho < this.radius(x, y, z)
                     ? inner
                     : Colors.mix(Colors.shade(outer, 0.8F), outer, Luna.clamp01((y - this.yBot) / (this.yTop - this.yBot) * 1.6F));
               }
            )
         );
      }
   }
}
