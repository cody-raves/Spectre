package net.spectre.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.spectre.client.gui.HudLayoutStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ConfigProfiles {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final ConfigProfiles INSTANCE = new ConfigProfiles();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{Nd} _()-]{1,32}");
   private static final Pattern NUMBERED = Pattern.compile("(.+) \\(\\d{1,3}\\)");
   private static final Set<String> RESERVED = Set.of(
      "con",
      "prn",
      "aux",
      "nul",
      "com1",
      "com2",
      "com3",
      "com4",
      "com5",
      "com6",
      "com7",
      "com8",
      "com9",
      "lpt1",
      "lpt2",
      "lpt3",
      "lpt4",
      "lpt5",
      "lpt6",
      "lpt7",
      "lpt8",
      "lpt9"
   );
   private static final String EXTENSION = ".json";
   private static final String FORMAT_KEY = "spectreProfile";
   private static final int FORMAT = 1;
   private static final int MAX_NAME = 32;
   private static final int MAX_FRIENDS = 1000;
   private static final long MAX_LOCAL_BYTES = 33554432L;
   public static final int MAX_SERVERS = 16;
   public static final String NAME_RULE = "Use 1-32 letters, digits, spaces, _ - ( )";
   public static final String BACKUP_NAME = "Auto backup";
   private final ModuleConfigStore store;
   private final Map<String, ConfigProfiles.Header> headers = new HashMap<>();
   private List<ConfigProfiles.Profile> cached;

   private ConfigProfiles() {
      this(ModuleConfigStore.getInstance());
   }

   private ConfigProfiles(ModuleConfigStore store) {
      this.store = store;
   }

   public static ConfigProfiles getInstance() {
      return INSTANCE;
   }

   public static boolean isValidName(String name) {
      return name != null && name.equals(name.trim()) && VALID_NAME.matcher(name).matches() && !RESERVED.contains(name.toLowerCase(Locale.ROOT));
   }

   public Path directory() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("profiles").toAbsolutePath().normalize();
   }

   public synchronized List<ConfigProfiles.Profile> list() {
      if (this.cached == null) {
         this.cached = this.scan();
      }

      return this.cached;
   }

   public synchronized void refresh() {
      this.cached = null;
   }

   public synchronized ConfigProfiles.Profile find(String name) {
      if (name == null) {
         return null;
      } else {
         for (ConfigProfiles.Profile profile : this.list()) {
            if (profile.name().equalsIgnoreCase(name)) {
               return profile;
            }
         }

         return null;
      }
   }

   public synchronized String create(String name) throws ProfileException {
      String wanted = name == null ? "" : name.trim();
      if (!isValidName(wanted)) {
         throw new ProfileException("Use 1-32 letters, digits, spaces, _ - ( )");
      } else {
         ConfigProfiles.Profile existing = this.find(wanted);
         if (existing != null) {
            throw new ProfileException(existing.name() + " already exists - use its Save button");
         } else if (Files.exists(this.file(wanted))) {
            this.cached = null;
            throw new ProfileException(wanted + " already exists - use its Save button");
         } else {
            ConfigProfiles.Contents contents = new ConfigProfiles.Contents();
            contents.data.put(ConfigProfiles.Part.MODULES, this.capture(ConfigProfiles.Part.MODULES));
            this.write(wanted, contents);
            this.activate(wanted);
            return wanted;
         }
      }
   }

   public synchronized Set<ConfigProfiles.Part> save(String name) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      ConfigProfiles.Contents contents = this.read(profile.name());
      Set<ConfigProfiles.Part> parts = contents.data.isEmpty() ? EnumSet.of(ConfigProfiles.Part.MODULES) : EnumSet.copyOf(contents.data.keySet());

      for (ConfigProfiles.Part part : parts) {
         contents.data.put(part, this.capture(part));
      }

      this.write(profile.name(), contents);
      this.activate(profile.name());
      return Collections.unmodifiableSet(parts);
   }

   public synchronized Set<ConfigProfiles.Part> load(String name) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      ConfigProfiles.Contents contents = this.read(profile.name());
      Set<ConfigProfiles.Part> applied = EnumSet.noneOf(ConfigProfiles.Part.class);

      for (Entry<ConfigProfiles.Part, JsonElement> entry : contents.data.entrySet()) {
         if (this.apply(entry.getKey(), entry.getValue())) {
            applied.add(entry.getKey());
         }
      }

      if (applied.isEmpty()) {
         throw new ProfileException("Nothing in " + profile.name() + " could be applied");
      } else {
         this.activate(profile.name());
         return Collections.unmodifiableSet(applied);
      }
   }

   public synchronized void delete(String name) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);

      try {
         Files.deleteIfExists(this.file(profile.name()));
      } catch (IOException var4) {
         LOGGER.warn("[Spectre] Failed to delete profile '{}'", profile.name(), var4);
         throw new ProfileException("Could not delete " + profile.name());
      }

      this.cached = null;
      if (profile.name().equalsIgnoreCase(this.store.activeProfile())) {
         this.store.setActiveProfile(null);
         this.store.save();
      }
   }

   public synchronized String rename(String name, String newName) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      String wanted = newName == null ? "" : newName.trim();
      if (wanted.equals(profile.name())) {
         return wanted;
      } else if (!isValidName(wanted)) {
         throw new ProfileException("Use 1-32 letters, digits, spaces, _ - ( )");
      } else {
         ConfigProfiles.Profile clash = this.find(wanted);
         boolean sameFile = clash != null && clash.name().equals(profile.name());
         if (clash != null && !sameFile) {
            throw new ProfileException(clash.name() + " already exists");
         } else {
            Path from = this.file(profile.name());
            Path to = this.file(wanted);

            try {
               if (sameFile) {
                  Path step = from.resolveSibling(profile.name() + ".rename.tmp");
                  Files.move(from, step, StandardCopyOption.REPLACE_EXISTING);

                  try {
                     Files.move(step, to);
                  } catch (IOException var11) {
                     Files.move(step, from, StandardCopyOption.REPLACE_EXISTING);
                     throw var11;
                  }
               } else {
                  Files.move(from, to);
               }
            } catch (IOException var12) {
               LOGGER.warn("[Spectre] Failed to rename profile '{}' to '{}'", new Object[]{profile.name(), wanted, var12});
               throw new ProfileException("Could not rename " + profile.name());
            }

            this.cached = null;
            if (profile.name().equalsIgnoreCase(this.store.activeProfile())) {
               this.store.setActiveProfile(wanted);
               this.store.save();
            }

            return wanted;
         }
      }
   }

   public synchronized void setPart(String name, ConfigProfiles.Part part, boolean included) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      ConfigProfiles.Contents contents = this.read(profile.name());
      if (included != contents.data.containsKey(part)) {
         if (included) {
            contents.data.put(part, this.capture(part));
         } else {
            if (contents.data.size() == 1) {
               throw new ProfileException("A profile needs at least one part");
            }

            contents.data.remove(part);
         }

         this.write(profile.name(), contents);
      }
   }

   public synchronized String addServer(String name, String address) throws ProfileException {
      ServerAddresses.Address parsed = ServerAddresses.parse(address);
      if (parsed == null) {
         throw new ProfileException("That isn't a server address");
      } else {
         ConfigProfiles.Profile profile = this.require(name);
         ConfigProfiles.Contents contents = this.read(profile.name());
         String shown = parsed.toString();
         if (contents.servers.contains(shown)) {
            throw new ProfileException(shown + " is already on the list");
         } else if (contents.servers.size() >= 16) {
            throw new ProfileException("A profile can list up to 16 servers");
         } else {
            contents.servers.add(shown);
            this.write(profile.name(), contents);
            return shown;
         }
      }
   }

   public synchronized void removeServer(String name, String address) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      ConfigProfiles.Contents contents = this.read(profile.name());
      if (contents.servers.remove(address)) {
         this.write(profile.name(), contents);
      }
   }

   public synchronized void exportTo(String name, Path target) throws ProfileException {
      JsonObject shared = this.exportJson(name);

      try {
         Files.writeString(target, GSON.toJson(shared), StandardCharsets.UTF_8);
      } catch (RuntimeException | IOException var5) {
         LOGGER.warn("[Spectre] Failed to export profile '{}' to {}", new Object[]{name, target, var5});
         throw new ProfileException("Could not write " + target.getFileName());
      }
   }

   public synchronized String shareCode(String name) throws ProfileException {
      return ProfileCodes.encode(this.exportJson(name));
   }

   public synchronized String importFile(Path source) throws ProfileException {
      String text;
      try {
         if (!Files.isRegularFile(source)) {
            throw new ProfileException("That file doesn't exist");
         }

         if (Files.size(source) > 4194304L) {
            throw new ProfileException("That file is too large to be a profile");
         }

         text = Files.readString(source, StandardCharsets.UTF_8);
      } catch (RuntimeException | IOException var6) {
         throw new ProfileException("Couldn't read " + source.getFileName());
      }

      String fileName = source.getFileName().toString();
      int dot = fileName.lastIndexOf(46);
      String fallback = dot > 0 ? fileName.substring(0, dot) : fileName;
      return this.importRoot(ProfileCodes.parseObject(text, "That file isn't an Spectre profile"), fallback);
   }

   public synchronized String importCode(String text) throws ProfileException {
      try {
         return this.importRoot(ProfileCodes.decode(text), "Shared profile");
      } catch (ProfileException var3) {
         LOGGER.warn("[Spectre] Share code import failed ({} characters on the clipboard): {}", text == null ? 0 : text.length(), var3.getMessage());
         throw var3;
      }
   }

   public synchronized void backup(Set<ConfigProfiles.Part> parts) throws ProfileException {
      ConfigProfiles.Contents contents = new ConfigProfiles.Contents();

      for (ConfigProfiles.Part part : parts) {
         contents.data.put(part, this.capture(part));
      }

      if (contents.data.isEmpty()) {
         contents.data.put(ConfigProfiles.Part.MODULES, this.capture(ConfigProfiles.Part.MODULES));
      }

      this.write("Auto backup", contents);
      this.cached = null;
   }

   public synchronized List<String> loadForServer(String address) {
      ServerAddresses.Address joined = ServerAddresses.parse(address);
      if (joined == null) {
         return List.of();
      } else {
         this.cached = null;
         List<ConfigProfiles.Match> matches = new ArrayList<>();

         for (ConfigProfiles.Profile profile : this.list()) {
            if (!profile.broken()) {
               int best = 0;
               int length = 0;

               for (String server : profile.servers()) {
                  ServerAddresses.Address entry = ServerAddresses.parse(server);
                  int score = ServerAddresses.score(entry, joined);
                  if (score > best || score == best && score > 0 && entry.host().length() > length) {
                     best = score;
                     length = entry.host().length();
                  }
               }

               if (best > 0) {
                  matches.add(new ConfigProfiles.Match(profile.name(), best, length));
               }
            }
         }

         matches.sort(
            Comparator.comparingInt(ConfigProfiles.Match::score)
               .thenComparingInt(ConfigProfiles.Match::length)
               .thenComparing(matchx -> matchx.name().toLowerCase(Locale.ROOT))
         );
         List<String> loaded = new ArrayList<>();

         for (ConfigProfiles.Match match : matches) {
            try {
               this.load(match.name());
               loaded.add(match.name());
            } catch (ProfileException var12) {
               LOGGER.warn("[Spectre] Could not load profile '{}' for {}: {}", new Object[]{match.name(), joined, var12.getMessage()});
            }
         }

         return loaded;
      }
   }

   private ConfigProfiles.Profile require(String name) throws ProfileException {
      ConfigProfiles.Profile profile = this.find(name);
      if (profile == null) {
         throw new ProfileException("That profile no longer exists");
      } else {
         return profile;
      }
   }

   private void activate(String name) {
      this.store.setActiveProfile(name);
      this.store.save();
   }

   private JsonObject exportJson(String name) throws ProfileException {
      ConfigProfiles.Profile profile = this.require(name);
      ConfigProfiles.Contents contents = this.shareable(this.read(profile.name()), "Could not prepare " + profile.name() + " for sharing");
      if (contents.data.isEmpty()) {
         throw new ProfileException(profile.name() + " has nothing to share");
      } else {
         JsonObject root = new JsonObject();
         root.addProperty("spectreProfile", 1);
         root.addProperty("name", profile.name());
         root.add("data", data(contents));
         return root;
      }
   }

   private String importRoot(JsonObject root, String fallbackName) throws ProfileException {
      ConfigProfiles.Contents contents = this.shareable(parse(root, false), "That profile is damaged");
      if (contents.data.isEmpty()) {
         throw new ProfileException("That profile has nothing Spectre can use");
      } else {
         JsonElement named = root.get("name");
         String wanted = named != null && named.isJsonPrimitive() ? named.getAsString() : fallbackName;
         String name = this.uniqueName(wanted);
         this.write(name, contents);
         return name;
      }
   }

   private JsonElement capture(ConfigProfiles.Part part) throws ProfileException {
      try {
         return (JsonElement)(switch (part) {
            case MODULES -> this.store.snapshot();
            case HUD -> {
               JsonObject hud = new JsonObject();
               hud.add("layout", HudLayoutStore.getInstance().profileSnapshot());
               hud.add("theme", HudTheme.get().profileSnapshot());
               yield hud;
            }
            case THEME -> ThemeStore.get().profileSnapshot();
            case FRIENDS -> {
               JsonArray friends = new JsonArray();

               for (String friend : FriendStore.getInstance().all()) {
                  friends.add(friend);
               }

               yield friends;
            }
         });
      } catch (RuntimeException var5) {
         LOGGER.warn("[Spectre] Failed to capture the {} part of a profile", part.key, var5);
         throw new ProfileException("Could not read your current " + part.label);
      }
   }

   private boolean apply(ConfigProfiles.Part part, JsonElement data) {
      try {
         return switch (part) {
            case MODULES -> data.isJsonObject() && this.store.applyProfile(data.getAsJsonObject());
            case HUD -> applyHud(data);
            case THEME -> {
               if (!data.isJsonObject()) {
                  yield false;
               } else {
                  ThemeStore theme = ThemeStore.get();
                  theme.applyProfile(data.getAsJsonObject());
                  theme.save();
                  yield true;
               }
            }
            case FRIENDS -> {
               if (!data.isJsonArray()) {
                  yield false;
               } else {
                  FriendStore.getInstance().replaceAll(friendNames(data.getAsJsonArray()));
                  yield true;
               }
            }
         };
      } catch (RuntimeException var4) {
         LOGGER.warn("[Spectre] Failed to apply the {} part of a profile", part.key, var4);
         return false;
      }
   }

   private static boolean applyHud(JsonElement data) {
      if (!data.isJsonObject()) {
         return false;
      } else {
         JsonObject hud = data.getAsJsonObject();
         boolean applied = false;
         if (hud.has("layout") && hud.get("layout").isJsonObject()) {
            applied = HudLayoutStore.getInstance().applyProfile(hud.getAsJsonObject("layout"));
         }

         if (hud.has("theme") && hud.get("theme").isJsonObject()) {
            HudTheme theme = HudTheme.get();
            theme.applyProfile(hud.getAsJsonObject("theme"));
            theme.save();
            applied = true;
         }

         return applied;
      }
   }

   private ConfigProfiles.Contents shareable(ConfigProfiles.Contents contents, String failure) throws ProfileException {
      ConfigProfiles.Contents kept = new ConfigProfiles.Contents();

      try {
         for (Entry<ConfigProfiles.Part, JsonElement> entry : contents.data.entrySet()) {
            JsonElement value = this.shareable(entry.getKey(), entry.getValue());
            if (value != null) {
               kept.data.put(entry.getKey(), value);
            }
         }

         return kept;
      } catch (RuntimeException var7) {
         LOGGER.warn("[Spectre] Failed to filter a profile for sharing", var7);
         throw new ProfileException(failure);
      }
   }

   private JsonElement shareable(ConfigProfiles.Part part, JsonElement data) {
      return (JsonElement)(switch (part) {
         case MODULES -> data.isJsonObject() ? this.store.shareable(data.getAsJsonObject()) : null;
         case HUD -> {
            if (!data.isJsonObject()) {
               yield null;
            } else {
               JsonObject hud = data.getAsJsonObject();
               JsonObject kept = new JsonObject();
               if (hud.has("layout") && hud.get("layout").isJsonObject()) {
                  JsonObject layout = HudLayoutStore.shareable(hud.getAsJsonObject("layout"));
                  if (layout != null) {
                     kept.add("layout", layout);
                  }
               }

               if (hud.has("theme") && hud.get("theme").isJsonObject()) {
                  kept.add("theme", HudTheme.get().shareable(hud.getAsJsonObject("theme")));
               }

               yield kept.size() == 0 ? null : kept;
            }
         }
         case THEME -> data.isJsonObject() ? ThemeStore.get().shareable(data.getAsJsonObject()) : null;
         case FRIENDS -> {
            if (!data.isJsonArray()) {
               yield null;
            } else {
               JsonArray kept = new JsonArray();

               for (String name : friendNames(data.getAsJsonArray())) {
                  kept.add(name);
               }

               yield kept;
            }
         }
      });
   }

   private static List<String> friendNames(JsonArray array) {
      Map<String, String> names = new LinkedHashMap<>();

      for (JsonElement element : array) {
         if (names.size() >= 1000) {
            break;
         }

         if (element.isJsonPrimitive()) {
            String name = element.getAsString().trim();
            if (FriendStore.isValidName(name)) {
               names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
         }
      }

      return List.copyOf(names.values());
   }

   private static ConfigProfiles.Contents parse(JsonObject root, boolean withServers) throws ProfileException {
      ConfigProfiles.Contents contents = new ConfigProfiles.Contents();
      if (root.has("spectreProfile")) {
         int format;
         try {
            format = root.get("spectreProfile").getAsInt();
         } catch (RuntimeException var10) {
            throw new ProfileException("That isn't an Spectre profile");
         }

         if (format > 1) {
            throw new ProfileException("Made by a newer Spectre - update to open it");
         }

         if (format < 1) {
            throw new ProfileException("That isn't an Spectre profile");
         }

         JsonElement data = root.get("data");
         if (data != null && data.isJsonObject()) {
            for (ConfigProfiles.Part part : ConfigProfiles.Part.values()) {
               JsonElement value = data.getAsJsonObject().get(part.key);
               if (value != null && (part == ConfigProfiles.Part.FRIENDS ? value.isJsonArray() : value.isJsonObject())) {
                  contents.data.put(part, value);
               }
            }
         }

         JsonElement servers = root.get("loadOnJoin");
         if (withServers && servers != null && servers.isJsonArray()) {
            for (JsonElement server : servers.getAsJsonArray()) {
               if (server.isJsonPrimitive()) {
                  addServer(contents.servers, server.getAsString());
               }
            }
         }
      } else {
         if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            throw new ProfileException("That isn't an Spectre profile");
         }

         contents.data.put(ConfigProfiles.Part.MODULES, root);
      }

      return contents;
   }

   private static void addServer(List<String> servers, String text) {
      ServerAddresses.Address address = ServerAddresses.parse(text);
      if (address != null && servers.size() < 16) {
         String shown = address.toString();
         if (!servers.contains(shown)) {
            servers.add(shown);
         }
      }
   }

   private static JsonObject data(ConfigProfiles.Contents contents) {
      JsonObject data = new JsonObject();

      for (Entry<ConfigProfiles.Part, JsonElement> entry : contents.data.entrySet()) {
         data.add(entry.getKey().key, entry.getValue());
      }

      return data;
   }

   private ConfigProfiles.Contents read(String name) throws ProfileException {
      Path path = this.file(name);

      try {
         if (Files.size(path) > 33554432L) {
            throw new ProfileException(name + " is too large to open");
         } else {
            ConfigProfiles.Contents var5;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
               JsonElement parsed = JsonParser.parseReader(reader);
               if (!parsed.isJsonObject()) {
                  throw new ProfileException(name + " isn't a profile file");
               }

               var5 = parse(parsed.getAsJsonObject(), true);
            }

            return var5;
         }
      } catch (RuntimeException | IOException var8) {
         LOGGER.warn("[Spectre] Failed to read profile '{}'", name, var8);
         throw new ProfileException("Could not read " + name);
      }
   }

   private void write(String name, ConfigProfiles.Contents contents) throws ProfileException {
      JsonObject root = new JsonObject();
      root.addProperty("spectreProfile", 1);
      JsonArray servers = new JsonArray();

      for (String server : contents.servers) {
         servers.add(server);
      }

      root.add("loadOnJoin", servers);
      root.add("data", data(contents));
      Path path = this.file(name);

      try {
         Files.createDirectories(path.getParent());
         Path tmp = path.resolveSibling(name + ".json.tmp");

         try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
         }

         try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException var11) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (RuntimeException | IOException var13) {
         LOGGER.warn("[Spectre] Failed to save profile '{}'", name, var13);
         throw new ProfileException("Could not save " + name);
      }

      this.cached = null;
   }

   private Path file(String name) throws ProfileException {
      if (!isValidName(name)) {
         throw new ProfileException("Use 1-32 letters, digits, spaces, _ - ( )");
      } else {
         Path dir = this.directory();
         Path path = dir.resolve(name + ".json").normalize();
         if (!dir.equals(path.getParent())) {
            throw new ProfileException("Use 1-32 letters, digits, spaces, _ - ( )");
         } else {
            return path;
         }
      }
   }

   private String uniqueName(String wanted) throws ProfileException {
      String base = cleanName(wanted);
      if (!this.taken(base)) {
         return base;
      } else {
         Matcher numbered = NUMBERED.matcher(base);
         String root = numbered.matches() ? numbered.group(1) : base;

         for (int i = 2; i < 1000; i++) {
            String suffix = " (" + i + ")";
            String candidate = cut(root, 32 - suffix.length()).stripTrailing() + suffix;
            if (isValidName(candidate) && !this.taken(candidate)) {
               return candidate;
            }
         }

         throw new ProfileException("Too many profiles share that name");
      }
   }

   private boolean taken(String name) {
      return this.find(name) != null || Files.exists(this.directory().resolve(name + ".json"));
   }

   private static String cleanName(String raw) {
      StringBuilder out = new StringBuilder();
      String text = raw == null ? "" : raw;
      int i = 0;

      while (i < text.length()) {
         int cp = text.codePointAt(i);
         i += Character.charCount(cp);
         if (Character.isLetter(cp) || Character.isDigit(cp) || "_-()".indexOf(cp) >= 0) {
            out.appendCodePoint(cp);
         } else if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            out.append(' ');
         }
      }

      String name = cut(out.toString().replaceAll(" {2,}", " ").trim(), 32).trim();
      return isValidName(name) ? name : "Imported";
   }

   private static String cut(String text, int codePoints) {
      return text.codePointCount(0, text.length()) <= codePoints ? text : text.substring(0, text.offsetByCodePoints(0, codePoints));
   }

   private List<ConfigProfiles.Profile> scan() {
      Path dir = this.directory();
      if (!Files.isDirectory(dir)) {
         this.headers.clear();
         return List.of();
      } else {
         List<ConfigProfiles.Profile> profiles = new ArrayList<>();
         Set<String> seen = new HashSet<>();

         try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
               String fileName = file.getFileName().toString();
               String name = fileName.substring(0, fileName.length() - ".json".length());
               if (isValidName(name) && Files.isRegularFile(file) && seen.add(name.toLowerCase(Locale.ROOT))) {
                  profiles.add(this.header(name, file));
               }
            }
         } catch (IOException var11) {
            LOGGER.warn("[Spectre] Failed to list profiles", var11);
         }

         Set<String> names = new HashSet<>();

         for (ConfigProfiles.Profile profile : profiles) {
            names.add(profile.name());
         }

         this.headers.keySet().retainAll(names);
         profiles.sort(Comparator.comparing(profilex -> profilex.name().toLowerCase(Locale.ROOT)));
         return List.copyOf(profiles);
      }
   }

   private ConfigProfiles.Profile header(String name, Path file) {
      try {
         BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
         ConfigProfiles.Header known = this.headers.get(name);
         if (known != null && known.modified().equals(attributes.lastModifiedTime()) && known.size() == attributes.size()) {
            return known.profile();
         } else {
            ConfigProfiles.Profile profile = readHeader(name, file);
            this.headers.put(name, new ConfigProfiles.Header(attributes.lastModifiedTime(), attributes.size(), profile));
            return profile;
         }
      } catch (IOException var6) {
         return new ConfigProfiles.Profile(name, Set.of(), List.of(), true);
      }
   }

   private static ConfigProfiles.Profile readHeader(String name, Path file) {
      Set<ConfigProfiles.Part> parts = EnumSet.noneOf(ConfigProfiles.Part.class);
      List<String> servers = new ArrayList<>();
      int format = -1;
      boolean legacy = false;

      try {
         JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8));

         try {
            reader.beginObject();

            while (reader.hasNext()) {
               String var7 = reader.nextName();
               switch (var7) {
                  case "spectreProfile":
                     format = reader.nextInt();
                     break;
                  case "loadOnJoin":
                     readServers(reader, servers);
                     break;
                  case "data":
                     readParts(reader, parts);
                     break;
                  case "modules":
                     legacy = reader.peek() == JsonToken.BEGIN_OBJECT;
                     reader.skipValue();
                     break;
                  default:
                     reader.skipValue();
               }
            }

            reader.endObject();
         } catch (Throwable var10) {
            try {
               reader.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }

         reader.close();
      } catch (RuntimeException | IOException var11) {
         LOGGER.warn("[Spectre] Failed to read profile '{}'", name, var11);
         return new ConfigProfiles.Profile(name, Set.of(), List.of(), true);
      }

      if (format < 0 && legacy) {
         return new ConfigProfiles.Profile(name, Collections.unmodifiableSet(EnumSet.of(ConfigProfiles.Part.MODULES)), List.of(), false);
      } else {
         boolean broken = format < 1 || format > 1 || parts.isEmpty();
         return new ConfigProfiles.Profile(name, Collections.unmodifiableSet(parts), List.copyOf(servers), broken);
      }
   }

   private static void readServers(JsonReader reader, List<String> servers) throws IOException {
      if (reader.peek() != JsonToken.BEGIN_ARRAY) {
         reader.skipValue();
      } else {
         reader.beginArray();

         while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) {
               addServer(servers, reader.nextString());
            } else {
               reader.skipValue();
            }
         }

         reader.endArray();
      }
   }

   private static void readParts(JsonReader reader, Set<ConfigProfiles.Part> parts) throws IOException {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
         reader.skipValue();
      } else {
         reader.beginObject();

         while (reader.hasNext()) {
            String key = reader.nextName();
            JsonToken token = reader.peek();

            for (ConfigProfiles.Part part : ConfigProfiles.Part.values()) {
               if (part.key.equals(key) && token == (part == ConfigProfiles.Part.FRIENDS ? JsonToken.BEGIN_ARRAY : JsonToken.BEGIN_OBJECT)) {
                  parts.add(part);
               }
            }

            reader.skipValue();
         }

         reader.endObject();
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Contents {
      final List<String> servers = new ArrayList<>();
      final Map<ConfigProfiles.Part, JsonElement> data = new EnumMap<>(ConfigProfiles.Part.class);
   }

   @Environment(EnvType.CLIENT)
   private record Header(FileTime modified, long size, ConfigProfiles.Profile profile) {
   }

   @Environment(EnvType.CLIENT)
   private record Match(String name, int score, int length) {
   }

   @Environment(EnvType.CLIENT)
   public static enum Part {
      MODULES("modules", "Modules"),
      HUD("hud", "HUD"),
      THEME("theme", "Theme"),
      FRIENDS("friends", "Friends");

      public final String key;
      public final String label;

      private Part(String key, String label) {
         this.key = key;
         this.label = label;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Profile(String name, Set<ConfigProfiles.Part> parts, List<String> servers, boolean broken) {
   }
}
