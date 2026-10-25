package com.example.client.mixin.indigo;

import net.spectre.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = MutableQuadViewImpl.class, remap = false)
public abstract class MixinAbstractTerrainRenderContext {
   @Inject(method = "emit()Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;", at = @At("HEAD"))
   private void spectre$xrayBufferQuad(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<MutableQuadViewImpl> ci) {
      MutableQuadViewImpl quad = (MutableQuadViewImpl)(Object)this;
      if (XrayModule.active != null) {
         float alpha = XrayModule.CURRENT_ALPHA.get()[0];
         if (!(alpha <= 0.0F) && !(alpha >= 1.0F)) {
            quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);

            for (int vertex = 0; vertex < 4; vertex++) {
               quad.color(vertex, ARGB.multiplyAlpha(quad.color(vertex), alpha));
            }
         }
      }
   }
}
