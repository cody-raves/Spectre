package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.client.HudModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class WorldText {
   public static final int NAME = HudChrome.TEXT_NAME;
   public static final int LABEL = HudChrome.TEXT_LABEL;
   private static final float LINE_GAP = 1.0F;
   private static final float ANCHOR_GAP = 2.0F;

   private WorldText() {
   }

   public static int hudAccent() {
      HudModule hud = ModuleRegistry.get(HudModule.class);
      return hud != null && hud.customAccent() ? GuiPalette.abgr(hud.accentArgb()) : GuiPalette.accent();
   }

   public static void draw(ImDrawList dl, float cx, float bottom, String name, String[] runs, int[] colors, float alpha) {
      if (!(alpha <= 0.001F)) {
         ImGuiManager fonts = ImGuiManager.getInstance();
         float u = fonts.uiScale();
         ImFont nameFont = fonts.hudTextFont();
         ImFont detailFont = fonts.hudLabelFont();
         int nameSize = HudChrome.px(17.0F, u);
         int detailSize = HudChrome.px(14.0F, u);
         boolean detail = runs.length > 0;
         float y = Math.round(bottom - 2.0F * u - (detail ? detailSize + 1.0F * u : 0.0F) - nameSize);
         float nameW = HudChrome.width(nameFont, nameSize, name);
         HudChrome.text(dl, nameFont, nameSize, Math.round(cx - nameW * 0.5F), y, HudChrome.scaleAlpha(NAME, alpha), name, true);
         if (detail) {
            float detailW = 0.0F;

            for (String run : runs) {
               detailW += HudChrome.width(detailFont, detailSize, run);
            }

            float x = Math.round(cx - detailW * 0.5F);
            float detailY = Math.round(y + nameSize + 1.0F * u);

            for (int i = 0; i < runs.length; i++) {
               HudChrome.text(dl, detailFont, detailSize, Math.round(x), detailY, HudChrome.scaleAlpha(colors[i], alpha), runs[i], true);
               x += HudChrome.width(detailFont, detailSize, runs[i]);
            }
         }
      }
   }
}
