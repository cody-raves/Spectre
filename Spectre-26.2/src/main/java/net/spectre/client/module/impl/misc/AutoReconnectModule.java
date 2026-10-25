package net.spectre.client.module.impl.misc;

import java.util.Locale;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class AutoReconnectModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 5.0F, 3.0F, 60.0F).step(1.0F));
   private ServerData lastServer;
   private Screen activeScreen;
   private Button reconnectButton;
   private int ticksLeft;

   public AutoReconnectModule() {
      super("auto-reconnect", "Auto Reconnect", "Reconnects to the last server after an unexpected disconnect.", ModuleCategory.MISC);
      TickEvent.TYPE.listen(this, this::trackServer);
      ScreenEvents.AFTER_INIT.register(this::onScreenInit);
   }

   private void trackServer(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         ServerData server = mc.isLocalServer() ? null : mc.getCurrentServer();
         this.lastServer = server != null && !server.isLan() && !server.isRealm() ? server : null;
      }
   }

   private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
      if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) {
         this.lastServer = null;
      } else if (screen instanceof DisconnectedScreen && this.lastServer != null) {
         ServerData server = this.lastServer;
         boolean auto = this.isEnabled();
         if (screen == this.activeScreen) {
            Screens.getWidgets(screen).remove(this.reconnectButton);
         } else {
            this.activeScreen = screen;
            this.ticksLeft = Math.round(this.delay.get() * 20.0F);
         }

         Button button = Button.builder(reconnectLabel(auto, this.ticksLeft), btn -> {
            btn.active = false;
            this.reconnect(client, server);
         }).bounds(scaledWidth - 150 - 10, scaledHeight - 20 - 10, 150, 20).build();
         this.reconnectButton = button;
         Screens.getWidgets(screen).add(button);
         if (auto) {
            ScreenEvents.afterTick(screen).register((AfterTick)s -> {
               this.ticksLeft--;
               if (this.ticksLeft <= 0) {
                  button.active = false;
                  this.reconnect(client, server);
               } else {
                  button.setMessage(reconnectLabel(true, this.ticksLeft));
               }
            });
         }
      }
   }

   private void reconnect(Minecraft client, ServerData server) {
      ServerAddress address = ServerAddress.parseString(server.ip);
      ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, address, server, false, null);
   }

   private static Component reconnectLabel(boolean auto, int ticksLeft) {
      return auto ? Component.literal(String.format(Locale.ROOT, "Reconnect (%.1f)", ticksLeft / 20.0)) : Component.literal("Reconnect");
   }
}
