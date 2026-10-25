package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class CustomGlintModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   public static volatile CustomGlintModule active;
   private static final String COLOR_CUSTOM = "Custom";
   private static final String COLOR_ACCENT = "Accent";
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", "Custom", "Custom", "Accent"));
   private final ColorSetting itemColor = this.addSetting("item-color", new ColorSetting("Item Color", -9524994));
   private final BooleanSetting separateArmor = this.addSetting("separate-armor", new BooleanSetting("Separate Armor", false));
   private final ColorSetting armorColor = this.addSetting("armor-color", new ColorSetting("Armor Color", -3697942));
   private final NumberSetting intensity = this.addSetting("intensity", new NumberSetting("Intensity", 1.0F, 0.05F, 1.0F).step(0.05F));
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 1.0F, 0.1F, 4.0F).step(0.1F));
   private final NumberSetting patternSize = this.addSetting("pattern-size", new NumberSetting("Pattern Size", 1.0F, 0.25F, 4.0F).step(0.25F));
   private final CustomGlintModule.GlintTexture items = new CustomGlintModule.GlintTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
   private final CustomGlintModule.GlintTexture armor = new CustomGlintModule.GlintTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR);
   private double glintClock = Double.NaN;
   private long glintClockReal;

   public CustomGlintModule() {
      super("custom-glint", "Custom Glint", "Recolours the enchantment glint and changes its speed and pattern size.", ModuleCategory.VISUALS);
      this.itemColor.visibleWhen(() -> this.colorMode.is("Custom"));
      this.separateArmor.visibleWhen(() -> this.colorMode.is("Custom"));
      this.armorColor.visibleWhen(() -> this.colorMode.is("Custom") && this.separateArmor.get());
   }

   @Override
   protected void onEnable() {
      this.glintClock = Double.NaN;
      active = this;
      this.on(TickEvent.TYPE, event -> this.refresh());
   }

   @Override
   protected void onDisable() {
      active = null;
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         this.restore();
      } else {
         mc.execute(() -> {
            if (active == null) {
               this.restore();
            }
         });
      }
   }

   public long glintMillis(long realMillis) {
      if (Double.isNaN(this.glintClock)) {
         this.glintClock = realMillis;
      } else {
         this.glintClock = this.glintClock + (double)Math.max(0L, realMillis - this.glintClockReal) * this.speed.get();
      }

      this.glintClockReal = realMillis;
      return (long)this.glintClock;
   }

   public float uvScaleMultiplier() {
      return 1.0F / this.patternSize.get();
   }

   private void refresh() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.gui.overlay() == null && RenderSystem.tryGetDevice() != null) {
         int itemRgb = this.colorMode.is("Accent") ? CosmeticAccent.argb() & 16777215 : this.itemColor.rgb();
         int armorRgb = this.colorMode.is("Custom") && this.separateArmor.get() ? this.armorColor.rgb() : itemRgb;
         float strength = this.intensity.get();
         this.items.tint(mc, itemRgb, strength);
         this.armor.tint(mc, armorRgb, strength);
      }
   }

   private void restore() {
      Minecraft mc = Minecraft.getInstance();
      this.items.restore(mc);
      this.armor.restore(mc);
   }

   @Environment(EnvType.CLIENT)
   private static final class GlintTexture {
      private final Identifier id;
      private int width;
      private int height;
      private int[] original;
      private int[] pattern;
      private GpuTexture written;
      private long writtenKey;
      private GpuTexture unusable;

      GlintTexture(Identifier id) {
         this.id = id;
      }

      void tint(Minecraft mc, int rgb, float intensity) {
         GpuTexture gpu = this.gpuTexture(mc);
         if (gpu != null && !gpu.isClosed() && gpu != this.unusable) {
            long key = (long)(rgb & 16777215) << 8 | Math.round(intensity * 255.0F);
            if (gpu != this.written || key != this.writtenKey) {
               if (gpu != this.written) {
                  this.written = null;
                  if ((gpu.usage() & 1) == 0 || !this.readShipped(mc, gpu)) {
                     this.unusable = gpu;
                     return;
                  }
               }

               if (!this.upload(gpu, this.tinted(rgb, intensity))) {
                  this.unusable = gpu;
               } else {
                  this.written = gpu;
                  this.writtenKey = key;
               }
            }
         }
      }

      void restore(Minecraft mc) {
         GpuTexture gpu = this.written != null ? this.gpuTexture(mc) : null;
         if (gpu != null && gpu == this.written && !gpu.isClosed() && this.original != null) {
            this.upload(gpu, this.original);
         }

         this.written = null;
         this.unusable = null;
         this.original = null;
         this.pattern = null;
      }

      private GpuTexture gpuTexture(Minecraft mc) {
         try {
            AbstractTexture texture = mc.getTextureManager().getTexture(this.id);
            return texture == null ? null : texture.getTexture();
         } catch (IllegalStateException var3) {
            return null;
         }
      }

      private boolean readShipped(Minecraft mc, GpuTexture gpu) {
         Optional<Resource> resource = mc.getResourceManager().getResource(this.id);
         if (resource.isEmpty()) {
            return false;
         } else {
            try {
               boolean var19;
               try (
                  InputStream in = resource.get().open();
                  NativeImage image = NativeImage.read(in);
               ) {
                  int w = image.getWidth();
                  int h = image.getHeight();
                  if (w == gpu.getWidth(0) && h == gpu.getHeight(0)) {
                     int[] pixels = new int[w * h];
                     int[] luma = new int[w * h];
                     int y = 0;

                     for (int i = 0; y < h; y++) {
                        for (int x = 0; x < w; i++) {
                           int argb = image.getPixel(x, y);
                           pixels[i] = argb;
                           luma[i] = Math.max(ARGB.red(argb), Math.max(ARGB.green(argb), ARGB.blue(argb)));
                           x++;
                        }
                     }

                     this.width = w;
                     this.height = h;
                     this.original = pixels;
                     this.pattern = luma;
                     return true;
                  }

                  var19 = false;
               }

               return var19;
            } catch (RuntimeException | IOException var18) {
               CustomGlintModule.LOGGER.warn("[Spectre] Custom Glint could not read {}", this.id, var18);
               return false;
            }
         }
      }

      private int[] tinted(int rgb, float intensity) {
         float r = ARGB.red(rgb) * intensity / 255.0F;
         float g = ARGB.green(rgb) * intensity / 255.0F;
         float b = ARGB.blue(rgb) * intensity / 255.0F;
         int[] out = new int[this.pattern.length];

         for (int i = 0; i < out.length; i++) {
            int l = this.pattern[i];
            out[i] = ARGB.color(ARGB.alpha(this.original[i]), Math.round(l * r), Math.round(l * g), Math.round(l * b));
         }

         return out;
      }

      private boolean upload(GpuTexture gpu, int[] argb) {
         try {
            int y;
            try (NativeImage image = new NativeImage(this.width, this.height, false)) {
               y = 0;

               for (int i = 0; y < this.height; y++) {
                  for (int x = 0; x < this.width; i++) {
                     image.setPixel(x, y, argb[i]);
                     x++;
                  }
               }

               RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpu, image);
               y = 1;
            }

            return y != 0;
         } catch (RuntimeException var9) {
            CustomGlintModule.LOGGER.warn("[Spectre] Custom Glint could not write {}", this.id, var9);
            return false;
         }
      }
   }
}
