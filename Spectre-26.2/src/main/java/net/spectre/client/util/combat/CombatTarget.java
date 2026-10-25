package net.spectre.client.util.combat;

import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.TickEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public final class CombatTarget {
   private static final Object OWNER = new Object();
   private static final long MEMORY_CAP_MS = 30000L;
   private static LivingEntity lastHit;
   private static long lastHitAt;

   private CombatTarget() {
   }

   public static void install() {
      PlayerAttackEntityEvent.TYPE.listen(OWNER, Stage.PRE, EventPriority.LOWEST, event -> {
         if (!event.isCancelled() && event.target instanceof LivingEntity living) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.isSameThread() && living.level() == mc.level) {
               lastHit = living;
               lastHitAt = Util.getMillis();
            }
         }
      });
      TickEvent.TYPE.listen(OWNER, event -> forgetStale());
   }

   private static void forgetStale() {
      LivingEntity entity = lastHit;
      if (entity != null) {
         if (!entity.isAlive() || entity.level() != Minecraft.getInstance().level || Util.getMillis() - lastHitAt > 30000L) {
            lastHit = null;
         }
      }
   }

   public static LivingEntity lastHit(long timeoutMs) {
      LivingEntity entity = lastHit;
      if (entity != null && entity.isAlive() && entity.level() == Minecraft.getInstance().level) {
         return Util.getMillis() - lastHitAt > timeoutMs ? null : entity;
      } else {
         return null;
      }
   }

   public static LivingEntity current(long timeoutMs) {
      LivingEntity hit = lastHit(timeoutMs);
      if (hit != null) {
         return hit;
      } else {
         return Minecraft.getInstance().crosshairPickEntity instanceof LivingEntity living && living.isAlive() ? living : null;
      }
   }
}
