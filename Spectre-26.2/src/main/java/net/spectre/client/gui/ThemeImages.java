package net.spectre.client.gui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.Surface;
import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ThemeImages {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final ThemeImages INSTANCE = new ThemeImages();
   private static final int MAX_SIZE = 8192;
   private static final long RESCAN_MS = 2000L;
   private static final int SWEEP_TICKS = 20;
   private final Map<String, ThemeImages.Texture> textures = new HashMap<>();
   private final Map<String, GifAnimation> animations = new HashMap<>();
   private final Map<String, GifAnimation> posters = new HashMap<>();
   private final Set<String> failed = new HashSet<>();
   private final List<Runnable> pendingFrees = new ArrayList<>();
   private List<String> files = List.of();
   private long lastScan;
   private boolean listingChanged;
   private int ticks;
   private volatile Path pendingImport;
   private volatile boolean dialogOpen;

   private ThemeImages() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (!this.pendingFrees.isEmpty()) {
            this.pendingFrees.forEach(Runnable::run);
            this.pendingFrees.clear();
         }

         if (++this.ticks % 20 == 0) {
            this.sweep();
         }
      });
   }

   public static ThemeImages get() {
      return INSTANCE;
   }

   public static boolean isImage(Path path) {
      String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
      return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || isGif(name);
   }

   public static boolean isGif(String fileName) {
      return fileName.toLowerCase(Locale.ROOT).endsWith(".gif");
   }

   public List<String> files() {
      long now = System.currentTimeMillis();
      if (now - this.lastScan > 2000L) {
         this.lastScan = now;
         List<String> scanned = scan();
         if (!scanned.equals(this.files)) {
            this.files = scanned;
            this.listingChanged = true;
         }
      }

      return this.files;
   }

   public ThemeImages.Texture texture(String fileName) {
      if (fileName == null || fileName.isBlank() || this.failed.contains(fileName)) {
         return null;
      } else if (isGif(fileName)) {
         return this.frame(this.animations, fileName, false);
      } else {
         ThemeImages.Texture texture = this.textures.get(fileName);
         if (texture == null) {
            texture = upload(ThemeStore.backgroundsDir().resolve(fileName));
            if (texture == null) {
               this.failed.add(fileName);
            } else {
               this.textures.put(fileName, texture);
            }
         }

         return texture;
      }
   }

   public ThemeImages.Texture preview(String fileName) {
      if (fileName != null && !this.failed.contains(fileName) && isGif(fileName)) {
         GifAnimation playing = this.animations.get(fileName);
         ThemeImages.Texture frame = playing != null ? playing.current() : null;
         return frame != null ? frame : this.frame(this.posters, fileName, true);
      } else {
         return this.texture(fileName);
      }
   }

   public boolean isBroken(String fileName) {
      return this.failed.contains(fileName);
   }

   public String importFile(Path source) {
      if (source != null && Files.isRegularFile(source) && isImage(source)) {
         try {
            Path dir = ThemeStore.backgroundsDir();
            Files.createDirectories(dir);
            if (dir.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize().getParent())) {
               return source.getFileName().toString();
            } else {
               String name = uniqueName(dir, source.getFileName().toString());
               Files.copy(source, dir.resolve(name));
               this.lastScan = 0L;
               return name;
            }
         } catch (RuntimeException | IOException var4) {
            LOGGER.warn("[Spectre] Failed to import header image {}", source, var4);
            return null;
         }
      } else {
         return null;
      }
   }

   public boolean delete(String fileName) {
      if (fileName != null && this.files.contains(fileName)) {
         try {
            Files.deleteIfExists(ThemeStore.backgroundsDir().resolve(fileName));
         } catch (RuntimeException | IOException var8) {
            LOGGER.warn("[Spectre] Failed to delete header image {}", fileName, var8);
            return false;
         }

         ThemeImages.Texture texture = this.textures.remove(fileName);
         if (texture != null) {
            this.pendingFrees.add(() -> GL11.glDeleteTextures(texture.id()));
         }

         GifAnimation playing = this.animations.remove(fileName);
         if (playing != null) {
            this.pendingFrees.add(playing::free);
         }

         GifAnimation poster = this.posters.remove(fileName);
         if (poster != null) {
            this.pendingFrees.add(poster::free);
         }

         this.failed.remove(fileName);
         List<Surface> surfaces = new ArrayList<>(ThemeStore.get().surfaces());
         surfaces.addAll(HudTheme.get().surfaces());

         for (Surface surface : surfaces) {
            if (fileName.equals(surface.image.get())) {
               surface.image.set("");
               if (surface.isImage()) {
                  surface.background.set("None");
               }
            }
         }

         this.lastScan = 0L;
         return true;
      } else {
         return false;
      }
   }

   public void browseAsync() {
      if (!this.dialogOpen) {
         this.dialogOpen = true;
         Thread thread = new Thread(() -> {
            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  PointerBuffer filters = stack.mallocPointer(4);
                  filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.gif")).flip();
                  String result = TinyFileDialogs.tinyfd_openFileDialog("Choose a header image", null, filters, "Images (png, jpg, gif)", false);
                  if (result != null) {
                     this.pendingImport = Path.of(result);
                  }
               } catch (Throwable var10) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                     }
                  }

                  throw var10;
               }

               if (stack != null) {
                  stack.close();
               }
            } catch (Throwable var11) {
               LOGGER.warn("[Spectre] File dialog failed", var11);
            } finally {
               this.dialogOpen = false;
            }
         }, "Spectre theme file dialog");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public Path takePendingImport() {
      Path path = this.pendingImport;
      this.pendingImport = null;
      return path;
   }

   public void openFolder() {
      try {
         Path dir = ThemeStore.backgroundsDir();
         Files.createDirectories(dir);
         Util.getPlatform().openPath(dir);
      } catch (RuntimeException | IOException var2) {
         LOGGER.warn("[Spectre] Failed to open backgrounds folder", var2);
      }
   }

   private ThemeImages.Texture frame(Map<String, GifAnimation> cache, String fileName, boolean poster) {
      GifAnimation animation = cache.computeIfAbsent(fileName, name -> GifAnimation.load(ThemeStore.backgroundsDir().resolve(name), poster));
      ThemeImages.Texture texture = animation.current();
      if (texture == null && animation.failed()) {
         cache.remove(fileName);
         this.failed.add(fileName);
      }

      return texture;
   }

   private void sweep() {
      Set<String> shown = new HashSet<>();

      for (Surface surface : ThemeStore.get().surfaces()) {
         if (surface.isImage()) {
            shown.add(surface.image.get());
         }
      }

      for (Surface surfacex : HudTheme.get().surfaces()) {
         if (surfacex.isImage()) {
            shown.add(surfacex.image.get());
         }
      }

      freeAllBut(this.animations, shown);
      if (this.listingChanged) {
         this.listingChanged = false;
         Set<String> present = new HashSet<>(this.files);
         freeAllBut(this.animations, present);
         freeAllBut(this.posters, present);
         this.textures.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
               return false;
            } else {
               GL11.glDeleteTextures(entry.getValue().id());
               return true;
            }
         });
         this.failed.retainAll(present);
      }
   }

   private static void freeAllBut(Map<String, GifAnimation> cache, Set<String> keep) {
      cache.entrySet().removeIf(entry -> {
         if (keep.contains(entry.getKey())) {
            return false;
         } else {
            entry.getValue().free();
            return true;
         }
      });
   }

   private static List<String> scan() {
      Path dir = ThemeStore.backgroundsDir();
      if (!Files.isDirectory(dir)) {
         return List.of();
      } else {
         try {
            List var2;
            try (Stream<Path> stream = Files.list(dir)) {
               var2 = stream.filter(x$0 -> Files.isRegularFile(x$0))
                  .filter(ThemeImages::isImage)
                  .map(path -> path.getFileName().toString())
                  .sorted(String.CASE_INSENSITIVE_ORDER)
                  .toList();
            }

            return var2;
         } catch (IOException var6) {
            return List.of();
         }
      }
   }

   private static String uniqueName(Path dir, String original) {
      String name = original.replaceAll("[^A-Za-z0-9._-]", "_");
      int dot = name.lastIndexOf(46);
      String base = dot > 0 ? name.substring(0, dot) : name;
      String ext = dot > 0 ? name.substring(dot) : "";
      String candidate = name;

      for (int i = 2; Files.exists(dir.resolve(candidate)); i++) {
         candidate = base + "-" + i + ext;
      }

      return candidate;
   }

   private static ThemeImages.Texture upload(Path path) {
      if (!Files.isRegularFile(path)) {
         return null;
      } else {
         try {
            return decode(Files.readAllBytes(path), path.getFileName().toString(), false);
         } catch (RuntimeException | IOException var2) {
            LOGGER.warn("[Spectre] Failed to load {}", path.getFileName(), var2);
            return null;
         }
      }
   }

   static ThemeImages.Texture decode(byte[] bytes, String name, boolean pixelated) {
      ByteBuffer file = null;
      ByteBuffer pixels = null;

      try {
         MemoryStack stack = MemoryStack.stackPush();

         Object var19;
         label179: {
            ThemeImages.Texture var20;
            label180: {
               try {
                  file = MemoryUtil.memAlloc(bytes.length);
                  file.put(bytes).flip();
                  IntBuffer w = stack.mallocInt(1);
                  IntBuffer h = stack.mallocInt(1);
                  IntBuffer channels = stack.mallocInt(1);
                  pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
                  if (pixels == null) {
                     LOGGER.warn("[Spectre] Can't decode {}: {}", name, STBImage.stbi_failure_reason());
                     var19 = null;
                     break label179;
                  }

                  int width = w.get(0);
                  int height = h.get(0);
                  if (width <= 8192 && height <= 8192) {
                     var20 = new ThemeImages.Texture(createTexture(pixels, width, height, pixelated), width, height);
                     break label180;
                  }

                  LOGGER.warn("[Spectre] {} is larger than {}px", name, 8192);
                  var20 = null;
               } catch (Throwable var17) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var16) {
                        var17.addSuppressed(var16);
                     }
                  }

                  throw var17;
               }

               if (stack != null) {
                  stack.close();
               }

               return var20;
            }

            if (stack != null) {
               stack.close();
            }

            return var20;
         }

         if (stack != null) {
            stack.close();
         }

         return (ThemeImages.Texture)var19;
      } finally {
         if (pixels != null) {
            STBImage.stbi_image_free(pixels);
         }

         if (file != null) {
            MemoryUtil.memFree(file);
         }
      }
   }

   public static ThemeImages.Texture fromPixels(ByteBuffer rgba, int width, int height, boolean pixelated) {
      return new ThemeImages.Texture(createTexture(rgba, width, height, pixelated), width, height);
   }

   private static int createTexture(ByteBuffer pixels, int width, int height, boolean pixelated) {
      int prevTexture = GL11.glGetInteger(32873);
      int prevUnpackBuffer = GL11.glGetInteger(35055);
      int prevAlignment = GL11.glGetInteger(3317);
      int prevRowLength = GL11.glGetInteger(3314);
      int prevSkipPixels = GL11.glGetInteger(3316);
      int prevSkipRows = GL11.glGetInteger(3315);

      int var11;
      try {
         GL15.glBindBuffer(35052, 0);
         GL11.glPixelStorei(3317, 4);
         GL11.glPixelStorei(3314, 0);
         GL11.glPixelStorei(3316, 0);
         GL11.glPixelStorei(3315, 0);
         int id = GL11.glGenTextures();
         GL11.glBindTexture(3553, id);
         GL11.glTexParameteri(3553, 10241, pixelated ? 9728 : 9987);
         GL11.glTexParameteri(3553, 10240, pixelated ? 9728 : 9729);
         GL11.glTexParameteri(3553, 10242, 33071);
         GL11.glTexParameteri(3553, 10243, 33071);
         GL11.glTexImage2D(3553, 0, 32856, width, height, 0, 6408, 5121, pixels);
         if (!pixelated) {
            GL30.glGenerateMipmap(3553);
         }

         var11 = id;
      } finally {
         GL11.glBindTexture(3553, prevTexture);
         GL15.glBindBuffer(35052, prevUnpackBuffer);
         GL11.glPixelStorei(3317, prevAlignment);
         GL11.glPixelStorei(3314, prevRowLength);
         GL11.glPixelStorei(3316, prevSkipPixels);
         GL11.glPixelStorei(3315, prevSkipRows);
      }

      return var11;
   }

   @Environment(EnvType.CLIENT)
   public record Texture(int id, int width, int height) {
   }
}
