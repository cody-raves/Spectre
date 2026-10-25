package net.spectre.client.module.impl.movement.speed;

import net.spectre.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class VanillaSpeedMode extends SpeedMode {
   private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);

   public VanillaSpeedMode(SpeedModule settings) {
      super(settings);
   }

   @Override
   public Vec3 onMove(LocalPlayer player, Vec3 movement) {
      Vec3 vel = horizontalVelocity(player, this.settings.vanillaSpeed());
      double velX = vel.x;
      double velZ = vel.z;
      MobEffectInstance speedEffect = player.getEffect(MobEffects.SPEED);
      if (speedEffect != null) {
         double value = (speedEffect.getAmplifier() + 1) * 0.205;
         velX += velX * value;
         velZ += velZ * value;
      }

      return new Vec3(velX, movement.y, velZ);
   }

   private static Vec3 horizontalVelocity(LocalPlayer player, double bps) {
      float yaw = player.getYRot();
      Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
      Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
      Input keys = player.input.keyPresses;
      double velX = 0.0;
      double velZ = 0.0;
      boolean a = false;
      if (keys.forward()) {
         velX += forward.x / 20.0 * bps;
         velZ += forward.z / 20.0 * bps;
         a = true;
      }

      if (keys.backward()) {
         velX -= forward.x / 20.0 * bps;
         velZ -= forward.z / 20.0 * bps;
         a = true;
      }

      boolean b = false;
      if (keys.right()) {
         velX += right.x / 20.0 * bps;
         velZ += right.z / 20.0 * bps;
         b = true;
      }

      if (keys.left()) {
         velX -= right.x / 20.0 * bps;
         velZ -= right.z / 20.0 * bps;
         b = true;
      }

      if (a && b) {
         velX *= DIAGONAL;
         velZ *= DIAGONAL;
      }

      return new Vec3(velX, 0.0, velZ);
   }
}
