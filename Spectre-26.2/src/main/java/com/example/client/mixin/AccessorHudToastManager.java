package com.example.client.mixin;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ToastManager.class)
public interface AccessorHudToastManager {
   @Accessor("visibleToasts")
   List<?> spectre$hudVisibleToasts();
}
