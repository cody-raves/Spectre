package net.spectre.client.event.impl;

import net.spectre.client.event.CancellableEvent;
import net.spectre.client.event.CancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MouseInputEvent extends CancellableEvent {
   public static final CancellableEventType<MouseInputEvent> TYPE = new CancellableEventType<>("MouseInputEvent");
   public long window;
   public int button;
   public int action;
   public int mods;
   private static final MouseInputEvent INSTANCE = new MouseInputEvent();

   private MouseInputEvent() {
   }

   public static boolean dispatch(long window, int button, int action, int mods) {
      if (!TYPE.hasPreListeners()) {
         return false;
      } else {
         INSTANCE.window = window;
         INSTANCE.button = button;
         INSTANCE.action = action;
         INSTANCE.mods = mods;
         INSTANCE.resetCancellation();
         TYPE.dispatch(INSTANCE);
         return INSTANCE.isCancelled();
      }
   }
}
