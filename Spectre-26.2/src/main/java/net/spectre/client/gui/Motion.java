package net.spectre.client.gui;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class Motion {
   static final float SETTLE = 0.001F;

   private Motion() {
   }

   static float step(float from, float target, float dt, float speed) {
      if (from != target && !Float.isNaN(from)) {
         float value = ClickGuiRenderer.smooth(from, target, dt, speed);
         return Math.abs(value - target) < 0.001F ? target : value;
      } else {
         return target;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Rows {
      private List<?> rows;
      private float[] hover = new float[0];
      private float[] on = new float[0];

      void bind(List<?> rows) {
         if (rows != this.rows) {
            this.rows = rows;
            this.hover = new float[rows.size()];
            this.on = new float[rows.size()];
            Arrays.fill(this.hover, Float.NaN);
            Arrays.fill(this.on, Float.NaN);
         }
      }

      float hover(int row, boolean hovered, float dt, float speed) {
         float from = this.hover[row];
         float target = hovered ? 1.0F : 0.0F;
         return from == target ? target : (this.hover[row] = Motion.step(from, target, dt, speed));
      }

      float on(int row, boolean enabled, float dt, float speed) {
         float from = this.on[row];
         float target = enabled ? 1.0F : 0.0F;
         return from == target ? target : (this.on[row] = Motion.step(from, target, dt, speed));
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Store {
      private final Int2FloatOpenHashMap values = new Int2FloatOpenHashMap();

      Store() {
         this.values.defaultReturnValue(Float.NaN);
      }

      float ease(int key, float target, float dt, float speed) {
         float from = this.values.get(key);
         float value = Motion.step(from, target, dt, speed);
         if (value != from) {
            this.values.put(key, value);
         }

         return value;
      }
   }
}
