package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.map.AtlasSmokeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in visual evidence of the actual startup splash, before the buffer swap. */
@Mixin(value = ImGuiManager.class, remap = false)
public class SmokeBrandingCapture {
    private static int spectre$loadingFrames;
    @Inject(method = "renderIfActive", at = @At("TAIL"), remap = false)
    private void spectre$capture(CallbackInfo ci) throws Exception {
        var mc = Minecraft.getInstance();
        net.spectre.client.map.ShowcaseCapture.frame(mc);
        if (mc.gui.overlay() instanceof LoadingOverlay && ++spectre$loadingFrames == 20) {
            AtlasSmokeClient.captureBack(mc, "spectre-startup.png");
        }
    }
}
