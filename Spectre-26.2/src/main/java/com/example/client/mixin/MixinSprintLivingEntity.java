package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinSprintLivingEntity {
   @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
   private float spectre$rageJumpYaw(float original) {
      SprintModule sprint = SprintModule.active;
      if (sprint == null) {
         return original;
      } else {
         LocalPlayer player = Minecraft.getInstance().player;
         if ((Object)this == player && sprint.rageSprint()) {
            float forward = Math.signum(player.zza);
            float strafe = 90.0F * Math.signum(player.xxa);
            if (forward != 0.0F) {
               strafe *= forward * 0.5F;
            }

            original -= strafe;
            if (forward < 0.0F) {
               original -= 180.0F;
            }

            return original;
         } else {
            return original;
         }
      }
   }

   @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"))
   private boolean spectre$rageJumpBoost(boolean original) {
      SprintModule sprint = SprintModule.active;
      if (sprint != null && original) {
         LocalPlayer player = Minecraft.getInstance().player;
         return (Object)this == player && sprint.rageSprint() ? Math.abs(player.zza) > 1.0E-5F || Math.abs(player.xxa) > 1.0E-5F : original;
      } else {
         return original;
      }
   }
}
