package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.spectre.client.module.impl.misc.TridentUtilModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TridentItem.class)
public abstract class MixinTridentItem {
   @ModifyExpressionValue(
      method = {"use", "releaseUsing"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"),
      require = 0
   )
   private boolean spectre$allowOutOfWater(boolean original) {
      return original || TridentUtilModule.allowOutOfWater();
   }

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "CONSTANT", args = "intValue=10"), require = 0)
   private int spectre$scaleMinCharge(int original) {
      return TridentUtilModule.minChargeTicks(original);
   }
}
