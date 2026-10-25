package net.spectre.client.module.impl.misc;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class HomeMetaModule extends ClientModule {
   private static final double TELEPORT_DISTANCE_SQ = 25.0;
   private final NumberSetting homeNumber = this.addSetting("home-number", new NumberSetting("Home Number", 3.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (s)", 0.5F, 0.0F, 10.0F).step(0.5F));
   private final NumberSetting rtpTimeout = this.addSetting("rtp-max-timeout", new NumberSetting("RTP Timeout (s)", 10.0F, 1.0F, 30.0F).step(1.0F));
   private HomeMetaModule.Step step;
   private long nextAtMs;
   private long rtpDeadlineMs;
   private Vec3 rtpStart;
   private int home;

   public HomeMetaModule() {
      super("home-meta", "Home Meta", "Re-sets a home, RTPs away, then teleports back home.", ModuleCategory.BASE_RAID);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      if (Minecraft.getInstance().player == null) {
         this.requestDisable();
      } else {
         this.home = this.homeNumber.getInt();
         this.step = HomeMetaModule.Step.DELHOME;
         this.nextAtMs = 0L;
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.getConnection() != null) {
         long now = System.currentTimeMillis();
         if (now >= this.nextAtMs) {
            long delayMs = (long)(this.delay.get() * 1000.0);
            switch (this.step) {
               case DELHOME:
                  command(mc, "delhome " + this.home);
                  this.step = HomeMetaModule.Step.SETHOME;
                  this.nextAtMs = now + delayMs;
                  break;
               case SETHOME:
                  command(mc, "sethome " + this.home);
                  this.step = HomeMetaModule.Step.RTP;
                  this.nextAtMs = now + delayMs;
                  break;
               case RTP:
                  this.rtpStart = mc.player.position();
                  command(mc, "rtp");
                  this.rtpDeadlineMs = now + (long)(this.rtpTimeout.get() * 1000.0);
                  this.step = HomeMetaModule.Step.WAIT_TELEPORT;
                  break;
               case WAIT_TELEPORT:
                  boolean moved = mc.player.position().distanceToSqr(this.rtpStart) > 25.0;
                  if (moved || now >= this.rtpDeadlineMs) {
                     this.step = HomeMetaModule.Step.HOME;
                     this.nextAtMs = now + delayMs;
                  }
                  break;
               case HOME:
                  command(mc, "home " + this.home);
                  this.requestDisable();
            }
         }
      } else {
         this.requestDisable();
      }
   }

   private static void command(Minecraft mc, String command) {
      mc.getConnection().sendCommand(command);
   }

   @Environment(EnvType.CLIENT)
   private static enum Step {
      DELHOME,
      SETHOME,
      RTP,
      WAIT_TELEPORT,
      HOME;
   }
}
