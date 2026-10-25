package net.spectre.client.module.impl.render;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.combat.CombatTarget;
import net.spectre.client.util.glyph.GlyphCanvas;
import net.spectre.client.util.glyph.GlyphShapes;
import net.spectre.client.util.glyph.GlyphTint;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class TargetEspModule extends ClientModule {
   private static final String STYLE_ANKH = "Ankh Circle";
   private static final String STYLE_HORUS = "Horus Halo";
   private static final String STYLE_VORTEX = "Sand Vortex";
   private static final String STYLE_CAPSTONE = "Capstone";
   private static final float APPEAR_SECONDS = 0.3F;
   private static final float VANISH_SECONDS = 0.38F;
   private static final float HURT_TICKS = 10.0F;
   private static final double MAX_DISTANCE_SQR = 16384.0;
   private static final int GLYPH_MARKS = 6;
   private static final int CARTOUCHE_DASHES = 12;
   private static final int VORTEX_GRAINS = 40;
   private static final int VORTEX_POSTS = 3;
   private static final float VORTEX_FALL = 0.48F;
   private static final float VORTEX_SPIN = 0.8F;
   private static final float VORTEX_SWIRL = 3.1F;
   private static final float VORTEX_RIPPLE = 0.96F;
   private static final float[] GRAIN_SEEDS = scatter(120, 1515036955L);
   private static final double BLINK_PERIOD = 4.6;
   private static final double BLINK_LENGTH = 0.22;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Ankh Circle", "Ankh Circle", "Horus Halo", "Sand Vortex", "Capstone"));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", "Theme", "Theme", "Manual"));
   private final ColorSetting mainColor = this.addSetting("main-color", new ColorSetting("Main Color", -12614409));
   private final ColorSetting secondColor = this.addSetting("second-color", new ColorSetting("Second Color", -15189606));
   private final ColorSetting hitColor = this.addSetting("hit-color", new ColorSetting("Hit Color", -2233345));
   private final NumberSetting scale = this.addSetting("scale", new NumberSetting("Scale", 1.0F, 0.6F, 1.8F).step(0.1F));
   private final NumberSetting tempo = this.addSetting("tempo", new NumberSetting("Tempo", 1.0F, 0.3F, 2.4F).step(0.1F));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 0.9F, 0.2F, 1.0F).step(0.05F));
   private final NumberSetting memory = this.addSetting("memory", new NumberSetting("Memory", 4.0F, 0.5F, 15.0F).step(0.5F));
   private final BooleanSetting crosshair = this.addSetting("crosshair", new BooleanSetting("Crosshair Pick", true));
   private final BooleanSetting playersOnly = this.addSetting("players-only", new BooleanSetting("Players Only", false));
   private final BooleanSetting hurtFlash = this.addSetting("hurt-flash", new BooleanSetting("Hit Flash", true));
   private final BooleanSetting throughWalls = this.addSetting("through-walls", new BooleanSetting("Through Walls", false));
   private final GlyphCanvas canvas = new GlyphCanvas();
   private final GlyphTint palette = new GlyphTint();
   private final double[] capX = new double[4];
   private final double[] capZ = new double[4];
   private TargetEspModule.Mark current = new TargetEspModule.Mark();
   private TargetEspModule.Mark previous = new TargetEspModule.Mark();
   private ClientLevel boundLevel;
   private long lastFrame;
   private double phase;
   private int gold;
   private int lapis;
   private int light;
   private int hit;

   public TargetEspModule() {
      super("target-esp", "Target ESP", "Draws an Egyptian mark on your combat target.", ModuleCategory.VISUALS);
      this.mainColor.visibleWhen(() -> this.colorMode.is("Manual"));
      this.secondColor.visibleWhen(() -> this.colorMode.is("Manual"));
      this.hitColor.visibleWhen(() -> this.colorMode.is("Manual") && this.hurtFlash.get());
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      this.on(Render3DEvent.TYPE, this::onRender);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.current.assign(null);
      this.previous.assign(null);
      this.boundLevel = null;
      this.lastFrame = 0L;
   }

   private void onTick(TickEvent event) {
      if (this.boundLevel != null && Minecraft.getInstance().level != this.boundLevel) {
         this.reset();
      }
   }

   private void onRender(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != null && mc.player != null) {
         if (level != this.boundLevel) {
            this.reset();
            this.boundLevel = level;
         }

         long now = System.nanoTime();
         float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
         this.lastFrame = now;
         this.phase = this.phase + dt * this.tempo.get();
         this.retarget(this.pick(mc), event.partialTick, level);
         this.current.presence = this.current.entity != null ? Math.min(1.0F, this.current.presence + dt / 0.3F) : 0.0F;
         this.previous.presence = Math.max(0.0F, this.previous.presence - dt / 0.38F);
         if (this.previous.presence <= 0.0F) {
            this.previous.entity = null;
         }

         this.current.follow(event.partialTick, level);
         this.previous.follow(event.partialTick, level);
         if (this.current.entity != null || this.previous.entity != null) {
            this.palette.resolve(this.colorMode.get(), CosmeticAccent.argb(), this.mainColor.argb(), this.secondColor.argb());
            this.hit = this.colorMode.is("Manual") ? this.hitColor.argb() : this.palette.flash();
            this.canvas.begin(mc.gameRenderer.mainCamera());
            if (this.previous.entity != null) {
               this.draw(this.previous, true);
            }

            if (this.current.entity != null) {
               this.draw(this.current, false);
            }

            this.canvas.submit(this.throughWalls.get());
         }
      } else {
         if (this.boundLevel != null) {
            this.reset();
         }
      }
   }

   private void retarget(LivingEntity wanted, float partialTick, ClientLevel level) {
      if (wanted != this.current.entity) {
         if (wanted != null && wanted == this.previous.entity) {
            TargetEspModule.Mark swap = this.current;
            this.current = this.previous;
            this.previous = swap;
         } else {
            if (this.current.entity != null && this.current.presence > 0.02F) {
               TargetEspModule.Mark swap = this.previous;
               this.previous = this.current;
               this.current = swap;
            }

            this.current.assign(wanted);
            this.current.follow(partialTick, level);
         }
      }
   }

   private LivingEntity pick(Minecraft mc) {
      long window = (long)(this.memory.get() * 1000.0F);
      LivingEntity target = this.crosshair.get() ? CombatTarget.current(window) : CombatTarget.lastHit(window);
      if (target == null || target == mc.player || !target.isAlive() || target.isSpectator()) {
         return null;
      } else if (target instanceof ArmorStand) {
         return null;
      } else {
         return this.playersOnly.get() && !(target instanceof Player) ? null : target;
      }
   }

   private void draw(TargetEspModule.Mark mark, boolean leaving) {
      if (!(this.canvas.distanceSqr(mark.x, mark.y, mark.z) > 16384.0)) {
         float p = mark.presence;
         float visible = leaving ? p * p : 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
         float grow = leaving ? 1.0F + 0.3F * (1.0F - p) : 0.55F + 0.45F * unfurl(p);
         float flash = this.hurtFlash.get() ? mark.hurt : 0.0F;
         float alpha = this.opacity.get() * visible;
         if (!(alpha < 0.01F)) {
            this.gold = GlyphTint.mix(this.palette.primary(), this.hit, flash * 0.8F);
            this.lapis = GlyphTint.mix(this.palette.secondary(), this.hit, flash * 0.45F);
            this.light = GlyphTint.mix(this.palette.highlight(), this.hit, flash * 0.6F);
            float size = this.scale.get() * grow * (1.0F + 0.1F * flash);
            float radius = (mark.width * 0.5F + 0.4F) * size;
            String var10 = this.style.get();
            switch (var10) {
               case "Horus Halo":
                  this.horusHalo(mark, size, alpha);
                  break;
               case "Sand Vortex":
                  this.sandVortex(mark, size, radius, alpha);
                  break;
               case "Capstone":
                  this.capstone(mark, size, alpha);
                  break;
               default:
                  this.ankhCircle(mark, size, radius, alpha);
            }
         }
      }
   }

   private void ankhCircle(TargetEspModule.Mark m, float size, float r, float a) {
      double gy = m.y + 0.02;
      this.canvas.ring(m.x, gy, m.z, r * 0.62F, r * 0.86F, 40, GlyphTint.alpha(this.lapis, a * 0.1F));
      this.canvas.ring(m.x, gy, m.z, r * 0.86F, r * 0.965F, 40, GlyphTint.alpha(this.gold, a * 0.22F));
      this.canvas.ring(m.x, gy, m.z, r * 0.965F, r * 1.035F, 48, GlyphTint.alpha(this.light, a * 0.9F));
      this.canvas.ring(m.x, gy, m.z, r * 1.035F, r * 1.14F, 48, GlyphTint.alpha(this.gold, a * 0.2F));
      float dash = (float) (Math.PI / 6);
      float drift = (float)(-this.phase * 0.7 % (float) (Math.PI * 2));

      for (int i = 0; i < 12; i++) {
         float from = drift + i * dash;
         this.canvas.band(m.x, gy, m.z, r * 0.74F, r * 0.8F, from, from + dash * 0.62F, 3, GlyphTint.alpha(this.lapis, a * 0.85F));
      }

      float orbit = (float)(this.phase * 0.9 % (float) (Math.PI * 2));
      float mark = 0.12F * size;

      for (int i = 0; i < 6; i++) {
         float angle = orbit + i * (float) (Math.PI * 2) / 6.0F;
         double px = m.x + Mth.cos(angle) * r;
         double pz = m.z + Mth.sin(angle) * r;
         double py = m.y + 0.2 * size + 0.045 * size * Mth.sin(this.phase * 2.6 + i * 1.3);
         boolean ankh = (i & 1) == 0;
         this.canvas.sprite(GlyphShapes.OCTAGON, px, py, pz, mark * 2.1F, 0.0F, GlyphTint.alpha(ankh ? this.gold : this.lapis, a * 0.14F));
         if (ankh) {
            this.canvas.sprite(GlyphShapes.GHOST, px, py, pz, mark, 0.0F, GlyphTint.alpha(this.gold, a));
         } else {
            this.canvas.sprite(GlyphShapes.CRESCENT, px, py, pz, mark * 0.9F, 0.0F, GlyphTint.alpha(this.lapis, a));
            this.canvas.sprite(GlyphShapes.DISK, px, py, pz, mark * 0.3F, mark * 0.3F, 0.0F, 0.0F, 4.2F, GlyphTint.alpha(this.light, a));
         }
      }
   }

   private void horusHalo(TargetEspModule.Mark m, float size, float a) {
      double top = m.y + m.height;
      float hr = (m.width * 0.5F + 0.2F) * size;
      double hy = top + 0.08;
      this.canvas.ring(m.x, hy, m.z, hr * 0.9F, hr, 32, GlyphTint.alpha(this.gold, a * 0.85F));
      this.canvas.ring(m.x, hy, m.z, hr, hr * 1.18F, 32, GlyphTint.alpha(this.gold, a * 0.18F));
      float spin = (float)(this.phase * 1.2 % (float) (Math.PI * 2));

      for (int i = 0; i < 8; i++) {
         float from = spin + i * (float) (Math.PI * 2) / 8.0F;
         this.canvas.band(m.x, hy, m.z, hr * 1.22F, hr * 1.44F, from, from + 0.13F, 1, GlyphTint.alpha(this.lapis, a * 0.8F));
      }

      float eye = 0.26F * size;
      double ey = top + 0.52 * size + 0.05 * size * Mth.sin(this.phase * 1.8);
      float lid = this.blink();
      this.canvas.sprite(GlyphShapes.OCTAGON, m.x, ey, m.z, eye * 1.95F, 0.0F, GlyphTint.alpha(this.lapis, a * 0.16F));
      this.canvas.sprite(GlyphShapes.DISK, m.x, ey, m.z, eye * 1.3F, 0.0F, GlyphTint.alpha(GlyphTint.mix(this.lapis, -16777216, 0.45F), a * 0.6F));
      this.canvas.sprite(GlyphShapes.HALO, m.x, ey, m.z, eye * 1.36F, 0.0F, GlyphTint.alpha(this.gold, a));
      this.canvas.sprite(GlyphShapes.EYE, m.x, ey, m.z, eye, eye * lid, 0.0F, GlyphTint.alpha(this.gold, a));
      this.canvas.sprite(GlyphShapes.EYE_PUPIL, m.x, ey, m.z, eye, eye * lid, 0.0F, GlyphTint.alpha(this.light, a));
   }

   private void sandVortex(TargetEspModule.Mark m, float size, float r, float a) {
      double base = m.y + 0.03;
      double top = m.y + m.height + 0.3 * size;
      double span = top - base;
      double waistY = m.y + m.height * 0.5;
      float waist = Mth.clamp((float)((waistY - base) / span), 0.15F, 0.85F);
      float neck = (m.width * 0.5F + 0.12F) * size;
      float fall = (float)(this.phase * 0.48F % 1.0);
      float turn = (float)(this.phase * 0.8F % (float) (Math.PI * 2));
      float tumble = (float)(this.phase * 4.0 % (float) (Math.PI * 2));
      this.canvas.ring(m.x, top, m.z, r * 0.9F, r * 0.98F, 40, GlyphTint.alpha(this.gold, a * 0.8F));
      this.canvas.ring(m.x, top, m.z, r * 0.98F, r * 1.1F, 40, GlyphTint.alpha(this.gold, a * 0.16F));

      for (int i = 0; i < 6; i++) {
         float from = -turn * 1.5F + i * (float) (Math.PI * 2) / 6.0F;
         this.canvas.band(m.x, top, m.z, r * 0.76F, r * 0.84F, from, from + 0.5F, 2, GlyphTint.alpha(this.lapis, a * 0.7F));
      }

      this.canvas.ring(m.x, waistY, m.z, neck * 0.9F, neck, 24, GlyphTint.alpha(this.light, a * 0.3F));
      this.canvas.ring(m.x, base, m.z, r * 0.92F, r, 40, GlyphTint.alpha(this.lapis, a * 0.6F));
      this.canvas.ring(m.x, base, m.z, 0.0F, r * 0.92F, 32, GlyphTint.alpha(this.gold, a * 0.08F));
      float wave = (float)(this.phase * 0.96F % 1.0);
      float swell = neck + (r * 1.12F - neck) * wave;
      this.canvas.ring(m.x, base + 0.005, m.z, swell * 0.93F, swell, 36, GlyphTint.alpha(this.gold, a * 0.55F * (1.0F - wave)));
      int post = GlyphTint.alpha(this.gold, a * 0.35F);

      for (int i = 0; i < 3; i++) {
         float angle = turn * 0.5F + i * (float) (Math.PI * 2) / 3.0F;
         double px = m.x + Mth.cos(angle) * r * 1.04F;
         double pz = m.z + Mth.sin(angle) * r * 1.04F;
         this.canvas.line(px, base, pz, px, top, pz, post, 1.4F);
      }

      for (int i = 0; i < 40; i++) {
         float lead = GRAIN_SEEDS[i * 3];
         float spread = GRAIN_SEEDS[i * 3 + 1];
         float grit = GRAIN_SEEDS[i * 3 + 2];
         float u = Mth.frac(fall + lead);
         float drop = u * (0.4F + 0.6F * u);
         float h = 1.0F - drop;
         float k = h >= waist ? (h - waist) / (1.0F - waist) : (waist - h) / waist;
         float bulge = k * (2.0F - k);
         float reach = neck + (r - neck) * bulge;
         float angle = spread * (float) (Math.PI * 2) + turn + drop * 3.1F;
         double px = m.x + Mth.cos(angle) * reach;
         double pz = m.z + Mth.sin(angle) * reach;
         double py = top - drop * span;
         float grainAlpha = a * Math.min(1.0F, u * 10.0F) * Math.min(1.0F, (1.0F - u) * 8.0F);
         float mote = 0.042F * size * (0.7F + 0.6F * grit);
         int color = GlyphTint.mix(this.light, this.gold, bulge);
         if (i % 5 == 0) {
            color = GlyphTint.mix(color, this.lapis, 0.6F);
         }

         if (k < 0.3F) {
            this.canvas.sprite(GlyphShapes.OCTAGON, px, py, pz, mote * 2.6F, 0.0F, GlyphTint.alpha(this.light, grainAlpha * 0.6F * (0.3F - k)));
         }

         this.canvas.sprite(GlyphShapes.GRAIN, px, py, pz, mote, tumble + grit * (float) (Math.PI * 2), GlyphTint.alpha(color, grainAlpha));
      }
   }

   private void capstone(TargetEspModule.Mark m, float size, float a) {
      double top = m.y + m.height;
      double apex = top + 0.28 * size + 0.06 * size * Mth.sin(this.phase * 2.1);
      float depth = 0.34F * size;
      float half = 0.17F * size * Mth.SQRT_OF_TWO;
      double rim = apex + depth;
      double core = apex + depth * 0.6;
      float spin = (float)(this.phase * 1.3 % (float) (Math.PI * 2));

      for (int k = 0; k < 4; k++) {
         float angle = spin + k * (float) (Math.PI / 2);
         this.capX[k] = m.x + Mth.cos(angle) * half;
         this.capZ[k] = m.z + Mth.sin(angle) * half;
      }

      this.canvas.sprite(GlyphShapes.OCTAGON, m.x, core, m.z, depth * 1.7F, 0.0F, GlyphTint.alpha(this.gold, a * 0.12F));

      for (int k = 0; k < 4; k++) {
         int n = k + 1 & 3;
         int face = (k & 1) == 0 ? this.gold : GlyphTint.mix(this.gold, this.lapis, 0.35F);
         this.canvas
            .outwardTriangle(this.capX[k], rim, this.capZ[k], this.capX[n], rim, this.capZ[n], m.x, apex, m.z, m.x, core, m.z, GlyphTint.alpha(face, a * 0.5F));
      }

      this.canvas
         .outwardTriangle(
            this.capX[0],
            rim,
            this.capZ[0],
            this.capX[1],
            rim,
            this.capZ[1],
            this.capX[2],
            rim,
            this.capZ[2],
            m.x,
            core,
            m.z,
            GlyphTint.alpha(this.lapis, a * 0.65F)
         );
      this.canvas
         .outwardTriangle(
            this.capX[0],
            rim,
            this.capZ[0],
            this.capX[2],
            rim,
            this.capZ[2],
            this.capX[3],
            rim,
            this.capZ[3],
            m.x,
            core,
            m.z,
            GlyphTint.alpha(this.lapis, a * 0.65F)
         );
      int edge = GlyphTint.alpha(this.light, a * 0.95F);

      for (int k = 0; k < 4; k++) {
         int n = k + 1 & 3;
         this.canvas.line(this.capX[k], rim, this.capZ[k], this.capX[n], rim, this.capZ[n], edge, 2.2F);
         this.canvas.line(this.capX[k], rim, this.capZ[k], m.x, apex, m.z, edge, 2.2F);
      }

      double drop = apex - top;
      int plumb = GlyphTint.alpha(this.gold, a * 0.45F);

      for (int i = 0; i < 3; i++) {
         double from = apex - drop * (0.12 + i * 0.3);
         double to = from - drop * 0.14;
         this.canvas.line(m.x, from, m.z, m.x, to, m.z, plumb, 1.6F);
      }

      float orbit = (float)(-this.phase * 1.9 % (float) (Math.PI * 2));
      float reach = half * 1.7F;

      for (int i = 0; i < 4; i++) {
         float angle = orbit + i * (float) (Math.PI / 2) + (float) (Math.PI / 4);
         float twinkle = 0.6F + 0.4F * Mth.sin(this.phase * 7.0 + i * 2.1);
         this.canvas
            .sprite(
               GlyphShapes.SPARK,
               m.x + Mth.cos(angle) * reach,
               rim - depth * 0.15,
               m.z + Mth.sin(angle) * reach,
               0.06F * size * twinkle,
               angle,
               GlyphTint.alpha(this.light, a * twinkle)
            );
      }
   }

   private float blink() {
      double cycle = this.phase % 4.6;
      return cycle > 0.22 ? 1.0F : 1.0F - 0.88F * Mth.sin(cycle / 0.22 * Math.PI);
   }

   private static float[] scatter(int count, long salt) {
      RandomSource random = RandomSource.create(salt);
      float[] out = new float[count];

      for (int i = 0; i < count; i++) {
         out[i] = random.nextFloat();
      }

      return out;
   }

   private static float unfurl(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * (1.0F + 1.6F * t) * (1.0F - 0.6F * t);
   }

   @Environment(EnvType.CLIENT)
   private static final class Mark {
      LivingEntity entity;
      double x;
      double y;
      double z;
      float width = 0.6F;
      float height = 1.8F;
      float presence;
      float hurt;

      void assign(LivingEntity target) {
         this.entity = target;
         this.presence = 0.0F;
         this.hurt = 0.0F;
      }

      void follow(float partialTick, ClientLevel level) {
         LivingEntity e = this.entity;
         if (e != null) {
            if (!e.isRemoved() && e.level() == level) {
               this.x = Mth.lerp((double)partialTick, e.xo, e.getX());
               this.y = Mth.lerp((double)partialTick, e.yo, e.getY());
               this.z = Mth.lerp((double)partialTick, e.zo, e.getZ());
               this.width = e.getBbWidth();
               this.height = e.getBbHeight();
               this.hurt = e.hurtTime > 0 ? Mth.clamp((e.hurtTime - partialTick) / 10.0F, 0.0F, 1.0F) : 0.0F;
            } else {
               this.hurt = 0.0F;
            }
         }
      }
   }
}
