package net.spectre.client.module.impl.movement.speed;

import net.spectre.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class StrafeSpeedMode extends SpeedMode {
   private long timer = 0L;

   public StrafeSpeedMode(SpeedModule settings) {
      super(settings);
   }

   @Override
   public Vec3 onMove(LocalPlayer player, Vec3 movement) {
      boolean moving = SpeedModule.isMoving(player);
      double y = movement.y;
      switch (this.stage) {
         case 0:
            if (moving) {
               this.stage++;
               this.speed = 1.18F * this.getDefaultSpeed(player) - 0.01;
            }
         case 1:
            if (moving && player.onGround()) {
               y = this.getHop(player, 0.40123128);
               this.speed = this.speed * this.settings.strafeSpeed();
               this.stage++;
            }
            break;
         case 2:
            this.speed = this.distance - 0.76 * (this.distance - this.getDefaultSpeed(player));
            this.stage++;
            break;
         case 3:
            if (!player.level().noCollision(player.getBoundingBox().move(0.0, player.getDeltaMovement().y, 0.0)) || player.verticalCollision && this.stage > 0) {
               this.stage = 0;
            }

            this.speed = this.distance - this.distance / 159.0;
      }

      this.speed = Math.max(this.speed, this.getDefaultSpeed(player));
      if (this.settings.speedLimit()) {
         if (System.currentTimeMillis() - this.timer > 2500L) {
            this.timer = System.currentTimeMillis();
         }

         this.speed = Math.min(this.speed, System.currentTimeMillis() - this.timer > 1250L ? 0.44 : 0.43);
      }

      double[] change = transformStrafe(player, this.speed);
      return new Vec3(change[0], y, change[1]);
   }

   private static double[] transformStrafe(LocalPlayer player, double speed) {
      Vec2 input = player.input.getMoveVector();
      float forward = Math.signum(input.y);
      float side = Math.signum(input.x);
      float yaw = player.getYRot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
      if (forward == 0.0F && side == 0.0F) {
         return new double[2];
      } else {
         float strafe = 90.0F * side;
         if (forward != 0.0F) {
            strafe *= forward * 0.5F;
         }

         yaw -= strafe;
         if (forward < 0.0F) {
            yaw -= 180.0F;
         }

         double yawRadians = Math.toRadians(yaw);
         return new double[]{-Math.sin(yawRadians) * speed, Math.cos(yawRadians) * speed};
      }
   }

   @Override
   public void onTick(LocalPlayer player) {
      double dx = player.getX() - player.xo;
      double dz = player.getZ() - player.zo;
      this.distance = Math.sqrt(dx * dx + dz * dz);
   }
}
