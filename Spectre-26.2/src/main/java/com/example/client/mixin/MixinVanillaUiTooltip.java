package com.example.client.mixin;

import net.spectre.client.gui.VanillaUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
abstract class MixinVanillaUiTooltip {
    @Inject(method="extractTooltipBackground",at=@At("HEAD"),cancellable=true)
    private static void spectre$tooltip(GuiGraphicsExtractor g,int x,int y,int w,int h,Identifier style,CallbackInfo ci){
        if(VanillaUi.enabled()&&style==null){VanillaUi.panel(g,x-5,y-5,w+10,h+10,true,250);ci.cancel();}
    }
}
