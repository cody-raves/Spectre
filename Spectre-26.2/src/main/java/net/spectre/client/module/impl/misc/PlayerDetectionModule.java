package net.spectre.client.module.impl.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.config.ModuleConfigStore;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class PlayerDetectionModule extends ClientModule {
   private static final Set<String> PERMANENT_WHITELIST = Set.of("freecamera");
   private static final long DISCONNECT_DELAY_MS = 500L;
   private static final SystemToastId TOAST_ID = new SystemToastId();
   private final StringSetting modulesToToggle = this.addSetting("modules-to-toggle", new StringSetting("Modules To Toggle", ""));
   private final BooleanSetting enableDisconnect = this.addSetting("disconnect", new BooleanSetting("Disconnect", true));
   private final ModeSetting notificationMode = this.addSetting("notification-mode", new ModeSetting("Notification Mode", "Both", "Chat", "Toast", "Both"));
   private final BooleanSetting toggleOnPlayer = this.addSetting("toggle-on-detect", new BooleanSetting("Toggle On Detect", true));
   private final StringSetting userWhitelist = this.addSetting("user-whitelist", new StringSetting("User Whitelist", ""));
   private final BooleanSetting enablePanicPay = this.addSetting("enable-panic-pay", new BooleanSetting("Enable Panic Pay", false));
   private final StringSetting panicPayTarget = this.addSetting("target-player", new StringSetting("Target Player", ""));
   private final StringSetting panicPayAmount = this.addSetting("amount", new StringSetting("Amount", ""));
   private final Set<String> detectedPlayers = new HashSet<>();
   private ClientLevel lastLevel;
   private String whitelistRaw;
   private Set<String> whitelist = Set.of();

   public PlayerDetectionModule() {
      super("player-detection", "Player Detection", "Detects when players are in the world.", ModuleCategory.DONUT);
      this.usesNotifications();
      this.panicPayTarget.visibleWhen(this.enablePanicPay::get);
      this.panicPayAmount.visibleWhen(this.enablePanicPay::get);
      this.panicPayTarget.privateValue();
      this.panicPayAmount.privateValue();
      this.userWhitelist.privateValue();
      this.userWhitelist.getImString().inputData.isResizable = true;
      this.modulesToToggle.getImString().inputData.isResizable = true;
   }

   @Override
   protected void onEnable() {
      this.detectedPlayers.clear();
      this.lastLevel = null;
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.detectedPlayers.clear();
      this.lastLevel = null;
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer self = mc.player;
      ClientLevel level = mc.level;
      if (self != null && level != null) {
         if (level != this.lastLevel) {
            this.detectedPlayers.clear();
            this.lastLevel = level;
         }

         String selfName = self.getGameProfile().name();
         UUID selfId = self.getUUID();
         Set<String> ignored = this.whitelist();
         Set<String> current = null;

         for (AbstractClientPlayer player : level.players()) {
            if (player != self && !selfId.equals(player.getUUID())) {
               String name = player.getGameProfile().name();
               if (name != null && !name.equals(selfName)) {
                  String key = name.toLowerCase(Locale.ROOT);
                  if (!PERMANENT_WHITELIST.contains(key) && !ignored.contains(key)) {
                     if (current == null) {
                        current = new LinkedHashSet<>();
                     }

                     current.add(name);
                  }
               }
            }
         }

         if (current == null) {
            this.detectedPlayers.clear();
         } else if (!current.equals(this.detectedPlayers)) {
            this.detectedPlayers.clear();
            this.detectedPlayers.addAll(current);
            this.handlePlayerDetection(current);
         }
      } else {
         this.detectedPlayers.clear();
         this.lastLevel = null;
      }
   }

   private void handlePlayerDetection(Set<String> players) {
      Minecraft mc = Minecraft.getInstance();
      String var3 = this.notificationMode.get();
      switch (var3) {
         case "Chat":
            this.announceDetection(players);
            break;
         case "Toast":
            this.showToast(mc);
            break;
         default:
            this.announceDetection(players);
            this.showToast(mc);
      }

      for (ClientModule module : this.resolveModulesToToggle()) {
         module.toggle();
      }

      if (this.enablePanicPay.get()) {
         this.panicPay(mc);
      }

      if (this.toggleOnPlayer.get()) {
         this.toggle();
      }

      if (this.enableDisconnect.get()) {
         this.scheduleDisconnect(mc, players);
      }
   }

   private void announceDetection(Set<String> players) {
      ModuleChat.send(this, names(ModuleChat.body().danger(players.size() == 1 ? "Player detected" : "Players detected").text(": "), players));
   }

   private static ModuleChat.Body names(ModuleChat.Body body, Collection<String> players) {
      boolean first = true;

      for (String player : players) {
         if (!first) {
            body.text(", ");
         }

         body.name(player);
         first = false;
      }

      return body;
   }

   private void showToast(Minecraft mc) {
      SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST_ID, Component.literal(this.name()), Component.literal("Player Detected!"));
   }

   private List<ClientModule> resolveModulesToToggle() {
      String raw = this.modulesToToggle.get();
      if (raw.isBlank()) {
         return List.of();
      } else {
         Set<ClientModule> resolved = new LinkedHashSet<>();

         for (String entry : raw.split(",")) {
            String key = entry.trim();
            if (!key.isEmpty()) {
               String renamed = ModuleConfigStore.renamedModuleId(key);
               if (renamed != null) {
                  key = renamed;
               }

               for (ClientModule module : ModuleRegistry.all()) {
                  if (module.id().equalsIgnoreCase(key) || module.name().equalsIgnoreCase(key)) {
                     if (module != this) {
                        resolved.add(module);
                     }
                     break;
                  }
               }
            }
         }

         return new ArrayList<>(resolved);
      }
   }

   private Set<String> whitelist() {
      String raw = this.userWhitelist.get();
      if (!raw.equals(this.whitelistRaw)) {
         Set<String> parsed = new HashSet<>();

         for (String entry : raw.split(",")) {
            String name = entry.trim();
            if (!name.isEmpty()) {
               parsed.add(name.toLowerCase(Locale.ROOT));
            }
         }

         this.whitelist = parsed;
         this.whitelistRaw = raw;
      }

      return this.whitelist;
   }

   private void panicPay(Minecraft mc) {
      String target = this.panicPayTarget.get().trim();
      String amount = this.panicPayAmount.get().trim();
      if (target.isEmpty()) {
         ModuleChat.send(this, ModuleChat.body().text("Panic pay target is ").warn("not set"));
      } else if (amount.isEmpty()) {
         ModuleChat.send(this, ModuleChat.body().text("Panic pay amount is ").warn("not set"));
      } else {
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            String command = String.format("/pay %s %s", target, amount);
            if (mc.gui != null) {
               mc.gui.hud.getChat().addRecentChat(command);
            }

            connection.sendCommand(command.substring(1));
            ModuleChat.send(this, ModuleChat.body().text("Panic pay ").good("sent").text(": ").value(amount).text(" to ").name(target));
         }
      }
   }

   private void scheduleDisconnect(Minecraft mc, Set<String> players) {
      ClientPacketListener listener = mc.getConnection();
      if (listener != null) {
         Connection session = listener.getConnection();
         CompletableFuture.delayedExecutor(500L, TimeUnit.MILLISECONDS, mc).execute(() -> this.disconnectFromServer(mc, session, players));
      }
   }

   private void disconnectFromServer(Minecraft mc, Connection session, Set<String> players) {
      ClientPacketListener listener = mc.getConnection();
      if (mc.level != null && listener != null && listener.getConnection() == session) {
         session.disconnect(Component.literal("Player(s) detected: " + String.join(", ", players)));
         ModuleChat.send(
            this,
            names(
                  ModuleChat.body().danger("Disconnected").text(" from the server (").text(players.size() == 1 ? "player" : "players").text(" detected: "),
                  players
               )
               .text(")")
         );
      }
   }

}
