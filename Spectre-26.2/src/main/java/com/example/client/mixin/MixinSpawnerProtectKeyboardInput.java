package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.misc.SpawnerProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinSpawnerProtectKeyboardInput {
   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyUp:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectForward(boolean down) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      return module != null && this.spectre$isLocalPlayerInput() ? module.forwardKey(down) : down;
   }

   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyDown:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectBack(boolean down) {
      return this.spectre$unusedMoveKey(down);
   }

   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyLeft:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectLeft(boolean down) {
      return this.spectre$unusedMoveKey(down);
   }

   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyRight:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectRight(boolean down) {
      return this.spectre$unusedMoveKey(down);
   }

   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyJump:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectJump(boolean down) {
      return this.spectre$unusedMoveKey(down);
   }

   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0),
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyShift:Lnet/minecraft/client/KeyMapping;"))
   )
   private boolean spectre$spawnerProtectSneak(boolean down) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      return module != null && this.spectre$isLocalPlayerInput() ? module.sneakKey(down) : down;
   }

   @Unique
   private boolean spectre$unusedMoveKey(boolean down) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      return module != null && this.spectre$isLocalPlayerInput() ? module.unusedMoveKey(down) : down;
   }

   @Unique
   private boolean spectre$isLocalPlayerInput() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && player.input == (Object)this;
   }
}
