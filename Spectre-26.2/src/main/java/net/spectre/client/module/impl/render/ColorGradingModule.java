package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class ColorGradingModule extends ClientModule {
   public static volatile ColorGradingModule active;
   private final NumberSetting vibrance = this.addSetting("vibrance", new NumberSetting("Vibrance", 35.0F, 0.0F, 100.0F).step(1.0F));
   private final NumberSetting contrast = this.addSetting("contrast", new NumberSetting("Contrast", 8.0F, -20.0F, 40.0F).step(1.0F));
   private final NumberSetting brightness = this.addSetting("brightness", new NumberSetting("Brightness", 3.0F, -20.0F, 30.0F).step(1.0F));
   private final NumberSetting warmth = this.addSetting("warmth", new NumberSetting("Warmth", 15.0F, -100.0F, 100.0F).step(1.0F));
   private final NumberSetting vignette = this.addSetting("vignette", new NumberSetting("Vignette", 30.0F, 0.0F, 100.0F).step(1.0F));

   public ColorGradingModule() {
      super("color-grading", "Color Grading", "Adds color, contrast and a vignette to the world.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      if (RenderSystem.isOnRenderThread()) {
         ColorGradingPass.release();
      } else {
         Minecraft.getInstance().execute(ColorGradingModule::releaseIfOff);
      }
   }

   private static void releaseIfOff() {
      if (active == null) {
         ColorGradingPass.release();
      }
   }

   public static void afterWorld(boolean worldRendered) {
      ColorGradingModule module = active;
      if (module != null) {
         ColorGradingPass.apply(
            worldRendered,
            module.vibrance.get() / 100.0F,
            1.0F + module.contrast.get() / 100.0F,
            1.0F + module.brightness.get() / 100.0F,
            module.warmth.get() / 100.0F,
            module.vignette.get() / 100.0F
         );
      }
   }
}
