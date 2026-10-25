package com.example.client.mixin;

import net.spectre.client.module.impl.combat.FriendGuardModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinFriendGuardMultiPlayerGameMode {
   @Inject(method = "attack", at = @At("HEAD"), cancellable = true, order = 900)
   private void spectre$friendGuard(Player player, Entity target, CallbackInfo ci) {
      if (FriendGuardModule.intercepts(player, target)) {
         player.resetAttackStrengthTicker();
         ci.cancel();
      }
   }
}
