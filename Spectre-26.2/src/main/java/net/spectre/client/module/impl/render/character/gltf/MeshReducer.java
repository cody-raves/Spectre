package net.spectre.client.module.impl.render.character.gltf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class MeshReducer {
   private final float[] pos;
   private final int[] tri;
   private final boolean[] dead;
   private final int[] corner;
   private final List<List<Integer>> around = new ArrayList<>();
   private final boolean[] locked;
   private final double[] quad;
   private final int[] version;
   private int live;

   MeshReducer(PosedMesh s, List<Integer> source, boolean weld) {
      Map<Long, Integer> index = new HashMap<>();
      Map<String, Integer> welded = new HashMap<>();
      List<Integer> order = new ArrayList<>();
      this.tri = new int[source.size() * 3];

      for (int i = 0; i < source.size(); i++) {
         int t = source.get(i);
         boolean back = t < 0;
         int real = back ? ~t : t;

         for (int c = 0; c < 3; c++) {
            int v = s.tris[real * 3 + (back ? 2 - c : c)];
            int key = back ? ~v : v;
            Integer id = index.get((long)key);
            if (id == null && weld) {
               String spot = Float.floatToIntBits(s.pos[v * 3])
                  + ","
                  + Float.floatToIntBits(s.pos[v * 3 + 1])
                  + ","
                  + Float.floatToIntBits(s.pos[v * 3 + 2])
                  + ","
                  + Float.floatToIntBits(s.uv[v * 2])
                  + ","
                  + Float.floatToIntBits(s.uv[v * 2 + 1])
                  + ","
                  + s.material[v]
                  + (back ? "b" : "");
               id = welded.get(spot);
               if (id == null) {
                  welded.put(spot, order.size());
               }

               if (id != null) {
                  index.put((long)key, id);
               }
            }

            if (id == null) {
               id = order.size();
               index.put((long)key, id);
               order.add(key);
            }

            this.tri[i * 3 + c] = id;
         }
      }

      int n = order.size();
      this.corner = new int[n];
      this.pos = new float[n * 3];

      for (int i = 0; i < n; i++) {
         this.corner[i] = order.get(i);
         int vx = this.corner[i] < 0 ? ~this.corner[i] : this.corner[i];
         System.arraycopy(s.pos, vx * 3, this.pos, i * 3, 3);
      }

      this.dead = new boolean[source.size()];
      this.live = source.size();

      for (int i = 0; i < n; i++) {
         this.around.add(new ArrayList<>());
      }

      for (int t = 0; t < source.size(); t++) {
         int a = this.tri[t * 3];
         int b = this.tri[t * 3 + 1];
         int c = this.tri[t * 3 + 2];
         if (a != b && b != c && a != c) {
            for (int k = 0; k < 3; k++) {
               this.around.get(this.tri[t * 3 + k]).add(t);
            }
         } else {
            this.dead[t] = true;
            this.live--;
         }
      }

      this.locked = new boolean[n];
      Map<Long, Integer> edges = new HashMap<>();

      for (int tx = 0; tx < source.size(); tx++) {
         if (!this.dead[tx]) {
            for (int c = 0; c < 3; c++) {
               edges.merge(key(this.tri[tx * 3 + c], this.tri[tx * 3 + (c + 1) % 3]), 1, Integer::sum);
            }
         }
      }

      for (Entry<Long, Integer> e : edges.entrySet()) {
         if (e.getValue() != 2) {
            this.locked[(int)(e.getKey() >> 32)] = true;
            this.locked[(int)(e.getKey() & 4294967295L)] = true;
         }
      }

      this.quad = new double[n * 10];

      for (int txx = 0; txx < source.size(); txx++) {
         if (!this.dead[txx]) {
            int a = this.tri[txx * 3];
            int b = this.tri[txx * 3 + 1];
            int c = this.tri[txx * 3 + 2];
            float[] f = Mat4.faceNormal(this.pos, a, b, c);
            double len = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
            if (!(len < 1.0E-12)) {
               double nx = f[0] / len;
               double ny = f[1] / len;
               double nz = f[2] / len;
               double d = -(nx * this.pos[a * 3] + ny * this.pos[a * 3 + 1] + nz * this.pos[a * 3 + 2]);

               for (int vx : new int[]{a, b, c}) {
                  this.addPlane(vx, nx, ny, nz, d, len * 0.5);
               }
            }
         }
      }

      this.version = new int[n];
   }

   private static long key(int a, int b) {
      return (long)Math.min(a, b) << 32 | Math.max(a, b);
   }

   private void addPlane(int v, double a, double b, double c, double d, double w) {
      int o = v * 10;
      this.quad[o] = this.quad[o] + w * a * a;
      this.quad[o + 1] = this.quad[o + 1] + w * a * b;
      this.quad[o + 2] = this.quad[o + 2] + w * a * c;
      this.quad[o + 3] = this.quad[o + 3] + w * a * d;
      this.quad[o + 4] = this.quad[o + 4] + w * b * b;
      this.quad[o + 5] = this.quad[o + 5] + w * b * c;
      this.quad[o + 6] = this.quad[o + 6] + w * b * d;
      this.quad[o + 7] = this.quad[o + 7] + w * c * c;
      this.quad[o + 8] = this.quad[o + 8] + w * c * d;
      this.quad[o + 9] = this.quad[o + 9] + w * d * d;
   }

   private double error(int v, double x, double y, double z) {
      double[] q = this.quad;
      int o = v * 10;
      return q[o] * x * x
         + 2.0 * q[o + 1] * x * y
         + 2.0 * q[o + 2] * x * z
         + 2.0 * q[o + 3] * x
         + q[o + 4] * y * y
         + 2.0 * q[o + 5] * y * z
         + 2.0 * q[o + 6] * y
         + q[o + 7] * z * z
         + 2.0 * q[o + 8] * z
         + q[o + 9];
   }

   int[][] reduce(int[] targets) {
      PriorityQueue<MeshReducer.Move> heap = new PriorityQueue<>((p, q) -> Double.compare(p.cost, q.cost));

      for (int v = 0; v < this.locked.length; v++) {
         this.push(heap, v);
      }

      int[][] out = new int[targets.length][];
      Integer[] order = new Integer[targets.length];

      for (int i = 0; i < order.length; i++) {
         order[i] = i;
      }

      Arrays.sort(order, (a, b) -> Integer.compare(targets[b], targets[a]));
      Integer[] var15 = order;
      int var6 = order.length;

      for (int var7 = 0; var7 < var6; var7++) {
         int i = var15[var7];

         while (this.live > targets[i] && !heap.isEmpty()) {
            MeshReducer.Move m = heap.poll();
            if (this.version[m.from] == m.vf && this.version[m.to] == m.vt && !this.locked[m.from] && this.collapse(m.from, m.to)) {
               Set<Integer> touched = new HashSet<>();
               touched.add(m.to);

               for (int t : this.around.get(m.to)) {
                  if (!this.dead[t]) {
                     for (int c = 0; c < 3; c++) {
                        touched.add(this.tri[t * 3 + c]);
                     }
                  }
               }

               for (int v : touched) {
                  this.push(heap, v);
               }
            }
         }

         out[i] = this.snapshot();
      }

      return out;
   }

   private void push(PriorityQueue<MeshReducer.Move> heap, int v) {
      if (!this.locked[v]) {
         for (int n : this.neighbours(v)) {
            double dx = this.pos[n * 3] - this.pos[v * 3];
            double dy = this.pos[n * 3 + 1] - this.pos[v * 3 + 1];
            double dz = this.pos[n * 3 + 2] - this.pos[v * 3 + 2];
            double cost = this.error(v, this.pos[n * 3], this.pos[n * 3 + 1], this.pos[n * 3 + 2]) + 1.0E-4 * (dx * dx + dy * dy + dz * dz);
            heap.add(new MeshReducer.Move(cost, v, n, this.version[v], this.version[n]));
         }
      }
   }

   private List<Integer> neighbours(int v) {
      List<Integer> out = new ArrayList<>();

      for (int t : this.around.get(v)) {
         if (!this.dead[t]) {
            for (int c = 0; c < 3; c++) {
               int w = this.tri[t * 3 + c];
               if (w != v && !out.contains(w)) {
                  out.add(w);
               }
            }
         }
      }

      return out;
   }

   private boolean collapse(int u, int v) {
      List<Integer> nu = this.neighbours(u);
      List<Integer> nv = this.neighbours(v);
      if (!nu.contains(v)) {
         return false;
      } else {
         int shared = 0;
         int common = 0;

         for (int t : this.around.get(u)) {
            if (!this.dead[t] && this.has(t, v)) {
               shared++;
            }
         }

         for (int w : nu) {
            if (nv.contains(w)) {
               common++;
            }
         }

         if (common != shared) {
            return false;
         } else {
            for (int tx : this.around.get(u)) {
               if (!this.dead[tx] && !this.has(tx, v)) {
                  float[] before = Mat4.faceNormal(this.pos, this.tri[tx * 3], this.tri[tx * 3 + 1], this.tri[tx * 3 + 2]);
                  int[] moved = new int[]{this.tri[tx * 3], this.tri[tx * 3 + 1], this.tri[tx * 3 + 2]};

                  for (int c = 0; c < 3; c++) {
                     if (moved[c] == u) {
                        moved[c] = v;
                     }
                  }

                  float[] after = Mat4.faceNormal(this.pos, moved[0], moved[1], moved[2]);
                  double lb = Math.sqrt(before[0] * before[0] + before[1] * before[1] + before[2] * before[2]);
                  double la = Math.sqrt(after[0] * after[0] + after[1] * after[1] + after[2] * after[2]);
                  if (la < 1.0E-12 || lb < 1.0E-12) {
                     return false;
                  }

                  if ((before[0] * after[0] + before[1] * after[1] + before[2] * after[2]) / (la * lb) < 0.5) {
                     return false;
                  }
               }
            }

            for (int txx : this.around.get(u)) {
               if (!this.dead[txx]) {
                  if (this.has(txx, v)) {
                     this.dead[txx] = true;
                     this.live--;
                  } else {
                     for (int cx = 0; cx < 3; cx++) {
                        if (this.tri[txx * 3 + cx] == u) {
                           this.tri[txx * 3 + cx] = v;
                        }
                     }

                     this.around.get(v).add(txx);
                  }
               }
            }

            this.around.get(u).clear();

            for (int k = 0; k < 10; k++) {
               this.quad[v * 10 + k] = this.quad[v * 10 + k] + this.quad[u * 10 + k];
            }

            this.version[u]++;
            this.version[v]++;
            this.locked[u] = true;
            return true;
         }
      }
   }

   private boolean has(int t, int v) {
      return this.tri[t * 3] == v || this.tri[t * 3 + 1] == v || this.tri[t * 3 + 2] == v;
   }

   private int[] snapshot() {
      int[] out = new int[this.live * 3];
      int k = 0;

      for (int t = 0; t < this.dead.length; t++) {
         if (!this.dead[t]) {
            for (int c = 0; c < 3; c++) {
               out[k++] = this.corner[this.tri[t * 3 + c]];
            }
         }
      }

      return Arrays.copyOf(out, k);
   }

   @Environment(EnvType.CLIENT)
   private record Move(double cost, int from, int to, int vf, int vt) {
   }
}
