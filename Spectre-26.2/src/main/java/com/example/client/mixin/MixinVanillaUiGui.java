package com.example.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.spectre.client.gui.VanillaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(Gui.class)
abstract class MixinVanillaUiGui {
    @Inject(method="setScreen",at=@At("HEAD"))
    private void spectre$transition(Screen next,CallbackInfo ci){VanillaUi.changingScreen(next);}

    @WrapOperation(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Hud;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void spectre$closing(Hud hud,GuiGraphicsExtractor g,DeltaTracker delta,Operation<Void> original){
        VanillaUi.extractClosing(g,delta.getGameTimeDeltaTicks());
        net.spectre.client.media.MediaPlayer.extractPinned(g);
        original.call(hud,g,delta);
    }
}
