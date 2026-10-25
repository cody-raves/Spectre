package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.SkinChangeModule;
import net.spectre.client.module.impl.render.NametagsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerRenderer {
   @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
   private void spectre$injectSkin(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
      if (entity == net.minecraft.client.Minecraft.getInstance().player && net.spectre.client.gui.DonutNamePrivacy.enabled()) {
         if(state.nameTag != null) state.nameTag = net.spectre.client.gui.DonutNamePrivacy.mask(entity.getDisplayName());
         if(state.scoreText != null) state.scoreText = net.spectre.client.gui.DonutNamePrivacy.mask(state.scoreText);
      }
      if (NametagsModule.hideVanillaNametags()) { state.nameTag = null; state.scoreText = null; }
      SkinChangeModule mod = ModuleRegistry.get(SkinChangeModule.class);
      if (mod != null && mod.isEnabled()) {
         state.skin = mod.skinFor(state.id, state.skin);
      }
   }

}
