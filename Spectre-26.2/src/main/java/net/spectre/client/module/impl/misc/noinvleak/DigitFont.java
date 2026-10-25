package net.spectre.client.module.impl.misc.noinvleak;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
final class DigitFont {
   private static final byte[][] DIGITS = new byte[][]{
      {14, 17, 19, 21, 25, 17, 14},
      {4, 12, 4, 4, 4, 4, 14},
      {14, 17, 1, 6, 8, 16, 31},
      {14, 17, 1, 6, 1, 17, 14},
      {2, 6, 10, 18, 31, 2, 2},
      {31, 16, 30, 1, 1, 17, 14},
      {14, 16, 30, 17, 17, 17, 14},
      {31, 1, 2, 4, 8, 8, 8},
      {14, 17, 17, 14, 17, 17, 14},
      {14, 17, 17, 15, 1, 17, 14}
   };
   private static final int SHADOW = -12632257;
   private static final int WHITE = -1;

   private DigitFont() {
   }

   static void decorations(DigitFont.Sink sink, ItemStack item, int ox, int oy, int scale) {
      if (item.isBarVisible()) {
         sink.rect(ox + 2 * scale, oy + 13 * scale, 13 * scale, 2 * scale, -16777216);
         sink.rect(ox + 2 * scale, oy + 13 * scale, item.getBarWidth() * scale, scale, 0xFF000000 | item.getBarColor());
      }

      if (item.getCount() != 1) {
         count(sink, item.getCount(), ox, oy, scale);
      }
   }

   private static void count(DigitFont.Sink sink, int count, int ox, int oy, int scale) {
      String text = Integer.toString(count);
      int x = ox + (17 - 6 * text.length()) * scale;
      int y = oy + 9 * scale;

      for (int i = 0; i < text.length(); i++) {
         int digit = text.charAt(i) - '0';
         if (digit >= 0 && digit <= 9) {
            int gx = x + i * 6 * scale;
            glyph(sink, DIGITS[digit], gx + scale, y + scale, scale, -12632257);
            glyph(sink, DIGITS[digit], gx, y, scale, -1);
         }
      }
   }

   private static void glyph(DigitFont.Sink sink, byte[] rows, int x, int y, int scale, int argb) {
      for (int r = 0; r < rows.length; r++) {
         int c = 0;

         while (c < 5) {
            if ((rows[r] >> 4 - c & 1) == 0) {
               c++;
            } else {
               int start = c;

               while (c < 5 && (rows[r] >> 4 - c & 1) != 0) {
                  c++;
               }

               sink.rect(x + start * scale, y + r * scale, (c - start) * scale, scale, argb);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   interface Sink {
      void rect(int var1, int var2, int var3, int var4, int var5);
   }
}
