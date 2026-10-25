package net.spectre.client.util.spotify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record LyricsQuery(String title, String artist, String album, int durationSeconds) {
   static final int MAX_DURATION_SECONDS = 3600;

   public LyricsQuery(String title, String artist, String album, int durationSeconds) {
      title = title == null ? "" : title.strip();
      artist = artist == null ? "" : artist.strip();
      album = album == null ? "" : album.strip();
      if (durationSeconds < 0 || durationSeconds > 3600) {
         durationSeconds = 0;
      }

      this.title = title;
      this.artist = artist;
      this.album = album;
      this.durationSeconds = durationSeconds;
   }

   public LyricsQuery withDurationMillis(long durationMs) {
      long seconds = Math.max(0L, (durationMs + 500L) / 1000L);
      return new LyricsQuery(this.title, this.artist, this.album, seconds > 3600L ? 0 : (int)seconds);
   }

   public boolean hasDuration() {
      return this.durationSeconds > 0;
   }

   public boolean searchable() {
      return !this.title.isEmpty() && !this.artist.isEmpty();
   }

   public String cacheKey() {
      String identity = squash(this.artist) + "\n" + squash(this.title) + "\n" + this.durationSeconds;

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(digest, 0, 16);
      } catch (NoSuchAlgorithmException var3) {
         throw new IllegalStateException("SHA-256 is always available", var3);
      }
   }

   private static String squash(String value) {
      return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
   }
}
