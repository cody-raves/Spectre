package net.spectre.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import imgui.type.ImString;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class StringSetting extends Setting {
   private final ImString imString;

   public StringSetting(String name, String defaultValue) {
      super(name);
      this.imString = new ImString(defaultValue, 512);
   }

   public String get() {
      return this.imString.get();
   }

   public void set(String newValue) {
      if (!this.imString.get().equals(newValue)) {
         this.imString.set(newValue);
         this.notifyChanged();
      }
   }

   public String getValue() {
      return this.get();
   }

   public ImString getImString() {
      return this.imString;
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.imString.get());
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            this.imString.set(element.getAsString());
         } catch (RuntimeException var3) {
         }
      }
   }
}
