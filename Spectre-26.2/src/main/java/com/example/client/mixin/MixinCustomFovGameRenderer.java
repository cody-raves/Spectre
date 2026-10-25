package com.example.client.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.render.CustomFovModule;
import net.spectre.client.module.impl.render.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Camera.class)
public abstract class MixinCustomFovGameRenderer {
    @Shadow public abstract boolean isPanoramicMode();
    @ModifyReturnValue(method="calculateFov",at=@At("RETURN"))
    private float spectre$customFov(float original){
        if(isPanoramicMode())return original;
        var module=CustomFovModule.active;
        return ZoomModule.fov(module==null?original:module.fov());
    }
    @ModifyReturnValue(method="calculateHudFov",at=@At("RETURN"))
    private float spectre$customHudFov(float original){
        var module=CustomFovModule.active;
        float fov=module!=null&&!isPanoramicMode()?module.fov():original;
        CustomFovModule.hudFovApplied(original,fov);
        return fov;
    }
}
