package com.example.client.mixin;

import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.SlowSwingModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
   @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
   private void spectre$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
      SlowSwingModule module = ModuleRegistry.get(SlowSwingModule.class);
      if (module != null && module.isEnabled()) {
         if ((Object)this == Minecraft.getInstance().player) {
            cir.setReturnValue(module.getModifiedSwingDuration((Integer)cir.getReturnValue()));
         }
      }
   }
}
