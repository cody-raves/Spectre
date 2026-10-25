package com.example.client.mixin;

import net.spectre.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinSneakKeyboardInput {
   @Inject(method = "tick", at = @At("TAIL"))
   private void spectre$forceSneak(CallbackInfo ci) {
      SneakModule sneak = SneakModule.active;
      if (sneak != null && sneak.doVanilla()) {
         ClientInput input = (ClientInput)(Object)this;
         Input keys = input.keyPresses;
         if (!keys.shift()) {
            input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), true, keys.sprint());
         }
      }
   }
}
