package com.example.client.mixin;

import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.EspModule;
import net.spectre.client.module.impl.render.ItemEspModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class MixinEntity {
   @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
   private void spectre$isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity)(Object)this;
      if (self.level().isClientSide()) {
         if (EspModule.shouldGlow(self) || ItemEspModule.shouldGlow(self)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
   private void spectre$getTeamColor(CallbackInfoReturnable<Integer> cir) {
      Entity self = (Entity)(Object)this;
      if (self.level().isClientSide()) {
         if (EspModule.shouldGlow(self)) {
            cir.setReturnValue(EspModule.glowColor(self));
         } else if (ItemEspModule.shouldGlow(self)) {
            cir.setReturnValue(ItemEspModule.glowColor(self));
         }
      }
   }

   @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
   private void spectre$freecamOwnBody(Player viewer, CallbackInfoReturnable<Boolean> cir) {
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null && freecam.drawsSelf((Entity)(Object)this, viewer)) {
         cir.setReturnValue(false);
      }
   }
}
