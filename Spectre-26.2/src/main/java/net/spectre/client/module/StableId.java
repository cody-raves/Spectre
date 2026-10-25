package net.spectre.client.module;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class StableId {
   private StableId() {
   }

   public static String validate(String id) {
      if (!isValid(id)) {
         throw new IllegalArgumentException("Invalid stable id: " + id);
      } else {
         return id;
      }
   }

   public static String legacy(String label) {
      String lower = label.toLowerCase(Locale.ROOT);
      StringBuilder id = new StringBuilder(lower.length());
      boolean pendingDash = false;

      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            if (pendingDash && !id.isEmpty()) {
               id.append('-');
            }

            pendingDash = false;
            id.append(c);
         } else {
            pendingDash = true;
         }
      }

      return validate(id.toString());
   }

   private static boolean isValid(String id) {
      if (id != null && !id.isEmpty()) {
         boolean afterSeparator = true;

         for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) {
               if (c != '.' && c != '_' && c != '-') {
                  return false;
               }

               if (afterSeparator) {
                  return false;
               }

               afterSeparator = true;
            } else {
               afterSeparator = false;
            }
         }

         return !afterSeparator;
      } else {
         return false;
      }
   }
}
