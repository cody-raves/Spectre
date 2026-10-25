package com.example.client.mixin;

import net.spectre.client.event.impl.KeyInputEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void spectre$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
      if (KeyInputEvent.dispatch(event.key(), event.scancode(), action, event.modifiers())) {
         ci.cancel();
      }
   }
}
