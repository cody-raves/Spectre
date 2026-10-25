package com.example.client.mixin;

import net.spectre.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Block.class)
public abstract class MixinBlock {
   @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
   private static void spectre$xrayForceFace(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      XrayModule.Snapshot snapshot = XrayModule.active;
      if (snapshot != null && snapshot.isWhitelisted(state.getBlock()) && XrayModule.CURRENT_ALPHA.get()[0] >= 1.0F) {
         cir.setReturnValue(true);
      }
   }
}
