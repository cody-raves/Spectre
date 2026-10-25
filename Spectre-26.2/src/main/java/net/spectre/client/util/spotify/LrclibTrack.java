package net.spectre.client.util.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record LrclibTrack(
   long id, String trackName, String artistName, String albumName, double duration, boolean instrumental, String plainLyrics, String syncedLyrics
) {
   public LrclibTrack(
      long id, String trackName, String artistName, String albumName, double duration, boolean instrumental, String plainLyrics, String syncedLyrics
   ) {
      trackName = nonNull(trackName);
      artistName = nonNull(artistName);
      albumName = nonNull(albumName);
      plainLyrics = nonNull(plainLyrics);
      syncedLyrics = nonNull(syncedLyrics);
      if (!Double.isFinite(duration) || duration < 0.0) {
         duration = 0.0;
      }

      this.id = id;
      this.trackName = trackName;
      this.artistName = artistName;
      this.albumName = albumName;
      this.duration = duration;
      this.instrumental = instrumental;
      this.plainLyrics = plainLyrics;
      this.syncedLyrics = syncedLyrics;
   }

   public boolean hasSynced() {
      return !this.syncedLyrics.isBlank();
   }

   public boolean hasPlain() {
      return !this.plainLyrics.isBlank();
   }

   public boolean usable() {
      return this.instrumental || this.hasSynced() || this.hasPlain();
   }

   public static LrclibTrack fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         return new LrclibTrack(
            number(object, "id", 0L),
            text(object, "trackName"),
            text(object, "artistName"),
            text(object, "albumName"),
            number(object, "duration", 0.0),
            bool(object, "instrumental"),
            text(object, "plainLyrics"),
            text(object, "syncedLyrics")
         );
      } else {
         return null;
      }
   }

   public static List<LrclibTrack> listFromJson(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         List<LrclibTrack> tracks = new ArrayList<>(array.size());

         for (JsonElement item : array) {
            LrclibTrack track = fromJson(item);
            if (track != null) {
               tracks.add(track);
            }
         }

         return List.copyOf(tracks);
      } else {
         return List.of();
      }
   }

   public static JsonElement parse(String json) {
      return JsonParser.parseString(json);
   }

   public JsonObject toJson() {
      JsonObject object = new JsonObject();
      object.addProperty("id", this.id);
      object.addProperty("trackName", this.trackName);
      object.addProperty("artistName", this.artistName);
      object.addProperty("albumName", this.albumName);
      object.addProperty("duration", this.duration);
      object.addProperty("instrumental", this.instrumental);
      object.addProperty("plainLyrics", this.plainLyrics);
      object.addProperty("syncedLyrics", this.syncedLyrics);
      return object;
   }

   private static String nonNull(String value) {
      return value == null ? "" : value;
   }

   private static String text(JsonObject object, String name) {
      return object.get(name) instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : "";
   }

   private static long number(JsonObject object, String name, long fallback) {
      if (object.get(name) instanceof JsonPrimitive primitive && primitive.isNumber()) {
         try {
            return primitive.getAsLong();
         } catch (NumberFormatException var7) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static double number(JsonObject object, String name, double fallback) {
      return object.get(name) instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsDouble() : fallback;
   }

   private static boolean bool(JsonObject object, String name) {
      return object.get(name) instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
   }
}
