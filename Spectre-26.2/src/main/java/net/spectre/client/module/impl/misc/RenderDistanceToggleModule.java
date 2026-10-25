package net.spectre.client.module.impl.misc;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class RenderDistanceToggleModule extends ClientModule {
   private static final int RESTORE_AFTER_TICKS = 10;
   private static final int REARM_MARGIN = 2;
   private final NumberSetting lowDistance = this.addSetting("low-distance", new NumberSetting("Low Distance", 2.0F, 2.0F, 32.0F).step(1.0F));
   private final NumberSetting resetY = this.addSetting("reset-y", new NumberSetting("Reset Y", -1.0F, -64.0F, 320.0F).step(1.0F));
   private boolean armed = true;
   private boolean restoring;
   private int timer;
   private int savedDistance = -1;

   public RenderDistanceToggleModule() {
      super("render-distance-toggle", "Render Distance Toggle", "Reloads chunks when you go below a Y level.", ModuleCategory.BASE_RAID);
   }

   @Override
   protected void onEnable() {
      this.armed = true;
      this.restoring = false;
      this.timer = 0;
      this.savedDistance = -1;
   }

   @Override
   protected void onDisable() {
      if (this.restoring) {
         this.restore(Minecraft.getInstance());
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         int y = mc.player.getBlockY();
         if (!this.restoring && y > this.resetY.getInt() + 2) {
            this.armed = true;
         }

         if (this.armed && y <= this.resetY.getInt()) {
            this.armed = false;
            this.restoring = true;
            this.timer = 0;
            this.savedDistance = mc.options.renderDistance().get();
            apply(mc, this.lowDistance.getInt());
         } else if (this.restoring && ++this.timer >= 10) {
            this.restore(mc);
         }
      } else {
         if (this.restoring) {
            this.restore(mc);
         }
      }
   }

   private void restore(Minecraft mc) {
      if (this.savedDistance >= 0) {
         apply(mc, this.savedDistance);
      }

      this.restoring = false;
   }

   private static void apply(Minecraft mc, int chunks) {
      mc.options.renderDistance().set(chunks);
      mc.options.broadcastOptions();
   }
}
