package net.spectre.client.module.impl.misc;

import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@Environment(EnvType.CLIENT)
public final class AirStuckModule extends ClientModule {
   public static volatile AirStuckModule active;

   public AirStuckModule() {
      super("air-stuck", "Air Stuck", "Freezes you in place, even mid-air.", ModuleCategory.DONUT);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      active = this;
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   private void onPacketSend(PacketSendEvent event) {
      if (event.packet instanceof ServerboundClientTickEndPacket || event.packet instanceof ServerboundMovePlayerPacket) {
         event.setCancelled(true);
      }
   }
}
