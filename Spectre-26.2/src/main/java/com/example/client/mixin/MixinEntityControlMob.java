package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.EntityControlModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Mob.class)
public abstract class MixinEntityControlMob {
   @ModifyReturnValue(method = "isSaddled", at = @At("RETURN"))
   private boolean spectre$entityControlSpoofSaddle(boolean original) {
      return original || EntityControlModule.spoofSaddle();
   }
}
