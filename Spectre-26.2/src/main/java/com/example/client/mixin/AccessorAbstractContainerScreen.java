package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public interface AccessorAbstractContainerScreen<T extends AbstractContainerMenu> {
   @Accessor("leftPos") int spectre$left();
   @Accessor("topPos") int spectre$top();
   @Accessor("imageWidth") int spectre$width();
   @Accessor("imageHeight") int spectre$height();
   @Accessor("hoveredSlot")
   Slot spectre$getHoveredSlot();
}
