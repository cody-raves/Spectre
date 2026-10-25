package net.spectre.client.chat;

import net.spectre.client.config.ThemeStore;
import net.spectre.client.gui.ToggleToasts;
import net.spectre.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public final class ModuleChat {
   private static final String HEADER = "Spectre Client";
   private static final int DARK_GREY = 5592405;
   private static final int GREY = 11184810;
   private static final int WHITE = 16777215;
   private static final int DANGER = 16733525;
   private static final int GOOD = 5635925;
   private static final int WARN = 16755200;
   private static final int VALUE = 5636095;

   private ModuleChat() {
   }

   public static ModuleChat.Body body() {
      return new ModuleChat.Body();
   }

   public static void send(ClientModule module, Component body) {
      onClientThread(() -> {
         if (module.notificationsOn()) {
            addLine(module, body);
         }
      });
   }

   public static void send(ClientModule module, ModuleChat.Body body) {
      send(module, body.build());
   }

   public static void send(ClientModule module, String text) {
      send(module, Component.literal(text));
   }

   public static void actionBar(ClientModule module, ModuleChat.Body body) {
      Component built = body.build();
      onClientThread(() -> {
         Minecraft mc = Minecraft.getInstance();
         if (module.notificationsOn() && mc.gui != null) {
            mc.gui.hud.setOverlayMessage(line(module, built), false);
         }
      });
   }

   public static void toggleLine(ClientModule module, ModuleChat.Body state) {
      ToggleToasts.show(module, module.isEnabled());
   }

   private static void addLine(ClientModule module, Component body) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui != null) {
         mc.gui.hud.getChat().addClientSystemMessage(line(module, body));
      }
   }

   private static MutableComponent line(ClientModule module, Component body) {
      return Component.empty()
         .append(Component.literal("[").withColor(5592405))
         .append(Component.literal("Spectre Client").withColor(ThemeStore.get().accent.rgb()))
         .append(Component.literal("] ").withColor(5592405))
         .append(Component.literal(module.name()).withColor(16777215))
         .append(Component.literal(" : ").withColor(5592405))
         .append(Component.empty().withColor(11184810).append(body));
   }

   private static void onClientThread(Runnable task) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         task.run();
      } else {
         mc.execute(task);
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Body {
      private final MutableComponent root = Component.empty().withColor(11184810);

      private Body() {
      }

      public ModuleChat.Body text(String text) {
         this.root.append(Component.literal(text));
         return this;
      }

      public ModuleChat.Body name(String name) {
         return this.part(name, 16777215);
      }

      public ModuleChat.Body danger(String text) {
         return this.part(text, 16733525);
      }

      public ModuleChat.Body good(String text) {
         return this.part(text, 5635925);
      }

      public ModuleChat.Body warn(String text) {
         return this.part(text, 16755200);
      }

      public ModuleChat.Body value(Object value) {
         return this.part(String.valueOf(value), 5636095);
      }

      public ModuleChat.Body coords(int x, int y, int z) {
         return this.part(x + ", " + y + ", " + z, 5636095);
      }

      public ModuleChat.Body append(Component component) {
         this.root.append(component);
         return this;
      }

      public Component build() {
         return this.root.copy();
      }

      private ModuleChat.Body part(String text, int rgb) {
         this.root.append(Component.literal(text).withColor(rgb));
         return this;
      }
   }
}


