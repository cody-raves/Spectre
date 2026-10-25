package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinElytraFlyLivingEntity {
   @ModifyReturnValue(method = "isFallFlying", at = @At("RETURN"))
   private boolean spectre$elytraFlyIsFallFlying(boolean original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         return (Object)this == player && mc.isSameThread() ? module.modifyFallFlying(player, original) : original;
      }
   }
}
