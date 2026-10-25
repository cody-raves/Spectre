package net.spectre.client.util.spotify;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public final class AlbumArt {
   private static final int MAX_SOURCE_SIDE = 4096;
   private final int size;
   private final byte[] rgba;

   private AlbumArt(int size, byte[] rgba) {
      this.size = size;
      this.rgba = rgba;
   }

   public static AlbumArt decode(byte[] encoded, int maxSide) {
      if (encoded != null && encoded.length != 0 && maxSide > 0) {
         ByteBuffer file = MemoryUtil.memAlloc(encoded.length);
         ByteBuffer pixels = null;

         try {
            MemoryStack stack = MemoryStack.stackPush();

            AlbumArt var18;
            label211: {
               label212: {
                  label213: {
                     try {
                        file.put(encoded).flip();
                        IntBuffer w = stack.mallocInt(1);
                        IntBuffer h = stack.mallocInt(1);
                        IntBuffer channels = stack.mallocInt(1);
                        if (!STBImage.stbi_info_from_memory(file, w, h, channels)) {
                           var18 = null;
                           break label211;
                        }

                        if (w.get(0) > 0 && h.get(0) > 0 && w.get(0) <= 4096 && h.get(0) <= 4096) {
                           pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
                           if (pixels == null) {
                              var18 = null;
                              break label212;
                           }

                           var18 = crop(pixels, w.get(0), h.get(0), maxSide);
                           break label213;
                        }

                        var18 = null;
                     } catch (Throwable var14) {
                        if (stack != null) {
                           try {
                              stack.close();
                           } catch (Throwable var13) {
                              var14.addSuppressed(var13);
                           }
                        }

                        throw var14;
                     }

                     if (stack != null) {
                        stack.close();
                     }

                     return var18;
                  }

                  if (stack != null) {
                     stack.close();
                  }

                  return var18;
               }

               if (stack != null) {
                  stack.close();
               }

               return (AlbumArt)var18;
            }

            if (stack != null) {
               stack.close();
            }

            return var18;
         } finally {
            if (pixels != null) {
               STBImage.stbi_image_free(pixels);
            }

            MemoryUtil.memFree(file);
         }
      } else {
         return null;
      }
   }

   static AlbumArt crop(ByteBuffer pixels, int width, int height, int maxSide) {
      int side = Math.min(width, height);
      int left = (width - side) / 2;
      int top = (height - side) / 2;
      int size = Math.min(side, maxSide);
      byte[] out = new byte[size * size * 4];
      int o = 0;

      for (int oy = 0; oy < size; oy++) {
         int y0 = top + (int)((long)oy * side / size);
         int y1 = Math.max(y0 + 1, top + (int)((long)(oy + 1) * side / size));

         for (int ox = 0; ox < size; ox++) {
            int x0 = left + (int)((long)ox * side / size);
            int x1 = Math.max(x0 + 1, left + (int)((long)(ox + 1) * side / size));
            long r = 0L;
            long g = 0L;
            long b = 0L;
            long a = 0L;

            for (int y = y0; y < y1; y++) {
               int row = (y * width + x0) * 4;

               for (int x = x0; x < x1; row += 4) {
                  r += pixels.get(row) & 255;
                  g += pixels.get(row + 1) & 255;
                  b += pixels.get(row + 2) & 255;
                  a += pixels.get(row + 3) & 255;
                  x++;
               }
            }

            long count = (long)(y1 - y0) * (x1 - x0);
            out[o++] = (byte)((r + count / 2L) / count);
            out[o++] = (byte)((g + count / 2L) / count);
            out[o++] = (byte)((b + count / 2L) / count);
            out[o++] = (byte)((a + count / 2L) / count);
         }
      }

      return new AlbumArt(size, out);
   }

   public int size() {
      return this.size;
   }

   public int byteSize() {
      return this.rgba.length;
   }

   public void copyTo(ByteBuffer target) {
      target.put(this.rgba);
   }
}
