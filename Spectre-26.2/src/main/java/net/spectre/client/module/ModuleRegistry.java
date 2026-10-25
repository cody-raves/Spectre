package net.spectre.client.module;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ModuleRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final List<ModuleProvider> PROVIDERS = new ArrayList<>(List.of(new BuiltinModuleProvider()));
   private static final List<ClientModule> STAGED = new ArrayList<>();
   private static volatile ModuleList modules;
   private static final Map<String, ClientModule> byName = new ConcurrentHashMap<>();
   private static final Map<String, ClientModule> byId = new ConcurrentHashMap<>();
   private static volatile Reference2ReferenceOpenHashMap<Class<?>, ClientModule> byClass;
   private static volatile Reference2ReferenceOpenHashMap<ModuleCategory, ModuleList> byCategory;
   private static volatile ModuleList enabled = ModuleList.EMPTY;
   private static volatile ModuleRegistry.TypeIndex types = new ModuleRegistry.TypeIndex(List.of());
   private static int revision;

   private ModuleRegistry() {
   }

   public static synchronized void registerProvider(ModuleProvider provider) {
      if (modules != null) {
         throw new IllegalStateException("ModuleRegistry is frozen — providers must register during client init, before the first read");
      } else {
         PROVIDERS.add(provider);
      }
   }

   public static List<ClientModule> all() {
      List<ClientModule> m = modules;
      if (m == null) {
         freeze();
         m = modules;
      }

      return m;
   }

   public static ClientModule byName(String name) {
      all();
      return byName.get(name);
   }

   public static ClientModule resolve(String key) {
      all();
      ClientModule module = byId.get(key);
      return module != null ? module : byName.get(key);
   }

   public static <T extends ClientModule> T get(Class<T> type) {
      Reference2ReferenceOpenHashMap<Class<?>, ClientModule> classes = byClass;
      if (classes == null) {
         all();
         classes = byClass;
      }

      return (T)classes.get(type);
   }

   public static List<ClientModule> inCategory(ModuleCategory category) {
      all();
      return category(category);
   }

   public static List<ClientModule> enabled() {
      all();
      return enabled;
   }

   public static <T> List<T> instancesOf(Class<T> type) {
      List<ClientModule> current = all();
      ModuleRegistry.TypeIndex index = types;
      if (index.source() != current) {
         index = new ModuleRegistry.TypeIndex(current);
         types = index;
      }

      List<?> list = index.lists().get(type);
      if (list == null) {
         List<T> matches = new ArrayList<>();

         for (ClientModule module : current) {
            if (type.isInstance(module)) {
               matches.add((T)module);
            }
         }

         list = List.copyOf(matches);
         index.lists().put(type, list);
      }

      return (List<T>)list;
   }

   public static int revision() {
      all();
      return revision;
   }

   public static synchronized boolean add(ClientModule module) {
      if (modules != null) {
         if (!byId.containsKey(module.id()) && !byName.containsKey(module.name())) {
            module.captureDefaults();
            byName.put(module.name(), module);
            byId.put(module.id(), module);
            if (!byClass.containsKey(module.getClass())) {
               Reference2ReferenceOpenHashMap<Class<?>, ClientModule> classes = byClass.clone();
               classes.put(module.getClass(), module);
               byClass = classes;
            }

            replaceCategory(module.category(), category(module.category()).with(module));
            module.registered = true;
            if (module.isEnabled()) {
               enabled = enabled.with(module);
            }

            publish(modules.with(module));
            return true;
         } else {
            return false;
         }
      } else {
         for (ClientModule staged : STAGED) {
            if (staged.id().equals(module.id()) || staged.name().equals(module.name())) {
               return false;
            }
         }

         STAGED.add(module);
         return true;
      }
   }

   public static synchronized void remove(ClientModule module) {
      all();
      if (byId.get(module.id()) == module) {
         ModuleList next = modules.without(module);
         byName.remove(module.name(), module);
         byId.remove(module.id(), module);
         if (byClass.get(module.getClass()) == module) {
            Reference2ReferenceOpenHashMap<Class<?>, ClientModule> classes = byClass.clone();
            classes.remove(module.getClass());

            for (ClientModule other : next) {
               if (other.getClass() == module.getClass()) {
                  classes.put(other.getClass(), other);
                  break;
               }
            }

            byClass = classes;
         }

         replaceCategory(module.category(), category(module.category()).without(module));
         module.registered = false;
         enabled = enabled.without(module);
         publish(next);
      }
   }

   static synchronized void switched(ClientModule module) {
      if (module.registered) {
         if (module.isEnabled()) {
            if (!enabled.containsIdentity(module)) {
               enabled = enabled.with(module);
            }
         } else {
            enabled = enabled.without(module);
         }
      }
   }

   private static synchronized void freeze() {
      if (modules == null) {
         List<ClientModule> collected = new ArrayList<>();
         Set<String> names = new HashSet<>();
         Set<String> ids = new HashSet<>();

         for (ModuleProvider provider : PROVIDERS) {
            int before = collected.size();

            for (ClientModule module : provider.createModules()) {
               if (ids.contains(module.id())) {
                  throw new IllegalStateException("Duplicate module id: " + module.id());
               }

               if (!names.add(module.name())) {
                  LOGGER.warn("[Spectre] Duplicate module name '{}' from provider '{}' — skipped", module.name(), provider.id());
               } else {
                  module.captureDefaults();
                  collected.add(module);
                  ids.add(module.id());
               }
            }

            LOGGER.info("[Spectre] Module provider '{}' contributed {} modules", provider.id(), collected.size() - before);
         }

         int before = collected.size();

         for (ClientModule module : STAGED) {
            if (!ids.contains(module.id()) && names.add(module.name())) {
               module.captureDefaults();
               collected.add(module);
               ids.add(module.id());
            } else {
               LOGGER.warn("[Spectre] Module '{}' ({}) clashes with a provided module's name or id — skipped", module.name(), module.id());
            }
         }

         if (!STAGED.isEmpty()) {
            LOGGER.info("[Spectre] Addons contributed {} modules", collected.size() - before);
         }

         STAGED.clear();
         Reference2ReferenceOpenHashMap<Class<?>, ClientModule> classes = new Reference2ReferenceOpenHashMap();
         Map<ModuleCategory, List<ClientModule>> grouped = new LinkedHashMap<>();
         List<ClientModule> on = new ArrayList<>();

         for (ClientModule modulex : collected) {
            byName.put(modulex.name(), modulex);
            byId.put(modulex.id(), modulex);
            classes.putIfAbsent(modulex.getClass(), modulex);
            grouped.computeIfAbsent(modulex.category(), category -> new ArrayList<>()).add(modulex);
            modulex.registered = true;
            if (modulex.isEnabled()) {
               on.add(modulex);
            }
         }

         Reference2ReferenceOpenHashMap<ModuleCategory, ModuleList> categories = new Reference2ReferenceOpenHashMap();
         grouped.forEach((category, list) -> categories.put(category, ModuleList.of(list)));
         byClass = classes;
         byCategory = categories;
         enabled = ModuleList.of(on);
         publish(ModuleList.of(collected));
      }
   }

   private static void publish(ModuleList ordered) {
      revision++;
      ModuleRevision.changed();
      modules = ordered;
   }

   private static ModuleList category(ModuleCategory category) {
      ModuleList list = (ModuleList)byCategory.get(category);
      return list != null ? list : ModuleList.EMPTY;
   }

   private static void replaceCategory(ModuleCategory category, ModuleList list) {
      Reference2ReferenceOpenHashMap<ModuleCategory, ModuleList> categories = byCategory.clone();
      if (list.isEmpty()) {
         categories.remove(category);
      } else {
         categories.put(category, list);
      }

      byCategory = categories;
   }

   @Environment(EnvType.CLIENT)
   private record TypeIndex(List<ClientModule> source, Map<Class<?>, List<?>> lists) {
      TypeIndex(List<ClientModule> source) {
         this(source, new ConcurrentHashMap<>());
      }
   }
}
