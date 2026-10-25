package net.spectre.client.module.impl.render;

import com.example.client.mixin.AccessorBlockOverlayMultiPlayerGameMode;
import com.example.client.mixin.AccessorGameRenderer;
import java.util.ArrayList;
import java.util.List;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class BlockOverlayModule extends ClientModule {
   private static final String BOTH = "Both";
   private static final String OUTLINE = "Outline";
   private static final String FILL = "Fill";
   private static final String GRADIENT = "Gradient";
   private static final String SOLID = "Solid";
   private static final String RAINBOW = "Rainbow";
   private static final float FADE_IN = 0.12F;
   private static final float FADE_OUT = 0.2F;
   private static final double SNAP_DISTANCE = 6.0;
   private static final float FLOW_SPEED = 0.35F;
   private static final double SEGMENT = 0.125;
   private static final double FILL_INFLATE = 0.002;
   private static final double LINE_LIFT = 0.004;
   private static final double GLOW_LIFT = 0.002;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Both", "Both", "Outline", "Fill"));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color Mode", "Gradient", "Gradient", "Solid", "Rainbow"));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Color", -871878));
   private final ColorSetting secondColor = this.addSetting("second-color", new ColorSetting("Second Color", -6480));
   private final BooleanSetting flow = this.addSetting("flow", new BooleanSetting("Flow", true));
   private final NumberSetting fillOpacity = this.addSetting("fill-opacity", new NumberSetting("Fill Opacity", 20.0F, 2.0F, 60.0F).step(1.0F));
   private final NumberSetting width = this.addSetting("width", new NumberSetting("Width", 2.0F, 1.0F, 5.0F).step(0.5F));
   private final BooleanSetting glow = this.addSetting("glow", new BooleanSetting("Glow", true));
   private final BooleanSetting smooth = this.addSetting("smooth", new BooleanSetting("Smooth", true));
   private final NumberSetting smoothSpeed = this.addSetting("smooth-speed", new NumberSetting("Smooth Speed", 16.0F, 4.0F, 40.0F).step(1.0F));
   private final BooleanSetting miningProgress = this.addSetting("mining-progress", new BooleanSetting("Mining Progress", true));
   private final BooleanSetting faceOnly = this.addSetting("face-only", new BooleanSetting("Face Only", false));
   private final BooleanSetting seeThrough = this.addSetting("see-through", new BooleanSetting("See Through", false));
   private ClientLevel level;
   private BlockPos pos;
   private BlockState state;
   private BlockPos partner;
   private BlockState partnerState;
   private final List<double[]> edges = new ArrayList<>();
   private List<AABB> boxes = List.of();
   private AABB localBounds;
   private AABB goal;
   private AABB box;
   private Direction face;
   private AABB faceBox;
   private float presence;
   private float progress;
   private long lastFrame;
   private Vec3 eye;
   private double time;
   private float fade;
   private double ax;
   private double bx;
   private double ay;
   private double by;
   private double az;
   private double bz;

   public BlockOverlayModule() {
      super("block-overlay", "Block Overlay", "A smooth, animated outline and fill on the block you look at.", ModuleCategory.VISUALS);
      this.secondColor.visibleWhen(() -> this.colorMode.is("Gradient"));
      this.flow.visibleWhen(() -> !this.colorMode.is("Solid"));
      this.fillOpacity.visibleWhen(() -> !this.style.is("Outline"));
      this.width.visibleWhen(() -> !this.style.is("Fill"));
      this.glow.visibleWhen(() -> !this.style.is("Fill"));
      this.smoothSpeed.visibleWhen(this.smooth::get);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         if (mc.level != this.level) {
            this.reset();
            this.level = mc.level;
         }

         long now = System.nanoTime();
         float dt = this.lastFrame == 0L ? 0.0F : Math.min((float)(now - this.lastFrame) / 1.0E9F, 0.1F);
         this.lastFrame = now;
         BlockHitResult hit = target(mc);
         if (hit != null && this.adopt(mc, hit)) {
            if (this.box != null && !(this.presence < 0.02F) && this.smooth.get() && !(this.box.getCenter().distanceTo(this.goal.getCenter()) > 6.0)) {
               this.box = lerp(this.box, this.goal, 1.0F - (float)Math.exp(-this.smoothSpeed.get() * dt));
            } else {
               this.box = this.goal;
            }

            this.presence = Math.min(1.0F, this.presence + dt / 0.12F);
         } else {
            hit = null;
            this.presence = Math.max(0.0F, this.presence - dt / 0.2F);
            if (this.presence == 0.0F) {
               this.reset();
               this.level = mc.level;
               return;
            }
         }

         if (this.box != null) {
            this.updateProgress(mc, dt, hit != null);
            this.eye = mc.gameRenderer.mainCamera().position();
            this.time = now % 3600000000000L / 1.0E9;
            this.draw();
         }
      } else {
         this.reset();
      }
   }

   private static BlockHitResult target(Minecraft mc) {
      if (!(mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK && !hit.isInside())) {
         return null;
      } else if (!((AccessorGameRenderer)mc.gameRenderer).spectre$shouldRenderBlockOutline()) {
         return null;
      } else {
         BlockPos at = hit.getBlockPos();
         return !mc.level.getBlockState(at).isAir() && mc.level.getWorldBorder().isWithinBounds(at) ? hit : null;
      }
   }

   private boolean adopt(Minecraft mc, BlockHitResult hit) {
      ClientLevel world = mc.level;
      BlockPos at = hit.getBlockPos();
      BlockPos other = partner(world, at, world.getBlockState(at));
      BlockPos ref = other != null && other.compareTo(at) < 0 ? other : at;
      BlockPos second = other == null ? null : (ref.equals(at) ? other : at);
      BlockState refState = world.getBlockState(ref);
      BlockState secondState = second == null ? null : world.getBlockState(second);
      if (!ref.equals(this.pos) || refState != this.state || secondState != this.partnerState) {
         Entity viewer = mc.getCameraEntity();
         CollisionContext context = viewer == null ? CollisionContext.empty() : CollisionContext.of(viewer);
         VoxelShape shape = refState.getShape(world, ref, context);
         if (second != null) {
            shape = Shapes.or(shape, secondState.getShape(world, second, context).move(second.subtract(ref)));
         }

         shape = shape.optimize();
         if (shape.isEmpty()) {
            return false;
         }

         if (!ref.equals(this.pos)) {
            this.progress = 0.0F;
         }

         this.pos = ref;
         this.state = refState;
         this.partner = second;
         this.partnerState = secondState;
         this.edges.clear();
         shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> this.edges.add(new double[]{x0, y0, z0, x1, y1, z1}));
         this.boxes = shape.toAabbs();
         this.localBounds = shape.bounds();
         this.goal = this.localBounds.move(ref);
      }

      this.face = hit.getDirection();
      this.faceBox = this.boxOnFace(hit.getLocation().subtract(this.pos.getX(), this.pos.getY(), this.pos.getZ()));
      return true;
   }

   private static BlockPos partner(ClientLevel world, BlockPos at, BlockState state) {
      BlockPos other = null;
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? at.above() : at.below();
      } else if (state.getBlock() instanceof BedBlock) {
         other = at.relative(BedBlock.getConnectedDirection(state));
      } else if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
         other = ChestBlock.getConnectedBlockPos(at, state);
      }

      return other != null && world.getBlockState(other).is(state.getBlock()) ? other : null;
   }

   private AABB boxOnFace(Vec3 local) {
      double e = 0.001;

      for (AABB b : this.boxes) {
         boolean onPlane = switch (this.face) {
            case EAST -> Math.abs(local.x - b.maxX) < e;
            case WEST -> Math.abs(local.x - b.minX) < e;
            case UP -> Math.abs(local.y - b.maxY) < e;
            case DOWN -> Math.abs(local.y - b.minY) < e;
            case SOUTH -> Math.abs(local.z - b.maxZ) < e;
            case NORTH -> Math.abs(local.z - b.minZ) < e;
         };
         if (onPlane && b.inflate(e).contains(local)) {
            return b;
         }
      }

      return this.localBounds;
   }

   private void updateProgress(Minecraft mc, float dt, boolean targeted) {
      float want = 0.0F;
      MultiPlayerGameMode gameMode = mc.gameMode;
      if (targeted && this.miningProgress.get() && gameMode != null && gameMode.isDestroying()) {
         AccessorBlockOverlayMultiPlayerGameMode mining = (AccessorBlockOverlayMultiPlayerGameMode)gameMode;
         BlockPos breaking = mining.spectre$blockOverlayDestroyPos();
         if (breaking != null && (breaking.equals(this.pos) || breaking.equals(this.partner))) {
            want = Mth.clamp(mining.spectre$blockOverlayDestroyProgress(), 0.0F, 1.0F);
         }
      }

      this.progress = this.progress + (want - this.progress) * (1.0F - (float)Math.exp(-18.0F * dt));
   }

   private void reset() {
      this.level = null;
      this.pos = null;
      this.state = null;
      this.partner = null;
      this.partnerState = null;
      this.edges.clear();
      this.boxes = List.of();
      this.localBounds = null;
      this.goal = null;
      this.box = null;
      this.face = null;
      this.faceBox = null;
      this.presence = 0.0F;
      this.progress = 0.0F;
      this.lastFrame = 0L;
   }

   private void draw() {
      this.fade = this.presence * this.presence * (3.0F - 2.0F * this.presence);
      double pop = 0.94 + 0.06 * this.fade;
      double[] x = axis(this.box.minX, this.box.maxX, this.goal.minX, this.goal.maxX, this.pos.getX() - this.goal.minX, pop);
      double[] y = axis(this.box.minY, this.box.maxY, this.goal.minY, this.goal.maxY, this.pos.getY() - this.goal.minY, pop);
      double[] z = axis(this.box.minZ, this.box.maxZ, this.goal.minZ, this.goal.maxZ, this.pos.getZ() - this.goal.minZ, pop);
      this.ax = x[0];
      this.bx = x[1];
      this.ay = y[0];
      this.by = y[1];
      this.az = z[0];
      this.bz = z[1];
      boolean lines = !this.style.is("Fill");
      boolean fill = !this.style.is("Outline");
      float fillAlpha = this.fillOpacity.get() / 100.0F;
      if (fill) {
         if (this.faceOnly.get()) {
            this.faceFill(this.faceBox, this.face, fillAlpha, 0.002);
         } else {
            for (AABB b : this.boxes) {
               this.boxFill(b, fillAlpha, 0.002);
            }
         }
      }

      if (this.progress > 0.01F) {
         this.progressFill(Math.min(0.45F, fillAlpha * 1.4F + 0.16F));
      }

      if (lines) {
         this.outline(this.faceOnly.get() ? faceEdges(this.faceBox, this.face) : this.edges);
      }
   }

   private static double[] axis(double boxMin, double boxMax, double goalMin, double goalMax, double offset, double pop) {
      double goalSize = goalMax - goalMin;
      double scale = goalSize > 1.0E-6 ? (boxMax - boxMin) / goalSize : 1.0;
      double centre = (boxMin + boxMax) * 0.5;
      double a = centre * (1.0 - pop) + (boxMin + offset * scale) * pop;
      return new double[]{a, scale * pop};
   }

   private Vec3 world(double lx, double ly, double lz) {
      return new Vec3(this.ax + this.bx * lx, this.ay + this.by * ly, this.az + this.bz * lz);
   }

   private void outline(List<double[]> list) {
      boolean varies = !this.colorMode.is("Solid");
      List<double[]> pieces = new ArrayList<>(list.size() * 4);
      List<Integer> colors = new ArrayList<>(list.size() * 4);

      for (double[] e : list) {
         double length = Math.sqrt(sq(e[3] - e[0]) + sq(e[4] - e[1]) + sq(e[5] - e[2]));
         int n = varies ? Mth.clamp((int)Math.ceil(length / 0.125), 1, 16) : 1;

         for (int i = 0; i < n; i++) {
            double t0 = (double)i / n;
            double t1 = (double)(i + 1) / n;
            double tm = (t0 + t1) * 0.5;
            pieces.add(
               new double[]{
                  Mth.lerp(t0, e[0], e[3]),
                  Mth.lerp(t0, e[1], e[4]),
                  Mth.lerp(t0, e[2], e[5]),
                  Mth.lerp(t1, e[0], e[3]),
                  Mth.lerp(t1, e[1], e[4]),
                  Mth.lerp(t1, e[2], e[5])
               }
            );
            colors.add(this.colorAt(Mth.lerp(tm, e[0], e[3]), Mth.lerp(tm, e[1], e[4]), Mth.lerp(tm, e[2], e[5])));
         }
      }

      float w = this.width.get();
      if (this.glow.get()) {
         for (int i = 0; i < pieces.size(); i++) {
            double[] p = pieces.get(i);
            int c = colors.get(i);
            this.line(p, ARGB.multiplyAlpha(c, 0.34F * this.fade), w + 3.0F, 0.002);
            this.line(p, ARGB.multiplyAlpha(c, 0.13F * this.fade), w + 8.0F, 0.002);
         }
      }

      for (int i = 0; i < pieces.size(); i++) {
         this.line(pieces.get(i), ARGB.multiplyAlpha(colors.get(i), this.fade), w, 0.004);
      }
   }

   private void line(double[] p, int argb, float w, double lift) {
      GizmoProperties drawn = Gizmos.line(this.lift(this.world(p[0], p[1], p[2]), lift), this.lift(this.world(p[3], p[4], p[5]), lift), argb, w);
      if (this.seeThrough.get()) {
         drawn.setAlwaysOnTop();
      }
   }

   private Vec3 lift(Vec3 point, double by) {
      Vec3 toEye = this.eye.subtract(point);
      double length = toEye.length();
      return length < 1.0E-6 ? point : point.add(toEye.scale(by / length));
   }

   private void boxFill(AABB b, float alpha, double inflate) {
      for (Direction d : Direction.values()) {
         this.faceFill(b, d, alpha, inflate);
      }
   }

   private void faceFill(AABB b, Direction d, float alpha, double inflate) {
      Vec3 min = this.world(b.minX, b.minY, b.minZ).subtract(inflate, inflate, inflate);
      Vec3 max = this.world(b.maxX, b.maxY, b.maxZ).add(inflate, inflate, inflate);
      Vec3 centre = faceCentre(b, d);
      int c = this.colorAt(centre.x, centre.y, centre.z);
      int argb = Mth.clamp(Math.round(alpha * this.fade * 255.0F), 0, 255) << 24 | c & 16777215;
      GizmoProperties drawn = Gizmos.rect(min, max, d, GizmoStyle.fill(argb));
      if (this.seeThrough.get()) {
         drawn.setAlwaysOnTop();
      }
   }

   private void progressFill(float alpha) {
      double inflate = 0.004;
      if (this.faceOnly.get()) {
         AABB b = this.faceBox;
         AABB part = this.face.getAxis() == Axis.Y
            ? b.deflate(b.getXsize() * (1.0F - this.progress) * 0.5, 0.0, b.getZsize() * (1.0F - this.progress) * 0.5)
            : new AABB(b.minX, b.minY, b.minZ, b.maxX, b.minY + b.getYsize() * this.progress, b.maxZ);
         this.faceFill(part, this.face, alpha, inflate);
      } else {
         double top = this.localBounds.minY + this.localBounds.getYsize() * this.progress;

         for (AABB b : this.boxes) {
            if (!(b.minY >= top)) {
               this.boxFill(new AABB(b.minX, b.minY, b.minZ, b.maxX, Math.min(b.maxY, top), b.maxZ), alpha, inflate);
            }
         }
      }
   }

   private int colorAt(double lx, double ly, double lz) {
      int base = this.color.argb();
      if (this.colorMode.is("Solid")) {
         return base;
      } else {
         double u = (
               norm(lx, this.localBounds.minX, this.localBounds.maxX)
                  + norm(ly, this.localBounds.minY, this.localBounds.maxY)
                  + norm(lz, this.localBounds.minZ, this.localBounds.maxZ)
            )
            / 3.0;
         double shift = this.flow.get() ? this.time * 0.35F : 0.0;
         if (this.colorMode.is("Rainbow")) {
            double hue = u * 0.5 - shift;
            return base & 0xFF000000 | Mth.hsvToRgb((float)(hue - Math.floor(hue)), 0.65F, 1.0F) & 16777215;
         } else {
            double k = this.flow.get() ? 0.5 - 0.5 * Math.cos((Math.PI * 2) * (u - shift)) : u;
            return ARGB.srgbLerp((float)k, base, this.secondColor.argb());
         }
      }
   }

   private static double norm(double v, double min, double max) {
      return max - min < 1.0E-6 ? 0.5 : (v - min) / (max - min);
   }

   private static Vec3 faceCentre(AABB b, Direction d) {
      Vec3 c = b.getCenter();

      return switch (d) {
         case EAST -> new Vec3(b.maxX, c.y, c.z);
         case WEST -> new Vec3(b.minX, c.y, c.z);
         case UP -> new Vec3(c.x, b.maxY, c.z);
         case DOWN -> new Vec3(c.x, b.minY, c.z);
         case SOUTH -> new Vec3(c.x, c.y, b.maxZ);
         case NORTH -> new Vec3(c.x, c.y, b.minZ);
      };
   }

   private static List<double[]> faceEdges(AABB b, Direction d) {
      double[][] c = switch (d.getAxis()) {
         case X -> {
            double x = d == Direction.EAST ? b.maxX : b.minX;
            yield new double[][]{{x, b.minY, b.minZ}, {x, b.maxY, b.minZ}, {x, b.maxY, b.maxZ}, {x, b.minY, b.maxZ}};
         }
         case Y -> {
            double y = d == Direction.UP ? b.maxY : b.minY;
            yield new double[][]{{b.minX, y, b.minZ}, {b.maxX, y, b.minZ}, {b.maxX, y, b.maxZ}, {b.minX, y, b.maxZ}};
         }
         case Z -> {
            double z = d == Direction.SOUTH ? b.maxZ : b.minZ;
            yield new double[][]{{b.minX, b.minY, z}, {b.maxX, b.minY, z}, {b.maxX, b.maxY, z}, {b.minX, b.maxY, z}};
         }
      };
      List<double[]> edges = new ArrayList<>(4);

      for (int i = 0; i < 4; i++) {
         double[] p = c[i];
         double[] q = c[(i + 1) % 4];
         edges.add(new double[]{p[0], p[1], p[2], q[0], q[1], q[2]});
      }

      return edges;
   }

   private static AABB lerp(AABB from, AABB to, float k) {
      return new AABB(
         Mth.lerp((double)k, from.minX, to.minX),
         Mth.lerp((double)k, from.minY, to.minY),
         Mth.lerp((double)k, from.minZ, to.minZ),
         Mth.lerp((double)k, from.maxX, to.maxX),
         Mth.lerp((double)k, from.maxY, to.maxY),
         Mth.lerp((double)k, from.maxZ, to.maxZ)
      );
   }

   private static double sq(double v) {
      return v * v;
   }
}
