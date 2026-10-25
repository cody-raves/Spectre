package net.spectre.client.event.impl;

import net.spectre.client.event.Event;
import net.spectre.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;

@Environment(EnvType.CLIENT)
public final class PacketAppliedEvent extends Event {
   public static final EventType<PacketAppliedEvent> TYPE = new EventType<>("PacketAppliedEvent");
   public Packet<?> packet;
   private static final PacketAppliedEvent INSTANCE = new PacketAppliedEvent();

   private PacketAppliedEvent() {
   }

   public static void dispatch(Packet<?> packet) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.packet = packet;
         TYPE.dispatch(INSTANCE);
         INSTANCE.packet = null;
      }
   }
}
