package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/world/item/ItemCooldowns$CooldownInstance")
public interface AccessorHudCooldownInstance {
   @Accessor("startTime")
   int spectre$hudStartTime();

   @Accessor("endTime")
   int spectre$hudEndTime();
}
