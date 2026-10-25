package com.example.client.mixin;

import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.combat.WTapRecodeModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {
   @Inject(method = "attack", at = @At("RETURN"))
   private void spectre$afterAttack(Player player, Entity entity, CallbackInfo ci) {
      WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
      if (wTap != null && wTap.isEnabled()) {
         wTap.afterAttack();
      }
   }
}
