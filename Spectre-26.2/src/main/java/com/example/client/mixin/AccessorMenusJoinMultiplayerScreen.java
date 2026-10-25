package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(JoinMultiplayerScreen.class)
public interface AccessorMenusJoinMultiplayerScreen {
   @Invoker("refreshServerList")
   void spectre$refreshServerList();
}
