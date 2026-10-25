package net.spectre.client.module.impl.render.character;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.spectre.client.module.impl.render.character.gltf.CharacterBake;
import net.spectre.client.module.impl.render.character.gltf.ImageDecoder;
import net.spectre.client.module.impl.render.character.kit.BakedDesign;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class CustomCharacters {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final long MAX_FILE = 268435456L;
   private static final int MAX_TEXTURE = 8192;

   private CustomCharacters() {
   }

   static Path folder() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("characters");
   }

   private static Path cache() {
      return folder().resolve(".baked");
   }

   static List<BakedDesign> scan() {
      List<Path> files = new ArrayList<>();

      try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder(), "*.{glb,GLB}")) {
         for (Path file : dir) {
            if (Files.isRegularFile(file)) {
               files.add(file);
            }
         }
      } catch (IOException var11) {
         return List.of();
      }

      files.sort(null);
      List<BakedDesign> out = new ArrayList<>();
      Set<String> ids = new HashSet<>();

      for (Path filex : files) {
         String name = filex.getFileName().toString();
         name = name.substring(0, name.length() - 4);

         try {
            String stamp = name + Files.size(filex) + Files.getLastModifiedTime(filex).toMillis() + "3";
            String id = "custom_" + slug(name) + "_" + Integer.toHexString(stamp.hashCode());
            ids.add(id);
            out.add(new BakedDesign(id, name, () -> open(filex, id)));
         } catch (IOException var9) {
            LOGGER.warn("[Spectre] Skipping custom character {}: {}", filex, var9.toString());
         }
      }

      prune(ids);
      return out;
   }

   private static String slug(String name) {
      String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
      return s.isEmpty() ? "model" : (s.length() > 32 ? s.substring(0, 32) : s);
   }

   private static void prune(Set<String> keep) {
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(cache(), "*.anc")) {
         for (Path file : dir) {
            String id = file.getFileName().toString().replace(".anc", "");
            if (!keep.contains(id)) {
               Files.deleteIfExists(file);
            }
         }
      } catch (IOException var7) {
      }
   }

   private static synchronized InputStream open(Path file, String id) throws IOException {
      Path baked = cache().resolve(id + ".anc");
      if (Files.exists(baked)) {
         return Files.newInputStream(baked);
      } else if (Files.size(file) > 268435456L) {
         throw new IOException("the file is larger than 256 MB");
      } else {
         String name = file.getFileName().toString();
         CharacterModule.baking(name);
         long start = System.nanoTime();
         CharacterBake.Result result = CharacterBake.bake(Files.readAllBytes(file), new CharacterBake.Options(), CustomCharacters::decode);
         LOGGER.info("[Spectre] Baked custom character {} in {} ms, moving {}", new Object[]{name, (System.nanoTime() - start) / 1000000L, result.limbs()});
         if (result.limbs().size() < 3) {
            CharacterModule.notice(name, result.limbs());
         }

         Files.createDirectories(cache());
         Path temp = cache().resolve(id + ".tmp");
         Files.write(temp, result.data());
         Files.move(temp, baked, StandardCopyOption.REPLACE_EXISTING);
         return new ByteArrayInputStream(result.data());
      }
   }

   private static ImageDecoder.Picture decode(byte[] data) throws IOException {
      ByteBuffer encoded = MemoryUtil.memAlloc(data.length);

      ImageDecoder.Picture var29;
      try {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            encoded.put(data).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer rgba = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
            if (rgba == null) {
               throw new IOException("a texture can't be read: " + STBImage.stbi_failure_reason());
            }

            try {
               int width = w.get(0);
               int height = h.get(0);
               if (width > 8192 || height > 8192) {
                  throw new IOException("a texture is over 8192 px");
               }

               int[] argb = new int[width * height];

               for (int i = 0; i < argb.length; i++) {
                  int r = rgba.get(i * 4) & 255;
                  int g = rgba.get(i * 4 + 1) & 255;
                  int b = rgba.get(i * 4 + 2) & 255;
                  int a = rgba.get(i * 4 + 3) & 255;
                  argb[i] = a << 24 | r << 16 | g << 8 | b;
               }

               var29 = new ImageDecoder.Picture(width, height, argb);
            } finally {
               STBImage.stbi_image_free(rgba);
            }
         } catch (Throwable var27) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var25) {
                  var27.addSuppressed(var25);
               }
            }

            throw var27;
         }

         if (stack != null) {
            stack.close();
         }
      } finally {
         MemoryUtil.memFree(encoded);
      }

      return var29;
   }
}
