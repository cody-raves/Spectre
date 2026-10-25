package net.spectre.client.module.impl.misc;

import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.LevelUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AutoRelogModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int REARM_HEIGHT = 4;
   private static final int REARM_TICKS = 40;
   private final NumberSetting yLevel = this.addSetting("y-level", new NumberSetting("Y Level", 0.0F, -60.0F, 16.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 3.0F, 1.0F, 30.0F).step(0.5F));
   private ClientLevel seenLevel;
   private LocalPlayer seenPlayer;
   private int aboveTicks;
   private ServerData rejoin;
   private boolean disconnected;
   private int rejoinTicks;

   public AutoRelogModule() {
      super("auto-relog", "Auto Relog", "Relogs once when you go down to deepslate level.", ModuleCategory.DONUT);
   }

   @Override
   protected void onEnable() {
      this.forget();
      this.on(TickEvent.TYPE, event -> this.onTick());
   }

   @Override
   protected void onDisable() {
      this.forget();
   }

   private void forget() {
      this.seenLevel = null;
      this.seenPlayer = null;
      this.aboveTicks = 0;
      this.rejoin = null;
      this.disconnected = false;
   }

   private void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (this.rejoin != null) {
         this.waitToRejoin(mc);
      } else {
         LocalPlayer player = mc.player;
         ClientLevel level = mc.level;
         if (player != null && level != null && !(mc.gui.screen() instanceof LevelLoadingScreen)) {
            if (level != this.seenLevel || player != this.seenPlayer) {
               this.seenLevel = level;
               this.seenPlayer = player;
               this.aboveTicks = 0;
            }

            if (!LevelUtil.isOverworld(level)) {
               this.aboveTicks = 0;
            } else {
               double y = player.getY();
               float line = this.yLevel.get();
               if (y >= line + 4.0F) {
                  if (this.aboveTicks < 40) {
                     this.aboveTicks++;
                  }
               } else if (y < line) {
                  boolean armed = this.aboveTicks >= 40;
                  this.aboveTicks = 0;
                  if (armed) {
                     this.relog(mc, y);
                  }
               }
            }
         } else {
            this.aboveTicks = 0;
         }
      }
   }

   private void relog(Minecraft mc, double y) {
      ServerData server = mc.isLocalServer() ? null : mc.getCurrentServer();
      if (server != null && !server.isRealm()) {
         LOGGER.info("[Spectre] Auto Relog: reached Y {}, relogging", (int)Math.floor(y));
         this.rejoin = server;
         this.disconnected = false;
         this.rejoinTicks = Math.round(this.delay.get() * 20.0F);
         mc.schedule(() -> {
            if (this.rejoin == server && mc.level != null) {
               mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
               this.disconnected = true;
            }
         });
      }
   }

   private void waitToRejoin(Minecraft mc) {
      if (this.disconnected) {
         if (mc.level == null && mc.gui.screen() instanceof JoinMultiplayerScreen) {
            if (--this.rejoinTicks <= 0) {
               ServerData server = this.rejoin;
               Screen serverList = mc.gui.screen();
               this.rejoin = null;
               mc.schedule(() -> {
                  if (mc.gui.screen() == serverList && mc.level == null) {
                     ConnectScreen.startConnecting(serverList, mc, ServerAddress.parseString(server.ip), server, false, null);
                  }
               });
            }
         } else {
            this.rejoin = null;
         }
      }
   }
}
