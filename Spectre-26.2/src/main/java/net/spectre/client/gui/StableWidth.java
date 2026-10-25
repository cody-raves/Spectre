package net.spectre.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class StableWidth {
   static final float SHRINK_DELAY = 1.5F;
   private static final float SHRINK_RATE = 12.0F;
   private static final float SNAP = 0.25F;
   private float current = -1.0F;
   private float narrowerFor;

   float update(float target, float dt) {
      if (!(this.current < 0.0F) && !(target >= this.current)) {
         this.narrowerFor += dt;
         if (this.narrowerFor < 1.5F) {
            return this.current;
         } else {
            this.current = this.current + (target - this.current) * (1.0F - (float)Math.exp(-12.0F * dt));
            if (Math.abs(this.current - target) < 0.25F) {
               this.current = target;
            }

            return this.current;
         }
      } else {
         this.current = target;
         this.narrowerFor = 0.0F;
         return this.current;
      }
   }
}
