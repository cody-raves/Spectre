package net.spectre.client.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.spectre.client.config.ModuleConfigStore;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AddonManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   public static final String ENTRYPOINT = "spectre";
   private static final String CLIENT_PACKAGE = "net.spectre.client";
   private static List<SpectreAddon> addons;
   private static boolean configLoaded;

   private AddonManager() {
   }

   public static List<SpectreAddon> addons() {
      List<SpectreAddon> loaded = addons;
      return loaded != null ? loaded : List.of();
   }

   public static void init() {
      if (addons == null) {
         List<SpectreAddon> loaded = create();
         loaded.removeIf(addonx -> !run(addonx, "register its categories", SpectreAddon::onRegisterCategories));
         loaded.removeIf(addonx -> {
            if (run(addonx, "initialise", SpectreAddon::onInitialize)) {
               return false;
            } else {
               removeModules(addonx);
               return true;
            }
         });

         for (SpectreAddon addon : loaded) {
            LOGGER.info(
               "[Spectre] Loaded addon {} v{} by {}",
               new Object[]{addon.name(), addon.version(), addon.authors().isEmpty() ? "unknown" : String.join(", ", addon.authors())}
            );
         }

         addons = List.copyOf(loaded);
      }
   }

   public static void configLoaded() {
      if (addons != null && !configLoaded) {
         configLoaded = true;
         List<SpectreAddon> loaded = new ArrayList<>(addons);
         loaded.removeIf(addon -> {
            if (run(addon, "start", SpectreAddon::onConfigLoaded)) {
               return false;
            } else {
               removeModules(addon);
               return true;
            }
         });
         addons = List.copyOf(loaded);
      }
   }

   private static List<SpectreAddon> create() {
      List<SpectreAddon> created = new ArrayList<>();

      List<EntrypointContainer<SpectreAddon>> containers;
      try {
         containers = FabricLoader.getInstance().getEntrypointContainers("spectre", SpectreAddon.class);
      } catch (LinkageError | RuntimeException var8) {
         LOGGER.error("[Spectre] Could not list addons; starting without them", var8);
         return created;
      }

      for (EntrypointContainer<SpectreAddon> container : containers) {
         ModMetadata metadata = container.getProvider().getMetadata();

         SpectreAddon addon;
         String pkg;
         try {
            addon = (SpectreAddon)container.getEntrypoint();
            pkg = addon.getPackage();
         } catch (LinkageError | RuntimeException var9) {
            LOGGER.error("[Spectre] Addon {} ({}) could not be created and is skipped", new Object[]{metadata.getName(), metadata.getId(), var9});
            continue;
         }

         addon.bind(metadata);
         String problem = packageProblem(addon, pkg, created);
         if (problem != null) {
            LOGGER.error("[Spectre] Addon {} is skipped: {}", addon.name(), problem);
         } else {
            addon.packageName = pkg;
            created.add(addon);
         }
      }

      return created;
   }

   private static String packageProblem(SpectreAddon addon, String pkg, List<SpectreAddon> created) {
      if (pkg != null && addon.getClass().getName().startsWith(pkg + ".")) {
         if (overlap(pkg, "net.spectre.client")) {
            return "its package " + pkg + " overlaps Spectre' own (net.spectre.client)";
         } else {
            for (SpectreAddon other : created) {
               if (overlap(pkg, other.packageName)) {
                  return "its package "
                     + pkg
                     + " overlaps "
                     + other.name()
                     + "'s ("
                     + other.packageName
                     + "); an addon made from the template needs its package renamed";
               }
            }

            return null;
         }
      } else {
         return "getPackage() is \"" + pkg + "\", which doesn't hold its class " + addon.getClass().getName();
      }
   }

   private static boolean overlap(String a, String b) {
      return (a + ".").startsWith(b + ".") || (b + ".").startsWith(a + ".");
   }

   private static boolean run(SpectreAddon addon, String step, Consumer<SpectreAddon> action) {
      try {
         action.accept(addon);
         return true;
      } catch (LinkageError | RuntimeException var4) {
         LOGGER.error("[Spectre] Addon {} failed to {} and is skipped{}", new Object[]{addon.name(), step, reportHint(addon), var4});
         return false;
      }
   }

   private static String reportHint(SpectreAddon addon) {
      try {
         SpectreAddon.GithubRepo repo = addon.getRepo();
         String where = repo != null ? repo.url() + "/issues" : addon.getWebsite();
         return where != null ? " - report it at " + where : "";
      } catch (LinkageError | RuntimeException var3) {
         return "";
      }
   }

   private static void removeModules(SpectreAddon addon) {
      String prefix = addon.packageName + ".";

      for (ClientModule module : ModuleRegistry.all()) {
         if (module.getClass().getName().startsWith(prefix)) {
            ModuleConfigStore.getInstance().release(module);
            if (module.isEnabled()) {
               module.setEnabled(false);
            }

            ModuleRegistry.remove(module);
         }
      }
   }
}
