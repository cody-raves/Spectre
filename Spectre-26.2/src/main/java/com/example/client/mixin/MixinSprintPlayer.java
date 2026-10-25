package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.spectre.client.module.impl.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class MixinSprintPlayer {
   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
   private boolean spectre$keepSprintVelocity(Player instance, Vec3 velocity) {
      return spectre$stopSprinting(instance);
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
   private boolean spectre$keepSprintFlag(Player instance, boolean sprinting) {
      return spectre$stopSprinting(instance);
   }

   @Unique
   private static boolean spectre$stopSprinting(Player instance) {
      SprintModule sprint = SprintModule.active;
      if (sprint == null) {
         return true;
      } else {
         LocalPlayer self = Minecraft.getInstance().player;
         if (self == null) {
            return true;
         } else {
            return instance == self || instance instanceof ServerPlayer && instance.getUUID().equals(self.getUUID()) ? sprint.stopSprinting() : true;
         }
      }
   }
}
