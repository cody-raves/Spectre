package net.spectre.client.module.impl.render.character.gltf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class ShapeCut {
   private static final int BANDS = 160;
   private static final int BRIDGE = 5;
   private static final int NO_TRUNK = -2;
   private final PosedMesh mesh;
   private final float height;
   private final float step;
   private final float gap;
   private final List<float[]>[] spans;
   private final float[][][] intervals;
   private final int[][] labels;

   private ShapeCut(PosedMesh mesh) {
      this.mesh = mesh;
      this.height = mesh.bounds()[4];
      this.step = this.height / 160.0F;
      this.gap = this.height * 0.004F;
      this.spans = new List[160];

      for (int i = 0; i < 160; i++) {
         this.spans[i] = new ArrayList<>();
      }

      this.intervals = new float[160][][];
      this.labels = new int[160][];
   }

   static CharacterBake.Cut cut(PosedMesh mesh) {
      ShapeCut s = new ShapeCut(mesh);
      if (!(s.height > 0.0F)) {
         return null;
      } else {
         s.slice();
         return s.find();
      }
   }

   private void slice() {
      float[] p = this.mesh.pos;

      for (int t = 0; t < this.mesh.triangles; t++) {
         int a = this.mesh.tris[t * 3];
         int b = this.mesh.tris[t * 3 + 1];
         int c = this.mesh.tris[t * 3 + 2];
         float ya = p[a * 3 + 1];
         float yb = p[b * 3 + 1];
         float yc = p[c * 3 + 1];
         float lo = Math.min(ya, Math.min(yb, yc));
         float hi = Math.max(ya, Math.max(yb, yc));
         int i0 = Math.max(0, (int)Math.ceil(lo / this.step - 0.5F));
         int i1 = Math.min(159, (int)Math.floor(hi / this.step - 0.5F));

         for (int i = i0; i <= i1; i++) {
            float y = (i + 0.5F) * this.step;
            float x0 = Float.MAX_VALUE;
            float x1 = -Float.MAX_VALUE;
            int[][] edges = new int[][]{{a, b}, {b, c}, {c, a}};

            for (int[] e : edges) {
               float y0 = p[e[0] * 3 + 1];
               float y1 = p[e[1] * 3 + 1];
               if (y0 != y1 && !((y - y0) * (y - y1) > 0.0F)) {
                  float x = p[e[0] * 3] + (p[e[1] * 3] - p[e[0] * 3]) * (y - y0) / (y1 - y0);
                  x0 = Math.min(x0, x);
                  x1 = Math.max(x1, x);
               }
            }

            if (x0 <= x1) {
               this.spans[i].add(new float[]{x0, x1});
            }
         }
      }

      for (int i = 0; i < 160; i++) {
         List<float[]> list = this.spans[i];
         list.sort((u, v) -> Float.compare(u[0], v[0]));
         List<float[]> merged = new ArrayList<>();

         for (float[] s : list) {
            float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && s[0] <= last[1] + this.gap) {
               last[1] = Math.max(last[1], s[1]);
            } else {
               merged.add(new float[]{s[0], s[1]});
            }
         }

         this.intervals[i] = merged.toArray(new float[0][]);
         this.labels[i] = new int[this.intervals[i].length];
         this.spans[i] = null;
      }
   }

   private CharacterBake.Cut find() {
      int crotch = this.crotch();
      int[] inner = new int[320];
      float[] trunk = null;

      for (int i = 0; i < 160; i++) {
         int[] in = this.innerIntervals(i, i < crotch);
         if (i >= crotch && in[0] >= 0) {
            float[] t = this.intervals[i][in[0]];
            boolean centred = t[0] <= 0.0F && t[1] >= 0.0F;
            boolean continues = trunk != null && t[0] <= trunk[1] && t[1] >= trunk[0];
            if (!centred && !continues) {
               in = new int[]{-2, -2};
            } else {
               trunk = t;
            }
         }

         inner[i * 2] = in[0];
         inner[i * 2 + 1] = in[1];
      }

      ShapeCut.Arm[] arm = new ShapeCut.Arm[]{this.arm(inner, crotch, 0), this.arm(inner, crotch, 1)};
      int shoulder = Math.max(arm[0] == null ? -1 : arm[0].to, arm[1] == null ? -1 : arm[1].to);
      int neck = this.neck(inner, crotch, shoulder < 0 ? 112 : shoulder);
      boolean legs = crotch > 0;
      if (!legs && arm[0] == null && arm[1] == null && neck < 0) {
         return null;
      } else {
         for (int i = 0; i < 160; i++) {
            float[][] iv = this.intervals[i];

            for (int k = 0; k < iv.length; k++) {
               int label = 1;
               if (i < crotch && legs) {
                  if (k == inner[i * 2]) {
                     label = 4;
                  } else if (k == inner[i * 2 + 1]) {
                     label = 5;
                  }
               }

               if (neck >= 0 && i > neck && label == 1) {
                  label = 0;
               }

               this.labels[i][k] = label;
            }
         }

         int[] part = new int[this.mesh.vertices];

         for (int v = 0; v < this.mesh.vertices; v++) {
            float x = this.mesh.pos[v * 3];
            float y = this.mesh.pos[v * 3 + 1];
            int band = Math.max(0, Math.min(159, (int)(y / this.step)));
            part[v] = this.label(x, y);

            for (int side = 0; side < 2; side++) {
               ShapeCut.Arm a = arm[side];
               if (a != null && band >= a.from && band <= a.to && (side == 0 ? x < a.edge[band] : x > a.edge[band])) {
                  part[v] = side == 0 ? 2 : 3;
               }
            }
         }

         if (this.share(part, 2) + this.share(part, 3) > 0.4F) {
            for (int v = 0; v < part.length; v++) {
               if (part[v] == 2 || part[v] == 3) {
                  part[v] = 1;
               }
            }

            arm = new ShapeCut.Arm[2];
         }

         if (arm[0] != null || arm[1] != null) {
            part = this.mesh.separate(part);
         }

         float[][] pivot = new float[6][];
         float[][] grip = new float[6][];
         float[][] bendAt = new float[6][];
         float[][] bendWeights = new float[6][];
         pivot[1] = new float[]{0.0F, 24.0F, 0.0F};
         float blend = this.height * 0.02F;
         if (legs) {
            float hip = crotch * this.step;

            for (int bone : new int[]{4, 5}) {
               pivot[bone] = this.mean(part, bone, hip - this.height * 0.06F, hip);
               pivot[bone][1] = hip;
               bendAt[bone] = this.mean(part, bone, hip * 0.45F, hip * 0.55F);
               bendAt[bone][1] = hip * 0.5F;
               bendWeights[bone] = this.below(bendAt[bone][1], blend);
            }
         }

         for (int sidex = 0; sidex < 2; sidex++) {
            ShapeCut.Arm a = arm[sidex];
            if (a != null) {
               int bone = sidex == 0 ? 2 : 3;
               float top = (a.to + 1) * this.step;
               float bottom = a.from * this.step;
               float[] upper = this.mean(part, bone, top - this.height * 0.05F, top);
               float[] lower = this.mean(part, bone, bottom, bottom + this.height * 0.05F);
               upper[1] = top;
               lower[1] = bottom + this.height * 0.03F;
               boolean raised = this.attached(a, sidex, a.from, -1) && !this.attached(a, sidex, a.to, 1);
               pivot[bone] = raised ? new float[]{lower[0], bottom, lower[2]} : upper;
               grip[bone] = raised ? new float[]{upper[0], top - this.height * 0.03F, upper[2]} : lower;
               this.lower(part, bone, pivot[bone], grip[bone]);
               float[] axis = new float[3];
               bendAt[bone] = new float[3];

               for (int k = 0; k < 3; k++) {
                  bendAt[bone][k] = (pivot[bone][k] + grip[bone][k]) * 0.5F;
                  axis[k] = grip[bone][k] - pivot[bone][k];
               }

               bendWeights[bone] = this.beyond(bendAt[bone], axis, blend);
            }
         }

         if (neck >= 0) {
            float y = (neck + 1) * this.step;
            pivot[0] = this.mean(part, 0, y, y + this.height * 0.04F);
            pivot[0][1] = y;
         }

         return new CharacterBake.Cut(part, pivot, grip, bendAt, bendWeights);
      }
   }

   private int crotch() {
      int start = -1;
      int i = 0;

      while (i < 160 && this.intervals[i].length == 0) {
         i++;
      }

      for (int limit = i + 19; i < 160; i++) {
         boolean split = this.splitAtCentre(i);
         if (start < 0) {
            if (split) {
               start = i;
            } else if (i > limit) {
               return 0;
            }
         } else if (!split) {
            return i * this.step >= this.height * 0.1F && i * this.step <= this.height * 0.65F ? i : 0;
         }
      }

      return 0;
   }

   private boolean splitAtCentre(int i) {
      float[][] iv = this.intervals[i];
      if (iv.length < 2) {
         return false;
      } else {
         for (float[] s : iv) {
            if (s[0] <= 0.0F && s[1] >= 0.0F) {
               return false;
            }
         }

         return iv[0][1] < 0.0F && iv[iv.length - 1][0] > 0.0F;
      }
   }

   private int[] innerIntervals(int i, boolean legs) {
      float[][] iv = this.intervals[i];
      if (iv.length == 0) {
         return new int[]{-1, -1};
      } else if (legs) {
         int right = -1;
         int left = -1;

         for (int k = 0; k < iv.length; k++) {
            if (iv[k][1] < 0.0F) {
               right = k;
            } else if (left < 0) {
               left = k;
            }
         }

         return new int[]{right, left};
      } else {
         int best = 0;
         float bestDist = Float.MAX_VALUE;

         for (int kx = 0; kx < iv.length; kx++) {
            float d = iv[kx][0] > 0.0F ? iv[kx][0] : (iv[kx][1] < 0.0F ? -iv[kx][1] : 0.0F);
            if (d < bestDist) {
               bestDist = d;
               best = kx;
            }
         }

         return new int[]{best, best};
      }
   }

   private ShapeCut.Arm arm(int[] inner, int crotch, int side) {
      float[] edge = new float[160];
      Arrays.fill(edge, Float.NaN);

      for (int i = 0; i < 160; i++) {
         int k = inner[i * 2 + side];
         float[][] iv = this.intervals[i];
         if (k == -2 && iv.length > 0 && (side == 0 ? iv[0][0] < 0.0F : iv[iv.length - 1][1] > 0.0F)) {
            edge[i] = 0.0F;
         }

         if (k >= 0) {
            if (side == 0 && k > 0) {
               edge[i] = (iv[k - 1][1] + iv[k][0]) * 0.5F;
            }

            if (side == 1 && k < iv.length - 1) {
               edge[i] = (iv[k][1] + iv[k + 1][0]) * 0.5F;
            }
         }
      }

      int bestFrom = -1;
      int bestTo = -1;
      int from = -1;
      int last = -1;

      for (int i = 0; i <= 160; i++) {
         boolean apart = i < 160 && !Float.isNaN(edge[i]);
         if (apart) {
            if (from >= 0 && i - last - 1 > 5) {
               if (last - from > bestTo - bestFrom) {
                  bestFrom = from;
                  bestTo = last;
               }

               from = -1;
            }

            if (from < 0) {
               from = i;
            }

            last = i;
         }
      }

      if (from >= 0 && last - from > bestTo - bestFrom) {
         bestFrom = from;
         bestTo = last;
      }

      if (bestFrom >= 0 && !(bestTo - bestFrom + 1 < 12.799999F) && bestTo > crotch) {
         for (int ix = bestFrom + 1; ix < bestTo; ix++) {
            if (Float.isNaN(edge[ix])) {
               int lo = ix - 1;
               int hi = ix + 1;

               while (Float.isNaN(edge[hi])) {
                  hi++;
               }

               edge[ix] = side == 0 ? Math.min(edge[lo], edge[hi]) : Math.max(edge[lo], edge[hi]);
            }
         }

         return new ShapeCut.Arm(bestFrom, bestTo, edge);
      } else {
         return null;
      }
   }

   private boolean attached(ShapeCut.Arm a, int side, int at, int dir) {
      float e = a.edge[at];
      boolean right = side == 0;

      for (int d = 1; d <= 2; d++) {
         int i = at + dir * d;
         if (i < 0 || i >= 160) {
            return false;
         }

         for (float[] iv : this.intervals[i]) {
            if (right ? iv[0] < e : iv[1] > e) {
               return true;
            }
         }
      }

      return false;
   }

   private void lower(int[] part, int bone, float[] pivot, float[] grip) {
      float dx = grip[0] - pivot[0];
      float dy = grip[1] - pivot[1];
      float dz = grip[2] - pivot[2];
      float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(len < 1.0E-4F)) {
         float angle = (float)Math.acos(Math.max(-1.0F, Math.min(1.0F, -dy / len)));
         if (!(angle < Math.toRadians(40.0))) {
            float ax = dz;
            float az = -dx;
            float al = (float)Math.sqrt(dz * dz + az * az);
            if (al < 1.0E-5F) {
               ax = 0.0F;
               az = 1.0F;
               al = 1.0F;
            }

            ax /= al;
            az /= al;
            float turn = angle - (float)Math.toRadians(10.0);
            float c = (float)Math.cos(turn);
            float s = (float)Math.sin(turn);

            for (int v = 0; v < part.length; v++) {
               if (part[v] == bone) {
                  rotate(this.mesh.pos, v * 3, pivot, ax, az, c, s);
                  rotate(this.mesh.nrm, v * 3, null, ax, az, c, s);
               }
            }

            float[] g = (float[])grip.clone();
            rotate(g, 0, pivot, ax, az, c, s);
            System.arraycopy(g, 0, grip, 0, 3);
         }
      }
   }

   private static void rotate(float[] a, int o, float[] about, float ax, float az, float c, float s) {
      float px = a[o] - (about == null ? 0.0F : about[0]);
      float py = a[o + 1] - (about == null ? 0.0F : about[1]);
      float pz = a[o + 2] - (about == null ? 0.0F : about[2]);
      float dot = ax * px + az * pz;
      float cx = -az * py;
      float cy = az * px - ax * pz;
      float cz = ax * py;
      float rx = px * c + cx * s + ax * dot * (1.0F - c);
      float ry = py * c + cy * s;
      float rz = pz * c + cz * s + az * dot * (1.0F - c);
      a[o] = rx + (about == null ? 0.0F : about[0]);
      a[o + 1] = ry + (about == null ? 0.0F : about[1]);
      a[o + 2] = rz + (about == null ? 0.0F : about[2]);
   }

   private int neck(int[] inner, int crotch, int shoulder) {
      int top = 154;
      int from = Math.max(shoulder, crotch + 1);
      int best = -1;
      float bestWidth = Float.MAX_VALUE;

      for (int i = from; i < top; i++) {
         float w = this.torsoWidth(inner, i);
         if (w > 0.0F && w < bestWidth) {
            bestWidth = w;
            best = i;
         }
      }

      if (best < 0) {
         return -1;
      } else {
         float above = 0.0F;
         float below = this.torsoWidth(inner, from);

         for (int ix = best + 1; ix < 160; ix++) {
            above = Math.max(above, this.torsoWidth(inner, ix));
         }

         float headHeight = (160 - best) * this.step;
         boolean narrow = bestWidth < above * 0.75F && bestWidth < below * 0.8F;
         return narrow && headHeight > this.height * 0.08F && headHeight < this.height * 0.45F ? best : -1;
      }
   }

   private float torsoWidth(int[] inner, int i) {
      int k = inner[i * 2];
      return k < 0 ? 0.0F : this.intervals[i][k][1] - this.intervals[i][k][0];
   }

   private int label(float x, float y) {
      int i = Math.max(0, Math.min(159, (int)(y / this.step)));

      for (int d = 0; d < 160; d++) {
         for (int j : new int[]{i - d, i + d}) {
            if (j >= 0 && j < 160 && this.intervals[j].length != 0) {
               float[][] iv = this.intervals[j];
               int best = 0;
               float bestDist = Float.MAX_VALUE;

               for (int k = 0; k < iv.length; k++) {
                  float dist = x < iv[k][0] ? iv[k][0] - x : (x > iv[k][1] ? x - iv[k][1] : 0.0F);
                  if (dist < bestDist) {
                     bestDist = dist;
                     best = k;
                  }
               }

               return this.labels[j][best];
            }
         }
      }

      return 1;
   }

   private float share(int[] part, int bone) {
      int n = 0;

      for (int p : part) {
         if (p == bone) {
            n++;
         }
      }

      return (float)n / Math.max(1, part.length);
   }

   private float[] mean(int[] part, int bone, float y0, float y1) {
      double x = 0.0;
      double y = 0.0;
      double z = 0.0;
      int n = 0;

      for (int pass = 0; pass < 2 && n == 0; pass++) {
         for (int v = 0; v < part.length; v++) {
            float vy = this.mesh.pos[v * 3 + 1];
            if (part[v] == bone && (pass != 0 || !(vy < y0) && !(vy > y1))) {
               x += this.mesh.pos[v * 3];
               y += vy;
               z += this.mesh.pos[v * 3 + 2];
               n++;
            }
         }
      }

      return n == 0 ? new float[3] : new float[]{(float)(x / n), (float)(y / n), (float)(z / n)};
   }

   private float[] beyond(float[] at, float[] axis, float blend) {
      float l = (float)Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
      float[] w = new float[this.mesh.vertices];
      if (l < 1.0E-5F) {
         return w;
      } else {
         for (int v = 0; v < w.length; v++) {
            float d = ((this.mesh.pos[v * 3] - at[0]) * axis[0] + (this.mesh.pos[v * 3 + 1] - at[1]) * axis[1] + (this.mesh.pos[v * 3 + 2] - at[2]) * axis[2])
               / l;
            float t = Math.max(0.0F, Math.min(1.0F, (d + blend) / (2.0F * blend)));
            w[v] = t * t * (3.0F - 2.0F * t);
         }

         return w;
      }
   }

   private float[] below(float y, float blend) {
      float[] w = new float[this.mesh.vertices];

      for (int v = 0; v < w.length; v++) {
         float t = Math.max(0.0F, Math.min(1.0F, (y + blend - this.mesh.pos[v * 3 + 1]) / (2.0F * blend)));
         w[v] = t * t * (3.0F - 2.0F * t);
      }

      return w;
   }

   @Environment(EnvType.CLIENT)
   private record Arm(int from, int to, float[] edge) {
   }
}
