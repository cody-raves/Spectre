package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface Paint2D {
   int argb(float var1, float var2);

   static Paint2D solid(int argb) {
      return (x, y) -> argb;
   }

   static Paint2D linear(float x0, float y0, float x1, float y1, int... stops) {
      if (stops.length == 0) {
         throw new IllegalArgumentException("gradient needs at least one colour");
      } else {
         float dx = x1 - x0;
         float dy = y1 - y0;
         float l2 = dx * dx + dy * dy;
         float inv = l2 < 1.0E-8F ? 0.0F : 1.0F / l2;
         return (x, y) -> stop(stops, ((x - x0) * dx + (y - y0) * dy) * inv);
      }
   }

   static Paint2D radial(float cx, float cy, float radius, int... stops) {
      if (stops.length == 0) {
         throw new IllegalArgumentException("gradient needs at least one colour");
      } else {
         float inv = 1.0F / Math.max(1.0E-4F, radius);
         return (x, y) -> {
            float dx = x - cx;
            float dy = y - cy;
            return stop(stops, (float)Math.sqrt(dx * dx + dy * dy) * inv);
         };
      }
   }

   static Paint2D elliptic(float cx, float cy, float rx, float ry, int... stops) {
      if (stops.length == 0) {
         throw new IllegalArgumentException("gradient needs at least one colour");
      } else {
         float ix = 1.0F / Math.max(1.0E-4F, rx);
         float iy = 1.0F / Math.max(1.0E-4F, ry);
         return (x, y) -> {
            float dx = (x - cx) * ix;
            float dy = (y - cy) * iy;
            return stop(stops, (float)Math.sqrt(dx * dx + dy * dy));
         };
      }
   }

   static Paint2D noise(int a, int b, float size, float contrast) {
      float f = 1.0F / Math.max(0.001F, size);
      return (x, y) -> {
         float n = Noise.fbm(x * f, y * f, 4) * contrast + 0.5F;
         return Colors.mix(a, b, n < 0.0F ? 0.0F : Math.min(n, 1.0F));
      };
   }

   static Paint2D grain(int light, int dark, float angleDeg, float spacing, float wobble) {
      double a = Math.toRadians(angleDeg);
      float ux = (float)Math.cos(a);
      float uy = (float)Math.sin(a);
      float inv = 1.0F / Math.max(0.1F, spacing);
      return (x, y) -> {
         float along = x * ux + y * uy;
         float across = -x * uy + y * ux;
         float w = Noise.fbm(along * 0.02F, across * 0.05F, 3) * wobble;
         float v = (across + w) * inv;
         float f = v - (float)Math.floor(v);
         float line = Paint.smooth(0.6F, 0.8F, f) * (1.0F - Paint.smooth(0.85F, 1.0F, f));
         float streak = Noise.noise(along * 0.015F, across * inv * 1.7F) * 0.5F + 0.5F;
         float k = line * 0.8F + streak * 0.3F;
         return Colors.mix(light, dark, k < 0.0F ? 0.0F : Math.min(k, 1.0F));
      };
   }

   private static int stop(int[] stops, float t) {
      if (stops.length == 1) {
         return stops[0];
      } else {
         t = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
         float f = t * (stops.length - 1);
         int i = Math.min((int)f, stops.length - 2);
         return Colors.mix(stops[i], stops[i + 1], f - i);
      }
   }
}
