package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinJesusLivingEntity {
   @ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
   private boolean spectre$jesusCanStandOnFluid(boolean original, FluidState fluidState) {
      if (original) {
         return true;
      } else {
         JesusModule jesus = JesusModule.active;
         return jesus != null && (Object)this == Minecraft.getInstance().player ? jesus.canWalkOnFluid(fluidState) : false;
      }
   }
}
