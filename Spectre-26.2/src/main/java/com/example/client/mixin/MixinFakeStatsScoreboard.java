package com.example.client.mixin;

import net.spectre.client.module.impl.misc.FakeStats;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Scoreboard.class)
public abstract class MixinFakeStatsScoreboard {
   @Inject(method = "setDisplayObjective", at = @At("HEAD"), cancellable = true)
   private void spectre$fakeSidebar(DisplaySlot slot, Objective objective, CallbackInfo ci) {
      if (FakeStats.redirect((Scoreboard)(Object)this, slot, objective)) {
         ci.cancel();
      }
   }
}
