package net.spectre.client.module.impl.render.character.gltf;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class Skeleton {
   static final int RIGHT = 0;
   static final int LEFT = 1;
   private static final Set<String> NOISE = Set.of(
      "j", "bip", "bip01", "def", "deform", "mch", "org", "jnt", "jt", "bone", "bn", "b", "c", "x", "stretch", "valve", "biped", "mixamorig"
   );
   private static final Set<String> LEFT_WORDS = Set.of("left", "l", "lf", "lft");
   private static final Set<String> RIGHT_WORDS = Set.of("right", "r", "rt", "rgt");
   private static final List<String> UPPER_ARM = List.of("upperarm", "uparm", "armupper");
   private static final List<String> FOREARM = List.of("forearm", "lowerarm", "elbow", "armlower", "loarm");
   private static final List<String> HAND = List.of("hand", "wrist");
   private static final List<String> THIGH = List.of("thigh", "upleg", "upperleg", "legupper");
   private static final List<String> SHIN = List.of("calf", "shin", "knee", "lowerleg", "leglower", "loleg");
   int head = -1;
   final int[] upperArm = new int[]{-1, -1};
   final int[] forearm = new int[]{-1, -1};
   final int[] hand = new int[]{-1, -1};
   final int[] thigh = new int[]{-1, -1};
   final int[] shin = new int[]{-1, -1};
   boolean facesBack;
   private final PosedMesh mesh;
   private final String[] core;
   private final int[] named;

   private Skeleton(PosedMesh mesh) {
      this.mesh = mesh;
      int n = mesh.file.nodes();
      this.core = new String[n];
      this.named = new int[n];

      for (int i = 0; i < n; i++) {
         List<String> words = words(mesh.file.nodeName(i));
         this.named[i] = -1;
         StringBuilder b = new StringBuilder();

         for (String w : words) {
            if (LEFT_WORDS.contains(w)) {
               this.named[i] = 1;
            } else if (RIGHT_WORDS.contains(w)) {
               this.named[i] = 0;
            } else if (!NOISE.contains(w)) {
               b.append(w);
            }
         }

         this.core[i] = b.toString().replaceAll("\\d+$", "");
      }
   }

   static Skeleton find(PosedMesh mesh, Map<String, String> bones) throws IOException {
      Skeleton s = new Skeleton(mesh);
      if (bones.isEmpty()) {
         s.facesBack = s.facingBack();
         s.head = s.shallowest(n -> s.core[n].startsWith("head"));

         for (int side = 0; side < 2; side++) {
            int sd = side;
            int namedElbow = s.shallowest(n -> s.side(n) == sd && s.is(n, FOREARM));
            int elbow = namedElbow;
            int arm = s.shallowest(
               n -> s.side(n) == sd && (s.is(n, UPPER_ARM) || s.core[n].equals("arm")) && (namedElbow < 0 || mesh.under(namedElbow, n) && n != namedElbow)
            );
            if (arm < 0 && namedElbow >= 0) {
               arm = mesh.parent[namedElbow];
            }

            if (namedElbow < 0 && arm >= 0) {
               elbow = s.mainChild(arm);
            }

            s.upperArm[sd] = arm;
            s.forearm[sd] = elbow;
            if (elbow >= 0) {
               int e = elbow;
               int wrist = s.shallowest(n -> n != e && mesh.under(n, e) && s.is(n, HAND));
               s.hand[sd] = wrist >= 0 ? wrist : s.mainChild(e);
            }

            int namedKnee = s.shallowest(n -> s.side(n) == sd && s.is(n, SHIN));
            int knee = namedKnee;
            int leg = s.shallowest(
               n -> s.side(n) == sd && (s.is(n, THIGH) || s.core[n].equals("leg")) && (namedKnee < 0 || mesh.under(namedKnee, n) && n != namedKnee)
            );
            if (namedKnee < 0 && leg >= 0) {
               int upperLeg = leg;
               knee = s.shallowest(n -> n != upperLeg && mesh.under(n, upperLeg) && s.core[n].equals("leg"));
               if (knee < 0) {
                  knee = s.mainChild(leg);
               }
            }

            if (leg < 0 && knee >= 0) {
               leg = mesh.parent[knee];
            }

            s.thigh[sd] = leg;
            s.shin[sd] = knee;
         }

         return s;
      } else {
         s.head = s.named(bones, "head");

         for (int side = 0; side < 2; side++) {
            String w = side == 0 ? "right " : "left ";
            s.upperArm[side] = s.named(bones, w + "arm");
            s.forearm[side] = s.named(bones, w + "elbow");
            s.hand[side] = s.named(bones, w + "hand");
            s.thigh[side] = s.named(bones, w + "leg");
            s.shin[side] = s.named(bones, w + "knee");
         }

         s.facesBack = s.upperArm[1] >= 0 && s.upperArm[0] >= 0 && mesh.world(s.upperArm[1])[0] < mesh.world(s.upperArm[0])[0];
         return s;
      }
   }

   private boolean is(int node, List<String> words) {
      for (String w : words) {
         if (this.core[node].contains(w)) {
            return true;
         }
      }

      return false;
   }

   private int named(Map<String, String> bones, String key) throws IOException {
      String name = bones.get(key);
      if (name == null) {
         return -1;
      } else {
         for (int i = 0; i < this.core.length; i++) {
            if (this.mesh.file.nodeName(i).equalsIgnoreCase(name)) {
               return i;
            }
         }

         String lower = name.toLowerCase(Locale.ROOT);

         for (int ix = 0; ix < this.core.length; ix++) {
            if (this.mesh.file.nodeName(ix).toLowerCase(Locale.ROOT).startsWith(lower)) {
               return ix;
            }
         }

         throw new IOException("no bone named " + name);
      }
   }

   private static List<String> words(String name) {
      String n = name.replaceAll("_\\d+$", "")
         .replaceAll("([a-z])([A-Z])", "$1 $2")
         .toLowerCase(Locale.ROOT)
         .replace("手首", " wrist ")
         .replace("足首", " ankle ")
         .replace("左", " left ")
         .replace("右", " right ")
         .replace("ひじ", " elbow ")
         .replace("ひざ", " knee ")
         .replace("腕", " arm ")
         .replace("足", " leg ")
         .replace("頭", " head ")
         .replace("首", " neck ");
      List<String> out = new ArrayList<>();

      for (String w : n.split("[^a-z0-9]+")) {
         if (!w.isEmpty()) {
            out.add(w);
         }
      }

      return out;
   }

   private boolean facingBack() {
      int left = this.shallowest(n -> this.named[n] == 1 && this.is(n, HAND));
      int right = this.shallowest(n -> this.named[n] == 0 && this.is(n, HAND));
      if (left < 0 || right < 0) {
         left = this.shallowest(n -> this.named[n] == 1 && this.is(n, SHIN));
         right = this.shallowest(n -> this.named[n] == 0 && this.is(n, SHIN));
      }

      if (left >= 0 && right >= 0) {
         return this.mesh.world(left)[0] < this.mesh.world(right)[0];
      } else {
         float[] box = this.mesh.bounds();
         float h = box[4] - box[1];
         float feet = 0.0F;
         float shins = 0.0F;
         int nf = 0;
         int ns = 0;

         for (int v = 0; v < this.mesh.vertices; v++) {
            float y = this.mesh.pos[v * 3 + 1] - box[1];
            float z = this.mesh.pos[v * 3 + 2];
            if (y < h * 0.04F) {
               feet += z;
               nf++;
            } else if (y > h * 0.1F && y < h * 0.2F) {
               shins += z;
               ns++;
            }
         }

         return nf > 0 && ns > 0 && feet / nf < shins / ns - h * 0.01F;
      }
   }

   private int side(int node) {
      if (this.named[node] >= 0) {
         return this.named[node];
      } else {
         return this.mesh.world(node)[0] < 0.0F != this.facesBack ? 0 : 1;
      }
   }

   private int shallowest(IntPredicate test) {
      int best = -1;
      int bestDepth = Integer.MAX_VALUE;

      for (int n : this.mesh.joints) {
         if (test.test(n)) {
            int depth = 0;

            for (int p = this.mesh.parent[n]; p >= 0 && depth <= this.core.length; p = this.mesh.parent[p]) {
               depth++;
            }

            if (depth < bestDepth || depth == bestDepth && n < best) {
               best = n;
               bestDepth = depth;
            }
         }
      }

      return best;
   }

   private int mainChild(int node) {
      int best = -1;
      int bestSize = -1;

      for (JsonElement c : GltfFile.array(this.mesh.file.node(node), "children")) {
         int child = c.getAsInt();
         int size = 0;

         for (int n = 0; n < this.core.length; n++) {
            if (this.mesh.under(n, child)) {
               size++;
            }
         }

         if (size > bestSize) {
            best = child;
            bestSize = size;
         }
      }

      return best;
   }

   Map<Integer, Float> armTurns() {
      Map<Integer, Float> turns = new HashMap<>();

      for (int s = 0; s < 2; s++) {
         if (this.upperArm[s] >= 0 && this.forearm[s] >= 0) {
            float[] a = this.mesh.world(this.upperArm[s]);
            float[] b = this.mesh.world(this.forearm[s]);
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float angle = (float)Math.toDegrees(Math.atan2(Math.abs(dx), -dy));
            if (angle > 40.0F) {
               turns.put(this.upperArm[s], (dx < 0.0F ? 1.0F : -1.0F) * (angle - 10.0F));
            }
         }
      }

      return turns;
   }

   int[] groups() {
      int[] out = new int[this.core.length];

      for (int n = 0; n < out.length; n++) {
         out[n] = 1;
         if (this.head >= 0 && this.mesh.under(n, this.head)) {
            out[n] = 0;
         } else if (this.upperArm[0] >= 0 && this.mesh.under(n, this.upperArm[0])) {
            out[n] = 2;
         } else if (this.upperArm[1] >= 0 && this.mesh.under(n, this.upperArm[1])) {
            out[n] = 3;
         } else if (this.thigh[0] >= 0 && this.mesh.under(n, this.thigh[0])) {
            out[n] = 4;
         } else if (this.thigh[1] >= 0 && this.mesh.under(n, this.thigh[1])) {
            out[n] = 5;
         }
      }

      return out;
   }

   boolean found() {
      return this.head >= 0 || this.upperArm[0] >= 0 || this.upperArm[1] >= 0 || this.thigh[0] >= 0 || this.thigh[1] >= 0;
   }

   String describe() {
      StringBuilder b = new StringBuilder();
      b.append("facing ").append(this.facesBack ? "-z (turned around)" : "+z").append('\n');
      this.line(b, "head", this.head);

      for (int s = 0; s < 2; s++) {
         String w = s == 0 ? "right " : "left ";
         this.line(b, w + "arm", this.upperArm[s]);
         this.line(b, w + "elbow", this.forearm[s]);
         this.line(b, w + "hand", this.hand[s]);
         this.line(b, w + "leg", this.thigh[s]);
         this.line(b, w + "knee", this.shin[s]);
      }

      return b.toString();
   }

   private void line(StringBuilder b, String role, int node) {
      b.append(String.format(Locale.ROOT, "%-11s %s%n", role, node < 0 ? "-" : this.mesh.file.nodeName(node)));
   }
}
