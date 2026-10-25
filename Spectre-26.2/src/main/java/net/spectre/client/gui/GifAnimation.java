package net.spectre.client.gui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class GifAnimation {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int MAX_SIDE = 1024;
   private static final int MAX_FRAMES = 300;
   private static final long MAX_PIXELS = 33554432L;
   private static final int POSTER_SIDE = 256;
   private static final int QUEUE_FRAMES = 8;
   private static final long UPLOAD_BUDGET_NANOS = 2000000L;
   private static final ExecutorService POSTERS = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Spectre GIF posters");
      thread.setDaemon(true);
      return thread;
   });
   private final String name;
   private final boolean poster;
   private final BlockingQueue<GifDecoder.Frame> queue = new ArrayBlockingQueue<>(8);
   private final List<ThemeImages.Texture> textures = new ArrayList<>();
   private final int[] ends;
   private volatile boolean decoded;
   private volatile boolean cancelled;
   private long startNanos = -1L;

   private GifAnimation(String name, boolean poster) {
      this.name = name;
      this.poster = poster;
      this.ends = new int[poster ? 1 : 300];
   }

   static GifAnimation load(Path path, boolean poster) {
      GifAnimation animation = new GifAnimation(path.getFileName().toString(), poster);
      if (poster) {
         POSTERS.execute(() -> animation.decode(path));
      } else {
         Thread.ofPlatform().daemon().name("Spectre GIF decoder").start(() -> animation.decode(path));
      }

      return animation;
   }

   ThemeImages.Texture current() {
      boolean complete = this.decoded;
      this.upload();
      int count = this.textures.size();
      if (count == 0) {
         return null;
      } else {
         if (this.startNanos < 0L) {
            if (!complete || !this.queue.isEmpty()) {
               return this.textures.get(0);
            }

            this.startNanos = System.nanoTime();
         }

         long time = (System.nanoTime() - this.startNanos) / 1000000L % this.ends[count - 1];
         int lo = 0;
         int hi = count - 1;

         while (lo < hi) {
            int mid = lo + hi >>> 1;
            if (this.ends[mid] > time) {
               hi = mid;
            } else {
               lo = mid + 1;
            }
         }

         return this.textures.get(lo);
      }
   }

   boolean failed() {
      return this.decoded && this.queue.isEmpty() && this.textures.isEmpty();
   }

   void free() {
      this.cancelled = true;
      this.queue.clear();

      for (ThemeImages.Texture texture : this.textures) {
         GL11.glDeleteTextures(texture.id());
      }

      this.textures.clear();
   }

   private void decode(Path path) {
      try {
         if (!this.cancelled) {
            GifDecoder gif = GifDecoder.open(Files.readAllBytes(path));
            int w = gif.width();
            int h = gif.height();
            int frames = Math.min(gif.frameCount(), this.ends.length);
            double scale = Math.min(1.0, (double)(this.poster ? 256 : 1024) / Math.max(w, h));
            if (!this.poster) {
               scale = Math.min(scale, Math.sqrt(3.3554432E7 / ((double)frames * w * h)));
            }

            int outW = Math.max(1, (int)Math.round(w * scale));
            int outH = Math.max(1, (int)Math.round(h * scale));
            if (!this.poster && frames < gif.frameCount()) {
               LOGGER.info("[Spectre] {} has {} frames; only the first {} will play", new Object[]{this.name, gif.frameCount(), frames});
            }

            if (!this.poster && outW < w) {
               LOGGER.info("[Spectre] {} ({}x{}, {} frames) plays at {}x{} to keep its memory use down", new Object[]{this.name, w, h, frames, outW, outH});
            }

            gif.decode(outW, outH, frames, this::offer);
            return;
         }
      } catch (RuntimeException | IOException var13) {
         LOGGER.warn("[Spectre] Failed to load {}", this.name, var13);
         return;
      } finally {
         this.decoded = true;
      }
   }

   private boolean offer(GifDecoder.Frame frame) {
      int[] pixels = frame.argb();

      for (int i = 0; i < pixels.length; i++) {
         int p = pixels[i];
         pixels[i] = p & -16711936 | p >>> 16 & 0xFF | (p & 0xFF) << 16;
      }

      try {
         while (!this.cancelled) {
            if (this.queue.offer(frame, 50L, TimeUnit.MILLISECONDS)) {
               return true;
            }
         }
      } catch (InterruptedException var5) {
         Thread.currentThread().interrupt();
      }

      return false;
   }

   private void upload() {
      long start = System.nanoTime();

      GifDecoder.Frame frame;
      while (this.textures.size() < this.ends.length && (frame = this.queue.poll()) != null) {
         ByteBuffer rgba = MemoryUtil.memAlloc(frame.argb().length * 4);

         try {
            rgba.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(frame.argb());
            this.textures.add(ThemeImages.fromPixels(rgba, frame.width(), frame.height(), false));
         } finally {
            MemoryUtil.memFree(rgba);
         }

         int count = this.textures.size();
         this.ends[count - 1] = (count > 1 ? this.ends[count - 2] : 0) + frame.delayMs();
         if (System.nanoTime() - start > 2000000L) {
            break;
         }
      }
   }
}
