package com.example.client.mixin;

import net.spectre.client.event.impl.BlockUpdateEvent;
import net.spectre.client.event.impl.WorldEntityAddEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
   @Inject(method = "addEntity", at = @At("HEAD"))
   private void spectre$onEntityAdd(Entity entity, CallbackInfo ci) {
      WorldEntityAddEvent.dispatch(entity);
   }

   @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
   private void spectre$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
      BlockUpdateEvent.dispatch(pos, oldState, newState);
   }
}
