package com.example.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.spectre.client.gui.VanillaUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class MixinVanillaUiGraphics {
    @Inject(method="innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",at=@At("HEAD"),cancellable=true)
    private void spectre$customCanvas(RenderPipeline pipeline,Identifier texture,int x0,int x1,int y0,int y1,float u0,float u1,float v0,float v1,int color,CallbackInfo ci){
        if(VanillaUi.containerCanvas((GuiGraphicsExtractor)(Object)this,texture,x1-x0,y1-y0)
            ||VanillaUi.auxiliaryCanvas((GuiGraphicsExtractor)(Object)this,texture,x0,y0,x1-x0,y1-y0))ci.cancel();
    }
    @Inject(method="blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",at=@At("HEAD"),cancellable=true)
    private void spectre$widgets(RenderPipeline pipeline,Identifier id,int x,int y,int w,int h,int color,CallbackInfo ci){
        if(VanillaUi.sprite((GuiGraphicsExtractor)(Object)this,id,x,y,w,h,color))ci.cancel();
    }
    @ModifyVariable(method="innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",at=@At("HEAD"),argsOnly=true,ordinal=4)
    private int spectre$containerColor(int color,RenderPipeline pipeline,Identifier texture,int x0,int x1,int y0,int y1,float u0,float u1,float v0,float v1,int original){
        return VanillaUi.textureTint(texture,color);
    }
    @ModifyVariable(method={"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V","text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V","text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"},at=@At("HEAD"),argsOnly=true,ordinal=2)
    private int spectre$legibleLabels(int color){return VanillaUi.textColor(color);}
}
