package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.spectre.client.event.impl.PlayerMoveEvent;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {
   @WrapMethod(method = "move")
   private void spectre$onMove(MoverType type, Vec3 movement, Operation<Void> original) {
      if (!PlayerMoveEvent.TYPE.hasListeners()) {
         original.call(new Object[]{type, movement});
      } else {
         PlayerMoveEvent.move(movement, changed -> original.call(new Object[]{type, changed}));
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void spectre$walkServerRotation(CallbackInfo ci) {
      ServerRotation.applyForMovement((LocalPlayer)(Object)this);
   }

   @Inject(method = "tick", at = @At("RETURN"))
   private void spectre$finishServerRotation(CallbackInfo ci) {
      ServerRotation.finishTick((LocalPlayer)(Object)this);
   }

   @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = Shift.AFTER))
   private void spectre$freecamAfterInput(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "sendPosition", at = @At("HEAD"))
   private void spectre$swapInServerRotation(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }

      ServerRotation.beforeSend(self);
   }

   @Inject(method = "sendPosition", at = @At("RETURN"))
   private void spectre$swapOutServerRotation(CallbackInfo ci) {
      ServerRotation.afterSend((LocalPlayer)(Object)this);
   }
}
