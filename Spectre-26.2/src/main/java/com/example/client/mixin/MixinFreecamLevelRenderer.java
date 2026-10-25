package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinFreecamLevelRenderer {
   @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
   private void spectre$freecamEntitiesReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (FreecamModule.active != null) {
         cir.setReturnValue(true);
      }
   }

}
