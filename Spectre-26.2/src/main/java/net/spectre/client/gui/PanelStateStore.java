package net.spectre.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import net.spectre.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PanelStateStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final PanelStateStore INSTANCE = new PanelStateStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 1;
   private final Set<String> collapsed = new LinkedHashSet<>();
   private boolean loaded;
   private boolean readOnly;

   private PanelStateStore() {
   }

   public static PanelStateStore getInstance() {
      return INSTANCE;
   }

   public synchronized boolean isCollapsed(ModuleCategory category) {
      this.ensureLoaded();
      return this.collapsed.contains(category.name());
   }

   public synchronized void setCollapsed(ModuleCategory category, boolean fold) {
      this.ensureLoaded();
      boolean changed = fold ? this.collapsed.add(category.name()) : this.collapsed.remove(category.name());
      if (changed) {
         this.save();
      }
   }

   public synchronized void load() {
      if (!this.loaded) {
         this.loaded = true;
         this.collapsed.clear();
         Path path = this.configPath();
         if (Files.isRegularFile(path)) {
            try {
               try (Reader reader = Files.newBufferedReader(path)) {
                  JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                  int version = root.has("version") ? root.get("version").getAsInt() : 0;
                  if (version > 1) {
                     this.readOnly = true;
                     LOGGER.warn("[Spectre] ClickGUI state version {} is newer than supported {}; skipping load", version, 1);
                     return;
                  }

                  if (root.has("collapsed") && root.get("collapsed").isJsonArray()) {
                     for (JsonElement element : root.getAsJsonArray("collapsed")) {
                        if (element.isJsonPrimitive()) {
                           this.collapsed.add(element.getAsString());
                        }
                     }

                     return;
                  }
               }
            } catch (RuntimeException | IOException var9) {
               LOGGER.warn("[Spectre] Failed to load the ClickGUI state; preserving the file", var9);
               this.collapsed.clear();
               this.readOnly = true;
            }
         }
      }
   }

   public synchronized void save() {
      if (!this.readOnly) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 1);
         JsonArray list = new JsonArray();

         for (String id : this.collapsed) {
            list.add(id);
         }

         root.add("collapsed", list);
         Path path = this.configPath();

         try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp)) {
               GSON.toJson(root, writer);
            }

            try {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var9) {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var11) {
            LOGGER.warn("[Spectre] Failed to save the ClickGUI state", var11);
         }
      }
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         this.load();
      }
   }

   private Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("clickgui.json");
   }
}
