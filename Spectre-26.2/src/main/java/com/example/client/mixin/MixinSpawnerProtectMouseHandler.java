package com.example.client.mixin;

import net.spectre.client.module.impl.misc.SpawnerProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MixinSpawnerProtectMouseHandler {
   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void spectre$spawnerProtectFreezeLook(double movementTime, CallbackInfo ci) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      if (module != null && module.blocksMouseTurn()) {
         ci.cancel();
      }
   }
}
