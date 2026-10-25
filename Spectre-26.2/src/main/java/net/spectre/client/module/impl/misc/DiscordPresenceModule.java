package net.spectre.client.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.spectre.client.discord.DiscordIpc;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class DiscordPresenceModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final String APPLICATION_ID = "1553391775840866314";
   private static final String LOGO_ASSET = "logo";
   private static final String LOGO_TEXT = "Spectre Client";
   private static final String BUTTON_LABEL = "Discord";
   private static final String IN_MENU = "In Main Menu";
   private static final String SINGLEPLAYER = "Singleplayer";
   private static final String MULTIPLAYER = "Multiplayer";
   private static final String LAN_WORLD = "LAN World";
   private static final String REALMS = "Realms";
   private static final long GAME_STARTED = ProcessHandle.current().info().startInstant().orElseGet(Instant::now).getEpochSecond();
   private static final long RETRY_MIN_MS = 5000L;
   private static final long RETRY_MAX_MS = 30000L;
   private static final long IDLE_POLL_MS = 1000L;
   private static final long MIN_UPDATE_GAP_MS = 4000L;
   private static final long KEEP_ALIVE_MS = 60000L;
   private final BooleanSetting showServerIp = this.addSetting("show-server-ip", new BooleanSetting("Show Server IP", true));
   private final BooleanSetting discordButton = this.addSetting("discord-button", new BooleanSetting("Discord Button", true));
   private volatile DiscordPresenceModule.Card card;
   private DiscordPresenceModule.Link link;
   private Connection session;
   private long joinedAt;
   private boolean loggedIdle;

   public DiscordPresenceModule() {
      super("discord-presence", "Discord Presence", "Shows Spectre Client and the server you are on in your Discord status.", ModuleCategory.CLIENT);
   }

   @Override
   public boolean enabledByDefault() {
      return true;
   }

   @Override
   protected void onEnable() {
      if ("1553391775840866314".isBlank()) {
         if (!this.loggedIdle) {
            this.loggedIdle = true;
            LOGGER.info("[Spectre] Discord Presence: no Discord application id set (DiscordPresenceModule.APPLICATION_ID); idle");
         }
      } else {
         this.session = null;
         this.card = new DiscordPresenceModule.Card("In Main Menu", GAME_STARTED, this.discordButton.get());
         this.link = new DiscordPresenceModule.Link(this.link);
         this.refresh();
         this.on(TickEvent.TYPE, event -> this.refresh());
         this.link.start();
      }
   }

   @Override
   protected void onDisable() {
      if (this.link != null) {
         this.link.stop();
      }

      this.session = null;
   }

   private void refresh() {
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener listener = mc.getConnection();
      String details;
      long startedAt;
      if (listener != null) {
         if (listener.getConnection() != this.session) {
            this.session = listener.getConnection();
            this.joinedAt = System.currentTimeMillis() / 1000L;
         }

         details = this.place(mc, listener.getServerData());
         startedAt = this.joinedAt;
      } else {
         if (this.session != null && this.session.isConnected() && this.session.getPacketListener() instanceof ClientConfigurationPacketListener) {
            return;
         }

         this.session = null;
         details = "In Main Menu";
         startedAt = GAME_STARTED;
      }

      boolean button = this.discordButton.get();
      DiscordPresenceModule.Card shown = this.card;
      if (startedAt != shown.startedAt() || button != shown.discordButton() || !details.equals(shown.details())) {
         this.card = new DiscordPresenceModule.Card(details, startedAt, button);
         this.link.wake();
      }
   }

   private String place(Minecraft mc, ServerData server) {
      if (mc.isLocalServer()) {
         return "Singleplayer";
      } else if (server == null) {
         return "Multiplayer";
      } else if (server.isRealm()) {
         return "Realms";
      } else if (server.isLan()) {
         return "LAN World";
      } else {
         return this.showServerIp.get() ? server.ip : "Multiplayer";
      }
   }

   private static JsonObject activity(DiscordPresenceModule.Card card) {
      JsonObject activity = new JsonObject();
      activity.addProperty("details", card.details());
      JsonObject timestamps = new JsonObject();
      timestamps.addProperty("start", card.startedAt());
      activity.add("timestamps", timestamps);
      JsonObject assets = new JsonObject();
      assets.addProperty("large_image", "logo");
      assets.addProperty("large_text", "Spectre Client");
      activity.add("assets", assets);
      if (card.discordButton()) {
         JsonObject button = new JsonObject();
         button.addProperty("label", "Discord");
         button.addProperty("url", "https://discord.gg/spectre");
         JsonArray buttons = new JsonArray();
         buttons.add(button);
         activity.add("buttons", buttons);
      }

      return activity;
   }

   @Environment(EnvType.CLIENT)
   private record Card(String details, long startedAt, boolean discordButton) {
   }

   @Environment(EnvType.CLIENT)
   private final class Link implements Runnable {
      private final Thread thread = new Thread(this, "Spectre Discord Presence");
      private volatile boolean running = true;
      private Thread previous;
      private DiscordIpc ipc;
      private DiscordPresenceModule.Card sent;
      private long sentAt;
      private long retryMs = 5000L;
      private String lastProblem;

      Link(DiscordPresenceModule.Link previous) {
         this.previous = previous != null ? previous.thread : null;
         this.thread.setDaemon(true);
      }

      void start() {
         this.thread.start();
      }

      void wake() {
         LockSupport.unpark(this.thread);
      }

      void stop() {
         this.running = false;
         LockSupport.unpark(this.thread);
      }

      @Override
      public void run() {
         this.awaitPrevious();

         while (this.running) {
            if (this.ipc == null && !this.connect()) {
               this.pause(this.retryMs);
               this.retryMs = Math.min(this.retryMs * 2L, 30000L);
            } else {
               try {
                  this.ipc.poll();
                  DiscordPresenceModule.Card next = DiscordPresenceModule.this.card;
                  long since = System.currentTimeMillis() - this.sentAt;
                  if (this.sent == null || since >= (next.equals(this.sent) ? 60000L : 4000L)) {
                     this.send(next);
                  }
               } catch (IOException var4) {
                  this.report("lost Discord", var4);
                  this.hangUp();
                  continue;
               }

               this.pause(1000L);
            }
         }

         if (this.ipc != null) {
            this.clear();
         }
      }

      private void awaitPrevious() {
         if (this.previous != null) {
            try {
               this.previous.join();
            } catch (InterruptedException var2) {
               this.running = false;
            }

            this.previous = null;
         }
      }

      private boolean connect() {
         try {
            this.ipc = DiscordIpc.connect("1553391775840866314");
         } catch (IOException var2) {
            this.report("could not connect", var2);
            return false;
         }

         if (this.ipc == null) {
            return false;
         } else {
            DiscordPresenceModule.LOGGER.info("[Spectre] Discord Presence: connected to Discord");
            this.retryMs = 5000L;
            this.lastProblem = null;
            this.sent = null;
            return true;
         }
      }

      private void send(DiscordPresenceModule.Card next) throws IOException {
         try {
            this.ipc.setActivity(DiscordPresenceModule.activity(next));
         } catch (DiscordIpc.RefusedException var3) {
            this.report("Discord refused the card", var3);
         }

         this.sent = next;
         this.sentAt = System.currentTimeMillis();
      }

      private void clear() {
         try {
            this.ipc.setActivity(null);
         } catch (DiscordIpc.RefusedException | IOException var2) {
         }

         this.hangUp();
      }

      private void hangUp() {
         this.ipc.close();
         this.ipc = null;
         this.sent = null;
      }

      private void pause(long millis) {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
      }

      private void report(String what, Exception problem) {
         String line = what + " (" + problem + ")";
         if (!line.equals(this.lastProblem)) {
            this.lastProblem = line;
            DiscordPresenceModule.LOGGER.warn("[Spectre] Discord Presence: {}", line);
         }
      }
   }
}
