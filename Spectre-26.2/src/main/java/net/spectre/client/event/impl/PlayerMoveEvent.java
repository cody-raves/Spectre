package net.spectre.client.event.impl;

import java.util.function.Consumer;
import net.spectre.client.event.CancellableEvent;
import net.spectre.client.event.PooledCancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PlayerMoveEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PlayerMoveEvent> TYPE = new PooledCancellableEventType<>("PlayerMoveEvent", PlayerMoveEvent::new, 16);
   public double x;
   public double y;
   public double z;

   private PlayerMoveEvent() {
   }

   public static void move(Vec3 movement, Consumer<Vec3> original) {
      if (!TYPE.hasListeners()) {
         original.accept(movement);
      } else {
         PlayerMoveEvent event = TYPE.pool.acquire();
         event.x = movement.x;
         event.y = movement.y;
         event.z = movement.z;
         event.resetCancellation();

         try {
            TYPE.dispatchPre(event);
            if (event.isCancelled()) {
               return;
            }

            if (Double.isFinite(event.x) && Double.isFinite(event.y) && Double.isFinite(event.z)) {
               Vec3 changed = event.x == movement.x && event.y == movement.y && event.z == movement.z ? movement : new Vec3(event.x, event.y, event.z);
               original.accept(changed);
               TYPE.dispatchPost(event);
               return;
            }
         } finally {
            TYPE.pool.release(event);
         }
      }
   }
}
