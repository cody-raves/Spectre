package net.spectre.client.module.impl.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class FakeStats extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final String OBJECTIVE = "spectre_fs";
   private static final String SHOWN_BY_DEFAULT = ".gg/spectre";
   private static final int LINES = 15;
   private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value)
      .reversed()
      .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
   private static final Pattern MONEY = Pattern.compile("\\$\\s?(\\d[\\d.,]*[KkMmBbTtQq]?)");
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
   private static volatile FakeStats active;
   private final StringSetting playerName = this.addSetting("name", new StringSetting("Name", ".gg/spectre"));
   private final FakeStats.Stat money = this.stat("Money", "money", "balance", "$");
   private final List<FakeStats.Stat> stats = List.of(
      this.money,
      this.stat("Shards", "shards", "★"),
      this.stat("Kills", "kills", "\ud83d\udde1", "⚔"),
      this.stat("Deaths", "deaths", "☠"),
      this.stat("Playtime", "playtime", "⌚"),
      this.stat("Team", "team")
   );
   private long seed;
   private boolean applying;
   private Scoreboard board;
   private Objective fake;
   private Objective serverSidebar;
   private String sidebarMoney;
   private boolean nameReported;

   public FakeStats() {
      super("fake-stats", "Fake Stats", "Shows your own name and stats on the sidebar and tab list. Blank stats get scrambled.", ModuleCategory.DONUT);
   }

   private FakeStats.Stat stat(String name, String... markers) {
      return new FakeStats.Stat(this.addSetting(name.toLowerCase(Locale.ROOT), new StringSetting(name, ".gg/spectre")), markers);
   }

   @Override
   public boolean enabledByDefault() {
      return true;
   }

   public static boolean redirect(Scoreboard board, DisplaySlot slot, Objective objective) {
      FakeStats module = active;
      if (module == null || module.applying || slot != DisplaySlot.SIDEBAR) {
         return false;
      } else if (objective != null && "spectre_fs".equals(objective.getName())) {
         return false;
      } else {
         module.serverSidebar = objective;
         if (objective == null) {
            return false;
         } else {
            Objective shown = module.install(board);
            if (shown == null) {
               return false;
            } else {
               module.applying = true;

               try {
                  board.setDisplayObjective(DisplaySlot.SIDEBAR, shown);
               } finally {
                  module.applying = false;
               }

               return true;
            }
         }
      }
   }

   public static Component tabName(UUID player, Component name) {
      FakeStats module = active;
      return module != null && name != null && isSelf(player) ? module.fakeTabMoney(name) : name;
   }

   public static FormattedText tabText(FormattedText text) {
      FakeStats module = active;
      return (FormattedText)(module != null && text instanceof Component line ? module.fakeTabMoney(line) : text);
   }

   public static ReadOnlyScoreInfo tabScore(ScoreHolder holder, ReadOnlyScoreInfo real) {
      final FakeStats module = active;
      LocalPlayer player = Minecraft.getInstance().player;
      return module != null && real != null && player != null && player.getScoreboardName().equals(holder.getScoreboardName()) ? new ReadOnlyScoreInfo() {
         @Override
         public int value() {
            return real.value();
         }

         @Override
         public boolean isLocked() {
            return real.isLocked();
         }

         @Override
         public NumberFormat numberFormat() {
            return real.numberFormat();
         }

         @Override
         public MutableComponent formatValue(NumberFormat fallback) {
            return Component.empty().append(module.fakeTabMoney(real.formatValue(fallback)));
         }
      } : real;
   }

   @Override
   protected void onEnable() {
      this.seed = ThreadLocalRandom.current().nextLong();
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, event -> this.refresh());
      this.refresh();
   }

   @Override
   protected void onDisable() {
      active = null;
      this.restore();
   }

   private void refresh() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.board = null;
         this.fake = null;
         this.serverSidebar = null;
         this.sidebarMoney = null;
      } else {
         Scoreboard current = mc.level.getScoreboard();
         if (current != this.board) {
            this.board = current;
            this.fake = null;
            this.serverSidebar = null;
         }

         Objective shown = current.getDisplayObjective(DisplaySlot.SIDEBAR);
         if (this.serverSidebar == null || this.serverSidebar.getScoreboard() != current) {
            this.serverSidebar = shown != null && !"spectre_fs".equals(shown.getName()) ? shown : null;
         }

         if (this.serverSidebar == null) {
            this.sidebarMoney = null;
            if (this.fake != null && shown == this.fake) {
               this.applying = true;

               try {
                  current.setDisplayObjective(DisplaySlot.SIDEBAR, null);
               } finally {
                  this.applying = false;
               }
            }
         } else {
            Objective installed = this.install(current);
            if (installed != null) {
               if (current.getDisplayObjective(DisplaySlot.SIDEBAR) != installed) {
                  this.applying = true;

                  try {
                     current.setDisplayObjective(DisplaySlot.SIDEBAR, installed);
                  } finally {
                     this.applying = false;
                  }
               }
            }
         }
      }
   }

   private Objective install(Scoreboard current) {
      Objective server = this.serverSidebar;
      if (server != null && server.getScoreboard() == current) {
         Objective objective = current.getObjective("spectre_fs");
         if (objective == null) {
            objective = current.addObjective("spectre_fs", ObjectiveCriteria.DUMMY, Component.empty(), RenderType.INTEGER, false, server.numberFormat());
         }

         this.fake = objective;
         this.board = current;
         objective.setNumberFormat(server.numberFormat());
         this.writeRows(current, objective, server);
         return objective;
      } else {
         return this.fake;
      }
   }

   private void writeRows(Scoreboard current, Objective objective, Objective server) {
      List<PlayerScoreEntry> rows = new ArrayList<>();

      for (PlayerScoreEntry entry : current.listPlayerScores(server)) {
         if (!entry.isHidden()) {
            rows.add(entry);
         }
      }

      rows.sort(ORDER);
      if (rows.size() > 15) {
         rows.subList(15, rows.size()).clear();
      }

      Component title = visible(server.getDisplayName());
      Component renamedTitle = this.renamed(title);
      objective.setDisplayName(renamedTitle != null ? renamedTitle : this.mask(title, 0));
      boolean nameShown = renamedTitle != null;
      NumberFormat format = server.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
      this.sidebarMoney = null;

      for (int i = 0; i < rows.size(); i++) {
         PlayerScoreEntry row = rows.get(i);
         Component name = visible(PlayerTeam.formatNameForTeam(current.getPlayersTeam(row.owner()), row.ownerName()));
         Component number = visible(row.formatValue(format));
         Component renamed = this.renamed(name);
         FakeStats.Hit hit = renamed == null ? this.find(name.getString(), number.getString()) : null;
         String custom = hit != null ? hit.stat().value().get().strip() : "";
         String shown = null;
         if (renamed != null) {
            name = renamed;
            nameShown = true;
         } else if (custom.isEmpty()) {
            name = this.mask(name, i + 1);
            number = this.mask(number, i + 1);
            if (hit != null) {
               shown = (hit.inNumber() ? number : name).getString().substring(hit.start(), hit.end());
            }
         } else {
            Component target = hit.inNumber() ? number : name;
            shown = fit(target.getString().substring(hit.start(), hit.end()), custom);
            Component swapped = replace(target, List.of(new int[]{hit.start(), hit.end()}), shown);
            name = hit.inNumber() ? this.mask(name, i + 1) : swapped;
            number = hit.inNumber() ? swapped : this.mask(number, i + 1);
         }

         if (hit != null && hit.stat() == this.money) {
            this.sidebarMoney = shown;
         }

         ScoreHolder holder = ScoreHolder.forNameOnly(holderName(i));
         current.removePlayerFromTeam(holder.getScoreboardName());
         ScoreAccess access = current.getOrCreatePlayerScore(holder, objective);
         access.set(row.value());
         access.display(name);
         access.numberFormatOverride(new FixedFormat(number));
      }

      for (int i = rows.size(); i < 15; i++) {
         ScoreHolder holder = ScoreHolder.forNameOnly(holderName(i));
         if (current.getPlayerScoreInfo(holder, objective) != null) {
            current.resetSinglePlayerScore(holder, objective);
         }
      }

      if (!nameShown && !this.playerName.get().isBlank() && !this.nameReported) {
         this.nameReported = true;
         this.reportMissingName(current, rows, format, title);
      }
   }

   private void reportMissingName(Scoreboard current, List<PlayerScoreEntry> rows, NumberFormat format, Component title) {
      StringBuilder lines = new StringBuilder("title: ").append(escaped(title.getString()));

      for (PlayerScoreEntry row : rows) {
         Component name = visible(PlayerTeam.formatNameForTeam(current.getPlayersTeam(row.owner()), row.ownerName()));
         lines.append("\n  ").append(escaped(name.getString())).append(" | ").append(escaped(visible(row.formatValue(format)).getString()));
      }

      LOGGER.info("Fake Stats: no sidebar line shows the name {}\n{}", selfName(), lines);
   }

   private static String escaped(String text) {
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= ' ' && c < 127) {
            out.append(c);
         } else {
            out.append(String.format("\\u%04X", Integer.valueOf(c)));
         }
      }

      return out.toString();
   }

   private void restore() {
      Scoreboard current = this.board;
      Objective server = this.serverSidebar;
      Objective shown = this.fake;
      this.board = null;
      this.fake = null;
      this.serverSidebar = null;
      this.sidebarMoney = null;
      if (current != null) {
         if (current.getDisplayObjective(DisplaySlot.SIDEBAR) == shown) {
            Objective back = server != null && server.getScoreboard() == current ? server : null;
            current.setDisplayObjective(DisplaySlot.SIDEBAR, back);
         }

         Objective leftover = current.getObjective("spectre_fs");
         if (leftover != null) {
            current.removeObjective(leftover);
         }
      }
   }

   private FakeStats.Hit find(String name, String number) {
      for (FakeStats.Stat stat : this.stats) {
         int[] value = valueAfter(name, stat);
         if (value != null && value[0] < value[1]) {
            return new FakeStats.Hit(stat, false, value[0], value[1]);
         }

         if (value != null) {
            return new FakeStats.Hit(stat, true, 0, number.length());
         }

         value = valueAfter(number, stat);
         if (value != null) {
            return new FakeStats.Hit(stat, true, value[0], value[1]);
         }
      }

      return null;
   }

   private static int[] valueAfter(String line, FakeStats.Stat stat) {
      for (String marker : stat.markers()) {
         int at = markerAt(line, marker);
         if (at >= 0) {
            int start = at + marker.length();
            int end = line.length();

            while (start < end && (Character.isWhitespace(line.charAt(start)) || line.charAt(start) == ':')) {
               start++;
            }

            while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
               end--;
            }

            return new int[]{start, end};
         }
      }

      return null;
   }

   private static int markerAt(String line, String marker) {
      String text = folded(line);
      boolean wordStart = wordChar(marker.charAt(0));
      boolean wordEnd = wordChar(marker.charAt(marker.length() - 1));

      for (int at = 0; at + marker.length() <= text.length(); at++) {
         int end = at + marker.length();
         if (text.regionMatches(true, at, marker, 0, marker.length())
            && (!wordStart || at <= 0 || !wordChar(text.charAt(at - 1)))
            && (!wordEnd || end >= text.length() || !wordChar(text.charAt(end)))) {
            return at;
         }
      }

      return -1;
   }

   private static String folded(String line) {
      char[] out = line.toCharArray();

      for (int i = 0; i < out.length; i++) {
         int letter = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".indexOf(out[i]);
         if (letter >= 0) {
            out[i] = (char)(97 + letter);
         }
      }

      return new String(out);
   }

   private static boolean wordChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
   }

   private static String selfName() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null ? player.getGameProfile().name() : null;
   }

   private Component renamed(Component line) {
      String self = selfName();
      int at = self != null ? markerAt(line.getString(), self) : -1;
      if (at < 0) {
         return null;
      } else {
         String custom = this.playerName.get().strip();
         return (Component)(custom.isEmpty() ? line : replace(line, List.of(new int[]{at, at + self.length()}), custom));
      }
   }

   private static String fit(String real, String custom) {
      String bare = custom.startsWith("$") ? custom.substring(1).strip() : custom;
      return real.startsWith("$") ? "$" + bare : bare;
   }

   private String tabMoney() {
      String custom = this.money.value().get().strip();
      String shown = custom.isEmpty() ? this.sidebarMoney : custom;
      if (shown == null) {
         return null;
      } else {
         shown = shown.startsWith("$") ? shown.substring(1).strip() : shown.strip();
         return shown.isEmpty() ? null : shown;
      }
   }

   private Component fakeTabMoney(Component line) {
      String shown = this.tabMoney();
      if (shown != null && line.getString().indexOf(36) >= 0) {
         Component clean = visible(line);
         Matcher matcher = MONEY.matcher(clean.getString());
         List<int[]> ranges = new ArrayList<>();

         while (matcher.find()) {
            ranges.add(new int[]{matcher.start(1), matcher.end(1)});
         }

         return (Component)(ranges.isEmpty() ? line : replace(clean, ranges, shown));
      } else {
         return line;
      }
   }

   private static boolean isSelf(UUID player) {
      LocalPlayer self = Minecraft.getInstance().player;
      return self != null && self.getUUID().equals(player);
   }

   private static Component visible(Component line) {
      if (line.getString().indexOf(167) < 0) {
         return line;
      } else {
         MutableComponent out = Component.empty();
         StringBuilder run = new StringBuilder();
         Style[] runStyle = new Style[]{null};
         StringDecomposer.iterateFormatted(line, Style.EMPTY, (index, style, codePoint) -> {
            if (runStyle[0] != null && !style.equals(runStyle[0])) {
               out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
               run.setLength(0);
            }

            runStyle[0] = style;
            run.appendCodePoint(codePoint);
            return true;
         });
         if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
         }

         return out;
      }
   }

   private static MutableComponent replace(Component line, List<int[]> ranges, String text) {
      if (line.getString().isEmpty()) {
         return Component.literal(text);
      } else {
         MutableComponent out = Component.empty();
         int[] pos = new int[]{0};
         int[] next = new int[]{0};
         line.visit((style, piece) -> {
            int start = pos[0];
            int end = start + piece.length();
            int cursor = start;

            while (cursor < end) {
               int[] range = next[0] < ranges.size() ? ranges.get(next[0]) : null;
               if (range == null || range[0] >= end) {
                  out.append(Component.literal(piece.substring(cursor - start)).setStyle(style));
                  cursor = end;
               } else if (cursor < range[0]) {
                  out.append(Component.literal(piece.substring(cursor - start, range[0] - start)).setStyle(style));
                  cursor = range[0];
               } else {
                  if (cursor == range[0]) {
                     out.append(Component.literal(text).setStyle(style));
                  }

                  cursor = Math.min(end, range[1]);
                  if (cursor == range[1]) {
                     next[0]++;
                  }
               }
            }

            pos[0] = end;
            return Optional.empty();
         }, Style.EMPTY);
         return out;
      }
   }

   private static String holderName(int row) {
      return new String(new char[]{'\ue000', (char)(57360 + row)});
   }

   private Component mask(Component line, int row) {
      if (line == null) {
         return Component.empty();
      } else {
         MutableComponent out = Component.empty();
         boolean[] changed = new boolean[]{false};
         int[] number = new int[]{0};
         line.visit((style, text) -> {
            String shown = this.maskText(text, row, number);
            if (!shown.equals(text)) {
               changed[0] = true;
            }

            out.append(Component.literal(shown).setStyle(style));
            return Optional.empty();
         }, Style.EMPTY);
         return (Component)(changed[0] ? out : line);
      }
   }

   private String maskText(String text, int row, int[] number) {
      StringBuilder out = null;
      int i = 0;

      while (i < text.length()) {
         int cp = text.codePointAt(i);
         if (!Character.isDigit(cp)) {
            if (out != null) {
               out.appendCodePoint(cp);
            }

            i += Character.charCount(cp);
         } else {
            int end = i;

            while (true) {
               if (end < text.length()) {
                  int c = text.codePointAt(end);
                  int width = Character.charCount(c);
                  if (Character.isDigit(c)) {
                     end += width;
                     continue;
                  }

                  if ((c == 44 || c == 46) && end + width < text.length() && Character.isDigit(text.codePointAt(end + width))) {
                     end += width;
                     continue;
                  }
               }

               String raw = text.substring(i, end);
               String fake = this.fakeNumber(row, (int)(number[0]++), raw);
               if (out == null) {
                  if (!fake.equals(raw)) {
                     out = new StringBuilder(text.length()).append(text, 0, i).append(fake);
                  }
               } else {
                  out.append(fake);
               }

               i = end;
               break;
            }
         }
      }

      return out == null ? text : out.toString();
   }

   private String fakeNumber(int row, int index, String raw) {
      int digits = 0;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            digits++;
         }
      }

      if (digits == 0) {
         return raw;
      } else if (digits > 15) {
         return this.shiftWide(row, index, raw);
      } else {
         long value = 0L;

         for (int ix = 0; ix < raw.length(); ix++) {
            char c = raw.charAt(ix);
            if (c >= '0' && c <= '9') {
               value = value * 10L + (c - '0');
            }
         }

         long span = pow10(digits);
         long offset = Long.remainderUnsigned(mix(this.seed ^ row * 1315423911L ^ index * 2654435761L ^ digits), span);
         if (offset == 0L) {
            offset = 1L;
         }

         boolean leadingZero = raw.charAt(0) == '0' && digits > 1;
         long fake = shifted(value, offset, span, digits, leadingZero);
         if (fake == value) {
            fake = shifted(value, offset == span - 1L ? 1L : offset + 1L, span, digits, leadingZero);
         }

         String rendered = Long.toString(fake);
         if (rendered.length() < digits) {
            rendered = "0".repeat(digits - rendered.length()) + rendered;
         }

         StringBuilder result = new StringBuilder(raw.length());
         int cursor = 0;

         for (int ixx = 0; ixx < raw.length(); ixx++) {
            char c = raw.charAt(ixx);
            if (c >= '0' && c <= '9') {
               result.append(rendered.charAt(cursor++));
            } else {
               result.append(c);
            }
         }

         return result.toString();
      }
   }

   private static long shifted(long value, long offset, long span, int digits, boolean leadingZero) {
      long fake = Math.floorMod(value + offset, span);
      if (!leadingZero && digits > 1 && fake < span / 10L) {
         fake += span / 10L;
      }

      return fake;
   }

   private String shiftWide(int row, int index, String raw) {
      long salt = mix(this.seed ^ row * 1315423911L ^ index);
      StringBuilder result = new StringBuilder(raw.length());
      int pos = 0;
      boolean first = true;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            int shift = (int)Long.remainderUnsigned(mix(salt + pos), 9L) + 1;
            int shown = (c - '0' + shift) % 10;
            if (first && shown == 0) {
               shown = shift;
            }

            first = false;
            result.append((char)(48 + shown));
            pos++;
         } else {
            result.append(c);
         }
      }

      return result.toString();
   }

   private static long pow10(int digits) {
      long value = 1L;

      for (int i = 0; i < digits; i++) {
         value *= 10L;
      }

      return value;
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }

   @Environment(EnvType.CLIENT)
   private record Hit(FakeStats.Stat stat, boolean inNumber, int start, int end) {
   }

   @Environment(EnvType.CLIENT)
   private record Stat(StringSetting value, String... markers) {
   }
}
