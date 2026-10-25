package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(BlockBehaviour.class)
public interface AccessorElytraFlyBlockBehaviour {
   @Accessor("hasCollision")
   boolean spectre$elytraFlyHasCollision();
}
