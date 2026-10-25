package net.spectre.client.util.glyph;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class GlyphTint {
   public static final String THEME = "Theme";
   public static final String MANUAL = "Manual";
   private static final float WARM = 0.16666667F;
   private static final float COOL = 0.6666667F;
   private static final float COMPANION_TURN = -0.09F;
   private int primary;
   private int secondary;
   private int highlight;
   private int companion;
   private int dust;
   private int flash;
   private float hue;
   private float saturation;
   private float value;

   public GlyphTint resolve(String mode, int accent, int manualMain, int manualSecond) {
      boolean manual = "Manual".equals(mode);
      this.primary = ARGB.opaque(manual ? manualMain : accent);
      this.split(this.primary);
      if (manual) {
         this.secondary = ARGB.opaque(manualSecond);
         this.companion = mix(this.primary, this.secondary, 0.5F);
      } else {
         this.secondary = hsv(toward(this.hue, 0.6666667F, 0.025F), this.saturation * 1.05F, this.value * 0.66F);
         this.companion = hsv(this.hue + -0.09F, this.saturation * 0.9F, this.value * 0.94F);
      }

      this.highlight = hsv(toward(this.hue, 0.16666667F, 0.03F), this.saturation * 0.5F, this.value + (1.0F - this.value) * 0.85F);
      this.dust = hsv(toward(this.hue, 0.16666667F, 0.02F), this.saturation * 0.34F, 0.5F + this.value * 0.4F);
      this.flash = mix(this.highlight, -1, 0.6F);
      return this;
   }

   private void split(int rgb) {
      int r = ARGB.red(rgb);
      int g = ARGB.green(rgb);
      int b = ARGB.blue(rgb);
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      float range = max - min;
      this.value = max / 255.0F;
      this.saturation = max == 0 ? 0.0F : range / max;
      if (range == 0.0F) {
         this.hue = 0.0F;
      } else if (max == r) {
         this.hue = Mth.frac((g - b) / range / 6.0F);
      } else if (max == g) {
         this.hue = (2.0F + (b - r) / range) / 6.0F;
      } else {
         this.hue = (4.0F + (r - g) / range) / 6.0F;
      }
   }

   private static float toward(float from, float target, float step) {
      float delta = target - from;
      delta -= Mth.floor(delta + 0.5F);
      return Math.abs(delta) <= step ? target : from + Math.copySign(step, delta);
   }

   private static int hsv(float h, float s, float v) {
      float wrapped = Mth.frac(h);
      if (wrapped >= 1.0F) {
         wrapped = 0.0F;
      }

      return Mth.hsvToArgb(wrapped, Mth.clamp(s, 0.0F, 1.0F), Mth.clamp(v, 0.0F, 1.0F), 255);
   }

   public int primary() {
      return this.primary;
   }

   public int secondary() {
      return this.secondary;
   }

   public int highlight() {
      return this.highlight;
   }

   public int companion() {
      return this.companion;
   }

   public int dust() {
      return this.dust;
   }

   public int flash() {
      return this.flash;
   }

   public static int alpha(int rgb, float alpha) {
      float clamped = alpha <= 0.0F ? 0.0F : Math.min(1.0F, alpha);
      return (int)(clamped * 255.0F + 0.5F) << 24 | rgb & 16777215;
   }

   public static int mix(int from, int to, float k) {
      return ARGB.srgbLerp(Math.max(0.0F, Math.min(1.0F, k)), from, to);
   }

   public static int lift(int rgb, float amount) {
      return mix(ARGB.opaque(rgb), -1, amount);
   }
}
