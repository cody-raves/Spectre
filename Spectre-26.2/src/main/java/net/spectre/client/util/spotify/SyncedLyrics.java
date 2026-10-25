package net.spectre.client.util.spotify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SyncedLyrics {
   public static final SyncedLyrics EMPTY = new SyncedLyrics(new SyncedLyrics.Line[0]);
   private static final Pattern TIMESTAMP = Pattern.compile("(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?");
   private static final Pattern WORD_TIMESTAMP = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
   private static final Pattern METADATA = Pattern.compile("[A-Za-z#][A-Za-z0-9_ ]*:.*", 32);
   private static final Pattern OFFSET = Pattern.compile("offset\\s*:\\s*([+-]?\\d{1,7})\\s*");
   private static final Pattern WHITESPACE = Pattern.compile("\\s+");
   private static final long WORD_EARLY_MS = 1000L;
   private final SyncedLyrics.Line[] lines;
   private final long[] times;

   private SyncedLyrics(SyncedLyrics.Line[] lines) {
      this.lines = lines;
      this.times = new long[lines.length];

      for (int i = 0; i < lines.length; i++) {
         this.times[i] = lines[i].timeMs();
      }
   }

   public static SyncedLyrics parse(String lrc) {
      if (lrc != null && !lrc.isBlank()) {
         List<SyncedLyrics.Line> parsed = new ArrayList<>();
         List<Long> stamps = new ArrayList<>(4);
         int offset = 0;

         for (String raw : lrc.split("\r\n|\r|\n")) {
            String line = raw.strip();
            if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
               line = line.substring(1).strip();
            }

            stamps.clear();
            int pos = 0;

            while (pos < line.length() && line.charAt(pos) == '[') {
               int close = line.indexOf(93, pos + 1);
               if (close < 0) {
                  break;
               }

               String tag = line.substring(pos + 1, close).strip();
               Matcher time = TIMESTAMP.matcher(tag);
               if (time.matches()) {
                  stamps.add(toMillis(time));
               } else {
                  if (!METADATA.matcher(tag).matches()) {
                     break;
                  }

                  Matcher shift = OFFSET.matcher(tag.toLowerCase(Locale.ROOT));
                  if (shift.matches()) {
                     offset = Integer.parseInt(shift.group(1));
                  }
               }

               pos = close + 1;

               while (pos < line.length() && line.charAt(pos) == ' ') {
                  pos++;
               }
            }

            if (!stamps.isEmpty()) {
               String body = line.substring(pos);
               String text = cleanText(body);
               long first = Collections.min(stamps);
               List<Long> words = wordTimes(body, text, first);

               for (long stamp : stamps) {
                  parsed.add(new SyncedLyrics.Line(stamp, text, shift(words, stamp - first)));
               }
            }
         }

         if (parsed.isEmpty()) {
            return EMPTY;
         } else {
            SyncedLyrics.Line[] sorted = parsed.toArray(new SyncedLyrics.Line[0]);
            if (offset != 0) {
               for (int i = 0; i < sorted.length; i++) {
                  SyncedLyrics.Line moved = sorted[i];
                  sorted[i] = new SyncedLyrics.Line(Math.max(0L, moved.timeMs() - offset), moved.text(), shift(moved.wordTimes(), -offset));
               }
            }

            Arrays.sort(sorted, Comparator.comparingLong(SyncedLyrics.Line::timeMs));
            return new SyncedLyrics(sorted);
         }
      } else {
         return EMPTY;
      }
   }

   public int indexAt(long timeMs) {
      int low = 0;
      int high = this.times.length;

      while (low < high) {
         int mid = low + high >>> 1;
         if (this.times[mid] <= timeMs) {
            low = mid + 1;
         } else {
            high = mid;
         }
      }

      return low - 1;
   }

   public int firstWithTimeOf(int index) {
      if (index >= 0 && index < this.times.length) {
         long time = this.times[index];

         while (index > 0 && this.times[index - 1] == time) {
            index--;
         }

         return index;
      } else {
         return index;
      }
   }

   public int lastWithTimeOf(int index) {
      if (index >= 0 && index < this.times.length) {
         long time = this.times[index];

         while (index + 1 < this.times.length && this.times[index + 1] == time) {
            index++;
         }

         return index;
      } else {
         return index;
      }
   }

   public SyncedLyrics.Line line(int index) {
      return this.lines[index];
   }

   public int size() {
      return this.lines.length;
   }

   public boolean isEmpty() {
      return this.lines.length == 0;
   }

   public List<String> texts() {
      List<String> texts = new ArrayList<>(this.lines.length);

      for (SyncedLyrics.Line line : this.lines) {
         if (!line.isGap()) {
            texts.add(line.text());
         }
      }

      return List.copyOf(texts);
   }

   private static long toMillis(Matcher time) {
      long minutes = Long.parseLong(time.group(1));
      long seconds = Long.parseLong(time.group(2));
      String fraction = time.group(3);
      long millis = 0L;
      if (fraction != null) {
         millis = Long.parseLong(fraction);
         if (fraction.length() == 1) {
            millis *= 100L;
         } else if (fraction.length() == 2) {
            millis *= 10L;
         }
      }

      return minutes * 60000L + seconds * 1000L + millis;
   }

   private static String cleanText(String text) {
      if (text.indexOf(60) >= 0) {
         text = WORD_TIMESTAMP.matcher(text).replaceAll("");
      }

      return WHITESPACE.matcher(text).replaceAll(" ").strip();
   }

   private static List<Long> wordTimes(String body, String text, long lineTime) {
      if (body.indexOf(60) >= 0 && !text.isEmpty()) {
         List<Long> starts = new ArrayList<>();
         Matcher tag = WORD_TIMESTAMP.matcher(body);
         long pending = -1L;
         boolean inWord = false;
         int i = 0;

         while (i < body.length()) {
            if (body.charAt(i) != '<' || !tag.region(i, body.length()).lookingAt()) {
               char c = body.charAt(i++);
               if (c <= ' ') {
                  inWord = false;
               } else if (!inWord) {
                  inWord = true;
                  if (pending < 0L) {
                     return List.of();
                  }

                  if (starts.isEmpty() ? pending < lineTime - 1000L : pending < starts.getLast()) {
                     return List.of();
                  }

                  starts.add(pending);
                  pending = -1L;
               }
            } else {
               i = tag.end();
               boolean syllable = inWord && i < body.length() && body.charAt(i) > ' ';
               if (!syllable) {
                  pending = toMillis(tag);
               }
            }
         }

         i = 1;

         for (int ix = 0; ix < text.length(); ix++) {
            if (text.charAt(ix) == ' ') {
               i++;
            }
         }

         return starts.size() == i ? starts : List.of();
      } else {
         return List.of();
      }
   }

   private static List<Long> shift(List<Long> times, long by) {
      if (by != 0L && !times.isEmpty()) {
         List<Long> moved = new ArrayList<>(times.size());

         for (long time : times) {
            moved.add(Math.max(0L, time + by));
         }

         return moved;
      } else {
         return times;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Line(long timeMs, String text, List<Long> wordTimes) {
      public Line(long timeMs, String text, List<Long> wordTimes) {
         text = text == null ? "" : text;
         wordTimes = wordTimes == null ? List.of() : List.copyOf(wordTimes);
         this.timeMs = timeMs;
         this.text = text;
         this.wordTimes = wordTimes;
      }

      public boolean isGap() {
         return this.text.isEmpty();
      }
   }
}
