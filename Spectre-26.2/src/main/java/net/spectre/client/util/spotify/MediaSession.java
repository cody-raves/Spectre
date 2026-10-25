package net.spectre.client.util.spotify;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MediaSession {
   private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
   private static final GUID IID_MANAGER_STATICS = WinRt.iid("2050C4EE-11A0-57DE-AED7-C97C70338245");
   private static final int STATICS_REQUEST_ASYNC = 6;
   private static final int MANAGER_GET_CURRENT_SESSION = 6;
   private static final int MANAGER_GET_SESSIONS = 7;
   private static final int VECTOR_VIEW_GET_AT = 6;
   private static final int VECTOR_VIEW_GET_SIZE = 7;
   private static final int SESSION_GET_SOURCE_APP_USER_MODEL_ID = 6;
   private static final int SESSION_TRY_GET_MEDIA_PROPERTIES_ASYNC = 7;
   private static final int SESSION_GET_TIMELINE_PROPERTIES = 8;
   private static final int SESSION_GET_PLAYBACK_INFO = 9;
   private static final int SESSION_TRY_SKIP_NEXT_ASYNC = 16;
   private static final int SESSION_TRY_SKIP_PREVIOUS_ASYNC = 17;
   private static final int SESSION_TRY_TOGGLE_PLAY_PAUSE_ASYNC = 20;
   private static final int PROPERTIES_GET_TITLE = 6;
   private static final int PROPERTIES_GET_ARTIST = 9;
   private static final int PROPERTIES_GET_ALBUM_TITLE = 10;
   private static final int PROPERTIES_GET_THUMBNAIL = 15;
   private static final int TIMELINE_GET_START_TIME = 6;
   private static final int TIMELINE_GET_END_TIME = 7;
   private static final int TIMELINE_GET_POSITION = 10;
   private static final int TIMELINE_GET_LAST_UPDATED_TIME = 11;
   private static final int PLAYBACK_INFO_GET_PLAYBACK_STATUS = 7;
   private static final int PLAYBACK_INFO_GET_PLAYBACK_RATE = 10;
   private static final int REFERENCE_GET_VALUE = 6;
   private static final int STREAM_REFERENCE_OPEN_READ_ASYNC = 6;
   private static final GUID IID_RANDOM_ACCESS_STREAM = WinRt.iid("905A0FE1-BC53-11DF-8C49-001E4FC686DA");
   private static final int RANDOM_ACCESS_STREAM_GET_SIZE = 6;
   private static final GUID IID_INPUT_STREAM = WinRt.iid("905A0FE2-BC53-11DF-8C49-001E4FC686DA");
   private static final GUID IID_CONTENT_TYPE_PROVIDER = WinRt.iid("97D098A5-3B99-4DE9-88A5-E11D2F50C795");
   private static final int CONTENT_TYPE_PROVIDER_GET_CONTENT_TYPE = 6;
   private static final String DATA_READER_CLASS = "Windows.Storage.Streams.DataReader";
   private static final GUID IID_DATA_READER_FACTORY = WinRt.iid("D7527847-57DA-4E15-914C-06806699A098");
   private static final int DATA_READER_FACTORY_CREATE = 6;
   private static final int DATA_READER_PUT_INPUT_STREAM_OPTIONS = 12;
   private static final int DATA_READER_READ_BYTES = 14;
   private static final int DATA_READER_LOAD_ASYNC = 29;
   private static final int INPUT_STREAM_OPTIONS_PARTIAL = 1;
   private static final long REQUEST_TIMEOUT_MS = 3000L;
   private static final long PROPERTIES_TIMEOUT_MS = 1000L;
   private static final long THUMBNAIL_TIMEOUT_MS = 1500L;
   private static final long CONTROL_TIMEOUT_MS = 1000L;
   private static final long MANAGER_RETRY_MS = 5000L;
   private static final int MAX_SESSIONS = 64;
   private static final int THUMBNAIL_CHECKS = 3;
   private static final long THUMBNAIL_RECHECK_MS = 2000L;
   private static final long THUMBNAIL_SETTLE_MS = 1500L;
   private static final int THUMBNAIL_CHUNK = 65536;
   private static final int THUMBNAIL_MAX_BYTES = 4194304;
   private static final long UNIX_EPOCH_TICKS = 116444736000000000L;
   private static final long TICKS_PER_MS = 10000L;
   private final String appFilter;
   private volatile Thread owner;
   private boolean ownerInitialized;
   private volatile boolean closed;
   private volatile boolean unsupported;
   private volatile String lastError;
   private Pointer manager;
   private Pointer dataReaderFactory;
   private long managerRetryAt;
   private String sessionApp = "";
   private String trackKey;
   private String trackAlbum = "";
   private long trackChangedAt;
   private byte[] thumbnail;
   private String thumbnailType = "";
   private int thumbnailVersion;
   private int thumbnailChecksLeft;
   private long nextThumbnailCheckAt;
   private byte[] staleThumbnail;

   public MediaSession(String appFilter) {
      this.appFilter = appFilter == null ? null : appFilter.toLowerCase(Locale.ROOT);
   }

   public static MediaSession spotify() {
      return new MediaSession("spotify");
   }

   public MediaSession.Snapshot poll() {
      try {
         if (!this.prepare()) {
            return null;
         } else {
            this.lastError = null;
            return this.read();
         }
      } catch (WinRt.WinRtException var2) {
         return this.fail(var2.getMessage());
      } catch (LinkageError var3) {
         this.unsupported = true;
         return this.fail("WinRT unavailable: " + var3);
      } catch (RuntimeException var4) {
         return this.fail(var4.toString());
      }
   }

   public boolean togglePlayPause() {
      return this.control(20, "TryTogglePlayPauseAsync");
   }

   public boolean skipNext() {
      return this.control(16, "TrySkipNextAsync");
   }

   public boolean skipPrevious() {
      return this.control(17, "TrySkipPreviousAsync");
   }

   public String lastError() {
      return this.lastError;
   }

   public boolean isUnsupported() {
      return this.unsupported;
   }

   public void close() {
      this.closed = true;
      if (this.owner == Thread.currentThread()) {
         this.dispose();
      }
   }

   private boolean prepare() {
      Thread current = Thread.currentThread();
      if (this.owner != current) {
         if (this.owner != null && this.owner.isAlive()) {
            this.lastError = "called from a thread other than the owner";
            return false;
         }

         if (this.closed || this.unsupported) {
            return false;
         }

         int result = WinRt.initializeMultithreaded();
         if (result < 0 && result != -2147417850) {
            throw new WinRt.WinRtException("RoInitialize", result);
         }

         this.owner = current;
         this.ownerInitialized = result >= 0;
      }

      if (this.closed) {
         this.dispose();
         return false;
      } else if (this.unsupported) {
         return false;
      } else {
         if (this.manager == null) {
            long now = System.currentTimeMillis();
            if (now < this.managerRetryAt) {
               return false;
            }

            this.managerRetryAt = now + 5000L;
            this.manager = this.requestManager();
         }

         return this.manager != null;
      }
   }

   private Pointer requestManager() {
      Pointer var4;
      try (WinRt.Refs refs = new WinRt.Refs()) {
         Pointer statics;
         try {
            statics = refs.add(WinRt.activationFactory("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager", IID_MANAGER_STATICS));
         } catch (WinRt.WinRtException var6) {
            if (var6.hresult == -2147221164 || var6.hresult == -2147467262) {
               this.unsupported = true;
            }

            throw var6;
         }

         Pointer operation = refs.add(WinRt.callForObject(statics, 6, "RequestAsync"));
         var4 = WinRt.awaitObject(operation, 3000L, "RequestAsync");
      }

      return var4;
   }

   private void dispose() {
      Pointer oldManager = this.manager;
      Pointer oldFactory = this.dataReaderFactory;
      this.manager = null;
      this.dataReaderFactory = null;

      try {
         WinRt.release(oldManager);
         WinRt.release(oldFactory);
      } catch (RuntimeException | LinkageError var5) {
      }

      this.forgetTrack();
      if (this.ownerInitialized) {
         this.ownerInitialized = false;

         try {
            WinRt.uninitialize();
         } catch (RuntimeException | LinkageError var4) {
         }
      }
   }

   private MediaSession.Snapshot fail(String message) {
      this.lastError = message;
      return null;
   }

   private void dropManager() {
      Pointer oldManager = this.manager;
      this.manager = null;

      try {
         WinRt.release(oldManager);
      } catch (RuntimeException | LinkageError var3) {
      }
   }

   private MediaSession.Snapshot read() {
      MediaSession.Snapshot var41;
      try (WinRt.Refs refs = new WinRt.Refs()) {
         Pointer session = this.findSession(refs);
         if (session == null) {
            this.forgetTrack();
            return null;
         }

         String app = this.sessionApp;
         MediaSession.PlaybackState state = MediaSession.PlaybackState.CLOSED;
         double rate = 1.0;
         Pointer playback = refs.add(WinRt.callForObject(session, 9, "GetPlaybackInfo"));
         if (playback != null) {
            state = MediaSession.PlaybackState.of(WinRt.getInt32(playback, 7, "PlaybackStatus"));
            Pointer rateValue = refs.add(WinRt.callForObject(playback, 10, "PlaybackRate"));
            if (rateValue != null) {
               double reported = WinRt.getDouble(rateValue, 6, "PlaybackRate value");
               if (reported > 0.0 && reported <= 16.0) {
                  rate = reported;
               }
            }
         }

         long startTicks = 0L;
         long endTicks = 0L;
         long positionTicks = 0L;
         long updatedTicks = 0L;
         Pointer timeline = refs.add(WinRt.callForObject(session, 8, "GetTimelineProperties"));
         if (timeline != null) {
            startTicks = WinRt.getInt64(timeline, 6, "StartTime");
            endTicks = WinRt.getInt64(timeline, 7, "EndTime");
            positionTicks = WinRt.getInt64(timeline, 10, "Position");
            updatedTicks = WinRt.getInt64(timeline, 11, "LastUpdatedTime");
         }

         long timelineReadAt = System.currentTimeMillis();
         String title = "";
         String artist = "";
         String album = "";
         Pointer operation = refs.add(WinRt.callForObject(session, 7, "TryGetMediaPropertiesAsync"));
         Pointer properties = refs.add(WinRt.awaitObject(operation, 1000L, "TryGetMediaPropertiesAsync"));
         if (properties != null) {
            title = WinRt.getString(properties, 6, "Title").strip();
            artist = WinRt.getString(properties, 9, "Artist").strip();
            album = WinRt.getString(properties, 10, "AlbumTitle").strip();
         }

         long now = System.currentTimeMillis();
         String key = app + "\n" + title + "\n" + artist + "\n" + album;
         if (!key.equals(this.trackKey)) {
            boolean sameAlbum = this.trackKey != null && !album.isEmpty() && album.equals(this.trackAlbum);
            this.staleThumbnail = sameAlbum ? null : this.thumbnail;
            if (!sameAlbum) {
               this.setThumbnail(null, "");
            }

            this.trackKey = key;
            this.trackAlbum = album;
            this.trackChangedAt = now;
            this.thumbnailChecksLeft = 3;
            this.nextThumbnailCheckAt = now;
         }

         if (properties != null && this.thumbnailChecksLeft > 0 && now >= this.nextThumbnailCheckAt) {
            MediaSession.Thumbnail art = this.readThumbnail(properties);
            now = System.currentTimeMillis();
            boolean stale = art != null && this.staleThumbnail != null && Arrays.equals(art.bytes(), this.staleThumbnail);
            if ((art == null || stale) && now - this.trackChangedAt < 1500L) {
               this.nextThumbnailCheckAt = now;
            } else {
               if (art != null) {
                  this.setThumbnail(art.bytes(), art.type());
               }

               this.staleThumbnail = null;
               this.thumbnailChecksLeft--;
               this.nextThumbnailCheckAt = now + 2000L;
            }
         }

         long durationMs = Math.max(0L, (endTicks - startTicks) / 10000L);
         long positionMs = Math.max(0L, (positionTicks - startTicks) / 10000L);
         if (state == MediaSession.PlaybackState.PLAYING && durationMs > 0L) {
            long fromMs = updatedTicks > 0L ? (updatedTicks - 116444736000000000L) / 10000L : timelineReadAt;
            long elapsed = now - fromMs;
            if (elapsed > 0L) {
               positionMs += (long)(Math.min(elapsed, durationMs) * rate);
            }
         }

         if (durationMs > 0L) {
            positionMs = Math.min(positionMs, durationMs);
         }

         var41 = new MediaSession.Snapshot(
            title, artist, album, durationMs, positionMs, now, state, rate, this.thumbnail, this.thumbnailType, this.thumbnailVersion
         );
      }

      return var41;
   }

   private Pointer findSession(WinRt.Refs refs) {
      if (this.appFilter == null) {
         Pointer session = refs.add(this.managerCall(6, "GetCurrentSession"));
         if (session != null) {
            this.sessionApp = WinRt.getString(session, 6, "SourceAppUserModelId");
         }

         return session;
      } else {
         Pointer sessions = refs.add(this.managerCall(7, "GetSessions"));
         if (sessions == null) {
            return null;
         } else {
            int count = Math.min(WinRt.getInt32(sessions, 7, "Sessions.Size"), 64);

            for (int i = 0; i < count; i++) {
               Pointer session = refs.add(WinRt.callForObject(sessions, 6, "Sessions.GetAt", i));
               if (session != null) {
                  String app = WinRt.getString(session, 6, "SourceAppUserModelId");
                  if (app.toLowerCase(Locale.ROOT).contains(this.appFilter)) {
                     this.sessionApp = app;
                     return session;
                  }
               }
            }

            return null;
         }
      }
   }

   private Pointer managerCall(int slot, String step) {
      try {
         return WinRt.callForObject(this.manager, slot, step);
      } catch (WinRt.WinRtException var4) {
         this.dropManager();
         throw var4;
      }
   }

   private void forgetTrack() {
      this.trackKey = null;
      this.trackAlbum = "";
      this.thumbnailChecksLeft = 0;
      this.staleThumbnail = null;
      this.setThumbnail(null, "");
   }

   private void setThumbnail(byte[] bytes, String type) {
      if (bytes == null ? this.thumbnail != null : !Arrays.equals(bytes, this.thumbnail)) {
         this.thumbnail = bytes;
         this.thumbnailType = bytes == null ? "" : type;
         this.thumbnailVersion++;
      }
   }

   private MediaSession.Thumbnail readThumbnail(Pointer properties) {
      try {
         MediaSession.Thumbnail var30;
         try (WinRt.Refs refs = new WinRt.Refs()) {
            Pointer reference = refs.add(WinRt.callForObject(properties, 15, "Thumbnail"));
            if (reference == null) {
               return null;
            }

            Pointer operation = refs.add(WinRt.callForObject(reference, 6, "OpenReadAsync"));
            Pointer stream = refs.add(WinRt.awaitObject(operation, 1500L, "OpenReadAsync"));
            if (stream == null) {
               return null;
            }

            String type = "";
            Pointer typed = refs.add(WinRt.tryQueryInterface(stream, IID_CONTENT_TYPE_PROVIDER));
            if (typed != null) {
               type = WinRt.getString(typed, 6, "ContentType");
            }

            Pointer random = refs.add(WinRt.queryInterface(stream, IID_RANDOM_ACCESS_STREAM, "IRandomAccessStream"));
            long size = WinRt.getInt64(random, 6, "Stream.Size");
            if (size < 0L || size > 4194304L) {
               return null;
            }

            Pointer input = refs.add(WinRt.queryInterface(stream, IID_INPUT_STREAM, "IInputStream"));
            Pointer reader = refs.add(WinRt.callForObject(this.dataReaderFactory(), 6, "CreateDataReader", input));
            if (reader == null) {
               return null;
            }

            WinRt.check(WinRt.invoke(reader, 12, 1), "InputStreamOptions");
            ByteArrayOutputStream out = new ByteArrayOutputStream(size > 0L ? (int)size : 65536);

            while (size == 0L || out.size() < size) {
               int want = size > 0L ? (int)Math.min(size - out.size(), 65536L) : 65536;
               Pointer load = WinRt.callForObject(reader, 29, "LoadAsync", want);

               int loaded;
               try {
                  loaded = WinRt.awaitUInt32(load, 1500L, "LoadAsync");
               } finally {
                  WinRt.release(load);
               }

               if (loaded <= 0) {
                  break;
               }

               if (out.size() + loaded > 4194304) {
                  return null;
               }

               byte[] chunk = new byte[loaded];
               WinRt.check(WinRt.invoke(reader, 14, loaded, chunk), "ReadBytes");
               out.write(chunk, 0, loaded);
            }

            if (out.size() == 0 || size > 0L && out.size() != size) {
               return null;
            }

            var30 = new MediaSession.Thumbnail(out.toByteArray(), type);
         }

         return var30;
      } catch (WinRt.WinRtException var25) {
         this.lastError = "album art: " + var25.getMessage();
         return null;
      }
   }

   private Pointer dataReaderFactory() {
      if (this.dataReaderFactory == null) {
         this.dataReaderFactory = WinRt.activationFactory("Windows.Storage.Streams.DataReader", IID_DATA_READER_FACTORY);
      }

      return this.dataReaderFactory;
   }

   private boolean control(int slot, String step, Object... args) {
      try {
         if (!this.prepare()) {
            return false;
         }

         boolean var8;
         try (WinRt.Refs refs = new WinRt.Refs()) {
            Pointer session = this.findSession(refs);
            if (session == null) {
               return false;
            }

            Pointer operation = refs.add(WinRt.callForObject(session, slot, step, args));
            boolean accepted = WinRt.awaitBoolean(operation, 1000L, step);
            this.lastError = null;
            var8 = accepted;
         }

         return var8;
      } catch (WinRt.WinRtException var11) {
         this.fail(var11.getMessage());
      } catch (LinkageError var12) {
         this.unsupported = true;
         this.fail("WinRT unavailable: " + var12);
      } catch (RuntimeException var13) {
         this.fail(var13.toString());
      }

      return false;
   }

   @Environment(EnvType.CLIENT)
   public static enum PlaybackState {
      CLOSED,
      OPENED,
      CHANGING,
      STOPPED,
      PLAYING,
      PAUSED;

      private static final MediaSession.PlaybackState[] VALUES = values();

      static MediaSession.PlaybackState of(int value) {
         return value >= 0 && value < VALUES.length ? VALUES[value] : CLOSED;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Snapshot(
      String title,
      String artist,
      String album,
      long durationMs,
      long positionMs,
      long sampledAtMs,
      MediaSession.PlaybackState state,
      double rate,
      byte[] thumbnail,
      String thumbnailType,
      int thumbnailVersion
   ) {
      public boolean hasThumbnail() {
         return this.thumbnail != null;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Thumbnail(byte[] bytes, String type) {
   }
}
