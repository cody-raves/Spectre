package net.spectre.client.module.impl.movement.elytrafly;

import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class BounceMode extends ElytraFlightMode {
   private boolean rubberbanded = false;
   private int tickDelay;
   private double prevFov;

   public BounceMode(ElytraFlyModule elytraFly) {
      super(elytraFly);
      this.tickDelay = elytraFly.restartDelay.getInt();
   }

   @Override
   public void onTick() {
      super.onTick();
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (this.mc.options.keyJump.isDown() && !player.isFallFlying() && !this.elytraFly.manualTakeoff.get()) {
            sendStartFallFlying(player);
         }

         if (checkConditions(player)) {
            if (!this.rubberbanded) {
               if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
                  this.mc.options.fovEffectScale().set(0.0);
               }

               if (this.elytraFly.autoJump.get()) {
                  this.mc.options.keyJump.setDown(true);
               }

               this.mc.options.keyUp.setDown(true);
               player.setYRot(this.getYawDirection(player));
               if (this.elytraFly.lockPitch.get()) {
                  player.setXRot(this.elytraFly.pitch.get());
               }
            }

            if (!this.elytraFly.sprint.get()) {
               if (player.isFallFlying()) {
                  player.setSprinting(player.onGround());
               } else {
                  player.setSprinting(true);
               }
            }

            if (this.rubberbanded && this.elytraFly.restart.get()) {
               if (this.tickDelay > 0) {
                  this.tickDelay--;
               } else {
                  sendStartFallFlying(player);
                  this.rubberbanded = false;
                  this.tickDelay = this.elytraFly.restartDelay.getInt();
               }
            }
         }
      }
   }

   @Override
   public void onPreTick() {
      super.onPreTick();
      LocalPlayer player = this.mc.player;
      if (player != null && checkConditions(player) && this.elytraFly.sprint.get()) {
         player.setSprinting(true);
      }
   }

   private void unpress() {
      this.mc.options.keyUp.setDown(false);
      if (this.elytraFly.autoJump.get()) {
         this.mc.options.keyJump.setDown(false);
      }
   }

   @Override
   public void onPositionPacket() {
      this.rubberbanded = true;
      LocalPlayer player = this.mc.player;
      if (player != null) {
         player.stopFallFlying();
      }
   }

   @Override
   public void onPacketSend(Packet<?> packet) {
      if (packet instanceof ServerboundPlayerCommandPacket command && command.getAction() == Action.START_FALL_FLYING && !this.elytraFly.sprint.get()) {
         this.mc.player.setSprinting(true);
      }
   }

   public static boolean recastElytra(LocalPlayer player) {
      if (checkConditions(player) && startGliding(player)) {
         sendStartFallFlying(player);
         return true;
      } else {
         return false;
      }
   }

   public static boolean checkConditions(LocalPlayer player) {
      BlockState blockState = player.getInBlockState();
      boolean isClimbing = blockState.is(BlockTags.CLIMBABLE) && !blockState.is(BlockTags.CAN_GLIDE_THROUGH);
      return !player.getAbilities().flying && !player.isPassenger() && !isClimbing && !player.isInWater() && !player.hasEffect(MobEffects.LEVITATION);
   }

   private static boolean startGliding(LocalPlayer player) {
      for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
         if (LivingEntity.canGlideUsing(player.getItemBySlot(equipmentSlot), equipmentSlot)) {
            Minecraft.getInstance().executeBlocking(player::startFallFlying);
            return true;
         }
      }

      return false;
   }

   private static void sendStartFallFlying(LocalPlayer player) {
      player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
   }

   private float getYawDirection(LocalPlayer player) {
      String var2 = this.elytraFly.yawLockMode.get();

      return switch (var2) {
         case "Smart" -> Math.round((player.getYRot() + 1.0F) / 45.0F) * 45.0F;
         case "Simple" -> this.elytraFly.yaw.get();
         default -> player.getYRot();
      };
   }

   @Override
   public void onActivate() {
      this.prevFov = this.mc.options.fovEffectScale().get();
   }

   @Override
   public void onDeactivate() {
      this.unpress();
      this.rubberbanded = false;
      if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
         this.mc.options.fovEffectScale().set(this.prevFov);
      }
   }
}
