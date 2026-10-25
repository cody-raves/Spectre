package net.spectre.client.util.antivanish;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AntiVanishText {
   private AntiVanishText() {
   }

   public static String normalize(String text) {
      if (text != null && !text.isBlank()) {
         String decomposed = Normalizer.normalize(stripLegacyCodes(text), Form.NFKD).toLowerCase(Locale.ROOT);
         StringBuilder out = new StringBuilder(decomposed.length());
         boolean spaced = true;
         int offset = 0;

         while (offset < decomposed.length()) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isMark(codePoint) && !isFormatCode(codePoint)) {
               int folded = foldConfusable(codePoint);
               if ((folded < 97 || folded > 122) && (folded < 48 || folded > 57)) {
                  if (!spaced) {
                     out.append(' ');
                     spaced = true;
                  }
               } else {
                  out.appendCodePoint(folded);
                  spaced = false;
               }
            }
         }

         offset = out.length();
         if (offset > 0 && out.charAt(offset - 1) == ' ') {
            out.setLength(offset - 1);
         }

         return out.toString();
      } else {
         return "";
      }
   }

   public static boolean containsPlayerName(String displayedName, String playerName) {
      String rawDisplay = stripLegacyCodes(displayedName == null ? "" : displayedName).toLowerCase(Locale.ROOT);
      String rawPlayer = stripLegacyCodes(playerName == null ? "" : playerName).trim().toLowerCase(Locale.ROOT);
      if (rawPlayer.matches("[a-z0-9_]{1,16}")) {
         int from = 0;

         while (from <= rawDisplay.length() - rawPlayer.length()) {
            int match = rawDisplay.indexOf(rawPlayer, from);
            if (match < 0) {
               return false;
            }

            int end = match + rawPlayer.length();
            boolean leftBoundary = match == 0 || !isUsernameCharacter(rawDisplay.charAt(match - 1));
            boolean rightBoundary = end == rawDisplay.length() || !isUsernameCharacter(rawDisplay.charAt(end));
            if (leftBoundary && rightBoundary) {
               return true;
            }

            from = match + 1;
         }

         return false;
      } else {
         String displayed = normalize(displayedName);
         String player = normalize(playerName);
         return !displayed.isBlank() && !player.isBlank() ? displayed.equals(player) || (" " + displayed + " ").contains(" " + player + " ") : false;
      }
   }

   public static boolean looksLikeLeaveMessage(String message, String playerName) {
      if (!containsPlayerName(message, playerName)) {
         return false;
      } else {
         String text = " " + normalize(message) + " ";
         String player = normalize(playerName);
         if (player.isBlank()) {
            return false;
         } else {
            String prefix = " " + player + " ";
            return text.contains(prefix + "left the game ")
               || text.contains(prefix + "left ")
               || text.contains(prefix + "has left ")
               || text.contains(prefix + "quit ")
               || text.contains(prefix + "has quit ")
               || text.contains(prefix + "disconnected ")
               || text.contains(prefix + "logged out ");
         }
      }
   }

   public static boolean isPlausiblePlayerName(String name) {
      if (name == null) {
         return false;
      } else {
         String trimmed = name.trim();
         int len = trimmed.length();
         return len >= 3 && len <= 16 ? trimmed.indexOf(32) < 0 : false;
      }
   }

   public static boolean isUsername(String name) {
      if (name == null) {
         return false;
      } else {
         int len = name.length();
         if (len >= 2 && len <= 32) {
            boolean hasAlnum = false;

            for (int i = 0; i < len; i++) {
               char c = name.charAt(i);
               boolean alnum = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
               if (alnum) {
                  hasAlnum = true;
               } else if (c != '_' && c != '.' && c != ' ') {
                  return false;
               }
            }

            return hasAlnum;
         } else {
            return false;
         }
      }
   }

   private static boolean isUsernameCharacter(char character) {
      return character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '_';
   }

   private static boolean isMark(int codePoint) {
      int type = Character.getType(codePoint);
      return type == 6 || type == 8 || type == 7;
   }

   private static boolean isFormatCode(int codePoint) {
      return codePoint == 167 || codePoint == 8203 || codePoint == 8204 || codePoint == 8205 || codePoint == 65279;
   }

   private static String stripLegacyCodes(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
               char code = Character.toLowerCase(text.charAt(i + 1));
               if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                  i++;
                  continue;
               }
            }

            out.append(c);
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private static int foldConfusable(int codePoint) {
      return switch (codePoint) {
         case 593, 945, 1072, 7424 -> 97;
         case 609, 610 -> 103;
         case 618, 953, 1110 -> 105;
         case 628, 1400 -> 110;
         case 640 -> 114;
         case 655, 1091 -> 121;
         case 665, 946, 1074 -> 98;
         case 668, 1085 -> 104;
         case 671 -> 108;
         case 949, 1077, 7431 -> 101;
         case 954, 1082, 7435 -> 107;
         case 957, 7456 -> 118;
         case 959, 1086, 7439 -> 111;
         case 961, 1088, 7448 -> 112;
         case 964, 1090, 7451 -> 116;
         case 965, 7452 -> 117;
         case 1010, 1089, 7428 -> 99;
         case 1084, 7437 -> 109;
         case 1093 -> 120;
         case 1109, 42801 -> 115;
         case 1112, 7434 -> 106;
         case 1121, 7457 -> 119;
         case 1281, 7429 -> 100;
         case 1307 -> 113;
         case 7458, 7459 -> 122;
         case 42800 -> 102;
         default -> codePoint;
      };
   }
}
