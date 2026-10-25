package net.spectre.client.util.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Flow.Subscription;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class LyricsService implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final String MOD_ID = "spectre";
   private static final URI LRCLIB = URI.create("https://lrclib.net/");
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8L);
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12L);
   private static final int MAX_BODY_BYTES = 4194304;
   private static final long REQUEST_GAP_MS = 300L;
   private static final long MAX_INLINE_WAIT_MS = 3000L;
   private static final int MAX_ATTEMPTS = 3;
   private static final long DEFAULT_RETRY_AFTER_MS = 30000L;
   private static final long MAX_RETRY_AFTER_MS = TimeUnit.HOURS.toMillis(1L);
   private static final int FOUND_TTL_DAYS = 30;
   private static final int NOT_FOUND_TTL_HOURS = 12;
   private static final long FOUND_TTL_MS = TimeUnit.DAYS.toMillis(30L);
   private static final long NOT_FOUND_TTL_MS = TimeUnit.HOURS.toMillis(12L);
   private static final long FAILURE_RETRY_MS = 60000L;
   private static final int MEMORY_ENTRIES = 64;
   private static final int DISK_MAX_FILES = 400;
   private static final long DISK_MAX_BYTES = 6291456L;
   private static final int WRITES_PER_PRUNE = 25;
   private static final long IDLE_THREAD_MS = 30000L;
   private final Object lock = new Object();
   private final Map<String, LyricsService.Memo> memory = new LinkedHashMap<String, LyricsService.Memo>(128, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, LyricsService.Memo> eldest) {
         return this.size() > 64;
      }
   };
   private final Set<String> pending = new HashSet<>();
   private final ThreadPoolExecutor worker;
   private final LyricsDiskCache disk;
   private final String userAgent;
   private volatile String wanted;
   private volatile LyricsService.KeyMemo lastKey;
   private volatile boolean closed;
   private volatile boolean online = true;
   private volatile int mode;
   private volatile long busyUntil;
   private volatile HttpClient http;
   private volatile ExecutorService httpExecutor;
   private long nextRequestAt;
   private int writesSincePrune = 25;

   public LyricsService() {
      Path cacheDir = defaultCacheDir();
      this.disk = cacheDir == null ? null : new LyricsDiskCache(cacheDir, 400, 6291456L);
      this.userAgent = defaultUserAgent();
      this.worker = new ThreadPoolExecutor(1, 1, 30000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), daemonThreads("Spectre-Lyrics"));
      this.worker.allowCoreThreadTimeOut(true);
   }

   public Lyrics lookup(LyricsQuery query) {
      if (query != null && query.searchable()) {
         String key = this.keyOf(query);
         this.wanted = key;
         long now = System.currentTimeMillis();
         synchronized (this.lock) {
            LyricsService.Memo memo = this.memory.get(key);
            if (memo != null && now < memo.freshUntil()) {
               return memo.lyrics();
            } else {
               if (!this.closed && this.pending.add(key)) {
                  try {
                     this.worker.execute(() -> this.resolve(key, query));
                  } catch (RejectedExecutionException var9) {
                     this.pending.remove(key);
                  }
               }

               if (memo != null) {
                  return memo.lyrics();
               } else {
                  return this.closed ? Lyrics.UNAVAILABLE : Lyrics.LOADING;
               }
            }
         }
      } else {
         return Lyrics.NOT_FOUND;
      }
   }

   public void setOnline(boolean online) {
      if (this.online != online) {
         this.online = online;
         synchronized (this.lock) {
            this.mode++;
            this.memory.clear();
         }
      }
   }

   @Override
   public void close() {
      this.closed = true;
      this.worker.shutdownNow();
      synchronized (this.lock) {
         this.pending.clear();
      }

      HttpClient client = this.http;
      if (client != null) {
         client.shutdownNow();
      }

      ExecutorService executor = this.httpExecutor;
      if (executor != null) {
         executor.shutdownNow();
      }
   }

   private void resolve(String key, LyricsQuery query) {
      LyricsDiskCache.Entry stored = null;
      int startMode = this.mode;

      try {
         if (this.closed || !key.equals(this.wanted)) {
            return;
         }

         stored = this.disk == null ? null : this.disk.read(key);
         if (stored != null) {
            long freshUntil = stored.fetchedAt() + (stored.found() ? FOUND_TTL_MS : NOT_FOUND_TTL_MS);
            if (System.currentTimeMillis() < freshUntil) {
               this.remember(key, Lyrics.of(stored.track()), freshUntil);
               return;
            }
         }

         if (this.online) {
            LyricsService.Found found = this.fetch(query);
            long fetchedAt = System.currentTimeMillis();
            Lyrics lyrics = Lyrics.of(found.track());
            if (!found.complete()) {
               this.remember(key, lyrics, this.retryAt());
               return;
            }

            LrclibTrack keep = lyrics.status() == Lyrics.Status.NOT_FOUND ? null : found.track();
            this.remember(key, lyrics, fetchedAt + (keep == null ? NOT_FOUND_TTL_MS : FOUND_TTL_MS));
            if (this.disk != null && !this.closed) {
               this.disk.write(key, keep, fetchedAt);
               if (++this.writesSincePrune >= 25) {
                  this.writesSincePrune = 0;
                  this.disk.prune();
                  return;
               }
            }

            return;
         }

         Lyrics offline = stored != null ? Lyrics.of(stored.track()) : Lyrics.NOT_FOUND;
         synchronized (this.lock) {
            if (this.mode == startMode) {
               this.memory.put(key, new LyricsService.Memo(offline, System.currentTimeMillis() + 60000L));
            }
         }
      } catch (InterruptedException var35) {
         Thread.currentThread().interrupt();
         return;
      } catch (IOException var36) {
         LOGGER.debug("[Spectre] Lyrics lookup for \"{}\" failed: {}", query.title(), var36.toString());
         this.remember(key, stored != null ? Lyrics.of(stored.track()) : Lyrics.UNAVAILABLE, this.retryAt());
         return;
      } catch (RuntimeException var37) {
         LOGGER.debug("[Spectre] Lyrics lookup for \"" + query.title() + "\" failed", var37);
         this.remember(key, Lyrics.UNAVAILABLE, this.retryAt());
         return;
      } finally {
         synchronized (this.lock) {
            this.pending.remove(key);
         }
      }
   }

   private LyricsService.Found fetch(LyricsQuery query) throws IOException, InterruptedException {
      LrclibTrack exact = this.get(query, true);
      if (exact == null && !query.album().isEmpty()) {
         exact = this.get(query, false);
      }

      if (exact == null || !exact.hasSynced() && (!exact.instrumental() || exact.hasPlain())) {
         LrclibTrack fallback = exact != null && exact.usable() ? exact : null;

         try {
            for (String[] terms : LyricsMatcher.searchTerms(query)) {
               LrclibTrack best = LyricsMatcher.best(this.search(terms[0], terms[1]), query);
               if (best != null) {
                  if (best.hasSynced()) {
                     return new LyricsService.Found(best, true);
                  }

                  if (fallback == null) {
                     fallback = best;
                  }
               }
            }
         } catch (IOException var7) {
            if (fallback == null) {
               throw var7;
            }

            return new LyricsService.Found(fallback, false);
         }

         return new LyricsService.Found(fallback, true);
      } else {
         return new LyricsService.Found(exact, true);
      }
   }

   private LrclibTrack get(LyricsQuery query, boolean withAlbum) throws IOException, InterruptedException {
      LyricsService.Response response = this.send(
         withQuery(
            "api/get",
            "track_name",
            query.title(),
            "artist_name",
            query.artist(),
            "album_name",
            withAlbum ? query.album() : "",
            "duration",
            query.hasDuration() ? Integer.toString(query.durationSeconds()) : ""
         )
      );
      if (response.status() == 404) {
         return null;
      } else if (response.status() != 200) {
         throw new IOException("LRCLIB /api/get answered HTTP " + response.status());
      } else {
         return LrclibTrack.fromJson(json(response.body()));
      }
   }

   private List<LrclibTrack> search(String title, String artist) throws IOException, InterruptedException {
      LyricsService.Response response = this.send(withQuery("api/search", "track_name", title, "artist_name", artist));
      if (response.status() == 404) {
         return List.of();
      } else if (response.status() != 200) {
         throw new IOException("LRCLIB /api/search answered HTTP " + response.status());
      } else {
         return LrclibTrack.listFromJson(json(response.body()));
      }
   }

   private LyricsService.Response send(String pathAndQuery) throws IOException, InterruptedException {
      URI uri = LRCLIB.resolve(pathAndQuery);
      int attempt = 1;

      while (true) {
         this.pace();
         HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", this.userAgent)
            .header("Accept", "application/json")
            .GET()
            .build();
         HttpResponse<byte[]> response = this.exchange(request);
         this.nextRequestAt = System.currentTimeMillis() + 300L;
         int status = response.statusCode();
         if (status != 429 && status != 503) {
            return new LyricsService.Response(status, new String(response.body(), StandardCharsets.UTF_8));
         }

         long waitMs = retryAfterMillis(response.headers().firstValue("Retry-After").orElse(null));
         if (waitMs * attempt > 3000L || attempt >= 3) {
            this.busyUntil = System.currentTimeMillis() + waitMs;
            throw new LyricsService.BusyException("LRCLIB answered HTTP " + status + ", retry in " + waitMs + " ms");
         }

         this.nextRequestAt = System.currentTimeMillis() + waitMs * attempt;
         attempt++;
      }
   }

   private HttpResponse<byte[]> exchange(HttpRequest request) throws IOException, InterruptedException {
      CompletableFuture<HttpResponse<byte[]>> call = this.http().sendAsync(request, info -> new LyricsService.CappedBody(4194304));

      try {
         return call.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException var6) {
         call.cancel(true);
         throw new HttpTimeoutException("LRCLIB did not answer within " + REQUEST_TIMEOUT.toMillis() + " ms");
      } catch (InterruptedException var7) {
         call.cancel(true);
         throw var7;
      } catch (ExecutionException var8) {
         Throwable cause = var8.getCause();
         if (cause instanceof IOException io) {
            throw io;
         } else {
            throw new IOException("LRCLIB request failed", cause);
         }
      }
   }

   private void pace() throws IOException, InterruptedException {
      long now = System.currentTimeMillis();
      long busy = this.busyUntil;
      if (busy - now > 3000L) {
         throw new LyricsService.BusyException("LRCLIB asked to wait another " + (busy - now) + " ms");
      } else {
         long wait = Math.max(this.nextRequestAt, busy) - now;
         if (wait > 0L) {
            Thread.sleep(wait);
         }
      }
   }

   private HttpClient http() throws InterruptedException {
      HttpClient client = this.http;
      if (client != null) {
         return client;
      } else if (this.closed) {
         throw new InterruptedException("lyrics service closed");
      } else {
         ExecutorService executor = Executors.newCachedThreadPool(daemonThreads("Spectre-Lyrics-Http"));
         client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(Redirect.NORMAL).executor(executor).build();
         this.httpExecutor = executor;
         this.http = client;
         if (this.closed) {
            client.shutdownNow();
            executor.shutdownNow();
            throw new InterruptedException("lyrics service closed");
         } else {
            return client;
         }
      }
   }

   private void remember(String key, Lyrics lyrics, long freshUntil) {
      synchronized (this.lock) {
         this.memory.put(key, new LyricsService.Memo(lyrics, freshUntil));
      }
   }

   private long retryAt() {
      return Math.max(System.currentTimeMillis() + 60000L, this.busyUntil);
   }

   private String keyOf(LyricsQuery query) {
      LyricsService.KeyMemo last = this.lastKey;
      if (last != null && last.query().equals(query)) {
         return last.key();
      } else {
         String key = query.cacheKey();
         this.lastKey = new LyricsService.KeyMemo(query, key);
         return key;
      }
   }

   private static String withQuery(String path, String... namesAndValues) {
      StringBuilder uri = new StringBuilder(path);
      char separator = '?';

      for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
         String value = namesAndValues[i + 1];
         if (value != null && !value.isEmpty()) {
            uri.append(separator).append(namesAndValues[i]).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            separator = '&';
         }
      }

      return uri.toString();
   }

   private static JsonElement json(String body) throws IOException {
      try {
         return LrclibTrack.parse(body);
      } catch (JsonParseException var2) {
         throw new IOException("LRCLIB sent malformed JSON", var2);
      }
   }

   static long retryAfterMillis(String header) {
      if (header != null && !header.isBlank()) {
         String value = header.strip();

         long millis;
         try {
            millis = Long.parseLong(value) * 1000L;
         } catch (NumberFormatException var7) {
            try {
               millis = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis();
            } catch (DateTimeParseException var6) {
               millis = 30000L;
            }
         }

         return Math.max(1000L, Math.min(MAX_RETRY_AFTER_MS, millis));
      } else {
         return 30000L;
      }
   }

   private static ThreadFactory daemonThreads(String name) {
      return runnable -> {
         Thread thread = new Thread(runnable, name);
         thread.setDaemon(true);
         return thread;
      };
   }

   private static Path defaultCacheDir() {
      try {
         return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("lyrics-cache");
      } catch (LinkageError | RuntimeException var1) {
         return null;
      }
   }

   static String defaultUserAgent() {
      String version;
      try {
         version = FabricLoader.getInstance()
            .getModContainer("spectre")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");
      } catch (LinkageError | RuntimeException var2) {
         version = "dev";
      }

      int build = version.indexOf(43);
      if (build > 0) {
         version = version.substring(0, build);
      }

      return "Spectre Client/" + version;
   }

   @Environment(EnvType.CLIENT)
   private static final class BusyException extends IOException {
      BusyException(String message) {
         super(message);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class CappedBody implements BodySubscriber<byte[]> {
      private final CompletableFuture<byte[]> result = new CompletableFuture<>();
      private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      private final int limit;
      private Subscription subscription;

      CappedBody(int limit) {
         this.limit = limit;
      }

      @Override
      public CompletionStage<byte[]> getBody() {
         return this.result;
      }

      @Override
      public void onSubscribe(Subscription subscription) {
         this.subscription = subscription;
         subscription.request(Long.MAX_VALUE);
      }

      public void onNext(List<ByteBuffer> buffers) {
         if (!this.result.isDone()) {
            for (ByteBuffer buffer : buffers) {
               if (this.bytes.size() + buffer.remaining() > this.limit) {
                  this.subscription.cancel();
                  this.result.completeExceptionally(new IOException("LRCLIB response too large"));
                  return;
               }

               byte[] chunk = new byte[buffer.remaining()];
               buffer.get(chunk);
               this.bytes.write(chunk, 0, chunk.length);
            }
         }
      }

      @Override
      public void onError(Throwable failure) {
         this.result.completeExceptionally(failure);
      }

      @Override
      public void onComplete() {
         this.result.complete(this.bytes.toByteArray());
      }
   }

   @Environment(EnvType.CLIENT)
   private record Found(LrclibTrack track, boolean complete) {
   }

   @Environment(EnvType.CLIENT)
   private record KeyMemo(LyricsQuery query, String key) {
   }

   @Environment(EnvType.CLIENT)
   private record Memo(Lyrics lyrics, long freshUntil) {
   }

   @Environment(EnvType.CLIENT)
   private record Response(int status, String body) {
   }
}
