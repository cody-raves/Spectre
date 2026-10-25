package net.spectre.client.proxy;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum ProxyType {
   SOCKS4("Socks4"),
   SOCKS5("Socks5");

   private final String label;

   private ProxyType(String label) {
      this.label = label;
   }

   public String label() {
      return this.label;
   }

   static ProxyType parse(String text) {
      if (text == null) {
         return null;
      } else {
         String var1 = text.trim().toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "socks4", "socks4a" -> SOCKS4;
            case "socks5", "socks5h", "socks" -> SOCKS5;
            default -> null;
         };
      }
   }
}
