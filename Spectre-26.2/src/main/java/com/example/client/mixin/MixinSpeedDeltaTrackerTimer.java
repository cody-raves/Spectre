package com.example.client.mixin;

import net.spectre.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Timer.class)
public abstract class MixinSpeedDeltaTrackerTimer {
   @Shadow
   private float deltaTicks;

   @Inject(method = "advanceGameTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/DeltaTracker$Timer;lastMs:J", opcode = 181))
   private void spectre$speedTimer(long time, CallbackInfoReturnable<Integer> cir) {
      float multiplier = SpeedModule.timerMultiplier;
      if (multiplier != 1.0F) {
         this.deltaTicks *= multiplier;
      }
   }
}
