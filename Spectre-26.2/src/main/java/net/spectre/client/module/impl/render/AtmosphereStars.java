package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class AtmosphereStars {
   private static final float RADIUS = 100.0F;
   private static final int STARS = 2600;
   private static final float GLOW_ABOVE = 0.8F;
   private static final long TWINKLE_NANOS = 50000000L;
   private static final int BAND_COLUMNS = 180;
   private static final int BAND_ROWS = 12;
   private static final float BAND_HALF_WIDTH = 17.0F;
   private static final int NEBULA_COLUMNS = 72;
   private static final int NEBULA_ROWS = 36;
   private static final Vector3f BAND_NORMAL = new Vector3f(0.42F, 0.5F, 0.76F).normalize();
   private final SkyMesh starMesh = new SkyMesh("Spectre stars", SkyMesh.GLOW, 5200);
   private final SkyMesh bandMesh = new SkyMesh("Spectre milky way", SkyMesh.GLOW, 2160);
   private final SkyMesh nebulaMesh = new SkyMesh("Spectre end nebulae", SkyMesh.GLOW, 2592);
   private final float[] dir = new float[7800];
   private final float[] size = new float[2600];
   private final float[] light = new float[2600];
   private final float[] roll = new float[2600];
   private final float[] phase = new float[2600];
   private final float[] speed = new float[2600];
   private final int[] rgb = new int[2600];
   private boolean placed;
   private long lastTwinkle;
   private float builtBrightness = -1.0F;
   private int bandTint;
   private int nebulaTint;

   void renderStars(PoseStack pose, float alpha, float brightness) {
      long now = System.nanoTime();
      if (now - this.lastTwinkle > 50000000L || brightness != this.builtBrightness) {
         this.lastTwinkle = now;
         this.builtBrightness = brightness;
         this.buildStars((float)(now % 3600000000000L) / 1.0E9F, brightness);
      }

      this.starMesh.draw(pose, alpha);
   }

   void renderBand(PoseStack pose, float alpha, int tint) {
      if (!this.bandMesh.isBuilt() || tint != this.bandTint) {
         this.bandTint = tint;
         this.buildBand(tint);
      }

      this.bandMesh.draw(pose, alpha);
   }

   void renderNebulae(PoseStack pose, float alpha) {
      renderNebulae(pose,alpha,0x956BCC);
   }
   void renderNebulae(PoseStack pose,float alpha,int tint){
      if(alpha<=.002F)return;
      if (!this.nebulaMesh.isBuilt()||this.nebulaTint!=tint) {
         this.nebulaTint=tint;
         this.buildNebulae();
      }

      this.nebulaMesh.draw(pose, alpha);
   }

   private void place() {
      RandomSource random = RandomSource.create(10842L);
      Vector3f a = new Vector3f();
      Vector3f b = new Vector3f();
      bandAxes(a, b);

      for (int i = 0; i < 2600; i++) {
         float x;
         float y;
         float z;
         if (random.nextFloat() < 0.3F) {
            float along = random.nextFloat() * (float) (Math.PI * 2);
            float across = (float)random.nextGaussian() * 9.0F * (float) (Math.PI / 180.0);
            float cosAcross = Mth.cos(across);
            float sinAcross = Mth.sin(across);
            x = cosAcross * (Mth.cos(along) * a.x + Mth.sin(along) * b.x) + sinAcross * BAND_NORMAL.x;
            y = cosAcross * (Mth.cos(along) * a.y + Mth.sin(along) * b.y) + sinAcross * BAND_NORMAL.y;
            z = cosAcross * (Mth.cos(along) * a.z + Mth.sin(along) * b.z) + sinAcross * BAND_NORMAL.z;
         } else {
            y = random.nextFloat() * 2.0F - 1.0F;
            float around = random.nextFloat() * (float) (Math.PI * 2);
            float flat = Mth.sqrt(1.0F - y * y);
            x = flat * Mth.cos(around);
            z = flat * Mth.sin(around);
         }

         this.dir[i * 3] = x;
         this.dir[i * 3 + 1] = y;
         this.dir[i * 3 + 2] = z;
         float big = (float)Math.pow(random.nextFloat(), 6.0);
         this.size[i] = 0.1F + 0.5F * big;
         this.light[i] = Mth.clamp(0.3F + 0.7F * big + 0.25F * random.nextFloat(), 0.0F, 1.0F);
         this.roll[i] = random.nextFloat() * (float) (Math.PI * 2);
         this.phase[i] = random.nextFloat() * (float) (Math.PI * 2);
         this.speed[i] = 1.5F + 3.5F * random.nextFloat();
         this.rgb[i] = starColour(random.nextFloat());
      }

      this.placed = true;
   }

   private static int starColour(float t) {
      if (t < 0.18F) {
         return ARGB.srgbLerp(t / 0.18F, 16757870, 16774116);
      } else {
         return t < 0.75F ? 16775408 : ARGB.srgbLerp((t - 0.75F) / 0.25F, 16774134, 11125503);
      }
   }

   private void buildStars(float time, float brightness) {
      if (!this.placed) {
         this.place();
      }

      BufferBuilder builder = this.starMesh.begin();

      for (int i = 0; i < 2600; i++) {
         float twinkle = 0.62F + 0.38F * Mth.sin(time * this.speed[i] + this.phase[i]);
         float alpha = Mth.clamp(this.light[i] * twinkle * brightness, 0.0F, 1.0F);
         this.star(builder, i, this.size[i], alpha);
         if (this.light[i] > 0.8F) {
            this.star(builder, i, this.size[i] * 2.3F, alpha * 0.035F);
         }
      }

      this.starMesh.upload(builder);
   }

   private void star(BufferBuilder builder, int i, float half, float alpha) {
      float x = this.dir[i * 3];
      float y = this.dir[i * 3 + 1];
      float z = this.dir[i * 3 + 2];
      float ux;
      float uy;
      float uz;
      if (Math.abs(y) < 0.9F) {
         ux = z;
         uy = 0.0F;
         uz = -x;
      } else {
         ux = 0.0F;
         uy = -z;
         uz = y;
      }

      float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
      ux /= ul;
      uy /= ul;
      uz /= ul;
      float vx = y * uz - z * uy;
      float vy = z * ux - x * uz;
      float vz = x * uy - y * ux;
      float c = Mth.cos(this.roll[i]) * half;
      float s = Mth.sin(this.roll[i]) * half;
      float ax = ux * c + vx * s;
      float ay = uy * c + vy * s;
      float az = uz * c + vz * s;
      float bx = vx * c - ux * s;
      float by = vy * c - uy * s;
      float bz = vz * c - uz * s;
      float cx = x * 100.0F;
      float cy = y * 100.0F;
      float cz = z * 100.0F;
      int argb = Math.round(alpha * 255.0F) << 24 | this.rgb[i];
      SkyMesh.vertex(builder, cx - ax - bx, cy - ay - by, cz - az - bz, argb);
      SkyMesh.vertex(builder, cx + ax - bx, cy + ay - by, cz + az - bz, argb);
      SkyMesh.vertex(builder, cx + ax + bx, cy + ay + by, cz + az + bz, argb);
      SkyMesh.vertex(builder, cx - ax + bx, cy - ay + by, cz - az + bz, argb);
   }

   private static void bandAxes(Vector3f a, Vector3f b) {
      new Vector3f(0.0F, 1.0F, 0.0F).cross(BAND_NORMAL, a).normalize();
      new Vector3f(BAND_NORMAL).cross(a, b).normalize();
   }

   private void buildBand(int tint) {
      Vector3f a = new Vector3f();
      Vector3f b = new Vector3f();
      bandAxes(a, b);
      float[][] px = new float[181][13];
      float[][] py = new float[181][13];
      float[][] pz = new float[181][13];
      int[][] colour = new int[181][13];

      for (int c = 0; c <= 180; c++) {
         float along = c / 180.0F * (float) (Math.PI * 2);
         float ax = Mth.cos(along) * a.x + Mth.sin(along) * b.x;
         float ay = Mth.cos(along) * a.y + Mth.sin(along) * b.y;
         float az = Mth.cos(along) * a.z + Mth.sin(along) * b.z;
         float width = 0.55F + 0.45F * fbm(ax * 1.7F, ay * 1.7F, az * 1.7F);
         float rift = 3.0F * (fbm(ax * 2.3F + 5.0F, ay * 2.3F, az * 2.3F) - 0.5F);
         float riftDepth = Mth.clamp(fbm(ax * 1.4F, ay * 1.4F + 9.0F, az * 1.4F) * 1.6F - 0.35F, 0.0F, 1.0F);

         for (int r = 0; r <= 12; r++) {
            float acrossDeg = (r / 12.0F * 2.0F - 1.0F) * 17.0F;
            float across = acrossDeg * (float) (Math.PI / 180.0);
            float cosAcross = Mth.cos(across);
            float sinAcross = Mth.sin(across);
            float x = cosAcross * ax + sinAcross * BAND_NORMAL.x;
            float y = cosAcross * ay + sinAcross * BAND_NORMAL.y;
            float z = cosAcross * az + sinAcross * BAND_NORMAL.z;
            px[c][r] = x * 100.0F;
            py[c][r] = y * 100.0F;
            pz[c][r] = z * 100.0F;
            float edge = acrossDeg / (17.0F * width);
            float core = (float)Math.exp(-edge * edge * 2.2F);
            float dust = 0.35F + 0.65F * fbm(x * 6.0F, y * 6.0F, z * 6.0F);
            float lane = 1.0F - 0.75F * riftDepth * (float)Math.exp(-sq((acrossDeg - rift) / 2.6F));
            float glow = Mth.clamp(core * dust * lane, 0.0F, 1.0F);
            int rgb = ARGB.srgbLerp(core, 7306440, tint) & 16777215;
            colour[c][r] = Math.round(glow * 0.42F * 255.0F) << 24 | rgb;
         }
      }

      BufferBuilder builder = this.bandMesh.begin();

      for (int c = 0; c < 180; c++) {
         for (int r = 0; r < 12; r++) {
            SkyMesh.vertex(builder, px[c][r], py[c][r], pz[c][r], colour[c][r]);
            SkyMesh.vertex(builder, px[c + 1][r], py[c + 1][r], pz[c + 1][r], colour[c + 1][r]);
            SkyMesh.vertex(builder, px[c + 1][r + 1], py[c + 1][r + 1], pz[c + 1][r + 1], colour[c + 1][r + 1]);
            SkyMesh.vertex(builder, px[c][r + 1], py[c][r + 1], pz[c][r + 1], colour[c][r + 1]);
         }
      }

      this.bandMesh.upload(builder);
   }

   private void buildNebulae() {
      float[][] px = new float[73][37];
      float[][] py = new float[73][37];
      float[][] pz = new float[73][37];
      int[][] colour = new int[73][37];

      for (int c = 0; c <= 72; c++) {
         float around = c / 72.0F * (float) (Math.PI * 2);

         for (int r = 0; r <= 36; r++) {
            float el = (r / 36.0F - 0.5F) * (float) Math.PI;
            float x = Mth.cos(el) * Mth.cos(around);
            float y = Mth.sin(el);
            float z = Mth.cos(el) * Mth.sin(around);
            px[c][r] = x * 100.0F;
            py[c][r] = y * 100.0F;
            pz[c][r] = z * 100.0F;
            float density = AtmosphereAurora.smoothstep((fbm(x * 2.2F, y * 2.2F, z * 2.2F) - 0.42F) / 0.35F);
            float hue = fbm(x * 1.3F + 11.0F, y * 1.3F, z * 1.3F);
            int rgb = ARGB.srgbLerp(hue*.6F,this.nebulaTint,0xCEDFFF);
            colour[c][r] = Math.round(density * 0.55F * 255.0F) << 24 | rgb & 16777215;
         }
      }

      BufferBuilder builder = this.nebulaMesh.begin();

      for (int c = 0; c < 72; c++) {
         for (int r = 0; r < 36; r++) {
            SkyMesh.vertex(builder, px[c][r], py[c][r], pz[c][r], colour[c][r]);
            SkyMesh.vertex(builder, px[c + 1][r], py[c + 1][r], pz[c + 1][r], colour[c + 1][r]);
            SkyMesh.vertex(builder, px[c + 1][r + 1], py[c + 1][r + 1], pz[c + 1][r + 1], colour[c + 1][r + 1]);
            SkyMesh.vertex(builder, px[c][r + 1], py[c][r + 1], pz[c][r + 1], colour[c][r + 1]);
         }
      }

      this.nebulaMesh.upload(builder);
   }

   static float fbm(float x, float y, float z) {
      float sum = 0.0F;
      float amplitude = 0.5F;
      float total = 0.0F;

      for (int octave = 0; octave < 4; octave++) {
         sum += amplitude * noise(x, y, z);
         total += amplitude;
         x *= 2.03F;
         y *= 2.03F;
         z *= 2.03F;
         amplitude *= 0.5F;
      }

      return sum / total;
   }

   private static float noise(float x, float y, float z) {
      int ix = Mth.floor(x);
      int iy = Mth.floor(y);
      int iz = Mth.floor(z);
      float fx = x - ix;
      float fy = y - iy;
      float fz = z - iz;
      fx = fx * fx * (3.0F - 2.0F * fx);
      fy = fy * fy * (3.0F - 2.0F * fy);
      fz = fz * fz * (3.0F - 2.0F * fz);
      float x00 = Mth.lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz));
      float x10 = Mth.lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
      float x01 = Mth.lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1));
      float x11 = Mth.lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
      return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
   }

   private static float hash(int x, int y, int z) {
      int h = x * 374761393 + y * 668265263 + z * 1274126177;
      h = (h ^ h >>> 13) * 1274126177;
      return ((h ^ h >>> 16) & 16777215) / 1.6777215E7F;
   }

   private static float sq(float v) {
      return v * v;
   }

   void close() {
      this.starMesh.close();
      this.bandMesh.close();
      this.nebulaMesh.close();
      this.builtBrightness = -1.0F;
   }
}
