package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinSneakLocalPlayer {
   @ModifyExpressionValue(
      method = "tick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/ClientInput;keyPresses:Lnet/minecraft/world/entity/player/Input;", opcode = 180),
      require = 3,
      allow = 3
   )
   private Input spectre$packetSneak(Input original) {
      SneakModule sneak = SneakModule.active;
      return sneak != null && !original.shift() && sneak.doPacket()
         ? new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, original.sprint())
         : original;
   }
}
