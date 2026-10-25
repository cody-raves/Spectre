package net.spectre.client.event;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class PooledEventType<E extends Event> extends EventType<E> {
   public final EventPool<E> pool;

   public PooledEventType(Supplier<E> factory, int capacity) {
      this.pool = new EventPool<>(factory, capacity);
   }

   public PooledEventType(String name, Supplier<E> factory, int capacity) {
      super(name);
      this.pool = new EventPool<>(factory, capacity);
   }
}
