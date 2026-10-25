package net.spectre.client.gui;

import net.spectre.client.config.ThemeStore;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

@Environment(EnvType.CLIENT)
public final class SpectreTitleScreen extends TitleScreen {
   private final MainMenuRenderer menu = new MainMenuRenderer(this);

   public static boolean enabled() {
      return ThemeStore.get().customMainMenu.get();
   }

   @Override
   protected void init() {
      ImGuiManager.getInstance().activate(this.menu::render);
   }

   @Override
   public void tick() {
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      return this.menu.keyPressed(event.key()) || super.keyPressed(event);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return false;
   }

   @Override
   public void removed() {
      super.removed();
      ImGuiManager.getInstance().deactivate();
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
