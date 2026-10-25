package net.spectre.client.event.impl;

import net.spectre.client.event.Event;
import net.spectre.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public final class Render2DEvent extends Event {
   public static final EventType<Render2DEvent> TYPE = new EventType<>("Render2DEvent");
   public GuiGraphicsExtractor graphics;
   public float partialTick;
   private static final Render2DEvent INSTANCE = new Render2DEvent();

   private Render2DEvent() {
   }

   public static void dispatch(GuiGraphicsExtractor graphics, float partialTick) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.graphics = graphics;
         INSTANCE.partialTick = partialTick;
         TYPE.dispatch(INSTANCE);
         INSTANCE.graphics = null;
      }
   }
}
