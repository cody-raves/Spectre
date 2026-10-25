package net.spectre.client.module.impl.render.character.designs;

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
public final class Tralalero extends CharacterDesign {
   private static final float YJ = 17.9F;
   private static final float PIVOT_Z = -1.5F;
   private static final float SHIFT = 0.8F;
   private static final float LEG_X = 2.9F;
   private static final float HIP = 12.0F;
   private static final float LEG_Z = 3.4F;
   private static final float KNEE = 6.4F;
   private static final float MID_HIP = 14.2F;
   private static final float MID_Z = -7.0F;
   private static final float SHOE_SCALE = 1.15F;
   private static final float[] BODY_Z = new float[]{-12.4F, -10.7F, -8.4F, -5.7F, -2.9F, 0.0F, 2.9F, 5.8F, 8.3F, 10.3F, 11.8F, 12.8F, 13.4F};
   private static final float[] BODY_Y = new float[]{1.4F, 1.15F, 0.8F, 0.4F, 0.1F, 0.0F, 0.05F, 0.25F, 0.5F, 0.75F, 0.95F, 1.05F, 1.1F};
   private static final float[] BODY_RY = new float[]{1.0F, 2.1F, 3.3F, 4.6F, 5.5F, 5.85F, 5.75F, 5.2F, 4.35F, 3.35F, 2.3F, 1.35F, 0.45F};
   private static final float[] BODY_RX = new float[]{0.9F, 1.8F, 2.9F, 4.2F, 5.1F, 5.45F, 5.35F, 4.8F, 3.95F, 3.0F, 2.1F, 1.25F, 0.45F};
   private static final float[] LINE_Z = new float[]{-14.5F, -11.0F, -8.0F, -5.0F, -2.0F, 1.0F, 3.5F, 5.5F, 7.0F, 9.0F, 11.0F, 14.5F};
   private static final float[] LINE_Y = new float[]{19.699999F, 19.1F, 18.1F, 17.1F, 16.4F, 16.3F, 16.9F, 17.65F, 17.4F, 17.6F, 18.1F, 18.4F};
   private static final float MOUTH_DEG = 152.0F;
   private static final int MOUTH_W = 284;
   private static final int MOUTH_H = 120;
   private static final float MOUTH_PX = 25.0F;
   private static final float MOUTH_Z = 9.45F;
   private static final float CORNER = 74.0F;
   private static final float LIP_FRONT = 11.0F;
   private static final float LIP_BACK = 7.4F;
   private static final int BACK_TOP = -12621165;
   private static final int BACK = -10515274;
   private static final int BELLY = -920330;
   private static final int FIN_TIP = -13346176;
   private static final int SKIN = -10055494;
   private static final int SKIN_LIGHT = -8674358;
   private static final int SKIN_DARK = -11634525;
   private static final int SHOE = -13933608;
   private static final int SHOE_DARK = -14989656;
   private static final int SHOE_LIGHT = -9593609;
   private static final int SOLE = -657935;
   private static final int SOLE_GREY = -4604218;
   private static final int LINE = -15194566;
   private static final int MOUTH = -12972264;
   private static final int GUM = -10872282;
   private static final int TEETH = -263436;

   public Tralalero() {
      super("tralalero", "Tralalero Tralala");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-15194566, 0.16F).occlusion(0.5F).firstPersonArms(false);
      rig.part(Bone.BODY).pivot(0.0F, 15.9F, 0.0F);
      Shape trunk = Shapes.field(Tralalero::loft, -5.8F, 11.7F, -14.6F, 5.8F, 24.099998F, 14.2F);
      Shape above = Shapes.field((x, y, z) -> bellyLine(z) - y, -6.0F, 10.9F, -14.0F, 6.0F, 24.9F, 15.0F);
      Shape body = Shapes.blend(1.1F, Shapes.intersect(0.5F, trunk, above).paint(Paint.field(Tralalero::skin)), dorsal()).move(0.0F, 0.0F, 0.8F);
      Canvas eyeArt = eyeCanvas();
      Canvas gillArt = gillCanvas();
      Canvas laceArt = laceCanvas();
      Canvas stripeArt = stripeCanvas();
      Part shark = rig.extra("shark", Bone.BODY)
         .pivot(0.0F, 17.9F, -1.5F)
         .shape(body)
         .resolution(1.2F)
         .detail(0.78F)
         .outline(0.1F)
         .neck(0.0F, 17.9F, 6.8F, 0.0F, 0.0F, 1.0F, 3.0F)
         .look(0.45F, 0.45F, 28.0F)
         .motion(Motion.none().idle(0.7F, 0.8F, 0.0F, 0.28F).walk(2.0F, 0.0F, 4.0F, 1.0F).follow(Bone.HEAD, 0.25F).spring(70.0F, 10.0F).limit(12.0F))
         .decal(eye(eyeArt, 1.0F))
         .decal(eye(eyeArt.mirrored(), -1.0F))
         .decal(gills(gillArt, 1.0F))
         .decal(gills(gillArt.mirrored(), -1.0F));
      Shape below = Shapes.field((x, y, z) -> y - bellyLine(z) - 0.4F, -6.0F, 10.9F, -14.0F, 6.0F, 24.9F, 15.0F);
      Shape shell = Shapes.intersect(trunk.inflate(-0.15F), below).paint(Paint.of(-920330).flatLight(0.4F).occlusion(0.35F)).move(0.0F, 0.0F, 0.8F);
      rig.extra("belly", shark).shape(shell).resolution(1.0F).detail(1.0F).decal(mouth());
      rig.extra("tail", shark)
         .pivot(0.0F, 19.199999F, -9.9F)
         .shape(caudal())
         .resolution(1.0F)
         .motion(
            Motion.tail().idle(1.0F, 9.0F, 0.0F, 0.45F).walk(2.0F, 14.0F, 0.0F, 1.0F).drag(5.0F).hang(0.3F).turn(0.0F, 16.0F).spring(40.0F, 5.5F).bend(1.2F)
         );
      Part finR = rig.part(Bone.RIGHT_ARM).pivot(-4.3F, 15.4F, 4.0F).grip(-6.8F, 13.4F, 2.7F).shape(pectoral()).resolution(0.9F).detail(0.8F).swing(0.35F);
      rig.part(Bone.LEFT_ARM).mirrorOf(finR);
      Part legR = rig.part(Bone.RIGHT_LEG)
         .pivot(-2.9F, 12.0F, 3.4F)
         .shape(leg(-2.9F, 12.0F, 3.4F, -1.0F))
         .resolution(1.0F)
         .detail(1.4F)
         .swing(0.5F, 35.0F)
         .joint(-2.8000002F, 6.5F, 3.5500002F, 0.8F);
      rig.part(Bone.LEFT_LEG).mirrorOf(legR);
      Part shoeR = rig.extra("shoe_r", Bone.RIGHT_LEG)
         .shape(shoe(-2.9F, 3.4F))
         .resolution(1.0F)
         .detail(1.6F)
         .decal(laces(laceArt, -2.9F, 3.4F))
         .decal(stripe(stripeArt, -2.9F, 3.4F, -1.0F));
      rig.extra("shoe_l", Bone.LEFT_LEG).mirrorOf(shoeR);
      Part mid = rig.extra("leg_mid", Bone.BODY)
         .pivot(0.0F, 14.2F, -7.0F)
         .shape(leg(0.0F, 14.2F, -7.0F, 1.0F))
         .resolution(1.0F)
         .detail(1.4F)
         .motion(Motion.none().follow(Bone.LEFT_LEG, 0.5F).walk(22.0F, 0.0F, 0.0F, 1.0F).hang(0.5F).limit(45.0F));
      rig.extra("shoe_mid", mid)
         .shape(shoe(0.0F, -7.0F))
         .resolution(1.0F)
         .detail(1.6F)
         .decal(laces(laceArt, 0.0F, -7.0F))
         .decal(stripe(stripeArt, 0.0F, -7.0F, -1.0F))
         .decal(stripe(stripeArt.mirrored(), 0.0F, -7.0F, 1.0F));
   }

   private static float smooth(float e0, float e1, float v) {
      float t = (v - e0) / (e1 - e0);
      t = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static int segment(float[] zs, float z) {
      int i = 0;

      while (i < zs.length - 2 && z > zs[i + 1]) {
         i++;
      }

      return i;
   }

   private static float spline(float[] zs, float[] v, int i, float z, boolean slope) {
      int n = zs.length;
      float h = zs[i + 1] - zs[i];
      float t = Math.max(0.0F, Math.min(1.0F, (z - zs[i]) / h));
      float p0 = v[Math.max(0, i - 1)];
      float p1 = v[i];
      float p2 = v[i + 1];
      float p3 = v[Math.min(n - 1, i + 2)];
      float a = 2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3;
      float b = 3.0F * p1 - p0 - 3.0F * p2 + p3;
      return slope ? 0.5F * (p2 - p0 + 2.0F * a * t + 3.0F * b * t * t) / h : 0.5F * (2.0F * p1 + (p2 - p0) * t + a * t * t + b * t * t * t);
   }

   private static float bellyLine(float z) {
      return spline(LINE_Z, LINE_Y, segment(LINE_Z, z), z, false);
   }

   private static float loft(float x, float y, float z) {
      int n = BODY_Z.length;
      if (z > BODY_Z[n - 1]) {
         float dy = y - 17.9F - BODY_Y[n - 1];
         float dz = z - BODY_Z[n - 1];
         return (float)Math.sqrt(x * x + dy * dy + dz * dz) - BODY_RY[n - 1];
      } else if (z < BODY_Z[0]) {
         float dy = y - 17.9F - BODY_Y[0];
         float dz = z - BODY_Z[0];
         return (float)Math.sqrt(x * x + dy * dy + dz * dz) - BODY_RX[0];
      } else {
         int i = segment(BODY_Z, z);
         float cy = 17.9F + spline(BODY_Z, BODY_Y, i, z, false);
         float ry = spline(BODY_Z, BODY_RY, i, z, false);
         float rx = spline(BODY_Z, BODY_RX, i, z, false);
         float sy = spline(BODY_Z, BODY_RY, i, z, true);
         float sx = spline(BODY_Z, BODY_RX, i, z, true);
         float sc = spline(BODY_Z, BODY_Y, i, z, true);
         float s = Math.max(Math.abs(sx), Math.abs(sy)) + Math.abs(sc);
         float px = x / rx;
         float py = (y - cy) / ry;
         float k0 = (float)Math.sqrt(px * px + py * py);
         float k1 = (float)Math.sqrt(px * px / (rx * rx) + py * py / (ry * ry));
         float d = k1 < 1.0E-6F ? -Math.min(rx, ry) : k0 * (k0 - 1.0F) / k1;
         return d / (float)Math.sqrt(1.0F + s * s);
      }
   }

   private static int skin(float x, float y, float z) {
      return Colors.mix(-10515274, -12621165, smooth(bellyLine(z) + 0.5F, 23.4F, y));
   }

   private static Shape2D outline(float dy, float... xy) {
      float[] p = (float[])xy.clone();

      for (int i = 1; i < p.length; i += 2) {
         p[i] += dy;
      }

      return Shape2D.polygon(p);
   }

   private static Shape blade(Shape2D profile, float cx, float cy, float w0, float s) {
      Shape slab = Shapes.extrude(profile, w0, w0);
      float n = (float)Math.sqrt(1.0F + s * s);
      return Shapes.field((x, y, z) -> {
         float r = (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
         return Math.max(slab.distance(x, y, z), (Math.abs(z) - w0 + s * r) / n);
      }, profile.minX(), profile.minY(), -w0, profile.maxX(), profile.maxY(), w0);
   }

   private static Shape side(Shape s) {
      return orient(s, 0.0F, 0.0F, 0.0F, new Vec3(0.0F, 0.0F, -1.0F), Vec3.UP);
   }

   private static Shape dorsal() {
      Shape2D first = outline(
         17.9F,
         -2.6F,
         4.2F,
         -1.9F,
         6.2F,
         -1.0F,
         7.9F,
         0.1F,
         9.4F,
         1.4F,
         10.6F,
         2.6F,
         11.5F,
         3.7F,
         12.1F,
         3.2F,
         11.2F,
         2.75F,
         10.0F,
         2.45F,
         8.7F,
         2.4F,
         7.4F,
         2.6F,
         6.2F,
         3.1F,
         5.0F,
         3.6F,
         4.2F
      );
      Shape2D second = outline(17.9F, 8.3F, 2.2F, 8.9F, 3.6F, 9.7F, 4.7F, 10.6F, 5.5F, 10.2F, 4.5F, 10.1F, 3.4F, 10.4F, 2.2F);
      Shape fins = Shapes.union(side(blade(first.inflate(0.1F), 0.4F, 22.5F, 1.5F, 0.125F)), side(blade(second, 9.4F, 20.699999F, 0.72F, 0.14F)));
      return fins.paint(Paint.vertical(-12621165, 22.9F, -13346176, 29.4F));
   }

   private static Shape caudal() {
      Shape2D lobes = outline(
         17.9F,
         12.0F,
         1.4F,
         11.7F,
         2.2F,
         12.4F,
         3.8F,
         13.2F,
         5.4F,
         14.0F,
         7.0F,
         14.8F,
         8.3F,
         15.4F,
         9.1F,
         15.0F,
         7.9F,
         14.6F,
         6.3F,
         14.3F,
         4.6F,
         14.1F,
         3.0F,
         14.2F,
         1.6F,
         14.4F,
         0.2F,
         14.7F,
         -1.3F,
         14.95F,
         -2.7F,
         15.2F,
         -3.9F,
         14.5F,
         -3.2F,
         13.6F,
         -2.2F,
         12.8F,
         -1.0F,
         11.9F,
         0.4F
      );
      Shape fin = side(blade(lobes.inflate(0.08F), 12.0F, 19.3F, 0.82F, 0.062F));
      Shape stem = Shapes.cone(0.0F, 19.1F, -10.3F, 1.5F, 0.0F, 19.3F, -12.5F, 1.0F);
      Shape keel = Shapes.ellipsoid(0.0F, 19.3F, -12.2F, 1.3F, 0.55F, 1.4F);
      return Shapes.blend(0.7F, stem, keel, fin).paint(Paint.radial(-10515274, 0.0F, 19.3F, -12.2F, 5.5F, -13346176)).move(0.0F, 0.0F, 0.8F);
   }

   private static Shape pectoral() {
      Shape2D profile = Shape2D.polygon(
         -1.2F,
         2.0F,
         0.5F,
         2.1F,
         2.0F,
         1.7F,
         3.5F,
         1.0F,
         5.0F,
         0.1F,
         6.3F,
         -0.9F,
         7.0F,
         -1.6F,
         6.0F,
         -1.5F,
         4.6F,
         -1.35F,
         3.2F,
         -1.3F,
         1.8F,
         -1.5F,
         0.4F,
         -1.9F,
         -1.2F,
         -2.2F
      );
      Shape fin = orient(blade(profile, 0.0F, 0.0F, 0.62F, 0.05F), -4.3F, 15.4F, 3.2F, new Vec3(-0.8F, -0.5F, -0.33F), new Vec3(0.25F, 0.05F, 1.0F));
      return fin.paint(Paint.radial(-10515274, -4.3F, 15.4F, 3.2F, 6.0F, -13346176)).move(0.0F, 0.0F, 0.8F);
   }

   private static Paint legPaint(float z0) {
      return Paint.field((x, y, z) -> {
         float front = smooth(z0 + 0.3F, z0 + 1.2F, z);
         float pit = smooth(1.3F, 0.3F, Math.abs(y - 6.4F)) * smooth(z0 - 0.2F, z0 - 0.8F, z);
         return Colors.mix(Colors.mix(-10055494, -8674358, front), -11634525, pit);
      });
   }

   private static Shape leg(float x, float hy, float z, float lat) {
      float k = 6.4F;
      float t = hy - 6.4F;
      Shape root = Shapes.capsule(x, hy, z, x, hy + 2.8F, z, 1.85F);
      Shape thigh = Shapes.cone(x, hy + 0.3F, z, 2.0F, x - lat * 0.15F, k + 0.9F, z + 0.15F, 1.12F);
      Shape quad = Shapes.ellipsoid(x + lat * 0.05F, k + 0.56F * t, z + 0.85F, 1.18F, 0.42F * t, 1.08F);
      Shape outer = Shapes.ellipsoid(x + lat * 0.7F, k + 0.55F * t, z, 0.85F, 0.45F * t, 1.05F);
      Shape inner = Shapes.ellipsoid(x - lat * 0.6F, k + 1.35F, z + 0.55F, 0.8F, 1.0F, 0.78F);
      Shape hams = Shapes.ellipsoid(x, k + 0.55F * t, z - 0.7F, 1.2F, 0.45F * t, 0.95F);
      Shape knee = Shapes.sphere(x - lat * 0.1F, k + 0.1F, z + 0.15F, 0.9F);
      Shape cap = Shapes.ellipsoid(x - lat * 0.05F, k + 0.25F, z + 0.95F, 0.55F, 0.62F, 0.4F);
      Shape shin = Shapes.cone(x - lat * 0.1F, k - 0.3F, z + 0.25F, 0.86F, x, 2.4F, z + 0.05F, 0.56F);
      Shape calfIn = Shapes.ellipsoid(x - lat * 0.36F, k - 1.55F, z - 0.62F, 0.84F, 1.55F, 0.98F);
      Shape calfOut = Shapes.ellipsoid(x + lat * 0.36F, k - 1.25F, z - 0.55F, 0.76F, 1.35F, 0.88F);
      Shape heel = Shapes.cone(x, k - 2.7F, z - 0.55F, 0.5F, x, 2.2F, z - 0.5F, 0.42F);
      Shape ankle = Shapes.sphere(x, 2.3F, z, 0.58F);
      Shape limb = Shapes.union(root, Shapes.blend(0.3F, thigh, quad, outer, inner, hams, knee, cap, shin, calfIn, calfOut, heel, ankle)).paint(legPaint(z));
      Shape sole = flat(foot(0.0F), x, 0.6F, z, 0.6F, 0.52F).paint(Paint.vertical(-4604218, 0.2F, -657935, 0.3F));
      return Shapes.union(limb, sole.scale(1.15F, x, 0.0F, z));
   }

   private static Shape orient(Shape s, float ox, float oy, float oz, Vec3 xAxis, Vec3 yHint) {
      Vec3 x = xAxis.normalize();
      Vec3 z = x.cross(yHint).normalize();
      Vec3 y = z.cross(x);
      float angle = (float)Math.acos(Math.max(-1.0F, Math.min(1.0F, (x.x() + y.y() + z.z() - 1.0F) * 0.5F)));
      Shape r = angle < 1.0E-4F ? s : s.rotateAxis(y.z() - z.y(), z.x() - x.z(), x.y() - y.x(), (float)Math.toDegrees(angle), 0.0F, 0.0F, 0.0F);
      return r.move(ox, oy, oz);
   }

   private static Shape flat(Shape2D profile, float x, float y, float z, float half, float round) {
      return orient(Shapes.extrude(profile, half, round), x, y, z, Vec3.LEFT, Vec3.FORWARD);
   }

   private static Shape2D foot(float inset) {
      Shape2D heel = Shape2D.circle(0.0F, -1.05F, 1.0F);
      Shape2D ball = Shape2D.circle(0.0F, 1.4F, 1.22F);
      Shape2D toe = Shape2D.circle(0.0F, 2.35F, 1.05F);
      Shape2D outline = Shape2D.blend(2.2F, heel, ball, toe);
      return inset > 0.0F ? outline.inflate(-inset) : outline;
   }

   private static Shape shoe(float x, float z) {
      Shape round = Shapes.blend(
         0.8F, Shapes.cone(x, 1.55F, z - 0.85F, 1.3F, x, 1.25F, z + 1.1F, 1.3F), Shapes.ellipsoid(x, 0.95F, z + 1.95F, 1.22F, 0.92F, 1.5F)
      );
      Paint upperPaint = Paint.field((px, py, pz) -> Colors.mix(-13933608, -14989656, smooth(z - 0.6F, z - 1.3F, pz)));
      Shape upper = Shapes.intersect(0.15F, round, flat(foot(0.1F), x, 2.0F, z, 1.3F, 0.25F)).paint(upperPaint);
      Shape collar = Shapes.torus(x, 2.95F, z - 0.5F, 0.78F, 0.34F).rotate(-14.0F, 0.0F, 0.0F, x, 2.95F, z - 0.5F).color(-14989656);
      Shape tongue = Shapes.ellipsoid(x, 2.9F, z + 0.35F, 0.52F, 0.4F, 0.7F).rotate(20.0F, 0.0F, 0.0F, x, 2.9F, z + 0.35F).color(-9593609);
      Shape tab = Shapes.ellipsoid(x, 3.15F, z - 1.25F, 0.26F, 0.45F, 0.2F).color(-14989656);
      return Shapes.blend(0.2F, upper, collar, tongue, tab).scale(1.15F, x, 0.0F, z);
   }

   private static Canvas laceCanvas() {
      Canvas c = new Canvas(60, 76);

      for (int i = 0; i < 4; i++) {
         float y = 6.5F + i * 20.25F;
         float w = 22.5F - i * 1.9F;
         c.fill(Shape2D.box(30.0F, y, w, 4.1F, 4.1F), -1);
         c.fill(Shape2D.circle(30.0F - w - 2.3F, y, 2.4F), -14274500);
         c.fill(Shape2D.circle(30.0F + w + 2.3F, y, 2.4F), -14274500);
      }

      return c;
   }

   private static Canvas stripeCanvas() {
      Canvas c = new Canvas(160, 30);
      c.fill(Shape2D.line(20.0F, 9.0F, 140.0F, 21.0F, 13.0F), -1);
      return c;
   }

   private static Decal stripe(Canvas c, float x, float z, float side) {
      float s = 1.15F;
      return Decal.project(c, x + side * 1.3F * s, 1.55F * s, z + 0.35F * s, side, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 3.6F * s, 0.68F * s)
         .depth(0.9F, 0.9F)
         .maxAngle(70.0F)
         .flatLight(0.6F)
         .occlusion(0.4F);
   }

   private static Decal laces(Canvas c, float x, float z) {
      float s = 1.15F;
      return Decal.project(c, x, 2.7F * s, z + 1.3F * s, 0.0F, 0.79F, 0.61F, 0.0F, 1.0F, 0.0F, 1.25F * s, 1.58F * s)
         .depth(0.9F, 0.9F)
         .flatLight(0.6F)
         .occlusion(0.4F);
   }

   private static Canvas eyeCanvas() {
      Canvas c = new Canvas(96, 96);
      float ex = 48.0F;
      float ey = 50.0F;
      Shape2D ball = Shape2D.ellipse(ex, ey, 21.0F, 20.0F);
      c.fill(ball.inflate(4.0F), 1428832344, 3.0F);
      c.fill(ball, Paint2D.radial(ex - 4.5F, ey - 6.0F, 26.0F, -14011322, -16316147));
      c.fill(Shape2D.circle(ex - 7.5F, ey - 7.0F, 5.6F), -1);
      c.fill(Shape2D.circle(ex + 7.5F, ey + 8.0F, 2.4F), -1325400065);
      c.fill(Shape2D.arc(ex, ey + 2.0F, 22.0F, 200.0F, 340.0F, 4.0F), -2144713630, 2.0F);
      return c;
   }

   private static Decal eye(Canvas c, float s) {
      return Decal.project(c, s * 3.3F, 20.1F, 10.2F, s * 0.84F, 0.36F, 0.4F, 0.0F, 1.0F, 0.0F, 2.2F, 2.2F).depth(1.2F, 1.2F).maxAngle(75.0F).flatLight(0.7F);
   }

   private static Canvas gillCanvas() {
      Canvas c = new Canvas(120, 120);

      for (int i = 0; i < 5; i++) {
         float gx = 16.0F + i * 22.0F;
         float len = 42.0F - i * 3.0F;
         c.fill(Shape2D.curve(gx, 58.0F - len, gx + 7.0F, 58.0F, gx, 58.0F + len, 3.4F, 3.4F), -802799780);
      }

      return c;
   }

   private static Decal gills(Canvas c, float s) {
      return Decal.project(c, s * 5.1F, 18.5F, 6.0F, s, 0.05F, 0.2F, 0.0F, 1.0F, 0.0F, 2.8F, 2.8F).depth(1.5F, 1.5F);
   }

   private static float mouthX(float a) {
      return 142.0F + a * 284.0F / 152.0F;
   }

   private static float arch(float a) {
      float k = Math.min(1.0F, Math.abs(a) / 74.0F);
      float z = 11.0F - 3.6F * (float)Math.pow(k, 1.8);
      return 60.0F - (z - 9.45F) * 25.0F;
   }

   private static float gape(float a) {
      float k = Math.min(1.0F, Math.abs(a) / 74.0F);
      return 15.000001F * (1.0F - k * k);
   }

   private static Decal mouth() {
      Canvas c = new Canvas(284, 120);
      float px2deg = 0.53521127F;
      float[] mid = new float[284];
      float[] half = new float[284];
      float[] norm = new float[284];

      for (int i = 0; i < 284; i++) {
         float a = (i + 0.5F - 142.0F) * px2deg;
         float slope = (arch(a + 0.5F) - arch(a - 0.5F)) * px2deg;
         mid[i] = arch(a);
         half[i] = Math.abs(a) > 75.0F ? -1.0F : gape(a);
         norm[i] = 1.0F / (float)Math.sqrt(1.0F + slope * slope);
      }

      Shape2D open = Shape2D.field((px, py) -> {
         int ix = Math.max(0, Math.min(283, (int)px));
         return half[ix] < 0.0F ? 4.0F : (Math.abs(py - mid[ix]) - half[ix]) * norm[ix];
      }, mouthX(-76.0F), 3.0F, mouthX(76.0F), 117.0F);
      c.fill(open, Paint2D.linear(0.0F, 0.0F, 0.0F, 120.0F, -10872282, -12972264));
      float lip = 0.75F;
      float least = 2.5F;

      for (int i = -4; i <= 4; i++) {
         float a = i * 15.0F;
         float hh = gape(a);
         if (!(hh < least)) {
            Shape2D top = Shape2D.polygon(
               mouthX(a - 6.0F), arch(a - 6.0F) - gape(a - 6.0F) - lip, mouthX(a + 6.0F), arch(a + 6.0F) - gape(a + 6.0F) - lip, mouthX(a), arch(a) + hh * 0.5F
            );
            c.fill(top.intersect(open), -263436);
            float b = a + 7.5F;
            float bh = gape(b);
            if (!(bh < least) && i != 4) {
               Shape2D bottom = Shape2D.polygon(
                  mouthX(b - 5.0F),
                  arch(b - 5.0F) + gape(b - 5.0F) + lip,
                  mouthX(b + 5.0F),
                  arch(b + 5.0F) + gape(b + 5.0F) + lip,
                  mouthX(b),
                  arch(b) - bh * 0.35F
               );
               c.fill(bottom.intersect(open), -1185056);
            }
         }
      }

      c.fill(open.outline(2.5F), -15194566);
      return Decal.wrap(c, 0.0F, 18.35F, 10.25F, 0.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 152.0F, 4.8F).maxAngle(80.0F).flatLight(1.0F).occlusion(0.3F);
   }
}
