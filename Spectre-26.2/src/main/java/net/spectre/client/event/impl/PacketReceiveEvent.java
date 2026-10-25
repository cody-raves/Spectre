package net.spectre.client.event.impl;

import java.util.function.BooleanSupplier;
import net.spectre.client.event.Event;
import net.spectre.client.event.EventBus;
import net.spectre.client.event.PooledEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;

@Environment(EnvType.CLIENT)
public final class PacketReceiveEvent extends Event implements Runnable {
   public static final PooledEventType<PacketReceiveEvent> TYPE = new PooledEventType<>("PacketReceiveEvent", PacketReceiveEvent::new, 1024);
   public Packet<?> packet;
   private BooleanSupplier connectionActive;

   private PacketReceiveEvent() {
   }

   public static void dispatchAsync(Packet<?> packet) {
      dispatchAsync(packet, null);
   }

   public static void dispatchAsync(Packet<?> packet, BooleanSupplier connectionActive) {
      if (TYPE.hasListeners()) {
         PacketReceiveEvent event = TYPE.pool.acquire();
         event.packet = packet;
         event.connectionActive = connectionActive;
         if (!EventBus.INSTANCE.postAsync(event)) {
            event.release();
         }
      }
   }

   @Override
   public void run() {
      try {
         if (this.connectionActive == null || this.connectionActive.getAsBoolean()) {
            TYPE.dispatch(this);
            TYPE.dispatchPost(this);
            return;
         }
      } finally {
         this.release();
      }
   }

   private void release() {
      this.packet = null;
      this.connectionActive = null;
      TYPE.pool.release(this);
   }
}
