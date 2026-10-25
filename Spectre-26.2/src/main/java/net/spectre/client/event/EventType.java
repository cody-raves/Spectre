package net.spectre.client.event;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class EventType<E extends Event> {
   protected final ListenerList<E> list = new ListenerList<>();
   private final String name;

   public EventType() {
      this("unnamed");
   }

   public EventType(String name) {
      this.name = name;
   }

   public final String name() {
      return this.name;
   }

   public void register(ListenerHandle<E> handle) {
      this.list.register(handle);
   }

   public void unregister(ListenerHandle<E> handle) {
      this.list.unregister(handle);
   }

   public ListenerHandle<E> listen(Object owner, Consumer<E> callback) {
      return this.listen(owner, Stage.PRE, EventPriority.NORMAL, callback);
   }

   public ListenerHandle<E> listen(Object owner, EventPriority priority, Consumer<E> callback) {
      return this.listen(owner, Stage.PRE, priority, callback);
   }

   public ListenerHandle<E> listen(Object owner, Stage stage, EventPriority priority, Consumer<E> callback) {
      ListenerHandle<E> handle = new ListenerHandle<>(this, stage, priority, owner, callback);
      this.register(handle);
      return handle;
   }

   public boolean hasPreListeners() {
      return this.list.preCallbacks().length != 0;
   }

   public boolean hasPostListeners() {
      return this.list.postCallbacks().length != 0;
   }

   public boolean hasListeners() {
      return this.hasPreListeners() || this.hasPostListeners();
   }

   public final void dispatch(E event) {
      this.dispatchPre(event);
   }

   public void dispatchPre(E event) {
      this.list.dispatchPre(event);
   }

   public void dispatchPost(E event) {
      this.list.dispatchPost(event);
   }
}
