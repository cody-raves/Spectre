package net.spectre.client.module.impl.misc;

import java.util.Locale;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.KeyInputEvent;
import net.spectre.client.event.impl.MouseInputEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;

@Environment(EnvType.CLIENT)
public final class ChatMacroModule extends ClientModule {
   public static final int MAX_MACROS = 8;
   private static final String ACTION_SEND = "Send";
   private static final String ACTION_OPEN = "Open in Chat";
   private final ModeSetting slots = this.addSetting("slots", new ModeSetting("Slots", "3", slotChoices()));
   private final ModeSetting pressAction = this.addSetting("on-press", new ModeSetting("On Press", "Send", "Send", "Open in Chat"));
   private final NumberSetting cooldown = this.addSetting("cooldown", new NumberSetting("Cooldown (s)", 1.0F, 1.0F, 10.0F).step(0.5F));
   private final KeySetting[] keys = new KeySetting[8];
   private final StringSetting[] texts = new StringSetting[8];
   private int pendingSlot = -1;
   private long lastSendNanos;
   private boolean sentAny;

   public ChatMacroModule() {
      super("chat-macro", "Chat Macro", "Sends chat messages or commands with a key press.", ModuleCategory.MISC);
      this.usesNotifications();
      this.cooldown.visibleWhen(() -> this.pressAction.is("Send"));

      for (int i = 0; i < 8; i++) {
         int slot = i + 1;
         this.keys[i] = this.addSetting("macro-" + slot + "-key", new KeySetting("Macro " + slot + " Key", -1));
         this.texts[i] = this.addSetting("macro-" + slot + "-text", new StringSetting("Macro " + slot + " Text", ""));
         this.texts[i].privateValue();
         this.keys[i].visibleWhen(() -> slot <= this.slotCount());
         this.texts[i].visibleWhen(() -> slot <= this.slotCount());
      }
   }

   @Override
   protected void onEnable() {
      this.pendingSlot = -1;
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.pendingSlot = -1;
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      if (ModuleKeybinds.isBindable(pressed) && this.pendingSlot == -1) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui.screen() == null && mc.player != null) {
            int count = this.slotCount();

            for (int i = 0; i < count; i++) {
               if (this.keys[i].value() == pressed && !this.texts[i].get().isBlank()) {
                  this.pendingSlot = i;
                  return;
               }
            }
         }
      }
   }

   private void onTick(TickEvent event) {
      int slot = this.pendingSlot;
      if (slot >= 0) {
         this.pendingSlot = -1;
         Minecraft mc = Minecraft.getInstance();
         ClientPacketListener connection = mc.getConnection();
         if (mc.player != null && connection != null && mc.gui.screen() == null) {
            String text = normalize(this.texts[slot].get());
            if (!text.isEmpty() && !text.equals("/")) {
               if (this.pressAction.is("Open in Chat")) {
                  mc.gui.setScreen(new ChatScreen(text, false));
               } else {
                  long now = System.nanoTime();
                  long left = (long)(this.cooldown.get() * 1.0E9) - (now - this.lastSendNanos);
                  if (this.sentAny && left > 0L) {
                     double seconds = Math.ceil(left / 1.0E8) / 10.0;
                     ModuleChat.actionBar(
                        this, ModuleChat.body().warn("On cooldown").text(", ").value(String.format(Locale.ROOT, "%.1fs", seconds)).text(" left")
                     );
                  } else {
                     this.lastSendNanos = now;
                     this.sentAny = true;
                     mc.gui.hud.getChat().addRecentChat(text);
                     if (text.startsWith("/")) {
                        connection.sendCommand(text.substring(1));
                     } else {
                        connection.sendChat(text);
                     }
                  }
               }
            }
         }
      }
   }

   static String normalize(String raw) {
      String allowed = StringUtil.filterText(raw);
      return StringUtil.trimChatMessage(StringUtils.normalizeSpace(allowed.trim()));
   }

   private int slotCount() {
      return Integer.parseInt(this.slots.get());
   }

   private static String[] slotChoices() {
      String[] choices = new String[8];

      for (int i = 0; i < 8; i++) {
         choices[i] = Integer.toString(i + 1);
      }

      return choices;
   }
}
