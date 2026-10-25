package net.spectre.client.module.impl.movement.speed;

import net.spectre.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public abstract class SpeedMode {
   private static final double BASE_SPEED = 0.2873;
   protected final SpeedModule settings;
   protected int stage;
   protected double distance;
   protected double speed;

   protected SpeedMode(SpeedModule settings) {
      this.settings = settings;
      this.reset();
   }

   public void onTick(LocalPlayer player) {
   }

   public Vec3 onMove(LocalPlayer player, Vec3 movement) {
      return movement;
   }

   public void onRubberband() {
      this.reset();
   }

   public void onActivate() {
   }

   public void onDeactivate() {
   }

   protected double getDefaultSpeed(LocalPlayer player) {
      double defaultSpeed = 0.2873;
      MobEffectInstance speedEffect = player.getEffect(MobEffects.SPEED);
      if (speedEffect != null) {
         defaultSpeed *= 1.0 + 0.2 * (speedEffect.getAmplifier() + 1);
      }

      MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
      if (slowness != null) {
         defaultSpeed /= 1.0 + 0.2 * (slowness.getAmplifier() + 1);
      }

      return defaultSpeed;
   }

   protected void reset() {
      this.stage = 0;
      this.distance = 0.0;
      this.speed = 0.2873;
   }

   protected double getHop(LocalPlayer player, double height) {
      MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP_BOOST);
      if (jumpBoost != null) {
         height += (jumpBoost.getAmplifier() + 1) * 0.1F;
      }

      return height;
   }
}
