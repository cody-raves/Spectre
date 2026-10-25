package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
final class AtmosphereSky {
   private static final float RADIUS = 100.0F;
   private static final float[] RINGS = new float[]{-30.0F, -12.0F, -4.0F, 0.0F, 4.0F, 9.0F, 16.0F, 26.0F, 40.0F, 58.0F, 76.0F, 90.0F};
   private static final int SEGMENTS = 48;
   private static final int HALO_RINGS = 4;
   private static final int HALO_SEGMENTS = 32;
   private static final int METEOR_SEGMENTS = 10;
   private static final float[] KEYS = new float[]{-0.35F, -0.08F, 0.06F, 0.3F};
   private static final int GOLDEN_ZENITH = 5210048;
   private static final int GOLDEN_HORIZON = 16032864;
   private static final int GOLDEN_GLOW = 16747069;
   private static final int DAY_ZENITH = 3108822;
   private static final int DAY_HORIZON = 11850996;
   private static final int DAY_GLOW = 16774362;
   private static final int BLUE_HOUR_ZENITH = 1319497;
   private final SkyMesh dome = new SkyMesh("Spectre sky dome", SkyMesh.PAINT, (RINGS.length - 1) * 48);
   private final SkyMesh endDome = new SkyMesh("Spectre end sky", SkyMesh.PAINT, (RINGS.length - 1) * 48);
   private final SkyMesh halo = new SkyMesh("Spectre sun and moon glow", SkyMesh.GLOW, 256);
   private final SkyMesh meteorMesh = new SkyMesh("Spectre shooting stars", SkyMesh.GLOW, 12);
   private final RandomSource random = RandomSource.create();
   private int zenith;
   private int horizon;
   private int glow;
   private float glowStrength;
   private float sunX;
   private float sunY;
   private float sunAngle;
   private float moonAngle;
   private boolean ready;
   private long meteorStart;
   private long meteorEnd;
   private long nextMeteor;
   private float mx;
   private float my;
   private float mz;
   private float tx;
   private float ty;
   private float tz;

   void update(float sunAngle, float moonAngle, float rain, float thunder, float flash, int nightZenith, int nightHorizon, int twilight) {
      this.sunAngle = sunAngle;
      this.moonAngle = moonAngle;
      this.sunX = -Mth.sin(sunAngle);
      this.sunY = Mth.cos(sunAngle);
      int blueHourZenith = ARGB.srgbLerp(0.3F, 1319497, nightZenith);
      int[] zeniths = new int[]{nightZenith, blueHourZenith, 5210048, 3108822};
      int[] horizons = new int[]{nightHorizon, scale(twilight, 0.75F), 16032864, 11850996};
      int[] glows = new int[]{nightHorizon, twilight, 16747069, 16774362};
      float[] strengths = new float[]{0.0F, 0.55F, 1.0F, 0.25F};
      int key = 0;

      while (key < KEYS.length - 2 && this.sunY > KEYS[key + 1]) {
         key++;
      }

      float t = AtmosphereAurora.smoothstep((this.sunY - KEYS[key]) / (KEYS[key + 1] - KEYS[key]));
      this.zenith = ARGB.srgbLerp(t, zeniths[key], zeniths[key + 1]);
      this.horizon = ARGB.srgbLerp(t, horizons[key], horizons[key + 1]);
      this.glow = ARGB.srgbLerp(t, glows[key], glows[key + 1]);
      this.glowStrength = Mth.lerp(t, strengths[key], strengths[key + 1]) * (1.0F - rain);
      this.zenith = weather(this.zenith, rain, thunder, flash);
      this.horizon = weather(this.horizon, rain, thunder, flash);
      this.ready = true;
   }

   private static int weather(int rgb, float rain, float thunder, float flash) {
      int grey = scale(luma(rgb), 0.85F);
      rgb = ARGB.srgbLerp(rain * 0.65F, rgb, grey);
      rgb = scale(rgb, 1.0F - 0.35F * rain - 0.25F * thunder);
      return flash > 0.0F ? ARGB.srgbLerp(flash * 0.45F, rgb, 15265023) : rgb;
   }

   boolean ready() {
      return this.ready;
   }

   int horizonToward(float x, float z) {
      float length = Mth.sqrt(x * x + z * z);
      return length < 1.0E-4F ? this.horizon : this.colourAt(x / length, 0.02F, z / length);
   }

   float golden() {
      return 1.0F - Math.min(1.0F, Math.abs(this.sunY - 0.06F) / 0.22F);
   }

   float nightness() {
      return AtmosphereAurora.smoothstep((0.05F - this.sunY) / 0.4F);
   }

   int twilightGlow() {
      return this.glow;
   }

   void renderDome(PoseStack pose) {
      BufferBuilder builder = this.dome.begin();
      this.domeQuads(builder, false);
      this.dome.upload(builder);
      this.dome.draw(pose, 1.0F);
   }

   void renderEndDome(PoseStack pose) {
      if (!this.endDome.isBuilt()) {
         BufferBuilder builder = this.endDome.begin();
         this.domeQuads(builder, true);
         this.endDome.upload(builder);
      }

      this.endDome.draw(pose, 1.0F);
   }

   private void domeQuads(BufferBuilder builder, boolean end) {
      for (int ring = 0; ring < RINGS.length - 1; ring++) {
         for (int s = 0; s < 48; s++) {
            this.corner(builder, RINGS[ring], s, end);
            this.corner(builder, RINGS[ring], s + 1, end);
            this.corner(builder, RINGS[ring + 1], s + 1, end);
            this.corner(builder, RINGS[ring + 1], s, end);
         }
      }
   }

   private void corner(BufferBuilder builder, float elevationDeg, int segment, boolean end) {
      float el = elevationDeg * (float) (Math.PI / 180.0);
      float az = segment / 48.0F * (float) (Math.PI * 2);
      float x = Mth.cos(el) * Mth.cos(az);
      float y = Mth.sin(el);
      float z = Mth.cos(el) * Mth.sin(az);
      int rgb = end ? endColourAt(y) : this.colourAt(x, y, z);
      SkyMesh.vertex(builder, x * 100.0F, y * 100.0F, z * 100.0F, 0xFF000000 | rgb);
   }

   private int colourAt(float x, float y, float z) {
      int rgb = ARGB.srgbLerp((float)Math.sqrt(Math.max(y, 0.0F)), this.horizon, this.zenith);
      if (y < 0.0F) {
         rgb = scale(rgb, 1.0F - 0.35F * AtmosphereAurora.smoothstep(-y / 0.4F));
      }

      if (this.glowStrength > 0.001F) {
         float toSun = Math.max(0.0F, x * this.sunX + y * this.sunY);
         float horizontal = Math.max(0.0F, this.sunX < 0.0F ? -x : x);
         float near = (float)(Math.pow(toSun, 8.0) * 0.9 + Math.pow(toSun, 2.0) * 0.25);
         float band = (float)(Math.exp(-Math.abs(y) * 9.0F) * Math.pow(horizontal, 1.5));
         float amount = Mth.clamp(this.glowStrength * (near + band * 0.6F), 0.0F, 1.0F);
         rgb = ARGB.srgbLerp(amount, rgb, this.glow);
      }

      return rgb & 16777215;
   }

   private static int endColourAt(float y) {
      return ARGB.srgbLerp((float)Math.sqrt(Math.max(y, 0.0F)), 1839152, 459789) & 16777215;
   }

   void renderHalos(PoseStack pose, float rainBrightness, float night) {
      BufferBuilder builder = this.halo.begin();
      float low = 1.0F - AtmosphereAurora.smoothstep(this.sunY / 0.35F);
      float sunAlpha = AtmosphereAurora.smoothstep((this.sunY + 0.12F) / 0.12F) * rainBrightness * (0.45F + 0.35F * low);
      int sunColour = ARGB.srgbLerp(low, 16773584, 16742963);
      haloQuads(builder, this.sunAngle, 28.0F * (1.0F + 0.6F * low), sunColour, sunAlpha);
      haloQuads(builder, this.moonAngle, 34.0F, 11520255, 0.32F * night * rainBrightness);
      this.halo.upload(builder);
      this.halo.draw(pose, 1.0F);
   }

   private static void haloQuads(BufferBuilder builder, float angle, float radius, int rgb, float alpha) {
      if (!(alpha <= 0.002F)) {
         float[] radii = new float[]{0.0F, radius * 0.16F, radius * 0.38F, radius * 0.66F, radius};
         float[] alphas = new float[]{1.0F, 0.55F, 0.22F, 0.07F, 0.0F};
         float cosA = Mth.cos(angle);
         float sinA = Mth.sin(angle);

         for (int ring = 0; ring < 4; ring++) {
            for (int s = 0; s < 32; s++) {
               haloVertex(builder, radii[ring], s, cosA, sinA, rgb, alphas[ring] * alpha);
               haloVertex(builder, radii[ring], s + 1, cosA, sinA, rgb, alphas[ring] * alpha);
               haloVertex(builder, radii[ring + 1], s + 1, cosA, sinA, rgb, alphas[ring + 1] * alpha);
               haloVertex(builder, radii[ring + 1], s, cosA, sinA, rgb, alphas[ring + 1] * alpha);
            }
         }
      }
   }

   private static void haloVertex(BufferBuilder builder, float r, int segment, float cosA, float sinA, int rgb, float alpha) {
      float around = segment / 32.0F * (float) (Math.PI * 2);
      float lx = Mth.cos(around) * r;
      float ly = 100.0F;
      float lz = Mth.sin(around) * r;
      float y = ly * cosA - lz * sinA;
      float z = ly * sinA + lz * cosA;
      SkyMesh.vertex(builder, -z, y, lx, Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 16777215);
   }

   void renderMeteors(PoseStack pose, float night, float rainBrightness) {
      long now = System.nanoTime();
      if (now >= this.meteorEnd) {
         if (this.nextMeteor == 0L) {
            this.nextMeteor = now + seconds(4.0F + 10.0F * this.random.nextFloat());
         }

         if (now < this.nextMeteor || night < 0.6F || rainBrightness < 0.8F) {
            return;
         }

         this.launch(now);
      }

      float life = (float)(now - this.meteorStart) / (float)(this.meteorEnd - this.meteorStart);
      float fade = Mth.sin((float) Math.PI * Mth.clamp(life, 0.0F, 1.0F)) * night * rainBrightness;
      BufferBuilder builder = this.meteorMesh.begin();
      float travel = life * 0.55F;
      float tail = 0.16F;
      float[] prev = null;

      for (int i = 0; i <= 10; i++) {
         float along = travel - tail * i / 10.0F;
         float[] p = new float[]{(this.mx + this.tx * along) * 100.0F, (this.my + this.ty * along) * 100.0F, (this.mz + this.tz * along) * 100.0F};
         if (prev != null) {
            streak(builder, prev, p, fade * (1.0F - (i - 1.0F) / 10.0F), fade * (1.0F - i / 10.0F));
         }

         prev = p;
      }

      this.meteorMesh.upload(builder);
      this.meteorMesh.draw(pose, 1.0F);
   }

   private void launch(long now) {
      float el = (30.0F + 40.0F * this.random.nextFloat()) * (float) (Math.PI / 180.0);
      float az = this.random.nextFloat() * (float) (Math.PI * 2);
      this.mx = Mth.cos(el) * Mth.cos(az);
      this.my = Mth.sin(el);
      this.mz = Mth.cos(el) * Mth.sin(az);
      float dx = -Mth.sin(az);
      float dz = Mth.cos(az);
      float dy = -0.6F - 0.4F * this.random.nextFloat();
      float dot = dx * this.mx + dy * this.my + dz * this.mz;
      this.tx = dx - dot * this.mx;
      this.ty = dy - dot * this.my;
      this.tz = dz - dot * this.mz;
      float length = Mth.sqrt(this.tx * this.tx + this.ty * this.ty + this.tz * this.tz);
      this.tx /= length;
      this.ty /= length;
      this.tz /= length;
      this.meteorStart = now;
      this.meteorEnd = now + seconds(0.7F + 0.5F * this.random.nextFloat());
      this.nextMeteor = this.meteorEnd + seconds(6.0F + 18.0F * this.random.nextFloat());
   }

   private static void streak(BufferBuilder builder, float[] a, float[] b, float alphaA, float alphaB) {
      float lx = b[0] - a[0];
      float ly = b[1] - a[1];
      float lz = b[2] - a[2];
      float wx = a[1] * lz - a[2] * ly;
      float wy = a[2] * lx - a[0] * lz;
      float wz = a[0] * ly - a[1] * lx;
      float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
      if (!(wl < 1.0E-5F)) {
         float half = 0.22F / wl;
         wx *= half;
         wy *= half;
         wz *= half;
         int ca = Math.round(Mth.clamp(alphaA, 0.0F, 1.0F) * 255.0F) << 24 | 15397631;
         int cb = Math.round(Mth.clamp(alphaB, 0.0F, 1.0F) * 255.0F) << 24 | 15397631;
         SkyMesh.vertex(builder, a[0] - wx, a[1] - wy, a[2] - wz, ca);
         SkyMesh.vertex(builder, a[0] + wx, a[1] + wy, a[2] + wz, ca);
         SkyMesh.vertex(builder, b[0] + wx, b[1] + wy, b[2] + wz, cb);
         SkyMesh.vertex(builder, b[0] - wx, b[1] - wy, b[2] - wz, cb);
      }
   }

   private static long seconds(float s) {
      return (long)(s * 1.0E9F);
   }

   static int scale(int rgb, float k) {
      int r = Mth.clamp(Math.round((rgb >> 16 & 0xFF) * k), 0, 255);
      int g = Mth.clamp(Math.round((rgb >> 8 & 0xFF) * k), 0, 255);
      int b = Mth.clamp(Math.round((rgb & 0xFF) * k), 0, 255);
      return rgb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   static int luma(int rgb) {
      int l = Math.round(0.2126F * (rgb >> 16 & 0xFF) + 0.7152F * (rgb >> 8 & 0xFF) + 0.0722F * (rgb & 0xFF));
      return rgb & 0xFF000000 | l << 16 | l << 8 | l;
   }

   void close() {
      this.dome.close();
      this.endDome.close();
      this.halo.close();
      this.meteorMesh.close();
      this.ready = false;
   }
}
