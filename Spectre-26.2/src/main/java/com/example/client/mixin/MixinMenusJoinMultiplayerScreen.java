package com.example.client.mixin;

import net.spectre.client.gui.SpectreTitleScreen;
import net.spectre.client.gui.mainmenu.MultiplayerOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinMenusJoinMultiplayerScreen extends Screen {
   @Unique
   private MultiplayerOverlay spectre$overlay;

   protected MixinMenusJoinMultiplayerScreen(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void spectre$addOverlay(CallbackInfo ci) {
      this.spectre$overlay = SpectreTitleScreen.enabled() ? new MultiplayerOverlay((JoinMultiplayerScreen)(Object)this) : null;
      if (this.spectre$overlay != null) {
         this.spectre$overlay.show();
      }
   }

   @Inject(method = "repositionElements", at = @At("TAIL"))
   private void spectre$showOverlay(CallbackInfo ci) {
      if (this.spectre$overlay != null) {
         this.spectre$overlay.show();
      }
   }

   @Inject(method = "removed", at = @At("TAIL"))
   private void spectre$hideOverlay(CallbackInfo ci) {
      if (this.spectre$overlay != null) {
         this.spectre$overlay.hide();
      }
   }

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   private void spectre$overlayKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (this.spectre$overlay != null && this.spectre$overlay.keyPressed(event.key())) {
         cir.setReturnValue(true);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return this.spectre$takesPointer() || super.mouseClicked(event, doubleClick);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      return this.spectre$takesPointer() || super.mouseReleased(event);
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      return this.spectre$takesPointer() || super.mouseDragged(event, dragX, dragY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return this.spectre$takesPointer() || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   @Unique
   private boolean spectre$takesPointer() {
      return this.spectre$overlay != null && this.spectre$overlay.takesPointer();
   }
}
