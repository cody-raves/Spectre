package com.example.client.mixin;

import net.spectre.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinEntityControlEntity {
   @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
   private Vec3 spectre$entityControlVehicleMove(Vec3 movement, MoverType type) {
      return !EntityControlVehicleHook.anyActive ? movement : EntityControlVehicleHook.onEntityMove((Entity)(Object)this, type, movement);
   }
}
