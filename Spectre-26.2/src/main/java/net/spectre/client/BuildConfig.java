package net.spectre.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BuildConfig {
   public static final boolean DEBUG = false;

   private BuildConfig() {
   }
}
