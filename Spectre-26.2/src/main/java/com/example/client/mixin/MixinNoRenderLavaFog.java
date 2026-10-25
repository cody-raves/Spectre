package com.example.client.mixin;

import net.spectre.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LavaFogEnvironment.class)
public abstract class MixinNoRenderLavaFog {
   @Inject(method = "setupFog", at = @At("TAIL"))
   private void spectre$clearLavaFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (NoRenderModule.hideLavaFog()) {
         fog.environmentalStart = -8.0F;
         fog.environmentalEnd = renderDistance;
         fog.skyEnd = renderDistance;
         fog.cloudEnd = renderDistance;
      }
   }
}
