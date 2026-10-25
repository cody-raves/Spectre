package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(BaseSpawner.class)
public interface AccessorSpawnerNametagsBaseSpawner {
   @Accessor("nextSpawnData")
   SpawnData spectre$spawnerNametagsNextSpawnData();

   @Accessor("requiredPlayerRange")
   int spectre$spawnerNametagsRequiredPlayerRange();
}
