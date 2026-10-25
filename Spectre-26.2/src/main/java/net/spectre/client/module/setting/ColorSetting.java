package net.spectre.client.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ColorSetting extends Setting {
   private final float[] value;
   private int packed;
   private int packedR;
   private int packedG;
   private int packedB;
   private int packedA;

   public ColorSetting(String name, int argb) {
      super(name);
      this.value = new float[]{(argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24 & 0xFF) / 255.0F};
   }

   public float[] value() {
      return this.value;
   }

   public void setArgb(int argb) {
      if (this.argb() != argb) {
         this.setFromArgb(argb);
         this.notifyChanged();
      }
   }

   public int argb() {
      int r = Float.floatToRawIntBits(this.value[0]);
      int g = Float.floatToRawIntBits(this.value[1]);
      int b = Float.floatToRawIntBits(this.value[2]);
      int a = Float.floatToRawIntBits(this.value[3]);
      if (r != this.packedR || g != this.packedG || b != this.packedB || a != this.packedA) {
         this.packed = this.pack();
         this.packedR = r;
         this.packedG = g;
         this.packedB = b;
         this.packedA = a;
      }

      return this.packed;
   }

   public int rgb() {
      return this.argb() & 16777215;
   }

   public int argbWithAlpha(int alpha) {
      return clamp255(alpha) << 24 | this.argb() & 16777215;
   }

   private int pack() {
      return pack(this.value[0], this.value[1], this.value[2], this.value[3]);
   }

   private static int pack(float red, float green, float blue, float alpha) {
      int a = Math.round(clamp01(alpha) * 255.0F);
      int r = Math.round(clamp01(red) * 255.0F);
      int g = Math.round(clamp01(green) * 255.0F);
      int b = Math.round(clamp01(blue) * 255.0F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   @Override
   public JsonElement compact(JsonElement element) {
      try {
         if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return new JsonPrimitive(
               hex(pack(readFloat(object, "r", 0.0F), readFloat(object, "g", 0.0F), readFloat(object, "b", 0.0F), readFloat(object, "a", 1.0F)))
            );
         }

         if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            JsonArray array = element.getAsJsonArray();
            float alpha = array.size() > 3 ? array.get(3).getAsFloat() : 1.0F;
            return new JsonPrimitive(hex(pack(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), alpha)));
         }
      } catch (RuntimeException var4) {
      }

      return element;
   }

   public static String hex(int argb) {
      return String.format("%08X", argb);
   }

   @Override
   public JsonElement serialize() {
      JsonObject object = new JsonObject();
      object.addProperty("r", clamp01(this.value[0]));
      object.addProperty("g", clamp01(this.value[1]));
      object.addProperty("b", clamp01(this.value[2]));
      object.addProperty("a", clamp01(this.value[3]));
      return object;
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null) {
         try {
            if (element.isJsonObject()) {
               JsonObject object = element.getAsJsonObject();
               this.value[0] = readFloat(object, "r", this.value[0]);
               this.value[1] = readFloat(object, "g", this.value[1]);
               this.value[2] = readFloat(object, "b", this.value[2]);
               this.value[3] = readFloat(object, "a", this.value[3]);
               this.clampValues();
               return;
            }

            if (element.isJsonArray()) {
               JsonArray array = element.getAsJsonArray();

               for (int i = 0; i < Math.min(4, array.size()); i++) {
                  this.value[i] = clamp01(array.get(i).getAsFloat());
               }

               return;
            }

            if (element.isJsonPrimitive()) {
               JsonPrimitive primitive = element.getAsJsonPrimitive();
               if (primitive.isString()) {
                  this.setFromHex(primitive.getAsString());
               } else if (primitive.isNumber()) {
                  this.setFromArgb(primitive.getAsInt());
               }
            }
         } catch (RuntimeException var4) {
         }
      }
   }

   private void setFromHex(String raw) {
      String hex = raw.trim();
      if (hex.startsWith("#")) {
         hex = hex.substring(1);
      }

      if (hex.length() == 6) {
         hex = "FF" + hex;
      }

      if (hex.length() == 8) {
         this.setFromArgb((int)Long.parseLong(hex, 16));
      }
   }

   private void setFromArgb(int argb) {
      this.value[0] = (argb >> 16 & 0xFF) / 255.0F;
      this.value[1] = (argb >> 8 & 0xFF) / 255.0F;
      this.value[2] = (argb & 0xFF) / 255.0F;
      this.value[3] = (argb >>> 24 & 0xFF) / 255.0F;
   }

   private void clampValues() {
      for (int i = 0; i < this.value.length; i++) {
         this.value[i] = clamp01(this.value[i]);
      }
   }

   private static float readFloat(JsonObject object, String name, float fallback) {
      return !object.has(name) ? fallback : clamp01(object.get(name).getAsFloat());
   }

   private static float clamp01(float v) {
      return !Float.isNaN(v) && !Float.isInfinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 1.0F;
   }

   private static int clamp255(int value) {
      return Math.max(0, Math.min(255, value));
   }
}
