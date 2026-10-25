package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Noise {
   private static final int[] PERM = new int[512];

   private Noise() {
   }

   public static float noise(float x, float y, float z) {
      int xi = floor(x);
      int yi = floor(y);
      int zi = floor(z);
      float xf = x - xi;
      float yf = y - yi;
      float zf = z - zi;
      int X = xi & 0xFF;
      int Y = yi & 0xFF;
      int Z = zi & 0xFF;
      float u = fade(xf);
      float v = fade(yf);
      float w = fade(zf);
      int a = PERM[X] + Y;
      int aa = PERM[a] + Z;
      int ab = PERM[a + 1] + Z;
      int b = PERM[X + 1] + Y;
      int ba = PERM[b] + Z;
      int bb = PERM[b + 1] + Z;
      float x1 = lerp(grad(PERM[aa], xf, yf, zf), grad(PERM[ba], xf - 1.0F, yf, zf), u);
      float x2 = lerp(grad(PERM[ab], xf, yf - 1.0F, zf), grad(PERM[bb], xf - 1.0F, yf - 1.0F, zf), u);
      float y1 = lerp(x1, x2, v);
      float x3 = lerp(grad(PERM[aa + 1], xf, yf, zf - 1.0F), grad(PERM[ba + 1], xf - 1.0F, yf, zf - 1.0F), u);
      float x4 = lerp(grad(PERM[ab + 1], xf, yf - 1.0F, zf - 1.0F), grad(PERM[bb + 1], xf - 1.0F, yf - 1.0F, zf - 1.0F), u);
      return lerp(y1, lerp(x3, x4, v), w);
   }

   public static float noise(float x, float y) {
      return noise(x, y, 0.37F);
   }

   public static float fbm(float x, float y, float z, int octaves) {
      float sum = 0.0F;
      float amp = 0.5F;
      float norm = 0.0F;

      for (int i = 0; i < octaves; i++) {
         sum += amp * noise(x, y, z);
         norm += amp;
         x = x * 2.03F + 17.1F;
         y = y * 2.03F - 4.7F;
         z = z * 2.03F + 9.3F;
         amp *= 0.5F;
      }

      return sum / norm;
   }

   public static float fbm(float x, float y, int octaves) {
      return fbm(x, y, 0.37F, octaves);
   }

   public static float hash(int x, int y, int z) {
      int h = x * 522133279 ^ y * 1540483477 ^ z * 668265263;
      h ^= h >>> 13;
      h *= -2048144789;
      h ^= h >>> 16;
      return (h & 65535) / 65535.0F;
   }

   private static int floor(float v) {
      int i = (int)v;
      return v < i ? i - 1 : i;
   }

   private static float fade(float t) {
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float grad(int hash, float x, float y, float z) {
      int h = hash & 15;
      float u = h < 8 ? x : y;
      float v = h < 4 ? y : (h != 12 && h != 14 ? z : x);
      return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
   }

   static {
      int[] p = new int[256];
      int i = 0;

      while (i < 256) {
         p[i] = i++;
      }

      long seed = 25214903917L;

      for (int ix = 255; ix > 0; ix--) {
         seed = seed * 6364136223846793005L + 1442695040888963407L;
         int j = (int)((seed >>> 33) % (ix + 1));
         int t = p[ix];
         p[ix] = p[j];
         p[j] = t;
      }

      for (int ix = 0; ix < 512; ix++) {
         PERM[ix] = p[ix & 0xFF];
      }
   }
}
