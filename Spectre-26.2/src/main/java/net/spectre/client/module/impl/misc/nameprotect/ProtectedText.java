package net.spectre.client.module.impl.misc.nameprotect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText.StyledContentConsumer;
import net.minecraft.network.chat.HoverEvent.EntityTooltipInfo;
import net.minecraft.network.chat.HoverEvent.ShowEntity;
import net.minecraft.network.chat.HoverEvent.ShowText;

@Environment(EnvType.CLIENT)
public final class ProtectedText {
   private ProtectedText() {
   }

   public static Component rewrite(NameMatcher matcher, Component text) {
      if (text != null && !matcher.isEmpty()) {
         Component rebuilt = rebuild(matcher, text, true);
         return rebuilt != null ? rebuilt : text;
      } else {
         return text;
      }
   }

   public static FormattedText rewrite(NameMatcher matcher, FormattedText text) {
      if (text != null && !matcher.isEmpty()) {
         Component rebuilt = rebuild(matcher, text, true);
         return (FormattedText)(rebuilt != null ? rebuilt : text);
      } else {
         return text;
      }
   }

   private static Component rebuild(NameMatcher matcher, FormattedText text, boolean withHovers) {
      ProtectedText.Pieces pieces = new ProtectedText.Pieces();
      text.visit(pieces, Style.EMPTY);
      if (pieces.count == 0) {
         return null;
      } else {
         pieces.seal();
         String joined = pieces.text.toString();
         List<NameMatcher.Match> matches = matcher.scan(joined, pieces.starts, pieces.count);
         Style[] hoverStyles = withHovers ? rewriteHovers(matcher, pieces) : null;
         if (matches.isEmpty() && hoverStyles == null) {
            return null;
         } else {
            Style[] styles = hoverStyles != null ? hoverStyles : pieces.styles;
            ProtectedText.Runs runs = new ProtectedText.Runs(pieces);
            int position = 0;

            for (NameMatcher.Match match : matches) {
               runs.copy(joined, position, match.start());
               runs.spread(match.alias(), match.start(), match.end());
               position = match.end();
            }

            runs.copy(joined, position, joined.length());
            return runs.build(styles);
         }
      }
   }

   private static Style[] rewriteHovers(NameMatcher matcher, ProtectedText.Pieces pieces) {
      Style[] out = null;
      Map<HoverEvent, HoverEvent> done = null;

      for (int i = 0; i < pieces.count; i++) {
         Style style = pieces.styles[i];
         HoverEvent hover = style.getHoverEvent();
         if (hover != null) {
            if (done == null) {
               done = new IdentityHashMap<>();
            }

            HoverEvent rewritten = done.get(hover);
            if (rewritten == null) {
               rewritten = rewriteHover(matcher, hover);
               done.put(hover, rewritten);
            }

            if (rewritten != hover) {
               if (out == null) {
                  out = Arrays.copyOf(pieces.styles, pieces.count);
               }

               out[i] = style.withHoverEvent(rewritten);
            }
         }
      }

      return out;
   }

   private static HoverEvent rewriteHover(NameMatcher matcher, HoverEvent hover) {
      if (hover instanceof ShowText showText) {
         Component value = showText.value();
         Component rewritten = value != null ? rebuild(matcher, value, false) : null;
         return (HoverEvent)(rewritten != null ? new ShowText(rewritten) : hover);
      } else if (hover instanceof ShowEntity showEntity) {
         EntityTooltipInfo info = showEntity.entity();
         if (info != null && !info.name.isEmpty()) {
            Component rewritten = rebuild(matcher, info.name.get(), false);
            return (HoverEvent)(rewritten != null ? new ShowEntity(new EntityTooltipInfo(info.type, info.uuid, rewritten)) : hover);
         } else {
            return hover;
         }
      } else {
         return hover;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Pieces implements StyledContentConsumer<Object> {
      final StringBuilder text = new StringBuilder();
      Style[] styles = new Style[8];
      int[] starts = new int[9];
      int count;

      @Override
      public Optional<Object> accept(Style style, String part) {
         if (part.isEmpty()) {
            return Optional.empty();
         } else {
            if (this.count == this.styles.length) {
               this.styles = Arrays.copyOf(this.styles, this.count * 2);
               this.starts = Arrays.copyOf(this.starts, this.count * 2 + 1);
            }

            this.styles[this.count] = style;
            this.starts[this.count] = this.text.length();
            this.count++;
            this.text.append(part);
            return Optional.empty();
         }
      }

      void seal() {
         this.starts[this.count] = this.text.length();
      }

      int pieceAt(int offset) {
         int found = Arrays.binarySearch(this.starts, 0, this.count, offset);
         return found >= 0 ? found : -found - 2;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Runs {
      private final ProtectedText.Pieces pieces;
      private final List<Integer> runPieces = new ArrayList<>();
      private final List<StringBuilder> runTexts = new ArrayList<>();

      Runs(ProtectedText.Pieces pieces) {
         this.pieces = pieces;
      }

      void copy(String joined, int from, int to) {
         for (int piece = from < to ? this.pieces.pieceAt(from) : 0; from < to; piece++) {
            int pieceEnd = Math.min(this.pieces.starts[piece + 1], to);
            this.run(piece).append(joined, from, pieceEnd);
            from = pieceEnd;
         }
      }

      void spread(String alias, int start, int end) {
         int span = end - start;
         int total = alias.codePointCount(0, alias.length());
         int index = 0;

         for (int offset = 0; offset < alias.length(); index++) {
            int codePoint = alias.codePointAt(offset);
            int source = start + (int)((long)index * span / total);
            this.run(this.pieces.pieceAt(source)).appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
         }
      }

      private StringBuilder run(int piece) {
         int last = this.runPieces.size() - 1;
         if (last >= 0 && this.runPieces.get(last) == piece) {
            return this.runTexts.get(last);
         } else {
            StringBuilder text = new StringBuilder();
            this.runPieces.add(piece);
            this.runTexts.add(text);
            return text;
         }
      }

      Component build(Style[] styles) {
         MutableComponent root = Component.empty();

         for (int i = 0; i < this.runTexts.size(); i++) {
            root.append(Component.literal(this.runTexts.get(i).toString()).setStyle(styles[this.runPieces.get(i)]));
         }

         return root;
      }
   }
}
