package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import net.spectre.client.gui.SpectreDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class DuskPill {
   private static final float BAND = 0.2F;
   private static final float STRIP = 4.0F;

   private DuskPill() {
   }

   static void frame(MenuFrame c, float x, float y, float w, float h, float hover, float pass, float a) {
      ImDrawList dl = c.bg();
      float s = c.scale();
      float r = h * 0.5F;
      if (hover > 0.01F) {
         float glow = hover * glow(c) * a;
         stroke(dl, x, y, w, h, r, SpectreDraw.col(0xff72e6ef, 0.1F * glow), 7.0F * s);
         stroke(dl, x, y, w, h, r, SpectreDraw.col(0xff72e6ef, 0.18F * glow), 3.5F * s);
      }

      stroke(dl, x, y, w, h, r, SpectreDraw.col(-16777216, 0.2F * a), 3.0F * s);
      stroke(dl, x, y, w, h, r, SpectreDraw.col(SpectreDraw.mix(0xff72e6ef, 0xffeaf8ff, hover), (0.7F + 0.3F * hover) * a), Math.max(1.0F, s));
      if (pass >= 0.0F) {
         light(dl, x, y, w, h, r, s, pass, a);
      }
   }

   static void halo(MenuFrame c, ImFont font, float x, float y, float track, float hover, String label) {
      if (!(hover <= 0.01F)) {
         float s = c.scale();
         float amount = hover * glow(c);

         for (int ring = 1; ring <= 2; ring++) {
            for (int i = 0; i < 8; i++) {
               double angle = i * Math.PI / 4.0;
               float d = ring * 1.8F * s;
               c.spacedText(
                  c.bg(), font, x + (float)Math.cos(angle) * d, y + (float)Math.sin(angle) * d, track, 0xff72e6ef, (ring == 1 ? 0.1F : 0.06F) * amount, label
               );
            }
         }
      }
   }

   private static float glow(MenuFrame c) {
      return 0.7F + 0.3F * DuskEmblem.breath(c.time());
   }

   private static void light(ImDrawList dl, float x, float y, float w, float h, float r, float s, float pass, float a) {
      float half = w * 0.2F;
      float pad = 5.0F * s;
      float step = 4.0F * s;
      float centre = x - half + pass * (w + half * 2.0F);
      float start = centre - half;
      int strips = (int)Math.ceil(half * 2.0F / step);

      for (int i = 0; i < strips; i++) {
         float x0 = Math.max((float)Math.round(start + i * step), x - pad);
         float x1 = Math.min((float)Math.round(start + (i + 1) * step), x + w + pad);
         float k = DuskEmblem.smooth(1.0F - Math.abs(start + (i + 0.5F) * step - centre) / half);
         if (!(k < 0.02F) && !(x1 <= x0)) {
            dl.pushClipRect(x0, y - pad, x1, y + h + pad, true);
            stroke(dl, x, y, w, h, r, SpectreDraw.col(0xff72e6ef, 0.26F * k * a), 8.0F * s);
            stroke(dl, x, y, w, h, r, SpectreDraw.col(0xffeaf8ff, 0.42F * k * a), 4.5F * s);
            stroke(dl, x, y, w, h, r, SpectreDraw.col(-3633, 0.95F * k * a), Math.max(2.0F, 2.0F * s));
            dl.popClipRect();
         }
      }
   }

   private static void stroke(ImDrawList dl, float x, float y, float w, float h, float r, int color, float thickness) {
      dl.addRect(x, y, x + w, y + h, color, r, 0, thickness);
   }
}
