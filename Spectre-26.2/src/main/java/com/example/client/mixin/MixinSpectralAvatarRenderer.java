package com.example.client.mixin;

import net.spectre.client.module.impl.render.SpectralHoodLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderer.class)
public abstract class MixinSpectralAvatarRenderer<T extends Avatar & ClientAvatarEntity> extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel> {
   protected MixinSpectralAvatarRenderer(Context context, PlayerModel model, float shadowRadius) {
      super(context, model, shadowRadius);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void spectre$addHeadCosmetics(Context context, boolean slim, CallbackInfo ci) {
      this.addLayer(new SpectralHoodLayer(this));
   }
}
