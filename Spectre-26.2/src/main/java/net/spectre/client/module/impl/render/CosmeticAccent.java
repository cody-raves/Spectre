package net.spectre.client.module.impl.render;

import net.spectre.client.config.ThemeStore;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.client.HudModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class CosmeticAccent {
   private CosmeticAccent() {
   }

   static int argb() {
      HudModule hud = ModuleRegistry.get(HudModule.class);
      int argb = hud != null && hud.customAccent() ? hud.accentArgb() : ThemeStore.get().accent.argb();
      return argb | 0xFF000000;
   }
}
