package net.spectre.client.config;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import java.net.IDN;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ServerAddresses {
   public static final int DEFAULT_PORT = 25565;
   private static final int MAX_HOST = 253;
   private static final Pattern HOST = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");
   private static final Set<String> FRONT_DOORS = Set.of("play", "mc", "www", "join", "hub", "lobby", "server", "connect", "go");

   private ServerAddresses() {
   }

   public static ServerAddresses.Address parse(String text) {
      if (text == null) {
         return null;
      } else {
         String trimmed = text.trim();
         if (!trimmed.isEmpty() && trimmed.length() <= 261) {
            try {
               HostAndPort parsed = HostAndPort.fromString(trimmed).withDefaultPort(25565);
               String host = parsed.getHost().toLowerCase(Locale.ROOT);

               while (host.endsWith(".")) {
                  host = host.substring(0, host.length() - 1);
               }

               if (host.isEmpty()) {
                  return null;
               } else {
                  if (!InetAddresses.isInetAddress(host)) {
                     host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
                     if (host.length() > 253 || !HOST.matcher(host).matches()) {
                        return null;
                     }
                  }

                  return new ServerAddresses.Address(host, parsed.getPort());
               }
            } catch (IllegalStateException | IllegalArgumentException var4) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   public static int score(ServerAddresses.Address entry, ServerAddresses.Address joined) {
      if (entry != null && joined != null && entry.port() == joined.port()) {
         String listed = entry.host();
         String actual = joined.host();
         if (listed.equals(actual)) {
            return 3;
         } else if (!InetAddresses.isInetAddress(listed) && !InetAddresses.isInetAddress(actual)) {
            String site = site(listed);
            if (site.equals(site(actual))) {
               return 2;
            } else {
               return actual.endsWith("." + site) ? 1 : 0;
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static String site(String host) {
      int dot = host.indexOf(46);
      if (dot > 0 && FRONT_DOORS.contains(host.substring(0, dot))) {
         String rest = host.substring(dot + 1);

         try {
            return InternetDomainName.from(rest).isUnderPublicSuffix() ? rest : host;
         } catch (IllegalArgumentException var4) {
            return rest.indexOf(46) > 0 ? rest : host;
         }
      } else {
         return host;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Address(String host, int port) {
      @Override
      public String toString() {
         String shown = this.host.indexOf(58) >= 0 ? "[" + this.host + "]" : this.host;
         return this.port == 25565 ? shown : shown + ":" + this.port;
      }
   }
}
