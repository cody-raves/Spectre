package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.spectre.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionCompiler.Results;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompiler {
   @WrapOperation(
      method = "compile",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
   )
   private BlockState spectre$xrayBlockState(RenderSectionRegion region, BlockPos pos, Operation<BlockState> original) {
      BlockState state = (BlockState)original.call(new Object[]{region, pos});
      XrayModule.Snapshot snapshot = XrayModule.active;
      if (snapshot != null && !state.isAir()) {
         float[] alphaSlot = XrayModule.CURRENT_ALPHA.get();
         float alpha = snapshot.alphaFor(region, state, pos);
         alphaSlot[0] = alpha;
         return alpha <= 0.0F ? Blocks.AIR.defaultBlockState() : state;
      } else {
         return state;
      }
   }

   @org.spongepowered.asm.mixin.injection.ModifyVariable(method="getOrBeginLayer",at=@At("HEAD"),argsOnly=true)
   private ChunkSectionLayer spectre$xrayLayer(ChunkSectionLayer layer) {
      float alpha=XrayModule.CURRENT_ALPHA.get()[0];
      return XrayModule.active!=null&&alpha>0&&alpha<1?ChunkSectionLayer.TRANSLUCENT:layer;
   }

   @Inject(method = "compile", at = @At("RETURN"))
   private void spectre$xrayResetAlpha(
      SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack bufferPack, CallbackInfoReturnable<Results> cir
   ) {
      XrayModule.CURRENT_ALPHA.get()[0] = 1.0F;
   }
}
