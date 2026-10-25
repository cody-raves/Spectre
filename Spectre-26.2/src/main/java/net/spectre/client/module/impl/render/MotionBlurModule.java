package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class MotionBlurModule extends ClientModule {
   public static volatile MotionBlurModule active;
   private final NumberSetting strength = this.addSetting("strength", new NumberSetting("Strength", 50.0F, 10.0F, 90.0F).step(5.0F));

   public MotionBlurModule() {
      super("motion-blur", "Motion Blur", "Adds motion blur when you turn.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      if (RenderSystem.isOnRenderThread()) {
         MotionBlurPass.release();
      } else {
         Minecraft.getInstance().execute(MotionBlurModule::releaseIfOff);
      }
   }

   private static void releaseIfOff() {
      if (active == null) {
         MotionBlurPass.release();
      }
   }

   public static void afterWorld(boolean worldRendered) {
      MotionBlurModule module = active;
      if (module != null) {
         MotionBlurPass.apply(worldRendered, module.strength.get() / 100.0F);
      }
   }
}
