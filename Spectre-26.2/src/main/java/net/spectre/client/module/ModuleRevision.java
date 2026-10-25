package net.spectre.client.module;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleRevision {
   private static final AtomicLong VALUE = new AtomicLong();
   public static final Runnable CHANGED = ModuleRevision::changed;

   private ModuleRevision() {
   }

   public static long current() {
      return VALUE.get();
   }

   public static void changed() {
      VALUE.incrementAndGet();
   }
}
