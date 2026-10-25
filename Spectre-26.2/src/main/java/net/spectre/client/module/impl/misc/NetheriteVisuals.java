package net.spectre.client.module.impl.misc;

import imgui.ImDrawList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.TracerEngine;
import net.spectre.client.util.glyph.GlyphCanvas;
import net.spectre.client.util.glyph.GlyphShapes;
import net.spectre.client.util.glyph.GlyphSwarm;
import net.spectre.client.util.glyph.GlyphTint;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class NetheriteVisuals {
   static final String DOTS = "Dots";
   static final String CUBES = "Cubes";
   static final String PULSES = "Pulses";
   private static final long PULSE_NANOS = 1400000000L;
   private static final float WAVE_SECONDS = 0.9F;
   private static final int MAX_WAVES = 12;
   private static final int GLOW_LIMIT = 32;
   private static final int DEBRIS_TRACERS = 3;
   private static final int LABEL_LIMIT = 8;
   private static final double PROBE_RADIUS = 12.0;
   private static final int DOT_LIMIT = 1800;
   private static final int CUBE_LIMIT = 450;
   private static final double MISSING_RADIUS = 32.0;
   private static final int MISSING_LIMIT = 1600;
   private static final float PROBE_FRESH_TICKS = 14.0F;
   private static final float[] SHELL_INFLATE = new float[]{0.06F, 0.16F, 0.3F};
   private static final float[] SHELL_ALPHA = new float[]{0.16F, 0.08F, 0.04F};
   private final GlyphCanvas canvas = new GlyphCanvas();
   private final GlyphSwarm swarm = new GlyphSwarm(128);
   private final RandomSource random = RandomSource.create();
   private final List<NetheriteVisuals.Wave> waves = new ArrayList<>();
   private final List<NetheriteVisuals.Label> labels = new ArrayList<>();
   private long lastFrame;
   private double pixel;

   void clear() {
      this.swarm.clear();
      this.waves.clear();
      this.labels.clear();
      this.lastFrame = 0L;
   }

   void burst(BlockPos pos, int rgb) {
      double x = pos.getX() + 0.5;
      double y = pos.getY() + 0.5;
      double z = pos.getZ() + 0.5;
      if (this.waves.size() >= 12) {
         this.waves.removeFirst();
      }

      this.waves.add(new NetheriteVisuals.Wave(x, y, z, System.nanoTime()));
      int light = GlyphTint.lift(rgb, 0.55F);

      for (int i = 0; i < 2; i++) {
         this.swarm
            .spawn(
               (byte)0,
               x + this.jitter(0.3F),
               y + 0.6,
               z + this.jitter(0.3F),
               this.jitter(0.2F),
               1.1F,
               this.jitter(0.2F),
               1.8F,
               0.22F,
               this.jitter(0.3F),
               this.jitter(0.5F),
               this.random.nextFloat(),
               light,
               128
            );
      }

      for (int i = 0; i < 10; i++) {
         float angle = this.random.nextFloat() * (float) (Math.PI * 2);
         float speed = 2.2F + this.random.nextFloat() * 1.6F;
         int color = GlyphTint.mix(rgb, -1, this.random.nextFloat() * 0.5F);
         this.swarm
            .spawn(
               (byte)3,
               x,
               y,
               z,
               Mth.cos(angle) * speed,
               1.2F + this.random.nextFloat() * 1.5F,
               Mth.sin(angle) * speed,
               0.8F,
               0.09F,
               angle,
               2.8F,
               this.random.nextFloat(),
               color,
               128
            );
      }

      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.9F));
   }

   void render(
      Minecraft mc,
      float partialTick,
      Vec3 eye,
      List<NetheriteVisuals.Section> sections,
      List<BlockPos> revealed,
      AdvancedFinder finder,
      NetheriteVisuals.Options options
   ) {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      this.swarm.step(dt);
      this.canvas.begin(mc.gameRenderer.mainCamera());
      this.pixel = FoglessLines.pixelScale(partialTick);
      int accent = GlyphTint.alpha(options.near(), 1.0F);
      int light = GlyphTint.lift(accent, 0.55F);
      float pulse = 0.5F + 0.5F * Mth.sin((float)(now % 1400000000L) / 1.4E9F * (float) (Math.PI * 2));
      this.labels.clear();
      this.drawRevealed(eye, revealed, options, accent, light, pulse);
      this.drawWaves(now, accent, light);
      if (options.showProbes()) {
         this.drawProbes(eye, finder, options, accent, light, pulse);
      }

      this.drawSections(sections, options, light, pulse);
      this.drawTracers(sections, options);
      this.swarm.draw(this.canvas, 1.0F, true);
      this.canvas.submit(true);
   }

   void overlay(ImDrawList dl, float screenW, float screenH) {
      Minecraft mc = Minecraft.getInstance();

      for (NetheriteVisuals.Label label : this.labels) {
         float[] screen = WorldLabel.project(mc, label.pos(), screenW, screenH);
         if (screen != null) {
            WorldLabel.drawText(dl, screen[0], screen[1], label.primary(), label.secondary(), 1.0F);
         }
      }
   }

   boolean hasLabels() {
      return !this.labels.isEmpty();
   }

   private static int tint(NetheriteVisuals.Options options, double distance) {
      float t = (float)Mth.clamp(distance / options.fade(), 0.0, 1.0);
      return GlyphTint.alpha(GlyphTint.mix(options.near(), options.far(), t), 1.0F);
   }

   private void drawSections(List<NetheriteVisuals.Section> sections, NetheriteVisuals.Options options, int light, float pulse) {
      for (NetheriteVisuals.Section section : sections) {
         if (section.progress() >= 0.0F) {
            this.drawScanning(section, light, pulse);
         }
      }

      for (NetheriteVisuals.Section sectionx : sections) {
         if (sectionx.progress() < 0.0F) {
            this.drawCandidate(sectionx, options);
         }
      }
   }

   private void drawCandidate(NetheriteVisuals.Section section, NetheriteVisuals.Options options) {
      double far = Mth.clamp(section.distance() / options.fade(), 0.0, 1.0);
      int base = tint(options, section.distance());
      this.box(section.box(), GlyphTint.alpha(base, (float)(0.95 - 0.3 * far)), (float)(2.4 - 0.8 * far));
      this.faces(section.box(), GlyphTint.alpha(base, (float)(0.07 - 0.03 * far)));
   }

   private void drawScanning(NetheriteVisuals.Section section, int light, float pulse) {
      AABB box = section.box();
      this.box(box, GlyphTint.alpha(light, 0.6F + 0.4F * pulse), 3.2F);
      this.faces(box, GlyphTint.alpha(light, 0.05F));
      double y = box.minY + (box.maxY - box.minY) * section.progress();
      int plane = GlyphTint.alpha(light, 0.2F);
      this.canvas.quad(box.minX, y, box.minZ, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, box.minX, y, box.maxZ, plane);
      this.canvas.quad(box.minX, y, box.minZ, box.minX, y, box.maxZ, box.maxX, y, box.maxZ, box.maxX, y, box.minZ, plane);
      int rim = GlyphTint.alpha(light, 0.95F);
      this.line(box.minX, y, box.minZ, box.maxX, y, box.minZ, rim, 2.4F);
      this.line(box.maxX, y, box.minZ, box.maxX, y, box.maxZ, rim, 2.4F);
      this.line(box.maxX, y, box.maxZ, box.minX, y, box.maxZ, rim, 2.4F);
      this.line(box.minX, y, box.maxZ, box.minX, y, box.minZ, rim, 2.4F);
   }

   private void drawTracers(List<NetheriteVisuals.Section> sections, NetheriteVisuals.Options options) {
      int drawn = 0;

      for (NetheriteVisuals.Section section : sections) {
         if (drawn >= options.tracerCount()) {
            break;
         }

         if (!(section.distance() < 1.0)) {
            Vec3 center = section.box().getCenter();
            TracerEngine.draw(center, GlyphTint.alpha(tint(options, section.distance()), 0.85F));
            String pct = section.progress() >= 0.0F ? Math.round(section.progress() * 100.0F) + "%" : null;
            this.labels.add(new NetheriteVisuals.Label(center, String.format(Locale.ROOT, "%.0fm", section.distance()), pct));
            drawn++;
         }
      }
   }

   private void drawProbes(Vec3 eye, AdvancedFinder finder, NetheriteVisuals.Options options, int accent, int light, float pulse) {
      int missing = GlyphTint.mix(light, -1, 0.5F);
      finder.forEachMissing(
         eye,
         32.0,
         1600,
         options.missingFrom(),
         (x, y, z) -> this.canvas
            .sprite(GlyphShapes.DIAMOND, x + 0.5, y + 0.5, z + 0.5, 0.11F + 0.05F * pulse, 0.0F, GlyphTint.alpha(missing, 0.5F + 0.45F * pulse))
      );
      int fresh = GlyphTint.mix(light, -1, 0.6F);
      String style = options.probeStyle();
      int limit = "Cubes".equals(style) ? 450 : 1800;
      finder.forEachProbe(eye, 12.0, limit, options.missingFrom(), (x, y, z, age) -> {
         float t = Math.min(1.0F, age / 14.0F);
         int color = GlyphTint.mix(fresh, accent, t);
         double cx = x + 0.5;
         double cy = y + 0.5;
         double cz = z + 0.5;
         if ("Cubes".equals(style)) {
            double half = 0.2 - 0.1 * t;
            this.faces(new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half), GlyphTint.alpha(color, 0.65F - 0.3F * t));
         } else {
            this.canvas.sprite(GlyphShapes.DIAMOND, cx, cy, cz, 0.13F - 0.06F * t, 0.0F, GlyphTint.alpha(color, 0.95F - 0.5F * t));
            if ("Pulses".equals(style) && t < 1.0F) {
               this.canvas.sprite(GlyphShapes.HALO, cx, cy, cz, 0.15F + 0.55F * t, 0.0F, GlyphTint.alpha(fresh, 0.55F * (1.0F - t) * (1.0F - t)));
            }
         }
      });
   }

   private void drawRevealed(Vec3 eye, List<BlockPos> revealed, NetheriteVisuals.Options options, int accent, int light, float pulse) {
      List<BlockPos> sorted = new ArrayList<>(revealed);
      sorted.sort(Comparator.comparingDouble(posx -> posx.distToCenterSqr(eye)));

      for (int i = 0; i < sorted.size(); i++) {
         BlockPos pos = sorted.get(i);
         double distance = Math.sqrt(pos.distToCenterSqr(eye));
         if (distance > options.range()) {
            break;
         }

         AABB box = new AABB(pos);
         if (i < 32) {
            for (int s = 0; s < SHELL_INFLATE.length; s++) {
               this.faces(box.inflate(SHELL_INFLATE[s]), GlyphTint.alpha(accent, SHELL_ALPHA[s] * (0.7F + 0.3F * pulse)));
            }
         }

         this.faces(box, GlyphTint.alpha(accent, 0.3F));
         this.box(box, GlyphTint.alpha(light, 0.95F), 2.6F);
         Vec3 center = box.getCenter();
         if (i < 3) {
            TracerEngine.draw(center, GlyphTint.alpha(accent, 0.8F));
         }

         if (i < 8) {
            this.labels.add(new NetheriteVisuals.Label(center.add(0.0, 0.9, 0.0), String.format(Locale.ROOT, "%.0fm", distance), null));
         }
      }
   }

   private void drawWaves(long now, int accent, int light) {
      Iterator<NetheriteVisuals.Wave> iterator = this.waves.iterator();

      while (iterator.hasNext()) {
         NetheriteVisuals.Wave wave = iterator.next();
         float t = (float)(now - wave.born()) / 1.0E9F / 0.9F;
         if (t >= 1.0F) {
            iterator.remove();
         } else {
            float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float radius = 0.4F + 2.6F * ease;
            float fade = (1.0F - t) * (1.0F - t);
            this.canvas.ring(wave.x(), wave.y(), wave.z(), radius * 0.86F, radius, 48, GlyphTint.alpha(light, fade * 0.9F));
            this.canvas.ring(wave.x(), wave.y(), wave.z(), radius, radius * 1.25F, 48, GlyphTint.alpha(accent, fade * 0.3F));
         }
      }
   }

   private void line(double ax, double ay, double az, double bx, double by, double bz, int argb, float width) {
      this.canvas.ribbon(ax, ay, az, bx, by, bz, argb, width, this.pixel);
   }

   private void box(AABB b, int argb, float width) {
      this.line(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, argb, width);
      this.line(b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, argb, width);
      this.line(b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, argb, width);
      this.line(b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, argb, width);
      this.line(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, argb, width);
      this.line(b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, argb, width);
      this.line(b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, argb, width);
      this.line(b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, argb, width);
      this.line(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, argb, width);
      this.line(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, argb, width);
      this.line(b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, argb, width);
      this.line(b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, argb, width);
   }

   private void faces(AABB b, int argb) {
      this.face(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, argb);
      this.face(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, argb);
      this.face(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ, argb);
      this.face(b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, argb);
      this.face(b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, argb);
      this.face(b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, argb);
   }

   private void face(
      double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz, int argb
   ) {
      this.canvas.quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, argb);
      this.canvas.quad(ax, ay, az, dx, dy, dz, cx, cy, cz, bx, by, bz, argb);
   }

   private float jitter(float amount) {
      return (this.random.nextFloat() * 2.0F - 1.0F) * amount;
   }

   @Environment(EnvType.CLIENT)
   private record Label(Vec3 pos, String primary, String secondary) {
   }

   @Environment(EnvType.CLIENT)
   record Options(int near, int far, double fade, double range, String probeStyle, float missingFrom, int tracerCount, boolean showProbes) {
   }

   @Environment(EnvType.CLIENT)
   record Section(AABB box, double distance, float progress) {
   }

   @Environment(EnvType.CLIENT)
   private record Wave(double x, double y, double z, long born) {
   }
}
