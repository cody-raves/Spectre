package com.example.client.mixin;

import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.render.BlockOverlayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinBlockOverlayLevelRenderer {
   @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
   private void spectre$hideVanillaOutline(CallbackInfo ci) {
      BlockOverlayModule module = ModuleRegistry.get(BlockOverlayModule.class);
      if (module != null && module.isEnabled()) {
         ci.cancel();
      }
   }
}
