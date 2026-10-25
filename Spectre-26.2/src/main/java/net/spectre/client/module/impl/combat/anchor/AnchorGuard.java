package net.spectre.client.module.impl.combat.anchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.spectre.client.module.impl.combat.crystal.Crystals;
import net.spectre.client.util.pvp.Rotation;
import net.spectre.client.util.pvp.RotationUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class AnchorGuard {
   private static final int BODY_COLUMNS = 3;
   private static final int BODY_ROWS = 5;
   private static final double MIN_SHADE = 0.15;

   private AnchorGuard() {
   }

   public static AnchorGuard.Spot find(ClientLevel level, LocalPlayer player, BlockPos anchor, Vec3 sightline) {
      Vec3 blast = Vec3.atCenterOf(anchor);
      AABB body = player.getBoundingBox();
      List<Vec3> points = bodyPoints(body);
      int needed = (int)Math.ceil(points.size() * 0.15);
      Vec3 eye = player.getEyePosition();
      BlockPos feet = player.blockPosition();
      List<AnchorGuard.Cell> cells = new ArrayList<>(8);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               BlockPos pos = feet.offset(dx, 0, dz);
               AABB box = new AABB(pos);
               if (!pos.equals(anchor) && !box.intersects(body) && !box.clip(eye, sightline).isPresent()) {
                  int shade = shade(box, blast, points);
                  if (shade >= needed) {
                     cells.add(new AnchorGuard.Cell(pos, shade, box.getCenter().distanceToSqr(player.position())));
                  }
               }
            }
         }
      }

      cells.sort(Comparator.comparingInt(AnchorGuard.Cell::shade).reversed().thenComparingDouble(AnchorGuard.Cell::distance));

      for (AnchorGuard.Cell cell : cells) {
         BlockState state = level.getBlockState(cell.pos());
         if (state.isCollisionShapeFullBlock(level, cell.pos())) {
            return new AnchorGuard.Spot(cell.pos(), null, null, null);
         }

         if (state.canBeReplaced()) {
            AnchorGuard.Spot spot = state.getShape(level, cell.pos()).isEmpty()
               ? supported(level, player, eye, cell.pos())
               : direct(level, player, eye, cell.pos(), state);
            if (spot != null) {
               return spot;
            }
         }
      }

      return null;
   }

   public static BlockHitResult hitAlong(ClientLevel level, LocalPlayer player, Rotation look, AnchorGuard.Spot spot) {
      BlockHitResult hit = trace(level, player, look);
      if (hit == null || !hit.getBlockPos().equals(spot.support())) {
         return null;
      } else if (spot.face() != null && hit.getDirection() != spot.face()) {
         return null;
      } else {
         BlockPos placed = Crystals.placedObsidian(level, player, hit, level.getBlockState(spot.support()));
         return spot.pos().equals(placed) ? hit : null;
      }
   }

   public static boolean looksAt(ClientLevel level, LocalPlayer player, Rotation look, BlockPos target) {
      BlockHitResult hit = trace(level, player, look);
      return hit != null && hit.getBlockPos().equals(target);
   }

   public static boolean isGuardBlock(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem item) {
         Block block = item.getBlock();
         return !(block instanceof EntityBlock) && !(block instanceof FallingBlock) && block != Blocks.RESPAWN_ANCHOR && block != Blocks.TNT
            ? block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            : false;
      } else {
         return false;
      }
   }

   private static AnchorGuard.Spot direct(ClientLevel level, LocalPlayer player, Vec3 eye, BlockPos pos, BlockState state) {
      AnchorGuard.Spot spot = new AnchorGuard.Spot(pos, pos, null, state.getShape(level, pos).bounds().move(pos).getCenter());
      return hitAlong(level, player, RotationUtils.fromTo(eye, spot.aim()), spot) != null ? spot : null;
   }

   private static AnchorGuard.Spot supported(ClientLevel level, LocalPlayer player, Vec3 eye, BlockPos pos) {
      AnchorGuard.Spot best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos support = pos.relative(side);
         BlockState state = level.getBlockState(support);
         if (!state.canBeReplaced()) {
            VoxelShape shape = state.getShape(level, support);
            if (!shape.isEmpty()) {
               Direction face = side.getOpposite();
               AABB box = shape.bounds().move(support);
               Axis axis = face.getAxis();
               double plane = face.getAxisDirection() == AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
               AnchorGuard.Spot spot = new AnchorGuard.Spot(pos, support, face, box.getCenter().with(axis, plane));
               if (hitAlong(level, player, RotationUtils.fromTo(eye, spot.aim()), spot) != null) {
                  double distance = side == Direction.DOWN ? -1.0 : eye.distanceToSqr(spot.aim());
                  if (distance < bestDistance) {
                     best = spot;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private static BlockHitResult trace(ClientLevel level, LocalPlayer player, Rotation look) {
      Vec3 eye = player.getEyePosition();
      Vec3 reach = Vec3.directionFromRotation(look.pitch(), look.yaw()).scale(player.blockInteractionRange());
      BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(reach), net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.NONE, player));
      return hit.getType() == Type.BLOCK ? hit : null;
   }

   private static int shade(AABB box, Vec3 blast, List<Vec3> points) {
      int blocked = 0;

      for (Vec3 point : points) {
         if (box.clip(point, blast).isPresent()) {
            blocked++;
         }
      }

      return blocked;
   }

   private static List<Vec3> bodyPoints(AABB body) {
      List<Vec3> points = new ArrayList<>(45);

      for (int x = 0; x < 3; x++) {
         double px = Mth.lerp(x / 2.0, body.minX, body.maxX);

         for (int z = 0; z < 3; z++) {
            double pz = Mth.lerp(z / 2.0, body.minZ, body.maxZ);

            for (int y = 0; y < 5; y++) {
               points.add(new Vec3(px, Mth.lerp(y / 4.0, body.minY, body.maxY), pz));
            }
         }
      }

      return points;
   }

   @Environment(EnvType.CLIENT)
   private record Cell(BlockPos pos, int shade, double distance) {
   }

   @Environment(EnvType.CLIENT)
   public record Spot(BlockPos pos, BlockPos support, Direction face, Vec3 aim) {
      public boolean standing() {
         return this.support == null;
      }
   }
}
