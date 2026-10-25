package net.spectre.client.module.impl.combat;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class WTapRecodeModule extends ClientModule {
   private final ModeSetting tapMode = this.addSetting("mode", new ModeSetting("Mode", "W Tap", "W Tap", "S Tap", "Sneak Tap", "Sprint Tap"));
   private final NumberSetting duration = this.addSetting("duration", new NumberSetting("Duration", 180.0F, 25.0F, 1000.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 500.0F, 0.0F, 2500.0F).step(1.0F));
   private final BooleanSetting onlyOnGround = this.addSetting("only-ground", new BooleanSetting("Only Ground", true));
   private long stopTime;
   private long nextTapTime;
   private boolean active;
   private boolean captured;
   private boolean oldForward;
   private boolean oldBack;
   private boolean oldSneak;
   private boolean oldSprintKey;

   public boolean isTapping() {
      return this.isEnabled() && this.active;
   }

   public WTapRecodeModule() {
      super("w-tap-recode", "W Tap Recode", "Timed movement taps after a successful hit.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.active = false;
      this.captured = false;
      this.nextTapTime = 0L;
   }

   @Override
   protected void onDisable() {
      this.restoreKeys();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   public void afterAttack() {
      Minecraft mc = Minecraft.getInstance();
      if (this.isEnabled() && mc.player != null) {
         long now = System.currentTimeMillis();
         if (!this.active && now >= this.nextTapTime) {
            if (!this.onlyOnGround.get() || mc.player.onGround()) {
               this.stopTime = now + Math.max(0L, (long)this.duration.getInt());
               this.nextTapTime = now + Math.max(0L, (long)this.delay.getInt());
               this.active = true;
               this.captured = false;
            }
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (this.active) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            this.active = false;
         } else {
            if (System.currentTimeMillis() <= this.stopTime) {
               this.applyTap(mc);
            } else {
               this.restoreKeys();
               this.active = false;
            }
         }
      }
   }

   private void applyTap(Minecraft mc) {
      this.captureKeys(mc);
      String var2 = this.tapMode.get();
      switch (var2) {
         case "W Tap":
            mc.options.keyUp.setDown(false);
            break;
         case "S Tap":
            mc.options.keyDown.setDown(true);
            break;
         case "Sneak Tap":
            mc.options.keyShift.setDown(true);
            break;
         case "Sprint Tap":
            mc.player.setSprinting(false);
      }
   }

   private void captureKeys(Minecraft mc) {
      if (!this.captured) {
         this.oldForward = mc.options.keyUp.isDown();
         this.oldBack = mc.options.keyDown.isDown();
         this.oldSneak = mc.options.keyShift.isDown();
         this.oldSprintKey = mc.options.keySprint.isDown();
         this.captured = true;
      }
   }

   private void restoreKeys() {
      if (this.captured) {
         Minecraft mc = Minecraft.getInstance();
         setDown(mc.options.keyUp, this.oldForward);
         setDown(mc.options.keyDown, this.oldBack);
         setDown(mc.options.keyShift, this.oldSneak);
         setDown(mc.options.keySprint, this.oldSprintKey);
         this.captured = false;
      }
   }

   private static void setDown(KeyMapping key, boolean down) {
      if (key != null) {
         key.setDown(down);
      }
   }
}
