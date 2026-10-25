package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum Detail {
   LOW("Low", 7500, 0.42F),
   MEDIUM("Medium", 15000, 0.3F),
   HIGH("High", 30000, 0.21F);

   public final String label;
   public final int budget;
   public final float step;

   private Detail(String label, int budget, float step) {
      this.label = label;
      this.budget = budget;
      this.step = step;
   }

   public static Detail byLabel(String label) {
      for (Detail d : values()) {
         if (d.label.equalsIgnoreCase(label) || d.name().equalsIgnoreCase(label)) {
            return d;
         }
      }

      return MEDIUM;
   }
}
