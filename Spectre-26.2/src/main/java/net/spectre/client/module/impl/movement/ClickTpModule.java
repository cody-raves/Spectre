package net.spectre.client.module.impl.movement;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class ClickTpModule extends ClientModule {
   private static final double RAYCAST_DISTANCE = 210.0;
   private static final double BLOCKS_PER_PACKET = 10.0;
   private static final int MAX_PADDING_PACKETS = 19;

   public ClickTpModule() {
      super("click-tp", "Click TP", "Teleports you to the block you click on.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   private void onPostTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      if (player != null && level != null) {
         if (player.getInventory().getSelectedItem().getUseAnimation() == ItemUseAnimation.NONE) {
            if (mc.options.keyUse.isDown()) {
               Camera camera = mc.gameRenderer.mainCamera();
               Vec3 cameraPos = camera.position();
               Vec3 direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
               FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
               boolean freecamOn = freecam != null && freecam.isEnabled();
               HitResult crosshair = freecamOn ? pickFromCamera(player, level, cameraPos, direction) : mc.hitResult;
               if (crosshair != null) {
                  if (crosshair.getType() == Type.ENTITY
                     && player.interactOn(((EntityHitResult)crosshair).getEntity(), InteractionHand.MAIN_HAND, crosshair.getLocation()) != InteractionResult.PASS) {
                     return;
                  }

                  if (crosshair.getType() == Type.BLOCK && player.getMainHandItem().getItem() instanceof BlockItem) {
                     return;
                  }
               }

               BlockHitResult hit = level.clip(new ClipContext(cameraPos, cameraPos.add(direction.scale(210.0)), Block.OUTLINE, Fluid.NONE, player));
               if (hit.getType() == Type.BLOCK) {
                  if (hit.isInside() && !freecamOn) {
                     FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
                     if (freelook != null && freelook.isEnabled()) {
                        return;
                     }
                  }

                  BlockPos pos = hit.getBlockPos();
                  Direction side = hit.getDirection();
                  if (level.getBlockState(pos).useWithoutItem(level, player, hit) == InteractionResult.PASS) {
                     BlockState state = level.getBlockState(pos);
                     VoxelShape shape = state.getCollisionShape(level, pos);
                     if (shape.isEmpty()) {
                        shape = state.getShape(level, pos);
                     }

                     double height = shape.isEmpty() ? 1.0 : shape.max(Axis.Y);
                     Vec3 newPos = new Vec3(pos.getX() + 0.5 + side.getStepX(), pos.getY() + height, pos.getZ() + 0.5 + side.getStepZ());
                     int packetsRequired = (int)Math.ceil(player.position().distanceTo(newPos) / 10.0) - 1;
                     if (packetsRequired > 19) {
                        packetsRequired = 0;
                     }

                     for (int i = 0; i < packetsRequired; i++) {
                        player.connection.send(new StatusOnly(true, true));
                     }

                     player.connection.send(new Pos(newPos.x, newPos.y, newPos.z, true, true));
                     player.setPos(newPos);
                  }
               }
            }
         }
      }
   }

   private static HitResult pickFromCamera(LocalPlayer player, ClientLevel level, Vec3 eye, Vec3 dir) {
      double blockRange = player.blockInteractionRange();
      double entityRange = player.entityInteractionRange();
      double reach = Math.max(blockRange, entityRange);
      double reachSq = reach * reach;
      BlockHitResult blockHit = level.clip(new ClipContext(eye, eye.add(dir.scale(reach)), Block.OUTLINE, Fluid.NONE, player));
      double blockDistSq = blockHit.getLocation().distanceToSqr(eye);
      if (blockHit.getType() != Type.MISS) {
         reachSq = blockDistSq;
         reach = Math.sqrt(blockDistSq);
      }

      Vec3 end = eye.add(dir.scale(reach));
      AABB searchBox = new AABB(eye, end).inflate(1.0);
      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox, EntitySelector.CAN_BE_PICKED, reachSq);
      if (entityHit != null && entityHit.getLocation().distanceToSqr(eye) < blockDistSq) {
         return entityHit.getLocation().closerThan(eye, entityRange) ? entityHit : null;
      } else {
         return blockHit.getLocation().closerThan(eye, blockRange) ? blockHit : null;
      }
   }
}
