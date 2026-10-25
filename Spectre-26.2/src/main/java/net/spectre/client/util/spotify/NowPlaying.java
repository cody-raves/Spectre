package net.spectre.client.util.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record NowPlaying(
   NowPlaying.Status status,
   String title,
   String artist,
   String album,
   NowPlaying.Source source,
   long durationMs,
   long positionMs,
   long sampledAtMs,
   double rate,
   AlbumArt art
) {
   private static final String SEPARATOR = " - ";
   static final long LYRICS_MIN_LENGTH_MS = 30000L;
   static final long LYRICS_MAX_LENGTH_MS = 3600000L;
   public static final NowPlaying UNSUPPORTED = idle(NowPlaying.Status.UNSUPPORTED);
   public static final NowPlaying WAITING = idle(NowPlaying.Status.WAITING);
   public static final NowPlaying NOT_RUNNING = idle(NowPlaying.Status.NOT_RUNNING);

   public NowPlaying(
      NowPlaying.Status status,
      String title,
      String artist,
      String album,
      NowPlaying.Source source,
      long durationMs,
      long positionMs,
      long sampledAtMs,
      double rate,
      AlbumArt art
   ) {
      if (status == null) {
         status = NowPlaying.Status.NOT_RUNNING;
      }

      if (source == null) {
         source = NowPlaying.Source.NONE;
      }

      title = title == null ? "" : title;
      artist = artist == null ? "" : artist;
      album = album == null ? "" : album;
      durationMs = Math.max(0L, durationMs);
      positionMs = durationMs > 0L ? Math.max(0L, Math.min(positionMs, durationMs)) : Math.max(0L, positionMs);
      if (!(rate > 0.0) || rate > 16.0) {
         rate = 1.0;
      }

      this.status = status;
      this.title = title;
      this.artist = artist;
      this.album = album;
      this.source = source;
      this.durationMs = durationMs;
      this.positionMs = positionMs;
      this.sampledAtMs = sampledAtMs;
      this.rate = rate;
      this.art = art;
   }

   public NowPlaying(NowPlaying.Status status, String title, String artist) {
      this(status, title, artist, "", NowPlaying.Source.WINDOW, 0L, 0L, 0L, 1.0, null);
   }

   private static NowPlaying idle(NowPlaying.Status status) {
      return new NowPlaying(status, "", "", "", NowPlaying.Source.NONE, 0L, 0L, 0L, 1.0, null);
   }

   public boolean playing() {
      return this.status == NowPlaying.Status.PLAYING;
   }

   public boolean hasTrack() {
      return !this.title.isEmpty();
   }

   public boolean active() {
      return this.status == NowPlaying.Status.PLAYING || this.status == NowPlaying.Status.PAUSED;
   }

   public boolean hasTimeline() {
      return this.source == NowPlaying.Source.SESSION && this.durationMs > 0L;
   }

   public boolean lyricsEligible() {
      if (!this.hasTimeline() || !this.hasTrack() || this.artist.isBlank()) {
         return false;
      } else {
         return this.durationMs >= 30000L && this.durationMs <= 3600000L
            ? !this.artist.equalsIgnoreCase("Spotify") && !this.title.equalsIgnoreCase("Advertisement")
            : false;
      }
   }

   public boolean sameTrack(NowPlaying other) {
      return other != null && this.title.equals(other.title) && this.artist.equals(other.artist) && this.album.equals(other.album);
   }

   public long positionAt(long nowMs) {
      if (!this.hasTimeline()) {
         return 0L;
      } else if (!this.playing()) {
         return this.positionMs;
      } else {
         long advanced = this.positionMs + (long)(Math.max(0L, nowMs - this.sampledAtMs) * this.rate);
         return Math.min(advanced, this.durationMs);
      }
   }

   public NowPlaying toggled() {
      long now = System.currentTimeMillis();

      return switch (this.status) {
         case PAUSED -> this.hasTrack()
            ? new NowPlaying(
               NowPlaying.Status.PLAYING, this.title, this.artist, this.album, this.source, this.durationMs, this.positionMs, now, this.rate, this.art
            )
            : this;
         case PLAYING -> new NowPlaying(
            NowPlaying.Status.PAUSED, this.title, this.artist, this.album, this.source, this.durationMs, this.positionAt(now), now, this.rate, this.art
         );
         default -> this;
      };
   }

   public static NowPlaying fromSession(MediaSession.Snapshot snapshot, AlbumArt art, NowPlaying previous) {
      NowPlaying.Status status = switch (snapshot.state()) {
         case PLAYING -> NowPlaying.Status.PLAYING;
         case CLOSED -> NowPlaying.Status.NOT_RUNNING;
         case CHANGING -> previous != null && previous.source == NowPlaying.Source.SESSION && previous.playing()
            ? NowPlaying.Status.PLAYING
            : NowPlaying.Status.PAUSED;
         default -> NowPlaying.Status.PAUSED;
      };
      return status == NowPlaying.Status.NOT_RUNNING
         ? NOT_RUNNING
         : new NowPlaying(
            status,
            snapshot.title(),
            snapshot.artist(),
            snapshot.album(),
            NowPlaying.Source.SESSION,
            snapshot.durationMs(),
            snapshot.positionMs(),
            snapshot.sampledAtMs(),
            snapshot.rate(),
            art
         );
   }

   public static NowPlaying fromWindowTitle(String windowTitle, NowPlaying previous) {
      if (windowTitle == null) {
         return NOT_RUNNING;
      } else {
         String title = windowTitle.strip();
         if (title.isEmpty()) {
            return NOT_RUNNING;
         } else {
            int split = title.indexOf(" - ");
            if (split < 0 && (title.equals("Spotify") || title.startsWith("Spotify "))) {
               return previous != null && previous.hasTrack()
                  ? new NowPlaying(NowPlaying.Status.PAUSED, previous.title, previous.artist)
                  : new NowPlaying(NowPlaying.Status.PAUSED, "", "");
            } else {
               return split > 0
                  ? new NowPlaying(NowPlaying.Status.PLAYING, title.substring(split + " - ".length()).strip(), title.substring(0, split).strip())
                  : new NowPlaying(NowPlaying.Status.PLAYING, title, "");
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Source {
      NONE,
      WINDOW,
      SESSION;
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      UNSUPPORTED,
      WAITING,
      NOT_RUNNING,
      PAUSED,
      PLAYING;
   }
}
