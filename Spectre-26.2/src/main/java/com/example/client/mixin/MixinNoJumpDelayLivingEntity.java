package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.impl.movement.NoJumpDelayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinNoJumpDelayLivingEntity {
   @Shadow
   private int noJumpDelay;

   @WrapOperation(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noJumpDelay:I", opcode = 181))
   private void spectre$noJumpDelay(LivingEntity self, int ticks, Operation<Void> original) {
      NoJumpDelayModule module = NoJumpDelayModule.active;
      if (module != null && ticks > this.noJumpDelay && self == Minecraft.getInstance().player) {
         ticks = module.rearm(ticks);
      }

      original.call(new Object[]{self, ticks});
   }
}
