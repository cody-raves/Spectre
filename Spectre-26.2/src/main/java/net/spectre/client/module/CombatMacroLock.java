package net.spectre.client.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CombatMacroLock {
   private static Object holder;

   private CombatMacroLock() {
   }

   public static boolean tryAcquire(Object owner) {
      if (holder != null && holder != owner) {
         return false;
      } else {
         holder = owner;
         return true;
      }
   }

   public static void release(Object owner) {
      if (holder == owner) {
         holder = null;
      }
   }

   public static boolean heldByOther(Object owner) {
      return holder != null && holder != owner;
   }
}
