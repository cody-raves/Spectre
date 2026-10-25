package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Sample {
   public float r;
   public float g;
   public float b;
   public float glow;
   public float flat;
   public float flatWeight;
   public float occlusion;
   public float occlusionWeight;

   public void set(int argb) {
      this.r = (argb >> 16 & 0xFF) / 255.0F;
      this.g = (argb >> 8 & 0xFF) / 255.0F;
      this.b = (argb & 0xFF) / 255.0F;
      this.glow = 0.0F;
      this.flat = 0.0F;
      this.flatWeight = 0.0F;
      this.occlusion = 0.0F;
      this.occlusionWeight = 0.0F;
   }

   public void set(Sample o) {
      this.r = o.r;
      this.g = o.g;
      this.b = o.b;
      this.glow = o.glow;
      this.flat = o.flat;
      this.flatWeight = o.flatWeight;
      this.occlusion = o.occlusion;
      this.occlusionWeight = o.occlusionWeight;
   }

   public void mix(Sample o, float t) {
      float s = 1.0F - t;
      this.r = this.r * s + o.r * t;
      this.g = this.g * s + o.g * t;
      this.b = this.b * s + o.b * t;
      this.glow = this.glow * s + o.glow * t;
      this.flat = this.flat * s + o.flat * t;
      this.flatWeight = this.flatWeight * s + o.flatWeight * t;
      this.occlusion = this.occlusion * s + o.occlusion * t;
      this.occlusionWeight = this.occlusionWeight * s + o.occlusionWeight * t;
   }

   public void toward(int argb, float t) {
      float s = 1.0F - t;
      this.r = this.r * s + (argb >> 16 & 0xFF) / 255.0F * t;
      this.g = this.g * s + (argb >> 8 & 0xFF) / 255.0F * t;
      this.b = this.b * s + (argb & 0xFF) / 255.0F * t;
   }

   public int argb() {
      return 0xFF000000 | channel(this.r) << 16 | channel(this.g) << 8 | channel(this.b);
   }

   float flat(float fallback) {
      return blend(fallback, this.flat, this.flatWeight);
   }

   float occlusion(float fallback) {
      return blend(fallback, this.occlusion, this.occlusionWeight);
   }

   private static float blend(float fallback, float weighted, float weight) {
      if (weight <= 1.0E-4F) {
         return fallback;
      } else {
         float w = Math.min(1.0F, weight);
         return Math.max(0.0F, Math.min(1.0F, fallback * (1.0F - w) + weighted * (w / weight)));
      }
   }

   static int channel(float v) {
      int c = Math.round(v * 255.0F);
      return c < 0 ? 0 : Math.min(c, 255);
   }
}
