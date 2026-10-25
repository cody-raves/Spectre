package net.spectre.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.ModuleConfigStore;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ImGuiScreen extends Screen {
   private final ClickGuiRenderer clickGui = new ClickGuiRenderer();
   public boolean builderWorkspaceOpen(){return clickGui.builderWorkspaceOpen();}

   public ImGuiScreen() {
      super(Component.literal("SPECTRE"));
   }

   @Override
   protected void init() {
      super.init();
      ImGuiManager.getInstance().activate(this.clickGui::render);
   }

   @Override
   public void removed() {
      super.removed();
      this.clickGui.handleEscape();
      ImGuiManager.getInstance().deactivate();
      ModuleConfigStore.getInstance().save();
      ThemeStore.get().save();
      HudTheme.get().save();
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (ThemeStore.get().menuBlur.get()) {
         super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   @Override
   public void onFilesDrop(List<Path> paths) {
      this.clickGui.onFilesDropped(paths);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      return event.key() == 256 && this.clickGui.handleEscape() ? true : super.keyPressed(event);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
