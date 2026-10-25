package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.event.EventBus;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.SpectreTitleScreen;
import net.spectre.client.module.impl.misc.AutoEatModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
   @Shadow
   public ClientLevel level;

   @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
   private void spectre$windowTitle(CallbackInfoReturnable<String> cir) {
      if (SpectreTitleScreen.enabled()) {
         cir.setReturnValue("Minecraft " + SharedConstants.getCurrentVersion().name() + " - Spectre Client");
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void spectre$onTickStart(CallbackInfo ci) {
      EventBus.INSTANCE.drainAsync();
      TickEvent.dispatch();
   }

   @Inject(method = "tick", at = @At("RETURN"))
   private void spectre$onTickEnd(CallbackInfo ci) {
      TickEvent.dispatchPost();
   }

   @ModifyExpressionValue(
      method = "startUseItem",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;")
   )
   private Type spectre$autoEatIgnoreCrosshair(Type type) {
      return AutoEatModule.isEating() ? Type.MISS : type;
   }
}
