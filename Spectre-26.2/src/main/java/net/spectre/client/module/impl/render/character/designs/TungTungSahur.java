package net.spectre.client.module.impl.render.character.designs;

import net.spectre.client.module.impl.render.character.kit.Bone;
import net.spectre.client.module.impl.render.character.kit.Canvas;
import net.spectre.client.module.impl.render.character.kit.CharacterDesign;
import net.spectre.client.module.impl.render.character.kit.Decal;
import net.spectre.client.module.impl.render.character.kit.Motion;
import net.spectre.client.module.impl.render.character.kit.Noise;
import net.spectre.client.module.impl.render.character.kit.Paint;
import net.spectre.client.module.impl.render.character.kit.Paint2D;
import net.spectre.client.module.impl.render.character.kit.Part;
import net.spectre.client.module.impl.render.character.kit.Rig;
import net.spectre.client.module.impl.render.character.kit.Sample;
import net.spectre.client.module.impl.render.character.kit.Shape;
import net.spectre.client.module.impl.render.character.kit.Shape2D;
import net.spectre.client.module.impl.render.character.kit.Shapes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TungTungSahur extends CharacterDesign {
   private static final float R = 5.0F;
   private static final float BOTTOM = 12.0F;
   private static final float TOP = 34.0F;
   private static final float RIM = 0.85F;
   private static final float FIST_X = -7.0F;
   private static final float FIST_Y = 11.6F;
   private static final float FIST_Z = 0.72F;
   private static final float[] KNUCKLE_DIR = unit(-0.16F, 0.86F, 0.48F);
   private static final int WOOD = -1063542;
   private static final int WOOD_DARK = -4158378;
   private static final int WOOD_WORN = -5868993;
   private static final int FISSURE = -9551070;
   private static final int KNOT = -11653867;
   private static final int CUT = -1389672;
   private static final int LIMB = -3567528;
   private static final int LIMB_DARK = -7053254;
   private static final int INK = -15003638;
   private static final int LINE = -12967406;

   public TungTungSahur() {
      super("tung_tung_sahur", "Tung Tung Tung Sahur");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-12967406, 0.16F).occlusion(0.3F).firstPersonArms(false);
      rig.part(Bone.HEAD).pivot(0.0F, 12.5F, 0.0F);
      Part upright = rig.extra("upright", Bone.HEAD).motion(Motion.none().hang(1.0F).limit(100.0F));
      rig.extra("log", upright)
         .shape(log())
         .detail(1.5F)
         .resolution(1.2F)
         .flatLight(0.35F)
         .neck(0.0F, 22.5F, 0.0F, 0.0F, 1.0F, 0.0F, 4.0F)
         .look(0.6F, 0.0F, 40.0F)
         .motion(Motion.none().walk(0.0F, 4.0F, -6.0F, 1.0F).drag(6.0F))
         .decal(eyes())
         .decal(mouth())
         .decal(rings());
      Part arm = rig.part(Bone.RIGHT_ARM).pivot(-5.3F, 22.8F, 0.2F).grip(-7.0F, 11.6F, 0.72F).shape(arm()).joint(-6.57F, 17.1F, 0.14F, 0.6F);
      rig.part(Bone.LEFT_ARM).mirrorOf(arm);
      Part hand = rig.extra("hand_r", Bone.RIGHT_ARM).shape(hand()).resolution(0.6F).detail(0.8F);
      rig.extra("hand_l", Bone.LEFT_ARM).mirrorOf(hand);
      Part leg = rig.part(Bone.RIGHT_LEG).pivot(-2.2F, 12.8F, 0.0F).shape(leg()).swing(0.6F, 35.0F).joint(-2.3F, 6.75F, 0.16F, 0.7F);
      rig.part(Bone.LEFT_LEG).mirrorOf(leg);
      Part foot = rig.extra("foot_r", Bone.RIGHT_LEG).shape(foot()).resolution(0.7F).detail(0.8F);
      rig.extra("foot_l", Bone.LEFT_LEG).mirrorOf(foot);
   }

   private static Shape log() {
      Shape trunk = Shapes.field(TungTungSahur::trunk, -5.5F, 11.8F, -5.5F, 5.5F, 34.2F, 5.5F);
      Paint grain = new TungTungSahur.Bark(
         Paint.wood(-1063542, -4158378, 0.3F, 0.0F, -0.2F, 0.0F, 1.0F, 0.0F, 0.8F).noise(-5868993, 3.5F, 0.08F).grain(0.35F, 0.04F)
      );
      Shape body = Shapes.blend(
         0.35F,
         trunk.paint(grain),
         knot(38.0F, 17.2F, 0.8F),
         knot(165.0F, 27.5F, 0.95F),
         knot(-120.0F, 19.5F, 0.7F),
         knot(-58.0F, 14.2F, 0.55F),
         stub(128.0F, 25.2F),
         eye(1.0F, grain),
         eye(-1.0F, grain)
      );
      Shape mouth = Shapes.ellipsoid(0.0F, 25.35F, radius(0.0F, 25.35F, 1.0F) + 0.12F, 2.7F, 1.05F, 0.55F);
      return Shapes.subtract(0.12F, Shapes.subtract(0.35F, body, mouth), Shapes.union(hole(165.0F, 27.5F, 0.32F), hole(-120.0F, 19.5F, 0.24F)));
   }

   private static Shape stub(float deg, float y) {
      float sx = (float)Math.sin(Math.toRadians(deg));
      float sz = (float)Math.cos(Math.toRadians(deg));
      float c = (float)Math.cos(Math.toRadians(32.0));
      float s = (float)Math.sin(Math.toRadians(32.0));
      float dx = sx * c;
      float dz = sz * c;
      float bx = sx * 3.9F;
      float bz = sz * 3.9F;
      float ex = bx + dx * 3.05F;
      float ey = y + s * 3.05F;
      float ez = bz + dz * 3.05F;
      Shape branch = Shapes.cone(bx, y, bz, 0.98F, ex + dx * 0.4F, ey + s * 0.4F, ez + dz * 0.4F, 0.64F)
         .paint(Paint.wood(-1063542, -4158378, bx, y, bz, dx, s, dz, 0.3F));
      Paint rings = Paint.radial(-6264264, ex, ey, ez, 0.75F, -1389672);
      return Shapes.subtract(branch, Shapes.plane(ex, ey, ez, -dx, -s, -dz).paint(rings));
   }

   private static Shape eye(float side, Paint grain) {
      float x = side * 2.14F;
      float y = 28.72F;
      float ux = x / 5.0F;
      float uz = (float)Math.sqrt(1.0F - ux * ux);
      float r = radius(ux, y, uz) - 0.5F;
      float deg = (float)Math.toDegrees(Math.asin(ux));
      return Shapes.ellipsoid(ux * r, y, uz * r, 1.85F, 1.8F, 0.85F).rotate(0.0F, deg, 0.0F, ux * r, y, uz * r).paint(grain);
   }

   private static Shape knot(float deg, float y, float size) {
      float sx = (float)Math.sin(Math.toRadians(deg));
      float sz = (float)Math.cos(Math.toRadians(deg));
      float tip = radius(sx, y, sz) + 0.28F;
      float c = tip - size;
      return Shapes.ellipsoid(sx * c, y, sz * c, size, size * 1.35F, size).paint(Paint.radial(-11653867, sx * tip, y, sz * tip, size * 1.05F, -4158378));
   }

   private static Shape hole(float deg, float y, float size) {
      float sx = (float)Math.sin(Math.toRadians(deg));
      float sz = (float)Math.cos(Math.toRadians(deg));
      float tip = radius(sx, y, sz) + 0.28F;
      return Shapes.ellipsoid(sx * tip, y, sz * tip, size, size * 1.3F, size).color(-11653867);
   }

   private static float radius(float ux, float y, float uz) {
      float lump = 0.17F * Noise.noise(ux * 1.1F + 2.3F, y * 0.1F, uz * 1.1F - 1.3F);
      return 5.0F + lump - 0.17F * fissure(ux, y, uz);
   }

   private static float fissure(float ux, float y, float uz) {
      float n = Math.abs(Noise.noise(ux * 3.0F - 5.1F, y * 0.08F, uz * 3.0F + 0.7F));
      float face = smooth(0.3F, 0.62F, uz) * smooth(21.8F, 23.6F, y);
      return Math.max(0.0F, 1.0F - n / 0.28F) * (1.0F - 0.88F * face);
   }

   private static float trunk(float x, float y, float z) {
      float rho = (float)Math.sqrt(x * x + z * z);
      float ux = rho > 1.0E-4F ? x / rho : 0.0F;
      float uz = rho > 1.0E-4F ? z / rho : 1.0F;
      float qx = rho - radius(ux, y, uz) + 0.85F;
      float qy = Math.abs(y - 23.0F) - 11.0F + 0.85F;
      float ox = Math.max(qx, 0.0F);
      float oy = Math.max(qy, 0.0F);
      return (float)Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0.0F) - 0.85F;
   }

   private static Shape arm() {
      Paint wood = stick(-5.4F, 22.6F, 0.2F, -7.0F, 11.6F, 0.72F);
      Shape shoulder = Shapes.blend(0.2F, Shapes.sphere(-5.3F, 22.8F, 0.2F, 0.7F), Shapes.capsule(-5.3F, 22.8F, 0.2F, -3.9F, 22.8F, 0.2F, 0.55F));
      Shape upper = Shapes.cone(-5.4F, 22.5F, 0.2F, 0.64F, -6.55F, 17.3F, 0.12F, 0.54F);
      Shape elbow = Shapes.sphere(-6.57F, 17.1F, 0.14F, 0.6F);
      Shape fore = Shapes.cone(-6.6F, 16.85F, 0.14F, 0.55F, -6.93F, 12.6F, 0.5F, 0.5F);
      return Shapes.blend(0.3F, shoulder, upper, elbow, fore).paint(wood);
   }

   private static Shape hand() {
      float dx = KNUCKLE_DIR[0];
      float dy = KNUCKLE_DIR[1];
      float dz = KNUCKLE_DIR[2];
      Shape palm = Shapes.ellipsoid(-6.92F, 11.650001F, 0.70000005F, 0.86F, 1.05F, 0.9F);
      Shape wrist = Shapes.cone(-6.93F, 12.75F, 0.5F, 0.5F, -6.95F, 12.150001F, 0.72F, 0.64F);
      Shape[] knuckles = new Shape[4];

      for (int i = 0; i < 4; i++) {
         float t = -0.66F + i * 0.44F;
         float cx = -7.0F + dx * t - 0.28F;
         float cy = 11.6F + dy * t;
         float cz = 0.72F + dz * t + 0.72F;
         knuckles[i] = Shapes.capsule(cx - 0.35F, cy, cz - 0.3F, cx + 0.3F, cy, cz + 0.1F, 0.26F + 0.02F * (3 - i));
      }

      Shape thumb = Shapes.capsule(-6.38F, 12.1F, 0.92F, -6.82F, 12.320001F, 1.6700001F, 0.27F);
      return Shapes.blend(0.18F, Shapes.blend(0.3F, palm, wrist), Shapes.union(knuckles), thumb).paint(stick(-6.9F, 12.8F, 0.5F, -7.0F, 10.6F, 0.72F));
   }

   private static Shape leg() {
      Paint wood = stick(-2.2F, 12.8F, 0.0F, -2.35F, 1.4F, 0.0F);
      Shape hip = Shapes.sphere(-2.2F, 12.6F, 0.0F, 0.8F);
      Shape thigh = Shapes.cone(-2.2F, 12.4F, 0.0F, 0.74F, -2.3F, 7.0F, 0.12F, 0.62F);
      Shape knee = Shapes.sphere(-2.3F, 6.75F, 0.16F, 0.68F);
      Shape shin = Shapes.cone(-2.3F, 6.5F, 0.12F, 0.62F, -2.36F, 1.9F, 0.0F, 0.55F);
      return Shapes.blend(0.3F, hip, thigh, knee, shin).paint(wood);
   }

   private static Shape foot() {
      Shape sole = Shapes.blend(
         0.5F,
         Shapes.ellipsoid(-2.42F, 0.55F, 1.0F, 1.2F, 0.55F, 1.9F),
         Shapes.ellipsoid(-2.36F, 0.95F, -0.1F, 0.8F, 0.85F, 1.0F),
         Shapes.sphere(-2.36F, 1.75F, 0.0F, 0.55F)
      );
      Shape toes = Shapes.union(
         Shapes.sphere(-1.7F, 0.38F, 2.55F, 0.4F),
         Shapes.sphere(-2.2F, 0.35F, 2.8F, 0.36F),
         Shapes.sphere(-2.68F, 0.34F, 2.76F, 0.33F),
         Shapes.sphere(-3.12F, 0.33F, 2.52F, 0.31F)
      );
      return Shapes.intersect(Shapes.blend(0.22F, sole, toes), Shapes.above(0.0F)).paint(stick(-2.4F, 0.6F, -1.0F, -2.4F, 0.6F, 3.0F));
   }

   private static Paint stick(float ax, float ay, float az, float bx, float by, float bz) {
      return Paint.wood(-3567528, -7053254, ax, ay, az, bx - ax, by - ay, bz - az, 0.35F).grain(0.3F, 0.05F);
   }

   private static Decal eyes() {
      Canvas c = new Canvas(336, 208);

      for (int side = -1; side <= 1; side += 2) {
         float ex = 168.0F + side * 85.5F;
         float ey = 122.0F;
         c.fill(Shape2D.circle(ex, ey, 75.0F), 808458248, 13.0F);
         c.fill(Shape2D.circle(ex, ey, 66.0F), -15003638);
         c.fill(Shape2D.circle(ex, ey, 60.75F), Paint2D.radial(ex - 15.0F, ey - 18.0F, 78.0F, -1, -132105, -1054240));
         float px = ex - side * 1.5F;
         float py = ey + 1.0F;
         c.fill(Shape2D.circle(px, py, 12.0F), -15857658);
         c.fill(Shape2D.circle(px - 3.9F, py - 4.4F, 3.6F), -285212673);
      }

      Shape2D brow = Shape2D.curve(15.0F, 48.0F, 74.0F, 18.0F, 147.0F, 71.0F, 21.0F, 34.5F);
      c.fill(brow, -15003638);
      c.fill(brow.mirrorX(168.0F).rotate(6.0F, 321.0F, 48.0F).move(0.0F, -7.0F), -15003638);
      return Decal.front(c, 0.0F, 29.175F, 5.0F, 8.4F, 5.2F).depth(3.6F, 1.5F).maxAngle(72.0F).flatLight(1.0F).occlusion(0.0F);
   }

   private static Decal mouth() {
      Canvas c = new Canvas(272, 104);
      Shape2D mouth = Shape2D.ellipse(136.0F, 19.0F, 124.5F, 78.0F).subtract(Shape2D.circle(136.0F, -482.0F, 507.0F));
      c.fill(mouth.inflate(6.0F), -15003638);
      c.fill(mouth, Paint2D.linear(136.0F, 10.0F, 136.0F, 95.5F, -15071227, -12970996));
      c.fill(Shape2D.ellipse(136.0F, 98.5F, 75.0F, 39.0F).intersect(mouth), -3646898);
      return Decal.front(c, 0.0F, 24.675F, 5.0F, 6.8F, 2.6F).depth(3.6F, 1.5F).maxAngle(72.0F);
   }

   private static Decal rings() {
      Canvas c = new Canvas(168, 168);
      c.fill(Shape2D.circle(84.0F, 84.0F, 72.0F), Paint2D.radial(81.0F, 86.0F, 72.0F, -3565470, -1917818, -1521774, -2709138), 4.0F);
      Shape2D lines = Shape2D.field((x, y) -> {
         float dx = x - 81.0F;
         float dy = y - 86.0F;
         float rr = (float)Math.sqrt(dx * dx + dy * dy) + 0.001F;
         float w = rr + 2.5F * Noise.noise(dx / rr * 1.3F, dy / rr * 1.3F, rr * 0.028F);
         float k = w / 8.25F;
         float f = k - Math.round(k);
         return Math.max(Math.max(Math.abs(f) * 8.25F - 0.82F, rr - 67.5F), 6.7F - rr);
      }, 12.0F, 17.0F, 150.0F, 155.0F);
      c.fill(lines, -1602927328);
      c.fill(Shape2D.circle(81.0F, 86.0F, 3.6F), -9813984);
      c.fill(Shape2D.taper(87.0F, 81.0F, 3.0F, 133.0F, 52.0F, 1.0F), -1069538288);
      c.fill(Shape2D.taper(77.0F, 91.0F, 2.2F, 47.0F, 126.0F, 1.0F), -1337973744);
      return Decal.top(c, 0.0F, 34.0F, 0.0F, 9.678F, 9.678F).depth(0.6F, 0.6F).maxAngle(35.0F);
   }

   private static float smooth(float a, float b, float v) {
      float t = Math.max(0.0F, Math.min(1.0F, (v - a) / (b - a)));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float[] unit(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return new float[]{x / l, y / l, z / l};
   }

   @Environment(EnvType.CLIENT)
   private static final class Bark extends Paint {
      private final Paint base;

      Bark(Paint base) {
         this.base = base;
      }

      @Override
      public void apply(float x, float y, float z, Sample out) {
         this.base.apply(x, y, z, out);
         float rho = (float)Math.sqrt(x * x + z * z);
         if (!(rho < 0.5F)) {
            float side = TungTungSahur.smooth(3.95F, 4.7F, rho) * TungTungSahur.smooth(12.0F, 12.6F, y) * (1.0F - TungTungSahur.smooth(33.4F, 34.0F, y));
            float f = TungTungSahur.fissure(x / rho, y, z / rho) * side;
            if (f > 0.0F) {
               out.toward(-9551070, Math.min(1.0F, f * 0.62F));
            }
         }
      }
   }
}
