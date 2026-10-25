package net.spectre.client.event;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record ListenerHandle<E extends Event>(EventType<E> type, Stage stage, EventPriority priority, Object owner, Consumer<E> callback) {
   public void unregister() {
      this.type.unregister(this);
   }
}
