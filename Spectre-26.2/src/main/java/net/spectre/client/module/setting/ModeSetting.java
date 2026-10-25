package net.spectre.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModeSetting extends Setting {
   private List<String> modes;
   private int selectedIndex;
   private String selected;

   public ModeSetting(String name, String defaultMode, String... modes) {
      super(name);
      this.modes = List.copyOf(Arrays.asList(modes));
      this.select(Math.max(0, this.modes.indexOf(defaultMode)));
   }

   public String get() {
      return this.selected;
   }

   public boolean is(String mode) {
      return this.selected == mode || this.selected.equals(mode);
   }

   public void set(String mode) {
      int idx = this.modes.indexOf(mode);
      if (idx >= 0 && idx != this.selectedIndex) {
         this.select(idx);
         this.notifyChanged();
      }
   }

   public String value() {
      return this.get();
   }

   public List<String> modes() {
      return this.modes;
   }

   public void setModes(List<String> next) {
      if (!next.isEmpty() && !next.equals(this.modes)) {
         this.modes = List.copyOf(next);
         int idx = this.modes.indexOf(this.selected);
         if (idx >= 0) {
            this.selectedIndex = idx;
         } else {
            this.select(0);
            this.notifyChanged();
         }
      }
   }

   public void next() {
      if (this.modes.size() >= 2) {
         this.select((this.selectedIndex + 1) % this.modes.size());
         this.notifyChanged();
      }
   }

   private void select(int index) {
      this.selectedIndex = index;
      this.selected = this.modes.get(index);
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.get());
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            int idx = this.modes.indexOf(element.getAsString());
            if (idx >= 0) {
               this.select(idx);
            }
         } catch (RuntimeException var3) {
         }
      }
   }
}
