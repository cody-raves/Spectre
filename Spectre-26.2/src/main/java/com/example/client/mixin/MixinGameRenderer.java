package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinGameRenderer {
   @Inject(method = "renderFrame", at = @At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
   private void spectre$beforeSwap(CallbackInfo ci) {
      StreamOverlay.endFrame();
      ImGuiManager.getInstance().renderIfActive();
   }
}
