package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinElytraFlyEntity {
   @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
   private Pose spectre$elytraFlyGetPose(Pose original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if ((Object)this == player && mc.isSameThread()) {
            return module.canPacketEfly(player) ? Pose.FALL_FLYING : original;
         } else {
            return original;
         }
      }
   }
}
