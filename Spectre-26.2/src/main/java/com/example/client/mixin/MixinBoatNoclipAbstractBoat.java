package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.BoatNoclipModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBoat.class)
public abstract class MixinBoatNoclipAbstractBoat {
   @ModifyReturnValue(method = "canCollideWith", at = @At("RETURN"))
   private boolean spectre$boatNoclipCollide(boolean original) {
      if (original && BoatNoclipModule.active != null) {
         AbstractBoat boat = (AbstractBoat)(Object)this;
         return !boat.level().isClientSide() || boat.getControllingPassenger() != Minecraft.getInstance().player;
      } else {
         return original;
      }
   }
}
