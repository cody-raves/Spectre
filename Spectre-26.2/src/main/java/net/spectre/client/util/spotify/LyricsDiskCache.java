package net.spectre.client.util.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LyricsDiskCache {
   private static final int FORMAT = 1;
   private static final String SUFFIX = ".json";
   private static final Pattern KEY = Pattern.compile("[0-9a-f]{16,64}");
   private static final long MAX_FILE_BYTES = 524288L;
   private final Path dir;
   private final int maxFiles;
   private final long maxBytes;

   LyricsDiskCache(Path dir, int maxFiles, long maxBytes) {
      this.dir = dir;
      this.maxFiles = maxFiles;
      this.maxBytes = maxBytes;
   }

   LyricsDiskCache.Entry read(String key) {
      Path file = this.file(key);
      if (file == null) {
         return null;
      } else {
         try {
            if (Files.size(file) > 524288L) {
               return null;
            } else {
               JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
               if (!root.isJsonObject()) {
                  return null;
               } else {
                  JsonObject object = root.getAsJsonObject();
                  if (intValue(object, "format") != 1) {
                     return null;
                  } else {
                     long fetchedAt = longValue(object, "fetchedAt");
                     if (fetchedAt <= 0L) {
                        return null;
                     } else {
                        LrclibTrack track = object.has("track") ? LrclibTrack.fromJson(object.get("track")) : null;
                        touch(file);
                        return new LyricsDiskCache.Entry(track, fetchedAt);
                     }
                  }
               }
            }
         } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | IOException var8) {
            return null;
         }
      }
   }

   void write(String key, LrclibTrack track, long fetchedAt) {
      Path file = this.file(key);
      if (file != null) {
         JsonObject object = new JsonObject();
         object.addProperty("format", 1);
         object.addProperty("fetchedAt", fetchedAt);
         if (track != null) {
            object.add("track", track.toJson());
         }

         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

         try {
            Files.createDirectories(this.dir);
            Files.writeString(tmp, object.toString(), StandardCharsets.UTF_8);

            try {
               Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var9) {
               Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var10) {
            delete(tmp);
         }
      }
   }

   void prune() {
      if (Files.isDirectory(this.dir)) {
         List<LyricsDiskCache.Stored> stored = new ArrayList<>();
         long total = 0L;

         try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir)) {
            for (Path file : files) {
               String name = file.getFileName().toString();
               if (name.endsWith(".json.tmp")) {
                  delete(file);
               } else if (name.endsWith(".json") && KEY.matcher(name.substring(0, name.length() - ".json".length())).matches()) {
                  BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                  if (attributes.isRegularFile()) {
                     stored.add(new LyricsDiskCache.Stored(file, attributes.size(), attributes.lastModifiedTime().toMillis()));
                     total += attributes.size();
                  }
               }
            }
         } catch (IOException var11) {
            return;
         }

         if (stored.size() > this.maxFiles || total > this.maxBytes) {
            stored.sort(Comparator.comparingLong(LyricsDiskCache.Stored::usedAt));
            int count = stored.size();

            for (LyricsDiskCache.Stored entry : stored) {
               if (count <= this.maxFiles && total <= this.maxBytes) {
                  break;
               }

               if (delete(entry.file())) {
                  count--;
                  total -= entry.size();
               }
            }
         }
      }
   }

   private Path file(String key) {
      return key != null && KEY.matcher(key).matches() ? this.dir.resolve(key + ".json") : null;
   }

   private static boolean delete(Path file) {
      try {
         Files.deleteIfExists(file);
         return true;
      } catch (IOException var2) {
         return false;
      }
   }

   private static void touch(Path file) {
      try {
         Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
      } catch (IOException var2) {
      }
   }

   private static int intValue(JsonObject object, String name) {
      return object.get(name) instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : -1;
   }

   private static long longValue(JsonObject object, String name) {
      return object.get(name) instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : -1L;
   }

   @Environment(EnvType.CLIENT)
   record Entry(LrclibTrack track, long fetchedAt) {
      boolean found() {
         return this.track != null;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Stored(Path file, long size, long usedAt) {
   }
}
