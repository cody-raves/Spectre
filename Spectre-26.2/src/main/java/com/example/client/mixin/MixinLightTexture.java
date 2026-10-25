package com.example.client.mixin;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LightmapRenderStateExtractor.class)
public abstract class MixinLightTexture {
    @Inject(method="extract",at=@At("TAIL"))
    private void spectre$lighting(LightmapRenderState state,float partialTick,CallbackInfo ci){
        if(NoRenderModule.hideBlindnessDarkness())state.darknessEffectScale=0;
        var fullbright=ModuleRegistry.get(FullbrightModule.class);
        if(fullbright!=null&&fullbright.isEnabled()){state.brightness=fullbright.gamma();state.needsUpdate=true;}
        if(XrayModule.active!=null){state.ambientColor=new org.joml.Vector3f(1);state.darknessEffectScale=0;state.brightness=1;state.needsUpdate=true;}
    }
}
