package net.spectre.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class PearlTrajectorySimulator {
   private static final double SPAWN_EYE_OFFSET = 0.1;
   private static final double GRAVITY = 0.03;
   private static final float AIR_DRAG = 0.99F;
   private static final float WATER_DRAG = 0.8F;
   private static final int MAX_TICKS = 300;
   private static final double ENTITY_MARGIN = 0.3;

   private PearlTrajectorySimulator() {
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(Level level, LocalPlayer player, float partialTick) {
      Vec3 eye = player.getEyePosition(partialTick);
      Vec3 pos = new Vec3(eye.x, eye.y - 0.1, eye.z);
      Vec3 velocity = player.getViewVector(partialTick).scale(EnderpearlItem.PROJECTILE_SHOOT_POWER);
      Vec3 motion = player.getKnownMovement();
      velocity = velocity.add(motion.x, player.onGround() ? 0.0 : motion.y, motion.z);
      return simulate(level, pos, velocity, player, null);
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(Level level, Projectile pearl) {
      return simulate(level, pearl.position(), pearl.getDeltaMovement(), pearl, pearl.getOwner());
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(Level level, Vec3 start, Vec3 velocity, Entity ignore, Entity alsoIgnore) {
      List<Vec3> points = new ArrayList<>(64);
      points.add(start);
      Vec3 pos = start;
      BlockHitResult blockHit = null;
      Entity entityHit = null;
      int ticks = 0;

      while (ticks < 300) {
         ticks++;
         Vec3 var15 = velocity.add(0.0, -0.03, 0.0);
         velocity = var15.scale(isInWater(level, pos) ? 0.8F : 0.99F);
         Vec3 next = pos.add(velocity);
         HitResult blockResult = level.clip(new ClipContext(pos, next, Block.COLLIDER, Fluid.NONE, ignore));
         Vec3 end = blockResult.getType() != Type.MISS ? blockResult.getLocation() : next;
         Entity entity = firstEntityOnSegment(level, pos, end, ignore, alsoIgnore);
         if (entity != null) {
            Vec3 at = entity.getBoundingBox().inflate(0.3).clip(pos, end).orElse(end);
            points.add(at);
            entityHit = entity;
            break;
         }

         if (blockResult instanceof BlockHitResult hit && hit.getType() != Type.MISS) {
            points.add(end);
            blockHit = hit;
            break;
         }

         pos = next;
         points.add(next);
         if (next.y < level.getMinY() - 64) {
            break;
         }
      }

      return new PearlTrajectorySimulator.SimulationResult(List.copyOf(points), points.getLast(), blockHit, entityHit, ticks);
   }

   private static Entity firstEntityOnSegment(Level level, Vec3 from, Vec3 to, Entity ignore, Entity alsoIgnore) {
      AABB sweep = new AABB(from, to).inflate(1.0);
      Entity closest = null;
      double closestSq = Double.MAX_VALUE;

      for (Entity entity : level.getEntities(ignore, sweep, PearlTrajectorySimulator::canHit)) {
         if (entity != alsoIgnore) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.3).clip(from, to);
            if (!hit.isEmpty()) {
               double distanceSq = from.distanceToSqr(hit.get());
               if (distanceSq < closestSq) {
                  closestSq = distanceSq;
                  closest = entity;
               }
            }
         }
      }

      return closest;
   }

   private static boolean canHit(Entity entity) {
      return EntitySelector.NO_SPECTATORS.test(entity) && entity.isPickable() && !(entity instanceof LocalPlayer);
   }

   private static boolean isInWater(Level level, Vec3 pos) {
      return level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
   }

   public static boolean isHoldingPearl(LocalPlayer player) {
      return player.getMainHandItem().is(Items.ENDER_PEARL) || player.getOffhandItem().is(Items.ENDER_PEARL);
   }

   @Environment(EnvType.CLIENT)
   public record SimulationResult(List<Vec3> points, Vec3 landing, BlockHitResult blockHit, Entity entityHit, int ticks) {
      public boolean landed() {
         return this.blockHit != null || this.entityHit != null;
      }
   }
}
