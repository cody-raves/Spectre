package net.spectre.client.util.playerbypass;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ProtectedChunkStore {
   public static final int RADIUS = 2;
   static final int CONFIG_VERSION = 1;
   static final long SAVE_DELAY_NANOS = 10000000000L;
   private static final long STOP_WAIT_SECONDS = 10L;
   private static final String DEFAULT_PORT_SUFFIX = ":25565";
   private static final int NAME_PART_LENGTH = 40;
   private static final long NOT_AN_INT = Long.MIN_VALUE;
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final ProtectedChunkStore INSTANCE = new ProtectedChunkStore();
   private final Map<String, ProtectedChunkStore.Bucket> buckets = new HashMap<>();
   private final Executor writer = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Spectre Player Bypass saves");
      thread.setDaemon(true);
      return thread;
   });
   private final ConcurrentHashMap<Path, long[]> queued = new ConcurrentHashMap<>();
   private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
   private volatile boolean writeFailed;
   private ProtectedChunkStore.Bucket active;
   private boolean dirty;
   private long dirtySince;

   private ProtectedChunkStore() {
   }

   public static ProtectedChunkStore get() {
      return INSTANCE;
   }

   public static long key(int chunkX, int chunkZ) {
      return chunkX & 4294967295L | (chunkZ & 4294967295L) << 32;
   }

   static int chunkX(long key) {
      return (int)key;
   }

   static int chunkZ(long key) {
      return (int)(key >>> 32);
   }

   public static String serverKey(String address) {
      String key = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
      if (key.endsWith(":25565")) {
         key = key.substring(0, key.length() - ":25565".length());
      }

      return key.isEmpty() ? "unknown" : key;
   }

   public static String singleplayerKey(String worldFolder) {
      return "singleplayer/" + worldFolder;
   }

   public static void forEachInArea(int centerX, int centerZ, LongConsumer action) {
      for (int x = centerX - 2; x <= centerX + 2; x++) {
         for (int z = centerZ - 2; z <= centerZ + 2; z++) {
            action.accept(key(x, z));
         }
      }
   }

   static String fileName(String server, String dimension) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest((server + "\n" + dimension).getBytes(StandardCharsets.UTF_8));
         String hash = HexFormat.of().formatHex(digest, 0, 8);
         return readable(server) + "_" + readable(dimension) + "-" + hash + ".json";
      } catch (NoSuchAlgorithmException var4) {
         throw new IllegalStateException("SHA-256 is always available", var4);
      }
   }

   public void select(String server, String dimension) {
      if (server != null && dimension != null) {
         String name = fileName(server, dimension);
         ProtectedChunkStore.Bucket bucket = this.buckets.get(name);
         if (bucket == null) {
            bucket = new ProtectedChunkStore.Bucket(this.directory().resolve(name), server, dimension);
            load(bucket);
            this.buckets.put(name, bucket);
         }

         this.active = bucket;
      } else {
         this.active = null;
      }
   }

   public boolean isProtected(long chunk) {
      return this.active != null && this.active.chunks.contains(chunk);
   }

   public boolean protect(long chunk) {
      if (this.active != null && this.active.chunks.add(chunk)) {
         this.changed(this.active);
         return true;
      } else {
         return false;
      }
   }

   public boolean flagAllowed(long chunk, boolean alreadyFlagged) {
      return alreadyFlagged || !this.isProtected(chunk);
   }

   public void tickAutosave() {
      this.retryFailedWrites();
      if (this.dirty && System.nanoTime() - this.dirtySince >= 10000000000L) {
         this.saveChanged();
      }
   }

   public void flush() {
      this.retryFailedWrites();
      if (this.dirty) {
         this.saveChanged();
      }
   }

   public void flushAndWait() {
      this.flush();

      try {
         this.lastWrite.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException var2) {
         Thread.currentThread().interrupt();
      } catch (TimeoutException | ExecutionException var3) {
         LOGGER.warn("[Spectre] Player Bypass protected chunks may not all be saved", var3);
      }
   }

   private void changed(ProtectedChunkStore.Bucket bucket) {
      if (!bucket.readOnly) {
         bucket.dirty = true;
         if (!this.dirty) {
            this.dirty = true;
            this.dirtySince = System.nanoTime();
         }
      }
   }

   private void retryFailedWrites() {
      if (this.writeFailed) {
         this.writeFailed = false;

         for (ProtectedChunkStore.Bucket bucket : this.buckets.values()) {
            if (bucket.writeFailed) {
               bucket.writeFailed = false;
               this.changed(bucket);
            }
         }
      }
   }

   private void saveChanged() {
      this.dirty = false;

      for (ProtectedChunkStore.Bucket bucket : this.buckets.values()) {
         if (bucket.dirty) {
            bucket.dirty = false;
            this.save(bucket);
         }
      }
   }

   private void save(ProtectedChunkStore.Bucket bucket) {
      if (this.queued.put(bucket.file, bucket.chunks.toLongArray()) == null) {
         this.lastWrite = CompletableFuture.runAsync(() -> this.writeQueued(bucket), this.writer);
      }
   }

   private void writeQueued(ProtectedChunkStore.Bucket bucket) {
      long[] chunks = this.queued.remove(bucket.file);
      if (chunks != null) {
         try {
            write(bucket, chunks);
         } catch (RuntimeException | IOException var4) {
            LOGGER.warn("[Spectre] Failed to save Player Bypass protected chunks to {}", bucket.file, var4);
            bucket.writeFailed = true;
            this.writeFailed = true;
         }
      }
   }

   private static void write(ProtectedChunkStore.Bucket bucket, long[] chunks) throws IOException {
      Files.createDirectories(bucket.file.getParent());
      Path tmp = bucket.file.resolveSibling(bucket.file.getFileName() + ".tmp");
      JsonWriter json = new JsonWriter(Files.newBufferedWriter(tmp));

      try {
         json.beginObject();
         json.name("version").value(1L);
         json.name("server").value(bucket.server);
         json.name("dimension").value(bucket.dimension);
         json.name("count").value(chunks.length);
         json.name("chunks").beginArray();

         for (long chunk : chunks) {
            json.value(chunkX(chunk)).value(chunkZ(chunk));
         }

         json.endArray();
         json.endObject();
      } catch (Throwable var11) {
         try {
            json.close();
         } catch (Throwable var9) {
            var11.addSuppressed(var9);
         }

         throw var11;
      }

      json.close();

      try {
         Files.move(tmp, bucket.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var10) {
         Files.move(tmp, bucket.file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static void load(ProtectedChunkStore.Bucket bucket) {
      if (Files.isRegularFile(bucket.file)) {
         int version = 0;

         try {
            JsonReader json = new JsonReader(Files.newBufferedReader(bucket.file));

            try {
               long maxChunks = Files.size(bucket.file) / 4L;
               json.beginObject();

               while (json.hasNext()) {
                  String var5 = json.nextName();
                  switch (var5) {
                     case "version":
                        version = json.nextInt();
                        break;
                     case "count":
                        long count = wholeInt(json);
                        if (count > 0L) {
                           bucket.chunks.ensureCapacity((int)Math.min(count, maxChunks));
                        }
                        break;
                     case "chunks":
                        if (json.peek() == JsonToken.BEGIN_ARRAY) {
                           readChunks(json, bucket.chunks);
                        } else {
                           json.skipValue();
                        }
                        break;
                     default:
                        json.skipValue();
                  }
               }

               json.endObject();
            } catch (Throwable var10) {
               try {
                  json.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }

               throw var10;
            }

            json.close();
         } catch (RuntimeException | IOException var11) {
            LOGGER.warn("[Spectre] Failed to load Player Bypass protected chunks from {}; preserving the file", bucket.file, var11);
            bucket.chunks.clear();
            bucket.readOnly = true;
            return;
         }

         if (version > 1) {
            LOGGER.warn(
               "[Spectre] Player Bypass protected chunks file {} is version {}, newer than supported {}; skipping load", new Object[]{bucket.file, version, 1}
            );
            bucket.chunks.clear();
            bucket.readOnly = true;
         }
      }
   }

   private static void readChunks(JsonReader json, LongOpenHashSet into) throws IOException {
      json.beginArray();
      int index = 0;
      long x = Long.MIN_VALUE;

      while (json.hasNext()) {
         long value = wholeInt(json);
         if ((index++ & 1) == 0) {
            x = value;
         } else if (x != Long.MIN_VALUE && value != Long.MIN_VALUE) {
            into.add(key((int)x, (int)value));
         }
      }

      json.endArray();
   }

   private static long wholeInt(JsonReader json) throws IOException {
      if (json.peek() != JsonToken.NUMBER) {
         json.skipValue();
         return Long.MIN_VALUE;
      } else {
         double value = json.nextDouble();
         return value == Math.rint(value) && !(value < -2.1474836E9F) && !(value > 2.147483647E9) ? (long)value : Long.MIN_VALUE;
      }
   }

   private static String readable(String key) {
      StringBuilder out = new StringBuilder(Math.min(key.length(), 40));

      for (int i = 0; i < key.length() && out.length() < 40; i++) {
         char c = Character.toLowerCase(key.charAt(i));
         out.append((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '.' && c != '-' ? '_' : c);
      }

      return out.toString();
   }

   private Path directory() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("playerbypass-protected");
   }

   @Environment(EnvType.CLIENT)
   private static final class Bucket {
      final Path file;
      final String server;
      final String dimension;
      final LongOpenHashSet chunks = new LongOpenHashSet();
      boolean readOnly;
      boolean dirty;
      volatile boolean writeFailed;

      Bucket(Path file, String server, String dimension) {
         this.file = file;
         this.server = server;
         this.dimension = dimension;
      }
   }
}
