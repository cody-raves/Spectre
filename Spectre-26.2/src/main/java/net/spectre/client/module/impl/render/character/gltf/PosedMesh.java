package net.spectre.client.module.impl.render.character.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class PosedMesh {
   private static final int MAX_TRIANGLES = 4000000;
   final GltfFile file;
   final float[][] globals;
   final int[] parent;
   final Set<Integer> joints = new HashSet<>();
   float[] pos = new float[0];
   float[] nrm = new float[0];
   float[] uv = new float[0];
   int[] material = new int[0];
   int[] color = new int[0];
   boolean colored;
   int[] influence = new int[0];
   float[] weight = new float[0];
   int[] tris = new int[0];
   int vertices;
   int triangles;
   private float scale = 1.0F;
   private float shiftX;
   private float shiftY;
   private float shiftZ;
   private boolean flipped;

   private PosedMesh(GltfFile file) {
      this.file = file;
      this.globals = new float[file.nodes()][];
      this.parent = new int[file.nodes()];
      Arrays.fill(this.parent, -1);
   }

   static PosedMesh pose(GltfFile file, Map<Integer, Float> turns) throws IOException {
      PosedMesh m = new PosedMesh(file);

      for (int n = 0; n < file.nodes(); n++) {
         for (JsonElement c : GltfFile.array(file.node(n), "children")) {
            m.parent[c.getAsInt()] = n;
         }
      }

      JsonArray scenes = GltfFile.array(file.json, "scenes");
      JsonArray roots = new JsonArray();
      if (scenes.isEmpty()) {
         for (int n = 0; n < file.nodes(); n++) {
            if (m.parent[n] < 0) {
               roots.add(n);
            }
         }
      } else {
         roots = GltfFile.array(scenes.get(GltfFile.integer(file.json, "scene", 0)).getAsJsonObject(), "nodes");
      }

      for (JsonElement r : roots) {
         m.walk(r.getAsInt(), Mat4.identity(), turns);
      }

      for (JsonElement skin : GltfFile.array(file.json, "skins")) {
         for (JsonElement j : GltfFile.array(skin.getAsJsonObject(), "joints")) {
            m.joints.add(j.getAsInt());
         }
      }

      for (JsonElement r : roots) {
         m.collect(r.getAsInt());
      }

      if (m.triangles == 0) {
         throw new IOException("it has no triangles");
      } else {
         return m;
      }
   }

   private void walk(int node, float[] parentGlobal, Map<Integer, Float> turns) {
      float[] global = Mat4.mul(parentGlobal, Mat4.local(this.file.node(node)));
      Float turn = turns.get(node);
      if (turn != null) {
         global = Mat4.mul(Mat4.rollAbout(turn, Mat4.point(global, 0.0F, 0.0F, 0.0F)), global);
      }

      this.globals[node] = global;

      for (JsonElement c : GltfFile.array(this.file.node(node), "children")) {
         this.walk(c.getAsInt(), global, turns);
      }
   }

   private void collect(int node) throws IOException {
      JsonObject n = this.file.node(node);
      if (n.has("mesh")) {
         JsonObject mesh = GltfFile.array(this.file.json, "meshes").get(n.get("mesh").getAsInt()).getAsJsonObject();
         int skin = GltfFile.integer(n, "skin", -1);

         for (JsonElement p : GltfFile.array(mesh, "primitives")) {
            this.primitive(p.getAsJsonObject(), node, skin);
         }
      }

      for (JsonElement c : GltfFile.array(n, "children")) {
         this.collect(c.getAsInt());
      }
   }

   private void primitive(JsonObject p, int node, int skin) throws IOException {
      if (GltfFile.integer(p, "mode", 4) == 4) {
         JsonObject attrs = GltfFile.object(p, "attributes");
         int mat = GltfFile.integer(p, "material", -1);
         float[] ps = this.file.floats(attrs.get("POSITION").getAsInt());
         int n = ps.length / 3;
         float[] ns = attrs.has("NORMAL") ? this.file.floats(attrs.get("NORMAL").getAsInt()) : null;
         String uvKey = "TEXCOORD_" + this.file.texCoord(mat);
         float[] ts = attrs.has(uvKey) ? this.file.floats(attrs.get(uvKey).getAsInt()) : new float[n * 2];
         float[] cs = attrs.has("COLOR_0") ? this.file.floats(attrs.get("COLOR_0").getAsInt()) : null;
         int cn = cs == null ? 0 : cs.length / n;
         this.colored |= cs != null;
         int[] idx = p.has("indices") ? this.file.ints(p.get("indices").getAsInt()) : null;
         if (idx == null) {
            idx = new int[n];
            int i = 0;

            while (i < n) {
               idx[i] = i++;
            }
         }

         for (int i : idx) {
            if (i < 0 || i >= n) {
               throw new IOException("a mesh has broken indices");
            }
         }

         if (this.triangles + idx.length / 3 > 4000000) {
            throw new IOException("it has more than four million triangles");
         } else {
            float[][] skinMats = null;
            int[] js = null;
            int[] jointNodes = null;
            float[] ws = null;
            if (skin >= 0 && attrs.has("JOINTS_0") && attrs.has("WEIGHTS_0")) {
               JsonObject sk = GltfFile.array(this.file.json, "skins").get(skin).getAsJsonObject();
               JsonArray list = GltfFile.array(sk, "joints");
               jointNodes = new int[list.size()];
               float[] ibm = sk.has("inverseBindMatrices") ? this.file.floats(sk.get("inverseBindMatrices").getAsInt()) : null;
               skinMats = new float[list.size()][];

               for (int j = 0; j < list.size(); j++) {
                  jointNodes[j] = list.get(j).getAsInt();
                  float[] inv = ibm == null ? Mat4.identity() : Arrays.copyOfRange(ibm, j * 16, j * 16 + 16);
                  float[] g = this.globals[jointNodes[j]];
                  skinMats[j] = Mat4.mul(g == null ? Mat4.identity() : g, inv);
               }

               js = this.file.ints(attrs.get("JOINTS_0").getAsInt());
               ws = this.file.floats(attrs.get("WEIGHTS_0").getAsInt());
            }

            float[] g = this.globals[node];
            int base = this.vertices;
            this.grow(n, idx.length / 3);

            for (int ix = 0; ix < n; ix++) {
               float x = ps[ix * 3];
               float y = ps[ix * 3 + 1];
               float z = ps[ix * 3 + 2];
               float nx = ns == null ? 0.0F : ns[ix * 3];
               float ny = ns == null ? 1.0F : ns[ix * 3 + 1];
               float nz = ns == null ? 0.0F : ns[ix * 3 + 2];
               int v = base + ix;
               Arrays.fill(this.influence, v * 4, v * 4 + 4, -1);
               float[] outP;
               float[] outN;
               if (skinMats != null) {
                  outP = new float[3];
                  outN = new float[3];

                  for (int k = 0; k < 4; k++) {
                     float w = ws[ix * 4 + k];
                     int j = js[ix * 4 + k];
                     if (!(w <= 0.0F) && j >= 0 && j < skinMats.length) {
                        float[] q = Mat4.point(skinMats[j], x, y, z);
                        float[] d = Mat4.dir(skinMats[j], nx, ny, nz);

                        for (int c = 0; c < 3; c++) {
                           outP[c] += q[c] * w;
                           outN[c] += d[c] * w;
                        }

                        this.influence[v * 4 + k] = jointNodes[j];
                        this.weight[v * 4 + k] = w;
                     }
                  }
               } else {
                  outP = Mat4.point(g, x, y, z);
                  outN = Mat4.dir(g, nx, ny, nz);
                  this.influence[v * 4] = node;
                  this.weight[v * 4] = 1.0F;
               }

               System.arraycopy(outP, 0, this.pos, v * 3, 3);
               Mat4.normalise(outN);
               System.arraycopy(outN, 0, this.nrm, v * 3, 3);
               this.uv[v * 2] = ts[ix * 2];
               this.uv[v * 2 + 1] = ts[ix * 2 + 1];
               this.material[v] = mat;
               this.color[v] = cs == null ? -1 : 0xFF000000 | srgb(cs[ix * cn]) << 16 | srgb(cs[ix * cn + 1]) << 8 | srgb(cs[ix * cn + 2]);
            }

            for (int t = 0; t < idx.length / 3; t++) {
               for (int c = 0; c < 3; c++) {
                  this.tris[(this.triangles + t) * 3 + c] = base + idx[t * 3 + c];
               }
            }

            int first = this.triangles;
            this.vertices += n;
            this.triangles += idx.length / 3;
            if (ns == null) {
               this.smoothNormals(base, n, first);
            }
         }
      }
   }

   private void smoothNormals(int base, int n, int firstTriangle) {
      Arrays.fill(this.nrm, base * 3, (base + n) * 3, 0.0F);

      for (int t = firstTriangle; t < this.triangles; t++) {
         float[] f = Mat4.faceNormal(this.pos, this.tris[t * 3], this.tris[t * 3 + 1], this.tris[t * 3 + 2]);

         for (int c = 0; c < 3; c++) {
            int v = this.tris[t * 3 + c];

            for (int k = 0; k < 3; k++) {
               this.nrm[v * 3 + k] = this.nrm[v * 3 + k] + f[k];
            }
         }
      }

      for (int v = base; v < base + n; v++) {
         float[] q = new float[]{this.nrm[v * 3], this.nrm[v * 3 + 1], this.nrm[v * 3 + 2]};
         Mat4.normalise(q);
         System.arraycopy(q, 0, this.nrm, v * 3, 3);
      }
   }

   private void grow(int moreVerts, int moreTris) {
      int nv = this.vertices + moreVerts;
      int nt = this.triangles + moreTris;
      this.pos = Arrays.copyOf(this.pos, nv * 3);
      this.nrm = Arrays.copyOf(this.nrm, nv * 3);
      this.uv = Arrays.copyOf(this.uv, nv * 2);
      this.influence = Arrays.copyOf(this.influence, nv * 4);
      this.weight = Arrays.copyOf(this.weight, nv * 4);
      this.material = Arrays.copyOf(this.material, nv);
      this.color = Arrays.copyOf(this.color, nv);
      this.tris = Arrays.copyOf(this.tris, nt * 3);
   }

   private static int srgb(float linear) {
      return Math.round((float)Math.pow(Math.max(0.0F, Math.min(1.0F, linear)), 0.45454545454545453) * 255.0F);
   }

   void cluster(int target) {
      if (this.triangles > target) {
         float[] box = this.bounds();
         float cell = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2])) / 600.0F;
         int[] map = new int[this.vertices];

         while (this.weld(cell, map) > target) {
            cell *= 1.25F;
         }

         int kept = 0;

         for (int m : map) {
            kept = Math.max(kept, m + 1);
         }

         float[] p = new float[kept * 3];
         float[] n = new float[kept * 3];
         float[] t = new float[kept * 2];
         int[] count = new int[kept];
         int[] mat = new int[kept];
         int[] inf = new int[kept * 4];
         float[] w = new float[kept * 4];
         long[] rgb = new long[kept * 3];

         for (int v = 0; v < this.vertices; v++) {
            int m = map[v];
            if (count[m]++ == 0) {
               mat[m] = this.material[v];
               System.arraycopy(this.influence, v * 4, inf, m * 4, 4);
               System.arraycopy(this.weight, v * 4, w, m * 4, 4);
            }

            for (int k = 0; k < 3; k++) {
               p[m * 3 + k] = p[m * 3 + k] + this.pos[v * 3 + k];
               n[m * 3 + k] = n[m * 3 + k] + this.nrm[v * 3 + k];
               rgb[m * 3 + k] = rgb[m * 3 + k] + (this.color[v] >> 16 - k * 8 & 0xFF);
            }

            t[m * 2] = t[m * 2] + this.uv[v * 2];
            t[m * 2 + 1] = t[m * 2 + 1] + this.uv[v * 2 + 1];
         }

         int[] c = new int[kept];

         for (int m = 0; m < kept; m++) {
            for (int k = 0; k < 3; k++) {
               p[m * 3 + k] = p[m * 3 + k] / count[m];
            }

            t[m * 2] = t[m * 2] / count[m];
            t[m * 2 + 1] = t[m * 2 + 1] / count[m];
            float[] q = new float[]{n[m * 3], n[m * 3 + 1], n[m * 3 + 2]};
            Mat4.normalise(q);
            System.arraycopy(q, 0, n, m * 3, 3);
            c[m] = 0xFF000000 | (int)(rgb[m * 3] / count[m]) << 16 | (int)(rgb[m * 3 + 1] / count[m]) << 8 | (int)(rgb[m * 3 + 2] / count[m]);
         }

         Set<Long> seen = new HashSet<>();
         int[] out = new int[this.triangles * 3];
         int corners = 0;

         for (int i = 0; i < this.triangles; i++) {
            int a = map[this.tris[i * 3]];
            int b = map[this.tris[i * 3 + 1]];
            int d = map[this.tris[i * 3 + 2]];
            if (a != b && b != d && a != d && seen.add(key(a, b, d))) {
               out[corners++] = a;
               out[corners++] = b;
               out[corners++] = d;
            }
         }

         this.pos = p;
         this.nrm = n;
         this.uv = t;
         this.material = mat;
         this.influence = inf;
         this.weight = w;
         this.color = c;
         this.tris = Arrays.copyOf(out, corners);
         this.vertices = kept;
         this.triangles = corners / 3;
      }
   }

   private int weld(float cell, int[] map) {
      Map<String, Integer> cells = new HashMap<>();

      for (int v = 0; v < this.vertices; v++) {
         String key = (int)Math.floor(this.pos[v * 3] / cell)
            + ","
            + (int)Math.floor(this.pos[v * 3 + 1] / cell)
            + ","
            + (int)Math.floor(this.pos[v * 3 + 2] / cell)
            + ","
            + this.material[v]
            + ","
            + Math.round(this.uv[v * 2] * 64.0F)
            + ","
            + Math.round(this.uv[v * 2 + 1] * 64.0F);
         Integer id = cells.putIfAbsent(key, cells.size());
         map[v] = id == null ? cells.size() - 1 : id;
      }

      Set<Long> seen = new HashSet<>();

      for (int i = 0; i < this.triangles; i++) {
         int a = map[this.tris[i * 3]];
         int b = map[this.tris[i * 3 + 1]];
         int c = map[this.tris[i * 3 + 2]];
         if (a != b && b != c && a != c) {
            seen.add(key(a, b, c));
         }
      }

      return seen.size();
   }

   private static long key(int a, int b, int c) {
      int lo = Math.min(a, Math.min(b, c));
      int hi = Math.max(a, Math.max(b, c));
      int mid = a + b + c - lo - hi;
      return (lo * 2654435761L + mid) * 2246822519L + hi;
   }

   int[] separate(int[] part) {
      Map<Long, Integer> copies = new HashMap<>();
      List<Integer> from = new ArrayList<>();
      List<Integer> owners = new ArrayList<>();

      for (int t = 0; t < this.triangles; t++) {
         int owner = CharacterBake.owner(part, this.tris[t * 3], this.tris[t * 3 + 1], this.tris[t * 3 + 2]);

         for (int c = 0; c < 3; c++) {
            int v = this.tris[t * 3 + c];
            if (part[v] != owner) {
               Integer copy = copies.get(v * 8L + owner);
               if (copy == null) {
                  copy = this.vertices + from.size();
                  copies.put(v * 8L + owner, copy);
                  from.add(v);
                  owners.add(owner);
               }

               this.tris[t * 3 + c] = copy;
            }
         }
      }

      int first = this.vertices;
      this.grow(from.size(), 0);
      int[] grown = Arrays.copyOf(part, first + from.size());

      for (int i = 0; i < from.size(); i++) {
         int v = from.get(i);
         int copy = first + i;
         System.arraycopy(this.pos, v * 3, this.pos, copy * 3, 3);
         System.arraycopy(this.nrm, v * 3, this.nrm, copy * 3, 3);
         System.arraycopy(this.uv, v * 2, this.uv, copy * 2, 2);
         System.arraycopy(this.influence, v * 4, this.influence, copy * 4, 4);
         System.arraycopy(this.weight, v * 4, this.weight, copy * 4, 4);
         this.material[copy] = this.material[v];
         this.color[copy] = this.color[v];
         grown[copy] = owners.get(i);
      }

      this.vertices = this.vertices + from.size();
      return grown;
   }

   void normalise(float height, boolean flip) {
      this.flipped = flip;
      if (flip) {
         for (int v = 0; v < this.vertices; v++) {
            this.pos[v * 3] = -this.pos[v * 3];
            this.pos[v * 3 + 2] = -this.pos[v * 3 + 2];
            this.nrm[v * 3] = -this.nrm[v * 3];
            this.nrm[v * 3 + 2] = -this.nrm[v * 3 + 2];
         }
      }

      float[] box = this.bounds();
      this.scale = height / Math.max(1.0E-6F, box[4] - box[1]);
      this.shiftX = -(box[0] + box[3]) * 0.5F;
      this.shiftY = -box[1];
      this.shiftZ = -(box[2] + box[5]) * 0.5F;

      for (int v = 0; v < this.vertices; v++) {
         this.pos[v * 3] = (this.pos[v * 3] + this.shiftX) * this.scale;
         this.pos[v * 3 + 1] = (this.pos[v * 3 + 1] + this.shiftY) * this.scale;
         this.pos[v * 3 + 2] = (this.pos[v * 3 + 2] + this.shiftZ) * this.scale;
      }
   }

   float[] bounds() {
      float[] b = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      for (int v = 0; v < this.vertices; v++) {
         for (int k = 0; k < 3; k++) {
            b[k] = Math.min(b[k], this.pos[v * 3 + k]);
            b[k + 3] = Math.max(b[k + 3], this.pos[v * 3 + k]);
         }
      }

      return b;
   }

   float[] world(int node) {
      float[] g = this.globals[node];
      return g == null ? new float[3] : Mat4.point(g, 0.0F, 0.0F, 0.0F);
   }

   float[] joint(int node) {
      float[] p = this.world(node);
      float x = this.flipped ? -p[0] : p[0];
      float z = this.flipped ? -p[2] : p[2];
      return new float[]{(x + this.shiftX) * this.scale, (p[1] + this.shiftY) * this.scale, (z + this.shiftZ) * this.scale};
   }

   boolean under(int node, int ancestor) {
      int n = node;

      for (int steps = 0; n >= 0 && steps <= this.parent.length; steps++) {
         if (n == ancestor) {
            return true;
         }

         n = this.parent[n];
      }

      return false;
   }

   float[] below(int joint) {
      float[] out = new float[this.vertices];

      for (int v = 0; v < this.vertices; v++) {
         for (int k = 0; k < 4; k++) {
            int n = this.influence[v * 4 + k];
            if (n >= 0 && this.under(n, joint)) {
               out[v] += this.weight[v * 4 + k];
            }
         }
      }

      return out;
   }
}
