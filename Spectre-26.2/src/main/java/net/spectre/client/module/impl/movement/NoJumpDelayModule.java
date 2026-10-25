package net.spectre.client.module.impl.movement;

import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class NoJumpDelayModule extends ClientModule {
   public static volatile NoJumpDelayModule active;
   private final NumberSetting cooldown = this.addSetting("cooldown", new NumberSetting("Cooldown", 0.0F, 0.0F, 9.0F).step(1.0F));
   private final NumberSetting variance = this.addSetting("variance", new NumberSetting("Variance", 0.0F, 0.0F, 4.0F).step(1.0F));

   public NoJumpDelayModule() {
      super("no-jump-delay", "No Jump Delay", "Removes the delay between jumps.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public int rearm(int vanillaTicks) {
      int ticks = this.cooldown.getInt();
      int spread = this.variance.getInt();
      if (spread > 0) {
         ticks += ThreadLocalRandom.current().nextInt(spread + 1);
      }

      return Math.min(vanillaTicks, ticks);
   }
}
