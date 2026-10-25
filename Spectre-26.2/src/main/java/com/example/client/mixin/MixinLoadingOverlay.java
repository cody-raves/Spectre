package com.example.client.mixin;

import net.spectre.client.gui.LoadingScreens;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public abstract class MixinLoadingOverlay {
   @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
   private int spectre$backgroundFill(int argb) {
      return LoadingScreens.backgroundColor(argb);
   }

   @ModifyArg(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;setVector4fFromARGB32(Lorg/joml/Vector4f;I)Lorg/joml/Vector4f;"),
      index = 1
   )
   private int spectre$backgroundClear(int argb) {
      return LoadingScreens.backgroundColor(argb);
   }
}
