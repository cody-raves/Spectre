package net.spectre.client.event;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class CancellableEventType<E extends CancellableEvent> extends EventType<E> {
   public CancellableEventType() {
   }

   public CancellableEventType(String name) {
      super(name);
   }

   public void dispatchPre(E event) {
      dispatchPreCancellable(this.list, event);
   }

   public void dispatchPost(E event) {
      dispatchPostGuarded(this.list, event);
   }

   static <E extends CancellableEvent> void dispatchPreCancellable(ListenerList<E> list, E event) {
      Consumer<E>[] cbs = list.preCallbacks();

      for (int i = 0; i < cbs.length; i++) {
         try {
            cbs[i].accept(event);
         } catch (Exception var5) {
            ListenerErrorReporter.report(list.handleOf(Stage.PRE, cbs[i]), event, var5);
         }

         if (event.cancelled) {
            break;
         }
      }
   }

   static <E extends CancellableEvent> void dispatchPostGuarded(ListenerList<E> list, E event) {
      event.postStage = true;

      try {
         list.dispatchPost(event);
      } finally {
         event.postStage = false;
      }
   }
}
