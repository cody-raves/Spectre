package com.example.client.mixin;

import net.spectre.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {
   @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
   private void spectre$xraySkipHiddenBlockEntity(
      BlockEntity blockEntity, float partialTick, CrumblingOverlay overlay, boolean debug, CallbackInfoReturnable<BlockEntityRenderState> cir
   ) {
      XrayModule.Snapshot snapshot = XrayModule.active;
      if (snapshot != null) {
         Level level = blockEntity.getLevel();
         if (level != null) {
            BlockPos pos = blockEntity.getBlockPos();
            BlockState state = blockEntity.getBlockState();
            if (snapshot.alphaFor(level, state, pos) <= 0.0F) {
               cir.setReturnValue(null);
            }
         }
      }
   }
}
