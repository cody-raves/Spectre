package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.impl.client.RegionMapModule;
import net.spectre.client.module.setting.ColorSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class RegionMapRenderer {
   private static final float PAD = 10.0F;
   private static final float INFO_GAP = 8.0F;
   private static final float INFO_LINE_H = 22.0F;
   private static final float LEGEND_GAP = 4.0F;
   private static final float LEGEND_ROW_H = 16.0F;
   private static final float LEGEND_SWATCH = 9.0F;
   private static final float LABEL_GAP = 8.0F;
   private static final String MINUS = "−";
   private static final String COORDINATE_LABEL = "Coordinate";
   private static final String REGION_LABEL = "Region";

   private RegionMapRenderer() {
   }

   public static RegionMapRenderer.Size measureSize(RegionMapModule module, float scale) {
      float gridSize = gridSize(module, scale);
      float pad = 10.0F * scale;
      float contentW = Math.max(gridSize, measureInfoWidth(module, scale));
      return new RegionMapRenderer.Size(Math.round(contentW + pad * 2.0F), Math.round(gridSize + pad * 2.0F + 8.0F * scale + measureInfoHeight(module) * scale));
   }

   private static float gridSize(RegionMapModule module, float scale) {
      float cellSize = module.cellSize() * scale;
      float cellGap = module.cellGap() * scale;
      return 9.0F * (cellSize + cellGap) - cellGap;
   }

   private static float measureInfoWidth(RegionMapModule module, float scale) {
      ImGuiManager m = ImGuiManager.getInstance();
      int textPx = HudChrome.px(17.0F, scale);
      int labelPx = HudChrome.px(14.0F, scale);
      float labelCol = labelColumn(module, m.hudLabelFont(), labelPx) + 8.0F * scale;
      float values = module.showCoordinates() ? HudChrome.tabularWidth(m.hudTextFont(), textPx, "−000000, −000000") : 0.0F;
      float legend = 0.0F;

      for (String name : DonutRegionMapData.regionTypeNames()) {
         values = Math.max(values, HudChrome.width(m.hudTextFont(), textPx, "000  ·  " + name));
         legend = Math.max(legend, 15.0F * scale + HudChrome.width(m.hudLabelFont(), labelPx, name));
      }

      values = Math.max(values, HudChrome.width(m.hudTextFont(), textPx, "000  ·  Unknown"));
      return (float)Math.ceil(Math.max(labelCol + values, module.showLegend() ? legend : 0.0F));
   }

   public static void render(ImDrawList dl, RegionMapModule module, HudLayoutStore.HudElementState layout) {
      render(dl, module, layout, 1.0F);
   }

   public static void render(ImDrawList dl, RegionMapModule module, HudLayoutStore.HudElementState layout, float fade) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && dl != null && module != null && layout != null) {
         float scale = HudRenderer.scaleOf(layout);
         float cellSize = module.cellSize() * scale;
         float cellGap = module.cellGap() * scale;
         float pad = 10.0F * scale;
         int mapSize = 9;
         float gridSize = gridSize(module, scale);
         float x = Math.round(layout.x);
         float y = Math.round(layout.y);
         float r = HudRenderer.hudRounding() * scale;
         float alpha = module.opacity() * fade;
         HudChrome.card(dl, HudRenderer.hudSurface(layout), x, y, layout.width, layout.height, r, alpha, module.shadow(), scale);
         float gridX = Math.round(x + (layout.width - gridSize) * 0.5F);
         float gridY = y + pad;
         float cellR = Math.min(Math.max(2.0F * scale, r - pad), cellSize * 0.3F);

         for (int row = 0; row < mapSize; row++) {
            for (int col = 0; col < mapSize; col++) {
               DonutRegionMapData.RegionInfo info = DonutRegionMapData.regionInfo(row * mapSize + col);
               if (info != null) {
                  float cx = gridX + col * (cellSize + cellGap);
                  float cy = gridY + row * (cellSize + cellGap);
                  int color = HudChrome.scaleAlpha(DonutRegionMapData.regionColor(info.regionType), alpha);
                  dl.addRectFilled(cx, cy, cx + cellSize, cy + cellSize, color, cellR);
                  if (module.showRegionIds()) {
                     drawCenteredCellText(dl, info.regionId, cx, cy, cellSize, module.textScale(), alpha, scale);
                  }
               }
            }
         }

         renderPlayerPin(dl, module, player, gridX, gridY, cellSize, cellGap, alpha, scale);
         renderInfoPanel(dl, module, player, x + pad, gridY + gridSize + 8.0F * scale, alpha, scale);
      }
   }

   private static void drawCenteredCellText(ImDrawList dl, int regionId, float cx, float cy, float size, float textScale, float alpha, float scale) {
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      int fontPx = Math.max(8, HudChrome.px(14.0F * textScale * 0.85F, scale));
      String label = String.valueOf(regionId);
      float tx = cx + (size - HudChrome.width(font, fontPx, label)) * 0.5F;
      float ty = cy + (size - fontPx) * 0.5F;
      HudChrome.text(dl, font, fontPx, tx, ty, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, alpha), label, true);
   }

   private static void renderPlayerPin(
      ImDrawList dl, RegionMapModule module, LocalPlayer player, float gridX, float gridY, float cellSize, float cellGap, float alpha, float scale
   ) {
      double px = player.getX();
      double pz = player.getZ();
      int[] grid = DonutRegionMapData.worldToGrid(px, pz);
      if (grid[0] >= 0 && grid[0] < 9 && grid[1] >= 0 && grid[1] < 9) {
         double[] cellPos = DonutRegionMapData.worldToCellPosition(px, pz);
         float centerX = gridX + grid[0] * (cellSize + cellGap) + (float)(cellPos[0] * cellSize);
         float centerY = gridY + grid[1] * (cellSize + cellGap) + (float)(cellPos[1] * cellSize);
         double angle = Math.toRadians(-player.getYRot() - 90.0);
         float arrowSize = 9.0F * module.playerScale() * scale;
         int color = HudChrome.scaleAlpha(toAbgr(module.playerIndicatorColor()), alpha);
         float tipX = centerX + (float)(Math.cos(angle) * arrowSize);
         float tipY = centerY - (float)(Math.sin(angle) * arrowSize);
         float leftX = centerX + (float)(Math.cos(angle + Math.toRadians(135.0)) * arrowSize);
         float leftY = centerY - (float)(Math.sin(angle + Math.toRadians(135.0)) * arrowSize);
         float rightX = centerX + (float)(Math.cos(angle - Math.toRadians(135.0)) * arrowSize);
         float rightY = centerY - (float)(Math.sin(angle - Math.toRadians(135.0)) * arrowSize);
         dl.addTriangleFilled(tipX, tipY, leftX, leftY, rightX, rightY, color);
         dl.addTriangle(tipX, tipY, leftX, leftY, rightX, rightY, HudChrome.scaleAlpha(-1728053248, alpha), 1.0F);
      }
   }

   private static void renderInfoPanel(ImDrawList dl, RegionMapModule module, LocalPlayer player, float x, float y, float alpha, float scale) {
      ImGuiManager m = ImGuiManager.getInstance();
      ImFont textFont = m.hudTextFont();
      ImFont labelFont = m.hudLabelFont();
      int textPx = HudChrome.px(17.0F, scale);
      int labelPx = HudChrome.px(14.0F, scale);
      float labelOffset = HudChrome.baselineOffset(textFont, textPx, labelFont, labelPx);
      float valueX = x + labelColumn(module, labelFont, labelPx) + 8.0F * scale;
      float lineH = 22.0F * scale;
      float textInset = (float)Math.floor((lineH - textPx) * 0.5F);
      float lineTop = y + textInset;
      if (module.showCoordinates()) {
         HudChrome.text(dl, labelFont, labelPx, x, lineTop + labelOffset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, alpha), "Coordinate", false);
         HudChrome.tabular(
            dl,
            textFont,
            textPx,
            valueX,
            lineTop,
            HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, alpha),
            number((long)Math.floor(player.getX())) + ", " + number((long)Math.floor(player.getZ())),
            false
         );
         lineTop += lineH;
      }

      int regionId = DonutRegionMapData.regionAt(player.getX(), player.getZ());
      String regionName = DonutRegionMapData.regionTypeName(player.getX(), player.getZ());
      HudChrome.text(dl, labelFont, labelPx, x, lineTop + labelOffset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, alpha), "Region", false);
      HudChrome.text(dl, textFont, textPx, valueX, lineTop, HudChrome.scaleAlpha(HudChrome.TEXT_NAME, alpha), regionId + "  ·  " + regionName, false);
      if (module.showLegend()) {
         float legendY = y + lineH * infoLines(module) + 4.0F * scale;
         float rowH = 16.0F * scale;
         float swatch = 9.0F * scale;
         String[] names = DonutRegionMapData.regionTypeNames();
         int[] colors = DonutRegionMapData.regionTypeColors();

         for (int i = 0; i < names.length; i++) {
            float rowY = legendY + i * rowH;
            float swatchY = rowY + (rowH - swatch) * 0.5F;
            dl.addRectFilled(x, swatchY, x + swatch, swatchY + swatch, HudChrome.scaleAlpha(colors[i], alpha), 2.0F * scale);
            HudChrome.text(
               dl,
               labelFont,
               labelPx,
               x + swatch + 6.0F * scale,
               rowY + (float)Math.floor((rowH - labelPx) * 0.5F),
               HudChrome.scaleAlpha(HudChrome.TEXT_NAME, alpha),
               names[i],
               false
            );
         }
      }
   }

   private static int infoLines(RegionMapModule module) {
      return module.showCoordinates() ? 2 : 1;
   }

   private static float labelColumn(RegionMapModule module, ImFont labelFont, int labelPx) {
      float region = HudChrome.width(labelFont, labelPx, "Region");
      return module.showCoordinates() ? Math.max(HudChrome.width(labelFont, labelPx, "Coordinate"), region) : region;
   }

   private static float measureInfoHeight(RegionMapModule module) {
      float height = 22.0F * infoLines(module);
      if (module.showLegend()) {
         height += 4.0F + DonutRegionMapData.regionTypeNames().length * 16.0F;
      }

      return height;
   }

   private static String number(long value) {
      return value < 0L ? "−" + -value : Long.toString(value);
   }

   private static int toAbgr(ColorSetting color) {
      return GuiPalette.abgr(color.argb());
   }

   @Environment(EnvType.CLIENT)
   public record Size(float width, float height) {
   }
}
