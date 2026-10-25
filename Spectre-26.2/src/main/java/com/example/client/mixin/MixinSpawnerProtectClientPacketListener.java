package com.example.client.mixin;

import net.spectre.client.module.impl.misc.SpawnerProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinSpawnerProtectClientPacketListener {
   @Inject(
      method = "handleBlockUpdate",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void spectre$spawnerProtectBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      if (module != null) {
         module.onBlockUpdatePacket(packet);
      }
   }

   @Inject(
      method = "handleChunkBlocksUpdate",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void spectre$spawnerProtectSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
      SpawnerProtectModule module = SpawnerProtectModule.active;
      if (module != null) {
         module.onSectionBlocksUpdatePacket(packet);
      }
   }
}
