package com.example.client.mixin;

import net.spectre.client.gui.VanillaUi;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AbstractButton.class)
abstract class MixinVanillaUiButton {
    @Inject(method="onClick",at=@At("HEAD"))
    private void spectre$click(MouseButtonEvent event,boolean doubleClick,CallbackInfo ci){
        var button=(AbstractButton)(Object)this;VanillaUi.press(button.getX(),button.getY(),button.getWidth(),button.getHeight());
    }
    @Inject(method="keyPressed",at=@At("RETURN"))
    private void spectre$key(KeyEvent key,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue()){var button=(AbstractButton)(Object)this;VanillaUi.press(button.getX(),button.getY(),button.getWidth(),button.getHeight());}
    }
}
