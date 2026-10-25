package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(PowderSnowBlock.class)
public abstract class MixinJesusPowderSnowBlock {
   @ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
   private static boolean spectre$jesusWalkOnPowderSnow(boolean original, Entity entity) {
      if (original) {
         return true;
      } else {
         JesusModule jesus = JesusModule.active;
         return jesus != null && entity == Minecraft.getInstance().player && jesus.canWalkOnPowderSnow();
      }
   }
}
