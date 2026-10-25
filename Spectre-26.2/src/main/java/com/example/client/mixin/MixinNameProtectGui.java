package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Hud.class)
public abstract class MixinNameProtectGui {
   @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
   private Component spectre$nameProtectTitle(Component title) {
      return NameProtectModule.protect(title);
   }

   @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
   private Component spectre$nameProtectSubtitle(Component subtitle) {
      return NameProtectModule.protect(subtitle);
   }

   @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
   private Component spectre$nameProtectActionBar(Component message) {
      return NameProtectModule.protect(message);
   }

   @ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
   private Component spectre$nameProtectSidebarTitle(Component original) {
      return NameProtectModule.protect(net.spectre.client.gui.DonutNamePrivacy.mask(original));
   }

   @ModifyExpressionValue(
      method = "displayScoreboardSidebar",
      at = @At(value = "FIELD", opcode = 180, ordinal = 0, target = "Lnet/minecraft/client/gui/Hud$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;")
   )
   private Component spectre$nameProtectSidebarLineWidth(Component original) {
      return NameProtectModule.protect(net.spectre.client.gui.DonutNamePrivacy.mask(original));
   }

   @ModifyExpressionValue(
      method = "displayScoreboardSidebar",
      at = @At(value = "FIELD", opcode = 180, ordinal = 1, target = "Lnet/minecraft/client/gui/Hud$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;")
   )
   private Component spectre$nameProtectSidebarLineDraw(Component original) {
      return NameProtectModule.protect(net.spectre.client.gui.DonutNamePrivacy.mask(original));
   }
}
