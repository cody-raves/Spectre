package net.spectre.client.event.impl;

import net.spectre.client.event.CancellableEvent;
import net.spectre.client.event.PooledCancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;

@Environment(EnvType.CLIENT)
public final class PacketReceiveNettyEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PacketReceiveNettyEvent> TYPE = new PooledCancellableEventType<>(
      "PacketReceiveNettyEvent", PacketReceiveNettyEvent::new, 1024
   );
   public Packet<?> packet;

   private PacketReceiveNettyEvent() {
   }

   public static boolean dispatchSync(Packet<?> packet) {
      if (!TYPE.hasPreListeners()) {
         return false;
      } else {
         PacketReceiveNettyEvent event = TYPE.pool.acquire();
         event.packet = packet;
         event.resetCancellation();

         boolean var2;
         try {
            TYPE.dispatch(event);
            var2 = event.isCancelled();
         } finally {
            event.packet = null;
            TYPE.pool.release(event);
         }

         return var2;
      }
   }
}
