package net.spectre.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientBrand {
   public static final String NAME = "Spectre";
   public static final String WINDOW_TITLE = "Spectre Client";
   public static final String LOGGER_NAME = "Spectre";

   private ClientBrand() {
   }
}
