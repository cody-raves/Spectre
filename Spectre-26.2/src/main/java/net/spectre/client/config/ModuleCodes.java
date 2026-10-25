package net.spectre.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleCodes {
   private static final String PREFIX = "SpectreModule:";

   private ModuleCodes() {
   }

   public static String copy(ClientModule module) {
      JsonObject settings = new JsonObject();

      for (Setting setting : module.settings()) {
         if (!setting.isPrivate() && !setting.isDefault()) {
            settings.add(setting.id(), setting.compact(setting.serialize()));
         }
      }

      JsonObject root = new JsonObject();
      root.addProperty("module", module.id());
      if (module.settingsVersion() > 0) {
         root.addProperty("settingsVersion", module.settingsVersion());
      }

      root.add("settings", settings);
      return ProfileCodes.encode(root, "SpectreModule:");
   }

   public static void paste(ClientModule module, String text) throws ProfileException {
      if (!ProfileCodes.contains(text, "SpectreModule:") && ProfileCodes.contains(text, "SpectreProfile:")) {
         throw new ProfileException("That's a whole profile, paste it in the Config tab");
      } else {
         JsonObject root = ProfileCodes.decode(text, "SpectreModule:", "No module config on the clipboard");
         String id = root.has("module") && root.get("module").isJsonPrimitive() ? root.get("module").getAsString() : "";
         if (!module.id().equals(id)) {
            ClientModule owner = id.isEmpty() ? null : ModuleRegistry.resolve(id);
            throw new ProfileException("That config is for " + (owner != null ? owner.name() : "another module"));
         } else {
            JsonObject settings = root.has("settings") && root.get("settings").isJsonObject() ? root.getAsJsonObject("settings") : new JsonObject();

            for (Setting setting : module.settings()) {
               if (!setting.isPrivate()) {
                  JsonElement value = settings.get(setting.id());
                  if (value != null) {
                     setting.deserialize(value);
                  } else {
                     setting.resetToDefault();
                  }
               }
            }

            int current = module.settingsVersion();
            int saved = 0;
            if (root.has("settingsVersion") && root.get("settingsVersion").isJsonPrimitive()) {
               try {
                  saved = root.get("settingsVersion").getAsInt();
               } catch (RuntimeException var8) {
               }
            }

            if (current > 0 && saved < current) {
               module.migrateSettings(saved);
            }
         }
      }
   }
}
