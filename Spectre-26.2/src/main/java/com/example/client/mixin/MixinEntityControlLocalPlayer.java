package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.BoatFlyModule;
import net.spectre.client.module.impl.movement.EntityControlModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinEntityControlLocalPlayer {
   @ModifyReturnValue(method = "getJumpRidingScale", at = @At("RETURN"))
   private float spectre$entityControlMaxJump(float original) {
      return EntityControlModule.maxJump() ? 1.0F : original;
   }

   @ModifyReturnValue(method = "jumpableVehicle", at = @At("RETURN"))
   private PlayerRideableJumping spectre$boatFlyCancelJump(PlayerRideableJumping original) {
      return original != null && BoatFlyModule.cancelsMountJump((LocalPlayer)(Object)this) ? null : original;
   }
}
