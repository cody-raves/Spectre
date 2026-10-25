package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public interface AccessorBlockOverlayMultiPlayerGameMode {
   @Accessor("destroyProgress")
   float spectre$blockOverlayDestroyProgress();

   @Accessor("destroyBlockPos")
   BlockPos spectre$blockOverlayDestroyPos();
}
