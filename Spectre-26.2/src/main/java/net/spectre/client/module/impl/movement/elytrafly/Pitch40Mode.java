package net.spectre.client.module.impl.movement.elytrafly;

import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.impl.PlayerMoveEvent;
import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class Pitch40Mode extends ElytraFlightMode {
   private static final int MIN_START_ABOVE_LOWER = 40;
   private boolean pitchingDown = true;
   private float pitch;

   public Pitch40Mode(ElytraFlyModule elytraFly) {
      super(elytraFly);
   }

   @Override
   public void onActivate() {
      LocalPlayer player = this.mc.player;
      if (player.getY() < this.elytraFly.pitch40upperBounds.get()) {
         ModuleChat.send(
            this.elytraFly,
            ModuleChat.body()
               .text("Pitch40 ")
               .danger("can't start")
               .text(", you must be above the upper bound (Y ")
               .value(this.elytraFly.pitch40upperBounds.getInt())
               .text(")")
         );
         this.elytraFly.disableFromMode();
      } else if (player.getY() - 40.0 < this.elytraFly.pitch40lowerBounds.get()) {
         ModuleChat.send(
            this.elytraFly,
            ModuleChat.body()
               .text("Pitch40 ")
               .danger("can't start")
               .text(", you must be at least ")
               .value(40)
               .text(" blocks above the lower bound (Y ")
               .value(this.elytraFly.pitch40lowerBounds.getInt())
               .text(")")
         );
         this.elytraFly.disableFromMode();
      }

      this.pitch = 37.72F;
   }

   private float randPitch(float pitch, float bound) {
      return (float)(pitch + bound * (Math.random() - 0.5));
   }

   @Override
   public void onTick() {
      super.onTick();
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (this.pitchingDown && player.getY() <= this.elytraFly.pitch40lowerBounds.get()) {
            this.pitchingDown = false;
         } else if (!this.pitchingDown && player.getY() >= this.elytraFly.pitch40upperBounds.get()) {
            this.pitchingDown = true;
         }

         if (!this.pitchingDown) {
            this.pitch = this.pitch - this.randPitch(this.elytraFly.pitch40rotationSpeedUp.get(), 1.0F);
            if (this.pitch < -54.77F) {
               this.pitch = -54.77F;
               this.pitchingDown = true;
            }
         } else if (this.pitch < 37.72F) {
            this.pitch = this.pitch + this.randPitch(this.elytraFly.pitch40rotationSpeedDown.get(), 0.5F);
         }

         player.setXRot(this.pitch);
      }
   }

   @Override
   public void autoTakeoff() {
   }

   @Override
   public void handleHorizontalSpeed(PlayerMoveEvent event) {
      this.velX = event.x;
      this.velZ = event.z;
   }

   @Override
   public void handleVerticalSpeed(PlayerMoveEvent event) {
   }

   @Override
   public void handleFallMultiplier() {
   }

   @Override
   public void handleAutopilot() {
   }
}
