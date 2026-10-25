package com.example.client.mixin;

import net.spectre.client.gui.VanillaUi;
import net.spectre.client.config.ThemeStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(Screen.class)
abstract class MixinVanillaUiScreen {
    @Shadow public int width;
    @Shadow public int height;
    @Inject(method="extractRenderStateWithTooltipAndSubtitles",at=@At("HEAD"))
    private void spectre$begin(GuiGraphicsExtractor g,int mx,int my,float tick,CallbackInfo ci){VanillaUi.beginFrame((Screen)(Object)this);}
    @WrapOperation(method="extractRenderStateWithTooltipAndSubtitles",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void spectre$buildBackground(Screen screen,GuiGraphicsExtractor g,int mx,int my,float tick,Operation<Void> original){
        if(!VanillaUi.building()){original.call(screen,g,mx,my,tick);return;}
        if(!VanillaUi.closingPass())VanillaUi.stableBackdrop(g,width,height);
        VanillaUi.build(g,()->original.call(screen,g,mx,my,tick));
    }
    @WrapOperation(method="extractRenderStateWithTooltipAndSubtitles",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void spectre$buildContents(Screen screen,GuiGraphicsExtractor g,int mx,int my,float tick,Operation<Void> original){
        if(VanillaUi.building())VanillaUi.build(g,()->original.call(screen,g,mx,my,tick));else original.call(screen,g,mx,my,tick);
    }
    @Inject(method="extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",at=@At("HEAD"),cancellable=true)
    private void spectre$menu(GuiGraphicsExtractor g,int x,int y,int w,int h,CallbackInfo ci){
        if(VanillaUi.enabled()){g.fillGradient(x,y,x+w,y+h,VanillaUi.alpha(VanillaUi.surface(),215),VanillaUi.alpha(VanillaUi.card(),235));ci.cancel();}
    }
    @Inject(method="extractTransparentBackground",at=@At("HEAD"),cancellable=true)
    private void spectre$dim(GuiGraphicsExtractor g,CallbackInfo ci){
        if(VanillaUi.buildPass()){ci.cancel();return;}
        if(VanillaUi.enabled()){int a=Math.round(ThemeStore.get().backgroundDim.get()*255);g.fillGradient(0,0,width,height,VanillaUi.alpha(VanillaUi.surface(),a),VanillaUi.alpha(VanillaUi.card(),Math.min(210,a+45)));ci.cancel();}
    }
}
