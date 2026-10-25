package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.spectre.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Slot.class)
public abstract class MixinNoInvLeakSlot {
   @ModifyReturnValue(method = "getNoItemIcon", at = @At("RETURN"))
   private Identifier spectre$noInvLeakSilhouette(Identifier icon) {
      Slot self = (Slot)(Object)this;
      return icon != null && NoInvLeakModule.overlaying() && self.container instanceof Inventory && self.hasItem() ? null : icon;
   }
}
