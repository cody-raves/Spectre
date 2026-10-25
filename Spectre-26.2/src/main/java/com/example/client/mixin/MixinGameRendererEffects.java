package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererEffects {
   @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
   private boolean spectre$freecamHands(boolean firstPerson) {
      FreecamModule freecam = FreecamModule.active;
      return freecam != null && freecam.keepHands() || firstPerson;
   }

   @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
   private void spectre$cancelTotemAnimation(ItemStack stack, CallbackInfo ci) {
      if (NoRenderModule.hideTotemAnimation()) {
         ci.cancel();
      }
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
   private float spectre$cancelNauseaWobble(float portal, float nausea, Operation<Float> original) {
      return NoRenderModule.hideNauseaPortal() ? 0.0F : (Float)original.call(new Object[]{portal, nausea});
   }
}
