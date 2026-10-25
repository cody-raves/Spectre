package com.example.client.mixin;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.spectre.client.module.impl.render.XrayModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ModelBlockRenderer.class)
public abstract class MixinModelBlockRenderer {
    @Shadow @Final private QuadInstance quadInstance;
    @Inject(method="putQuadWithTint",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void spectre$alpha(CallbackInfo ci){
        if(XrayModule.active!=null)for(int i=0;i<4;i++)quadInstance.setColor(i,net.minecraft.util.ARGB.multiplyAlpha(quadInstance.getColor(i),XrayModule.CURRENT_ALPHA.get()[0]));
    }
}
