package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class MixinPlayer {
   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void spectre$onAttack(Entity target, CallbackInfo ci) {
      if (PlayerAttackEntityEvent.dispatch(target)) {
         ci.cancel();
      }
   }

   @ModifyReturnValue(method = "getFlyingSpeed", at = @At("RETURN"))
   private float spectre$flySpeed(float original) {
      FlyModule fly = FlyModule.active;
      if (fly == null) {
         return original;
      } else {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && (Object)this == player) {
            float override = fly.getOffGroundSpeed(player);
            return override != -1.0F ? override : original;
         } else {
            return original;
         }
      }
   }
}
