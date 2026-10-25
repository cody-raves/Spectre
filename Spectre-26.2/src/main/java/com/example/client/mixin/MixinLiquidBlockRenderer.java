package com.example.client.mixin;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.spectre.client.module.impl.render.XrayModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(FluidRenderer.class)
public abstract class MixinLiquidBlockRenderer {
    @ModifyVariable(method="vertex",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private int spectre$fluidAlpha(int color){
        if(XrayModule.active==null)return color;
        int alpha=(int)((color>>>24)*XrayModule.CURRENT_ALPHA.get()[0]);
        return (color&0xffffff)|(alpha<<24);
    }
}
