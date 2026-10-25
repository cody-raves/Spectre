package net.spectre.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface Listener<E extends Event> {
   void invoke(E var1);
}
