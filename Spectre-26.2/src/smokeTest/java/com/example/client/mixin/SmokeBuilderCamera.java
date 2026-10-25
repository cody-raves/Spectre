package com.example.client.mixin;

import net.minecraft.client.Camera;
import net.spectre.client.map.BuilderDemoClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filming camera only. Keeps the actual player and its movement packets unchanged. */
@Mixin(Camera.class)
public abstract class SmokeBuilderCamera {
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Shadow private boolean detached;
    @Inject(method="alignWithEntity",at=@At("TAIL"))
    private void demoCamera(float partialTick,CallbackInfo ci){
        if(!BuilderDemoClient.cameraActive)return;
        double a=BuilderDemoClient.cameraAngle;
        double x=2+Math.cos(a)*11,z=2+Math.sin(a)*11,y=-52;
        double dx=2-x,dy=-58-y,dz=2-z;
        setPosition(x,y,z);setRotation((float)Math.toDegrees(-Math.atan2(dx,dz)),(float)Math.toDegrees(-Math.atan2(dy,Math.hypot(dx,dz))));
        detached=true;
    }
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void demoCull(net.minecraft.client.renderer.state.level.CameraRenderState state,float tick,CallbackInfo ci){
        if(BuilderDemoClient.cameraActive)state.smartCull=false;
    }
}
