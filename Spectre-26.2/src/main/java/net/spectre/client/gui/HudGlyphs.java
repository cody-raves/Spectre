package net.spectre.client.gui;

import imgui.ImDrawList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class HudGlyphs {
   private static final float TAU = (float) (Math.PI * 2);

   private HudGlyphs() {
   }

   static int ink(int argb, float alpha) {
      return SpectreDraw.col(argb, alpha);
   }

   static int vitality(float fraction) {
      float f = Math.max(0.0F, Math.min(1.0F, fraction));
      return f >= 0.55F ? SpectreDraw.mix(-871878, -12600956, (f - 0.55F) / 0.45F) : SpectreDraw.mix(-2537681, -871878, f / 0.55F);
   }

   static float approach(float current, float target, float rate, float dt) {
      float next = current + (target - current) * (1.0F - (float)Math.exp(-rate * dt));
      return Math.abs(next - target) < 5.0E-4F ? target : next;
   }

   static float easeOutQuint(float t) {
      float inv = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
      return 1.0F - inv * inv * inv * inv * inv;
   }

   static void register(ImDrawList dl, float x0, float x1, float y, float thickness, int abgr) {
      if (!(x1 - x0 < 2.0F)) {
         float mid = (x0 + x1) * 0.5F;
         int clear = abgr & 16777215;
         dl.addRectFilledMultiColor(x0, y, mid, y + thickness, clear, abgr, abgr, clear);
         dl.addRectFilledMultiColor(mid, y, x1, y + thickness, abgr, clear, clear, abgr);
      }
   }

   static void arc(ImDrawList dl, float cx, float cy, float r, float from, float sweep, float thickness, int abgr) {
      if (!(Math.abs(sweep) < 0.002F) && (abgr >>> 24 & 0xFF) != 0) {
         int segments = Math.max(6, Math.round(Math.abs(sweep) / (float) (Math.PI * 2) * 40.0F));
         dl.pathClear();
         dl.pathArcTo(cx, cy, r, from, from + sweep, segments);
         dl.pathStroke(abgr, 0, thickness);
      }
   }

   static void ankh(ImDrawList dl, float cx, float cy, float h, float thickness, int abgr) {
      float top = cy - h * 0.5F;
      float bottom = cy + h * 0.5F;
      float bar = top + h * 0.42F;
      float loopW = h * 0.24F;
      dl.addBezierCubic(cx, bar, cx - loopW * 1.25F, bar - h * 0.12F, cx - loopW, top, cx, top, abgr, thickness, 10);
      dl.addBezierCubic(cx, bar, cx + loopW * 1.25F, bar - h * 0.12F, cx + loopW, top, cx, top, abgr, thickness, 10);
      dl.addLine(cx - h * 0.3F, bar, cx + h * 0.3F, bar, abgr, thickness);
      dl.addQuadFilled(cx - thickness * 0.5F, bar, cx + thickness * 0.5F, bar, cx + thickness * 1.1F, bottom, cx - thickness * 1.1F, bottom, abgr);
   }

   static void sparkle(ImDrawList dl, float cx, float cy, float r, int abgr) {
      float w = r * 0.3F;
      dl.addQuadFilled(cx, cy - r, cx + w, cy, cx, cy + r, cx - w, cy, abgr);
      dl.addQuadFilled(cx - r, cy, cx, cy - w, cx + r, cy, cx, cy + w, abgr);
   }

   static void hourglass(ImDrawList dl, float cx, float cy, float h, float thickness, int abgr) {
      float half = h * 0.5F;
      float w = h * 0.36F;
      dl.addLine(cx - w, cy - half, cx + w, cy - half, abgr, thickness);
      dl.addLine(cx - w, cy + half, cx + w, cy + half, abgr, thickness);
      dl.addTriangleFilled(cx - w * 0.8F, cy - half, cx + w * 0.8F, cy - half, cx, cy, abgr);
      dl.addTriangle(cx - w * 0.8F, cy + half, cx + w * 0.8F, cy + half, cx, cy, abgr, thickness);
   }

   static void horusEye(ImDrawList dl, float cx, float cy, float r, float thickness, int abgr) {
      float l = cx - r;
      float rr = cx + r * 0.9F;
      dl.addBezierQuadratic(l, cy, cx, cy - r * 0.78F, rr, cy, abgr, thickness, 14);
      dl.addBezierQuadratic(l, cy, cx, cy + r * 0.5F, rr, cy, abgr, thickness, 14);
      dl.addCircleFilled(cx - r * 0.05F, cy - r * 0.04F, r * 0.24F, abgr, 12);
      dl.addBezierQuadratic(l - r * 0.05F, cy - r * 0.55F, cx, cy - r * 0.98F, rr + r * 0.15F, cy - r * 0.62F, abgr, thickness, 14);
      dl.addLine(rr, cy, rr + r * 0.34F, cy - r * 0.04F, abgr, thickness);
      dl.addLine(cx - r * 0.12F, cy + r * 0.24F, cx - r * 0.34F, cy + r * 0.95F, abgr, thickness);
      dl.addBezierCubic(
         cx + r * 0.2F, cy + r * 0.24F, cx + r * 0.3F, cy + r * 0.8F, cx + r * 0.78F, cy + r * 0.9F, cx + r * 0.72F, cy + r * 0.6F, abgr, thickness, 12
      );
   }
}
