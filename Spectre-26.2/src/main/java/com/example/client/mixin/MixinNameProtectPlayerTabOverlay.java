package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class MixinNameProtectPlayerTabOverlay {
   @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
   private Component spectre$nameProtectTabName(Component original) {
      return NameProtectModule.protect(original);
   }

   @ModifyArg(
      method = "extractRenderState",
      at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
      index = 0
   )
   private FormattedText spectre$nameProtectTabHeader(FormattedText header) {
      return NameProtectModule.protectText(header);
   }

   @ModifyArg(
      method = "extractRenderState",
      at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
      index = 0
   )
   private FormattedText spectre$nameProtectTabFooter(FormattedText footer) {
      return NameProtectModule.protectText(footer);
   }

   @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"))
   private PlayerSkin spectre$nameProtectTabHead(PlayerInfo info, Operation<PlayerSkin> original) {
      PlayerSkin real = (PlayerSkin)original.call(new Object[]{info});
      return NameProtectModule.hidesName(info.getProfile().name()) ? NameProtectModule.hiddenHead(info.getProfile().id(), real) : real;
   }
}
