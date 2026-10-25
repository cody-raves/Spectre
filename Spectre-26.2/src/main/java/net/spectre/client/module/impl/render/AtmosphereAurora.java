package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
final class AtmosphereAurora {
   private static final float RADIUS = 100.0F;
   private static final int COLUMNS = 64;
   private static final int ROWS = 6;
   private static final float[][] CURTAINS = new float[][]{{-40.0F, 22.0F, 78.0F, 18.0F}, {6.0F, 28.0F, 100.0F, 23.0F}, {54.0F, 20.0F, 70.0F, 16.0F}};
   private final SkyMesh mesh = new SkyMesh("Spectre aurora", SkyMesh.GLOW, CURTAINS.length * 64 * 6);
   private final float[] x = new float[455];
   private final float[] y = new float[this.x.length];
   private final float[] z = new float[this.x.length];
   private final int[] color = new int[this.x.length];

   void render(PoseStack pose, float intensity, float time, int low, int high) {
      BufferBuilder builder = this.mesh.begin();

      for (int i = 0; i < CURTAINS.length; i++) {
         this.curtain(builder, CURTAINS[i], i, time, low, high);
      }

      this.mesh.upload(builder);
      this.mesh.draw(pose, intensity);
   }

   private void curtain(BufferBuilder builder, float[] shape, int seed, float time, int low, int high) {
      float azimuth = shape[0];
      float elevation = shape[1];
      float width = shape[2];
      float height = shape[3];

      for (int c = 0; c <= 64; c++) {
         float t = c / 64.0F;
         float az = azimuth + (t - 0.5F) * width + 3.0F * wave(t * 2.1F + time * 0.05F + seed);
         float base = elevation + 5.0F * Mth.sin((float) Math.PI * t) + 3.0F * wave(t * 3.7F - time * 0.07F + seed * 2.0F);
         float ends = smoothstep(t / 0.25F) * smoothstep((1.0F - t) / 0.25F);
         float glow = ends * (0.55F + 0.45F * wave01(t * 5.3F + time * 0.11F + seed * 1.3F));
         float rays = 0.72F + 0.28F * wave01(t * 23.0F + time * 0.35F + seed);
         float top = height * (0.8F + 0.35F * wave01(t * 4.1F + time * 0.06F + seed * 3.0F));
         float cosAz = Mth.cos(az * (float) (Math.PI / 180.0));
         float sinAz = Mth.sin(az * (float) (Math.PI / 180.0));

         for (int r = 0; r <= 6; r++) {
            float v = r / 6.0F;
            float el = (base + v * top) * (float) (Math.PI / 180.0);
            float cosEl = Mth.cos(el);
            int i = c * 7 + r;
            this.x[i] = 100.0F * cosEl * sinAz;
            this.y[i] = 100.0F * Mth.sin(el);
            this.z[i] = -100.0F * cosEl * cosAz;
            float profile = v < 0.12F ? smoothstep(v / 0.12F) : (float)Math.pow(1.0F - (v - 0.12F) / 0.88F, 1.5);
            float alpha = Mth.clamp(glow * rays * profile * 0.8F, 0.0F, 1.0F);
            int rgb = ARGB.srgbLerp((float)Math.pow(v, 0.7), low, high) & 16777215;
            this.color[i] = Math.round(alpha * 255.0F) << 24 | rgb;
         }
      }

      for (int c = 0; c < 64; c++) {
         for (int r = 0; r < 6; r++) {
            int a = c * 7 + r;
            int b = (c + 1) * 7 + r;
            this.vertex(builder, a);
            this.vertex(builder, b);
            this.vertex(builder, b + 1);
            this.vertex(builder, a + 1);
         }
      }
   }

   private void vertex(BufferBuilder builder, int i) {
      SkyMesh.vertex(builder, this.x[i], this.y[i], this.z[i], this.color[i]);
   }

   static float wave(float s) {
      return 0.5F * Mth.sin(s) + 0.3F * Mth.sin(s * 2.13F + 1.3F) + 0.2F * Mth.sin(s * 4.37F + 2.9F);
   }

   static float wave01(float s) {
      return 0.5F + 0.5F * wave(s);
   }

   static float smoothstep(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   void close() {
      this.mesh.close();
   }
}
