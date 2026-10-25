package net.spectre.client.util.staff;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class StaffMatcher {
   public static final String DEFAULT_RANK_WORDS = "owner, co-owner, manager, admin, administrator, developer, dev, sr-admin, sr-mod, senior-mod, moderator, mod, trial-mod, sr-helper, senior-helper, helper, trial-helper, support, staff";
   public static final String DEFAULT_MARKERS = "U+2605 U+2606 U+2726 U+2727 U+272A U+272F U+2730 U+2B50";
   public static final String MODE_ALL = "Ranks + Markers + Team";
   public static final String MODE_RANKS = "Ranks";
   public static final String MODE_MARKERS = "Markers";
   public static final String MODE_TEAM = "Team Rank";
   public static final int DEFAULT_TEAM_RANK_MAX = 5;
   private static final String GENERIC_RANK = "Staff";
   private static final Pattern CODEPOINT = Pattern.compile("(?i)(?:u\\+|\\\\u)([0-9a-f]{4,6})");
   private static final Pattern TEAM_RANK = Pattern.compile("^(\\d{2})-");
   private static final int MAX_SPAN = 3;
   private static final int TEAM_RANK_SLOTS = 100;
   private static final Map<String, String> LABELS = Map.ofEntries(
      Map.entry("owner", "Owner"),
      Map.entry("coowner", "Co-Owner"),
      Map.entry("manager", "Manager"),
      Map.entry("admin", "Admin"),
      Map.entry("administrator", "Admin"),
      Map.entry("sradmin", "Sr. Admin"),
      Map.entry("developer", "Dev"),
      Map.entry("dev", "Dev"),
      Map.entry("srmod", "Sr. Mod"),
      Map.entry("seniormod", "Sr. Mod"),
      Map.entry("moderator", "Mod"),
      Map.entry("mod", "Mod"),
      Map.entry("trialmod", "Trial Mod"),
      Map.entry("srhelper", "Sr. Helper"),
      Map.entry("seniorhelper", "Sr. Helper"),
      Map.entry("helper", "Helper"),
      Map.entry("trialhelper", "Trial Helper"),
      Map.entry("support", "Support"),
      Map.entry("staff", "Staff"),
      Map.entry("builder", "Builder")
   );
   private static final Map<Integer, Character> SMALL_CAPS = smallCaps();
   private static final char SMALL_CAPS_Q = 'ǫ';
   private final boolean useRanks;
   private final boolean useMarkers;
   private final boolean useTeamRank;
   private final int teamRankMax;
   private final Map<String, Integer> wordIndex = new HashMap<>();
   private final List<StaffMatcher.Word> words = new ArrayList<>();
   private final Set<Integer> markers;
   private final boolean iconGlyphs;
   private final Set<String> knownNames;
   private final Set<String> ignoredNames;

   public StaffMatcher(String mode, String rankWords, String markerGlyphs, boolean iconGlyphs, int teamRankMax, String knownNames, String ignoredNames) {
      this.useRanks = "Ranks + Markers + Team".equals(mode) || "Ranks".equals(mode);
      this.useMarkers = "Ranks + Markers + Team".equals(mode) || "Markers".equals(mode);
      this.useTeamRank = "Ranks + Markers + Team".equals(mode) || "Team Rank".equals(mode);
      this.teamRankMax = teamRankMax;
      this.iconGlyphs = iconGlyphs;

      for (String raw : rankWords.split(",")) {
         String typed = raw.trim();
         String folded = fold(typed).replace(" ", "");
         if (!folded.isEmpty() && !this.wordIndex.containsKey(folded)) {
            this.wordIndex.put(folded, this.words.size());
            this.words.add(new StaffMatcher.Word(folded, LABELS.getOrDefault(folded, capitalize(typed))));
         }
      }

      this.markers = parseMarkers(markerGlyphs);
      this.knownNames = parseNames(knownNames);
      this.ignoredNames = parseNames(ignoredNames);
   }

   public StaffMatcher.Match match(String name, String teamName, String... tabTexts) {
      if (name != null && !name.isEmpty()) {
         String key = name.toLowerCase(Locale.ROOT);
         if (this.ignoredNames.contains(key)) {
            return null;
         } else {
            StringBuilder combined = new StringBuilder();

            for (String text : tabTexts) {
               if (text != null && !text.isEmpty()) {
                  combined.append(text).append(' ');
               }
            }

            if (teamName != null && !teamName.isEmpty()) {
               combined.append(teamName).append(' ');
            }

            String around = withoutName(stripLegacyCodes(combined.toString()), name);
            StaffMatcher.Word rank = this.useRanks ? this.bestRank(around) : null;
            if (rank != null) {
               return new StaffMatcher.Match(rank.label(), this.wordIndex.get(rank.folded()));
            } else {
               int teamRank = this.useTeamRank ? teamRank(teamName) : -1;
               if (teamRank >= 0 && teamRank <= this.teamRankMax) {
                  return new StaffMatcher.Match(teamRankLabel(teamRank), this.words.size() + teamRank);
               } else {
                  return (!this.useMarkers || !this.hasMarker(around)) && !this.knownNames.contains(key)
                     ? null
                     : new StaffMatcher.Match("Staff", this.words.size() + 100);
               }
            }
         }
      } else {
         return null;
      }
   }

   public static int teamRank(String teamName) {
      if (teamName != null && teamName.length() >= 3) {
         Matcher m = TEAM_RANK.matcher(teamName);
         return m.lookingAt() ? Integer.parseInt(m.group(1)) : -1;
      } else {
         return -1;
      }
   }

   public static String teamRankLabel(int teamRank) {
      return teamRank < 10 ? "0" + teamRank : Integer.toString(teamRank);
   }

   private StaffMatcher.Word bestRank(String text) {
      if (this.words.isEmpty()) {
         return null;
      } else {
         List<String> tokens = tokens(text);
         int best = Integer.MAX_VALUE;
         int i = 0;

         while (i < tokens.size()) {
            int matchedSpan = 0;

            for (int span = Math.min(3, tokens.size() - i); span >= 1; span--) {
               Integer index = this.wordIndex.get(String.join("", tokens.subList(i, i + span)));
               if (index != null) {
                  best = Math.min(best, index);
                  matchedSpan = span;
                  break;
               }
            }

            i += Math.max(1, matchedSpan);
         }

         return best == Integer.MAX_VALUE ? null : this.words.get(best);
      }
   }

   static List<String> tokens(String text) {
      String folded = fold(text);
      List<String> out = new ArrayList<>();
      StringBuilder current = new StringBuilder();

      for (int i = 0; i < folded.length(); i++) {
         char c = folded.charAt(i);
         if (c >= 'a' && c <= 'z') {
            current.append(c);
         } else if (!current.isEmpty()) {
            out.add(current.toString());
            current.setLength(0);
         }
      }

      if (!current.isEmpty()) {
         out.add(current.toString());
      }

      return out;
   }

   static String stripLegacyCodes(String text) {
      if (text.indexOf(167) < 0) {
         return text;
      } else {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167) {
               i++;
            } else {
               out.append(c);
            }
         }

         return out.toString();
      }
   }

   static String fold(String text) {
      String decomposed = Normalizer.normalize(text.replace('ǫ', 'q'), Form.NFKD);
      StringBuilder out = new StringBuilder(decomposed.length());
      int i = 0;

      while (i < decomposed.length()) {
         int cp = decomposed.codePointAt(i);
         i += Character.charCount(cp);
         if (Character.getType(cp) != 6) {
            Character small = SMALL_CAPS.get(cp);
            if (small != null) {
               out.append(small.charValue());
            } else {
               int lower = Character.toLowerCase(cp);
               out.append(lower >= 97 && lower <= 122 ? (char)lower : ' ');
            }
         }
      }

      return out.toString();
   }

   private boolean hasMarker(String text) {
      int i = 0;

      while (i < text.length()) {
         int cp = text.codePointAt(i);
         i += Character.charCount(cp);
         if (this.markers.contains(cp) || this.iconGlyphs && isPrivateUse(cp)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isPrivateUse(int cp) {
      return cp >= 57344 && cp <= 63743 || cp >= 983040 && cp <= 1048573 || cp >= 1048576 && cp <= 1114109;
   }

   static Set<Integer> parseMarkers(String raw) {
      Set<Integer> out = new HashSet<>();

      for (String token : raw.split("[,\\s]+")) {
         if (!token.isEmpty()) {
            Matcher m = CODEPOINT.matcher(token);
            if (m.matches()) {
               int cp = Integer.parseInt(m.group(1), 16);
               if (Character.isValidCodePoint(cp)) {
                  out.add(cp);
               }
            } else {
               token.codePoints().forEach(out::add);
            }
         }
      }

      return out;
   }

   private static Set<String> parseNames(String raw) {
      Set<String> out = new HashSet<>();

      for (String part : raw.split("[,\\s]+")) {
         if (!part.isEmpty()) {
            out.add(part.toLowerCase(Locale.ROOT));
         }
      }

      return out;
   }

   private static String withoutName(String text, String name) {
      String lowerText = text.toLowerCase(Locale.ROOT);
      String lowerName = name.toLowerCase(Locale.ROOT);
      if (!lowerText.contains(lowerName)) {
         return text;
      } else {
         StringBuilder out = new StringBuilder(text.length());
         int from = 0;

         for (int at = lowerText.indexOf(lowerName); at >= 0; at = lowerText.indexOf(lowerName, from)) {
            out.append(text, from, at).append(' ');
            from = at + lowerName.length();
         }

         return out.append(text, from, text.length()).toString();
      }
   }

   private static String capitalize(String word) {
      String trimmed = word.trim();
      return trimmed.isEmpty() ? "Staff" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
   }

   private static Map<Integer, Character> smallCaps() {
      int[] codepoints = new int[]{
         7424, 665, 7428, 7429, 7431, 42800, 610, 668, 618, 7434, 7435, 671, 7437, 628, 7439, 7448, 42927, 640, 42801, 7451, 7452, 7456, 7457, 655, 7458
      };
      String letters = "abcdefghijklmnopqrstuvwyz";
      Map<Integer, Character> map = new HashMap<>();

      for (int i = 0; i < codepoints.length; i++) {
         map.put(codepoints[i], letters.charAt(i));
      }

      return Map.copyOf(map);
   }

   @Environment(EnvType.CLIENT)
   public record Match(String rank, int seniority) {
   }

   @Environment(EnvType.CLIENT)
   private record Word(String folded, String label) {
   }
}
