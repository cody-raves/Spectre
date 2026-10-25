package net.spectre.client.module.impl.movement.elytrafly;

import net.spectre.client.event.impl.PlayerMoveEvent;
import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public abstract class ElytraFlightMode {
   protected final Minecraft mc = Minecraft.getInstance();
   protected final ElytraFlyModule elytraFly;
   protected boolean lastJumpPressed;
   protected boolean incrementJumpTimer;
   public boolean lastForwardPressed;
   protected int jumpTimer;
   public double velX;
   public double velY;
   public double velZ;
   protected double ticksLeft;
   public Vec3 forward;
   public Vec3 right;
   protected double acceleration;

   protected ElytraFlightMode(ElytraFlyModule elytraFly) {
      this.elytraFly = elytraFly;
   }

   public void onTick() {
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (this.elytraFly.autoReplenish.get()) {
            int fireworks = ElytraFlyInventory.find(stack -> stack.getItem() == Items.FIREWORK_ROCKET);
            if (fireworks != -1 && !ElytraFlyInventory.isHotbar(fireworks)) {
               ElytraFlyInventory.moveToHotbar(fireworks, this.elytraFly.replenishSlot.getInt() - 1);
            }
         }

         if (this.elytraFly.replace.get()) {
            ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
            if (chestStack.getItem() == Items.ELYTRA) {
               int threshold = this.elytraFly.replaceDurability.getInt();
               if (chestStack.getMaxDamage() - chestStack.getDamageValue() <= threshold) {
                  int elytra = ElytraFlyInventory.find(stack -> stack.getMaxDamage() - stack.getDamageValue() > threshold && stack.getItem() == Items.ELYTRA);
                  ElytraFlyInventory.moveToChest(elytra);
               }
            }
         }
      }
   }

   public void onPreTick() {
   }

   public void onPacketSend(Packet<?> packet) {
   }

   public void onPositionPacket() {
   }

   public void onPlayerMove() {
   }

   public void onActivate() {
      this.lastJumpPressed = false;
      this.jumpTimer = 0;
      this.ticksLeft = 0.0;
      this.acceleration = 0.0;
   }

   public void onDeactivate() {
   }

   public void autoTakeoff() {
      LocalPlayer player = this.mc.player;
      if (this.incrementJumpTimer) {
         this.jumpTimer++;
      }

      boolean jumpPressed = this.mc.options.keyJump.isDown();
      ElytraFlightModes mode = this.elytraFly.flightModeType();
      if (this.elytraFly.autoTakeOff.get() && mode != ElytraFlightModes.Pitch40 && mode != ElytraFlightModes.Bounce
         || !this.elytraFly.manualTakeoff.get() && mode == ElytraFlightModes.Bounce && jumpPressed) {
         if (!this.lastJumpPressed && !player.isFallFlying()) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = true;
         }

         if (this.jumpTimer >= 8) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = false;
            player.setJumping(false);
            player.setSprinting(true);
            player.jumpFromGround();
            player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
         }
      }

      this.lastJumpPressed = jumpPressed;
   }

   public void handleAutopilot() {
      LocalPlayer player = this.mc.player;
      if (player.isFallFlying()) {
         if (this.elytraFly.autoPilot.get()
            && player.getY() > this.elytraFly.autoPilotMinimumHeight.get()
            && this.elytraFly.flightModeType() != ElytraFlightModes.Bounce) {
            this.mc.options.keyUp.setDown(true);
            this.lastForwardPressed = true;
         }

         if (this.elytraFly.useFireworks.get()) {
            if (this.ticksLeft <= 0.0) {
               this.ticksLeft = this.elytraFly.autoPilotFireworkDelay.get() * 20.0F;
               int slot = ElytraFlyInventory.findInHotbar(stack -> stack.getItem() == Items.FIREWORK_ROCKET);
               if (slot == -1 || this.mc.gameMode == null) {
                  return;
               }

               if (slot == 40) {
                  this.mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                  player.swing(InteractionHand.OFF_HAND);
               } else {
                  ElytraFlyInventory.swap(slot, true);
                  this.mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                  player.swing(InteractionHand.MAIN_HAND);
                  ElytraFlyInventory.swapBack();
               }
            }

            this.ticksLeft--;
         }
      }
   }

   public void handleHorizontalSpeed(PlayerMoveEvent event) {
      boolean a = false;
      boolean b = false;
      if (this.mc.options.keyUp.isDown()) {
         this.velX = this.velX + this.forward.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ + this.forward.z * this.getSpeed() * 10.0;
         a = true;
      } else if (this.mc.options.keyDown.isDown()) {
         this.velX = this.velX - this.forward.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ - this.forward.z * this.getSpeed() * 10.0;
         a = true;
      }

      if (this.mc.options.keyRight.isDown()) {
         this.velX = this.velX + this.right.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ + this.right.z * this.getSpeed() * 10.0;
         b = true;
      } else if (this.mc.options.keyLeft.isDown()) {
         this.velX = this.velX - this.right.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ - this.right.z * this.getSpeed() * 10.0;
         b = true;
      }

      if (a && b) {
         double diagonal = 1.0 / Math.sqrt(2.0);
         this.velX *= diagonal;
         this.velZ *= diagonal;
      }
   }

   public void handleVerticalSpeed(PlayerMoveEvent event) {
      if (this.mc.options.keyJump.isDown()) {
         this.velY = this.velY + 0.5 * this.elytraFly.verticalSpeed.get();
      } else if (this.mc.options.keyShift.isDown()) {
         this.velY = this.velY - 0.5 * this.elytraFly.verticalSpeed.get();
      }
   }

   public void handleFallMultiplier() {
      if (this.velY < 0.0) {
         this.velY = this.velY * this.elytraFly.fallMultiplier.get();
      } else if (this.velY > 0.0) {
         this.velY = 0.0;
      }
   }

   public void handleAcceleration() {
      if (this.elytraFly.acceleration.get()) {
         if (!this.isMoving()) {
            this.acceleration = 0.0;
         }

         this.acceleration = Math.min(
            this.acceleration + this.elytraFly.accelerationMin.get() + this.elytraFly.accelerationStep.get() * 0.1,
            (double)this.elytraFly.horizontalSpeed.get()
         );
      } else {
         this.acceleration = 0.0;
      }
   }

   public void zeroAcceleration() {
      this.acceleration = 0.0;
   }

   protected double getSpeed() {
      return this.elytraFly.acceleration.get() ? this.acceleration : this.elytraFly.horizontalSpeed.get();
   }

   private boolean isMoving() {
      LocalPlayer player = this.mc.player;
      return player.zza != 0.0F || player.xxa != 0.0F;
   }
}
