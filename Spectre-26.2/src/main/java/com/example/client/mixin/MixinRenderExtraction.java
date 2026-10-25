package com.example.client.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.spectre.client.util.TracerEngine;
import net.spectre.client.util.ShaderOverlay;
import net.spectre.client.event.impl.Render3DEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(LevelExtractor.class)
public abstract class MixinRenderExtraction {
    @ModifyArg(method="extract",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"),index=0)
    private Frustum spectre$renderEvents(Frustum frustum){
        var mc=Minecraft.getInstance();var state=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        TracerEngine.beginFrame(state.viewRotationMatrix,state.projectionMatrix);
        try(var capture=ShaderOverlay.capture()){Render3DEvent.dispatch(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false),frustum);}
        return frustum;
    }
}
