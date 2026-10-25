package com.example.client.mixin;

import net.spectre.client.event.impl.ChatSendEvent;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
   @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
   private void spectre$onSendChat(String message, CallbackInfo ci) {
      if (ChatSendEvent.dispatch(message)) {
         ci.cancel();
      }
   }

   @Inject(method = "handleLogin", at = @At("TAIL"))
   private void spectre$afterLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
   private void spectre$afterChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
   private void spectre$afterChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
   private void spectre$afterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
   private void spectre$afterSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
   private void spectre$afterLightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }
}
