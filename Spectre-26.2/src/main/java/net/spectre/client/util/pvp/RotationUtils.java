package net.spectre.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class RotationUtils {
   private RotationUtils() {
   }

   public static Rotation fromTo(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
      float pitch = (float)(-(Mth.atan2(dy, horizontal) * 180.0F / (float)Math.PI));
      return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F));
   }

   public static Rotation entityRotation(Entity entity) {
      return new Rotation(entity.getYRot(), entity.getXRot());
   }

   public static float yawDelta(float fromYaw, float toYaw) {
      return Mth.wrapDegrees(toYaw - fromYaw);
   }

   public static float pitchDelta(float fromPitch, float toPitch) {
      return Mth.clamp(toPitch - fromPitch, -180.0F, 180.0F);
   }

   public static double angleTo(Rotation from, Rotation to) {
      float yaw = yawDelta(from.yaw(), to.yaw());
      float pitch = pitchDelta(from.pitch(), to.pitch());
      return Math.hypot(yaw, pitch);
   }

   public static float approachAngle(float current, float target, float maxStep) {
      float delta = yawDelta(current, target);
      return Mth.wrapDegrees(current + Mth.clamp(delta, -maxStep, maxStep));
   }

   public static float approachPitch(float current, float target, float maxStep) {
      float delta = target - current;
      return Mth.clamp(current + Mth.clamp(delta, -maxStep, maxStep), -90.0F, 90.0F);
   }
}
