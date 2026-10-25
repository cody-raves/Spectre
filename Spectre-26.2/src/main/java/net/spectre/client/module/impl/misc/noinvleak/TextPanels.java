package net.spectre.client.module.impl.misc.noinvleak;

import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
final class TextPanels {
   private static final int TOOLTIP_MARGIN = 12;
   private static final int LINE = 10;
   private static final Canvas canvas = new Canvas();
   private static OverlayWindow tooltipWindow;
   private static OverlayWindow nameWindow;
   private static ItemStack tooltipStack;
   private static int tooltipMouseX;
   private static int tooltipMouseY;
   private static ItemStack nameStack;
   private static int nameAlpha;
   private static int nameY;
   private static ItemStack shownTooltip;
   private static int shownTooltipCount;
   private static int shownTooltipDamage;
   private static int shownTooltipScale;
   private static int tooltipWidth;
   private static int tooltipHeight;
   private static int tooltipScreenX;
   private static int tooltipScreenY;
   private static ItemStack shownName;
   private static int shownNameAlpha;
   private static int shownNameScale;
   private static int nameScreenX;
   private static int nameScreenY;

   private TextPanels() {
   }

   static void tooltip(ItemStack stack, int mouseX, int mouseY) {
      tooltipStack = stack;
      tooltipMouseX = mouseX;
      tooltipMouseY = mouseY;
   }

   static void itemName(ItemStack stack, int alpha, int y) {
      nameStack = stack;
      nameAlpha = alpha;
      nameY = y;
   }

   static boolean started() {
      return tooltipWindow != null || nameWindow != null;
   }

   static void frameDone() {
      tooltipStack = null;
      nameStack = null;
   }

   static void update(Window win, int scale, int windowX, int windowY) {
      Minecraft mc = Minecraft.getInstance();
      int guiW = win.getGuiScaledWidth();
      int guiH = win.getGuiScaledHeight();
      updateTooltip(mc, win, scale, windowX, windowY, guiW, guiH);
      updateName(mc, win, scale, windowX, windowY, guiW);
   }

   private static void updateTooltip(Minecraft mc, Window win, int scale, int windowX, int windowY, int guiW, int guiH) {
      ItemStack stack = tooltipStack;
      if (stack == null) {
         if (tooltipWindow != null) {
            tooltipWindow.hide();
         }

         shownTooltip = null;
      } else {
         if (tooltipWindow == null) {
            tooltipWindow = new OverlayWindow(win.handle());
         }

         boolean changed = stack != shownTooltip
            || stack.getCount() != shownTooltipCount
            || stack.getDamageValue() != shownTooltipDamage
            || scale != shownTooltipScale;
         if (changed) {
            List<Component> lines = Screen.getTooltipFromItem(mc, stack);
            int w = 0;
            int h = lines.size() == 1 ? -2 : 0;

            for (Component line : lines) {
               w = Math.max(w, mc.font.width(line));
               h += 10;
            }

            tooltipWidth = w;
            tooltipHeight = h;
            int boxW = w + 24;
            int boxH = h + 24;
            canvas.wrap(tooltipWindow.pixels(boxW * scale, boxH * scale), tooltipWindow.stride(), boxW * scale, boxH * scale);
            OverlayArt.slice(canvas, OverlayArt.tooltipBackground(), 0, 0, boxW, boxH, scale);
            OverlayArt.slice(canvas, OverlayArt.tooltipFrame(), 0, 0, boxW, boxH, scale);
            int y = 12;

            for (int i = 0; i < lines.size(); i++) {
               OverlayArt.text(canvas, lines.get(i), 12, y, scale, -1, true);
               y += 10 + (i == 0 ? 2 : 0);
            }

            shownTooltip = stack;
            shownTooltipCount = stack.getCount();
            shownTooltipDamage = stack.getDamageValue();
            shownTooltipScale = scale;
         }

         int x = tooltipMouseX + 12;
         int y = tooltipMouseY - 12;
         if (x + tooltipWidth > guiW) {
            x = Math.max(x - 24 - tooltipWidth, 4);
         }

         if (y + tooltipHeight + 3 > guiH) {
            y = guiH - tooltipHeight - 3;
         }

         int screenX = windowX + (x - 12) * scale;
         int screenY = windowY + (y - 12) * scale;
         if (changed) {
            tooltipWindow.present(screenX, screenY, (tooltipWidth + 24) * scale, (tooltipHeight + 24) * scale);
         } else if (screenX != tooltipScreenX || screenY != tooltipScreenY) {
            tooltipWindow.move(screenX, screenY);
         }

         tooltipScreenX = screenX;
         tooltipScreenY = screenY;
      }
   }

   private static void updateName(Minecraft mc, Window win, int scale, int windowX, int windowY, int guiW) {
      ItemStack stack = nameStack;
      if (stack != null && nameAlpha > 0) {
         if (nameWindow == null) {
            nameWindow = new OverlayWindow(win.handle());
         }

         Component name = Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity().color());
         if (stack.has(DataComponents.CUSTOM_NAME)) {
            name = name.copy().withStyle(ChatFormatting.ITALIC);
         }

         int width = mc.font.width(name);
         int x = (guiW - width) / 2;
         int screenX = windowX + (x - 2) * scale;
         int screenY = windowY + (nameY - 2) * scale;
         boolean changed = stack != shownName || nameAlpha != shownNameAlpha || scale != shownNameScale;
         if (changed) {
            int boxW = width + 4;
            int boxH = 9 + 4;
            canvas.wrap(nameWindow.pixels(boxW * scale, boxH * scale), nameWindow.stride(), boxW * scale, boxH * scale);
            int backdrop = mc.options.getBackgroundColor(0.0F);
            if (backdrop != 0) {
               int a = (backdrop >>> 24) * nameAlpha / 255;
               canvas.rect(0, 0, boxW * scale, boxH * scale, a << 24 | backdrop & 16777215);
            }

            OverlayArt.text(canvas, name, 2, 2, scale, nameAlpha << 24 | 16777215, true);
            nameWindow.present(screenX, screenY, boxW * scale, boxH * scale);
            shownName = stack;
            shownNameAlpha = nameAlpha;
            shownNameScale = scale;
         } else if (screenX != nameScreenX || screenY != nameScreenY) {
            nameWindow.move(screenX, screenY);
         }

         nameScreenX = screenX;
         nameScreenY = screenY;
      } else {
         if (nameWindow != null) {
            nameWindow.hide();
         }

         shownName = null;
      }
   }

   static void hide() {
      if (tooltipWindow != null) {
         tooltipWindow.hide();
      }

      if (nameWindow != null) {
         nameWindow.hide();
      }

      shownTooltip = null;
      shownName = null;
   }

   static void release() {
      if (tooltipWindow != null) {
         tooltipWindow.destroy();
         tooltipWindow = null;
      }

      if (nameWindow != null) {
         nameWindow.destroy();
         nameWindow = null;
      }

      shownTooltip = null;
      shownName = null;
      tooltipStack = null;
      nameStack = null;
   }
}
