package com.example.client.mixin;

import net.spectre.client.module.impl.movement.BoatFlyModule;
import net.spectre.client.module.impl.movement.BoatNoclipModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinBoatFlyClientPacketListener {
   @Inject(
      method = "handleMoveVehicle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;fromEntity(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;")
   )
   private void spectre$boatFlyVehicleSetback(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
      BoatFlyModule fly = BoatFlyModule.active;
      if (fly != null) {
         fly.onVehicleSetback();
      }

      BoatNoclipModule noclip = BoatNoclipModule.active;
      if (noclip != null) {
         noclip.onVehicleSetback();
      }
   }
}
