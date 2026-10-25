package net.spectre.client.module.impl.render.character;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import net.spectre.client.module.impl.render.character.kit.CharacterBuilder;
import net.spectre.client.module.impl.render.character.kit.CharacterDesign;
import net.spectre.client.module.impl.render.character.kit.CharacterModel;
import net.spectre.client.module.impl.render.character.kit.Detail;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class CharacterCache {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int KEEP = 4;
   private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)), pool -> {
      ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
      thread.setName("Spectre Character Builder " + thread.getPoolIndex());
      thread.setDaemon(true);
      thread.setPriority(4);
      return thread;
   }, null, false);
   private static final Map<String, CharacterCache.Entry> ENTRIES = new LinkedHashMap<>(8, 0.75F, true);
   private static boolean listening;
   private static CharacterCache.Entry shown;

   private CharacterCache() {
   }

   static void listen() {
      if (!listening) {
         listening = true;
         ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(Identifier.fromNamespaceAndPath("spectre", "character_textures"), (ResourceManagerReloadListener)manager -> releaseTextures());
      }
   }

   static void prepare(CharacterDesign design, Detail detail) {
      String key = design.id() + "/" + detail.name();
      if (!ENTRIES.containsKey(key)) {
         CharacterCache.Entry created = new CharacterCache.Entry(design, detail);
         ENTRIES.put(key, created);
         trim();
         CompletableFuture.runAsync(() -> build(created), POOL);
      }
   }

   static WornCharacter ready(CharacterDesign design, Detail detail) {
      String key = design.id() + "/" + detail.name();
      CharacterCache.Entry entry = ENTRIES.get(key);
      if (entry == null) {
         CharacterCache.Entry created = new CharacterCache.Entry(design, detail);
         ENTRIES.put(key, created);
         trim();
         CompletableFuture.runAsync(() -> build(created), POOL);
         return previous();
      } else if (entry.worn != null) {
         shown = entry;
         return entry.worn;
      } else {
         CharacterModel model = entry.model;
         if (entry.failed) {
            return null;
         } else if (model == null) {
            return previous();
         } else {
            Minecraft.getInstance()
               .getTextureManager()
               .register(entry.texture, new CharacterTexture(entry.texture.toString(), model.atlasWidth, model.atlasHeight, model.atlasMips));
            entry.worn = new WornCharacter(model, entry.texture);
            shown = entry;
            return entry.worn;
         }
      }
   }

   private static WornCharacter previous() {
      CharacterCache.Entry last = shown;
      return last != null && ENTRIES.containsValue(last) ? last.worn : null;
   }

   private static void build(CharacterCache.Entry entry) {
      try {
         CharacterModel model = CharacterBuilder.build(entry.design, entry.detail);
         LOGGER.info(
            "[Spectre] 3D Character '{}' ({}) built: {} triangles in {} ms",
            new Object[]{entry.design.name(), entry.detail.label, model.triangles(), model.buildMillis}
         );
         entry.model = model;
      } catch (Throwable var2) {
         entry.failed = true;
         LOGGER.error("[Spectre] 3D Character '{}' ({}) failed to build", new Object[]{entry.design.name(), entry.detail.label, var2});
         CharacterModule.failed(entry.design, var2);
      }
   }

   private static void trim() {
      Iterator<CharacterCache.Entry> it = ENTRIES.values().iterator();

      while (ENTRIES.size() > 4 && it.hasNext()) {
         CharacterCache.Entry eldest = it.next();
         release(eldest);
         it.remove();
      }
   }

   static void releaseTextures() {
      for (CharacterCache.Entry entry : ENTRIES.values()) {
         release(entry);
      }
   }

   static void clear() {
      releaseTextures();
      ENTRIES.clear();
      shown = null;
   }

   private static void release(CharacterCache.Entry entry) {
      if (entry.worn != null) {
         entry.worn = null;
         Minecraft.getInstance().getTextureManager().release(entry.texture);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Entry {
      final CharacterDesign design;
      final Detail detail;
      final Identifier texture;
      volatile CharacterModel model;
      volatile boolean failed;
      WornCharacter worn;

      Entry(CharacterDesign design, Detail detail) {
         this.design = design;
         this.detail = detail;
         this.texture = Identifier.fromNamespaceAndPath("spectre", "character/" + design.id() + "_" + detail.name().toLowerCase(Locale.ROOT));
      }
   }
}
