package net.spectre.client.module.impl.render;

import imgui.ImDrawList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.misc.NameProtectModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.PearlTrajectorySimulator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PearlTrajectoryModule extends ClientModule implements HudOverlayProvider {
   private static final int MAX_TRAIL_POINTS = 200;
   private static final double MIN_TRAIL_DISTANCE_SQ = 1.0E-4;
   private static final double PIECE_LENGTH = 0.45;
   private static final double FADE_IN_DISTANCE = 1.8;
   private static final int RING_SEGMENTS = 48;
   private static final float POP_IN_SECONDS = 0.22F;
   private static final float WAVE_PERIOD_SECONDS = 1.4F;
   private static final long LABEL_MAX_AGE_NANOS = 250000000L;
   private static final long EPOCH = System.nanoTime();
   private static final String LANDING_RING = "Ring";
   private static final String LANDING_BOX = "Box";
   private static final String LANDING_NONE = "None";
   private final Map<Integer, List<Vec3>> trails = new HashMap<>();
   private final List<PearlTrajectoryModule.Label> labels = new ArrayList<>();
   private long labelsAt;
   private long predictionShownAt = -1L;
   private final BooleanSetting sPredict = this.addSetting("predict", new BooleanSetting("Predict", true));
   private final BooleanSetting sTrail = this.addSetting("trail", new BooleanSetting("Trail", true));
   private final BooleanSetting sOthers = this.addSetting("others-pearls", new BooleanSetting("Others' Pearls", false));
   private final ModeSetting sLanding = this.addSetting("landing", new ModeSetting("Landing", "Ring", "Ring", "Box", "None"));
   private final BooleanSetting sInfo = this.addSetting("landing-info", new BooleanSetting("Landing Info", true));
   private final BooleanSetting sGlow = this.addSetting("glow", new BooleanSetting("Glow", true));
   private final BooleanSetting sGradient = this.addSetting("gradient", new BooleanSetting("Gradient", true));
   private final ColorSetting sColor = this.addSetting("color", new ColorSetting("Color", -9524994));
   private final ColorSetting sEndColor = this.addSetting("end-color", new ColorSetting("End Color", -3697942));
   private final ColorSetting sHitColor = this.addSetting("hit-color", new ColorSetting("Hit Color", -42406));
   private final NumberSetting sLineWidth = this.addSetting("line-width", new NumberSetting("Line Width", 2.0F, 0.5F, 5.0F).step(0.5F));

   public PearlTrajectoryModule() {
      super("pearl-trajectory", "Pearl Trajectory", "Predicts where a held pearl lands and previews the flight of thrown pearls.", ModuleCategory.RENDER);
      this.sEndColor.visibleWhen(this.sGradient::get);
   }

   @Override
   protected void onDisable() {
      this.trails.clear();
      this.labels.clear();
      this.predictionShownAt = -1L;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         Set<Integer> active = new HashSet<>();

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && this.isTracked(pearl, mc)) {
               active.add(pearl.getId());
               List<Vec3> points = this.trails.computeIfAbsent(pearl.getId(), ignored -> new ArrayList<>());
               Vec3 position = pearl.position();
               if (points.isEmpty() || !(points.getLast().distanceToSqr(position) < 1.0E-4)) {
                  points.add(position);
                  if (points.size() > 200) {
                     points.removeFirst();
                  }
               }
            }
         }

         this.trails.keySet().retainAll(active);
      } else {
         this.trails.clear();
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.labels.clear();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         long now = System.nanoTime();
         this.labelsAt = now;
         float time = (float)(now - EPOCH) / 1.0E9F;
         float width = this.sLineWidth.get();
         Vec3 eye = mc.player.getEyePosition(event.partialTick);
         if (this.sPredict.get() && PearlTrajectorySimulator.isHoldingPearl(mc.player)) {
            if (this.predictionShownAt < 0L) {
               this.predictionShownAt = now;
            }

            float pop = easeOutBack(Math.min(1.0F, (float)(now - this.predictionShownAt) / 1.0E9F / 0.22F));
            PearlTrajectorySimulator.SimulationResult result = PearlTrajectorySimulator.simulate(mc.level, mc.player, event.partialTick);
            this.drawPath(result.points(), width, true, false, 1.0F);
            this.drawLanding(result, pop, time, width, 1.0F);
            this.addLabel(result, eye, null);
         } else {
            this.predictionShownAt = -1L;
         }

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && this.isTracked(pearl, mc)) {
               Vec3 head = pearl.getPosition(event.partialTick);
               if (this.sTrail.get()) {
                  this.drawTrail(this.trails.get(pearl.getId()), head, width);
               }

               PearlTrajectorySimulator.SimulationResult rest = PearlTrajectorySimulator.simulate(mc.level, pearl);
               List<Vec3> ahead = new ArrayList<>(rest.points());
               ahead.set(0, head);
               this.drawPath(ahead, width * 0.8F, false, true, 0.7F);
               this.drawLanding(rest, 1.0F, time, width, 0.75F);
               Entity owner = pearl.getOwner();
               boolean own = owner == null || owner == mc.player;
               this.addLabel(rest, eye, own ? null : NameProtectModule.protect(owner.getName()).getString());
            }
         }
      }
   }

   private void drawPath(List<Vec3> points, float width, boolean fadeIn, boolean dashed, float alphaMul) {
      if (points.size() >= 2) {
         double total = 0.0;

         for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
         }

         if (!(total <= 1.0E-6)) {
            int start = this.sColor.argb();
            int end = this.sGradient.get() ? this.sEndColor.argb() : start;
            boolean glow = this.sGlow.get();
            double travelled = 0.0;
            int piece = 0;

            for (int i = 1; i < points.size(); i++) {
               Vec3 a = points.get(i - 1);
               Vec3 b = points.get(i);
               double length = a.distanceTo(b);
               int pieces = Math.max(1, (int)Math.ceil(length / 0.45));

               for (int p = 0; p < pieces; piece++) {
                  if (!dashed || (piece & 1) != 1) {
                     double t0 = (double)p / pieces;
                     double t1 = (double)(p + 1) / pieces;
                     double mid = travelled + length * (t0 + t1) * 0.5;
                     float alpha = alphaMul;
                     if (fadeIn) {
                        alpha = alphaMul * smoothstep((float)(mid / 1.8));
                     }

                     if (!(alpha < 0.02F)) {
                        int color = scaleAlpha(lerpArgb(start, end, (float)(mid / total)), alpha);
                        Vec3 from = a.lerp(b, t0);
                        Vec3 to = a.lerp(b, t1);
                        if (glow) {
                           Gizmos.line(from, to, scaleAlpha(color, 0.2F), width * 3.2F).setAlwaysOnTop();
                        }

                        Gizmos.line(from, to, color, width).setAlwaysOnTop();
                     }
                  }

                  p++;
               }

               travelled += length;
            }
         }
      }
   }

   private void drawTrail(List<Vec3> points, Vec3 head, float width) {
      if (points != null && !points.isEmpty()) {
         int color = this.sColor.argb();
         int count = points.size() + 1;

         for (int i = 1; i < count; i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = i < points.size() ? points.get(i) : head;
            float alpha = 0.15F + 0.85F * i / (count - 1.0F);
            Gizmos.line(from, to, scaleAlpha(color, alpha), width).setAlwaysOnTop();
         }
      }
   }

   private void drawLanding(PearlTrajectorySimulator.SimulationResult result, float scale, float time, float width, float alphaMul) {
      int hitColor = scaleAlpha(this.sHitColor.argb(), alphaMul);
      if (result.entityHit() != null) {
         AABB box = result.entityHit().getBoundingBox().inflate(0.05);
         Gizmos.cuboid(box, GizmoStyle.strokeAndFill(hitColor, width, scaleAlpha(hitColor, 0.14F))).setAlwaysOnTop();
      } else {
         BlockHitResult hit = result.blockHit();
         String mode = this.sLanding.get();
         if (hit != null && !"None".equals(mode)) {
            int color = scaleAlpha(this.sGradient.get() ? this.sEndColor.argb() : this.sColor.argb(), alphaMul);
            Direction face = hit.getDirection();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            if ("Box".equals(mode)) {
               AABB box = new AABB(hit.getBlockPos()).inflate(0.002);
               Gizmos.rect(box.getMinPosition(), box.getMaxPosition(), face, GizmoStyle.strokeAndFill(color, width, scaleAlpha(color, 0.22F))).setAlwaysOnTop();
            } else {
               Vec3 centre = result.landing().add(normal.scale(0.02));
               float pulse = 1.0F + 0.05F * (float)Math.sin(time * 5.0F);
               drawRing(centre, normal, 0.38F * scale * pulse, color, width);
               drawCross(centre, normal, 0.09F * scale, color, width);
               float phase = time % 1.4F / 1.4F;
               float waveRadius = lerp(0.3F, 0.95F, easeOutCubic(phase)) * scale;
               drawRing(centre, normal, waveRadius, scaleAlpha(color, 0.75F * (1.0F - phase)), width * 0.8F);
            }
         }
      }
   }

   private static void drawRing(Vec3 centre, Vec3 normal, float radius, int color, float width) {
      if ((color >>> 24 & 0xFF) >= 4 && !(radius <= 0.0F)) {
         Vec3[] basis = planeBasis(normal);
         Vec3 previous = null;

         for (int i = 0; i <= 48; i++) {
            double angle = (Math.PI * 2) * i / 48.0;
            Vec3 point = centre.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
            if (previous != null) {
               Gizmos.line(previous, point, color, width).setAlwaysOnTop();
            }

            previous = point;
         }
      }
   }

   private static void drawCross(Vec3 centre, Vec3 normal, float half, int color, float width) {
      Vec3[] basis = planeBasis(normal);
      Gizmos.line(centre.add(basis[0].scale(-half)), centre.add(basis[0].scale(half)), color, width).setAlwaysOnTop();
      Gizmos.line(centre.add(basis[1].scale(-half)), centre.add(basis[1].scale(half)), color, width).setAlwaysOnTop();
   }

   private static Vec3[] planeBasis(Vec3 normal) {
      Vec3 u = Math.abs(normal.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : normal.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      return new Vec3[]{u, normal.cross(u).normalize()};
   }

   private void addLabel(PearlTrajectorySimulator.SimulationResult result, Vec3 eye, String owner) {
      if (this.sInfo.get() && result.landed()) {
         String time = String.format(Locale.ROOT, "%.1fs", result.ticks() / 20.0F);
         String primary = String.format(Locale.ROOT, "%.1fm", eye.distanceTo(result.landing()));
         String secondary;
         if (result.entityHit() != null) {
            secondary = NameProtectModule.protect(result.entityHit().getName()).getString() + "  ·  " + time;
         } else {
            secondary = owner != null ? owner + "  ·  " + time : time;
         }

         this.labels.add(new PearlTrajectoryModule.Label(result.landing(), primary, secondary));
      }
   }

   private boolean isTracked(ThrownEnderpearl pearl, Minecraft mc) {
      if (pearl.isRemoved()) {
         return false;
      } else {
         Entity owner = pearl.getOwner();
         return owner == null || owner == mc.player || this.sOthers.get();
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.sInfo.get();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      if (!this.labels.isEmpty() && System.nanoTime() - this.labelsAt <= 250000000L) {
         Minecraft mc = Minecraft.getInstance();

         for (PearlTrajectoryModule.Label label : this.labels) {
            float[] screen = WorldLabel.project(mc, label.pos().add(0.0, 0.35, 0.0), screenW, screenH);
            if (screen != null) {
               WorldLabel.drawText(dl, screen[0], screen[1], label.primary(), label.secondary(), 1.0F);
            }
         }
      }
   }

   private static int lerpArgb(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   private static int scaleAlpha(int argb, float mul) {
      int alpha = Math.round((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, mul)));
      return alpha << 24 | argb & 16777215;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float smoothstep(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float easeOutCubic(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float u = t - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   @Environment(EnvType.CLIENT)
   private record Label(Vec3 pos, String primary, String secondary) {
   }
}
