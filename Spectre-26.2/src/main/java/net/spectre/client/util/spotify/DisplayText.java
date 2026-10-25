package net.spectre.client.util.spotify;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.function.IntPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DisplayText {
   private DisplayText() {
   }

   public static String fold(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder out = null;
         boolean space = false;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = replacement(c);
            if (replacement != null || c == ' ' && space) {
               if (out == null) {
                  out = new StringBuilder(text.length()).append(text, 0, i);
               }

               if (replacement != null) {
                  for (int k = 0; k < replacement.length(); k++) {
                     char r = replacement.charAt(k);
                     if (r != ' ' || !space && !out.isEmpty()) {
                        out.append(r);
                        space = r == ' ';
                     }
                  }
               }
            } else {
               if (out != null) {
                  out.append(c);
               }

               space = c == ' ';
            }
         }

         if (out == null) {
            String stripped = text.strip();
            return stripped.length() == text.length() ? text : stripped;
         } else {
            return out.toString().strip();
         }
      } else {
         return "";
      }
   }

   public static String fold(String text, IntPredicate drawable) {
      String folded = fold(text);
      StringBuilder out = null;
      int i = 0;

      while (i < folded.length()) {
         int cp = folded.codePointAt(i);
         int next = i + Character.charCount(cp);
         String base = cp >= 128 && Character.isLetter(cp) && !drawable.test(cp) ? baseLetters(cp) : null;
         if (base != null && !base.codePoints().allMatch(b -> b < 128 || drawable.test(b))) {
            base = null;
         }

         if (base != null && out == null) {
            out = new StringBuilder(folded.length()).append(folded, 0, i);
         }

         if (out != null) {
            if (base != null) {
               out.append(base);
            } else {
               out.append(folded, i, next);
            }
         }

         i = next;
      }

      return out == null ? folded : out.toString();
   }

   static String baseLetters(int cp) {
      String special = switch (cp) {
         case 272 -> "D";
         case 273 -> "d";
         case 294 -> "H";
         case 295 -> "h";
         case 306 -> "IJ";
         case 307 -> "ij";
         case 319 -> "L";
         case 320 -> "l";
         case 321 -> "L";
         case 322 -> "l";
         case 338 -> "OE";
         case 339 -> "oe";
         case 358 -> "T";
         case 359 -> "t";
         case 383 -> "s";
         default -> null;
      };
      if (special != null) {
         return special;
      } else {
         String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Form.NFD);
         StringBuilder base = new StringBuilder(decomposed.length());
         decomposed.codePoints().filter(c -> Character.getType(c) != 6).forEach(base::appendCodePoint);
         return !base.isEmpty() && base.length() != decomposed.length() ? base.toString() : null;
      }
   }

   private static String replacement(char c) {
      return switch (c) {
         case '\t', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　' -> " ";
         case '\u200b', '\u200c', '\u200d', '\u2060', '♩', '♪', '♫', '♬', '\ufeff' -> "";
         case '‐', '‑', '‒', '–', '—', '―', '−' -> "-";
         case '‘', '’', '‚', '‛', '′' -> "'";
         case '“', '”', '„', '‟', '″' -> "\"";
         case '•', '‧', '∙' -> "·";
         case '…' -> "...";
         default -> null;
      };
   }
}
