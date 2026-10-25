package net.spectre.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.spectre.client.input.ModuleKeybinds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class KeySetting extends Setting {
   private int value;

   public KeySetting(String name, int defaultKey) {
      super(name);
      this.value = defaultKey;
   }

   public int value() {
      return this.value;
   }

   public void setValue(int glfwKey) {
      if (this.value != glfwKey) {
         this.value = glfwKey;
         this.notifyChanged();
      }
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.value);
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            int key = element.getAsInt();
            if (key == -1 || ModuleKeybinds.isBindable(key)) {
               this.value = key;
            }
         } catch (RuntimeException var3) {
         }
      }
   }
}
