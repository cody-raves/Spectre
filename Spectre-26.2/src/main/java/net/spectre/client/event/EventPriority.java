package net.spectre.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum EventPriority {
   LOWEST(-100),
   LOW(-50),
   NORMAL(0),
   HIGH(50),
   HIGHEST(100);

   public final int value;

   private EventPriority(int value) {
      this.value = value;
   }

   public static EventPriority fromInt(int v) {
      if (v >= 75) {
         return HIGHEST;
      } else if (v >= 25) {
         return HIGH;
      } else if (v >= -25) {
         return NORMAL;
      } else {
         return v >= -75 ? LOW : LOWEST;
      }
   }
}
