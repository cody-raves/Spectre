package com.example.client.mixin;

import net.spectre.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinMovementCorrectionKeyboardInput {
   @Inject(method = "tick", at = @At("TAIL"))
   private void spectre$correctForServerRotation(CallbackInfo ci) {
      ClientInput input = (ClientInput)(Object)this;
      Input corrected = ServerRotation.correctInput(input.keyPresses);
      if (corrected != input.keyPresses) {
         input.keyPresses = corrected;
         ((AccessorMovementClientInput)input)
            .spectre$setMoveVector(new Vec2(impulse(corrected.left(), corrected.right()), impulse(corrected.forward(), corrected.backward())).normalized());
      }
   }

   private static float impulse(boolean positive, boolean negative) {
      return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
   }
}
