package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class MixinCamera {
   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
   private void spectre$freelookRotation(Camera camera, float yRot, float xRot, Operation<Void> original) {
      FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
      if (FreecamModule.active == null && freelook != null && freelook.isEnabled()) {
         original.call(new Object[]{camera, freelook.cameraYaw(), freelook.cameraPitch()});
      } else {
         original.call(new Object[]{camera, yRot, xRot});
      }
   }

   @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
   private float spectre$freelookThroughBlocks(Camera camera, float distance, Operation<Float> original) {
      return FreelookModule.throughBlocks() ? distance : (Float)original.call(new Object[]{camera, distance});
   }

   @Inject(method = "alignWithEntity", at = @At("RETURN"))
   private void spectre$freecamCamera(float partialTick, CallbackInfo ci) {
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null) {
         this.setPosition(freecam.getX(partialTick), freecam.getY(partialTick), freecam.getZ(partialTick));
         this.setRotation(freecam.getYaw(), freecam.getPitch());
      }
   }
   @Inject(method="extractRenderState",at=@At("TAIL"))
   private void spectre$cameraState(net.minecraft.client.renderer.state.level.CameraRenderState state,float tick,CallbackInfo ci){
      if(net.spectre.client.module.impl.render.NoRenderModule.hideBlindnessDarkness())state.entityRenderState.doesMobEffectBlockSky=false;
      if(FreecamModule.active!=null || ((Camera)(Object)this).isDetached()&&FreelookModule.throughBlocks())state.smartCull=false;
   }
}
