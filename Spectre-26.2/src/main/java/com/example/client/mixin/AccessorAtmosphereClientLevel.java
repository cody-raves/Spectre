package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ClientLevel.class)
public interface AccessorAtmosphereClientLevel {
   @Accessor("skyFlashTime")
   int spectre$atmosphereSkyFlash();
}
