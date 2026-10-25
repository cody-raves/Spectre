package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBoat.class)
public abstract class MixinEntityControlAbstractBoat {
   @ModifyExpressionValue(method = "controlBoat", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputLeft:Z", opcode = 180))
   private boolean spectre$entityControlInputLeft(boolean original) {
      return original && !EntityControlVehicleHook.locksBoatTurning((AbstractBoat)(Object)this);
   }

   @ModifyExpressionValue(method = "controlBoat", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputRight:Z", opcode = 180))
   private boolean spectre$entityControlInputRight(boolean original) {
      return original && !EntityControlVehicleHook.locksBoatTurning((AbstractBoat)(Object)this);
   }
}
