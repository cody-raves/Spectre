package com.example.client.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.spectre.client.gui.WindowIcon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinWindow {
   @Shadow
   public abstract long handle();

   @Inject(method = "setIcon", at = @At("TAIL"))
   private void spectre$setIcon(PackResources resources, IconSet iconSet, CallbackInfo ci) {
      WindowIcon.apply(this.handle());
   }
}
