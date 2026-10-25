package net.spectre.client.config;

import java.lang.ref.WeakReference;
import java.util.List;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ProfileAutoLoad {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final Object OWNER = new Object();
   private static final long TOAST_WAIT_NANOS = 15000000000L;
   private static WeakReference<Connection> handled = new WeakReference<>(null);
   private static String pendingTitle;
   private static String pendingMessage;
   private static long pendingSince;

   private ProfileAutoLoad() {
   }

   public static void install() {
      PacketAppliedEvent.TYPE.listen(OWNER, event -> {
         if (event.packet instanceof ClientboundLoginPacket) {
            onJoin();
         }
      });
      TickEvent.TYPE.listen(OWNER, event -> showPendingToast());
   }

   private static void onJoin() {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isLocalServer()) {
         ClientPacketListener listener = mc.getConnection();
         ServerData server = listener == null ? null : listener.getServerData();
         if (server != null && server.ip != null) {
            Connection connection = listener.getConnection();
            if (connection != handled.get()) {
               handled = new WeakReference<>(connection);
               List<String> loaded = ConfigProfiles.getInstance().loadForServer(server.ip);
               if (!loaded.isEmpty()) {
                  LOGGER.info("[Spectre] Joined {} - loaded profile {}", server.ip, String.join(", ", loaded));
                  pendingTitle = loaded.size() == 1 ? "Profile loaded" : "Profiles loaded";
                  pendingMessage = String.join(", ", loaded);
                  pendingSince = System.nanoTime();
               }
            }
         }
      }
   }

   private static void showPendingToast() {
      if (pendingTitle != null) {
         Minecraft mc = Minecraft.getInstance();
         boolean expired = System.nanoTime() - pendingSince > 15000000000L;
         if (expired || mc.player != null && !(mc.gui.screen() instanceof LevelLoadingScreen)) {
            String title = pendingTitle;
            String message = pendingMessage;
            pendingTitle = null;
            pendingMessage = null;
            if (mc.level != null) {
               ChallengeToast.addOrUpdate(new ItemStack(Items.WRITABLE_BOOK), title, Component.literal(message), false);
            }
         }
      }
   }
}
