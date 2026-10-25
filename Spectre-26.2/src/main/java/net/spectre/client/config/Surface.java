package net.spectre.client.config;

import java.util.List;
import net.spectre.client.module.StableId;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.Setting;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Surface {
   public static final String BG_NONE = "None";
   public static final String BG_IMAGE = "Image";
   public static final String BG_FLOW = "Flow";
   public static final String BG_TOPOGRAPHIC = "Topographic";
   public static final String BG_RINGS = "Rings";
   public static final String BG_GRID = "Grid";
   public static final String MAP_EACH = "Each";
   public static final String MAP_SCREEN = "Screen";
   public static final String COVER_FULL = "Full";
   public static final String COVER_HEADER = "Header";
   public final String label;
   public final boolean card;
   public final ColorSetting color;
   public final NumberSetting opacity;
   public final ModeSetting background = new ModeSetting("Background", "None", "None", "Image", "Flow", "Topographic", "Rings", "Grid");
   public final StringSetting image = new StringSetting("Image", "");
   public final ModeSetting mapping = new ModeSetting("Mapping", "Each", "Each", "Screen");
   public final ModeSetting coverage = new ModeSetting("Coverage", "Header", "Header", "Full");
   public final NumberSetting headerHeight = new NumberSetting("Header height", 120.0F, 70.0F, 220.0F).step(1.0F);
   public final NumberSetting imageOpacity;
   public final NumberSetting imageScale = new NumberSetting("Image scale", 1.0F, 0.5F, 3.0F).step(0.1F);
   public final NumberSetting imageX = new NumberSetting("Image X", 0.5F, 0.0F, 1.0F).step(0.05F);
   public final NumberSetting imageY = new NumberSetting("Image Y", 0.5F, 0.0F, 1.0F).step(0.05F);
   public final NumberSetting lineOpacity = new NumberSetting("Line opacity", 0.5F, 0.1F, 1.0F).step(0.05F);
   private final List<Setting> settings;

   Surface(String key, String label, boolean card, int color, float opacity, float imageOpacity) {
      this.label = label;
      this.card = card;
      this.color = new ColorSetting("Color", color);
      this.opacity = new NumberSetting("Opacity", opacity, 0.2F, 1.0F).step(0.01F);
      this.imageOpacity = new NumberSetting("Image opacity", imageOpacity, 0.05F, 1.0F).step(0.05F);
      this.settings = List.of(
         this.color,
         this.opacity,
         this.background,
         this.image,
         this.mapping,
         this.coverage,
         this.headerHeight,
         this.imageOpacity,
         this.imageScale,
         this.imageX,
         this.imageY,
         this.lineOpacity
      );

      for (Setting setting : this.settings) {
         String name = setting == this.imageY ? "Image focus" : setting.name();
         setting.bindId(key + "." + StableId.legacy(name));
      }

      this.image.visibleWhen(() -> false);
      this.image.privateValue();
      this.mapping.visibleWhen(() -> !card && !this.background.is("None"));
      this.coverage.visibleWhen(() -> card && !this.background.is("None"));
      this.headerHeight.visibleWhen(() -> card && !this.background.is("None") && this.coverage.is("Header"));
      this.imageOpacity.visibleWhen(this::isImage);
      this.imageScale.visibleWhen(this::isImage);
      this.imageX.visibleWhen(this::isImage);
      this.imageY.visibleWhen(this::isImage);
      this.lineOpacity.visibleWhen(this::isPattern);
   }

   public boolean isImage() {
      return this.background.is("Image");
   }

   public boolean isPattern() {
      return !this.background.is("None") && !this.background.is("Image");
   }

   public boolean isBanner() {
      return this.card && this.coverage.is("Header");
   }

   public boolean isScreenMapped() {
      return !this.card && this.mapping.is("Screen");
   }

   public List<Setting> settings() {
      return this.settings;
   }
}
