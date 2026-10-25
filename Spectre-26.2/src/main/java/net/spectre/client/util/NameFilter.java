package net.spectre.client.util;

import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class NameFilter {
   private static volatile UnaryOperator<String> filter = UnaryOperator.identity();

   private NameFilter() {
   }

   public static String apply(String name) {
      if (name == null) {
         return null;
      } else {
         String shown = filter.apply(name);
         return shown != null ? shown : name;
      }
   }

   public static void set(UnaryOperator<String> newFilter) {
      filter = newFilter != null ? newFilter : UnaryOperator.identity();
   }
}
