package net.spectre.client.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.spectre.client.module.ModuleRevision;
import net.spectre.client.module.StableId;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public abstract class Setting {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private final String name;
   private String id;
   private boolean idBound;
   private long revision;
   private final List<Runnable> changeListeners = new ArrayList<>(1);
   private BooleanSupplier visibility;
   private boolean privateValue;
   private JsonElement defaultValue;
   private long defaultCheckedRevision = -1L;
   private boolean defaultCheckResult = true;

   protected Setting(String name) {
      this.name = name;
      this.id = StableId.legacy(name);
   }

   public final String id() {
      return this.id;
   }

   public final long revision() {
      return this.revision;
   }

   public final void bindId(String id) {
      if (this.idBound) {
         throw new IllegalStateException("Setting id already bound");
      } else {
         this.id = StableId.validate(id);
         this.idBound = true;
      }
   }

   public final String name() {
      return this.name;
   }

   public final Setting onChange(Runnable listener) {
      this.changeListeners.add(listener);
      return this;
   }

   public final void notifyChanged() {
      this.revision++;

      for (int i = 0; i < this.changeListeners.size(); i++) {
         Runnable listener = this.changeListeners.get(i);

         try {
            if (listener == ModuleRevision.CHANGED) {
               ModuleRevision.changed();
            } else {
               listener.run();
            }
         } catch (Throwable var4) {
            LOGGER.error("Setting change listener failed for '{}'", this.name, var4);
         }
      }
   }

   public final Setting visibleWhen(BooleanSupplier condition) {
      this.visibility = condition;
      return this;
   }

   public final boolean isVisible() {
      return this.visibility == null || this.visibility.getAsBoolean();
   }

   public final Setting privateValue() {
      this.privateValue = true;
      return this;
   }

   public final boolean isPrivate() {
      return this.privateValue;
   }

   public final void captureDefault() {
      if (this.defaultValue == null) {
         this.defaultValue = this.serialize();
      }
   }

   public final boolean isDefault() {
      if (this.defaultValue == null) {
         return true;
      } else {
         if (this.defaultCheckedRevision != this.revision) {
            this.defaultCheckResult = this.defaultValue.equals(this.serialize());
            this.defaultCheckedRevision = this.revision;
         }

         return this.defaultCheckResult;
      }
   }

   public final boolean isDefault(JsonElement value) {
      return this.defaultValue != null && sameValue(this.defaultValue, value);
   }

   public static boolean sameValue(JsonElement a, JsonElement b) {
      if (a == null || b == null) {
         return a == b;
      } else if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
         JsonPrimitive left = a.getAsJsonPrimitive();
         JsonPrimitive right = b.getAsJsonPrimitive();
         if (left.isNumber() && right.isNumber()) {
            try {
               return left.getAsBigDecimal().compareTo(right.getAsBigDecimal()) == 0;
            } catch (NumberFormatException var6) {
               return false;
            }
         } else {
            return left.equals(right);
         }
      } else if (a.isJsonArray() && b.isJsonArray()) {
         JsonArray left = a.getAsJsonArray();
         JsonArray right = b.getAsJsonArray();
         if (left.size() != right.size()) {
            return false;
         } else {
            for (int i = 0; i < left.size(); i++) {
               if (!sameValue(left.get(i), right.get(i))) {
                  return false;
               }
            }

            return true;
         }
      } else if (a.isJsonObject() && b.isJsonObject()) {
         JsonObject left = a.getAsJsonObject();
         JsonObject right = b.getAsJsonObject();
         if (!left.keySet().equals(right.keySet())) {
            return false;
         } else {
            for (String key : left.keySet()) {
               if (!sameValue(left.get(key), right.get(key))) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return a.equals(b);
      }
   }

   public final void resetToDefault() {
      if (!this.isDefault()) {
         this.deserialize(this.defaultValue);
      }
   }

   public JsonElement compact(JsonElement value) {
      return value;
   }

   protected final JsonElement defaultJson() {
      return this.defaultValue;
   }

   public abstract JsonElement serialize();

   public final void deserialize(JsonElement element) {
      this.deserializeValue(element);
      this.notifyChanged();
   }

   protected abstract void deserializeValue(JsonElement var1);
}
