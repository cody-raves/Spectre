package net.spectre.client.module.impl.misc.nameprotect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class NameMatcher {
   public static final NameMatcher EMPTY = new NameMatcher(Map.of());
   public static final int MAX_NAME_LENGTH = 32;
   private final Map<String, String> mapping;
   private final String[] names;
   private final String[] aliases;
   private final int mask;
   private final long lengths;

   public NameMatcher(Map<String, String> nameToAlias) {
      Map<String, String> unique = new HashMap<>();
      List<String> keptNames = new ArrayList<>(nameToAlias.size());
      List<String> keptAliases = new ArrayList<>(nameToAlias.size());

      for (Entry<String, String> entry : nameToAlias.entrySet()) {
         String name = entry.getKey();
         String alias = entry.getValue();
         if (name != null && !name.isEmpty() && name.length() <= 32 && alias != null && unique.putIfAbsent(name.toLowerCase(Locale.ROOT), alias) == null) {
            keptNames.add(name);
            keptAliases.add(alias);
         }
      }

      int capacity = Integer.highestOneBit(Math.max(1, keptNames.size()) * 2) * 2;
      String[] tableNames = new String[capacity];
      String[] tableAliases = new String[capacity];
      long lengthBits = 0L;

      for (int i = 0; i < keptNames.size(); i++) {
         String name = keptNames.get(i);
         int slot = hash(name, 0, name.length()) & capacity - 1;

         while (tableNames[slot] != null) {
            slot = slot + 1 & capacity - 1;
         }

         tableNames[slot] = name;
         tableAliases[slot] = keptAliases.get(i);
         lengthBits |= 1L << name.length();
      }

      this.mapping = Map.copyOf(unique);
      this.names = tableNames;
      this.aliases = tableAliases;
      this.mask = capacity - 1;
      this.lengths = lengthBits;
   }

   public boolean isEmpty() {
      return this.mapping.isEmpty();
   }

   public boolean sameMapping(NameMatcher other) {
      return other != null && this.mapping.equals(other.mapping);
   }

   public static boolean isNameChar(char c) {
      return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
   }

   public static String nameCore(String raw) {
      if (raw == null) {
         return null;
      } else {
         int start = 0;
         int end = raw.length();

         while (start < end && !isNameChar(raw.charAt(start))) {
            start++;
         }

         while (end > start && !isNameChar(raw.charAt(end - 1))) {
            end--;
         }

         if (start != end && end - start <= 32) {
            for (int i = start; i < end; i++) {
               if (!isNameChar(raw.charAt(i))) {
                  return null;
               }
            }

            return raw.substring(start, end);
         } else {
            return null;
         }
      }
   }

   public String aliasAt(String text, int start, int end) {
      int length = end - start;
      if (length > 0 && length <= 32 && (this.lengths & 1L << length) != 0L) {
         int slot = hash(text, start, end) & this.mask;

         while (true) {
            String name = this.names[slot];
            if (name == null) {
               return null;
            }

            if (name.length() == length && name.regionMatches(true, 0, text, start, length)) {
               return this.aliases[slot];
            }

            slot = slot + 1 & this.mask;
         }
      } else {
         return null;
      }
   }

   public String replace(String text) {
      if (text != null && !text.isEmpty() && !this.isEmpty()) {
         List<NameMatcher.Match> matches = this.scan(text, new int[]{0, text.length()}, 1);
         if (matches.isEmpty()) {
            return text;
         } else {
            StringBuilder out = new StringBuilder(text.length() + 16);
            int position = 0;

            for (NameMatcher.Match match : matches) {
               out.append(text, position, match.start()).append(match.alias());
               position = match.end();
            }

            return out.append(text, position, text.length()).toString();
         }
      } else {
         return text;
      }
   }

   public boolean mentionsAny(String text) {
      return text != null && !text.isEmpty() && !this.isEmpty() && !this.scan(text, new int[]{0, text.length()}, 1).isEmpty();
   }

   public NameMatcher newNamesSince(NameMatcher older) {
      Map<String, String> fresh = null;

      for (Entry<String, String> entry : this.mapping.entrySet()) {
         if (older == null || !entry.getValue().equals(older.mapping.get(entry.getKey()))) {
            if (fresh == null) {
               fresh = new HashMap<>();
            }

            fresh.put(entry.getKey(), entry.getValue());
         }
      }

      return fresh != null ? new NameMatcher(fresh) : EMPTY;
   }

   public List<NameMatcher.Match> scan(String text, int[] segmentStarts, int segmentCount) {
      if (!this.isEmpty() && !text.isEmpty()) {
         List<NameMatcher.Match> found = null;
         int length = text.length();
         int segment = 0;
         int i = 0;

         while (i < length) {
            while (segment < segmentCount - 1 && segmentStarts[segment + 1] <= i) {
               segment++;
            }

            int segmentEnd = segmentStarts[segment + 1];
            char c = text.charAt(i);
            if (c == 167) {
               i += i + 1 < segmentEnd ? 2 : 1;
            } else if (!isNameChar(c)) {
               i++;
            } else {
               int wordEnd = i + 1;

               while (wordEnd < length && isNameChar(text.charAt(wordEnd))) {
                  wordEnd++;
               }

               String alias = this.aliasAt(text, i, wordEnd);
               if (alias != null) {
                  found = add(found, i, wordEnd, alias);
               } else if (segmentEnd < wordEnd) {
                  for (int part = segment + 1; part < segmentCount && segmentStarts[part] < wordEnd; part++) {
                     int from = segmentStarts[part];
                     String endingAlias = this.aliasAt(text, from, wordEnd);
                     if (endingAlias != null) {
                        found = add(found, from, wordEnd, endingAlias);
                        break;
                     }
                  }
               }

               i = wordEnd;
            }
         }

         return found != null ? found : List.of();
      } else {
         return List.of();
      }
   }

   private static List<NameMatcher.Match> add(List<NameMatcher.Match> found, int start, int end, String alias) {
      List<NameMatcher.Match> list = (List<NameMatcher.Match>)(found != null ? found : new ArrayList<>(4));
      list.add(new NameMatcher.Match(start, end, alias));
      return list;
   }

   private static int hash(String text, int start, int end) {
      int h = 0;

      for (int i = start; i < end; i++) {
         char c = text.charAt(i);
         if (c >= 'A' && c <= 'Z') {
            c = (char)(c + ' ');
         }

         h = 31 * h + c;
      }

      return h ^ h >>> 16;
   }

   @Environment(EnvType.CLIENT)
   public record Match(int start, int end, String alias) {
   }
}
