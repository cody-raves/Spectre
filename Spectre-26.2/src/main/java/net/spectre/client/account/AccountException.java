package net.spectre.client.account;

import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class AccountException extends CompletionException {
   AccountException(String message) {
      super(message);
   }

   AccountException(String message, Throwable cause) {
      super(message, cause);
   }
}
