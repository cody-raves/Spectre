package com.example.client.mixin;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.spectre.client.module.impl.render.ShaderEspModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinShaderEspLevelRenderer {
    @Inject(method="extractEntity",at=@At("RETURN"))
    private void spectre$keep(Entity entity,float tick,CallbackInfoReturnable<EntityRenderState> cir){if(ShaderEspModule.active!=null)ShaderEspModule.extracted(entity,cir.getReturnValue());}
}
