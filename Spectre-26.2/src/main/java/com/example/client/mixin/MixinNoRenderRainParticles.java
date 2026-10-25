package com.example.client.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.spectre.client.module.impl.render.NoRenderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class MixinNoRenderRainParticles {
    @Inject(method="tickWeatherEffects",at=@At("HEAD"),cancellable=true)
    private void spectre$rain(CallbackInfo ci){if(NoRenderModule.hideWeather())ci.cancel();}
}
