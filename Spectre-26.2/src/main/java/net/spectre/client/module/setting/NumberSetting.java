package net.spectre.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class NumberSetting extends Setting {
   private static final int MAX_DECIMALS = 4;
   private static final double[] POW10 = new double[]{1.0, 10.0, 100.0, 1000.0, 10000.0};
   private static final String[] FORMATS = new String[]{"%.0f", "%.1f", "%.2f", "%.3f", "%.4f"};
   private final float[] value;
   private final float min;
   private final float max;
   private float step;
   private int stepDecimals;
   private double gridMin;
   private double gridStep;

   public NumberSetting(String name, float value, float min, float max) {
      super(name);
      this.value = new float[]{value};
      this.min = min;
      this.max = max;
   }

   public NumberSetting step(float step) {
      if (step >= 0.0F && !Float.isInfinite(step)) {
         this.step = step;
         this.stepDecimals = step > 0.0F ? Math.max(decimalsOf(step), decimalsOf(this.min)) : 0;
         this.gridMin = this.toDecimals(this.min);
         double written = this.toDecimals(step);
         this.gridStep = written > 0.0 ? written : step;
         this.set(this.value[0]);
         return this;
      } else {
         throw new IllegalArgumentException("Invalid step " + step + " for setting '" + this.name() + "'");
      }
   }

   public float step() {
      return this.step;
   }

   public float get() {
      return this.value[0];
   }

   public int getInt() {
      return Math.round(this.value[0]);
   }

   public void set(float newValue) {
      float snapped = this.snap(newValue);
      if (this.value[0] != snapped) {
         this.value[0] = snapped;
         this.notifyChanged();
      }
   }

   public float snap(float v) {
      float clamped = this.clamp(v);
      if (this.step <= 0.0F) {
         return clamped;
      } else {
         long k = Math.round((clamped - this.gridMin) / this.gridStep + 1.0E-9);
         float snapped = this.onGrid(k);
         if (snapped > this.max) {
            snapped = this.onGrid(k - 1L);
         }

         return Math.max(this.min, Math.min(this.max, snapped));
      }
   }

   public int decimals() {
      if (this.step > 0.0F) {
         return this.stepDecimals;
      } else {
         float range = this.max - this.min;
         if (this.min == Math.rint(this.min) && this.max == Math.rint(this.max) && range >= 20.0F) {
            return 0;
         } else {
            return range <= 2.0F ? 2 : 1;
         }
      }
   }

   public String formatted() {
      int decimals = this.decimals();
      return decimals == 0 ? Integer.toString(Math.round(this.value[0])) : String.format(Locale.ROOT, FORMATS[decimals], this.value[0]);
   }

   public float[] value() {
      return this.value;
   }

   public float min() {
      return this.min;
   }

   public float max() {
      return this.max;
   }

   private float clamp(float v) {
      if (Float.isNaN(v)) {
         return this.value[0];
      } else if (v < this.min) {
         return this.min;
      } else {
         return v > this.max ? this.max : v;
      }
   }

   private float onGrid(long k) {
      return (float)this.toDecimals(this.gridMin + k * this.gridStep);
   }

   private double toDecimals(double v) {
      double scale = POW10[this.stepDecimals];
      return Math.round(v * scale) / scale;
   }

   private static int decimalsOf(float v) {
      if (!Float.isFinite(v)) {
         return 0;
      } else {
         int scale = new BigDecimal(Float.toString(v)).stripTrailingZeros().scale();
         return Math.max(0, Math.min(4, scale));
      }
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.value[0]);
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            this.value[0] = this.snap(element.getAsFloat());
         } catch (RuntimeException var3) {
         }
      }
   }
}
