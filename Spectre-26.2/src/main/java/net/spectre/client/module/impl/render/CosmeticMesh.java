package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;

@Environment(EnvType.CLIENT)
final class CosmeticMesh {
   static final int GOLD = 0;
   static final int INLAY = 1;
   static final int GEM = 2;
   static final int BRONZE = 3;
   static final int FUR = 4;
   static final int MATERIALS = 5;
   static final int STYLE_HOOD = 0;
   static final int STYLE_WRAITH = 1;
   static final int STYLE_HALO = 2;
   private static final float PX = 0.0625F;
   private static final float TWIST = 0.12F;
   private static final float[] CORNER_U = new float[]{0.0F, 1.0F, 1.0F, 0.0F};
   private static final float[] CORNER_V = new float[]{0.0F, 0.0F, 1.0F, 1.0F};
   private static final float BAND_TOP = -6.6F;
   private static final float BAND_BOTTOM = -5.3F;
   private final float[] pos;
   private final float[] normal;
   private final float[] sweep;
   private final byte[] material;
   private final float[] tone;
   private final int quads;

   CosmeticMesh(CosmeticMesh.Builder b) {
      this.quads = b.quads;
      this.pos = Arrays.copyOf(b.pos, this.quads * 12);
      this.normal = Arrays.copyOf(b.normal, this.quads * 3);
      this.sweep = Arrays.copyOf(b.sweep, this.quads * 8);
      this.material = Arrays.copyOf(b.material, this.quads);
      this.tone = Arrays.copyOf(b.tone, this.quads);
   }

   boolean isEmpty() {
      return this.quads == 0;
   }

   void emit(Pose pose, VertexConsumer out, int light, CosmeticMesh.Look look) {
      int[] palette = look.palette();
      float gain = look.sweepGain();
      boolean glint = gain > 0.004F;
      float sc = look.sweepCos();
      float ss = look.sweepSin();

      for (int q = 0; q < this.quads; q++) {
         int mat = this.material[q];
         int argb = palette[mat];
         float base = mat == 2 ? this.tone[q] * look.gemPulse() : this.tone[q];
         boolean metal = glint && (mat == 0 || mat == 3);
         int lit = mat == 2 && look.glow() ? 15728880 : light;
         int n = q * 3;
         float nx = this.normal[n];
         float ny = this.normal[n + 1];
         float nz = this.normal[n + 2];

         for (int v = 0; v < 4; v++) {
            int p = q * 12 + v * 3;
            float bright = base;
            if (metal) {
               int s = q * 8 + v * 2;
               float c = this.sweep[s] * sc + this.sweep[s + 1] * ss;
               if (c > 0.0F) {
                  c *= c;
                  c *= c;
                  c *= c;
                  bright = base + gain * c;
               }
            }

            out.addVertex(pose, this.pos[p], this.pos[p + 1], this.pos[p + 2])
               .setColor(tint(argb, bright))
               .setUv(CORNER_U[v], CORNER_V[v])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(lit)
               .setNormal(pose, nx, ny, nz);
         }
      }
   }

   private static int tint(int argb, float k) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      if (k <= 1.0F) {
         r = (int)(r * k);
         g = (int)(g * k);
         b = (int)(b * k);
      } else {
         float w = Math.min(1.0F, k - 1.0F);
         r += (int)((255 - r) * w);
         g += (int)((255 - g) * w);
         b += (int)((255 - b) * w);
      }

      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   // Open ghost hood: no Egyptian crown, stripes or serpent geometry.
   static CosmeticMesh head(int style, int detail, boolean helmet) {
      Builder b=new Builder();float e=helmet?5.5F:4.9F,front=-e-.2F;
      if(style==2){
         for(int i=0;i<20;i++){double a=i*Math.PI*2/20;float x=(float)Math.cos(a)*(e+1),z=(float)Math.sin(a)*(e+1);b.box(x-.45F,-10.4F,z-.45F,x+.45F,-9.8F,z+.45F,2);}
      }else{
         b.box(-e,-9.3F,-e,e,-8.0F,e,0);
         b.box(-e,-8,-e,-e+1.1F,style==1?-.5F:-2,e,0);
         b.box(e-1.1F,-8,-e,e,style==1?-.5F:-2,e,0);
         b.box(-e,-8,e-1.1F,e,1,e,0);
         // Spectral eyes above the open face; scalloped cloth below the hood.
         b.box(-2.6F,-7.4F,front,-1.3F,-5.8F,front+.35F,2);
         b.box(1.3F,-7.4F,front,2.6F,-5.8F,front+.35F,2);
         for(int i=0;i<detail;i++){float x=-e+1+i*(e*2-2)/Math.max(1,detail-1);b.slab(0,x-.5F,x+.5F,e-1,e,2+(i%2),x-.2F,x+.2F,e-1,e,0);}
      }
      return new CosmeticMesh(b);
   }
   static CosmeticMesh broadCollar(boolean chest,boolean collar){return body(0,5,chest,collar);}
   static CosmeticMesh body(int style,int detail,boolean chest,boolean collar){
      Builder b=new Builder();if(collar){float z=chest?-3.3F:-2.5F;
         b.box(-3.6F,-.5F,z,3.6F,.4F,z+.3F,0);
         b.box(-.65F,.4F,z-.15F,.65F,2,z+.3F,2);
      }return new CosmeticMesh(b);
   }
   private static void ring(float[] out, float y, float xa, float xb, float za, float zb) {
      set(out, 0, xa, y, za);
      set(out, 1, xb, y, za);
      set(out, 2, xb, y, zb);
      set(out, 3, xa, y, zb);
   }

   private static void set(float[] out, int corner, float x, float y, float z) {
      out[corner * 3] = x;
      out[corner * 3 + 1] = y;
      out[corner * 3 + 2] = z;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   @Environment(EnvType.CLIENT)
   static final class Builder {
      private float[] pos = new float[3072];
      private float[] normal = new float[768];
      private float[] sweep = new float[2048];
      private byte[] material = new byte[256];
      private float[] tone = new float[256];
      private int quads;
      private float shade = 1.0F;
      private final float[] corners = new float[24];

      void shade(float value) {
         this.shade = value;
      }

      private void ensure() {
         if (this.quads >= this.material.length) {
            int capacity = this.material.length * 2;
            this.pos = Arrays.copyOf(this.pos, capacity * 12);
            this.normal = Arrays.copyOf(this.normal, capacity * 3);
            this.sweep = Arrays.copyOf(this.sweep, capacity * 8);
            this.material = Arrays.copyOf(this.material, capacity);
            this.tone = Arrays.copyOf(this.tone, capacity);
         }
      }

      void quad(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         float ox,
         float oy,
         float oz,
         int mat
      ) {
         float ux = cx - ax;
         float uy = cy - ay;
         float uz = cz - az;
         float vx = dx - bx;
         float vy = dy - by;
         float vz = dz - bz;
         float nx = uy * vz - uz * vy;
         float ny = uz * vx - ux * vz;
         float nz = ux * vy - uy * vx;
         float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (!(length < 1.0E-6F)) {
            nx /= length;
            ny /= length;
            nz /= length;
            float mx = (ax + bx + cx + dx) * 0.25F - ox;
            float my = (ay + by + cy + dy) * 0.25F - oy;
            float mz = (az + bz + cz + dz) * 0.25F - oz;
            if (nx * mx + ny * my + nz * mz < 0.0F) {
               nx = -nx;
               ny = -ny;
               nz = -nz;
            }

            this.ensure();
            int p = this.quads * 12;
            this.vertex(p, ax, ay, az);
            this.vertex(p + 3, bx, by, bz);
            this.vertex(p + 6, cx, cy, cz);
            this.vertex(p + 9, dx, dy, dz);
            this.normal[this.quads * 3] = nx;
            this.normal[this.quads * 3 + 1] = ny;
            this.normal[this.quads * 3 + 2] = nz;
            this.material[this.quads] = (byte)mat;
            int hash = this.quads * -1640531535;
            hash ^= hash >>> 15;
            this.tone[this.quads] = this.shade * (0.955F + 0.065F * ((hash & 0xFF) / 255.0F));
            this.quads++;
         }
      }

      private void vertex(int p, float x, float y, float z) {
         this.pos[p] = x * 0.0625F;
         this.pos[p + 1] = y * 0.0625F;
         this.pos[p + 2] = z * 0.0625F;
         double angle = Math.atan2(x, -z) + y * 0.12F;
         int s = this.quads * 8 + (p - this.quads * 12) / 3 * 2;
         this.sweep[s] = (float)Math.cos(angle);
         this.sweep[s + 1] = (float)Math.sin(angle);
      }

      void prism(float[] c, int mat) {
         float ox = 0.0F;
         float oy = 0.0F;
         float oz = 0.0F;

         for (int i = 0; i < 8; i++) {
            ox += c[i * 3];
            oy += c[i * 3 + 1];
            oz += c[i * 3 + 2];
         }

         ox *= 0.125F;
         oy *= 0.125F;
         oz *= 0.125F;
         this.face(c, 0, 1, 2, 3, ox, oy, oz, mat);
         this.face(c, 4, 5, 6, 7, ox, oy, oz, mat);
         this.face(c, 0, 1, 5, 4, ox, oy, oz, mat);
         this.face(c, 1, 2, 6, 5, ox, oy, oz, mat);
         this.face(c, 2, 3, 7, 6, ox, oy, oz, mat);
         this.face(c, 3, 0, 4, 7, ox, oy, oz, mat);
      }

      private void face(float[] c, int a, int b, int d, int e, float ox, float oy, float oz, int mat) {
         this.quad(
            c[a * 3],
            c[a * 3 + 1],
            c[a * 3 + 2],
            c[b * 3],
            c[b * 3 + 1],
            c[b * 3 + 2],
            c[d * 3],
            c[d * 3 + 1],
            c[d * 3 + 2],
            c[e * 3],
            c[e * 3 + 1],
            c[e * 3 + 2],
            ox,
            oy,
            oz,
            mat
         );
      }

      void slab(float y0, float xa0, float xb0, float za0, float zb0, float y1, float xa1, float xb1, float za1, float zb1, int mat) {
         float[] c = this.corners;
         CosmeticMesh.set(c, 0, xa0, y0, za0);
         CosmeticMesh.set(c, 1, xb0, y0, za0);
         CosmeticMesh.set(c, 2, xb0, y0, zb0);
         CosmeticMesh.set(c, 3, xa0, y0, zb0);
         CosmeticMesh.set(c, 4, xa1, y1, za1);
         CosmeticMesh.set(c, 5, xb1, y1, za1);
         CosmeticMesh.set(c, 6, xb1, y1, zb1);
         CosmeticMesh.set(c, 7, xa1, y1, zb1);
         this.prism(c, mat);
      }

      void box(float x0, float y0, float z0, float x1, float y1, float z1, int mat) {
         this.slab(y0, x0, x1, z0, z1, y1, x0, x1, z0, z1, mat);
      }

      void stripedSlab(
         float y0, float xa0, float xb0, float za0, float zb0, float y1, float xa1, float xb1, float za1, float zb1, int bands, int first, int second
      ) {
         float[] from = new float[12];
         float[] to = new float[12];
         CosmeticMesh.ring(from, y0, xa0, xb0, za0, zb0);
         CosmeticMesh.ring(to, y1, xa1, xb1, za1, zb1);
         this.striped(from, to, bands, first, second);
      }

      void striped(float[] from, float[] to, int bands, int first, int second) {
         float[] c = this.corners;

         for (int k = 0; k < bands; k++) {
            float t0 = (float)k / bands;
            float t1 = (float)(k + 1) / bands;

            for (int i = 0; i < 12; i++) {
               c[i] = CosmeticMesh.lerp(from[i], to[i], t0);
               c[12 + i] = CosmeticMesh.lerp(from[i], to[i], t1);
            }

            this.prism(c, (k & 1) == 0 ? first : second);
         }
      }

      void studs(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         boolean wide,
         boolean clearCentre,
         float ox,
         float oy,
         float oz
      ) {
         int count = wide ? 3 : 2;

         for (int i = 0; i < count; i++) {
            float u = wide ? 0.2F + 0.3F * i : 0.35F + 0.3F * i;
            if (!clearCentre || !wide || i != 1) {
               this.stud(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u, 0.5F, ox, oy, oz);
            }
         }
      }

      private void stud(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         float u,
         float v,
         float ox,
         float oy,
         float oz
      ) {
         float px = CosmeticMesh.lerp(CosmeticMesh.lerp(ax, bx, u), CosmeticMesh.lerp(dx, cx, u), v);
         float py = CosmeticMesh.lerp(CosmeticMesh.lerp(ay, by, u), CosmeticMesh.lerp(dy, cy, u), v);
         float pz = CosmeticMesh.lerp(CosmeticMesh.lerp(az, bz, u), CosmeticMesh.lerp(dz, cz, u), v);
         float ux = bx - ax;
         float uy = by - ay;
         float uz = bz - az;
         float ul = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
         float vx = dx - ax;
         float vy = dy - ay;
         float vz = dz - az;
         float vl = (float)Math.sqrt(vx * vx + vy * vy + vz * vz);
         if (!(ul < 1.0E-5F) && !(vl < 1.0E-5F)) {
            ux /= ul;
            uy /= ul;
            uz /= ul;
            vx /= vl;
            vy /= vl;
            vz /= vl;
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(nl < 1.0E-5F)) {
               nx /= nl;
               ny /= nl;
               nz /= nl;
               if (nx * (px - ox) + ny * (py - oy) + nz * (pz - oz) < 0.0F) {
                  nx = -nx;
                  ny = -ny;
                  nz = -nz;
               }

               float h = 0.32F;
               float lift = 0.07F;
               px += nx * lift;
               py += ny * lift;
               pz += nz * lift;
               this.quad(
                  px - ux * h - vx * h,
                  py - uy * h - vy * h,
                  pz - uz * h - vz * h,
                  px + ux * h - vx * h,
                  py + uy * h - vy * h,
                  pz + uz * h - vz * h,
                  px + ux * h + vx * h,
                  py + uy * h + vy * h,
                  pz + uz * h + vz * h,
                  px - ux * h + vx * h,
                  py - uy * h + vy * h,
                  pz - uz * h + vz * h,
                  px - nx,
                  py - ny,
                  pz - nz,
                  0
               );
            }
         }
      }

      void tube(float[] path, float[] half, int samples, int mat) {
         float[] c = this.corners;

         for (int i = 0; i + 1 < samples; i++) {
            section(c, 0, path, half, samples, i);
            section(c, 4, path, half, samples, i + 1);
            this.prism(c, i + 2 == samples ? 3 : mat);
         }
      }

      private static void section(float[] out, int corner, float[] path, float[] half, int samples, int i) {
         int prev = Math.max(0, i - 1);
         int next = Math.min(samples - 1, i + 1);
         float tx = path[next * 3] - path[prev * 3];
         float ty = path[next * 3 + 1] - path[prev * 3 + 1];
         float length = (float)Math.sqrt(tx * tx + ty * ty);
         float nx = length > 1.0E-5F ? -ty / length : 1.0F;
         float ny = length > 1.0E-5F ? tx / length : 0.0F;
         float h = half[i];
         float x = path[i * 3];
         float y = path[i * 3 + 1];
         float z = path[i * 3 + 2];
         CosmeticMesh.set(out, corner, x + nx * h, y + ny * h, z - h);
         CosmeticMesh.set(out, corner + 1, x + nx * h, y + ny * h, z + h);
         CosmeticMesh.set(out, corner + 2, x - nx * h, y - ny * h, z + h);
         CosmeticMesh.set(out, corner + 3, x - nx * h, y - ny * h, z - h);
      }

      void disc(float cx, float cy, float cz, float radius, float depth, int segments, int faceMat, int rimMat) {
         float zf = cz - depth;
         float zb = cz + depth;

         for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2) * i / segments;
            double a1 = (Math.PI * 2) * (i + 1) / segments;
            float x0 = cx + radius * (float)Math.cos(a0);
            float y0 = cy + radius * (float)Math.sin(a0);
            float x1 = cx + radius * (float)Math.cos(a1);
            float y1 = cy + radius * (float)Math.sin(a1);
            this.quad(cx, cy, zf, x0, y0, zf, x1, y1, zf, x1, y1, zf, cx, cy, cz, faceMat);
            this.quad(cx, cy, zb, x0, y0, zb, x1, y1, zb, x1, y1, zb, cx, cy, cz, faceMat);
            this.quad(x0, y0, zf, x1, y1, zf, x1, y1, zb, x0, y0, zb, cx, cy, cz, rimMat);
         }
      }

      void annulus(float cx, float cy, float z, float inner, float outer, int segments, int mat, float behind) {
         for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2) * i / segments;
            double a1 = (Math.PI * 2) * (i + 1) / segments;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            this.quad(
               cx + c0 * inner,
               cy + s0 * inner,
               z,
               cx + c0 * outer,
               cy + s0 * outer,
               z,
               cx + c1 * outer,
               cy + s1 * outer,
               z,
               cx + c1 * inner,
               cy + s1 * inner,
               z,
               cx,
               cy,
               behind,
               mat
            );
         }
      }
   }

   @Environment(EnvType.CLIENT)
   record Look(int[] palette, boolean glow, float sweepCos, float sweepSin, float sweepGain, float gemPulse) {
   }
}
