package net.spectre.client.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

@Environment(EnvType.CLIENT)
public final class Ballistics {
   public static final int DEFAULT_LIFETIME = 240;
   private static final double VOID_DEPTH = 64.0;
   private static final double FULL_MARGIN = 0.3;
   private static final double SEARCH_PAD = 1.3;
   private static final double FLUID_SKIN = 0.001;
   private static final double SHOOTER_CLEARANCE = 1.0;

   private Ballistics() {
   }

   @Environment(EnvType.CLIENT)
   public static enum Body {
      ARROW(0.05, 0.99, 0.6, true, 0.5),
      TRIDENT(0.05, 0.99, 0.99, true, 0.5),
      ORB(0.03, 0.99, 0.8, false, 0.25),
      FLASK(0.05, 0.99, 0.8, false, 0.25),
      BOTTLE(0.07, 0.99, 0.8, false, 0.25),
      ROCKET(0.0, 1.0, 1.0, true, 0.25);

      private final double gravity;
      private final double airKeep;
      private final double waterKeep;
      private final boolean movesFirst;
      private final double size;

      private Body(double gravity, double airKeep, double waterKeep, boolean movesFirst, double size) {
         this.gravity = gravity;
         this.airKeep = airKeep;
         this.waterKeep = waterKeep;
         this.movesFirst = movesFirst;
         this.size = size;
      }

      public double gravity() {
         return this.gravity;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Flight {
      private double[] xs = new double[96];
      private double[] ys = new double[96];
      private double[] zs = new double[96];
      private int size;
      private int ticks;
      private double length;
      private Ballistics.Outcome outcome = Ballistics.Outcome.OPEN;
      private Direction face = Direction.UP;
      private Entity struck;
      private Ballistics.Body body = Ballistics.Body.ARROW;
      private double px;
      private double py;
      private double pz;
      private double vx;
      private double vy;
      private double vz;
      private double gravity;
      private double width;
      private double height;
      private boolean wet;
      private int age;
      private int lifetime = 240;
      private Entity shooter;
      private Entity self;
      private boolean leftShooter;
      private Entity shooterRoot;
      private final List<AABB> shooterBoxes = new ArrayList<>();
      private final MutableBlockPos cursor = new MutableBlockPos();
      private double near;
      private double far;

      public Ballistics.Flight launch(Ballistics.Body body, Vec3 from, Vec3 motion) {
         this.body = body;
         this.px = from.x;
         this.py = from.y;
         this.pz = from.z;
         this.vx = motion.x;
         this.vy = motion.y;
         this.vz = motion.z;
         this.gravity = body.gravity;
         this.width = body.size;
         this.height = body.size;
         this.wet = false;
         this.age = 0;
         this.lifetime = 240;
         this.shooter = null;
         this.self = null;
         this.leftShooter = false;
         return this;
      }

      public Ballistics.Flight gravity(double value) {
         this.gravity = value;
         return this;
      }

      public Ballistics.Flight bounds(double w, double h) {
         this.width = w;
         this.height = h;
         return this;
      }

      public Ballistics.Flight wet(boolean value) {
         this.wet = value;
         return this;
      }

      public Ballistics.Flight age(int ticksAlive) {
         this.age = ticksAlive;
         return this;
      }

      public Ballistics.Flight lifetime(int maxTicks) {
         this.lifetime = Math.max(1, maxTicks);
         return this;
      }

      public Ballistics.Flight ignoring(Entity shooter, Entity self) {
         this.shooter = shooter;
         this.self = self;
         return this;
      }

      public Ballistics.Flight leftShooter(boolean value) {
         this.leftShooter = value;
         return this;
      }

      public Ballistics.Flight solve(Level level) {
         this.size = 0;
         this.struck = null;
         this.outcome = Ballistics.Outcome.OPEN;
         this.face = Direction.UP;
         this.trace(level);
         this.strike(level);
         this.measure();
         this.release();
         return this;
      }

      public void clear() {
         this.size = 0;
         this.length = 0.0;
         this.struck = null;
         this.leftShooter = false;
         this.outcome = Ballistics.Outcome.OPEN;
         this.release();
      }

      private void release() {
         this.shooter = null;
         this.self = null;
         this.shooterRoot = null;
         this.shooterBoxes.clear();
      }

      public int size() {
         return this.size;
      }

      public double x(int i) {
         return this.xs[i];
      }

      public double y(int i) {
         return this.ys[i];
      }

      public double z(int i) {
         return this.zs[i];
      }

      public Vec3 point(int i) {
         return new Vec3(this.xs[i], this.ys[i], this.zs[i]);
      }

      public Vec3 impact() {
         return this.point(this.size - 1);
      }

      public Vec3 heading() {
         if (this.size < 2) {
            return Vec3.ZERO;
         } else {
            int last = this.size - 1;
            return new Vec3(this.xs[last] - this.xs[last - 1], this.ys[last] - this.ys[last - 1], this.zs[last] - this.zs[last - 1]);
         }
      }

      public int ticks() {
         return this.ticks;
      }

      public double length() {
         return this.length;
      }

      public Ballistics.Outcome outcome() {
         return this.outcome;
      }

      public Direction face() {
         return this.face;
      }

      public Entity struck() {
         return this.struck;
      }

      public boolean hasLeftShooter() {
         return this.leftShooter;
      }

      private void trace(Level level) {
         CollisionContext context = this.self != null ? CollisionContext.of(this.self) : CollisionContext.empty();
         double floor = level.getMinY() - 64.0;
         double x = this.px;
         double y = this.py;
         double z = this.pz;
         double mx = this.vx;
         double my = this.vy;
         double mz = this.vz;
         boolean inWater = this.wet;
         this.push(x, y, z);
         int tick = 0;

         while (tick < this.lifetime) {
            tick++;
            if (!this.body.movesFirst) {
               my -= this.gravity;
               double keep = inWater ? this.body.waterKeep : this.body.airKeep;
               mx *= keep;
               my *= keep;
               mz *= keep;
            }

            double nx = x + mx;
            double ny = y + my;
            double nz = z + mz;
            BlockHitResult hit = level.clipIncludingBorder(new ClipContext(new Vec3(x, y, z), new Vec3(nx, ny, nz), Block.COLLIDER, Fluid.NONE, context));
            if (hit.getType() != Type.MISS) {
               Vec3 at = hit.getLocation();
               this.push(at.x, at.y, at.z);
               this.face = hit.getDirection();
               this.outcome = Ballistics.Outcome.BLOCK;
               this.ticks = tick;
               return;
            }

            x = nx;
            y = ny;
            z = nz;
            this.push(nx, ny, nz);
            if (this.body.movesFirst) {
               double keep = inWater ? this.body.waterKeep : this.body.airKeep;
               mx *= keep;
               my = my * keep - this.gravity;
               mz *= keep;
            }

            inWater = this.submerged(level, nx, ny, nz);
            if (ny < floor) {
               break;
            }
         }

         this.ticks = tick;
         if (this.body == Ballistics.Body.ROCKET && tick >= this.lifetime) {
            this.outcome = Ballistics.Outcome.BURST;
         }
      }

      private void strike(Level level) {
         if (this.size >= 2) {
            boolean clear = this.leftShooter || this.departs();
            this.leftShooter = clear;
            double minX = this.xs[0];
            double minY = this.ys[0];
            double minZ = this.zs[0];
            double maxX = minX;
            double maxY = minY;
            double maxZ = minZ;

            for (int i = 1; i < this.size; i++) {
               minX = Math.min(minX, this.xs[i]);
               minY = Math.min(minY, this.ys[i]);
               minZ = Math.min(minZ, this.zs[i]);
               maxX = Math.max(maxX, this.xs[i]);
               maxY = Math.max(maxY, this.ys[i]);
               maxZ = Math.max(maxZ, this.zs[i]);
            }

            AABB span = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.3);
            List<Entity> candidates = level.getEntities(this.self, span, this::canStrike);
            if (!candidates.isEmpty()) {
               for (int i = 1; i < this.size; i++) {
                  if (!clear && i > 1) {
                     clear = this.outside(i - 1);
                  }

                  double ax = this.xs[i - 1];
                  double ay = this.ys[i - 1];
                  double az = this.zs[i - 1];
                  double dx = this.xs[i] - ax;
                  double dy = this.ys[i] - ay;
                  double dz = this.zs[i] - az;
                  double margin = Mth.clamp((this.age + i - 2) / 20.0, 0.0, 0.3);
                  double best = Double.MAX_VALUE;
                  Entity victim = null;

                  for (int c = 0; c < candidates.size(); c++) {
                     Entity entity = candidates.get(c);
                     if (clear || entity.getRootVehicle() != this.shooterRoot) {
                        AABB box = entity.getBoundingBox();
                        double t = this.entry(ax, ay, az, dx, dy, dz, box, margin);
                        if (t < best) {
                           best = t;
                           victim = entity;
                        }
                     }
                  }

                  if (victim != null) {
                     this.xs[i] = ax + dx * best;
                     this.ys[i] = ay + dy * best;
                     this.zs[i] = az + dz * best;
                     this.size = i + 1;
                     this.ticks = i;
                     this.struck = victim;
                     this.outcome = Ballistics.Outcome.ENTITY;
                     return;
                  }
               }
            }
         }
      }

      private boolean canStrike(Entity entity) {
         return !entity.isSpectator() && entity.canBeHitByProjectile();
      }

      private boolean departs() {
         this.shooterRoot = this.shooter != null ? this.shooter.getRootVehicle() : null;
         if (this.shooterRoot == null) {
            return true;
         } else {
            this.shooterBoxes.clear();
            if (EntitySelector.CAN_BE_PICKED.test(this.shooterRoot)) {
               this.shooterBoxes.add(this.shooterRoot.getBoundingBox());
            }

            for (Entity rider : this.shooterRoot.getIndirectPassengers()) {
               if (EntitySelector.CAN_BE_PICKED.test(rider)) {
                  this.shooterBoxes.add(rider.getBoundingBox());
               }
            }

            return this.outside(0);
         }
      }

      private boolean outside(int i) {
         double x = this.xs[i];
         double y = this.ys[i];
         double z = this.zs[i];
         double dx = this.xs[i + 1] - x;
         double dy = this.ys[i + 1] - y;
         double dz = this.zs[i + 1] - z;
         double half = this.width * 0.5;
         double minX = x - half + Math.min(dx, 0.0) - 1.0;
         double maxX = x + half + Math.max(dx, 0.0) + 1.0;
         double minY = y + Math.min(dy, 0.0) - 1.0;
         double maxY = y + this.height + Math.max(dy, 0.0) + 1.0;
         double minZ = z - half + Math.min(dz, 0.0) - 1.0;
         double maxZ = z + half + Math.max(dz, 0.0) + 1.0;

         for (int b = 0; b < this.shooterBoxes.size(); b++) {
            if (this.shooterBoxes.get(b).intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
               return false;
            }
         }

         return true;
      }

      private double entry(double ax, double ay, double az, double dx, double dy, double dz, AABB box, double margin) {
         this.near = 0.0;
         this.far = 1.0;
         if (!this.slab(ax, dx, box.minX - margin, box.maxX + margin)) {
            return Double.MAX_VALUE;
         } else if (!this.slab(ay, dy, box.minY - margin, box.maxY + margin)) {
            return Double.MAX_VALUE;
         } else if (!this.slab(az, dz, box.minZ - margin, box.maxZ + margin)) {
            return Double.MAX_VALUE;
         } else {
            return this.near > 0.0 ? this.near : Double.MAX_VALUE;
         }
      }

      private boolean slab(double origin, double delta, double lo, double hi) {
         if (!(Math.abs(delta) < 1.0E-12)) {
            double enter = (lo - origin) / delta;
            double exit = (hi - origin) / delta;
            if (enter > exit) {
               double swap = enter;
               enter = exit;
               exit = swap;
            }

            if (enter > this.near) {
               this.near = enter;
            }

            if (exit < this.far) {
               this.far = exit;
            }

            return this.near <= this.far;
         } else {
            return origin >= lo && origin <= hi;
         }
      }

      private boolean submerged(Level level, double x, double y, double z) {
         double half = this.width * 0.5 - 0.001;
         double bottom = y + 0.001;
         int x0 = Mth.floor(x - half);
         int x1 = Mth.floor(x + half);
         int y0 = Mth.floor(bottom);
         int y1 = Mth.floor(y + this.height - 0.001);
         int z0 = Mth.floor(z - half);
         int z1 = Mth.floor(z + half);

         for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
               for (int bz = z0; bz <= z1; bz++) {
                  FluidState fluid = level.getFluidState(this.cursor.set(bx, by, bz));
                  if (fluid.is(FluidTags.WATER) && by + fluid.getHeight(level, this.cursor) >= bottom) {
                     return true;
                  }
               }
            }
         }

         return false;
      }

      private void measure() {
         double total = 0.0;

         for (int i = 1; i < this.size; i++) {
            double dx = this.xs[i] - this.xs[i - 1];
            double dy = this.ys[i] - this.ys[i - 1];
            double dz = this.zs[i] - this.zs[i - 1];
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
         }

         this.length = total;
      }

      private void push(double x, double y, double z) {
         if (this.size == this.xs.length) {
            int grown = this.size * 2;
            this.xs = Arrays.copyOf(this.xs, grown);
            this.ys = Arrays.copyOf(this.ys, grown);
            this.zs = Arrays.copyOf(this.zs, grown);
         }

         this.xs[this.size] = x;
         this.ys[this.size] = y;
         this.zs[this.size] = z;
         this.size++;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Outcome {
      OPEN,
      BLOCK,
      ENTITY,
      BURST;
   }
}
