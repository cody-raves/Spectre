package net.spectre.client.module.impl.render.character.gltf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CharacterBake {
   static final int HEAD = 0;
   static final int BODY = 1;
   static final int RIGHT_ARM = 2;
   static final int LEFT_ARM = 3;
   static final int RIGHT_LEG = 4;
   static final int LEFT_LEG = 5;
   private static final int[] BUDGETS = new int[]{7500, 15000, 30000};
   private static final int DENSE = 150000;
   private static final float[] STICK = new float[]{0.7F, 0.0F, 0.6F, 0.6F, 0.3F, 0.3F};

   private CharacterBake() {
   }

   public static CharacterBake.Result bake(byte[] glb, CharacterBake.Options options, ImageDecoder decoder) throws IOException {
      GltfFile file = GltfFile.read(glb);
      PosedMesh mesh = PosedMesh.pose(file, Map.of());
      Skeleton skeleton = Skeleton.find(mesh, options.bones);
      Map<Integer, Float> turns = skeleton.armTurns();
      if (!turns.isEmpty()) {
         mesh = PosedMesh.pose(file, turns);
         skeleton = Skeleton.find(mesh, options.bones);
      }

      mesh.normalise(options.height, skeleton.facesBack);
      mesh.cluster(150000);
      CharacterBake.Cut cut = skeleton.found() ? skeletonCut(mesh, skeleton, options) : ShapeCut.cut(mesh);
      if (cut == null) {
         cut = still(mesh);
      }

      List<CharacterBake.Piece> pieces = split(mesh, cut.part());
      reduce(mesh, pieces);
      TextureAtlas atlas = TextureAtlas.pack(file, uvBounds(mesh, pieces), decoder);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      write(new DataOutputStream(bytes), mesh, cut, pieces, atlas, options);
      List<String> limbs = new ArrayList<>();
      if (has(pieces, 0)) {
         limbs.add("head");
      }

      if (has(pieces, 2) || has(pieces, 3)) {
         limbs.add("arms");
      }

      if (has(pieces, 4) || has(pieces, 5)) {
         limbs.add("legs");
      }

      return new CharacterBake.Result(bytes.toByteArray(), limbs);
   }

   public static String describe(byte[] glb, CharacterBake.Options options) throws IOException {
      GltfFile file = GltfFile.read(glb);
      PosedMesh mesh = PosedMesh.pose(file, Map.of());
      Skeleton skeleton = Skeleton.find(mesh, options.bones);
      StringBuilder b = new StringBuilder(
         String.format(Locale.ROOT, "%d vertices, %d triangles, %d bones%n", mesh.vertices, mesh.triangles, mesh.joints.size())
      );
      b.append(skeleton.found() ? skeleton.describe() : "no usable skeleton, limbs are cut by shape\n");
      Set<Integer> materials = new HashSet<>();

      for (int v = 0; v < mesh.vertices; v++) {
         materials.add(mesh.material[v]);
      }

      for (int m : materials) {
         b.append("material ").append(file.materialName(m)).append('\n');
      }

      return b.toString();
   }

   private static CharacterBake.Cut still(PosedMesh mesh) {
      float[][] pivot = new float[6][];
      pivot[1] = new float[]{0.0F, 24.0F, 0.0F};
      return new CharacterBake.Cut(new int[mesh.vertices], pivot, new float[6][], new float[6][], new float[6][]);
   }

   private static CharacterBake.Cut skeletonCut(PosedMesh mesh, Skeleton sk, CharacterBake.Options o) {
      int[] group = sk.groups();
      boolean legs = sk.thigh[0] >= 0 && sk.thigh[1] >= 0;
      float floor = o.floor;
      if (floor < 0.0F && sk.shin[0] >= 0 && sk.shin[1] >= 0) {
         floor = Math.min(mesh.joint(sk.shin[0])[1], mesh.joint(sk.shin[1])[1]);
      }

      boolean pinned = !o.headMaterials.isEmpty() && sk.head >= 0;
      int[] part = new int[mesh.vertices];
      float[] sum = new float[6];

      for (int v = 0; v < mesh.vertices; v++) {
         float x = mesh.pos[v * 3];
         float y = mesh.pos[v * 3 + 1];
         if (pinned && o.headMaterials.contains(mesh.file.materialName(mesh.material[v]))) {
            part[v] = y < o.neck ? 1 : 0;
         } else {
            Arrays.fill(sum, 0.0F);

            for (int k = 0; k < 4; k++) {
               int node = mesh.influence[v * 4 + k];
               if (node >= 0) {
                  sum[group[node]] = sum[group[node]] + mesh.weight[v * 4 + k];
               }
            }

            int best = 1;

            for (int b = 0; b < 6; b++) {
               if (b != 1 && sum[b] >= STICK[b] && sum[b] > sum[best] && (b != 0 || !pinned)) {
                  best = b;
               }
            }

            boolean lower = best == 1 || best == 4 || best == 5;
            if (legs && lower && o.hip > 0.0F) {
               best = y >= o.hip ? 1 : (x < 0.0F ? 4 : 5);
            } else if (legs && best == 1 && y < floor) {
               best = x < 0.0F ? 4 : 5;
            }

            part[v] = best;
         }
      }

      float[][] pivot = new float[6][];
      float[][] grip = new float[6][];
      float[][] bendAt = new float[6][];
      float[][] bendWeights = new float[6][];
      pivot[1] = new float[]{0.0F, 24.0F, 0.0F};
      if (sk.head >= 0) {
         pivot[0] = mesh.joint(sk.head);
      }

      for (int s = 0; s < 2; s++) {
         int arm = s == 0 ? 2 : 3;
         int leg = s == 0 ? 4 : 5;
         if (sk.upperArm[s] >= 0) {
            pivot[arm] = mesh.joint(sk.upperArm[s]);
            int end = sk.hand[s] >= 0 ? sk.hand[s] : sk.forearm[s];
            grip[arm] = end >= 0 ? mesh.joint(end) : pivot[arm];
            if (sk.forearm[s] >= 0) {
               bendAt[arm] = mesh.joint(sk.forearm[s]);
               bendWeights[arm] = bend(mesh, sk.forearm[s], sk.upperArm[s]);
            }
         }

         if (sk.thigh[s] >= 0) {
            pivot[leg] = mesh.joint(sk.thigh[s]);
            if (sk.shin[s] >= 0) {
               bendAt[leg] = mesh.joint(sk.shin[s]);
               bendWeights[leg] = bend(mesh, sk.shin[s], sk.thigh[s]);
            }
         }
      }

      return new CharacterBake.Cut(part, pivot, grip, bendAt, bendWeights);
   }

   static int owner(int[] part, int a, int b, int c) {
      return part[b] == part[c] ? part[b] : part[a];
   }

   private static List<CharacterBake.Piece> split(PosedMesh mesh, int[] part) {
      CharacterBake.Piece[][] pieces = new CharacterBake.Piece[6][2];

      for (int t = 0; t < mesh.triangles; t++) {
         int a = mesh.tris[t * 3];
         int b = mesh.tris[t * 3 + 1];
         int c = mesh.tris[t * 3 + 2];
         if (!(Mat4.area(mesh.pos, a, b, c) < 1.0E-9F)) {
            int owner = owner(part, a, b, c);
            int layer = mesh.file.translucent(mesh.material[a]) ? 1 : 0;
            CharacterBake.Piece p = pieces[owner][layer];
            if (p == null) {
               p = pieces[owner][layer] = new CharacterBake.Piece(owner, layer);
            }

            p.source.add(t);
            if (layer == 1 && mesh.file.doubleSided(mesh.material[a])) {
               p.source.add(~t);
            }
         }
      }

      List<CharacterBake.Piece> all = new ArrayList<>();

      for (CharacterBake.Piece[] row : pieces) {
         for (CharacterBake.Piece px : row) {
            if (px != null) {
               all.add(px);
            }
         }
      }

      return all;
   }

   private static void reduce(PosedMesh mesh, List<CharacterBake.Piece> pieces) {
      double weightSum = 0.0;

      for (CharacterBake.Piece p : pieces) {
         weightSum += p.source.size() * (p.bone == 0 ? 2.0 : 1.0);
      }

      for (CharacterBake.Piece p : pieces) {
         int[] targets = new int[BUDGETS.length];

         for (int l = 0; l < BUDGETS.length; l++) {
            double share = p.source.size() * (p.bone == 0 ? 2.0 : 1.0) / weightSum;
            targets[l] = (int)Math.min((long)p.source.size(), Math.max(16L, Math.round(BUDGETS[l] * share)));
         }

         boolean needed = targets[BUDGETS.length - 1] < p.source.size();
         p.levels = new MeshReducer(mesh, p.source, needed).reduce(targets);
      }
   }

   private static Map<Integer, float[]> uvBounds(PosedMesh mesh, List<CharacterBake.Piece> pieces) {
      Map<Integer, float[]> used = new LinkedHashMap<>();

      for (CharacterBake.Piece p : pieces) {
         for (int t : p.source) {
            int tri = t < 0 ? ~t : t;

            for (int c = 0; c < 3; c++) {
               int v = mesh.tris[tri * 3 + c];
               float[] b = used.computeIfAbsent(mesh.material[v], k -> new float[]{1.0E9F, 1.0E9F, -1.0E9F, -1.0E9F});
               b[0] = Math.min(b[0], mesh.uv[v * 2]);
               b[1] = Math.min(b[1], mesh.uv[v * 2 + 1]);
               b[2] = Math.max(b[2], mesh.uv[v * 2]);
               b[3] = Math.max(b[3], mesh.uv[v * 2 + 1]);
            }
         }
      }

      return used;
   }

   private static boolean has(List<CharacterBake.Piece> pieces, int bone) {
      for (CharacterBake.Piece p : pieces) {
         if (p.bone == bone) {
            return true;
         }
      }

      return false;
   }

   private static CharacterBake.Piece find(List<CharacterBake.Piece> pieces, int bone, int layer) {
      for (CharacterBake.Piece p : pieces) {
         if (p.bone == bone && p.layer == layer) {
            return p;
         }
      }

      return null;
   }

   private static void write(
      DataOutputStream out, PosedMesh mesh, CharacterBake.Cut cut, List<CharacterBake.Piece> pieces, TextureAtlas atlas, CharacterBake.Options o
   ) throws IOException {
      float[][] pivot = cut.pivot();
      float[][] grip = cut.grip();
      float[][] bendAt = cut.bendAt();
      float[][] bendWeights = cut.bendWeights();
      float[] box = mesh.bounds();
      float hipY = pivot[4] != null && pivot[5] != null ? (pivot[4][1] + pivot[5][1]) * 0.5F : 12.0F;
      out.writeInt(1095648072);
      out.writeInt(3);
      out.writeFloat(box[1]);
      out.writeFloat(box[4]);
      out.writeFloat(hipY);
      out.writeFloat(o.flat);
      out.writeBoolean(true);
      out.writeBoolean(mesh.colored);
      out.writeInt(BUDGETS.length);
      int bones = 0;

      for (int b = 0; b < 6; b++) {
         if (has(pieces, b)) {
            bones++;
         }
      }

      out.writeInt(bones);

      for (int bx = 0; bx < 6; bx++) {
         if (has(pieces, bx)) {
            out.writeByte(bx);

            for (float f : pivot[bx]) {
               out.writeFloat(f);
            }

            for (float f : grip[bx] != null ? grip[bx] : pivot[bx]) {
               out.writeFloat(f);
            }

            float[] bounds = bounds(mesh, pieces, bx);

            for (float f : bounds) {
               out.writeFloat(f);
            }

            boolean arm = bx == 2 || bx == 3;
            boolean leg = bx == 4 || bx == 5;
            out.writeFloat(arm ? 0.6F : (leg ? 0.55F : 1.0F));
            out.writeFloat(arm ? 40.0F : (leg ? 30.0F : 0.0F));
            out.writeBoolean(bendAt[bx] != null);
            if (bendAt[bx] != null) {
               for (float f : bendAt[bx]) {
                  out.writeFloat(f);
               }
            }

            for (int l = 0; l < BUDGETS.length; l++) {
               for (int layer = 0; layer < 2; layer++) {
                  writeLevel(out, mesh, atlas, find(pieces, bx, layer), l, bounds, bendWeights[bx], bendAt[bx] != null);
               }
            }
         }
      }

      byte[] pixels = atlas.packed();
      out.writeInt(atlas.width);
      out.writeInt(atlas.height);
      out.writeInt(pixels.length);
      out.write(pixels);
      out.flush();
   }

   private static float[] bend(PosedMesh mesh, int joint, int limb) {
      float[] at = mesh.joint(joint);
      float[] w = mesh.below(joint);
      float[] inLimb = mesh.below(limb);

      for (int v = 0; v < mesh.vertices; v++) {
         if (!(inLimb[v] >= 0.5F)) {
            float t = Math.max(0.0F, Math.min(1.0F, at[1] - mesh.pos[v * 3 + 1] + 0.5F));
            w[v] = t * t * (3.0F - 2.0F * t);
         }
      }

      return w;
   }

   private static float[] bounds(PosedMesh mesh, List<CharacterBake.Piece> pieces, int bone) {
      float[] b = new float[]{1.0E9F, 1.0E9F, 1.0E9F, -1.0E9F, -1.0E9F, -1.0E9F};

      for (CharacterBake.Piece p : pieces) {
         if (p.bone == bone) {
            for (int t : p.source) {
               int tri = t < 0 ? ~t : t;

               for (int c = 0; c < 3; c++) {
                  int v = mesh.tris[tri * 3 + c];

                  for (int k = 0; k < 3; k++) {
                     b[k] = Math.min(b[k], mesh.pos[v * 3 + k]);
                     b[k + 3] = Math.max(b[k + 3], mesh.pos[v * 3 + k]);
                  }
               }
            }
         }
      }

      for (int k = 0; k < 3; k++) {
         if (b[k + 3] - b[k] < 0.001F) {
            b[k + 3] = b[k] + 0.001F;
         }
      }

      return b;
   }

   private static void writeLevel(
      DataOutputStream out, PosedMesh mesh, TextureAtlas atlas, CharacterBake.Piece p, int level, float[] box, float[] bend, boolean jointed
   ) throws IOException {
      if (p == null) {
         out.writeInt(0);
         out.writeInt(0);
      } else {
         int[] corners = p.levels[level];
         Map<Integer, Integer> remap = new HashMap<>();
         List<Integer> keys = new ArrayList<>();
         int[] local = new int[corners.length];

         for (int i = 0; i < corners.length; i++) {
            Integer idx = remap.get(corners[i]);
            if (idx == null) {
               idx = keys.size();
               remap.put(corners[i], idx);
               keys.add(corners[i]);
            }

            local[i] = idx;
         }

         out.writeInt(keys.size());

         for (int k : keys) {
            int v = k < 0 ? ~k : k;

            for (int a = 0; a < 3; a++) {
               float t = (mesh.pos[v * 3 + a] - box[a]) / (box[a + 3] - box[a]);
               out.writeChar(Math.round(Math.max(0.0F, Math.min(1.0F, t)) * 65535.0F));
            }
         }

         for (int k : keys) {
            int v = k < 0 ? ~k : k;
            float sign = k < 0 ? -1.0F : 1.0F;

            for (int a = 0; a < 3; a++) {
               out.writeByte(Math.round(mesh.nrm[v * 3 + a] * sign * 127.0F));
            }
         }

         for (int k : keys) {
            int v = k < 0 ? ~k : k;
            float[] t = atlas.map(mesh.material[v], mesh.uv[v * 2], mesh.uv[v * 2 + 1]);
            out.writeChar(Math.round(Math.max(0.0F, Math.min(1.0F, t[0])) * 65535.0F));
            out.writeChar(Math.round(Math.max(0.0F, Math.min(1.0F, t[1])) * 65535.0F));
         }

         if (mesh.colored) {
            for (int k : keys) {
               int c = mesh.color[k < 0 ? ~k : k];
               out.writeByte(c >> 16);
               out.writeByte(c >> 8);
               out.writeByte(c);
            }
         }

         if (jointed) {
            for (int k : keys) {
               out.writeByte(Math.round(Math.max(0.0F, Math.min(1.0F, bend[k < 0 ? ~k : k])) * 255.0F));
            }
         }

         out.writeInt(local.length / 3);
         boolean wide = keys.size() > 65535;

         for (int i : local) {
            if (wide) {
               out.writeInt(i);
            } else {
               out.writeChar(i);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   record Cut(int[] part, float[][] pivot, float[][] grip, float[][] bendAt, float[][] bendWeights) {
   }

   @Environment(EnvType.CLIENT)
   public static final class Options {
      public float height = 30.0F;
      public float flat;
      public final Map<String, String> bones = new HashMap<>();
      public final Set<String> headMaterials = new HashSet<>();
      public float neck = -1.0F;
      public float hip = -1.0F;
      public float floor = -1.0F;
   }

   @Environment(EnvType.CLIENT)
   private static final class Piece {
      final int bone;
      final int layer;
      final List<Integer> source = new ArrayList<>();
      int[][] levels;

      Piece(int bone, int layer) {
         this.bone = bone;
         this.layer = layer;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Result(byte[] data, List<String> limbs) {
   }
}
