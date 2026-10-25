package net.spectre.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class WindowIcon {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int[] SIZES = new int[]{16, 32, 48, 64, 128, 256};

   private WindowIcon() {
   }

   public static void apply(long window) {
      int platform = GLFW.glfwGetPlatform();
      if (platform == 393217 || platform == 393220) {
         List<ByteBuffer> pixels = new ArrayList<>(SIZES.length);

         try {
            MemoryStack stack = MemoryStack.stackPush();

            label130: {
               try {
                  Buffer images = GLFWImage.malloc(SIZES.length, stack);
                  int count = 0;

                  for (int size : SIZES) {
                     ByteBuffer image = load("/assets/spectre/icons/icon_" + size + ".png", stack);
                     if (image != null) {
                        pixels.add(image);
                        images.position(count++);
                        images.width(size);
                        images.height(size);
                        images.pixels(image);
                     }
                  }

                  if (count == 0) {
                     break label130;
                  }

                  images.position(0);
                  images.limit(count);
                  GLFW.glfwSetWindowIcon(window, images);
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

               return;
            }

            if (stack != null) {
               stack.close();
            }
         } finally {
            pixels.forEach(STBImage::stbi_image_free);
         }
      }
   }

   private static ByteBuffer load(String resource, MemoryStack stack) {
      try {
         ByteBuffer var8;
         try (InputStream in = WindowIcon.class.getResourceAsStream(resource)) {
            if (in == null) {
               return null;
            }

            byte[] bytes = in.readAllBytes();
            ByteBuffer file = MemoryUtil.memAlloc(bytes.length);

            try {
               file.put(bytes).flip();
               IntBuffer w = stack.mallocInt(1);
               IntBuffer h = stack.mallocInt(1);
               IntBuffer channels = stack.mallocInt(1);
               var8 = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
            } finally {
               MemoryUtil.memFree(file);
            }
         }

         return var8;
      } catch (RuntimeException | IOException var17) {
         LOGGER.warn("[Spectre+] Failed to load window icon {}", resource, var17);
         return null;
      }
   }
}
