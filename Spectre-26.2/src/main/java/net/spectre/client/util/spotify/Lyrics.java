package net.spectre.client.util.spotify;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record Lyrics(Lyrics.Status status, SyncedLyrics synced, List<String> plainLines, long sourceId) {
   public static final Lyrics LOADING = new Lyrics(Lyrics.Status.LOADING, SyncedLyrics.EMPTY, List.of(), 0L);
   public static final Lyrics NOT_FOUND = new Lyrics(Lyrics.Status.NOT_FOUND, SyncedLyrics.EMPTY, List.of(), 0L);
   public static final Lyrics UNAVAILABLE = new Lyrics(Lyrics.Status.UNAVAILABLE, SyncedLyrics.EMPTY, List.of(), 0L);

   public Lyrics(Lyrics.Status status, SyncedLyrics synced, List<String> plainLines, long sourceId) {
      if (status == null) {
         status = Lyrics.Status.NOT_FOUND;
      }

      if (synced == null) {
         synced = SyncedLyrics.EMPTY;
      }

      plainLines = plainLines == null ? List.of() : List.copyOf(plainLines);
      this.status = status;
      this.synced = synced;
      this.plainLines = plainLines;
      this.sourceId = sourceId;
   }

   public static Lyrics of(LrclibTrack track) {
      if (track == null) {
         return NOT_FOUND;
      } else {
         SyncedLyrics synced = SyncedLyrics.parse(track.syncedLyrics());
         List<String> plain = plainLines(track.plainLyrics());
         if (plain.isEmpty() && synced.isEmpty()) {
            plain = plainLines(track.syncedLyrics());
         }

         if (!synced.isEmpty() && !synced.texts().isEmpty()) {
            return new Lyrics(Lyrics.Status.SYNCED, synced, plain.isEmpty() ? synced.texts() : plain, track.id());
         } else if (!plain.isEmpty()) {
            return new Lyrics(Lyrics.Status.PLAIN, SyncedLyrics.EMPTY, plain, track.id());
         } else {
            return !track.instrumental() && synced.size() <= 0 ? NOT_FOUND : new Lyrics(Lyrics.Status.INSTRUMENTAL, SyncedLyrics.EMPTY, List.of(), track.id());
         }
      }
   }

   static List<String> plainLines(String text) {
      if (text != null && !text.isBlank()) {
         List<String> lines = new ArrayList<>();
         boolean gap = false;

         for (String raw : text.split("\r\n|\r|\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
               gap = !lines.isEmpty();
            } else {
               if (gap) {
                  lines.add("");
               }

               gap = false;
               lines.add(line);
            }
         }

         return List.copyOf(lines);
      } else {
         return List.of();
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      LOADING,
      SYNCED,
      PLAIN,
      INSTRUMENTAL,
      NOT_FOUND,
      UNAVAILABLE;
   }
}
