package net.spectre.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class BlockClusterGeometry {
   private static final int[][] AXES = new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

   private BlockClusterGeometry() {
   }

   public static BlockClusterGeometry.Mesh build(Collection<BlockPos> blocks, BlockClusterGeometry.Membership same) {
      List<int[]> unitEdges = new ArrayList<>(blocks.size() * 4);
      List<BlockClusterGeometry.Face> faces = new ArrayList<>(blocks.size() * 3);

      for (BlockPos pos : blocks) {
         int[] p = new int[]{pos.getX(), pos.getY(), pos.getZ()};
         addFaces(p, same, faces);
         addEdges(p, same, unitEdges);
      }

      return new BlockClusterGeometry.Mesh(mergeCollinear(unitEdges), faces);
   }

   private static List<BlockClusterGeometry.Segment> mergeCollinear(List<int[]> unitEdges) {
      Map<List<Integer>, List<Integer>> lines = new HashMap<>();

      for (int[] e : unitEdges) {
         int axis = e[0];
         int u = (axis + 1) % 3;
         int v = (axis + 2) % 3;
         lines.computeIfAbsent(List.of(axis, e[1 + u], e[1 + v]), k -> new ArrayList<>()).add(e[1 + axis]);
      }

      List<BlockClusterGeometry.Segment> merged = new ArrayList<>(unitEdges.size());

      for (Entry<List<Integer>, List<Integer>> line : lines.entrySet()) {
         int axis = line.getKey().get(0);
         int u = (axis + 1) % 3;
         int v = (axis + 2) % 3;
         List<Integer> starts = line.getValue();
         starts.sort(null);
         int runStart = starts.get(0);
         int runEnd = runStart + 1;

         for (int i = 1; i <= starts.size(); i++) {
            if (i < starts.size() && starts.get(i) == runEnd) {
               runEnd++;
            } else {
               int[] a = new int[3];
               int[] b = new int[3];
               a[u] = b[u] = line.getKey().get(1);
               a[v] = b[v] = line.getKey().get(2);
               a[axis] = runStart;
               b[axis] = runEnd;
               merged.add(new BlockClusterGeometry.Segment(vec(a), vec(b)));
               if (i < starts.size()) {
                  runStart = starts.get(i);
                  runEnd = runStart + 1;
               }
            }
         }
      }

      return merged;
   }

   public static List<List<BlockPos>> connectedComponents(Collection<BlockPos> blocks) {
      Set<BlockPos> remaining = new HashSet<>(blocks);
      List<List<BlockPos>> components = new ArrayList<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();

      while (!remaining.isEmpty()) {
         BlockPos seed = remaining.iterator().next();
         remaining.remove(seed);
         List<BlockPos> component = new ArrayList<>();
         queue.add(seed);

         while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            component.add(current);

            for (int[] axis : AXES) {
               for (int sign = -1; sign <= 1; sign += 2) {
                  BlockPos next = current.offset(axis[0] * sign, axis[1] * sign, axis[2] * sign);
                  if (remaining.remove(next)) {
                     queue.add(next);
                  }
               }
            }
         }

         components.add(component);
      }

      return components;
   }

   private static void addFaces(int[] p, BlockClusterGeometry.Membership same, List<BlockClusterGeometry.Face> out) {
      for (int axis = 0; axis < 3; axis++) {
         int u = (axis + 1) % 3;
         int v = (axis + 2) % 3;

         for (int sign = -1; sign <= 1; sign += 2) {
            if (!same.contains(p[0] + AXES[axis][0] * sign, p[1] + AXES[axis][1] * sign, p[2] + AXES[axis][2] * sign)) {
               int[] base = (int[])p.clone();
               if (sign > 0) {
                  base[axis]++;
               }

               out.add(new BlockClusterGeometry.Face(corner(base, u, 0, v, 0), corner(base, u, 1, v, 0), corner(base, u, 1, v, 1), corner(base, u, 0, v, 1)));
            }
         }
      }
   }

   private static void addEdges(int[] p, BlockClusterGeometry.Membership same, List<int[]> out) {
      for (int axis = 0; axis < 3; axis++) {
         int u = (axis + 1) % 3;
         int v = (axis + 2) % 3;

         for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
               boolean nu = contains(same, p, u, su, -1, 0);
               boolean nv = contains(same, p, v, sv, -1, 0);
               boolean diagonal = contains(same, p, u, su, v, sv);
               boolean convex = !nu && !nv;
               boolean concave = nu && nv && !diagonal;
               if (convex || concave) {
                  int[] start = (int[])p.clone();
                  if (su > 0) {
                     start[u]++;
                  }

                  if (sv > 0) {
                     start[v]++;
                  }

                  out.add(new int[]{axis, start[0], start[1], start[2]});
               }
            }
         }
      }
   }

   private static boolean contains(BlockClusterGeometry.Membership same, int[] p, int axisA, int signA, int axisB, int signB) {
      int[] q = (int[])p.clone();
      q[axisA] += signA;
      if (axisB >= 0) {
         q[axisB] += signB;
      }

      return same.contains(q[0], q[1], q[2]);
   }

   private static Vec3 corner(int[] base, int u, int du, int v, int dv) {
      int[] q = (int[])base.clone();
      q[u] += du;
      q[v] += dv;
      return vec(q);
   }

   private static Vec3 vec(int[] p) {
      return new Vec3(p[0], p[1], p[2]);
   }

   @Environment(EnvType.CLIENT)
   public record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Membership {
      boolean contains(int var1, int var2, int var3);
   }

   @Environment(EnvType.CLIENT)
   public record Mesh(List<BlockClusterGeometry.Segment> edges, List<BlockClusterGeometry.Face> faces) {
   }

   @Environment(EnvType.CLIENT)
   public record Segment(Vec3 a, Vec3 b) {
   }
}
