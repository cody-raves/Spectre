package net.spectre.client.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleCategory {
   private static final List<ModuleCategory> REGISTERED = new ArrayList<>();
   private static volatile List<ModuleCategory> values = List.of();
   public static final ModuleCategory COMBAT = register("COMBAT", "Combat", ModuleCategory.Icon.CROSSHAIR);
   public static final ModuleCategory MISC = register("MISC", "Misc", ModuleCategory.Icon.GRID);
   public static final ModuleCategory DONUT = register("DONUT", "Donut", ModuleCategory.Icon.RING);
   public static final ModuleCategory RENDER = register("RENDER", "Render", ModuleCategory.Icon.EYE);
   public static final ModuleCategory CLIENT = register("CLIENT", "Spectre Client", ModuleCategory.Icon.GHOST);
   public static final ModuleCategory MOVEMENT = register("MOVEMENT", "Movement", ModuleCategory.Icon.CHEVRON);
   public static final ModuleCategory BASE_RAID = register("BASE_RAID", "Base Raid", ModuleCategory.Icon.HOUSE);
   public static final ModuleCategory BASE_BUILDER = register("BASE_BUILDER", "Base Builder", ModuleCategory.Icon.PAPYRUS);
   public static final ModuleCategory VISUALS = register("VISUALS", "Visuals", ModuleCategory.Icon.MONITOR);
   private final String id;
   private final String displayName;
   private final ModuleCategory.Icon icon;

   private ModuleCategory(String id, String displayName, ModuleCategory.Icon icon) {
      this.id = id;
      this.displayName = displayName;
      this.icon = icon;
   }

   public static synchronized ModuleCategory register(String id, String displayName, ModuleCategory.Icon icon) {
      if (!isValidId(id)) {
         throw new IllegalArgumentException("Category id must be upper-case letters, digits and '_': " + id);
      } else {
         for (ModuleCategory category : REGISTERED) {
            if (category.id.equals(id)) {
               throw new IllegalArgumentException("Duplicate category id: " + id);
            }
         }

         ModuleCategory categoryx = new ModuleCategory(id, Objects.requireNonNull(displayName, "displayName"), Objects.requireNonNull(icon, "icon"));
         REGISTERED.add(categoryx);
         values = List.copyOf(REGISTERED);
         return categoryx;
      }
   }

   private static boolean isValidId(String id) {
      if (id != null && !id.isEmpty() && id.charAt(0) >= 'A' && id.charAt(0) <= 'Z') {
         for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static ModuleCategory register(String id, String displayName) {
      return register(id, displayName, ModuleCategory.Icon.GHOST);
   }

   public static List<ModuleCategory> values() {
      return values;
   }

   public String name() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public ModuleCategory.Icon icon() {
      return this.icon;
   }

   @Override
   public String toString() {
      return this.id;
   }

   @Environment(EnvType.CLIENT)
   public static enum Icon {
      CROSSHAIR,
      CHEVRON,
      GRID,
      RING,
      EYE,
      MONITOR,
      HOUSE,
      PAPYRUS,
      ANKH,
      GHOST;
   }
}
