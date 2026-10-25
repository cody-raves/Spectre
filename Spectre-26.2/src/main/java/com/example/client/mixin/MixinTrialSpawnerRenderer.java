package com.example.client.mixin;

import net.spectre.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.TrialSpawnerRenderer;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(TrialSpawnerRenderer.class)
public abstract class MixinTrialSpawnerRenderer {
   @Inject(method = "extractSpawnerData", at = @At("HEAD"), cancellable = true)
   private static void spectre$cancelSpawnerMobExtract(
      SpawnerRenderState state, float partialTick, Entity displayEntity, EntityRenderDispatcher dispatcher, double x, double y, CallbackInfo ci
   ) {
      if (NoRenderModule.hideSpawnerMob()) {
         ci.cancel();
      }
   }
}
