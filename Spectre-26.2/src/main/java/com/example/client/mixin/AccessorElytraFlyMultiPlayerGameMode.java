package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public interface AccessorElytraFlyMultiPlayerGameMode {
   @Invoker("ensureHasSentCarriedItem")
   void spectre$elytraFlySyncSelected();
}
