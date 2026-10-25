package net.spectre.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ProfileException extends Exception {
   public ProfileException(String message) {
      super(message);
   }
}
