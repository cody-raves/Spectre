package net.spectre.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import imgui.type.ImBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BooleanSetting extends Setting {
   private final ImBoolean value;

   public BooleanSetting(String name, boolean value) {
      super(name);
      this.value = new ImBoolean(value);
   }

   public boolean get() {
      return this.value.get();
   }

   public void set(boolean newValue) {
      if (this.value.get() != newValue) {
         this.value.set(newValue);
         this.notifyChanged();
      }
   }

   public ImBoolean value() {
      return this.value;
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.value.get());
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            this.value.set(element.getAsBoolean());
         } catch (RuntimeException var3) {
         }
      }
   }
}
