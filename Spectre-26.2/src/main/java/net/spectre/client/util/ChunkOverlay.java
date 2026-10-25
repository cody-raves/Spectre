package net.spectre.client.util;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class ChunkOverlay {
   public static final double MARKER_Y = 63.0;
   private static final float EDGE_SHADE = 0.77F;
   private static final float LINE_WIDTH = 1.5F;

   private ChunkOverlay() {
   }

   public static void draw(LongSet chunks, int fill, float partialTick) {
      if (!chunks.isEmpty()) {
         GizmoStyle plane = GizmoStyle.fill(fill);
         int edge = ARGB.color(Math.min(255, ARGB.alpha(fill) * 3 / 2), ARGB.scaleRGB(fill, 0.77F));
         FoglessLines lines = FoglessLines.forFrame(partialTick);
         LongIterator var6 = chunks.iterator();

         while (var6.hasNext()) {
            long key = (Long)var6.next();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            double x0 = cx << 4;
            double z0 = cz << 4;
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            Gizmos.cuboid(new AABB(x0, 63.0, z0, x1, 63.0, z1), plane).setAlwaysOnTop();
            if (!chunks.contains(ChunkPos.pack(cx, cz - 1))) {
               edge(lines, x0, z0, x1, z0, edge);
            }

            if (!chunks.contains(ChunkPos.pack(cx, cz + 1))) {
               edge(lines, x0, z1, x1, z1, edge);
            }

            if (!chunks.contains(ChunkPos.pack(cx - 1, cz))) {
               edge(lines, x0, z0, x0, z1, edge);
            }

            if (!chunks.contains(ChunkPos.pack(cx + 1, cz))) {
               edge(lines, x1, z0, x1, z1, edge);
            }
         }
      }
   }

   private static void edge(FoglessLines lines, double ax, double az, double bx, double bz, int color) {
      lines.line(new Vec3(ax, 63.0, az), new Vec3(bx, 63.0, bz), color, 1.5F);
   }
}
