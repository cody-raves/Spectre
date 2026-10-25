package net.spectre.client.module.impl.movement;

import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TridentBoostModule extends ClientModule {
   public static volatile TridentBoostModule active;
   private final NumberSetting boost = this.addSetting("boost", new NumberSetting("Boost", 2.0F, 0.1F, 10.0F).step(0.1F));
   private final BooleanSetting outOfWater = this.addSetting("out-of-water", new BooleanSetting("Out Of Water", true));

   public TridentBoostModule() {
      super("trident-boost", "Trident Boost", "Boosts you when using riptide with a trident.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public static double riptideMultiplier() {
      TridentBoostModule module = active;
      return module != null ? module.boost.get() : 1.0;
   }

   public static boolean allowOutOfWater() {
      TridentBoostModule module = active;
      return module != null && module.outOfWater.get();
   }
}
