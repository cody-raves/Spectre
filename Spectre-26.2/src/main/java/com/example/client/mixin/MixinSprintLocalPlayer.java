package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinSprintLocalPlayer {
   @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean spectre$rageCanStartSprinting(boolean original) {
      SprintModule sprint = SprintModule.active;
      if (sprint != null && sprint.rageSprint()) {
         LocalPlayer self = (LocalPlayer)(Object)this;
         float forwards = Math.abs(self.zza);
         float sideways = Math.abs(self.xxa);
         return self.isUnderWater() ? forwards > 1.0E-5F || sideways > 1.0E-5F : forwards > 0.8 || sideways > 0.8;
      } else {
         return original;
      }
   }

   @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean spectre$rageHadImpulse(boolean original) {
      SprintModule sprint = SprintModule.active;
      if (sprint != null && sprint.rageSprint()) {
         LocalPlayer self = (LocalPlayer)(Object)this;
         return Math.abs(self.xxa) > 1.0E-5F || Math.abs(self.zza) > 1.0E-5F;
      } else {
         return original;
      }
   }

   @ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
   private boolean spectre$rageKeepSprinting(boolean original) {
      if (!original) {
         return false;
      } else {
         SprintModule sprint = SprintModule.active;
         return sprint != null && sprint.rageSprint() ? sprint.unsprintInWater() && ((LocalPlayer)(Object)this).isInWater() : true;
      }
   }
}
