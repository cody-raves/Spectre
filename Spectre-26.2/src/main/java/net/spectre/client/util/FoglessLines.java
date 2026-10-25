package net.spectre.client.util;

import com.example.client.mixin.AccessorGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class FoglessLines {
   private static final double NEAR = 0.05;
   private final Vec3 eye;
   private final Vector3fc forward;
   private final double pixelPerDepth;

   private FoglessLines(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      this.eye = eye;
      this.forward = forward;
      this.pixelPerDepth = pixelPerDepth;
   }

   public static FoglessLines forFrame(float partialTick) {
      Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
      return new FoglessLines(camera.position(), camera.forwardVector(), pixelScale(partialTick));
   }

   public static double pixelScale(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      Camera camera = mc.gameRenderer.mainCamera();
      float fov = camera.getFov();
      return 2.0 * Math.tan(Math.toRadians(fov) * 0.5) / Math.max(1, mc.getWindow().getHeight());
   }

   public static FoglessLines forView(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      return new FoglessLines(eye, forward, pixelPerDepth);
   }

   public void line(Vec3 from, Vec3 to, int argb, float width) {
      double depthFrom = this.depth(from);
      double depthTo = this.depth(to);
      if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
         if (depthFrom < 0.05) {
            from = from.lerp(to, (0.05 - depthFrom) / (depthTo - depthFrom));
            depthFrom = 0.05;
         } else if (depthTo < 0.05) {
            to = to.lerp(from, (0.05 - depthTo) / (depthFrom - depthTo));
            depthTo = 0.05;
         }

         Vec3 side = from.subtract(this.eye).cross(to.subtract(from));
         double length = side.length();
         if (!(length < 1.0E-9)) {
            double scale = width * 0.5 * this.pixelPerDepth / length;
            Vec3 atFrom = side.scale(scale * depthFrom);
            Vec3 atTo = side.scale(scale * depthTo);
            Gizmos.rect(from.add(atFrom), to.add(atTo), to.subtract(atTo), from.subtract(atFrom), GizmoStyle.fill(argb)).setAlwaysOnTop();
         }
      }
   }

   public void box(AABB box, int argb, float width) {
      double x0 = box.minX;
      double y0 = box.minY;
      double z0 = box.minZ;
      double x1 = box.maxX;
      double y1 = box.maxY;
      double z1 = box.maxZ;

      for (double y : new double[]{y0, y1}) {
         this.line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb, width);
         this.line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb, width);
         this.line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb, width);
         this.line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb, width);
      }

      this.line(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), argb, width);
      this.line(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), argb, width);
      this.line(new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), argb, width);
      this.line(new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), argb, width);
   }

   private double depth(Vec3 point) {
      return (point.x - this.eye.x) * this.forward.x() + (point.y - this.eye.y) * this.forward.y() + (point.z - this.eye.z) * this.forward.z();
   }
}
