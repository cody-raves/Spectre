package net.spectre.client.esp;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.impl.client.PerformanceModule;
import net.spectre.client.util.BlockBoxHelper;
import net.spectre.client.util.BlockClusterGeometry;
import net.spectre.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class BlockLayer {
   private static final long REVEAL_NANOS = 380000000L;
   private static final double FADE_START = 64.0;
   private static final double FADE_END = 256.0;
   private static final float FADE_FLOOR = 0.45F;
   private final BlockLayer.Palette palette;
   private final Supplier<Object> dimension;
   private final Map<Object, BlockLayer.World> worlds = new HashMap<>();

   public BlockLayer(BlockLayer.Palette palette, Supplier<Object> dimension) {
      this.palette = palette;
      this.dimension = dimension;
   }

   public void put(long pos, Object kind) {
      BlockLayer.World world = this.world(true);
      if (world != null) {
         BlockLayer.Found existing = (BlockLayer.Found)world.positions.get(pos);
         if (existing == null || !Objects.equals(existing.kind(), kind)) {
            world.positions.put(pos, new BlockLayer.Found(kind, System.nanoTime()));
            ((LongOpenHashSet)world.byChunk.computeIfAbsent(chunkOf(pos), k -> new LongOpenHashSet())).add(pos);
            markDirty(world, pos);
         }
      }
   }

   public void remove(long pos) {
      BlockLayer.World world = this.world(false);
      if (world != null && world.positions.remove(pos) != null) {
         long chunk = chunkOf(pos);
         LongOpenHashSet set = (LongOpenHashSet)world.byChunk.get(chunk);
         if (set != null && set.remove(pos) && set.isEmpty()) {
            world.byChunk.remove(chunk);
         }

         markDirty(world, pos);
      }
   }

   public void clear() {
      this.worlds.clear();
   }

   public void retainChunk(long chunkKey, LongPredicate keep) {
      BlockLayer.World world = this.world(false);
      if (world != null) {
         LongOpenHashSet set = (LongOpenHashSet)world.byChunk.get(chunkKey);
         if (set != null) {
            for (long pos : set.toLongArray()) {
               if (!keep.test(pos)) {
                  this.remove(pos);
               }
            }
         }
      }
   }

   public List<BlockLayer.Vein> veins() {
      BlockLayer.World world = this.world(false);
      if (world == null) {
         return List.of();
      } else {
         this.rebuildDirty(world);
         if (world.veinsDirty) {
            world.veins = joinVeins(world);
            world.veinsDirty = false;
         }

         return world.veins;
      }
   }

   private BlockLayer.World world(boolean create) {
      Object key = this.dimension.get();
      if (key == null) {
         return null;
      } else {
         return create ? this.worlds.computeIfAbsent(key, k -> new BlockLayer.World()) : this.worlds.get(key);
      }
   }

   private static long chunkKey(int cx, int cz) {
      return cx & 4294967295L | (long)cz << 32;
   }

   private static long chunkOf(long pos) {
      return chunkKey(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
   }

   private static void markDirty(BlockLayer.World world, long pos) {
      int x = BlockPos.getX(pos);
      int z = BlockPos.getZ(pos);
      int cx = x >> 4;
      int cz = z >> 4;
      world.dirty.add(chunkKey(cx, cz));
      if ((x & 15) == 0) {
         world.dirty.add(chunkKey(cx - 1, cz));
      }

      if ((x & 15) == 15) {
         world.dirty.add(chunkKey(cx + 1, cz));
      }

      if ((z & 15) == 0) {
         world.dirty.add(chunkKey(cx, cz - 1));
      }

      if ((z & 15) == 15) {
         world.dirty.add(chunkKey(cx, cz + 1));
      }
   }

   private void rebuildDirty(BlockLayer.World world) {
      if (!world.dirty.isEmpty()) {
         LongIterator var2 = world.dirty.iterator();

         while (var2.hasNext()) {
            long chunk = (Long)var2.next();
            this.rebuildChunk(world, chunk);
         }

         world.dirty.clear();
         world.veinsDirty = true;
      }
   }

   private void rebuildChunk(BlockLayer.World world, long chunkKey) {
      LongOpenHashSet set = (LongOpenHashSet)world.byChunk.get(chunkKey);
      if (set == null) {
         world.shapes.remove(chunkKey);
      } else {
         Map<Object, List<BlockPos>> byKind = new LinkedHashMap<>();
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxY = Integer.MIN_VALUE;
         int maxZ = Integer.MIN_VALUE;
         LongIterator cx = set.iterator();

         while (cx.hasNext()) {
            long pos = (Long)cx.next();
            BlockPos block = BlockPos.of(pos);
            byKind.computeIfAbsent(((BlockLayer.Found)world.positions.get(pos)).kind(), k -> new ArrayList<>()).add(block);
            minX = Math.min(minX, block.getX());
            maxX = Math.max(maxX, block.getX());
            minY = Math.min(minY, block.getY());
            maxY = Math.max(maxY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxZ = Math.max(maxZ, block.getZ());
         }

         int cxx = (int)chunkKey;
         int cz = (int)(chunkKey >> 32);
         List<BlockLayer.Cluster> clusters = new ArrayList<>();

         for (Entry<Object, List<BlockPos>> entry : byKind.entrySet()) {
            Object kind = entry.getKey();
            BlockClusterGeometry.Membership same = (x, y, z) -> {
               BlockLayer.Found found = (BlockLayer.Found)world.positions.get(BlockPos.asLong(x, y, z));
               return found != null && Objects.equals(found.kind(), kind);
            };

            for (List<BlockPos> component : BlockClusterGeometry.connectedComponents(entry.getValue())) {
               clusters.add(buildCluster(world, kind, component, same, cxx, cz));
            }
         }

         world.shapes.put(chunkKey, new BlockLayer.Shapes(clusters, new AABB(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 2, maxZ + 2)));
      }
   }

   private static BlockLayer.Cluster buildCluster(
      BlockLayer.World world, Object kind, List<BlockPos> component, BlockClusterGeometry.Membership same, int cx, int cz
   ) {
      long[] positions = new long[component.size()];
      List<Long> links = new ArrayList<>();
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      long bornAt = Long.MAX_VALUE;

      for (int i = 0; i < positions.length; i++) {
         BlockPos pos = component.get(i);
         positions[i] = pos.asLong();
         sx += pos.getX() + 0.5;
         sy += pos.getY() + 0.5;
         sz += pos.getZ() + 0.5;
         bornAt = Math.min(bornAt, ((BlockLayer.Found)world.positions.get(positions[i])).bornAt());

         for (BlockPos next : new BlockPos[]{pos.east(), pos.west(), pos.south(), pos.north()}) {
            if ((next.getX() >> 4 != cx || next.getZ() >> 4 != cz) && same.contains(next.getX(), next.getY(), next.getZ())) {
               links.add(next.asLong());
            }
         }
      }

      int n = positions.length;
      return new BlockLayer.Cluster(
         kind,
         positions,
         new Vec3(sx / n, sy / n, sz / n),
         bornAt,
         BlockClusterGeometry.build(component, same),
         links.stream().mapToLong(Long::longValue).toArray()
      );
   }

   private static List<BlockLayer.Vein> joinVeins(BlockLayer.World world) {
      List<BlockLayer.Cluster> all = new ArrayList<>();
      Long2ObjectOpenHashMap<BlockLayer.Cluster> owner = new Long2ObjectOpenHashMap();
      ObjectIterator parent = world.shapes.values().iterator();

      while (parent.hasNext()) {
         BlockLayer.Shapes shapes = (BlockLayer.Shapes)parent.next();

         for (BlockLayer.Cluster cluster : shapes.clusters()) {
            all.add(cluster);
            if (cluster.links().length != 0) {
               for (long pos : cluster.positions()) {
                  int lx = BlockPos.getX(pos) & 15;
                  int lz = BlockPos.getZ(pos) & 15;
                  if (lx == 0 || lx == 15 || lz == 0 || lz == 15) {
                     owner.put(pos, cluster);
                  }
               }
            }
         }
      }

      Map<BlockLayer.Cluster, BlockLayer.Cluster> parentx = new IdentityHashMap<>();

      for (BlockLayer.Cluster clusterx : all) {
         parentx.put(clusterx, clusterx);
      }

      for (BlockLayer.Cluster clusterx : all) {
         for (long link : clusterx.links()) {
            BlockLayer.Cluster other = (BlockLayer.Cluster)owner.get(link);
            if (other != null) {
               parentx.put(find(parentx, clusterx), find(parentx, other));
            }
         }
      }

      Map<BlockLayer.Cluster, double[]> sums = new IdentityHashMap<>();

      for (BlockLayer.Cluster clusterx : all) {
         double[] sum = sums.computeIfAbsent(find(parentx, clusterx), r -> new double[4]);
         int n = clusterx.positions().length;
         sum[0] += clusterx.center().x * n;
         sum[1] += clusterx.center().y * n;
         sum[2] += clusterx.center().z * n;
         sum[3] += n;
      }

      List<BlockLayer.Vein> veins = new ArrayList<>(sums.size());

      for (Entry<BlockLayer.Cluster, double[]> entry : sums.entrySet()) {
         double[] s = entry.getValue();
         veins.add(new BlockLayer.Vein(entry.getKey().kind(), new Vec3(s[0] / s[3], s[1] / s[3], s[2] / s[3])));
      }

      return veins;
   }

   private static BlockLayer.Cluster find(Map<BlockLayer.Cluster, BlockLayer.Cluster> parent, BlockLayer.Cluster cluster) {
      BlockLayer.Cluster root = cluster;

      while (parent.get(root) != root) {
         root = parent.get(root);
      }

      while (parent.get(cluster) != root) {
         BlockLayer.Cluster next = parent.get(cluster);
         parent.put(cluster, root);
         cluster = next;
      }

      return root;
   }

   public void render(Render3DEvent event, EspStyle style) {
      Minecraft mc = Minecraft.getInstance();
      BlockLayer.World world = this.world(false);
      if (mc.player != null && world != null) {
         this.rebuildDirty(world);
         Frustum frustum = PerformanceModule.frustumCulling() ? event.frustum : null;
         long now = System.nanoTime();
         Vec3 eye = mc.player.getEyePosition(event.partialTick);
         boolean outline = style.outline();
         boolean glow = style.glow.get();
         boolean fade = style.fade.get();
         boolean reveal = style.reveal.get();
         float baseWidth = style.lineWidth.get();
         float fillAlpha = style.fillAlpha.get();
         float boxSize = style.boxSize.get();
         double range = style.range.get();
         ObjectIterator var18 = world.shapes.values().iterator();

         while (var18.hasNext()) {
            BlockLayer.Shapes shapes = (BlockLayer.Shapes)var18.next();
            if (frustum == null || frustum.isVisible(shapes.bounds())) {
               for (BlockLayer.Cluster cluster : shapes.clusters()) {
                  double dist = eye.distanceTo(cluster.center());
                  if (!(dist > range)) {
                     float alpha = fade ? distanceFade(dist) : 1.0F;
                     float t = reveal ? Math.min(1.0F, (float)(now - cluster.bornAt()) / 3.8E8F) : 1.0F;
                     alpha *= easeOutCubic(t);
                     if (!(alpha < 0.02F)) {
                        float scale = t < 1.0F ? 0.55F + 0.45F * easeOutBack(t) : 1.0F;
                        float width = fade ? baseWidth * (1.0F - 0.3F * (float)Math.min(1.0, dist / 256.0)) : baseWidth;
                        int color = this.palette.color(cluster.kind());
                        int stroke = scaleAlpha(color, alpha);
                        int fill = fillAlpha > 0.0F ? withAlpha(color, fillAlpha * alpha) : 0;
                        if (outline) {
                           drawOutline(cluster, stroke, fill, width, glow, scale);
                        } else {
                           drawBoxes(cluster, stroke, fill, width, boxSize * scale);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void drawOutline(BlockLayer.Cluster cluster, int stroke, int fill, float width, boolean glow, float scale) {
      Vec3 c = cluster.center();
      BlockClusterGeometry.Mesh mesh = cluster.mesh();
      if (fill >>> 24 > 0) {
         GizmoStyle style = GizmoStyle.fill(fill);

         for (BlockClusterGeometry.Face face : mesh.faces()) {
            Gizmos.rect(grow(face.a(), c, scale), grow(face.b(), c, scale), grow(face.c(), c, scale), grow(face.d(), c, scale), style).setAlwaysOnTop();
         }
      }

      int halo = glow ? scaleAlpha(stroke, 0.18F) : 0;

      for (BlockClusterGeometry.Segment edge : mesh.edges()) {
         Vec3 a = grow(edge.a(), c, scale);
         Vec3 b = grow(edge.b(), c, scale);
         if (glow) {
            Gizmos.line(a, b, halo, width * 3.0F).setAlwaysOnTop();
         }

         Gizmos.line(a, b, stroke, width).setAlwaysOnTop();
      }
   }

   private static void drawBoxes(BlockLayer.Cluster cluster, int stroke, int fill, float width, float size) {
      GizmoStyle style = fill >>> 24 > 0 ? GizmoStyle.strokeAndFill(stroke, width, fill) : GizmoStyle.stroke(stroke, width);

      for (long pos : cluster.positions()) {
         Gizmos.cuboid(BlockBoxHelper.scaledBlockBox(BlockPos.of(pos), size), style).setAlwaysOnTop();
      }
   }

   private static Vec3 grow(Vec3 p, Vec3 c, float scale) {
      return scale == 1.0F ? p : c.add(p.subtract(c).scale(scale));
   }

   private static float distanceFade(double dist) {
      if (dist <= 64.0) {
         return 1.0F;
      } else {
         float t = (float)Math.min(1.0, (dist - 64.0) / 192.0);
         return 1.0F - 0.55F * t * t;
      }
   }

   public void renderTracers(EspStyle style) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         Vec3 eye = mc.player.getEyePosition(1.0F);
         double rangeSq = (double)style.range.get() * style.range.get();

         for (BlockLayer.Vein vein : this.veins()) {
            if (!(eye.distanceToSqr(vein.center()) > rangeSq)) {
               int color = this.palette.tracerColor(vein.kind());
               if (color >>> 24 != 0) {
                  TracerEngine.draw(vein.center(), color);
               }
            }
         }
      }
   }

   private static int withAlpha(int argb, float alpha) {
      return Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha))) << 24 | argb & 16777215;
   }

   private static int scaleAlpha(int argb, float mul) {
      int alpha = Math.round((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, mul)));
      return alpha << 24 | argb & 16777215;
   }

   private static float easeOutCubic(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float u = t - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   @Environment(EnvType.CLIENT)
   private record Cluster(Object kind, long[] positions, Vec3 center, long bornAt, BlockClusterGeometry.Mesh mesh, long[] links) {
   }

   @Environment(EnvType.CLIENT)
   private record Found(Object kind, long bornAt) {
   }

   @Environment(EnvType.CLIENT)
   public interface Palette {
      int color(Object var1);

      int tracerColor(Object var1);
   }

   @Environment(EnvType.CLIENT)
   private record Shapes(List<BlockLayer.Cluster> clusters, AABB bounds) {
   }

   @Environment(EnvType.CLIENT)
   public record Vein(Object kind, Vec3 center) {
   }

   @Environment(EnvType.CLIENT)
   private static final class World {
      final Long2ObjectOpenHashMap<BlockLayer.Found> positions = new Long2ObjectOpenHashMap();
      final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap();
      final Long2ObjectOpenHashMap<BlockLayer.Shapes> shapes = new Long2ObjectOpenHashMap();
      final LongOpenHashSet dirty = new LongOpenHashSet();
      List<BlockLayer.Vein> veins = List.of();
      boolean veinsDirty;
   }
}
