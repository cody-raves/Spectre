package net.spectre.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class TracerEngine {
   private static final float WIDTH = 1.0F;
   private static boolean active;
   private static Vec3 cameraPos = Vec3.ZERO;
   private static Vec3 origin = Vec3.ZERO;
   private static FoglessLines lines;
   private static final Matrix4f viewProjection = new Matrix4f();

   private TracerEngine() {
   }

   public static void beginFrame(Matrix4fc viewRotation, Matrix4fc projection) {
      active = false;
      lines = null;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         if (mc.gui.screen() == null || mc.gui.screen() instanceof ChatScreen) {
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera != null && camera.isInitialized()) {
               viewProjection.set(projection).mul(viewRotation);
               Matrix4f inverse = viewProjection.invert(new Matrix4f());
               Vector4f centre = inverse.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
               Vector4f eye = inverse.transform(new Vector4f(0.0F, 0.0F, -1.0F, 0.0F));
               cameraPos = camera.position();
               origin = cameraPos.add(centre.x / centre.w, centre.y / centre.w, centre.z / centre.w);
               Vec3 eyePos = cameraPos.add(eye.x / eye.w, eye.y / eye.w, eye.z / eye.w);
               Vector3fc forward = new Vector3f((float)(origin.x - eyePos.x), (float)(origin.y - eyePos.y), (float)(origin.z - eyePos.z)).normalize();
               double pixel = 2.0 / (projection.m11() * Math.max(1, mc.getWindow().getHeight()));
               lines = FoglessLines.forView(eyePos, forward, pixel);
               active = true;
            }
         }
      }
   }

   public static void draw(Vec3 target, int argb) {
      if (active) {
         lines.line(origin, target, argb, 1.0F);
      }
   }

   public static void stem(Vec3 feet, double height, int argb) {
      if (active) {
         lines.line(feet, feet.add(0.0, height, 0.0), argb, 1.0F);
      }
   }

   public static Vector4f clip(Vec3 world) {
      return !active
         ? null
         : viewProjection.transform(new Vector4f((float)(world.x - cameraPos.x), (float)(world.y - cameraPos.y), (float)(world.z - cameraPos.z), 1.0F));
   }

   public static boolean isInFront(Camera camera, Vec3 target) {
      Vec3 delta = target.subtract(camera.position());
      Vector3fc fwd = camera.forwardVector();
      return delta.x * fwd.x() + delta.y * fwd.y() + delta.z * fwd.z() > 0.05;
   }
}
