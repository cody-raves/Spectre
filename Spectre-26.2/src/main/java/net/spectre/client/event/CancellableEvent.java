package net.spectre.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class CancellableEvent extends Event {
   boolean cancelled;
   boolean postStage;

   public final void setCancelled(boolean cancelled) {
      if (!this.postStage) {
         this.cancelled = cancelled;
      }
   }

   public final boolean isCancelled() {
      return this.cancelled;
   }

   protected void resetCancellation() {
      this.cancelled = false;
      this.postStage = false;
   }
}
