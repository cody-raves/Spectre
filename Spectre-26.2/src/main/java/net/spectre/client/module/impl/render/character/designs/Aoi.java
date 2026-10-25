package net.spectre.client.module.impl.render.character.designs;

import java.util.ArrayList;
import java.util.Arrays;
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
public final class Aoi extends CharacterDesign {
   private static final float TAU = (float) (Math.PI * 2);
   private static final float NECK = 24.4F;
   private static final float HEAD_K = 1.16F;
   private static final float HEAD_DY = -0.25F;
   private static final float FACE_X = 2.25F;
   private static final float FACE_TOP = 28.6F;
   private static final float FACE_LOW = 26.08F;
   private static final float FACE_PX = 80.0F;
   private static final float YOKE = 21.75F;
   private static final float BEND = 13.0F;
   private static final int SKIN = -9274;
   private static final int SKIN_SHADE = -1331558;
   private static final int SKIN_LINE = -6657438;
   private static final int HAIR = -14140794;
   private static final int HAIR_DEEP = -15063975;
   private static final int HAIR_TOP = -12756032;
   private static final int HAIR_BLUE = -12948262;
   private static final int HAIR_TIP = -13246209;
   private static final int HAIR_INNER = -13646610;
   private static final int HAIR_SHINE = -7363350;
   private static final int HAIR_LINE = -15327158;
   private static final int CYAN = -13703425;
   private static final int NAVY = -14932394;
   private static final int JACKET = -12959662;
   private static final int JACKET_DARK = -14210250;
   private static final int JACKET_WHITE = -1446155;
   private static final int JACKET_SHADE = -3945254;
   private static final int LINE = -15065558;
   private static final int TOP = -525828;
   private static final int TOP_SHADE = -2498584;
   private static final int SKIRT = -12892555;
   private static final int SKIRT_DARK = -14472632;
   private static final int BLACK = -15263457;
   private static final int SILVER = -3550757;
   private static final int SOCK = -14012606;
   private static final int SOLE = -920586;
   private static final float SKIRT_ZC = -0.05F;
   private static final float SKIRT_TOP = 17.3F;
   private static final float SKIRT_HEM = 11.25F;
   private static final int PLEATS = 18;
   private static final float[][] MAIN_LOCKS = new float[][]{
      {0.95F, -1.55F, 29.85F, -2.0F, 29.5F, -2.3F, 28.45F, -2.24F, 27.65F, -2.0F, 27.42F},
      {0.95F, -1.15F, 29.95F, -1.38F, 29.5F, -1.6F, 28.6F, -1.55F, 28.12F, -1.3F, 27.93F},
      {0.85F, -0.64F, 29.95F, -0.45F, 29.4F, -0.08F, 28.25F, 0.16F, 27.4F, 0.04F, 27.02F},
      {1.0F, -0.08F, 30.0F, 0.35F, 29.6F, 0.9F, 28.7F, 1.2F, 28.22F, 0.98F, 28.0F},
      {1.05F, 0.56F, 30.0F, 1.15F, 29.7F, 1.8F, 28.75F, 2.08F, 27.8F, 1.85F, 27.45F}
   };
   private static final float[][] ACCENT_LOCKS = new float[][]{
      {0.55F, -0.95F, 29.85F, -1.0F, 29.3F, -0.9F, 28.5F, -0.76F, 28.3F},
      {0.55F, -0.22F, 29.85F, 0.14F, 29.35F, 0.52F, 28.72F, 0.44F, 28.55F},
      {0.5F, 0.5F, 29.85F, 1.02F, 29.4F, 1.5F, 28.5F, 1.44F, 28.2F}
   };

   public Aoi() {
      super("aoi", "Aoi");
   }

   @Override
   protected void define(Rig rig) {
      rig.scale(1.0F).outline(-15065558, 0.13F).occlusion(0.35F);
      Canvas stripe = stripe();
      Shape head = headShape();
      List<float[]> main = fringeLocks(head, MAIN_LOCKS, 0.2F, 0.17F);
      List<float[]> accent = fringeLocks(head, ACCENT_LOCKS, 0.2F, 0.17F);
      rig.part(Bone.HEAD)
         .pivot(0.0F, 24.4F, 0.0F)
         .shape(grow(head.color(-9274)))
         .detail(1.1F)
         .resolution(0.85F)
         .outline(-6657438, 0.09F)
         .flatLight(1.0F)
         .occlusion(0.2F)
         .decal(face(main));
      hair(rig, main, accent);
      ponytail(rig);
      Shape jacket = body(rig, stripe);
      skirt(rig, stripe);
      arms(rig, stripe);
      legs(rig);
      gear(rig, jacket);
   }

   private static Shape grow(Shape s) {
      return s.scale(1.16F, 0.0F, 24.4F, 0.0F).move(0.0F, -0.25F, 0.0F);
   }

   private static float hy(float y) {
      return 24.4F + (y - 24.4F) * 1.16F + -0.25F;
   }

   private static Shape headShape() {
      Shape skull = Shapes.ellipsoid(0.0F, 28.52F, -0.15F, 2.28F, 2.24F, 2.32F);
      Shape cheek = Shapes.ellipsoid(0.0F, 27.4F, 0.7F, 1.95F, 1.1F, 1.45F);
      Shape jaw = Shapes.cone(0.0F, 27.25F, 0.42F, 1.68F, 0.0F, 26.15F, 1.18F, 0.55F);
      Shape jawline = Shapes.extrude(
         Shape2D.polygon(
            -2.6F,
            31.5F,
            -2.6F,
            29.0F,
            -2.24F,
            27.6F,
            -2.11F,
            27.2F,
            -1.9F,
            26.8F,
            -1.62F,
            26.44F,
            -1.28F,
            26.12F,
            -0.92F,
            25.91F,
            -0.54F,
            25.79F,
            -0.25F,
            25.75F,
            0.0F,
            25.74F,
            0.25F,
            25.75F,
            0.54F,
            25.79F,
            0.92F,
            25.91F,
            1.28F,
            26.12F,
            1.62F,
            26.44F,
            1.9F,
            26.8F,
            2.11F,
            27.2F,
            2.24F,
            27.6F,
            2.6F,
            29.0F,
            2.6F,
            31.5F
         ),
         4.0F,
         0.0F
      );
      Shape face = Shapes.blend(0.9F, skull, cheek, jaw);
      Shape cut = face.intersect(0.3F, jawline);
      return Shapes.field((x, y, z) -> {
         float d = y > 28.8F ? face.distance(x, y, z) : cut.distance(x, y, z);
         return Math.max(d, Math.max(-1.0F - z, y - 30.1F));
      }, face.minX(), face.minY(), -1.1F, face.maxX(), 30.2F, face.maxZ());
   }

   private static List<float[]> fringeLocks(Shape head, float[][] data, float mid, float tip) {
      List<float[]> out = new ArrayList<>();

      for (float[] k : data) {
         int n = (k.length - 1) / 2;
         float[] lock = new float[n * 4];

         for (int i = 0; i < n; i++) {
            float x = k[1 + i * 2];
            float y = k[2 + i * 2];
            float u = 1.0F - x * x / 6.76F - (y - 28.62F) * (y - 28.62F) / 7.08F;
            float z = Math.max(frontZ(head, x, y, 5.0F), -0.5F + 2.64F * (float)Math.sqrt(Math.max(0.0F, u)));
            float dy = y - 28.3F;
            float dz = z + 0.4F;
            float l = (float)Math.sqrt(x * x + dy * dy + dz * dz);
            float t = i / (n - 1.0F);
            float lift = (i == 0 ? 0.1F : (i == n - 1 ? tip : mid)) + 0.22F * smooth(1.3F, 2.2F, Math.abs(x));
            lock[i * 4] = x + x / l * lift;
            lock[1 + i * 4] = y + dy / l * lift;
            lock[2 + i * 4] = z + dz / l * lift;
            lock[3 + i * 4] = k[0] * (float)Math.pow(1.0F - t, 1.2F) + 0.05F * t;
         }

         out.add(lock);
      }

      return out;
   }

   private static void hair(Rig rig, List<float[]> main, List<float[]> accent) {
      List<Shape> parts = new ArrayList<>();
      parts.add(
         Shapes.subtract(0.35F, Shapes.ellipsoid(0.0F, 28.62F, -0.2F, 2.6F, 2.66F, 2.64F), Shapes.ellipsoid(0.0F, 26.75F, 2.75F, 2.45F, 2.85F, 2.72F))
            .intersect(Shapes.above(26.0F))
      );

      for (float a : new float[]{-155.0F, -118.0F, -80.0F, 80.0F, 118.0F, 155.0F}) {
         parts.add(flowLock(a));
      }

      rig.extra("hair", Bone.HEAD)
         .shape(grow(Shapes.union(parts.toArray(new Shape[0])).paint(capPaint())))
         .detail(0.8F)
         .resolution(0.95F)
         .outline(-15327158, 0.1F)
         .flatLight(0.5F)
         .occlusion(0.4F);
      Part bangs = rig.extra("fringe", Bone.HEAD)
         .shape(grow(fringe(main, accent)))
         .detail(0.4F)
         .resolution(0.55F)
         .outline(-15327158, 0.09F)
         .flatLight(0.5F)
         .occlusion(0.2F);
      shine(bangs, main);
      Motion lockMotion = Motion.hair()
         .idle(1.5F, 0.0F, 1.5F, 0.35F)
         .walk(3.0F, 0.0F, 3.0F, 2.0F)
         .drag(8.0F)
         .hang(0.7F)
         .turn(10.0F, 0.0F)
         .spring(60.0F, 7.0F)
         .bend(1.3F);
      Paint lockPaint = Paint.field((x, y, z) -> Colors.mix(Colors.mix(-14140794, -12948262, smooth(28.2F, 25.8F, y)), -13246209, smooth(26.2F, 24.4F, y)));
      Paint innerPaint = Paint.field((x, y, z) -> Colors.mix(-13646610, -13246209, smooth(27.0F, 25.0F, y)));
      Shape lockR = Shapes.union(
         Shapes.tube(
               -1.55F,
               29.95F,
               1.0F,
               0.4F,
               -2.32F,
               28.5F,
               1.4F,
               0.55F,
               -2.55F,
               27.1F,
               1.3F,
               0.52F,
               -2.48F,
               25.9F,
               1.15F,
               0.42F,
               -2.3F,
               24.9F,
               1.2F,
               0.28F,
               -2.1F,
               24.3F,
               1.34F,
               0.04F
            )
            .flattenFrom(2.0F, 0.0F, 27.5F, -0.5F)
            .paint(lockPaint),
         Shapes.tube(
               -1.7F, 29.7F, 0.7F, 0.36F, -2.28F, 28.2F, 1.05F, 0.44F, -2.36F, 26.8F, 0.95F, 0.42F, -2.22F, 25.6F, 0.9F, 0.3F, -2.05F, 24.55F, 1.0F, 0.04F
            )
            .flattenFrom(2.0F, 0.0F, 27.5F, -0.5F)
            .paint(innerPaint)
      );
      Part right = rig.extra("lock_r", Bone.HEAD)
         .pivot(-2.32F, hy(29.2F), 1.16F)
         .shape(grow(lockR))
         .motion(lockMotion)
         .detail(0.7F)
         .resolution(0.7F)
         .outline(-15327158, 0.1F)
         .flatLight(0.5F)
         .occlusion(0.35F);
      rig.extra("lock_l", Bone.HEAD).mirrorOf(right);
   }

   private static Shape flowLock(float angleDeg) {
      float cx = 0.0F;
      float cy = 28.62F;
      float cz = -0.2F;
      float rx = 2.6F;
      float ry = 2.66F;
      float rz = 2.64F;
      float a = (float)Math.toRadians(angleDeg);
      float abs = Math.abs(angleDeg);
      float y0 = 29.1F - 1.7F * smooth(20.0F, 90.0F, abs) - 1.2F * smooth(95.0F, 160.0F, abs);
      float e = (float)Math.asin(Math.max(-0.95F, Math.min(0.95F, (y0 - cy) / ry)));
      float ux = (float)(Math.sin(a) * Math.cos(e));
      float uy = (float)Math.sin(e);
      float uz = (float)(Math.cos(a) * Math.cos(e));
      float tx = 0.0F;
      float ty = 30.25F - cy;
      float tz = -1.95F - cz;
      float tl = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
      tx /= tl;
      ty /= tl;
      tz /= tl;
      float[] f = new float[]{0.0F, 0.35F, 0.7F, 1.0F};
      float[] radius = new float[]{0.5F, 0.56F, 0.5F, 0.32F};
      float[] pts = new float[16];

      for (int i = 0; i < 4; i++) {
         float dx = ux + (tx - ux) * f[i];
         float dy = uy + (ty - uy) * f[i];
         float dz = uz + (tz - uz) * f[i];
         float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         dx /= l;
         dy /= l;
         dz /= l;
         float r = 1.0F / (float)Math.sqrt(dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rz * rz));
         float lift = i == 3 ? -0.1F : 0.04F;
         pts[i * 4] = cx + dx * (r + lift);
         pts[i * 4 + 1] = cy + dy * (r + lift);
         pts[i * 4 + 2] = cz + dz * (r + lift);
         pts[i * 4 + 3] = radius[i];
      }

      return Shapes.tube(pts).flattenFrom(2.4F, cx, cy, cz);
   }

   private static Paint capPaint() {
      return Paint.field((x, y, z) -> Colors.mix(Colors.mix(-15063975, -14140794, smooth(26.2F, 28.4F, y)), -12756032, smooth(29.4F, 31.2F, y) * 0.8F));
   }

   private static Shape fringe(List<float[]> main, List<float[]> accent) {
      Shape[] parts = new Shape[main.size() + accent.size()];

      for (int i = 0; i < parts.length; i++) {
         boolean top = i >= main.size();
         float[] k = top ? accent.get(i - main.size()) : main.get(i);
         int n = k.length / 4;
         float[] xy = new float[n * 2];

         for (int j = 0; j < n; j++) {
            xy[j * 2] = k[j * 4];
            xy[j * 2 + 1] = k[j * 4 + 1];
         }

         float tint = top ? 1.12F : ((i & 1) == 0 ? 0.93F : 1.04F);
         parts[i] = Shapes.tube(k)
            .flattenFrom(2.6F, 0.0F, 28.4F, -0.3F)
            .intersect(Shapes.extrude(Shape2D.path(k[3] * 2.6F, 0.02F, xy), 4.0F, 0.0F))
            .paint(
               Paint.field(
                  (x, y, z) -> Colors.shade(
                     Colors.mix(Colors.mix(-14140794, -12756032, smooth(29.6F, 30.6F, y) * 0.5F), -12948262, smooth(28.9F, 27.4F, y) * 0.85F), tint
                  )
               )
            );
      }

      return Shapes.union(parts);
   }

   private static void shine(Part bangs, List<float[]> locks) {
      for (int i = 0; i < locks.size(); i++) {
         float[] k = locks.get(i);
         int n = k.length / 4;

         for (int j = 1; j < n; j++) {
            float y0 = k[j * 4 - 3];
            float y1 = k[j * 4 + 1];
            if (!(y0 < 29.15F) && !(y1 > 29.15F)) {
               float t = (y0 - 29.15F) / (y0 - y1);
               float x = k[j * 4 - 4] + (k[j * 4] - k[j * 4 - 4]) * t;
               float z = k[j * 4 - 2] + (k[j * 4 + 2] - k[j * 4 - 2]) * t;
               float r = k[j * 4 - 1] + (k[j * 4 + 3] - k[j * 4 - 1]) * t;
               float mid = (float)Math.atan2(x, z + 0.2F);
               if (!(Math.abs(mid) > 0.96F)) {
                  float span = r * 1.1F / (float)Math.sqrt(x * x + (z + 0.2F) * (z + 0.2F));
                  float w = 7.0F + (float)Math.toDegrees(span) * 0.25F;
                  float px = 64.0F / (span * 1.5F);
                  float[] a = new float[6];
                  float[] b = new float[6];

                  for (int q = 0; q < 3; q++) {
                     float da = span * 0.6F * (q * 0.5F - 1.0F);
                     float db = span * 0.6F * q * 0.5F;
                     a[q * 2] = 32.0F + da * px;
                     a[q * 2 + 1] = 13.0F + 25.0F * ((float)Math.cos(mid) - (float)Math.cos(mid + da));
                     b[q * 2] = 32.0F + db * px;
                     b[q * 2 + 1] = 13.0F + 25.0F * ((float)Math.cos(mid) - (float)Math.cos(mid + db));
                  }

                  Canvas c = new Canvas(64, 26);
                  c.fill(Shape2D.path(w * 0.2F, w, a).union(Shape2D.path(w, w * 0.2F, b)), Colors.withAlpha(-7363350, 0.5F), 5.0F);
                  float cy = hy(29.1F) + 0.081F - 0.29F * (1.0F - (float)Math.cos(mid)) + ((i & 1) * 2 - 1) * 0.03F;
                  bangs.decal(
                     Decal.wrap(
                           c,
                           0.0F,
                           cy,
                           -0.232F,
                           0.0F,
                           1.0F,
                           0.0F,
                           (float)Math.sin(mid),
                           0.0F,
                           (float)Math.cos(mid),
                           (float)Math.toDegrees(span * 1.5F),
                           0.30159998F
                        )
                        .radius(1.8F, 4.2F)
                        .flatLight(0.8F)
                  );
               }
            }
         }
      }
   }

   private static void ponytail(Rig rig) {
      float[][] spine = new float[][]{
         {30.4F, -2.0F},
         {31.1F, -2.5F},
         {31.35F, -3.25F},
         {30.95F, -3.95F},
         {29.7F, -4.3F},
         {27.6F, -4.38F},
         {25.1F, -4.22F},
         {22.5F, -4.0F},
         {19.9F, -3.8F},
         {17.5F, -3.68F},
         {15.3F, -3.74F}
      };
      int n = spine.length;
      float[] spread = new float[]{0.0F, 0.55F, 1.0F, 1.18F, 1.22F, 1.18F, 1.08F, 0.96F, 0.86F, 0.98F, 1.35F};
      float[] profile = new float[]{0.45F, 0.88F, 1.05F, 1.1F, 1.1F, 1.05F, 0.98F, 0.86F, 0.7F, 0.52F, 0.34F};
      float[] ny = new float[n];
      float[] nz = new float[n];

      for (int j = 0; j < n; j++) {
         int a = Math.max(0, j - 1);
         int b = Math.min(n - 1, j + 1);
         float ty = spine[b][0] - spine[a][0];
         float tz = spine[b][1] - spine[a][1];
         float tl = (float)Math.sqrt(ty * ty + tz * tz);
         ny[j] = tz / tl;
         nz[j] = -ty / tl;
      }

      float[][] locks = new float[][]{
         {-1.02F, 0.25F, 10.1F, 0.68F, 0.0F, -1.0F},
         {-0.5F, -0.3F, 10.25F, 0.72F, 1.1F, -1.0F},
         {0.02F, 0.3F, 10.8F, 0.74F, 2.3F, 0.0F},
         {0.54F, -0.28F, 9.7F, 0.72F, 0.6F, 1.0F},
         {1.03F, 0.22F, 9.55F, 0.66F, 1.7F, 1.0F},
         {-0.72F, 0.62F, 9.4F, 0.62F, 2.9F, -1.0F},
         {0.74F, 0.6F, 9.5F, 0.62F, 0.3F, 1.0F},
         {-0.05F, -0.62F, 10.6F, 0.64F, 2.0F, 0.0F}
      };
      List<Shape> strands = new ArrayList<>();

      for (float[] k : locks) {
         int whole = Math.min((int)Math.floor(k[2]), n - 1);
         float frac = k[2] - whole;
         float[] pts = new float[(whole + 2) * 4];

         for (int j = 0; j <= whole; j++) {
            float s = spread[j];
            float g = smooth(6.5F, 8.6F, j);
            float wave = 0.14F * (float)Math.sin(j * 0.9F + k[4]) * smooth(1.0F, 4.0F, j) * (1.0F - g);
            float x = k[0] * s + wave;
            float off = k[1] * s * 0.75F * (1.0F - 0.6F * g);
            pts[j * 4] = x + (k[5] * 1.15F * s + k[0] * 0.2F * s - x) * g;
            pts[j * 4 + 1] = spine[j][0] + ny[j] * off;
            pts[j * 4 + 2] = spine[j][1] + nz[j] * off;
            pts[j * 4 + 3] = k[3] * 1.08F * profile[j];
         }

         int o = (whole + 1) * 4;
         int next = Math.min(whole + 1, n - 1);
         float f = Math.max(frac, 0.45F);
         pts[o] = pts[whole * 4] * 1.12F + k[5] * 0.2F;
         pts[o + 1] = spine[whole][0] + (spine[next][0] - spine[whole][0]) * f - 1.1F;
         pts[o + 2] = pts[whole * 4 + 2] + (spine[next][1] - spine[whole][1]) * f - 0.1F;
         pts[o + 3] = 0.04F;
         split(strands, pts, 6, 1.6F, true);
      }

      float[] core = new float[36];

      for (int j = 0; j < 9; j++) {
         core[j * 4 + 1] = spine[j][0];
         core[j * 4 + 2] = spine[j][1];
         core[j * 4 + 3] = 0.64F * profile[j];
      }

      split(strands, core, 5, 1.4F, false);
      float[][] spray = new float[][]{
         {0.05F, 30.55F, -2.15F, 0.1F, 31.45F, -2.6F, 0.15F, 32.05F, -3.35F, 0.1F, 31.85F, -4.15F},
         {0.3F, 30.5F, -2.1F, 0.72F, 31.25F, -2.7F, 0.98F, 31.72F, -3.45F, 1.08F, 31.42F, -4.05F},
         {-0.35F, 30.5F, -2.1F, -0.76F, 31.2F, -2.75F, -1.02F, 31.62F, -3.4F, -1.12F, 31.32F, -4.0F}
      };

      for (float[] k : spray) {
         strands.add(
            Shapes.tube(k[0], k[1], k[2], 0.36F, k[3], k[4], k[5], 0.44F, k[6], k[7], k[8], 0.3F, k[9], k[10], k[11], 0.04F)
               .flattenFrom(1.8F, 0.0F, 26.0F, 0.5F)
         );
      }

      Paint paint = Paint.field((xx, y, z) -> {
         int c = Colors.mix(-14140794, -12948262, smooth(27.5F, 21.5F, y));
         c = Colors.mix(c, -13246209, smooth(21.5F, 16.0F, y));
         float s = smooth(30.9F, 31.5F, y) * smooth(-2.4F, -3.2F, z);
         return Colors.mix(c, -7363350, s * 0.5F);
      });
      Shape tail = Shapes.union(strands.toArray(new Shape[0])).paint(paint);
      Shape tie = ring(0.0F, 30.38F, -2.05F, 0.0F, 0.55F, -0.83F, 0.52F, 0.38F).roughen(0.05F, 0.3F).color(-13703425);
      rig.extra("ponytail", Bone.HEAD)
         .pivot(0.0F, hy(30.35F), -2.3779998F)
         .shape(grow(Shapes.union(tail, tie)))
         .detail(1.0F)
         .resolution(1.3F)
         .outline(-15327158, 0.12F)
         .flatLight(0.4F)
         .occlusion(0.4F)
         .motion(
            Motion.hair()
               .idle(3.0F, 0.0F, 2.5F, 0.3F)
               .walk(7.0F, 0.0F, 5.0F, 2.0F)
               .drag(22.0F)
               .hang(0.3F)
               .follow(Bone.BODY, 0.6F)
               .turn(18.0F, 4.0F)
               .spring(36.0F, 4.2F)
               .bend(1.5F)
               .limit(70.0F)
         );
   }

   private static void split(List<Shape> out, float[] pts, int at, float flat, boolean sharp) {
      int n = pts.length / 4;
      if (n < at + 3) {
         out.add(Shapes.tube(pts).flattenFrom(flat, 0.0F, 26.0F, 0.5F));
      } else {
         out.add(Shapes.tube(Arrays.copyOfRange(pts, 0, (at + 2) * 4)).flattenFrom(flat, 0.0F, 26.0F, 0.5F));
         Shape end = Shapes.tube(Arrays.copyOfRange(pts, (at - 1) * 4, n * 4)).flattenFrom(flat, 0.0F, 26.0F, 0.5F);
         if (sharp) {
            int a = (n - 3) * 4;
            int b = (n - 2) * 4;
            int c = (n - 1) * 4;
            Shape2D tip = Shape2D.path(2.4F, 0.02F, pts[a], pts[a + 1], pts[b], pts[b + 1], pts[c], pts[c + 1]);
            end = end.intersect(Shapes.union(Shapes.above(pts[b + 1]), Shapes.extrude(tip, 8.0F, 0.0F)));
         }

         out.add(end);
      }
   }

   private static Shape body(Rig rig, Canvas stripe) {
      Paint skin = Paint.field((x, y, z) -> Colors.mix(-9274, -1331558, smooth(24.9F, 25.5F, y))).flatLight(1.0F).occlusion(0.1F);
      Shape neck = Shapes.cone(0.0F, 23.2F, -0.22F, 0.66F, 0.0F, 26.3F, 0.0F, 0.58F).paint(skin);
      Shape top = Shapes.blend(
            1.0F,
            Shapes.ellipsoid(0.0F, 21.15F, 0.0F, 2.25F, 2.05F, 1.45F),
            Shapes.ellipsoid(0.0F, 18.7F, 0.0F, 1.78F, 1.75F, 1.22F),
            Shapes.ellipsoid(0.0F, 16.6F, -0.05F, 2.0F, 1.5F, 1.4F),
            Shapes.capsule(-2.2F, 22.3F, -0.18F, 0.0F, 22.62F, -0.18F, 0.88F),
            Shapes.capsule(0.0F, 22.62F, -0.18F, 2.2F, 22.3F, -0.18F, 0.88F)
         )
         .blend(0.6F, Shapes.ellipsoid(0.82F, 20.85F, 0.55F, 0.88F, 0.82F, 0.85F).mirrorX());
      top.paint(Paint.vertical(-2498584, 17.2F, -525828, 19.4F).flatLight(0.4F));
      Shape mock = Shapes.cone(0.0F, 23.0F, -0.2F, 0.9F, 0.0F, 25.3F, -0.12F, 0.74F).intersect(0.06F, Shapes.below(25.1F)).color(-15263457);
      Shape pelvis = Shapes.ellipsoid(0.0F, 14.95F, -0.05F, 1.95F, 1.2F, 1.35F).color(-15263457);
      Shape jacketVol = Shapes.blend(
            1.1F,
            Shapes.ellipsoid(0.0F, 21.0F, -0.1F, 2.7F, 2.4F, 1.98F),
            Shapes.capsule(-2.62F, 22.25F, -0.22F, 0.0F, 22.6F, -0.22F, 1.04F),
            Shapes.capsule(0.0F, 22.6F, -0.22F, 2.62F, 22.25F, -0.22F, 1.04F),
            oval(-0.1F, 18.3F, 20.3F, 2.45F, 1.97F, 2.55F, 1.97F)
         )
         .paint(Paint.field((x, y, z) -> Colors.mix(-12959662, -1446155, smooth(21.72F, 21.78F, y))).flatLight(0.3F).occlusion(0.18F));
      Shape hem = oval(-0.1F, 17.42F, 18.45F, 2.45F, 2.03F, 2.47F, 2.02F).color(-14210250);
      Shape collar = Shapes.torus(0.0F, 23.52F, -0.28F, 1.28F, 0.38F).rotate(16.0F, 0.0F, 0.0F, 0.0F, 23.52F, -0.28F).color(-14210250);
      Shape jacketSolid = Shapes.union(jacketVol, hem, collar);
      Shape jacket = Shapes.subtract(0.06F, jacketSolid, opening(0.0F));
      float kz = frontZ(jacketSolid, -1.85F, 18.95F, 6.0F);
      float by = 16.95F;
      float e = flare(skirtT(by));
      float brx = 1.85F + 1.6F * e + 0.22F;
      float brz = 1.5F + 1.4F * e + 0.22F;
      Shape belt = ovalRing(-0.05F, by, 0.42F, brx, brz, 0.26F).color(-15263457);
      float bz = -0.05F + brz * (float)Math.sqrt(1.0F - 0.3025F / (brx * brx)) + 0.26F;
      Shape buckle = Shapes.box(0.55F, by, bz + 0.05F, 0.46F, 0.36F, 0.14F, 0.1F).color(-13749702);
      Part body = rig.part(Bone.BODY)
         .pivot(0.0F, 24.4F, 0.0F)
         .shape(Shapes.union(neck, mock, top, pelvis, jacket, belt, buckle))
         .detail(1.0F)
         .outline(-15065558, 0.13F)
         .occlusion(0.3F);
      Canvas back = backStripe();
      Canvas band = band();
      body.decal(topGraphic())
         .decal(Decal.back(back, 2.02F, 19.6F, -2.0F, 0.6F, 4.0F).depth(1.4F, 1.4F))
         .decal(Decal.back(back, -2.02F, 19.6F, -2.0F, 0.6F, 4.0F).depth(1.4F, 1.4F))
         .decal(Decal.wrap(stripe, 0.0F, 18.12F, -0.1F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.3F).radius(1.95F, 3.2F))
         .decal(Decal.front(pocket(), -1.85F, 18.95F, kz, 0.95F, 0.36F).depth(0.5F, 0.5F).maxAngle(70.0F))
         .decal(Decal.front(pocket().mirrored(), 1.85F, 18.95F, kz, 0.95F, 0.36F).depth(0.5F, 0.5F).maxAngle(70.0F))
         .decal(Decal.front(band, 0.0F, 17.62F, 1.9F, 2.0F, 0.28F).depth(1.2F, 0.4F).maxAngle(70.0F))
         .decal(Decal.wrap(band, 0.0F, 24.8F, -0.14F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 240.0F, 0.26F).radius(0.4F, 1.05F))
         .decal(Decal.front(buckle(), 0.55F, by, bz + 0.19F, 0.92F, 0.72F).depth(0.4F, 0.3F).maxAngle(60.0F));
      Shape strap = Shapes.union(
         Shapes.tube(0.98F, 16.7F, bz + 0.02F, 0.28F, 1.02F, 15.8F, bz + 0.26F, 0.28F, 1.05F, 14.9F, bz + 0.45F, 0.27F, 1.07F, 14.3F, bz + 0.55F, 0.26F)
            .flatten(2.2F, 0.0F, 0.0F, 1.0F)
            .color(-15263457),
         Shapes.box(1.07F, 14.15F, bz + 0.56F, 0.28F, 0.2F, 0.13F, 0.08F).color(-3550757)
      );
      rig.extra("strap", Bone.BODY)
         .pivot(0.98F, 16.7F, bz)
         .shape(strap)
         .resolution(0.7F)
         .detail(0.3F)
         .noOutline()
         .motion(Motion.cloth().walk(8.0F, 0.0F, 5.0F, 2.0F).drag(6.0F).spring(40.0F, 5.0F).bend(1.2F));
      return jacketSolid;
   }

   private static float openingWidth(float y) {
      return 0.85F + 0.65F * (24.9F - y) / 10.1F;
   }

   private static Shape opening(float grow) {
      return Shapes.extrude(Shape2D.polygon(-0.85F - grow, 24.9F, 0.85F + grow, 24.9F, 1.5F + grow, 14.8F, -1.5F - grow, 14.8F), 2.6F, 0.0F)
         .move(0.0F, 0.0F, 3.15F);
   }

   private static Shape piping(Shape jacket, float side, float y0, float y1) {
      float[] pts = new float[24];

      for (int i = 0; i < 8; i++) {
         float y = y0 + (y1 - y0) * i / 7.0F;
         float w = openingWidth(y);
         pts[i * 3] = side * (w + 0.09F);
         pts[i * 3 + 1] = y;
         pts[i * 3 + 2] = frontZ(jacket, side * (w + 0.1F), y, 6.0F) - 0.06F;
      }

      return Shapes.strand(0.13F, 0.13F, pts);
   }

   private static void skirt(Rig rig, Canvas stripe) {
      Paint paint = Paint.field((x, y, z) -> {
         float theta = (float)Math.atan2(x, z - -0.05F);
         float p = pleat(theta * 18.0F / (float) (Math.PI * 2));
         return Colors.mix(-14472632, -12892555, 0.4F + 0.6F * (0.5F + 0.5F * p));
      }).flatLight(0.15F);
      Decal hem = Decal.wrap(stripe, 0.0F, 11.77F, -0.05F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.27F).radius(2.4F, 4.6F);
      float half = (float)Math.toRadians(30.0);
      float tuck = (float)Math.toRadians(14.0);
      String[] names = new String[]{"skirt_f", "skirt_s", "skirt_b"};
      float[] angle = new float[]{-30.0F, -90.0F, -150.0F};
      float[] follow = new float[]{0.5F, 0.42F, 0.5F};
      float[] limit = new float[]{16.0F, 13.0F, 15.0F};

      for (int i = 0; i < 3; i++) {
         float a = (float)Math.toRadians(angle[i]);
         float px = 1.8F * (float)Math.sin(a);
         float pz = -0.05F + 1.45F * (float)Math.cos(a);
         Motion m = Motion.skirt(Bone.RIGHT_LEG).follow(Bone.RIGHT_LEG, follow[i], limit[i]).drag(i == 0 ? 0.0F : 3.0F);
         Part right = rig.extra(names[i] + "_r", Bone.BODY)
            .pivot(px, 16.9F, pz)
            .shape(skirtPanel(a, half, tuck).paint(paint))
            .motion(m)
            .detail(0.32F)
            .resolution(1.0F)
            .occlusion(0.3F)
            .noOutline()
            .decal(hem);
         rig.extra(names[i] + "_l", Bone.BODY).mirrorOf(right);
      }
   }

   private static float skirtT(float y) {
      return clamp01((17.3F - y) / 6.049999F);
   }

   private static float flare(float t) {
      return t * (1.25F - 0.25F * t);
   }

   private static float skirtRadius(float x, float dz, float rho, float theta, float y) {
      float t = skirtT(y);
      float e = flare(t);
      float rx = 1.85F + 1.6F * e;
      float rz = 1.5F + 1.4F * e;
      float q = (float)Math.sqrt(rz * rz * x * x + rx * rx * dz * dz);
      float re = q < 1.0E-5F ? rx : rx * rz * rho / q;
      return re + (0.05F + 0.24F * t) * pleat(theta * 18.0F / (float) (Math.PI * 2));
   }

   private static Shape skirtPanel(float centre, float half, float tuck) {
      float x0 = 0.0F;
      float x1 = 0.0F;
      float z0 = -0.05F;
      float z1 = -0.05F;

      for (int i = 0; i <= 16; i++) {
         float a = centre - half - tuck + (half + tuck) * 2.0F * i / 16.0F;

         for (float r : new float[]{1.4F, 4.05F}) {
            x0 = Math.min(x0, r * (float)Math.sin(a));
            x1 = Math.max(x1, r * (float)Math.sin(a));
            z0 = Math.min(z0, -0.05F + r * (float)Math.cos(a));
            z1 = Math.max(z1, -0.05F + r * (float)Math.cos(a));
         }
      }

      return Shapes.field((x, y, z) -> {
         float dz = z - -0.05F;
         float theta = (float)Math.atan2(x, dz);
         float rel = theta - centre;
         if (rel > Math.PI) {
            rel -= (float) (Math.PI * 2);
         }

         if (rel < -Math.PI) {
            rel += (float) (Math.PI * 2);
         }

         float out = Math.abs(rel) - half;
         float rho = (float)Math.sqrt(x * x + dz * dz);
         float rx = skirtRadius(x, dz, rho, theta, y) - 0.32F * smooth(0.0F, 0.05F, out);
         float shell = Math.abs(rho - rx) - 0.42F;
         float cap = Math.max(y - 17.3F, 11.25F - y);
         float ax = Math.max(-1.2F, Math.min(1.2F, out - tuck));
         float edge = rho * ax * (1.0F - ax * ax / 6.0F);
         return Math.max(shell, Math.max(cap, edge)) * 0.8F;
      }, x0 - 0.2F, 11.05F, z0 - 0.2F, x1 + 0.2F, 17.5F, z1 + 0.2F);
   }

   private static void arms(Rig rig, Canvas stripe) {
      float ex = -3.68F;
      float ey = 18.4F;
      float ez = -0.1F;
      float ax = ex + 3.3F;
      float ay = ey - 21.8F;
      float az = ez + 0.12F;
      float al = (float)Math.sqrt(ax * ax + ay * ay + az * az);
      ax /= al;
      ay /= al;
      az /= al;
      Shape sleeveBody = Shapes.blend(0.45F, Shapes.sphere(-3.22F, 22.2F, -0.15F, 1.1F), Shapes.cone(-3.3F, 21.8F, -0.12F, 1.07F, ex, ey, ez, 0.96F));
      Shape sleeve = sleeveBody.intersect(Shapes.plane(ex, ey, ez, ax, ay, az).color(-14210250))
         .paint(Paint.field((x, y, z) -> Colors.mix(-1446155, -3945254, smooth(-3.2F, -2.6F, x) * 0.7F)).flatLight(0.3F));
      float rx = ex - ax * 0.05F;
      float ry = ey - ay * 0.05F;
      float rz = ez - az * 0.05F;
      Shape roll = ring(rx, ry, rz, ax, ay, az, 0.86F, 0.34F).color(-14210250);
      Paint skin = Paint.of(-9274).flatLight(1.0F).occlusion(0.25F);
      Shape forearm = Shapes.cone(ex, ey - 0.1F, ez + 0.05F, 0.55F, -3.92F, 14.66F, 0.15F, 0.39F).paint(skin).rotate(-13.0F, 0.0F, 0.0F, ex, ey, ez);
      float tilt = -1.5F;
      float c = (float)Math.cos(Math.toRadians(tilt));
      float s = (float)Math.sin(Math.toRadians(tilt));
      float tx = -3.3F + (rx + 3.3F) * c - (ry - 22.4F) * s;
      float ty = 22.4F + (rx + 3.3F) * s + (ry - 22.4F) * c;
      float bx = ax * c - ay * s;
      float by = ax * s + ay * c;
      float[] grip = bent(-4.15F, 12.75F, 0.45F, ex, ey, ez);
      float gx = -3.3F + (grip[0] + 3.3F) * c - (grip[1] - 22.4F) * s;
      float gy = 22.4F + (grip[0] + 3.3F) * s + (grip[1] - 22.4F) * c;
      Part right = rig.part(Bone.RIGHT_ARM)
         .pivot(-3.3F, 22.4F, -0.1F)
         .grip(gx, gy, grip[2])
         .shape(Shapes.union(sleeve, roll, forearm).rotate(0.0F, 0.0F, tilt, -3.3F, 22.4F, -0.1F))
         .outline(-12959918, 0.12F)
         .occlusion(0.3F)
         .swing(0.8F)
         .joint(-3.78F, 18.41F, -0.1F, 0.55F);
      Part left = rig.part(Bone.LEFT_ARM).mirrorOf(right);
      right.decal(Decal.wrap(stripe, tx, ty, rz, -bx, -by, -az, -1.0F, 0.0F, 0.3F, 250.0F, 0.4F).radius(0.95F, 1.6F));
      left.decal(Decal.wrap(stripe, -tx, ty, rz, bx, -by, -az, 1.0F, 0.0F, 0.3F, 250.0F, 0.4F).radius(0.95F, 1.6F));
      left.decal(Decal.left(sleevePatch(), 4.45F, 20.3F, -0.1F, 1.05F, 1.05F).depth(0.8F, 0.8F).maxAngle(65.0F));
      right.decal(Decal.right(sleeveText(), -4.5F, 20.2F, -0.1F, 0.62F, 1.75F).depth(0.8F, 0.8F).maxAngle(65.0F));
      Part hand = rig.extra("hand_r", Bone.RIGHT_ARM)
         .shape(hand().rotate(-13.0F, 0.0F, 0.0F, ex, ey, ez).rotate(0.0F, 0.0F, tilt, -3.3F, 22.4F, -0.1F))
         .resolution(0.45F)
         .detail(0.5F)
         .outline(-6657438, 0.1F)
         .flatLight(1.0F)
         .occlusion(0.25F);
      rig.extra("hand_l", Bone.LEFT_ARM).mirrorOf(hand);
      Part wrist = rig.extra("wrist_r", Bone.RIGHT_ARM)
         .shape(
            Shapes.ellipsoid(-3.97F, 14.12F, 0.16F, 0.31F, 0.5F, 0.37F)
               .color(-9274)
               .rotate(-13.0F, 0.0F, 0.0F, ex, ey, ez)
               .rotate(0.0F, 0.0F, tilt, -3.3F, 22.4F, -0.1F)
         )
         .resolution(0.6F)
         .detail(1.4F)
         .outline(-6657438, 0.1F)
         .flatLight(1.0F)
         .occlusion(0.25F);
      rig.extra("wrist_l", Bone.LEFT_ARM).mirrorOf(wrist);
   }

   private static float[] bent(float x, float y, float z, float ex, float ey, float ez) {
      double a = Math.toRadians(-13.0);
      float c = (float)Math.cos(a);
      float s = (float)Math.sin(a);
      float py = y - ey;
      float pz = z - ez;
      return new float[]{x, ey + py * c - pz * s, ez + py * s + pz * c};
   }

   private static Shape hand() {
      Shape palm = Shapes.ellipsoid(-4.0F, 13.2F, 0.14F, 0.3F, 0.68F, 0.54F);
      Shape thumb = Shapes.strand(0.2F, 0.15F, -3.86F, 13.35F, 0.6F, -3.72F, 12.9F, 0.92F, -3.62F, 12.5F, 0.98F);
      Shape fingers = Shapes.union(
         Shapes.strand(0.17F, 0.15F, -3.98F, 12.72F, 0.56F, -3.92F, 12.1F, 0.72F, -3.8F, 11.66F, 0.8F),
         Shapes.strand(0.17F, 0.15F, -4.0F, 12.74F, 0.24F, -3.88F, 12.12F, 0.28F, -3.66F, 11.8F, 0.3F),
         Shapes.strand(0.165F, 0.145F, -4.0F, 12.72F, -0.08F, -3.88F, 12.18F, -0.08F, -3.66F, 11.88F, -0.06F),
         Shapes.strand(0.15F, 0.13F, -3.99F, 12.66F, -0.36F, -3.88F, 12.3F, -0.36F, -3.7F, 12.06F, -0.33F)
      );
      return Shapes.union(
         Shapes.blend(0.12F, palm, thumb, fingers).color(-9274), Shapes.cylinder(-3.95F, 14.22F, 0.18F, -3.93F, 14.9F, 0.15F, 0.5F, 0.1F).color(-13703425)
      );
   }

   private static void legs(Rig rig) {
      Shape thigh = Shapes.cone(-1.24F, 14.1F, 0.0F, 1.3F, -1.1F, 8.3F, 0.1F, 0.84F);
      Shape knee = Shapes.sphere(-1.07F, 7.68F, 0.18F, 0.75F);
      Shape kneecap = Shapes.sphere(-1.06F, 7.8F, 0.5F, 0.52F);
      Shape calf = Shapes.cone(-1.07F, 7.3F, 0.06F, 0.78F, -1.12F, 2.6F, -0.1F, 0.5F);
      Shape muscle = Shapes.ellipsoid(-1.12F, 5.8F, -0.2F, 0.72F, 1.5F, 0.74F);
      Shape hip = Shapes.sphere(-1.1F, 14.4F, -0.05F, 1.32F);
      Paint legPaint = Paint.field((x, y, z) -> Colors.mix(Colors.mix(-14012606, -9274, smooth(6.7F, 6.95F, y)), -15263457, smooth(12.2F, 12.4F, y)));
      Shape leg = Shapes.union(Shapes.blend(0.5F, thigh, knee, calf, muscle).blend(0.3F, kneecap), hip).paint(legPaint);
      Shape sock = Shapes.union(Shapes.blend(0.5F, calf, muscle).inflate(0.1F).intersect(Shapes.below(6.95F)), Shapes.torus(-1.075F, 6.9F, 0.0F, 0.86F, 0.15F))
         .color(-14012606);
      Part right = rig.part(Bone.RIGHT_LEG)
         .pivot(-1.2F, 14.3F, 0.0F)
         .shape(leg)
         .outline(-11914188, 0.12F)
         .flatLight(0.7F)
         .occlusion(0.3F)
         .swing(0.55F, 30.0F)
         .joint(-1.07F, 7.68F, 0.18F, 0.75F);
      rig.part(Bone.LEFT_LEG).mirrorOf(right);
      Part sockR = rig.extra("sock_r", Bone.RIGHT_LEG).shape(sock).resolution(0.7F).detail(0.45F).outline(-15460836, 0.12F);
      Part sockL = rig.extra("sock_l", Bone.LEFT_LEG).mirrorOf(sockR);
      Canvas socks = socks();
      sockR.decal(Decal.wrap(socks, -1.1F, 6.42F, -0.05F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.3F).radius(0.3F, 1.4F));
      sockL.decal(Decal.wrap(socks, 1.1F, 6.42F, -0.05F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 360.0F, 0.3F).radius(0.3F, 1.4F));
      float fx = -1.14F;
      float turn = -7.0F;
      Part shoeR = rig.extra("shoe_r", Bone.RIGHT_LEG)
         .shape(shoe(fx).rotate(0.0F, turn, 0.0F, fx, 1.0F, 0.3F))
         .resolution(0.8F)
         .detail(0.55F)
         .outline(-12959664, 0.12F);
      Part shoeL = rig.extra("shoe_l", Bone.LEFT_LEG).mirrorOf(shoeR);
      Canvas laces = laces();
      double a = Math.toRadians(turn);
      float c = (float)Math.cos(a);
      float s = (float)Math.sin(a);
      float lx = fx + 0.65F * s;
      float lz = 0.3F + 0.65F * c;
      shoeR.decal(Decal.project(laces, lx, 2.1F, lz, 0.87F * s, 0.5F, 0.87F * c, -0.5F * s, 0.87F, -0.5F * c, 0.8F, 1.28F).depth(0.7F, 0.7F).maxAngle(75.0F));
      shoeL.decal(Decal.project(laces, -lx, 2.1F, lz, -0.87F * s, 0.5F, 0.87F * c, 0.5F * s, 0.87F, -0.5F * c, 0.8F, 1.28F).depth(0.7F, 0.7F).maxAngle(75.0F));
   }

   private static Shape shoe(float fx) {
      Shape sole = Shapes.blend(
         0.35F,
         Shapes.box(fx, 0.42F, 0.4F, 0.88F, 0.42F, 1.45F, 0.36F),
         Shapes.ellipsoid(fx, 0.44F, 1.62F, 0.88F, 0.44F, 0.95F),
         Shapes.ellipsoid(fx, 0.44F, -0.85F, 0.84F, 0.44F, 0.7F)
      );
      Shape toe = Shapes.ellipsoid(fx, 1.12F, 1.35F, 0.76F, 0.58F, 1.1F);
      Shape instep = Shapes.capsule(fx, 1.3F, 0.85F, fx, 2.05F, 0.1F, 0.7F);
      Shape heel = Shapes.ellipsoid(fx, 1.45F, -0.62F, 0.8F, 0.82F, 0.82F);
      Shape collar = Shapes.union(
         Shapes.cone(fx, 1.8F, -0.3F, 0.84F, fx, 3.5F, -0.17F, 0.74F).intersect(Shapes.below(3.2F)), Shapes.torus(fx, 3.18F, -0.19F, 0.66F, 0.15F)
      );
      Shape tongue = Shapes.ellipsoid(fx, 3.05F, 0.42F, 0.46F, 0.72F, 0.3F);
      Shape upper = Shapes.blend(0.4F, toe, instep, heel, collar).blend(0.15F, tongue);
      Paint paint = Paint.field(
         (x, y, z) -> y < 0.9F ? Colors.mix(-14012872, -920586, smooth(0.05F, 0.24F, y)) : Colors.mix(-591621, -14210250, smooth(2.8F, 3.05F, y))
      );
      return Shapes.union(sole, upper).paint(paint);
   }

   private static void gear(Rig rig, Shape jacket) {
      List<Shape> parts = new ArrayList<>();
      Canvas glow = phoneRing();
      Part part = rig.extra("gear", Bone.BODY);

      for (int s = -1; s <= 1; s += 2) {
         float ax = s * 0.35F;
         float ay = 0.45F;
         float az = 0.82F;
         float l = (float)Math.sqrt(ax * ax + ay * ay + az * az);
         ax /= l;
         ay /= l;
         az /= l;
         float cx = s * 1.55F;
         float cy = 23.5F;
         float cz = 1.12F;
         float py = ay;
         float px = ax, pz = az;
         Shape shell = Shapes.cylinder(cx - ax * 0.1F, cy - ay * 0.1F, cz - az * 0.1F, cx + ax * 0.16F, cy + ay * 0.16F, cz + az * 0.16F, 0.86F, 0.12F)
            .paint(Paint.field((x, y, z) -> {
               float t = (x - cx) * px + (y - cy) * py + (z - cz) * pz;
               return Colors.mix(-14210250, -723206, smooth(0.03F, 0.1F, t));
            }));
         Shape cushion = Shapes.cylinder(cx - ax * 0.36F, cy - ay * 0.36F, cz - az * 0.36F, cx - ax * 0.08F, cy - ay * 0.08F, cz - az * 0.08F, 0.78F, 0.1F)
            .color(-14210250);
         Shape arm = Shapes.capsule(cx - ax * 0.1F - s * 0.1F, cy + 0.8F, cz - az * 0.1F - 0.2F, s * 1.3F, 24.4F, 0.5F, 0.2F).color(-14210250);
         parts.add(Shapes.union(shell, cushion, arm));
         part.decal(
            Decal.project(glow, cx + ax * 0.17F, cy + ay * 0.17F, cz + az * 0.17F, ax, ay, az, 0.0F, 1.0F, 0.0F, 1.3F, 1.3F)
               .depth(0.4F, 0.4F)
               .maxAngle(70.0F)
               .glow()
         );
      }

      parts.add(
         Shapes.tube(
               1.3F,
               24.4F,
               0.5F,
               0.24F,
               1.22F,
               24.85F,
               -0.35F,
               0.26F,
               0.72F,
               25.02F,
               -1.2F,
               0.26F,
               0.0F,
               25.08F,
               -1.42F,
               0.26F,
               -0.72F,
               25.02F,
               -1.2F,
               0.26F,
               -1.22F,
               24.85F,
               -0.35F,
               0.26F,
               -1.3F,
               24.4F,
               0.5F,
               0.24F
            )
            .flattenFrom(1.8F, 0.0F, 24.9F, -0.2F)
            .color(-14210250)
      );
      parts.add(Shapes.union(piping(jacket, 1.0F, 23.3F, 17.5F), piping(jacket, -1.0F, 23.3F, 17.5F)).color(-13703425));
      part.shape(Shapes.union(parts.toArray(new Shape[0]))).resolution(0.7F).detail(0.7F).outline(-15065820, 0.1F).flatLight(0.7F).occlusion(0.15F);
      float[] path = new float[]{1.75F, 22.65F, 1.85F, 21.8F, 1.92F, 20.9F, 1.95F, 20.15F};
      float[] cable = new float[12];

      for (int i = 0; i < 4; i++) {
         cable[i * 3] = path[i * 2];
         cable[i * 3 + 1] = path[i * 2 + 1];
         cable[i * 3 + 2] = i == 0 ? 1.3F : frontZ(jacket, path[i * 2], path[i * 2 + 1], 6.0F) + 0.16F;
      }

      float jz = cable[11];
      Shape plug = Shapes.union(
         Shapes.strand(0.13F, 0.13F, cable).color(-7563613),
         Shapes.capsule(1.95F, 20.2F, jz, 1.96F, 19.65F, jz + 0.02F, 0.19F).color(-12959662),
         Shapes.capsule(1.96F, 19.6F, jz + 0.02F, 1.96F, 19.3F, jz + 0.03F, 0.12F).color(-3550757)
      );
      rig.extra("cable", Bone.BODY)
         .pivot(1.75F, 22.65F, 1.3F)
         .shape(plug)
         .resolution(0.5F)
         .detail(0.25F)
         .noOutline()
         .motion(Motion.cloth().idle(1.0F, 0.0F, 1.5F, 0.35F).walk(4.0F, 0.0F, 5.0F, 2.0F).drag(-2.0F).hang(0.4F).spring(45.0F, 5.0F).bend(1.1F));
   }

   private static float fu(float x) {
      return (x + 2.25F) * 80.0F;
   }

   private static float fv(float y) {
      return (28.6F - y) * 80.0F;
   }

   private static Decal face(List<float[]> locks) {
      Canvas c = new Canvas(Math.round(360.0F), Math.round(201.60004F));

      for (float[] k : locks) {
         Shape2D shadow = null;

         for (int i = 0; i + 7 < k.length; i += 4) {
            if (!(k[i + 1] > 29.2F) || !(k[i + 5] > 29.2F)) {
               Shape2D seg = Shape2D.taper(
                  fu(k[i]), fv(k[i + 1] - 0.22F), k[i + 3] * 80.0F * 1.7F, fu(k[i + 4]), fv(k[i + 5] - 0.22F), Math.max(2.0F, k[i + 7] * 80.0F * 1.5F)
               );
               shadow = shadow == null ? seg : shadow.union(seg);
            }
         }

         if (shadow != null) {
            c.fill(shadow, 2028380826, 3.0F);
         }
      }

      for (int side = -1; side <= 1; side += 2) {
         eye(c, side);
      }

      c.fill(Shape2D.taper(fu(0.06F), fv(26.8F), 4.0F, fu(-0.02F), fv(26.64F), 2.0F), -1714123650, 0.0F);
      c.fill(Shape2D.curve(fu(-0.22F), fv(26.2F), fu(0.0F), fv(26.1F), fu(0.2F), fv(26.23F), 3.4F, 2.2F), -7455931, 0.0F);

      for (int side = -1; side <= 1; side += 2) {
         c.fill(Shape2D.ellipse(fu(side * 1.25F), fv(26.42F), 32.0F, 11.0F), 1442807955, 10.0F);
      }

      float cy = 27.34F;
      return Decal.front(c, 0.0F, hy(cy), 3.132F, 5.22F, 2.9232004F).depth(3.0159998F, 0.8F).maxAngle(72.0F).flatLight(1.0F).occlusion(0.3F);
   }

   private static void eye(Canvas c, int side) {
      float ex = fu(side * 0.98F);
      float ey = fv(27.2F);
      float ix = ex - side * 2.0F;
      float iy = ey + 4.0F;
      float inX = ex - side * 46.0F;
      float outX = ex + side * 47.0F;
      float inY = ey - 40.0F;
      float outY = ey - 50.0F;
      float midX = ex - side * 2.0F;
      float midY = ey - 49.0F;
      Shape2D lid = Shape2D.polygon(
         inX - side * 30.0F,
         inY + 2.0F,
         inX,
         inY,
         midX,
         midY,
         outX,
         outY,
         outX + side * 30.0F,
         outY,
         outX + side * 30.0F,
         ey + 90.0F,
         inX - side * 30.0F,
         ey + 90.0F
      );
      Shape2D white = Shape2D.ellipse(ex, ey - 2.0F, 47.0F, 53.0F).intersect(lid);
      c.fill(white, Paint2D.linear(ex, ey - 50.0F, ex, ey + 50.0F, -2498062, -1, -1));
      Shape2D iris = Shape2D.ellipse(ix, iy, 32.0F, 43.0F);
      Shape2D inIris = iris.intersect(white);
      c.fill(inIris, Paint2D.linear(ix, iy - 43.0F, ix, iy + 43.0F, -16050107, -15449954, -14711060, -10687489));
      c.fill(Shape2D.ellipse(ix, iy + 25.0F, 22.0F, 14.0F).intersect(inIris), -1871645953, 6.0F);
      c.fill(Shape2D.ellipse(ix, iy + 3.0F, 12.0F, 19.0F), -16315098, 0.0F);
      c.fill(iris.outline(4.5F).intersect(white), -536210893, 0.0F);
      c.fill(Shape2D.ellipse(ex, ey - 44.0F, 62.0F, 20.0F).intersect(white), 1714438256, 8.0F);
      c.fill(Shape2D.box(ix - 12.0F, iy - 15.0F, 6.5F, 10.5F, 3.5F).rotate(-14.0F, ix - 12.0F, iy - 15.0F), -1, 0.0F);
      c.fill(Shape2D.box(ix - 2.0F, iy - 20.0F, 2.2F, 5.0F, 1.5F).rotate(-14.0F, ix - 2.0F, iy - 20.0F), -855638017, 0.0F);
      c.fill(Shape2D.circle(ix + 13.0F, iy + 21.0F, 4.2F), -570425345, 0.0F);
      int ink = -15724002;
      c.fill(Shape2D.path(5.0F, 9.0F, inX, inY + 1.0F, midX, midY - 1.0F, outX, outY), ink, 0.0F);
      c.fill(Shape2D.taper(outX - side * 6.0F, outY + 1.0F, 7.5F, outX + side * 12.0F, outY - 7.0F, 1.5F), ink, 0.0F);
      c.fill(Shape2D.curve(ex + side * 6.0F, ey + 51.0F, ex + side * 32.0F, ey + 46.0F, outX - side * 3.0F, ey + 22.0F, 1.2F, 3.0F), -1072030672, 0.0F);
      c.fill(Shape2D.curve(ex - side * 40.0F, ey - 72.0F, ex + side * 2.0F, ey - 80.0F, ex + side * 42.0F, ey - 72.0F, 3.6F, 2.0F), -14011280, 0.0F);
   }

   private static Canvas stripe() {
      Canvas c = new Canvas(64, 10);
      c.fill(Shape2D.box(32.0F, 3.3F, 40.0F, 2.8F, 0.0F), -13703425);
      c.fill(Shape2D.box(32.0F, 8.2F, 40.0F, 0.8F, 0.0F), -1);
      return c;
   }

   private static Canvas band() {
      Canvas c = new Canvas(64, 8);
      c.fill(Shape2D.box(32.0F, 4.0F, 40.0F, 3.0F, 0.0F), -13703425);
      return c;
   }

   private static Canvas socks() {
      Canvas c = new Canvas(64, 12);
      c.fill(Shape2D.box(32.0F, 2.5F, 40.0F, 2.0F, 0.0F), -13703425);
      c.fill(Shape2D.box(32.0F, 9.5F, 40.0F, 2.0F, 0.0F), -13703425);
      return c;
   }

   private static Canvas phoneRing() {
      Canvas c = new Canvas(96, 96);
      c.fill(Shape2D.circle(48.0F, 48.0F, 33.0F).outline(5.0F), -13703425);
      return c;
   }

   private static Decal topGraphic() {
      Canvas c = new Canvas(152, 120);
      c.fill(Shape2D.circle(76.0F, 60.0F, 42.0F).outline(10.0F), -13703425);
      float[] h = new float[]{16.0F, 30.0F, 50.0F, 72.0F, 94.0F, 72.0F, 50.0F, 30.0F, 16.0F};

      for (int i = 0; i < h.length; i++) {
         c.fill(Shape2D.box(76.0F + (i - 4) * 15.0F, 60.0F, 4.6F, h[i] * 0.5F, 3.0F), -14932394);
      }

      return Decal.front(c, 0.0F, 20.3F, 1.9F, 1.7F, 1.34F).depth(0.75F, 0.45F).maxAngle(62.0F);
   }

   private static Canvas backStripe() {
      Canvas c = new Canvas(12, 80);
      c.fill(Shape2D.box(6.0F, 40.0F, 3.1F, 39.0F, 0.9F), -13703425);
      c.fill(Shape2D.box(0.9F, 40.0F, 0.7F, 39.0F, 0.35F), -1512206);
      c.fill(Shape2D.box(11.1F, 40.0F, 0.7F, 39.0F, 0.35F), -1512206);
      return c;
   }

   private static Canvas buckle() {
      Canvas c = new Canvas(64, 48);
      c.fill(Shape2D.box(32.0F, 24.0F, 31.0F, 23.0F, 7.0F), Paint2D.linear(0.0F, 2.0F, 0.0F, 46.0F, -1643535, -7563613));
      c.fill(Shape2D.box(32.0F, 24.0F, 21.0F, 12.0F, 4.0F), -14276045);
      c.fill(Shape2D.box(32.0F, 24.0F, 14.0F, 3.5F, 1.5F), -13703425);
      c.fill(Shape2D.box(8.5F, 24.0F, 2.0F, 9.0F, 1.0F), -14276045);
      c.fill(Shape2D.box(55.5F, 24.0F, 2.0F, 9.0F, 1.0F), -14276045);
      return c;
   }

   private static Canvas pocket() {
      Canvas c = new Canvas(64, 24);
      c.fill(Shape2D.box(32.0F, 12.0F, 28.0F, 3.4F, 3.0F), -15460837);
      c.fill(Shape2D.box(32.0F, 12.0F, 25.0F, 0.8F, 0.4F), -13703425);
      c.fill(Shape2D.box(54.0F, 15.5F, 2.2F, 5.0F, 1.2F), -3550757);
      return c;
   }

   private static Canvas sleeveText() {
      float k = 0.43F;
      Canvas c = new Canvas(41, 117);
      Shape2D a = Shape2D.union(
         Shape2D.line(26.0F, 86.0F, 48.0F, 14.0F, 13.0F), Shape2D.line(48.0F, 14.0F, 70.0F, 86.0F, 13.0F), Shape2D.line(36.0F, 62.0F, 60.0F, 62.0F, 10.0F)
      );
      Shape2D o = Shape2D.ellipse(48.0F, 136.0F, 22.0F, 34.0F).outline(13.0F);
      Shape2D i = Shape2D.union(
         Shape2D.line(48.0F, 190.0F, 48.0F, 258.0F, 13.0F), Shape2D.line(32.0F, 190.0F, 64.0F, 190.0F, 9.0F), Shape2D.line(32.0F, 258.0F, 64.0F, 258.0F, 9.0F)
      );
      Shape2D text = Shape2D.union(a, o, i);
      ink(c, k, text.move(3.0F, 3.0F), -13703425, 0.0F);
      ink(c, k, text, -14932394, 0.0F);
      return c;
   }

   private static Canvas sleevePatch() {
      float k = 0.625F;
      Canvas c = new Canvas(80, 80);
      ink(c, k, Shape2D.circle(64.0F, 64.0F, 60.0F), -14210250, 0.0F);
      ink(c, k, Shape2D.circle(64.0F, 64.0F, 52.0F).outline(6.0F), -13703425, 0.0F);
      ink(c, k, Shape2D.arc(64.0F, 70.0F, 30.0F, 180.0F, 360.0F, 8.0F), -591621, 0.0F);
      ink(c, k, Shape2D.box(36.0F, 78.0F, 8.0F, 14.0F, 5.0F), -591621, 0.0F);
      ink(c, k, Shape2D.box(92.0F, 78.0F, 8.0F, 14.0F, 5.0F), -591621, 0.0F);
      return c;
   }

   private static Canvas laces() {
      float k = 0.5F;
      Canvas c = new Canvas(48, 80);

      for (int i = 0; i < 4; i++) {
         float y = 30.0F + i * 32.0F;
         ink(c, k, Shape2D.line(20.0F, y, 76.0F, y + 16.0F, 9.0F), -13703425, 0.0F);
         ink(c, k, Shape2D.line(76.0F, y, 20.0F, y + 16.0F, 9.0F), -13703425, 0.0F);
         ink(c, k, Shape2D.circle(14.0F, y, 5.0F), -3550757, 0.0F);
         ink(c, k, Shape2D.circle(82.0F, y, 5.0F), -3550757, 0.0F);
      }

      return c;
   }

   private static void ink(Canvas c, float k, Shape2D s, int argb, float feather) {
      c.fill(s.scale(k, 0.0F, 0.0F), argb, feather * k);
   }

   private static float frontZ(Shape s, float x, float y, float start) {
      float z = start;

      for (int i = 0; i < 400 && z > -8.0F; i++) {
         float d = s.distance(x, y, z);
         if (d < 0.003F) {
            return z;
         }

         z -= Math.max(0.005F, d * 0.7F);
      }

      return z;
   }

   private static Shape ring(float cx, float cy, float cz, float ax, float ay, float az, float major, float minor) {
      float l = (float)Math.sqrt(ax * ax + ay * ay + az * az);
      float nx = ax / l;
      float ny = ay / l;
      float nz = az / l;
      Shape t = Shapes.torus(cx, cy, cz, major, minor);
      if (nx * nx + nz * nz < 1.0E-8F) {
         return t;
      } else {
         float angle = (float)Math.toDegrees(Math.acos(Math.max(-1.0F, Math.min(1.0F, ny))));
         return t.rotateAxis(nz, 0.0F, -nx, angle, cx, cy, cz);
      }
   }

   private static Shape oval(float zc, float y0, float y1, float rx0, float rz0, float rx1, float rz1) {
      float mx = Math.max(rx0, rx1);
      float mz = Math.max(rz0, rz1);
      return Shapes.field((x, y, z) -> {
         float t = clamp01((y - y0) / (y1 - y0));
         float d = ellipse(x, z - zc, rx0 + (rx1 - rx0) * t, rz0 + (rz1 - rz0) * t);
         return Math.max(d * 0.95F, Math.max(y0 - y, y - y1));
      }, -mx, y0, zc - mz, mx, y1, zc + mz);
   }

   private static Shape ovalRing(float zc, float yc, float halfHeight, float rx, float rz, float thick) {
      float round = Math.min(thick, halfHeight) * 0.5F;
      return Shapes.field((x, y, z) -> {
         float a = Math.abs(ellipse(x, z - zc, rx, rz)) - thick + round;
         float b = Math.abs(y - yc) - halfHeight + round;
         float oa = Math.max(a, 0.0F);
         float ob = Math.max(b, 0.0F);
         return ((float)Math.sqrt(oa * oa + ob * ob) + Math.min(Math.max(a, b), 0.0F) - round) * 0.95F;
      }, -rx - thick, yc - halfHeight, zc - rz - thick, rx + thick, yc + halfHeight, zc + rz + thick);
   }

   private static float ellipse(float x, float z, float rx, float rz) {
      float k0 = (float)Math.sqrt(x * x / (rx * rx) + z * z / (rz * rz));
      float k1 = (float)Math.sqrt(x * x / (rx * rx * rx * rx) + z * z / (rz * rz * rz * rz));
      return k1 < 1.0E-6F ? -Math.min(rx, rz) : k0 * (k0 - 1.0F) / k1;
   }

   private static float pleat(float v) {
      float w = tri(v);
      return w * (1.5F - 0.5F * w * w);
   }

   private static float tri(float v) {
      float u = v - (float)Math.floor(v);
      return 4.0F * Math.min(u, 1.0F - u) - 1.0F;
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   private static float smooth(float e0, float e1, float x) {
      float t = clamp01((x - e0) / (e1 - e0));
      return t * t * (3.0F - 2.0F * t);
   }
}
