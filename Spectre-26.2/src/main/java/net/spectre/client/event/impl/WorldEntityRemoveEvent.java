package net.spectre.client.event.impl;

import net.spectre.client.event.Event;
import net.spectre.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;

@Environment(EnvType.CLIENT)
public final class WorldEntityRemoveEvent extends Event {
   public static final EventType<WorldEntityRemoveEvent> TYPE = new EventType<>("WorldEntityRemoveEvent");
   public Entity entity;
   private static final WorldEntityRemoveEvent INSTANCE = new WorldEntityRemoveEvent();

   private WorldEntityRemoveEvent() {
   }

   public static void dispatch(Entity entity) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.entity = entity;
         TYPE.dispatch(INSTANCE);
         INSTANCE.entity = null;
      }
   }
}
