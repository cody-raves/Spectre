package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.spectre.client.gui.mainmenu.ConnectionsPanel;
import net.spectre.client.gui.mainmenu.DuskMenu;
import net.spectre.client.gui.mainmenu.MenuContext;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class MainMenuRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final float NOWHERE = -10000.0F;
   private final MenuContext context;
   private final ConnectionsPanel accounts = new ConnectionsPanel(() -> {});
   private long openedAt = System.nanoTime();
   private boolean failed;

   MainMenuRenderer(Screen screen) {
      this.context = new MenuContext(screen, () -> this.accounts.open(ConnectionsPanel.Tab.ACCOUNTS));
   }

   boolean keyPressed(int key) {
      return this.accounts.keyPressed(key);
   }

   void render() {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      if (mc.gui.overlay() != null) {
         this.openedAt = now;
      }

      ImGuiIO io = ImGui.getIO();
      boolean live = mc.gui.overlay() == null;
      float t = (float)(now - this.openedAt) / 1.0E9F;
      float mx = io.getMousePosX();
      float my = io.getMousePosY();
      boolean click = live && ImGui.isMouseClicked(0);
      float width = io.getDisplaySizeX();
      float height = io.getDisplaySizeY();
      float scale = ImGuiManager.getInstance().uiScale();
      float dt = Math.max(0.001F, io.getDeltaTime());
      boolean menuPointer = !this.accounts.isOpen();
      this.context
         .begin(
            ImGui.getBackgroundDrawList(),
            ImGui.getForegroundDrawList(),
            width,
            height,
            scale,
            t,
            dt,
            menuPointer ? mx : -10000.0F,
            menuPointer ? my : -10000.0F,
            menuPointer && click
         );

      try {
         DuskMenu.render(this.context);
      } catch (RuntimeException var16) {
         if (!this.failed) {
            LOGGER.error("[Spectre] Main menu failed to render", var16);
         }

         this.failed = true;
         return;
      }

      if (this.context.pendingTooltip() != null) {
         drawTooltip(this.context);
      }

      this.accounts.render(width, height, scale, dt);
   }

   private static void drawTooltip(MenuContext c) {
      ImDrawList dl = c.fg();
      ImFont font = c.tagFont();
      String text = c.pendingTooltip();
      float size = c.textHeight(font);
      float w = c.textWidth(font, text) + c.px(18.0F);
      float h = size + c.px(9.0F);
      float x = c.tooltipX() - w * 0.5F;
      float y = c.tooltipY();
      dl.addRectFilled(x, y, x + w, y + h, MenuContext.col(-16053492, 0.96F), c.px(3.0F));
      dl.addRect(x, y, x + w, y + h, MenuContext.col(-1, 0.25F), c.px(3.0F), 0, Math.max(1.0F, c.scale()));
      c.text(dl, font, x + c.px(9.0F), y + c.px(4.5F), -921103, 1.0F, text);
   }
}
