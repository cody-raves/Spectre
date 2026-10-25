package com.example.client.mixin;

import net.spectre.client.gui.VanillaUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class MixinVanillaUiContainer {
    @Inject(method="extractSlot",at=@At("HEAD"))
    private void spectre$slot(GuiGraphicsExtractor g,Slot slot,int mx,int my,CallbackInfo ci){
        if(VanillaUi.enabled())VanillaUi.slot(g,slot.x-1,slot.y-1,slot==((AccessorAbstractContainerScreen<?>)(Object)this).spectre$getHoveredSlot());
    }
}
