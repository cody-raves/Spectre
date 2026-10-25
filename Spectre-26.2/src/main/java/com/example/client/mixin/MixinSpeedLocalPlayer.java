package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.spectre.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinSpeedLocalPlayer {
   @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
   private Vec3 spectre$speedMove(Vec3 movement, @Local(argsOnly = true) MoverType type) {
      SpeedModule speed = SpeedModule.active;
      return speed != null && type == MoverType.SELF ? speed.onSelfMove((LocalPlayer)(Object)this, movement) : movement;
   }
}
