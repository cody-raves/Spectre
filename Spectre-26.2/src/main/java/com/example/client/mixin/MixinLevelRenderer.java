package com.example.client.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.spectre.client.module.impl.render.ShaderEspModule;
import net.spectre.client.util.ShaderOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method="renderLevel",at=@At("TAIL"))
    private void spectre$afterLevel(CallbackInfo ci){
        var mc=Minecraft.getInstance();var camera=mc.gameRenderer.mainCamera();
        var state=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        var fog=((AccessorGameRenderer)mc.gameRenderer).spectre$getFogRenderer();
        net.spectre.client.builder.SchematicPreview.INSTANCE.renderTextured(camera.position(),state.viewRotationMatrix,state.projectionMatrix,fog.getBuffer(FogMode.WORLD));
        if(ShaderOverlay.pending())ShaderOverlay.render(camera,state.viewRotationMatrix,state.projectionMatrix,fog.getBuffer(FogMode.WORLD),fog.getBuffer(FogMode.NONE));
        if(ShaderEspModule.active!=null)ShaderEspModule.afterLevel(camera,state.viewRotationMatrix,state.projectionMatrix);
    }
}
