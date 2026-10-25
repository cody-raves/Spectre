package net.spectre.client.module.impl.combat.crystal;

import java.util.function.LongSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Bypass {
   public static final String OFF = "Off";
   public static final String GRIM_ALDENZ = "Grim-Aldenz";
   public static final float GRIM_GREY_BREAK_RANGE = 3.02F;
   public static final float GRIM_PLACE_RANGE = 4.5F;
   public static final int GRIM_ACTIONS_PER_SECOND = 80;

   private Bypass() {
   }

   public static float clampBreakRange(Bypass.Profile profile, float requested) {
      return profile.grim() ? Math.min(requested, 3.02F) : requested;
   }

   public static float clampPlaceRange(Bypass.Profile profile, float requested) {
      return profile.grim() ? Math.min(requested, 4.5F) : requested;
   }

   public static boolean requiresLook(Bypass.Profile profile) {
      return profile.grim();
   }

   public static boolean allowSameTickPlaceAndBreak(Bypass.Profile profile) {
      return false;
   }

   public static boolean allowMultiPlace(Bypass.Profile profile) {
      return false;
   }

   public static boolean allowMultiBreak(Bypass.Profile profile) {
      return false;
   }

   public static boolean allowAttackWhileUsing(Bypass.Profile profile) {
      return profile.grim();
   }

   public static boolean allowPlaceWhileDigging(Bypass.Profile profile) {
      return profile.grim();
   }

   public static boolean allowGhostPlace(Bypass.Profile profile) {
      return profile.grim();
   }

   public static int minSwitchInterval(Bypass.Profile profile) {
      return profile.grim() ? 1 : 2;
   }

   public static int minDelay(Bypass.Profile profile, int requested) {
      return Math.max(profile.grim() ? 0 : 1, requested);
   }

   public static int confirmTicks(Bypass.Profile profile, int pingWindow) {
      return profile.grim() ? 1 : Math.max(1, pingWindow);
   }

   public static boolean mayRetryCrystal(Bypass.Profile profile, int crystalId, int pendingId, int pendingAge, int window) {
      int wait = confirmTicks(profile, window);
      return crystalId != pendingId || pendingAge >= wait;
   }

   public static float aimYaw(float anchorYaw, float desiredYaw) {
      float delta = desiredYaw - anchorYaw;
      delta %= 360.0F;
      if (delta >= 180.0F) {
         delta -= 360.0F;
      }

      if (delta < -180.0F) {
         delta += 360.0F;
      }

      float yaw = anchorYaw + delta;
      if (yaw == anchorYaw) {
         yaw += 0.01F;
      }

      return yaw;
   }

   public static float aimPitch(float desiredPitch) {
      if (desiredPitch > 90.0F) {
         return 90.0F;
      } else {
         return desiredPitch < -90.0F ? -90.0F : desiredPitch;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Budget {
      private final LongSupplier clock;
      private long second = -1L;
      private int used;

      public Budget() {
         this(System::currentTimeMillis);
      }

      Budget(LongSupplier clock) {
         this.clock = clock;
      }

      public boolean allows(Bypass.Profile profile) {
         this.roll();
         return !profile.grim() || this.used < 80;
      }

      public void note() {
         this.roll();
         this.used++;
      }

      public void reset() {
         this.second = -1L;
         this.used = 0;
      }

      private void roll() {
         long now = this.clock.getAsLong() / 1000L;
         if (now != this.second) {
            this.second = now;
            this.used = 0;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Profile {
      OFF,
      GRIM_ALDENZ;

      public static Bypass.Profile of(String mode) {
         return "Grim-Aldenz".equals(mode) ? GRIM_ALDENZ : OFF;
      }

      public boolean grim() {
         return this == GRIM_ALDENZ;
      }
   }
}
