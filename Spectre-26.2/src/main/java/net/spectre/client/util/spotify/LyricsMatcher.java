package net.spectre.client.util.spotify;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LyricsMatcher {
   static final double DURATION_TOLERANCE_SECONDS = 3.0;
   private static final int TITLE_EXACT = 40;
   private static final int TITLE_BASE = 30;
   private static final int TITLE_CONTAINS = 12;
   private static final int ARTIST_EXACT = 30;
   private static final int ARTIST_SHARED = 20;
   private static final int ARTIST_CONTAINS = 10;
   private static final int DURATION_MAX = 20;
   private static final int SYNCED = 25;
   private static final int INSTRUMENTAL = 8;
   private static final int PLAIN = 5;
   private static final Pattern MARKS = Pattern.compile("\\p{M}+");
   private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
   private static final Pattern APOSTROPHES = Pattern.compile("['’`´]");
   private static final Pattern BRACKETED = Pattern.compile(
      "\\s*[(\\[][^)\\]]*\\b(feat|ft|featuring|with|remaster(ed)?|explicit|clean|mono|stereo|bonus|deluxe|anniversary|edition|single version|album version)\\b[^)\\]]*[)\\]]",
      2
   );
   private static final Pattern DASHED = Pattern.compile(
      "\\s+[-–—]\\s+[^-–—]*\\b(remaster(ed)?|explicit|clean|mono|stereo|bonus track|deluxe|anniversary|edition|single version|album version)\\b.*$", 2
   );
   private static final Pattern FEATURING = Pattern.compile("\\s+(feat\\.?|ft\\.|featuring)\\s+.*$", 2);
   private static final Pattern ARTIST_SPLIT = Pattern.compile(
      "\\s*(?:,|;|/|&|•|\\+|\\bx\\b|\\band\\b|\\bwith\\b|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bfeaturing\\b)\\s*", 2
   );
   private static final Pattern CREDIT_SPLIT = Pattern.compile("\\s*[,;•]\\s*|\\s+(?:&|x|feat\\.?|ft\\.?|featuring|with)\\s+", 2);

   private LyricsMatcher() {
   }

   static LrclibTrack best(List<LrclibTrack> candidates, LyricsQuery query) {
      if (candidates != null && !candidates.isEmpty() && query != null) {
         LrclibTrack best = null;
         int bestScore = -1;
         double bestDrift = Double.MAX_VALUE;

         for (LrclibTrack candidate : candidates) {
            if (candidate != null) {
               int score = score(candidate, query);
               if (score >= 0) {
                  double drift = drift(candidate, query);
                  if (score > bestScore || score == bestScore && drift < bestDrift) {
                     best = candidate;
                     bestScore = score;
                     bestDrift = drift;
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static int score(LrclibTrack candidate, LyricsQuery query) {
      if (!candidate.usable()) {
         return -1;
      } else {
         int duration = durationScore(candidate, query);
         if (duration < 0) {
            return -1;
         } else {
            int title = titleScore(query.title(), candidate.trackName());
            if (title <= 0) {
               return -1;
            } else {
               int artist = artistScore(query.artist(), candidate.artistName());
               if (artist <= 0) {
                  return -1;
               } else {
                  int content = candidate.hasSynced() ? 25 : (candidate.hasPlain() ? 5 : 8);
                  return title + artist + duration + content;
               }
            }
         }
      }
   }

   private static int durationScore(LrclibTrack candidate, LyricsQuery query) {
      if (query.hasDuration() && !(candidate.duration() <= 0.0)) {
         double drift = Math.abs(candidate.duration() - query.durationSeconds());
         return drift > 3.0 ? -1 : (int)Math.round(20.0 * (1.0 - drift / 6.0));
      } else {
         return 0;
      }
   }

   private static double drift(LrclibTrack candidate, LyricsQuery query) {
      return query.hasDuration() && !(candidate.duration() <= 0.0) ? Math.abs(candidate.duration() - query.durationSeconds()) : 3.0;
   }

   static int titleScore(String wanted, String offered) {
      String a = normalize(wanted);
      String b = normalize(offered);
      if (a.isEmpty() || b.isEmpty()) {
         return 0;
      } else if (a.equals(b)) {
         return 40;
      } else {
         String baseA = normalize(baseTitle(wanted));
         String baseB = normalize(baseTitle(offered));
         if (!baseA.isEmpty() && baseA.equals(baseB)) {
            return 30;
         } else {
            return !containsWords(baseA, baseB) && !containsWords(baseB, baseA) ? 0 : 12;
         }
      }
   }

   static int artistScore(String wanted, String offered) {
      String a = normalize(wanted);
      String b = normalize(offered);
      if (!a.isEmpty() && !b.isEmpty()) {
         if (a.equals(b)) {
            return 30;
         } else {
            Set<String> wantedArtists = artists(wanted);
            Set<String> offeredArtists = artists(offered);

            for (String artist : wantedArtists) {
               if (offeredArtists.contains(artist)) {
                  return 20;
               }
            }

            String first = wantedArtists.isEmpty() ? a : wantedArtists.iterator().next();
            return !containsWords(b, first) && !containsWords(a, b) ? 0 : 10;
         }
      } else {
         return 0;
      }
   }

   static String baseTitle(String title) {
      if (title == null) {
         return "";
      } else {
         String base = BRACKETED.matcher(title).replaceAll("");
         base = DASHED.matcher(base).replaceFirst("");
         base = FEATURING.matcher(base).replaceFirst("");
         base = base.strip();
         return base.isEmpty() ? title.strip() : base;
      }
   }

   static String primaryArtist(String artist) {
      if (artist == null) {
         return "";
      } else {
         String[] parts = CREDIT_SPLIT.split(artist.strip());

         for (String part : parts) {
            if (!part.isBlank()) {
               return part.strip();
            }
         }

         return artist.strip();
      }
   }

   static String normalize(String text) {
      if (text != null && !text.isEmpty()) {
         String folded = text.toLowerCase(Locale.ROOT)
            .replace('ı', 'i')
            .replace("ß", "ss")
            .replace("æ", "ae")
            .replace("œ", "oe")
            .replace('ø', 'o')
            .replace('ł', 'l')
            .replace('đ', 'd')
            .replace("&", " and ");
         folded = MARKS.matcher(Normalizer.normalize(folded, Form.NFD)).replaceAll("");
         folded = APOSTROPHES.matcher(folded).replaceAll("");
         return NON_WORD.matcher(folded).replaceAll(" ").strip();
      } else {
         return "";
      }
   }

   private static Set<String> artists(String credit) {
      Set<String> names = new LinkedHashSet<>();

      for (String part : ARTIST_SPLIT.split(credit)) {
         String name = normalize(part);
         if (!name.isEmpty()) {
            names.add(name);
         }
      }

      return names;
   }

   private static boolean containsWords(String haystack, String needle) {
      return needle.length() >= 3 && haystack.length() >= needle.length() ? (" " + haystack + " ").contains(" " + needle + " ") : false;
   }

   static List<String[]> searchTerms(LyricsQuery query) {
      List<String[]> terms = new ArrayList<>(2);
      terms.add(new String[]{query.title(), query.artist()});
      String title = baseTitle(query.title());
      String artist = primaryArtist(query.artist());
      if (!title.equals(query.title()) || !artist.equals(query.artist())) {
         terms.add(new String[]{title, artist});
      }

      return terms;
   }
}
