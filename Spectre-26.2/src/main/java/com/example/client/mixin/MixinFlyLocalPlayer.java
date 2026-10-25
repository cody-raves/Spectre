package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinFlyLocalPlayer {
   @ModifyReturnValue(method = "isShiftKeyDown", at = @At("RETURN"))
   private boolean spectre$flyNoSneak(boolean original) {
      if (!original) {
         return false;
      } else {
         FlyModule fly = FlyModule.active;
         return fly == null || !fly.noSneak();
      }
   }

   @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F"))
   private float spectre$flyFreecamVertical(float original) {
      FlyModule fly = FlyModule.active;
      return fly != null && fly.suppressFlightVerticalInput() ? 0.0F : original;
   }
}
