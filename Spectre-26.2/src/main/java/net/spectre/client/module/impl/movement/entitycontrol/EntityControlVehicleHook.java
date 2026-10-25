package net.spectre.client.module.impl.movement.entitycontrol;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.movement.BoatFlyModule;
import net.spectre.client.module.impl.movement.BoatNoclipModule;
import net.spectre.client.module.impl.movement.EntityControlModule;
import net.spectre.client.module.impl.movement.EntitySpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class EntityControlVehicleHook {
   private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);
   public static volatile boolean anyActive;

   private EntityControlVehicleHook() {
   }

   public static void refresh() {
      anyActive = EntityControlModule.active != null || EntitySpeedModule.active != null || BoatFlyModule.active != null || BoatNoclipModule.active != null;
   }

   public static Vec3 onEntityMove(Entity entity, MoverType type, Vec3 movement) {
      if (type == MoverType.SELF && entity.level().isClientSide()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.getVehicle() == entity && entity.getControllingPassenger() == player) {
            Vec3 velocity = entity.getDeltaMovement();
            BoatNoclipModule noclip = BoatNoclipModule.active;
            if (noclip != null && entity instanceof AbstractBoat boat) {
               Vec3 result = noclip.boatMovement(boat, player);
               entity.setYRot(player.getYRot());
               if (movement == velocity) {
                  entity.setDeltaMovement(result);
               }

               return result;
            } else {
               EntityControlModule control = EntityControlModule.active;
               EntitySpeedModule speed = EntitySpeedModule.active;
               BoatFlyModule fly = BoatFlyModule.active;
               double velX = velocity.x;
               double velY = velocity.y;
               double velZ = velocity.z;
               boolean overridden = false;
               if (speed != null) {
                  if (speed.appliesTo(entity)) {
                     Vec3 horizontal = horizontalVelocity(player, speed.horizontalSpeed());
                     velX = horizontal.x;
                     velZ = horizontal.z;
                     overridden = true;
                  }
               } else if (fly != null) {
                  Vec3 horizontal = horizontalVelocity(player, fly.horizontalSpeed());
                  velX = horizontal.x;
                  velZ = horizontal.z;
                  overridden = true;
               }

               if (fly != null) {
                  velY = fly.verticalVelocity();
                  overridden = true;
               }

               if (fly != null || control != null && control.locksYaw(entity)) {
                  entity.setYRot(player.getYRot());
               }

               if (!overridden) {
                  return movement;
               } else {
                  Vec3 result = new Vec3(velX, velY, velZ);
                  if (movement == velocity) {
                     entity.setDeltaMovement(result);
                  }

                  return result;
               }
            }
         } else {
            return movement;
         }
      } else {
         return movement;
      }
   }

   public static boolean locksBoatTurning(AbstractBoat boat) {
      if (!anyActive || !boat.level().isClientSide()) {
         return false;
      } else if (BoatFlyModule.active == null && BoatNoclipModule.active == null) {
         EntityControlModule control = EntityControlModule.active;
         return control != null && control.locksYaw(boat);
      } else {
         return true;
      }
   }

   public static Vec3 horizontalVelocity(LocalPlayer player, double bps) {
      float yaw = player.getYRot();
      Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
      Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
      Input input = player.input.keyPresses;
      double velX = 0.0;
      double velZ = 0.0;
      boolean longitudinal = false;
      if (input.forward()) {
         velX += forward.x / 20.0 * bps;
         velZ += forward.z / 20.0 * bps;
         longitudinal = true;
      }

      if (input.backward()) {
         velX -= forward.x / 20.0 * bps;
         velZ -= forward.z / 20.0 * bps;
         longitudinal = true;
      }

      boolean lateral = false;
      if (input.right()) {
         velX += right.x / 20.0 * bps;
         velZ += right.z / 20.0 * bps;
         lateral = true;
      }

      if (input.left()) {
         velX -= right.x / 20.0 * bps;
         velZ -= right.z / 20.0 * bps;
         lateral = true;
      }

      if (longitudinal && lateral) {
         velX *= DIAGONAL;
         velZ *= DIAGONAL;
      }

      return new Vec3(velX, 0.0, velZ);
   }

   public static boolean isPhysicallyDown(KeyMapping mapping) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui.screen() == null && !mapping.isUnbound() && !isFreecamActive()) {
         Key key = KeyMappingHelper.getBoundKeyOf(mapping);
         Type type = key.getType();
         if (type == Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
         } else {
            return type == Type.MOUSE ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == 1 : mapping.isDown();
         }
      } else {
         return false;
      }
   }

   public static boolean isOnAir(Entity entity) {
      return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0)).allMatch(BlockStateBase::isAir);
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }
}
