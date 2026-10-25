package net.spectre.client.gui.mainmenu;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.spectre.client.gui.ThemeImages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class MenuTextures {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final Map<String, ThemeImages.Texture> TEXTURES = new HashMap<>();

   private MenuTextures() {
   }

   static ThemeImages.Texture original(String resource) {
      return cached(resource, resource, UnaryOperator.identity());
   }

   static ThemeImages.Texture mask(String resource) {
      return cached(resource + "#mask", resource, p -> {
         int[] out = new int[p.argb().length];

         for (int i = 0; i < out.length; i++) {
            out[i] = p.argb()[i] & 0xFF000000 | 16777215;
         }

         return new MenuTextures.Pixels(p.width(), p.height(), out);
      });
   }

   static ThemeImages.Texture blurred(String resource, int radius) {
      return cached(resource + "#blur" + radius, resource, p -> blur(p, radius));
   }

   private static ThemeImages.Texture cached(String key, String resource, UnaryOperator<MenuTextures.Pixels> variant) {
      if (TEXTURES.containsKey(key)) {
         return TEXTURES.get(key);
      } else {
         ThemeImages.Texture texture = null;

         try (InputStream in = MenuTextures.class.getResourceAsStream(resource)) {
            if (in == null) {
               LOGGER.warn("[Spectre] Missing menu image {}", resource);
            } else {
               MenuTextures.Pixels p = decode(in.readAllBytes(), resource);
               if (p != null) {
                  p = variant.apply(p);
                  texture = upload(p.argb(), p.width(), p.height());
               }
            }
         } catch (Exception var9) {
            LOGGER.warn("[Spectre] Failed to load menu image {}", key, var9);
         }

         TEXTURES.put(key, texture);
         return texture;
      }
   }

   private static MenuTextures.Pixels blur(MenuTextures.Pixels p, int radius) {
      int pad = radius * 2;
      int w = p.width() + pad * 2;
      int h = p.height() + pad * 2;
      float[] alpha = new float[w * h];

      for (int y = 0; y < p.height(); y++) {
         for (int x = 0; x < p.width(); x++) {
            alpha[(y + pad) * w + x + pad] = (p.argb()[y * p.width() + x] >>> 24) / 255.0F;
         }
      }

      for (int pass = 0; pass < 3; pass++) {
         alpha = box(alpha, w, h, radius, true);
         alpha = box(alpha, w, h, radius, false);
      }

      int[] out = new int[w * h];

      for (int i = 0; i < out.length; i++) {
         out[i] = Math.round(Math.min(1.0F, alpha[i]) * 255.0F) << 24 | 16777215;
      }

      return new MenuTextures.Pixels(w, h, out);
   }

   private static float[] box(float[] in, int w, int h, int radius, boolean across) {
      float[] out = new float[in.length];
      float norm = 1.0F / (radius * 2 + 1);

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float sum = 0.0F;

            for (int k = -radius; k <= radius; k++) {
               int sx = across ? x + k : x;
               int sy = across ? y : y + k;
               if (sx >= 0 && sy >= 0 && sx < w && sy < h) {
                  sum += in[sy * w + sx];
               }
            }

            out[y * w + x] = sum * norm;
         }
      }

      return out;
   }

   private static MenuTextures.Pixels decode(byte[] bytes, String name) {
      ByteBuffer file = null;
      ByteBuffer rgba = null;

      try {
         MemoryStack stack = MemoryStack.stackPush();

         Object var23;
         label148: {
            MenuTextures.Pixels var24;
            try {
               file = MemoryUtil.memAlloc(bytes.length);
               file.put(bytes).flip();
               IntBuffer w = stack.mallocInt(1);
               IntBuffer h = stack.mallocInt(1);
               IntBuffer channels = stack.mallocInt(1);
               rgba = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
               if (rgba == null) {
                  LOGGER.warn("[Spectre] Can't decode {}: {}", name, STBImage.stbi_failure_reason());
                  var23 = null;
                  break label148;
               }

               int width = w.get(0);
               int height = h.get(0);
               int[] argb = new int[width * height];

               for (int i = 0; i < argb.length; i++) {
                  int r = rgba.get(i * 4) & 255;
                  int g = rgba.get(i * 4 + 1) & 255;
                  int b = rgba.get(i * 4 + 2) & 255;
                  int a = rgba.get(i * 4 + 3) & 255;
                  argb[i] = a << 24 | r << 16 | g << 8 | b;
               }

               var24 = new MenuTextures.Pixels(width, height, argb);
            } catch (Throwable var21) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var20) {
                     var21.addSuppressed(var20);
                  }
               }

               throw var21;
            }

            if (stack != null) {
               stack.close();
            }

            return var24;
         }

         if (stack != null) {
            stack.close();
         }

         return (MenuTextures.Pixels)var23;
      } finally {
         if (rgba != null) {
            STBImage.stbi_image_free(rgba);
         }

         if (file != null) {
            MemoryUtil.memFree(file);
         }
      }
   }

   private static ThemeImages.Texture upload(int[] argb, int width, int height) {
      ByteBuffer buffer = MemoryUtil.memAlloc(argb.length * 4);

      ThemeImages.Texture var11;
      try {
         for (int c : argb) {
            buffer.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c).put((byte)(c >>> 24));
         }

         buffer.flip();
         var11 = ThemeImages.fromPixels(buffer, width, height, false);
      } finally {
         MemoryUtil.memFree(buffer);
      }

      return var11;
   }

   @Environment(EnvType.CLIENT)
   private record Pixels(int width, int height, int[] argb) {
   }
}
