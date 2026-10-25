package net.spectre.client.service;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TickJitter {
   private static final float VARIANCE = 0.2F;

   private TickJitter() {
   }

   public static int apply(int baseTicks) {
      if (baseTicks <= 0) {
         return baseTicks;
      } else {
         int span = Math.max(1, Math.round(baseTicks * 0.2F));
         int delta = ThreadLocalRandom.current().nextInt(-span, span + 1);
         return Math.max(1, baseTicks + delta);
      }
   }
}
