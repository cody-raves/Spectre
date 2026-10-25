package net.spectre.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class Event {
   boolean pooledValid = true;

   public final void checkValid() {
   }
}
