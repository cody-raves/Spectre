package net.spectre.client.module.impl.render.character.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CharacterBuilder {
   private static final float DECAL_RESERVE = 0.1F;
   private static final float AO_STEP = 0.38F;
   private static final int MIN_TRIANGLES = 24;
   private static final float MAX_EXTENT = 160.0F;
   private static final float OUTLINE_SHARE = 0.5F;
   private static final float BURIED = 0.02F;
   private static final float SINK = 0.12F;
   private static final int CORNER = 10;

   private CharacterBuilder() {
   }

   public static CharacterModel build(CharacterDesign design, Detail detail) {
      if (design instanceof BakedDesign baked) {
         return BakedModel.load(baked, detail);
      } else {
         long start = System.nanoTime();
         Rig rig = design.rig();
         List<Part> order = order(rig);
         int n = order.size();
         if (n == 0) {
            throw new IllegalStateException(design.id() + ": the design defines no parts");
         } else {
            Map<Part, Integer> index = new IdentityHashMap<>();

            for (int i = 0; i < n; i++) {
               index.put(order.get(i), i);
            }

            List<Shape> solids = new ArrayList<>();
            List<Canvas> canvases = new ArrayList<>();
            Shape[] shapes = new Shape[n];
            float[] steps = new float[n];

            for (int i = 0; i < n; i++) {
               Part p = order.get(i);
               if (p.shape != null) {
                  Shape s = p.shape;
                  if (!s.bounded()) {
                     throw new IllegalStateException(p.name + ": shape is unbounded; intersect planes with a box");
                  }

                  if (s.maxX - s.minX > 160.0F || s.maxY - s.minY > 160.0F || s.maxZ - s.minZ > 160.0F) {
                     throw new IllegalStateException(p.name + ": shape is larger than 160.0 units");
                  }

                  steps[i] = SurfaceNets.fitStep(s.maxX - s.minX, s.maxY - s.minY, s.maxZ - s.minZ, detail.step * p.resolution);
                  float half = steps[i] * 0.75F;
                  s = s.map(x -> (Shape)(x instanceof Strand strand ? strand.thickened(half) : x));
                  shapes[i] = s;
                  solids.add(s);
               }

               for (Decal d : p.decals) {
                  canvases.add(d.canvas);
               }
            }

            Shape world = solids.isEmpty() ? null : Shapes.union(solids.toArray(new Shape[0]));
            Shape[] covers = covers(order, shapes);
            Atlas atlas = Atlas.pack(canvases);
            float[] thickness = new float[n];

            for (int i = 0; i < n; i++) {
               Part p = order.get(i);
               thickness[i] = p.outline >= 0.0F ? p.outline : rig.outline;
            }

            Mesh[] fine = new Mesh[n];
            float[] shown = new float[n];
            IntStream.range(0, n).parallel().forEach(i -> {
               Shape sx = shapes[i];
               if (sx != null) {
                  fine[i] = SurfaceNets.mesh(sx, sx.minX, sx.minY, sx.minZ, sx.maxX, sx.maxY, sx.maxZ, steps[i]);
                  shown[i] = visible(fine[i], covers[i]);
               }
            });
            int[] baseTarget = new int[n];
            budget(order, fine, shown, detail, baseTarget, thickness);
            Mesh[] base = new Mesh[n];
            IntStream.range(0, n).parallel().forEach(i -> {
               if (fine[i] != null && fine[i].triCount != 0) {
                  Simplifier sx = new Simplifier(fine[i]);
                  sx.reduce(baseTarget[i]);
                  base[i] = sx.snapshot();
               }
            });
            PartMesh[] parts = new PartMesh[n];
            float[] extent = new float[]{Float.MAX_VALUE, -Float.MAX_VALUE};
            int[] slots = new int[n];
            int slotCount = 0;

            for (int i = 0; i < n; i++) {
               Part p = order.get(i);
               slots[i] = p.extra && p.motion != null ? slotCount++ : -1;
            }

            IntStream.range(0, n)
               .parallel()
               .forEach(
                  i -> {
                     Part px = order.get(i);
                     Surface baseSurface = null;
                     Surface hullSurface = null;
                     Surface decalSurface = null;
                     if (base[i] != null && base[i].triCount > 0) {
                        float strength = px.occlusion >= 0.0F ? px.occlusion : rig.occlusion;
                        float flat = px.flat >= 0.0F ? px.flat : rig.flat;
                        CharacterBuilder.Shaded shaded = shade(shapes[i], world, base[i], steps[i], strength, flat);
                        Mesh kept = bury(shaded, base[i], covers[i], steps[i]);
                        baseSurface = baseSurface(shaded, kept, px, atlas);
                        if (baseSurface != null && thickness[i] > 0.0F) {
                           int color = px.outlineColorSet ? px.outlineColor : rig.outlineColor;
                           hullSurface = hullSurface(shapes[i], shaded, baseSurface, px, thickness[i], steps[i], color, atlas);
                        }

                        if (!px.decals.isEmpty()) {
                           decalSurface = decalSurface(px, shaded, kept, atlas);
                        }

                        synchronized (extent) {
                           for (int v = 0; v < shaded.count; v++) {
                              extent[0] = Math.min(extent[0], shaded.pos[v * 3 + 1]);
                              extent[1] = Math.max(extent[1], shaded.pos[v * 3 + 1]);
                           }
                        }
                     }

                     int parent = px.parent == null ? -1 : index.get(px.parent);
                     Motion motion = px.extra ? px.motion : null;
                     if (motion != null && motion.bendPower() > 0.0F) {
                        float reach = reach(baseSurface);
                        weigh(baseSurface, reach, motion.bendPower());
                        if (hullSurface != null && baseSurface != null) {
                           hullSurface.weight = baseSurface.weight;
                        }

                        weigh(decalSurface, reach, motion.bendPower());
                     }

                     boolean limb = !px.extra && (px.bone.arm() || px.bone.leg());
                     Part owner = px;

                     while (owner != null && !owner.jointSet) {
                        owner = owner.parent;
                     }

                     boolean jointed = owner != null;
                     if (jointed) {
                        jointWeights(baseSurface, px, owner);
                        jointWeights(decalSurface, px, owner);
                        if (hullSurface != null && baseSurface != null) {
                           hullSurface.joint = baseSurface.joint;
                        }
                     }

                     parts[i] = new PartMesh(
                        px.name,
                        px.bone,
                        parent,
                        px.extra,
                        px.pivotX,
                        px.pivotY,
                        px.pivotZ,
                        px.gripX(),
                        px.gripY(),
                        px.gripZ(),
                        motion,
                        baseSurface,
                        hullSurface,
                        decalSurface,
                        slots[i],
                        limb ? px.swing : 1.0F,
                        limb ? px.swingLimit : 0.0F,
                        jointed,
                        jointed ? owner.jointX - px.pivotX : 0.0F,
                        jointed ? owner.jointY - px.pivotY : 0.0F,
                        jointed ? owner.jointZ - px.pivotZ : 0.0F,
                        jointed && owner.neck,
                        jointed ? owner.lookPitch : 0.0F,
                        jointed ? owner.lookYaw : 0.0F,
                        jointed ? owner.lookLimit : 0.0F
                     );
                  }
               );
            long finished = System.nanoTime();
            float hip = 12.0F;
            Part rl = rig.bones[Bone.RIGHT_LEG.ordinal()];
            Part ll = rig.bones[Bone.LEFT_LEG.ordinal()];
            if (rl != null && ll != null) {
               hip = (rl.pivotY + ll.pivotY) * 0.5F;
            } else if (rl != null) {
               hip = rl.pivotY;
            } else if (ll != null) {
               hip = ll.pivotY;
            }

            float minY = extent[0] == Float.MAX_VALUE ? 0.0F : extent[0];
            float maxY = extent[1] == -Float.MAX_VALUE ? 32.0F : extent[1];
            long total = (finished - start) / 1000000L;
            return new CharacterModel(
               design.id(),
               design.name(),
               detail,
               rig.scale,
               parts,
               slotCount,
               atlas.width,
               atlas.height,
               atlas.mips,
               atlas.whiteU(),
               atlas.whiteV(),
               minY,
               maxY,
               hip,
               rig.firstPersonArms,
               total
            );
         }
      }
   }

   private static List<Part> order(Rig rig) {
      List<Part> list = new ArrayList<>();

      for (Part p : rig.bones) {
         if (p != null) {
            list.add(p);
         }
      }

      list.addAll(rig.extras);
      return list;
   }

   private static Shape[] covers(List<Part> order, Shape[] shapes) {
      int n = order.size();
      Part[] root = new Part[n];

      for (int i = 0; i < n; i++) {
         Part r = order.get(i);

         while (r.extra && r.motion == null && r.parent != null) {
            r = r.parent;
         }

         root[i] = r;
      }

      Shape[] covers = new Shape[n];

      for (int i = 0; i < n; i++) {
         if (shapes[i] != null) {
            List<Shape> near = new ArrayList<>();

            for (int j = 0; j < n; j++) {
               if (j != i && shapes[j] != null && root[j] == root[i] && overlaps(shapes[i], shapes[j])) {
                  near.add(shapes[j]);
               }
            }

            if (!near.isEmpty()) {
               covers[i] = Shapes.union(near.toArray(new Shape[0]));
            }
         }
      }

      return covers;
   }

   private static boolean overlaps(Shape a, Shape b) {
      return a.minX <= b.maxX && b.minX <= a.maxX && a.minY <= b.maxY && b.minY <= a.maxY && a.minZ <= b.maxZ && b.minZ <= a.maxZ;
   }

   private static float visible(Mesh mesh, Shape cover) {
      if (cover != null && mesh.triCount != 0) {
         boolean[] buried = new boolean[mesh.vertexCount];

         for (int v = 0; v < mesh.vertexCount; v++) {
            buried[v] = cover.distance(mesh.pos[v * 3], mesh.pos[v * 3 + 1], mesh.pos[v * 3 + 2]) < -0.02F;
         }

         int hidden = 0;

         for (int t = 0; t < mesh.triCount; t++) {
            if (buried[mesh.tris[t * 3]] && buried[mesh.tris[t * 3 + 1]] && buried[mesh.tris[t * 3 + 2]]) {
               hidden++;
            }
         }

         return Math.max(0.05F, 1.0F - (float)hidden / mesh.triCount);
      } else {
         return 1.0F;
      }
   }

   private static void budget(List<Part> order, Mesh[] fine, float[] visible, Detail detail, int[] baseTarget, float[] thickness) {
      int n = order.size();
      float total = 0.0F;
      float outlined = 0.0F;
      float[] weight = new float[n];

      for (int i = 0; i < n; i++) {
         Part p = order.get(i);
         if (fine[i] != null) {
            weight[i] = fine[i].triCount * visible[i] * p.detail;
            total += weight[i];
            if (thickness[i] > 0.0F) {
               outlined += weight[i];
            }
         }
      }

      if (!(total <= 0.0F)) {
         float available = detail.budget * 0.9F;
         float share = available / (1.0F + outlined / total);

         for (int ix = 0; ix < n; ix++) {
            if (fine[ix] != null) {
               int target = Math.max(24, Math.round(share * weight[ix] / total / visible[ix]));
               baseTarget[ix] = Math.min(target, fine[ix].triCount);
            }
         }
      }
   }

   private static CharacterBuilder.Shaded shade(Shape shape, Shape world, Mesh mesh, float step, float occlusion, float flat) {
      CharacterBuilder.Shaded out = new CharacterBuilder.Shaded(mesh.vertexCount);
      int chunks = (mesh.vertexCount + 255) / 256;
      IntStream.range(0, chunks).parallel().forEach(c -> {
         float[] p = new float[3];
         float[] g = new float[3];
         Sample sample = new Sample();
         int end = Math.min(mesh.vertexCount, (c + 1) * 256);

         for (int v = c * 256; v < end; v++) {
            p[0] = mesh.pos[v * 3];
            p[1] = mesh.pos[v * 3 + 1];
            p[2] = mesh.pos[v * 3 + 2];
            project(shape, p, g, step);
            System.arraycopy(p, 0, out.pos, v * 3, 3);
            System.arraycopy(g, 0, out.nrm, v * 3, 3);
            shape.sample(p[0], p[1], p[2], sample);
            float ao = occlusion(world, p[0], p[1], p[2], g[0], g[1], g[2], sample.occlusion(occlusion));
            ao += (1.0F - ao) * Math.min(1.0F, sample.glow);
            out.ao[v] = ao;
            out.flat[v] = sample.flat(flat);
            out.glow[v] = (byte)(sample.glow >= 0.5F ? 1 : 0);
            out.color[v] = 0xFF000000 | Sample.channel(sample.r * ao) << 16 | Sample.channel(sample.g * ao) << 8 | Sample.channel(sample.b * ao);
         }
      });
      return out;
   }

   private static Mesh bury(CharacterBuilder.Shaded s, Mesh mesh, Shape cover, float step) {
      if (cover == null) {
         return mesh;
      } else {
         float[] depth = new float[s.count];
         int chunks = (s.count + 255) / 256;
         IntStream.range(0, chunks).parallel().forEach(cxx -> {
            float[] p = new float[3];
            float[] g = new float[3];
            int end = Math.min(s.count, (cxx + 1) * 256);

            for (int v = cxx * 256; v < end; v++) {
               p[0] = s.pos[v * 3];
               p[1] = s.pos[v * 3 + 1];
               p[2] = s.pos[v * 3 + 2];
               float d = cover.distance(p[0], p[1], p[2]);
               depth[v] = d;
               if (d < 0.0F) {
                  gradient(cover, p, Math.max(0.01F, step * 0.3F), g);
                  float sink = Math.min(0.12F, -2.0F * d);
                  s.pos[v * 3] = p[0] - g[0] * sink;
                  s.pos[v * 3 + 1] = p[1] - g[1] * sink;
                  s.pos[v * 3 + 2] = p[2] - g[2] * sink;
               }
            }
         });
         s.cover = depth;
         int[] kept = new int[mesh.triCount * 3];
         int count = 0;

         for (int t = 0; t < mesh.triCount; t++) {
            int a = mesh.tris[t * 3];
            int b = mesh.tris[t * 3 + 1];
            int c = mesh.tris[t * 3 + 2];
            if (depth[a] < -0.02F && depth[b] < -0.02F && depth[c] < -0.02F) {
               float cx = (mesh.pos[a * 3] + mesh.pos[b * 3] + mesh.pos[c * 3]) / 3.0F;
               float cy = (mesh.pos[a * 3 + 1] + mesh.pos[b * 3 + 1] + mesh.pos[c * 3 + 1]) / 3.0F;
               float cz = (mesh.pos[a * 3 + 2] + mesh.pos[b * 3 + 2] + mesh.pos[c * 3 + 2]) / 3.0F;
               if (cover.distance(cx, cy, cz) < -0.02F) {
                  continue;
               }
            }

            kept[count * 3] = a;
            kept[count * 3 + 1] = b;
            kept[count * 3 + 2] = c;
            count++;
         }

         return count == mesh.triCount ? mesh : new Mesh(mesh.pos, mesh.vertexCount, Arrays.copyOf(kept, count * 3), count);
      }
   }

   static void project(Shape shape, float[] p, float[] n, float step) {
      project(shape, p, n, step, 0.0F);
   }

   static void project(Shape shape, float[] p, float[] n, float step, float iso) {
      float eps = Math.max(0.01F, step * 0.3F);
      float tolerance = step * 0.01F;
      float d = shape.distance(p[0], p[1], p[2]) - iso;
      gradient(shape, p, eps, n);

      for (int it = 0; it < 2 && Math.abs(d) > tolerance; it++) {
         float move = Math.max(-step, Math.min(step, d));
         p[0] -= n[0] * move;
         p[1] -= n[1] * move;
         p[2] -= n[2] * move;
         d = shape.distance(p[0], p[1], p[2]) - iso;
         gradient(shape, p, eps, n);
      }
   }

   static void gradient(Shape shape, float[] p, float eps, float[] out) {
      float x = p[0];
      float y = p[1];
      float z = p[2];
      float gx = shape.distance(x + eps, y, z) - shape.distance(x - eps, y, z);
      float gy = shape.distance(x, y + eps, z) - shape.distance(x, y - eps, z);
      float gz = shape.distance(x, y, z + eps) - shape.distance(x, y, z - eps);
      float l = (float)Math.sqrt(gx * gx + gy * gy + gz * gz);
      if (l < 1.0E-12F) {
         out[0] = 0.0F;
         out[1] = 1.0F;
         out[2] = 0.0F;
      } else {
         out[0] = gx / l;
         out[1] = gy / l;
         out[2] = gz / l;
      }
   }

   private static float occlusion(Shape world, float x, float y, float z, float nx, float ny, float nz, float strength) {
      if (world != null && !(strength <= 0.0F)) {
         float occ = 0.0F;
         float total = 0.0F;
         float w = 1.0F;

         for (int i = 1; i <= 5; i++) {
            float h = 0.38F * i;
            float d = world.distance(x + nx * h, y + ny * h, z + nz * h);
            occ += Math.max(0.0F, h - d) * w;
            total += h * w;
            w *= 0.72F;
         }

         float k = Math.min(1.0F, occ / total * 1.6F);
         return 1.0F - strength * k * (1.4F - 0.4F * k);
      } else {
         return 1.0F;
      }
   }

   private static Surface baseSurface(CharacterBuilder.Shaded s, Mesh mesh, Part p, Atlas atlas) {
      if (mesh.triCount == 0) {
         return null;
      } else {
         Surface.Builder b = new Surface.Builder();
         float u = atlas.whiteU();
         float v = atlas.whiteV();

         for (int i = 0; i < s.count; i++) {
            b.vertex(
               s.pos[i * 3] - p.pivotX,
               s.pos[i * 3 + 1] - p.pivotY,
               s.pos[i * 3 + 2] - p.pivotZ,
               s.nrm[i * 3],
               s.nrm[i * 3 + 1],
               s.nrm[i * 3 + 2],
               u,
               v,
               s.color[i],
               s.glow[i] != 0,
               s.flat[i]
            );
         }

         pair(mesh.tris, mesh.triCount, mesh.vertexCount, b);
         return b.build();
      }
   }

   private static Surface hullSurface(Shape shape, CharacterBuilder.Shaded s, Surface base, Part p, float thickness, float step, int color, Atlas atlas) {
      float[] hull = new float[s.count * 3];
      float[] width = new float[s.count];
      float reach = thickness / 0.5F * 1.5F;
      int chunks = (s.count + 255) / 256;
      IntStream.range(0, chunks).parallel().forEach(cx -> {
         float[] qx = new float[3];
         float[] g = new float[3];
         int end = Math.min(s.count, (cx + 1) * 256);

         for (int i = cx * 256; i < end; i++) {
            float px = s.pos[i * 3];
            float py = s.pos[i * 3 + 1];
            float pz = s.pos[i * 3 + 2];
            float nx = s.nrm[i * 3];
            float ny = s.nrm[i * 3 + 1];
            float nz = s.nrm[i * 3 + 2];
            float w = s.cover != null && s.cover[i] < 0.0F ? 0.0F : thickness;
            if (w > 0.0F) {
               float inner = 0.0F;

               for (int k = 1; k <= 5; k++) {
                  float t = reach * k / 5.0F;
                  inner = Math.max(inner, -shape.distance(px - nx * t, py - ny * t, pz - nz * t));
               }

               w = Math.min(w, 0.5F * inner);
            }

            qx[0] = px + nx * w;
            qx[1] = py + ny * w;
            qx[2] = pz + nz * w;
            if (w > 0.0F) {
               project(shape, qx, g, step, w);
            }

            hull[i * 3] = qx[0];
            hull[i * 3 + 1] = qx[1];
            hull[i * 3 + 2] = qx[2];
            width[i] = w;
         }
      });
      Surface.Builder b = new Surface.Builder();
      float u = atlas.whiteU();
      float v = atlas.whiteV();

      for (int i = 0; i < s.count; i++) {
         b.vertex(hull[i * 3] - p.pivotX, hull[i * 3 + 1] - p.pivotY, hull[i * 3 + 2] - p.pivotZ, 0.0F, 1.0F, 0.0F, u, v, color | 0xFF000000, false);
      }

      int[] quads = base.quads;

      for (int q = 0; q < quads.length; q += 4) {
         int a = quads[q];
         int bb = quads[q + 1];
         int c = quads[q + 2];
         int d = quads[q + 3];
         if (!(width[a] <= 0.0F) || !(width[bb] <= 0.0F) || !(width[c] <= 0.0F) || !(width[d] <= 0.0F)) {
            b.quad(a, d, c, bb, c == d ? 1 : 2);
         }
      }

      return b.build();
   }

   static void pair(int[] tris, int count, int vertices, Surface.Builder out) {
      long[] edges = new long[count * 3];

      for (int t = 0; t < count; t++) {
         for (int e = 0; e < 3; e++) {
            int a = tris[t * 3 + e];
            int b = tris[t * 3 + (e + 1) % 3];
            long key = (long)Math.min(a, b) * vertices + Math.max(a, b);
            edges[t * 3 + e] = key << 21 | t * 3L + e;
         }
      }

      Arrays.sort(edges);
      int[] neighbour = new int[count * 3];
      Arrays.fill(neighbour, -1);
      long mask = 2097151L;

      for (int i = 0; i + 1 < edges.length; i++) {
         if (edges[i] >>> 21 == edges[i + 1] >>> 21) {
            if (i + 2 < edges.length && edges[i + 2] >>> 21 == edges[i] >>> 21) {
               while (i + 1 < edges.length && edges[i + 1] >>> 21 == edges[i] >>> 21) {
                  i++;
               }
            } else {
               int h0 = (int)(edges[i] & mask);
               int h1 = (int)(edges[i + 1] & mask);
               neighbour[h0] = h1;
               neighbour[h1] = h0;
               i++;
            }
         }
      }

      boolean[] used = new boolean[count];

      for (int t = 0; t < count; t++) {
         if (!used[t]) {
            int pick = -1;

            for (int e = 0; e < 3 && pick < 0; e++) {
               int h = neighbour[t * 3 + e];
               if (h >= 0 && !used[h / 3] && h / 3 != t) {
                  pick = e;
               }
            }

            int a0 = tris[t * 3];
            int a1 = tris[t * 3 + 1];
            int a2 = tris[t * 3 + 2];
            if (pick < 0) {
               used[t] = true;
               out.quad(a0, a1, a2, a2, 1);
            } else {
               int h = neighbour[t * 3 + pick];
               int o = h / 3;
               used[t] = true;
               used[o] = true;
               int a = tris[t * 3 + pick];
               int b = tris[t * 3 + (pick + 1) % 3];
               int c = tris[t * 3 + (pick + 2) % 3];
               int d = tris[o * 3 + (h % 3 + 2) % 3];
               out.quad(a, d, b, c, 2);
            }
         }
      }
   }

   private static Surface decalSurface(Part p, CharacterBuilder.Shaded s, Mesh mesh, Atlas atlas) {
      Surface.Builder b = new Surface.Builder();
      float[] poly = new float[200];
      float[] tmp = new float[200];

      for (Decal d : p.decals) {
         for (int t = 0; t < mesh.triCount; t++) {
            int i0 = mesh.tris[t * 3];
            int i1 = mesh.tris[t * 3 + 1];
            int i2 = mesh.tris[t * 3 + 2];
            if (faces(d, s, i0, i1, i2)) {
               int[] idx = new int[]{i0, i1, i2};
               float firstAngle = 0.0F;

               for (int c = 0; c < 3; c++) {
                  int v = idx[c];
                  int o = c * 10;
                  poly[o] = s.pos[v * 3];
                  poly[o + 1] = s.pos[v * 3 + 1];
                  poly[o + 2] = s.pos[v * 3 + 2];
                  poly[o + 3] = s.nrm[v * 3];
                  poly[o + 4] = s.nrm[v * 3 + 1];
                  poly[o + 5] = s.nrm[v * 3 + 2];
                  poly[o + 8] = s.ao[v];
                  poly[o + 9] = s.flat[v];
                  float rx = poly[o] - d.cx;
                  float ry = poly[o + 1] - d.cy;
                  float rz = poly[o + 2] - d.cz;
                  if (!d.wrap) {
                     poly[o + 6] = 0.5F + (rx * d.rx + ry * d.ry + rz * d.rz) / d.width;
                     poly[o + 7] = 0.5F - (rx * d.ux + ry * d.uy + rz * d.uz) / d.height;
                  } else {
                     float along = rx * d.dx + ry * d.dy + rz * d.dz;
                     float fx = rx - along * d.dx;
                     float fy = ry - along * d.dy;
                     float fz = rz - along * d.dz;
                     float angle = (float)Math.toDegrees(Math.atan2(fx * d.rx + fy * d.ry + fz * d.rz, fx * d.ux + fy * d.uy + fz * d.uz));
                     if (c == 0) {
                        firstAngle = angle;
                     } else {
                        float delta = angle - firstAngle;

                        while (delta > 180.0F) {
                           delta -= 360.0F;
                        }

                        while (delta < -180.0F) {
                           delta += 360.0F;
                        }

                        angle = firstAngle + delta;
                     }

                     poly[o + 6] = 0.5F + angle / d.width;
                     poly[o + 7] = 0.5F - along / d.height;
                  }
               }

               emitClipped(d, poly, tmp, 0.0F, p, atlas, b);
               if (d.wrap && d.width >= 359.9F) {
                  emitClipped(d, poly, tmp, 1.0F, p, atlas, b);
                  emitClipped(d, poly, tmp, -1.0F, p, atlas, b);
               }
            }
         }
      }

      return b.build();
   }

   private static boolean faces(Decal d, CharacterBuilder.Shaded s, int i0, int i1, int i2) {
      float ax = s.pos[i0 * 3];
      float ay = s.pos[i0 * 3 + 1];
      float az = s.pos[i0 * 3 + 2];
      float ux = s.pos[i1 * 3] - ax;
      float uy = s.pos[i1 * 3 + 1] - ay;
      float uz = s.pos[i1 * 3 + 2] - az;
      float vx = s.pos[i2 * 3] - ax;
      float vy = s.pos[i2 * 3 + 1] - ay;
      float vz = s.pos[i2 * 3 + 2] - az;
      float nx = uy * vz - uz * vy;
      float ny = uz * vx - ux * vz;
      float nz = ux * vy - uy * vx;
      float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (nl < 1.0E-10F) {
         return false;
      } else {
         nx /= nl;
         ny /= nl;
         nz /= nl;
         int[] idx = new int[]{i0, i1, i2};
         if (!d.wrap) {
            if (nx * d.dx + ny * d.dy + nz * d.dz < d.cosLimit) {
               return false;
            } else {
               for (int v : idx) {
                  float depth = (s.pos[v * 3] - d.cx) * d.dx + (s.pos[v * 3 + 1] - d.cy) * d.dy + (s.pos[v * 3 + 2] - d.cz) * d.dz;
                  if (depth < d.near || depth > d.far) {
                     return false;
                  }
               }

               return true;
            }
         } else {
            float cx = (s.pos[i0 * 3] + s.pos[i1 * 3] + s.pos[i2 * 3]) / 3.0F - d.cx;
            float cy = (s.pos[i0 * 3 + 1] + s.pos[i1 * 3 + 1] + s.pos[i2 * 3 + 1]) / 3.0F - d.cy;
            float cz = (s.pos[i0 * 3 + 2] + s.pos[i1 * 3 + 2] + s.pos[i2 * 3 + 2]) / 3.0F - d.cz;
            float along = cx * d.dx + cy * d.dy + cz * d.dz;
            float rx = cx - along * d.dx;
            float ry = cy - along * d.dy;
            float rz = cz - along * d.dz;
            float rl = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1.0E-5F) {
               return false;
            } else if ((nx * rx + ny * ry + nz * rz) / rl < d.cosLimit) {
               return false;
            } else {
               float near = d.near == -Float.MAX_VALUE ? 0.0F : d.near;
               return rl >= near && rl <= d.far;
            }
         }
      }
   }

   private static void emitClipped(Decal d, float[] tri, float[] tmp, float shift, Part p, Atlas atlas, Surface.Builder out) {
      float[] a = new float[200];
      System.arraycopy(tri, 0, a, 0, 30);
      if (shift != 0.0F) {
         for (int c = 0; c < 3; c++) {
            a[c * 10 + 6] = a[c * 10 + 6] + shift;
         }
      }

      int n = 3;
      n = clip(a, n, tmp, 6, 0.0F, true);
      n = clip(tmp, n, a, 6, 1.0F, false);
      n = clip(a, n, tmp, 7, 0.0F, true);
      n = clip(tmp, n, a, 7, 1.0F, false);
      if (n >= 3) {
         int first = -1;

         for (int c = 0; c < n; c++) {
            int o = c * 10;
            float nx = a[o + 3];
            float ny = a[o + 4];
            float nz = a[o + 5];
            float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 1.0E-8F) {
               nx /= l;
               ny /= l;
               nz /= l;
            }

            int shade = Sample.channel(1.0F - d.occlusion * (1.0F - a[o + 8]));
            int argb = 0xFF000000 | shade << 16 | shade << 8 | shade;
            float flat = d.flat == d.flat ? d.flat : a[o + 9];
            int idx = out.vertex(
               a[o] + nx * d.offset - p.pivotX,
               a[o + 1] + ny * d.offset - p.pivotY,
               a[o + 2] + nz * d.offset - p.pivotZ,
               nx,
               ny,
               nz,
               atlas.u(d.canvas, a[o + 6]),
               atlas.v(d.canvas, a[o + 7]),
               argb,
               d.glow,
               flat
            );
            if (first < 0) {
               first = idx;
            }
         }

         int k;
         for (k = 1; k + 2 < n; k += 2) {
            out.quad(first, first + k, first + k + 1, first + k + 2, 2);
         }

         if (k + 1 < n) {
            out.quad(first, first + k, first + k + 1, first + k + 1, 1);
         }
      }
   }

   private static int clip(float[] in, int n, float[] out, int comp, float bound, boolean keepAbove) {
      int m = 0;

      for (int i = 0; i < n; i++) {
         int cur = i * 10;
         int prev = (i + n - 1) % n * 10;
         float cd = keepAbove ? in[cur + comp] - bound : bound - in[cur + comp];
         float pd = keepAbove ? in[prev + comp] - bound : bound - in[prev + comp];
         if (cd >= 0.0F) {
            if (pd < 0.0F) {
               m = cut(in, prev, cur, pd, cd, out, m);
            }

            System.arraycopy(in, cur, out, m * 10, 10);
            m++;
         } else if (pd >= 0.0F) {
            m = cut(in, prev, cur, pd, cd, out, m);
         }

         if (m >= 19) {
            break;
         }
      }

      return m;
   }

   private static int cut(float[] in, int a, int b, float da, float db, float[] out, int m) {
      float t = da / (da - db);

      for (int k = 0; k < 10; k++) {
         out[m * 10 + k] = in[a + k] + (in[b + k] - in[a + k]) * t;
      }

      return m + 1;
   }

   private static float reach(Surface s) {
      if (s == null) {
         return 1.0F;
      } else {
         float r = 0.0F;

         for (int i = 0; i < s.vertexCount(); i++) {
            float x = s.pos[i * 3];
            float y = s.pos[i * 3 + 1];
            float z = s.pos[i * 3 + 2];
            r = Math.max(r, x * x + y * y + z * z);
         }

         return Math.max(0.5F, (float)Math.sqrt(r));
      }
   }

   private static void jointWeights(Surface s, Part p, Part owner) {
      if (s != null) {
         float[] w = new float[s.vertexCount()];
         float ox = owner.jointX - p.pivotX;
         float oy = owner.jointY - p.pivotY;
         float oz = owner.jointZ - p.pivotZ;
         float blend = owner.jointBlend;
         boolean any = false;

         for (int i = 0; i < w.length; i++) {
            float along = (s.pos[i * 3] - ox) * owner.jointDirX + (s.pos[i * 3 + 1] - oy) * owner.jointDirY + (s.pos[i * 3 + 2] - oz) * owner.jointDirZ;
            float t = Math.max(0.0F, Math.min(1.0F, (along + blend) / (2.0F * blend)));
            w[i] = t * t * (3.0F - 2.0F * t);
            any |= w[i] > 0.0F;
         }

         if (any) {
            s.joint = w;
         }
      }
   }

   private static void weigh(Surface s, float reach, float power) {
      if (s != null) {
         float[] w = new float[s.vertexCount()];

         for (int i = 0; i < w.length; i++) {
            float x = s.pos[i * 3];
            float y = s.pos[i * 3 + 1];
            float z = s.pos[i * 3 + 2];
            float t = Math.min(1.0F, (float)Math.sqrt(x * x + y * y + z * z) / reach);
            w[i] = (float)Math.pow(t, power);
         }

         s.weight = w;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Shaded {
      final int count;
      final float[] pos;
      final float[] nrm;
      final int[] color;
      final byte[] glow;
      final float[] ao;
      final float[] flat;
      float[] cover;

      Shaded(int count) {
         this.count = count;
         this.pos = new float[count * 3];
         this.nrm = new float[count * 3];
         this.color = new int[count];
         this.glow = new byte[count];
         this.ao = new float[count];
         this.flat = new float[count];
      }
   }
}
