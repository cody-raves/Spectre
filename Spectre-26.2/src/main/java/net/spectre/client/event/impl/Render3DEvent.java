package net.spectre.client.event.impl;

import net.spectre.client.event.Event;
import net.spectre.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;

@Environment(EnvType.CLIENT)
public final class Render3DEvent extends Event {
   public static final EventType<Render3DEvent> TYPE = new EventType<>("Render3DEvent");
   public float partialTick;
   public Frustum frustum;
   private static final Render3DEvent INSTANCE = new Render3DEvent();
   private static boolean rendering;
   private static long frame;
   private static long lastDurationNanos;

   private Render3DEvent() {
   }

   public static void dispatch(float partialTick) {
      dispatch(partialTick, null);
   }

   public static void dispatch(float partialTick, Frustum frustum) {
      if (!TYPE.hasListeners()) {
         lastDurationNanos = 0L;
      } else if (rendering) {
         throw new IllegalStateException("Render3DEvent cannot be nested");
      } else {
         rendering = true;
         long start = System.nanoTime();
         INSTANCE.partialTick = partialTick;
         INSTANCE.frustum = frustum;

         try {
            TYPE.dispatchPre(INSTANCE);
            TYPE.dispatchPost(INSTANCE);
         } finally {
            INSTANCE.frustum = null;
            lastDurationNanos = System.nanoTime() - start;
            frame++;
            rendering = false;
         }
      }
   }

   public static long frame() {
      return frame;
   }

   public static long lastDurationNanos() {
      return lastDurationNanos;
   }
}
