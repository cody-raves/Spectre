package net.spectre.client.module.impl.misc.nameprotect;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AliasPolicy {
   public static final int MAX_LENGTH = 16;
   private static final int MIN_GLUED_NAME = 4;
   private static final String[] STAFF_FRAGMENTS = skeletons(
      "admin", "owner", "moderat", "staff", "helper", "founder", "manager", "develop", "console", "support", "operator", "official", "donutsmp"
   );
   private static final Set<String> STAFF_WORDS = Set.of(
      skeletons("mod", "mods", "srmod", "jrmod", "trialmod", "headmod", "dev", "devs", "op", "ops", "server", "system", "builder")
   );

   private AliasPolicy() {
   }

   public static String sanitize(String raw) {
      if (raw == null) {
         return "";
      } else {
         StringBuilder clean = new StringBuilder(Math.min(raw.length(), 16));

         for (int i = 0; i < raw.length() && clean.length() < 16; i++) {
            char c = raw.charAt(i);
            if (c == 167) {
               i++;
            } else if (NameMatcher.isNameChar(c)) {
               clean.append(c);
            } else if (clean.length() > 0 && clean.charAt(clean.length() - 1) != ' ') {
               clean.append(' ');
            }
         }

         return clean.toString().trim();
      }
   }

   public static String skeleton(CharSequence text) {
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = Character.toLowerCase(text.charAt(i));
         switch (c) {
            case ' ':
            case '_':
               break;
            case '0':
               out.append('o');
               break;
            case '1':
            case 'i':
               out.append('l');
               break;
            case '3':
               out.append('e');
               break;
            case '4':
               out.append('a');
               break;
            case '5':
               out.append('s');
               break;
            case '7':
               out.append('t');
               break;
            default:
               out.append(c);
         }
      }

      return out.toString();
   }

   public static AliasPolicy.Refusal check(String alias, Predicate<String> taken) {
      String compact = skeleton(alias);

      for (String fragment : STAFF_FRAGMENTS) {
         if (compact.contains(fragment)) {
            return AliasPolicy.Refusal.STAFF_TITLE;
         }
      }

      if (STAFF_WORDS.contains(compact)) {
         return AliasPolicy.Refusal.STAFF_TITLE;
      } else if (taken.test(compact)) {
         return AliasPolicy.Refusal.PLAYER_NAME;
      } else {
         for (int length = 4; length < compact.length(); length++) {
            if (taken.test(compact.substring(0, length)) || taken.test(compact.substring(compact.length() - length))) {
               return AliasPolicy.Refusal.PLAYER_NAME;
            }
         }

         for (String word : alias.split("[ _]+")) {
            if (!word.isEmpty()) {
               String wordSkeleton = skeleton(word);
               if (STAFF_WORDS.contains(wordSkeleton)) {
                  return AliasPolicy.Refusal.STAFF_TITLE;
               }

               if (taken.test(wordSkeleton)) {
                  return AliasPolicy.Refusal.PLAYER_NAME;
               }
            }
         }

         return null;
      }
   }

   private static String[] skeletons(String... words) {
      String[] out = new String[words.length];

      for (int i = 0; i < words.length; i++) {
         out[i] = skeleton(words[i].toLowerCase(Locale.ROOT));
      }

      return out;
   }

   @Environment(EnvType.CLIENT)
   public static enum Refusal {
      STAFF_TITLE("it reads like a staff or server title"),
      PLAYER_NAME("it is the name of a player seen on this server");

      private final String reason;

      private Refusal(String reason) {
         this.reason = reason;
      }

      public String reason() {
         return this.reason;
      }
   }
}
