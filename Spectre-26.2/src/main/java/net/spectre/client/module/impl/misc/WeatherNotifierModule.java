package net.spectre.client.module.impl.misc;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class WeatherNotifierModule extends ClientModule {
   private static final int STABLE_TICKS = 20;
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private ClientLevel level;
   private boolean raining;
   private int changedTicks;

   public WeatherNotifierModule() {
      super("weather-notifier", "Weather Notifier", "Shows an advancement-style toast when it starts raining.", ModuleCategory.MISC);
   }

   @Override
   protected void onEnable() {
      this.level = null;
      this.raining = false;
      this.changedTicks = 0;
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.level = null;
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel current = mc.level;
      if (current != this.level) {
         this.level = current;
         this.raining = false;
         this.changedTicks = 0;
      }

      if (current != null && !(mc.gui.screen() instanceof LevelLoadingScreen)) {
         if (current.isRaining() == this.raining) {
            this.changedTicks = 0;
         } else if (++this.changedTicks >= 20) {
            this.raining = !this.raining;
            this.changedTicks = 0;
            if (this.raining) {
               mc.gui.toastManager().addToast(new WeatherNotifierModule.WeatherToast(this.sound.get()));
            }
         }
      } else {
         this.changedTicks = 0;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class WeatherToast extends ChallengeToast {
      private static final Component TITLE = Component.literal("Weather Changed!");
      private static final Component MESSAGE = Component.literal("It started raining.");

      WeatherToast(boolean sound) {
         super(new ItemStack(Items.TRIDENT), TITLE, MESSAGE, sound);
      }
   }
}
