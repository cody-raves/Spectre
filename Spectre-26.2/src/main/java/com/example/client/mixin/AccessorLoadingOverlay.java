package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public interface AccessorLoadingOverlay {
   @Accessor("currentProgress")
   float spectre$getProgress();

   @Accessor("fadeOutStart")
   long spectre$getFadeOutStart();

   @Accessor("fadeInStart")
   long spectre$getFadeInStart();

   @Accessor("fadeIn")
   boolean spectre$isFadeIn();
}
