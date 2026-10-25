package net.spectre.client.module.impl.client;

import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RegionMapModule extends ClientModule {
   private final NumberSetting cellSize = this.addSetting("cell-size", new NumberSetting("Cell Size", 22.0F, 10.0F, 50.0F).step(1.0F));
   private final NumberSetting cellGap = this.addSetting("cell-gap", new NumberSetting("Cell Gap", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting playerScale = this.addSetting("player-scale", new NumberSetting("Player Scale", 1.0F, 0.5F, 3.0F).step(0.1F));
   private final NumberSetting textScale = this.addSetting("text-scale", new NumberSetting("Text Scale", 1.0F, 0.1F, 2.0F).step(0.1F));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 1.0F, 0.1F, 1.0F).step(0.05F));
   private final BooleanSetting shadow = this.addSetting("shadow", new BooleanSetting("Shadow", true));
   private final BooleanSetting showRegionIds = this.addSetting("show-region-ids", new BooleanSetting("Show Region IDs", true));
   private final BooleanSetting showLegend = this.addSetting("show-legend", new BooleanSetting("Show Legend", true));
   private final BooleanSetting showCoordinates = this.addSetting("show-coordinates", new BooleanSetting("Show Coordinates", true));
   private final ColorSetting playerIndicatorColor = this.addSetting(new ColorSetting("Player Indicator", -52686));

   public RegionMapModule() {
      super("region-map", "Region Map", "DonutSMP region map with directional indicator.", ModuleCategory.CLIENT);
   }

   public float cellSize() {
      return this.cellSize.get();
   }

   public float cellGap() {
      return this.cellGap.get();
   }

   public float playerScale() {
      return this.playerScale.get();
   }

   public float textScale() {
      return this.textScale.get();
   }

   public float opacity() {
      return this.opacity.get();
   }

   public boolean shadow() {
      return this.shadow.get();
   }

   public boolean showRegionIds() {
      return this.showRegionIds.get();
   }

   public boolean showLegend() {
      return this.showLegend.get();
   }

   public boolean showCoordinates() {
      return this.showCoordinates.get();
   }

   public ColorSetting playerIndicatorColor() {
      return this.playerIndicatorColor;
   }
}
