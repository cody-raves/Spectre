package net.spectre.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class TargetingUtils {
   private TargetingUtils() {
   }

   public static LivingEntity findBestLivingTarget(Minecraft mc, double range, double fov, boolean playersOnly, double boxShrink, double prediction) {
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         Vec3 eye = player.getEyePosition(partialTick);
         Rotation current = RotationUtils.entityRotation(player);
         double rangeSq = range * range;
         double maxAngle = fov * 0.5;
         LivingEntity best = null;
         double bestScore = Double.MAX_VALUE;

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && isValidTarget(player, living, playersOnly)) {
               AABB box = predictedBox(living, partialTick, boxShrink, prediction);
               Vec3 targetPos = closestPoint(eye, box);
               double distanceSq = targetPos.distanceToSqr(eye);
               if (!(distanceSq > rangeSq)) {
                  Rotation targetRotation = RotationUtils.fromTo(eye, targetPos);
                  double angle = RotationUtils.angleTo(current, targetRotation);
                  if (!(angle > maxAngle)) {
                     double score = angle * 4.0 + distanceSq * 0.01;
                     if (score < bestScore) {
                        bestScore = score;
                        best = living;
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   public static boolean isValidTarget(LocalPlayer player, LivingEntity target, boolean playersOnly) {
      if (target == player || target.isRemoved() || !target.isAlive() || target.isDeadOrDying()) {
         return false;
      } else {
         return playersOnly && !(target instanceof Player) ? false : !(target instanceof Player targetPlayer && targetPlayer.isSpectator());
      }
   }

   public static AABB predictedBox(Entity entity, float partialTick, double shrink, double prediction) {
      Vec3 current = entity.position();
      Vec3 interpolated = entity.getPosition(partialTick);
      Vec3 velocity = current.subtract(entity.oldPosition());
      AABB box = entity.getBoundingBox().move(interpolated.x - current.x, interpolated.y - current.y, interpolated.z - current.z);
      if (shrink > 0.0) {
         box = box.deflate(Math.min(shrink, box.getXsize() * 0.45), Math.min(shrink, box.getYsize() * 0.45), Math.min(shrink, box.getZsize() * 0.45));
      }

      return box.move(velocity.scale(prediction));
   }

   public static Vec3 aimPoint(Vec3 eye, AABB box, String mode) {
      if ("Center".equals(mode)) {
         return box.getCenter();
      } else {
         return box.contains(eye)
            ? new Vec3((box.minX + box.maxX) * 0.5, TargetingUtils.MthLike.clamp(eye.y, box.minY, box.maxY), (box.minZ + box.maxZ) * 0.5)
            : closestPoint(eye, box);
      }
   }

   public static Vec3 closestPoint(Vec3 point, AABB box) {
      return new Vec3(
         TargetingUtils.MthLike.clamp(point.x, box.minX, box.maxX),
         TargetingUtils.MthLike.clamp(point.y, box.minY, box.maxY),
         TargetingUtils.MthLike.clamp(point.z, box.minZ, box.maxZ)
      );
   }

   public static boolean rayIntersects(Vec3 origin, Vec3 direction, AABB box, double maxDistance) {
      Vec3 end = origin.add(direction.normalize().scale(maxDistance));
      return box.clip(origin, end).isPresent();
   }

   public static LivingEntity raycastLivingTarget(Minecraft mc, double range, boolean throughWalls, boolean playersOnly) {
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         Vec3 eye = player.getEyePosition(partialTick);
         Vec3 view = player.getViewVector(partialTick);
         double traceRange = range;
         if (!throughWalls) {
            HitResult blockHit = player.pick(range, partialTick, false);
            if (blockHit.getType() != Type.MISS) {
               traceRange = Math.min(range, blockHit.getLocation().distanceTo(eye));
            }
         }

         Vec3 end = eye.add(view.scale(traceRange));
         AABB searchBox = player.getBoundingBox().expandTowards(view.scale(traceRange)).inflate(1.0);
         EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player,
            eye,
            end,
            searchBox,
            entity -> entity instanceof LivingEntity livingx && EntitySelector.CAN_BE_PICKED.test(entity) && isValidTarget(player, livingx, playersOnly),
            traceRange * traceRange
         );
         return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class MthLike {
      private static double clamp(double value, double min, double max) {
         return Math.max(min, Math.min(max, value));
      }
   }
}
