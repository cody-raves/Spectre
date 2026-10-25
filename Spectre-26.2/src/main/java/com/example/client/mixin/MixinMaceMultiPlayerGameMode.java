package com.example.client.mixin;

import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.combat.MaceModule;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
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
public abstract class MixinMaceMultiPlayerGameMode {
   @Inject(method = "attack", at = @At("HEAD"))
   private void spectre$maceBeforeAttack(Player player, Entity target, CallbackInfo ci) {
      MeleeAttacks.noteAttack(player);
      MaceModule mace = ModuleRegistry.get(MaceModule.class);
      if (mace != null && mace.isEnabled()) {
         mace.beforeAttack(player, target);
      }
   }
}
