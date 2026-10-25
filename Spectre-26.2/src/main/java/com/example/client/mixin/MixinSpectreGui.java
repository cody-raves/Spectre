package com.example.client.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.spectre.client.gui.SpectreTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class MixinSpectreGui {
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void spectre$title(Screen screen,CallbackInfo ci){
        if(!(screen instanceof SpectreTitleScreen)&&SpectreTitleScreen.enabled()&&(screen instanceof TitleScreen||screen==null&&Minecraft.getInstance().level==null)){
            ci.cancel();((Gui)(Object)this).setScreen(new SpectreTitleScreen());
        }
    }
    @Inject(method="setScreen",at=@At("HEAD"))
    private void spectre$spamDisconnected(Screen screen,CallbackInfo ci){
        if(net.spectre.client.module.impl.misc.SpamModule.active!=null && Minecraft.getInstance().level!=null && screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)
            net.spectre.client.module.impl.misc.SpamModule.onDisconnectedScreen();
    }
    @Inject(method="setScreen",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;releaseAll()V"))
    private void spectre$releaseKeys(Screen screen,CallbackInfo ci){
        var module=net.spectre.client.module.impl.misc.SpawnerProtectModule.active;
        if(module!=null)module.onKeysReleased(screen);
    }
}
