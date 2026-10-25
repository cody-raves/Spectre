package com.example.client.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ItemCooldowns.class)
public interface AccessorHudItemCooldowns {
   @Accessor("cooldowns")
   Map<Identifier, ?> spectre$hudCooldowns();

   @Accessor("tickCount")
   int spectre$hudTickCount();
}
