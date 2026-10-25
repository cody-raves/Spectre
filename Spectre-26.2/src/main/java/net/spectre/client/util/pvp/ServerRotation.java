package net.spectre.client.util.pvp;

import net.spectre.client.module.impl.combat.crystal.Bypass;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

@Environment(EnvType.CLIENT)
public final class ServerRotation {
   private static Rotation requested;
   private static Rotation sent;
   private static float ownYaw;
   private static float ownPitch;
   private static boolean swapped;
   private static double sentX;
   private static double sentZ;
   private static boolean hasSentPos;
   private static Rotation walked;

   private ServerRotation() {
   }

   public static void request(Rotation rotation) {
      requested = rotation;
   }

   public static Rotation sent() {
      return sent;
   }

   public static void apply(LocalPlayer player) {
      if (requested != null && !swapped) {
         ownYaw = player.getYRot();
         ownPitch = player.getXRot();
         float anchor = sent != null ? sent.yaw() : ownYaw;
         player.setYRot(Bypass.aimYaw(anchor, requested.yaw()));
         player.setXRot(Bypass.aimPitch(requested.pitch()));
         swapped = true;
      }
   }

   public static void applyForMovement(LocalPlayer player) {
      if (requested != null && !swapped && correctable(player)) {
         if (player.connection.hasClientLoaded() && !player.isPassenger()) {
            apply(player);
            walked = new Rotation(player.getYRot(), player.getXRot());
         }
      }
   }

   public static boolean correctable(LocalPlayer player) {
      return !player.isFallFlying() && !player.isAutoSpinAttack();
   }

   public static Input correctInput(Input keys) {
      if (walked == null) {
         return keys;
      } else {
         int forward = impulse(keys.forward(), keys.backward());
         int strafe = impulse(keys.left(), keys.right());
         if (forward == 0 && strafe == 0) {
            return keys;
         } else {
            double[] wanted = heading(ownYaw, strafe, forward);
            int bestForward = forward;
            int bestStrafe = strafe;
            double bestDot = Double.NEGATIVE_INFINITY;

            for (int f = -1; f <= 1; f++) {
               for (int s = -1; s <= 1; s++) {
                  if (f != 0 || s != 0) {
                     double[] way = heading(walked.yaw(), s, f);
                     double dot = way[0] * wanted[0] + way[1] * wanted[1];
                     if (dot > bestDot) {
                        bestDot = dot;
                        bestForward = f;
                        bestStrafe = s;
                     }
                  }
               }
            }

            return bestForward == forward && bestStrafe == strafe
               ? keys
               : new Input(bestForward > 0, bestForward < 0, bestStrafe > 0, bestStrafe < 0, keys.jump(), keys.shift(), keys.sprint());
         }
      }
   }

   public static void finishTick(LocalPlayer player) {
      if (swapped) {
         restore(player);
      }

      requested = null;
   }

   private static int impulse(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }

   private static double[] heading(float yaw, int strafe, int forward) {
      double sin = Mth.sin(yaw * (float) (Math.PI / 180.0));
      double cos = Mth.cos(yaw * (float) (Math.PI / 180.0));
      double x = strafe * cos - forward * sin;
      double z = forward * cos + strafe * sin;
      double length = Math.sqrt(x * x + z * z);
      return new double[]{x / length, z / length};
   }

   public static boolean yawFree(LocalPlayer player) {
      Input keys = player.input.keyPresses;
      if (keys.forward() || keys.backward() || keys.left() || keys.right()) {
         return false;
      } else {
         return keys.jump() && player.isSprinting() ? false : !player.isFallFlying() && !player.isAutoSpinAttack();
      }
   }

   public static void beforeSend(LocalPlayer player) {
      if (!swapped && requested != null) {
         if (!yawFree(player)) {
            requested = null;
         } else {
            ownYaw = player.getYRot();
            ownPitch = player.getXRot();
            float anchor = sent != null ? sent.yaw() : ownYaw;
            player.setYRot(Bypass.aimYaw(anchor, requested.yaw()));
            player.setXRot(Bypass.aimPitch(requested.pitch()));
            swapped = true;
         }
      }
   }

   public static void afterSend(LocalPlayer player) {
      sentX = player.getX();
      sentZ = player.getZ();
      hasSentPos = true;
      sent = new Rotation(player.getYRot(), player.getXRot());
      requested = null;
      if (swapped) {
         restore(player);
      }
   }

   private static void restore(LocalPlayer player) {
      if (walked != null) {
         player.yBob = player.yBob + (ownYaw - walked.yaw()) * 0.5F;
         player.xBob = player.xBob + (ownPitch - walked.pitch()) * 0.5F;
         walked = null;
      }

      player.setYRot(ownYaw);
      player.setXRot(ownPitch);
      swapped = false;
   }
}
