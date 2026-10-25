package net.spectre.client.module.impl.render;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.client.PerformanceModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.glyph.GlyphCanvas;
import net.spectre.client.util.glyph.GlyphShapes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class FloatingRelicsModule extends ClientModule {
   private static final String SYMBOL_MIXED = "Mixed";
   private static final String SYMBOL_ANKH = "Ghost";
   private static final String SYMBOL_EYE = "Star";
   private static final String SYMBOL_SCARAB = "Crescent";
   private static final String SYMBOL_DJED = "Wisp";
   private static final String SYMBOL_FEATHER = "Crystal";
   private static final String SYMBOL_WAS = "Orbit";
   private static final String[] SYMBOL_NAMES = new String[]{"Ghost", "Star", "Crescent", "Wisp", "Crystal", "Orbit"};
   private static final float[][] SYMBOL_GLYPHS = new float[][]{
      net.spectre.client.util.glyph.SpectralShapes.GHOST, net.spectre.client.util.glyph.SpectralShapes.STAR, net.spectre.client.util.glyph.SpectralShapes.MOON, net.spectre.client.util.glyph.SpectralShapes.WISP, net.spectre.client.util.glyph.SpectralShapes.CRYSTAL, net.spectre.client.util.glyph.SpectralShapes.ORBIT
   };
   private static final float[] SYMBOL_FIT = new float[]{1.0F, 0.84F, 0.94F, 1.0F, 1.0F, 1.0F};
   private static final String COLOR_THEME = "Theme";
   private static final String COLOR_MANUAL = "Manual";
   private static final int MAX_RELICS = 32;
   private static final float GRAVITY = 0.016F;
   private static final float BUOYANCY = 0.045F;
   private static final float AIR_DRAG = 0.982F;
   private static final float GROUND_GRIP = 0.86F;
   private static final float ORBIT_PULL = 0.012F;
   private static final float SWIRL = 0.0025F;
   private static final float FOLLOW = 0.035F;
   private static final float WANDER = 0.003F;
   private static final float MAX_SPEED = 0.9F;
   private static final float SPIN_SETTLE = 0.03F;
   private static final float ANGLE_WRAP = 512.0F;
   private static final float TILT_SPRING = 0.09F;
   private static final float TILT_DAMP = 0.9F;
   private static final float MAX_TILT = 0.55F;
   private static final int APPEAR_TICKS = 12;
   private static final int STAGGER_TICKS = 3;
   private static final float GLYPH_SCALE = 1.25F;
   private static final float THICKNESS = 0.07F;
   private static final float SWAY = 0.12F;
   private static final float BOB = 0.2F;
   private static final float FACE_ALPHA = 0.94F;
   private static final float BACK_ALPHA = 0.9F;
   private static final float GLINT = 0.45F;
   private static final float[] HALO_SCALES = new float[]{1.35F, 2.0F};
   private static final float[] HALO_WEIGHTS = new float[]{1.0F, 0.5F};
   private static final float HALO_PUSH = 0.6F;
   private static final double DETAIL_RANGE_SQR = 576.0;
   private static final float LIGHT_X;
   private static final float LIGHT_Y;
   private static final float LIGHT_Z;
   private static final long EPOCH = System.nanoTime();
   private final ModeSetting symbols = this.addSetting(
      "symbols", new ModeSetting("Symbols", "Mixed", "Mixed", "Ghost", "Star", "Crescent", "Wisp", "Crystal", "Orbit")
   );
   private final NumberSetting count = this.addSetting("count", new NumberSetting("Count", 9.0F, 1.0F, 32.0F).step(1.0F));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 0.3F, 0.15F, 0.75F).step(0.05F));
   private final NumberSetting spread = this.addSetting("spread", new NumberSetting("Spread", 2.6F, 1.2F, 6.0F).step(0.1F));
   private final NumberSetting bounce = this.addSetting("bounce", new NumberSetting("Bounce", 0.6F, 0.1F, 0.95F).step(0.05F));
   private final NumberSetting drift = this.addSetting("drift", new NumberSetting("Drift", 0.5F, 0.0F, 2.5F).step(0.1F));
   private final NumberSetting spinSpeed = this.addSetting("spin", new NumberSetting("Spin", 1.0F, 0.2F, 3.0F).step(0.1F));
   private final ModeSetting colorMode = this.addSetting("color", new ModeSetting("Color", "Theme", "Theme", "Manual"));
   private final ColorSetting mainColor = this.addSetting("main-color", new ColorSetting("Main Color", -12614409));
   private final ColorSetting secondColor = this.addSetting("second-color", new ColorSetting("Second Color", -14923108));
   private final BooleanSetting halo = this.addSetting("halo", new BooleanSetting("Halo", true));
   private final BooleanSetting blocks = this.addSetting("block-bounce", new BooleanSetting("Block Bounce", true));
   private final FloatingRelicsModule.Relic[] relics = new FloatingRelicsModule.Relic[32];
   private final GlyphCanvas canvas = new GlyphCanvas();
   private final RandomSource random = RandomSource.create();
   private final MutableBlockPos cursor = new MutableBlockPos();
   private int live;
   private int age;
   private ClientLevel boundLevel;
   private int tintMain;
   private int tintShade;
   private int tintShine;

   public FloatingRelicsModule() {
      super("floating-relics", "Spectral Wisps", "Ghosts, stars, moons and wisps drift around your player.", ModuleCategory.VISUALS);
      this.mainColor.visibleWhen(() -> this.colorMode.is("Manual"));
      this.secondColor.visibleWhen(() -> this.colorMode.is("Manual"));

      for (int i = 0; i < 32; i++) {
         this.relics[i] = new FloatingRelicsModule.Relic();
      }
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      this.on(Render3DEvent.TYPE, this::onRender3D);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.live = 0;
      this.age = 0;
      this.boundLevel = null;
   }

   private void syncLevel(ClientLevel level) {
      if (level != this.boundLevel) {
         this.boundLevel = level;
         this.live = 0;
      }
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      if (player != null && level != null) {
         this.syncLevel(level);
         this.age++;
         int want = Math.min(32, this.count.getInt());

         for (int i = this.live; i < want; i++) {
            this.wake(this.relics[i], (i - this.live) * 3);
         }

         this.live = want;
         double px = player.getX();
         double py = player.getY();
         double pz = player.getZ();
         float height = player.getBbHeight();
         float moveX = (float)(px - player.xo);
         float moveZ = (float)(pz - player.zo);
         float restitution = this.bounce.get();

         for (int i = 0; i < this.live; i++) {
            this.step(this.relics[i], level, px, py, pz, height, moveX, moveZ, restitution);
         }

         for (int i = 0; i < this.live; i++) {
            for (int j = i + 1; j < this.live; j++) {
               this.knock(this.relics[i], this.relics[j], restitution);
            }
         }
      } else {
         this.reset();
      }
   }

   private void wake(FloatingRelicsModule.Relic r, int delay) {
      r.placed = false;
      r.wakeAt = this.age + delay;
      r.orbit = 0.55F + 0.45F * this.random.nextFloat();
      r.lift = this.random.nextFloat();
      r.grain = 0.82F + 0.3F * this.random.nextFloat();
      r.phase = this.random.nextFloat() * (float) (Math.PI * 2);
      r.restTurn = this.signed(0.07F, 0.12F);
   }

   private float signed(float min, float max) {
      float value = min + (max - min) * this.random.nextFloat();
      return this.random.nextBoolean() ? value : -value;
   }

   private void place(FloatingRelicsModule.Relic r, double px, double py, double pz) {
      float angle = this.random.nextFloat() * (float) (Math.PI * 2);
      double radius = this.spread.get() * r.orbit;
      r.x = px + Mth.cos(angle) * radius;
      r.y = py + 0.15;
      r.z = pz + Mth.sin(angle) * radius;
      r.lastX = r.x;
      r.lastY = r.y;
      r.lastZ = r.z;
      r.vx = -Mth.sin(angle) * 0.03F;
      r.vy = 0.14F + 0.08F * this.random.nextFloat();
      r.vz = Mth.cos(angle) * 0.03F;
      r.yaw = this.random.nextFloat() * (float) (Math.PI * 2);
      r.lastYaw = r.yaw;
      r.turn = r.restTurn * 3.0F;
      r.tiltX = 0.0F;
      r.tiltZ = 0.0F;
      r.lastTiltX = 0.0F;
      r.lastTiltZ = 0.0F;
      r.swayX = this.signed(0.02F, 0.05F);
      r.swayZ = this.signed(0.02F, 0.05F);
      r.placed = true;
      r.placedAt = this.age;
   }

   private void step(
      FloatingRelicsModule.Relic r, ClientLevel level, double px, double py, double pz, float height, float moveX, float moveZ, float restitution
   ) {
      if (!r.placed) {
         if (this.age < r.wakeAt) {
            return;
         }

         this.place(r, px, py, pz);
      }

      r.lastX = r.x;
      r.lastY = r.y;
      r.lastZ = r.z;
      double dx = r.x - px;
      double dz = r.z - pz;
      double flat = Math.sqrt(dx * dx + dz * dz);
      double hover = py + 0.35 + r.lift * (height + 0.2) + 0.16 * Mth.sin((this.age + r.phase * 16.0F) * 0.045F);
      double leash = this.spread.get() * 3.0 + 8.0;
      if (!(flat > leash) && !(Math.abs(r.y - hover) > leash)) {
         float nx = flat > 1.0E-4 ? (float)(dx / flat) : 1.0F;
         float nz = flat > 1.0E-4 ? (float)(dz / flat) : 0.0F;
         float pull = (float)(this.spread.get() * r.orbit - flat) * 0.012F;
         float swirl = this.drift.get() * 0.0025F;
         r.vx += nx * pull - nz * swirl + moveX * 0.035F;
         r.vz += nz * pull + nx * swirl + moveZ * 0.035F;
         float wander = (this.age + r.phase * 20.0F) * 0.06F;
         r.vx = r.vx + Mth.sin(wander) * 0.003F;
         r.vz = r.vz + Mth.cos(wander * 1.31F) * 0.003F;
         r.vy -= 0.016F;
         double sink = hover - r.y;
         if (sink > 0.0) {
            r.vy = r.vy + (float)Math.min(sink, 1.5) * 0.045F;
         }

         r.vx *= 0.982F;
         r.vy *= 0.982F;
         r.vz *= 0.982F;
         float speed = r.vx * r.vx + r.vy * r.vy + r.vz * r.vz;
         if (speed > 0.80999994F) {
            float k = 0.9F / (float)Math.sqrt(speed);
            r.vx *= k;
            r.vy *= k;
            r.vz *= k;
         }

         boolean collide = this.blocks.get();
         float half = this.size.get() * r.grain * 0.5F;
         double next = r.x + r.vx;
         if (collide && this.solid(level, next + Math.copySign(half, r.vx), r.y, r.z)) {
            r.vx = -r.vx * restitution;
            this.tumble(r, 0.6F);
         } else {
            r.x = next;
         }

         next = r.y + r.vy;
         if (collide && this.solid(level, r.x, next + Math.copySign(half, r.vy), r.z)) {
            if (r.vy < 0.0F) {
               r.vx *= 0.86F;
               r.vz *= 0.86F;
            }

            r.vy = -r.vy * restitution;
            this.tumble(r, 0.8F);
         } else {
            r.y = next;
         }

         next = r.z + r.vz;
         if (collide && this.solid(level, r.x, r.y, next + Math.copySign(half, r.vz))) {
            r.vz = -r.vz * restitution;
            this.tumble(r, 0.6F);
         } else {
            r.z = next;
         }

         if (collide && this.solid(level, r.x, r.y, r.z)) {
            r.y += 0.2;
            r.lastY = r.y;
            r.vy = Math.max(r.vy, 0.08F);
         }

         if (Math.abs(r.yaw) > 512.0F) {
            r.yaw %= (float) (Math.PI * 2);
         }

         r.lastYaw = r.yaw;
         r.lastTiltX = r.tiltX;
         r.lastTiltZ = r.tiltZ;
         r.turn = r.turn + (r.restTurn * this.spinSpeed.get() - r.turn) * 0.03F;
         r.yaw = r.yaw + r.turn;
         r.swayX = (r.swayX - r.tiltX * 0.09F) * 0.9F;
         r.swayZ = (r.swayZ - r.tiltZ * 0.09F) * 0.9F;
         r.tiltX = Mth.clamp(r.tiltX + r.swayX, -0.55F, 0.55F);
         r.tiltZ = Mth.clamp(r.tiltZ + r.swayZ, -0.55F, 0.55F);
      } else {
         this.place(r, px, py, pz);
      }
   }

   private void knock(FloatingRelicsModule.Relic a, FloatingRelicsModule.Relic b, float restitution) {
      if (a.placed && b.placed) {
         double dx = b.x - a.x;
         double dy = b.y - a.y;
         double dz = b.z - a.z;
         double reach = this.size.get() * (a.grain + b.grain) * 0.5 * 0.95;
         double d2 = dx * dx + dy * dy + dz * dz;
         if (!(d2 >= reach * reach) && !(d2 < 1.0E-8)) {
            double d = Math.sqrt(d2);
            float nx = (float)(dx / d);
            float ny = (float)(dy / d);
            float nz = (float)(dz / d);
            double push = (reach - d) * 0.5;
            a.x -= nx * push;
            a.y -= ny * push;
            a.z -= nz * push;
            b.x += nx * push;
            b.y += ny * push;
            b.z += nz * push;
            float closing = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny + (a.vz - b.vz) * nz;
            if (!(closing <= 0.0F)) {
               float impulse = closing * (1.0F + restitution) * 0.5F;
               a.vx -= impulse * nx;
               a.vy -= impulse * ny;
               a.vz -= impulse * nz;
               b.vx += impulse * nx;
               b.vy += impulse * ny;
               b.vz += impulse * nz;
               this.tumble(a, 0.5F);
               this.tumble(b, 0.5F);
            }
         }
      }
   }

   private void tumble(FloatingRelicsModule.Relic r, float strength) {
      r.turn = r.turn + (this.random.nextFloat() - 0.5F) * 0.3F * strength;
      r.swayX = r.swayX + (this.random.nextFloat() - 0.5F) * 0.12F * strength;
      r.swayZ = r.swayZ + (this.random.nextFloat() - 0.5F) * 0.12F * strength;
   }

   private boolean solid(ClientLevel level, double x, double y, double z) {
      this.cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
      BlockState state = level.getBlockState(this.cursor);
      if (state.isAir()) {
         return false;
      } else {
         VoxelShape shape = state.getCollisionShape(level, this.cursor);
         if (shape.isEmpty()) {
            return false;
         } else {
            double lx = x - this.cursor.getX();
            double ly = y - this.cursor.getY();
            double lz = z - this.cursor.getZ();
            return lx >= shape.min(Axis.X)
               && lx <= shape.max(Axis.X)
               && ly >= shape.min(Axis.Y)
               && ly <= shape.max(Axis.Y)
               && lz >= shape.min(Axis.Z)
               && lz <= shape.max(Axis.Z);
         }
      }
   }

   private void onRender3D(Render3DEvent event) {
      if (this.live != 0) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            this.canvas.begin(mc.gameRenderer.mainCamera());
            float pt = event.partialTick;
            float time = (float)(System.nanoTime() - EPOCH) / 1.0E9F;
            Frustum frustum = PerformanceModule.frustumCulling() ? event.frustum : null;
            float base = this.size.get() * 0.5F * 1.25F;
            int pick = this.symbolIndex();
            boolean glow = this.halo.get();
            this.tint();

            for (int i = 0; i < this.live; i++) {
               FloatingRelicsModule.Relic r = this.relics[i];
               if (r.placed) {
                  int kind = pick < 0 ? i % SYMBOL_GLYPHS.length : pick;
                  float since = this.age - r.placedAt + pt;
                  float extent = base * r.grain * SYMBOL_FIT[kind] * appear(since / 12.0F);
                  if (!(extent < 0.004F)) {
                     double x = Mth.lerp((double)pt, r.lastX, r.x);
                     double y = Mth.lerp((double)pt, r.lastY, r.y) + extent * 0.2F * Mth.sin(time * 2.4F + r.phase * 3.0F);
                     double z = Mth.lerp((double)pt, r.lastZ, r.z);
                     double reach = extent * 2.0;
                     if (frustum == null || frustum.isVisible(new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach))) {
                        float fade = Math.min(1.0F, since / 7.2000003F);
                        float yaw = Mth.lerp(pt, r.lastYaw, r.yaw);
                        float tiltX = Mth.lerp(pt, r.lastTiltX, r.tiltX) + 0.12F * Mth.sin(time * 1.3F + r.phase);
                        float tiltZ = Mth.lerp(pt, r.lastTiltZ, r.tiltZ) + 0.12F * Mth.sin(time * 0.9F + r.phase * 2.3F);
                        float shimmer = glow ? (0.1F + 0.07F * Mth.sin(time * 2.1F + r.phase)) * fade : 0.0F;
                        this.drawRelic(SYMBOL_GLYPHS[kind], x, y, z, extent, yaw, tiltX, tiltZ, fade, shimmer);
                     }
                  }
               }
            }

            this.canvas.submit(false);
         }
      }
   }

   private int symbolIndex() {
      String chosen = this.symbols.get();

      for (int k = 0; k < SYMBOL_NAMES.length; k++) {
         if (SYMBOL_NAMES[k].equals(chosen)) {
            return k;
         }
      }

      return -1;
   }

   private void tint() {
      if (this.colorMode.is("Manual")) {
         this.tintMain = ARGB.opaque(this.mainColor.argb());
         this.tintShade = ARGB.opaque(this.secondColor.argb());
      } else {
         this.tintMain = CosmeticAccent.argb();
         this.tintShade = ARGB.scaleRGB(this.tintMain, 0.45F);
      }

      this.tintShine = ARGB.srgbLerp(0.6F, this.tintMain, -1);
   }

   private void drawRelic(float[] glyph, double x, double y, double z, float extent, float yaw, float tiltX, float tiltZ, float fade, float shimmer) {
      float cy = Mth.cos(yaw);
      float sy = Mth.sin(yaw);
      float cx = Mth.cos(tiltX);
      float sx = Mth.sin(tiltX);
      float cz = Mth.cos(tiltZ);
      float sz = Mth.sin(tiltZ);
      float ux = cy * cz + sy * sx * sz;
      float uy = cx * sz;
      float uz = -sy * cz + cy * sx * sz;
      float vx = -cy * sz + sy * sx * cz;
      float vy = cx * cz;
      float vz = sy * sz + cy * sx * cz;
      float nx = sy * cx;
      float ny = -sx;
      float nz = cy * cx;
      double tx = this.canvas.camX() - x;
      double ty = this.canvas.camY() - y;
      double tz = this.canvas.camZ() - z;
      double distSqr = tx * tx + ty * ty + tz * tz;
      if (!(distSqr < 1.0E-8)) {
         double dist = Math.sqrt(distSqr);
         float facing = (float)((nx * tx + ny * ty + nz * tz) / dist);
         if (facing < 0.0F) {
            ux = -ux;
            uy = -uy;
            uz = -uz;
            nx = -nx;
            ny = -ny;
            nz = -nz;
            facing = -facing;
         }

         if (shimmer > 0.0F) {
            double push = extent * 0.6F / dist;
            double hx = x - tx * push;
            double hy = y - ty * push;
            double hz = z - tz * push;

            for (int layer = 0; layer < HALO_SCALES.length; layer++) {
               this.canvas.sprite(GlyphShapes.DISK, hx, hy, hz, extent * HALO_SCALES[layer], 0.0F, withAlpha(this.tintMain, shimmer * HALO_WEIGHTS[layer]));
            }
         }

         double au = ux * extent;
         double bu = uy * extent;
         double cu = uz * extent;
         double av = vx * extent;
         double bv = vy * extent;
         double cv = vz * extent;
         double depth = extent * 0.07F;
         if (distSqr < 576.0 && facing < 0.995F) {
            this.canvas.shape(glyph, x - nx * depth, y - ny * depth, z - nz * depth, au, bu, cu, av, bv, cv, withAlpha(this.tintShade, 0.9F * fade));
         }

         float light = Math.max(0.0F, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
         float glint = facing * facing;
         glint *= glint;
         glint *= glint;
         int face = ARGB.srgbLerp(glint * 0.45F, ARGB.scaleRGB(this.tintMain, 0.6F + 0.22F * light + 0.18F * facing), this.tintShine);
         this.canvas.shape(glyph, x + nx * depth, y + ny * depth, z + nz * depth, au, bu, cu, av, bv, cv, withAlpha(face, 0.94F * fade));
      }
   }

   private static float appear(float t) {
      if (t >= 1.0F) {
         return 1.0F;
      } else if (t <= 0.0F) {
         return 0.0F;
      } else {
         float inv = 1.0F - t;
         return 1.0F - inv * inv * inv + 0.22F * Mth.sin(t * (float) Math.PI) * inv;
      }
   }

   private static int withAlpha(int rgb, float alpha) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F + 0.5F);
      return a << 24 | rgb & 16777215;
   }

   static {
      float lx = 0.32F;
      float ly = 0.87F;
      float lz = 0.37F;
      float length = (float)Math.sqrt(lx * lx + ly * ly + lz * lz);
      LIGHT_X = lx / length;
      LIGHT_Y = ly / length;
      LIGHT_Z = lz / length;
   }

   @Environment(EnvType.CLIENT)
   private static final class Relic {
      double x;
      double y;
      double z;
      double lastX;
      double lastY;
      double lastZ;
      float vx;
      float vy;
      float vz;
      float yaw;
      float lastYaw;
      float turn;
      float restTurn;
      float tiltX;
      float tiltZ;
      float lastTiltX;
      float lastTiltZ;
      float swayX;
      float swayZ;
      float orbit;
      float lift;
      float grain;
      float phase;
      boolean placed;
      int wakeAt;
      int placedAt;
   }
}
