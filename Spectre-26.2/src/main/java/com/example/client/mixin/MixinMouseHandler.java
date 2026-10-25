package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.event.impl.MouseInputEvent;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.FreelookModule;
import net.spectre.client.module.impl.render.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
   @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
   private void spectre$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
      if (MouseInputEvent.dispatch(window, buttonInfo.button(), action, buttonInfo.modifiers())) {
         ci.cancel();
      }
   }

   @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
   private void spectre$wheel(long window, double horizontal, double vertical, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (vertical != 0.0 && window == mc.getWindow().handle() && mc.gui.screen() == null && mc.gui.overlay() == null) {
         if (ZoomModule.scroll(vertical) || FreecamModule.scroll(vertical)) {
            ci.cancel();
         }
      }
   }

   @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
   private void spectre$cameraTurn(LocalPlayer player, double yRot, double xRot, Operation<Void> original) {
      double scale = ZoomModule.turnScale();
      yRot *= scale;
      xRot *= scale;
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null) {
         freecam.turn(yRot, xRot);
      } else {
         FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
         if (freelook != null && freelook.cameraMode()) {
            freelook.turnCamera(yRot, xRot);
         } else {
            original.call(new Object[]{player, yRot, xRot});
         }
      }
   }
}
