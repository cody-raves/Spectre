package net.spectre.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.spectre.client.SpectreClientMod;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class HudLayoutStore {
   public static final String INFO_BAR_ID = "info_bar";
   public static final String COORDINATES_ID = "coordinates";
   public static final String POTION_TRACKER_ID = "potion_tracker";
   public static final String MODULE_LIST_ID = "module_list";
   public static final String REGION_MAP_ID = "region_map";
   public static final String ANTI_VANISH_ID = "anti_vanish";
   public static final String STAFF_LIST_ID = "staff_list";
   public static final String SPOTIFY_ID = "spotify";
   public static final String RADAR_ID = "radar";
   public static final String HOTKEYS_ID = "hotkeys";
   public static final String TARGET_CARD_ID = "target_card";
   public static final String ARMOR_STATUS_ID = "armor_status";
   public static final String ITEM_COOLDOWNS_ID = "item_cooldowns";
   private static final HudLayoutStore INSTANCE = new HudLayoutStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 11;
   private static final float DEFAULT_MARGIN = 10.0F;
   private static final float DEFAULT_GAP = 6.0F;
   private static final float CENTER_TOLERANCE = 24.0F;
   static final int ANCHOR_LEFT = 0;
   static final int ANCHOR_CENTER = 1;
   static final int ANCHOR_RIGHT = 2;
   private static final String[] KNOWN_IDS = new String[]{
      "info_bar",
      "coordinates",
      "module_list",
      "potion_tracker",
      "region_map",
      "anti_vanish",
      "staff_list",
      "spotify",
      "hotkeys",
      "radar",
      "target_card",
      "armor_status",
      "item_cooldowns"
   };
   private final Map<String, HudLayoutStore.HudElementState> elements = new LinkedHashMap<>();
   private boolean loaded;

   private HudLayoutStore() {
   }

   public static HudLayoutStore getInstance() {
      return INSTANCE;
   }

   synchronized HudLayoutStore.HudElementState element(String id) {
      this.loadIfNeeded();
      HudLayoutStore.HudElementState state = this.elements.get(id);
      if (state == null) {
         state = defaultElement(id);
         this.elements.put(id, state);
      }

      return state;
   }

   synchronized List<HudLayoutStore.HudElementState> orderedElements() {
      this.loadIfNeeded();
      return List.copyOf(this.elements.values());
   }

   synchronized void updateBounds(String id, float width, float height, float screenW, float screenH) {
      HudLayoutStore.HudElementState state = this.element(id);
      float newW = Math.max(1.0F, width);
      float newH = Math.max(1.0F, height);
      if (state.positioned && state.autoPlaced && screenW > 0.0F && screenH > 0.0F && dockMoved(state, screenW, screenH)) {
         state.positioned = false;
      }

      if (state.positioned && !(screenW <= 0.0F) && !(screenH <= 0.0F)) {
         if (!state.anchored) {
            this.adoptLoadedPosition(state, screenW, screenH);
         }

         rebaseToScreen(state, screenW, screenH);
         float dw = state.width - newW;
         if (state.anchorX == 2) {
            state.x += dw;
         } else if (state.anchorX == 1) {
            state.x += dw * 0.5F;
         }

         if (state.anchorBottom) {
            state.y = state.y + (state.height - newH);
         }

         state.width = newW;
         state.height = newH;
         clampToScreen(state, screenW, screenH);
      } else {
         state.width = newW;
         state.height = newH;
         if (!state.positioned && screenW > 0.0F && screenH > 0.0F && !isCollapsed(state)) {
            this.placeDefault(state, screenW, screenH);
         }
      }
   }

   private void adoptLoadedPosition(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      if (state.refScreenW > 0.0F && state.refScreenH > 0.0F) {
         state.anchored = true;
      } else {
         if ("info_bar".equals(state.id)) {
            state.x = (screenW - state.width) * 0.5F;
         } else if (state.x > screenW - state.width || state.y > screenH - state.height) {
            this.placeDefault(state, screenW, screenH);
            return;
         }

         computeAnchor(state, screenW, screenH);
      }
   }

   private static void rebaseToScreen(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      float dx = screenW - state.refScreenW;
      float dy = screenH - state.refScreenH;
      if (dx != 0.0F || dy != 0.0F) {
         if (state.anchorX == 2) {
            state.x += dx;
         } else if (state.anchorX == 1) {
            state.x += dx * 0.5F;
         }

         if (state.anchorBottom) {
            state.y += dy;
         }

         state.refScreenW = screenW;
         state.refScreenH = screenH;
      }
   }

   synchronized void moveTo(String id, float x, float y, float screenW, float screenH) {
      HudLayoutStore.HudElementState state = this.element(id);
      state.x = x;
      state.y = y;
      state.positioned = true;
      state.autoPlaced = false;
      clampToScreen(state, screenW, screenH);
      computeAnchor(state, screenW, screenH);
   }

   private static void computeAnchor(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      float cx = state.x + state.width * 0.5F;
      if (Math.abs(cx - screenW * 0.5F) <= 24.0F * uiScale()) {
         state.anchorX = 1;
      } else {
         state.anchorX = cx > screenW * 0.5F ? 2 : 0;
      }

      state.anchorBottom = state.y + state.height * 0.5F > screenH * 0.5F;
      state.refScreenW = screenW;
      state.refScreenH = screenH;
      state.anchored = true;
   }

   private static float uiScale() {
      return ImGuiManager.getInstance().uiScale();
   }

   synchronized void resetToDefaults(float screenW, float screenH) {
      Map<String, HudLayoutStore.Dimensions> currentSizes = new LinkedHashMap<>();

      for (String id : KNOWN_IDS) {
         currentSizes.put(id, new HudLayoutStore.Dimensions(this.existingWidth(id, fallbackWidth(id)), this.existingHeight(id, 26.0F)));
      }

      this.elements.clear();

      for (String id : KNOWN_IDS) {
         HudLayoutStore.HudElementState state = defaultElement(id);
         HudLayoutStore.Dimensions size = currentSizes.get(id);
         state.width = size.width();
         state.height = size.height();
         this.elements.put(state.id, state);
      }

      for (HudLayoutStore.HudElementState state : this.elements.values()) {
         if (!isCollapsed(state)) {
            this.placeDefault(state, screenW, screenH);
            clampToScreen(state, screenW, screenH);
         }
      }

      this.save();
   }

   public synchronized JsonObject profileSnapshot() {
      this.loadIfNeeded();
      return GSON.toJsonTree(this.buildConfigFile()).getAsJsonObject();
   }

   public synchronized boolean applyProfile(JsonObject layout) {
      this.loadIfNeeded();
      HudLayoutStore.ConfigFile config = parse(layout);
      if (config == null) {
         return false;
      } else {
         this.applyConfig(config);
         this.save();
         return true;
      }
   }

   public static JsonObject shareable(JsonObject layout) {
      HudLayoutStore.ConfigFile config = parse(layout);
      return config == null ? null : GSON.toJsonTree(config).getAsJsonObject();
   }

   private static HudLayoutStore.ConfigFile parse(JsonObject layout) {
      HudLayoutStore.ConfigFile config;
      try {
         config = (HudLayoutStore.ConfigFile)GSON.fromJson(layout, HudLayoutStore.ConfigFile.class);
      } catch (RuntimeException var5) {
         return null;
      }

      if (config != null && config.elements != null) {
         List<HudLayoutStore.HudElementState> kept = new ArrayList<>();

         for (HudLayoutStore.HudElementState state : config.elements) {
            if (state != null && state.id != null && isKnownId(state.id) && finite(state)) {
               kept.add(state);
            }
         }

         config.elements = kept;
         return config;
      } else {
         return null;
      }
   }

   private static boolean finite(HudLayoutStore.HudElementState state) {
      return finite(state.x)
         && finite(state.y)
         && finite(state.width)
         && finite(state.height)
         && finite(state.scale)
         && finite(state.refScreenW)
         && finite(state.refScreenH)
         && finite(state.placedGuiScale)
         && finite(state.placedUiScale);
   }

   public synchronized void save() {
      this.loadIfNeeded();
      HudLayoutStore.ConfigFile config = this.buildConfigFile();
      Path path = configPath();

      try {
         Files.createDirectories(path.getParent());
         Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

         try (Writer writer = Files.newBufferedWriter(tmp)) {
            GSON.toJson(config, writer);
         }

         try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException var8) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var10) {
         SpectreClientMod.LOGGER.warn("[Spectre] Failed to save HUD layout config", var10);
      }
   }

   private void loadIfNeeded() {
      if (!this.loaded) {
         this.loaded = true;
         this.installDefaults();
         Path path = configPath();
         if (Files.isRegularFile(path)) {
            try {
               try (Reader reader = Files.newBufferedReader(path)) {
                  HudLayoutStore.ConfigFile config = (HudLayoutStore.ConfigFile)GSON.fromJson(reader, HudLayoutStore.ConfigFile.class);
                  if (config != null && config.elements != null) {
                     this.applyConfig(config);
                     return;
                  }
               }
            } catch (RuntimeException | IOException var7) {
               SpectreClientMod.LOGGER.warn("[Spectre] Failed to load HUD layout config; using defaults", var7);
               this.elements.clear();
               this.installDefaults();
            }
         }
      }
   }

   private HudLayoutStore.ConfigFile buildConfigFile() {
      HudLayoutStore.ConfigFile config = new HudLayoutStore.ConfigFile();
      config.version = 11;
      config.elements = new ArrayList<>();

      for (HudLayoutStore.HudElementState state : this.elements.values()) {
         if (state.positioned) {
            config.elements.add(state);
         }
      }

      return config;
   }

   private void applyConfig(HudLayoutStore.ConfigFile config) {
      this.elements.clear();
      this.installDefaults();
      boolean oldLayout = config.version < 11;

      for (HudLayoutStore.HudElementState loadedState : config.elements) {
         if (loadedState != null && loadedState.id != null && !loadedState.id.isBlank() && isKnownId(loadedState.id)) {
            normalize(loadedState);
            this.elements.put(loadedState.id, loadedState);
         }
      }

      this.ensureKnownElements();
      if (oldLayout) {
         this.resetPositionsForMigration();
      }
   }

   private void installDefaults() {
      for (String id : KNOWN_IDS) {
         this.elements.put(id, defaultElement(id));
      }
   }

   private void ensureKnownElements() {
      for (String id : KNOWN_IDS) {
         this.elements.computeIfAbsent(id, HudLayoutStore::defaultElement);
      }
   }

   private void resetPositionsForMigration() {
      for (String id : KNOWN_IDS) {
         HudLayoutStore.HudElementState state = this.elements.get(id);
         if (state != null) {
            state.positioned = false;
         }
      }
   }

   private static HudLayoutStore.HudElementState defaultElement(String id) {
      HudLayoutStore.HudElementState state = new HudLayoutStore.HudElementState();
      state.id = id;
      state.x = 0.0F;
      state.y = 0.0F;
      state.width = fallbackWidth(id);
      state.height = 26.0F;
      state.visible = true;
      state.scale = 1.0F;
      state.positioned = false;
      return state;
   }

   private static void normalize(HudLayoutStore.HudElementState state) {
      state.scale = finite(state.scale) ? clamp(state.scale, 0.5F, 3.0F) : 1.0F;
      state.width = finite(state.width) && state.width > 0.0F ? state.width : 1.0F;
      state.height = finite(state.height) && state.height > 0.0F ? state.height : 1.0F;
      state.positioned = finite(state.x) && finite(state.y);
      if (state.anchorX < 0 || state.anchorX > 2 || !finite(state.refScreenW) || !finite(state.refScreenH)) {
         state.refScreenW = 0.0F;
         state.refScreenH = 0.0F;
      }
   }

   private void placeDefault(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      float margin = Math.round(10.0F * uiScale());
      float gap = Math.round(6.0F * uiScale());
      float coordsBottom = margin + this.existingHeight("coordinates", 28.0F) + gap;
      String var7 = state.id;
      switch (var7) {
         case "potion_tracker":
            state.x = Math.max(margin, screenW - margin - Math.max(1.0F, state.width));
            state.y = Math.max(margin, screenH - margin - Math.max(1.0F, state.height));
            break;
         case "info_bar":
            state.x = Math.max(margin, (screenW - state.width) * 0.5F);
            state.y = margin;
            break;
         case "module_list":
            state.x = Math.max(margin, screenW - margin - Math.max(1.0F, state.width));
            state.y = margin;
            break;
         case "region_map":
            state.x = margin;
            state.y = this.firstFreeY(state, coordsBottom, gap);
            break;
         case "anti_vanish":
            state.x = Math.max(margin, (screenW - state.width) * 0.5F);
            state.y = margin + this.existingHeight("info_bar", 28.0F) + gap;
            break;
         case "spotify":
            state.x = margin;
            state.y = this.firstFreeY(state, coordsBottom, gap);
            break;
         case "staff_list":
            state.x = margin;
            state.y = this.firstFreeY(state, Math.round(screenH * 0.38F), gap);
            break;
         case "hotkeys":
            state.x = Math.max(margin, screenW - margin - Math.max(1.0F, state.width));
            state.y = this.firstFreeY(state, Math.round(screenH * 0.36F), gap);
            break;
         case "radar":
            state.x = Math.max(margin, screenW - margin - Math.max(1.0F, state.width));
            state.y = this.firstFreeY(state, Math.round(screenH * 0.58F), gap);
            break;
         case "target_card":
            state.x = Math.round(screenW * 0.5F + 44.0F * uiScale());
            state.y = this.firstFreeY(state, Math.round(screenH * 0.5F + 30.0F * uiScale()), gap);
            break;
         case "armor_status":
            state.x = Math.round(screenW * 0.5F + 119.0F * vanillaGuiScale());
            state.y = Math.max(margin, screenH - margin - Math.max(1.0F, state.height));
            break;
         case "item_cooldowns":
            state.x = Math.max(margin, (float)Math.round((screenW - state.width) * 0.5F));
            state.y = Math.max(margin, screenH - Math.round(76.0F * vanillaGuiScale()) - Math.max(1.0F, state.height));
            break;
         default:
            state.x = margin;
            state.y = margin;
      }

      state.positioned = true;
      clampToScreen(state, screenW, screenH);
      computeAnchor(state, screenW, screenH);
      state.autoPlaced = followsVanillaHud(state.id);
      if (state.autoPlaced) {
         state.anchorX = "item_cooldowns".equals(state.id) ? 1 : 0;
         state.placedGuiScale = vanillaGuiScale();
         state.placedUiScale = uiScale();
      }
   }

   private static boolean followsVanillaHud(String id) {
      return "target_card".equals(id) || "armor_status".equals(id) || "item_cooldowns".equals(id);
   }

   private static boolean dockMoved(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      return screenW != state.refScreenW || screenH != state.refScreenH || vanillaGuiScale() != state.placedGuiScale || uiScale() != state.placedUiScale;
   }

   private static float fallbackWidth(String id) {
      return switch (id) {
         case "module_list" -> 170.0F;
         case "info_bar" -> 290.0F;
         case "coordinates" -> 210.0F;
         case "potion_tracker" -> 190.0F;
         case "region_map" -> 240.0F;
         case "anti_vanish" -> 180.0F;
         case "staff_list" -> 190.0F;
         case "spotify" -> 320.0F;
         case "hotkeys" -> 190.0F;
         case "radar" -> 150.0F;
         case "target_card" -> 244.0F;
         case "armor_status" -> 120.0F;
         case "item_cooldowns" -> 80.0F;
         default -> 110.0F;
      };
   }

   private static float vanillaGuiScale() {
      return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
   }

   private float firstFreeY(HudLayoutStore.HudElementState state, float startY, float gap) {
      float y = startY;
      float w = Math.max(1.0F, state.width);
      float h = Math.max(1.0F, state.height);

      for (int pass = 0; pass < this.elements.size(); pass++) {
         boolean moved = false;

         for (HudLayoutStore.HudElementState other : this.elements.values()) {
            if (other != state && other.positioned && !isCollapsed(other)) {
               boolean overlaps = state.x < other.x + other.width && other.x < state.x + w && y < other.y + other.height && other.y < y + h;
               if (overlaps) {
                  y = other.y + other.height + gap;
                  moved = true;
               }
            }
         }

         if (!moved) {
            break;
         }
      }

      return y;
   }

   private static boolean isCollapsed(HudLayoutStore.HudElementState state) {
      return state.width <= 1.0F;
   }

   private static void clampToScreen(HudLayoutStore.HudElementState state, float screenW, float screenH) {
      float maxX = Math.max(0.0F, screenW - Math.max(1.0F, state.width));
      float maxY = Math.max(0.0F, screenH - Math.max(1.0F, state.height));
      state.x = clamp(state.x, 0.0F, maxX);
      state.y = clamp(state.y, 0.0F, maxY);
   }

   private float existingWidth(String id, float fallback) {
      HudLayoutStore.HudElementState state = this.elements.get(id);
      return state != null && finite(state.width) && state.width > 0.0F ? state.width : fallback;
   }

   private float existingHeight(String id, float fallback) {
      HudLayoutStore.HudElementState state = this.elements.get(id);
      return state != null && finite(state.height) && state.height > 0.0F ? state.height : fallback;
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("hud.json");
   }

   private static boolean isKnownId(String id) {
      for (String k : KNOWN_IDS) {
         if (k.equals(id)) {
            return true;
         }
      }

      return false;
   }

   private static boolean finite(float value) {
      return !Float.isNaN(value) && !Float.isInfinite(value);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   @Environment(EnvType.CLIENT)
   private static final class ConfigFile {
      int version;
      List<HudLayoutStore.HudElementState> elements;
   }

   @Environment(EnvType.CLIENT)
   private record Dimensions(float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   static final class HudElementState {
      String id;
      float x;
      float y;
      float width;
      float height;
      boolean visible;
      float scale;
      transient boolean positioned;
      int anchorX;
      boolean anchorBottom;
      float refScreenW;
      float refScreenH;
      transient boolean anchored;
      boolean autoPlaced;
      float placedGuiScale;
      float placedUiScale;
   }
}
