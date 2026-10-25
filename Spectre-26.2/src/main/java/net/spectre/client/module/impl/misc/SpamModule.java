package net.spectre.client.module.impl.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.apache.commons.lang3.RandomStringUtils;

@Environment(EnvType.CLIENT)
public final class SpamModule extends ClientModule {
   public static volatile SpamModule active;
   private static final char SEPARATOR = '|';
   private static final int MAX_MESSAGE_LENGTH = 256;
   private final StringSetting messages = this.addSetting("messages", new StringSetting("Messages (separate with |)", "Spectre was here!"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 20.0F, 0.0F, 1200.0F).step(1.0F));
   private final BooleanSetting disableOnLeave = this.addSetting("disable-on-leave", new BooleanSetting("Disable On Leave", true));
   private final BooleanSetting disableOnDisconnect = this.addSetting("disable-on-disconnect", new BooleanSetting("Disable On Disconnect", true));
   private final BooleanSetting random = this.addSetting("randomise", new BooleanSetting("Randomise", false));
   private final BooleanSetting autoSplitMessages = this.addSetting("auto-split-messages", new BooleanSetting("Auto Split Messages", false));
   private final NumberSetting splitLength = this.addSetting("split-length", new NumberSetting("Split Length", 256.0F, 1.0F, 256.0F).step(1.0F));
   private final NumberSetting autoSplitDelay = this.addSetting("split-delay", new NumberSetting("Split Delay", 20.0F, 0.0F, 200.0F).step(1.0F));
   private final BooleanSetting bypass = this.addSetting("bypass", new BooleanSetting("Bypass", false));
   private final BooleanSetting uppercase = this.addSetting("include-uppercase-characters", new BooleanSetting("Include Uppercase Characters", true));
   private final NumberSetting length = this.addSetting("length", new NumberSetting("Length", 16.0F, 1.0F, 256.0F).step(1.0F));
   private final Random rng = new Random();
   private int messageI;
   private int timer;
   private int splitNum;
   private String text;
   private boolean resetPending;
   private String parsedRaw;
   private List<String> parsedMessages = List.of();

   public SpamModule() {
      super("spam", "Spam", "Spams specified messages in chat.", ModuleCategory.MISC);
      this.splitLength.visibleWhen(this.autoSplitMessages::get);
      this.autoSplitDelay.visibleWhen(this.autoSplitMessages::get);
      this.uppercase.visibleWhen(this.bypass::get);
      this.length.visibleWhen(this.bypass::get);
   }

   @Override
   protected void onEnable() {
      this.resetPending = true;
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public static void onGameLeft() {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) {
         mc.execute(SpamModule::onGameLeft);
      } else {
         SpamModule spam = active;
         if (spam != null) {
            spam.handleGameLeft();
         }
      }
   }

   public static void onDisconnectedScreen() {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) {
         mc.execute(SpamModule::onDisconnectedScreen);
      } else {
         SpamModule spam = active;
         if (spam != null && spam.isEnabled() && spam.disableOnDisconnect.get()) {
            spam.requestDisable();
         }
      }
   }

   private void handleGameLeft() {
      if (this.isEnabled()) {
         if (this.disableOnLeave.get()) {
            this.requestDisable();
         } else {
            this.resetPending = true;
         }
      }
   }

   private void onPostTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (this.resetPending) {
            this.resetPending = false;
            this.timer = this.delay.getInt();
            this.messageI = 0;
            this.splitNum = 0;
         }

         List<String> list = this.messages();
         if (!list.isEmpty()) {
            if (this.timer > 0) {
               this.timer--;
            } else {
               if (this.text == null) {
                  int i;
                  if (this.random.get()) {
                     i = this.rng.nextInt(list.size());
                  } else {
                     if (this.messageI >= list.size()) {
                        this.messageI = 0;
                     }

                     i = this.messageI++;
                  }

                  this.text = list.get(i);
                  if (this.bypass.get()) {
                     String suffix = RandomStringUtils.insecure().nextAlphabetic(this.length.getInt());
                     if (!this.uppercase.get()) {
                        suffix = suffix.toLowerCase(Locale.ROOT);
                     }

                     this.text = this.text + " " + suffix;
                  }
               }

               int chunk = this.splitLength.getInt();
               if (this.autoSplitMessages.get() && this.text.length() > chunk) {
                  int splits = (this.text.length() + chunk - 1) / chunk;
                  if (this.splitNum >= splits) {
                     this.splitNum = 0;
                  }

                  int start = this.splitNum * chunk;
                  int end = Math.min(start + chunk, this.text.length());
                  sendPlayerMsg(mc, player.connection, this.text.substring(start, end));
                  this.splitNum = (this.splitNum + 1) % splits;
                  this.timer = this.autoSplitDelay.getInt();
                  if (this.splitNum == 0) {
                     this.timer = this.delay.getInt();
                     this.text = null;
                  }
               } else {
                  if (this.text.length() > 256) {
                     this.text = this.text.substring(0, 256);
                  }

                  sendPlayerMsg(mc, player.connection, this.text);
                  this.timer = this.delay.getInt();
                  this.text = null;
               }
            }
         }
      }
   }

   private static void sendPlayerMsg(Minecraft mc, ClientPacketListener connection, String message) {
      mc.gui.hud.getChat().addRecentChat(message);
      if (message.startsWith("/")) {
         connection.sendCommand(message.substring(1));
      } else {
         connection.sendChat(message);
      }
   }

   private List<String> messages() {
      String raw = this.messages.get();
      if (!raw.equals(this.parsedRaw)) {
         this.parsedRaw = raw;
         this.parsedMessages = parseMessages(raw);
      }

      return this.parsedMessages;
   }

   static List<String> parseMessages(String raw) {
      List<String> out = new ArrayList<>();
      StringBuilder current = new StringBuilder();

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '|') {
            current.append('|');
            i++;
         } else if (c == '|') {
            addEntry(out, current);
            current.setLength(0);
         } else {
            current.append(c);
         }
      }

      addEntry(out, current);
      return List.copyOf(out);
   }

   private static void addEntry(List<String> out, CharSequence entry) {
      String message = entry.toString().trim();
      if (!message.isEmpty()) {
         out.add(message);
      }
   }
}
