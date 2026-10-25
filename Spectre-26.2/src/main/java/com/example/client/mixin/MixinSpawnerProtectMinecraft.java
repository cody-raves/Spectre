package com.example.client.mixin;

import net.spectre.client.module.impl.misc.SpawnerProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinSpawnerProtectMinecraft {
   @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
   private void spectre$spawnerProtectContinueAttack(boolean down, CallbackInfo ci) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      if (module != null && module.onVanillaContinueAttack()) {
         ci.cancel();
      }
   }

   @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
   private void spectre$spawnerProtectStartAttack(CallbackInfoReturnable<Boolean> cir) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      if (module != null && module.blocksVanillaAttack()) {
         cir.setReturnValue(false);
      }
   }

}
