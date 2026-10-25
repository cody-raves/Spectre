package net.spectre.client.util.staff;

import java.util.ArrayDeque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;

@Environment(EnvType.CLIENT)
public final class StaffAlarm {
   public static final String SIREN = "Siren";
   public static final String BEEP = "Beep";
   public static final String BELL = "Bell";
   public static final String GUARDIAN = "Guardian";
   public static final String OFF = "Off";
   private static final int SIREN_INTERVAL = 4;
   private static final float SIREN_HIGH = 1.5F;
   private static final float SIREN_LOW = 1.0F;
   private static final int BEEP_INTERVAL = 3;
   private static final float BEEP_PITCH = 1.6F;
   private static final int BELL_INTERVAL = 10;
   private static final int GUARDIAN_INTERVAL = 60;
   private static final int MAX_RINGING = 8;
   private String mode = "Off";
   private float volume;
   private int ticksLeft;
   private int step;
   private final ArrayDeque<SoundInstance> ringing = new ArrayDeque<>(8);

   public void start(String mode, float seconds, float volume) {
      if (mode != null && !"Off".equals(mode) && !(volume <= 0.0F) && !(seconds <= 0.0F)) {
         int ticks = Math.max(1, Math.round(seconds * 20.0F));
         this.volume = Math.min(1.0F, volume);
         if (this.isPlaying() && mode.equals(this.mode)) {
            this.ticksLeft = Math.max(this.ticksLeft, ticks);
         } else {
            this.stop();
            this.mode = mode;
            this.ticksLeft = ticks;
            this.step = 0;
         }
      }
   }

   public void tick() {
      if (this.ticksLeft > 0) {
         int interval = interval(this.mode);
         if (this.step % interval == 0) {
            this.play(this.step / interval);
         }

         this.step++;
         if (--this.ticksLeft == 0) {
            this.step = 0;
         }
      }
   }

   public void stop() {
      this.ticksLeft = 0;
      this.step = 0;
      if (!this.ringing.isEmpty()) {
         SoundManager sounds = Minecraft.getInstance().getSoundManager();
         if (sounds != null) {
            this.ringing.forEach(sounds::stop);
         }

         this.ringing.clear();
      }
   }

   public boolean isPlaying() {
      return this.ticksLeft > 0;
   }

   private void play(int note) {
      SoundManager sounds = Minecraft.getInstance().getSoundManager();
      if (sounds == null) return;
      SoundEvent sound;
      float pitch;
      switch (this.mode) {
         case "Beep" -> { sound = SoundEvents.NOTE_BLOCK_BIT.value(); pitch = 1.6F; }
         case "Bell" -> { sound = SoundEvents.BELL_BLOCK; pitch = 1.0F; }
         case "Guardian" -> { sound = SoundEvents.ELDER_GUARDIAN_CURSE; pitch = 1.0F; }
         default -> { sound = SoundEvents.NOTE_BLOCK_PLING.value(); pitch = note % 2 == 0 ? 1.5F : 1.0F; }
      }
      SimpleSoundInstance instance = new SimpleSoundInstance(sound.location(), SoundSource.MASTER,
         this.volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
         SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
      if (this.ringing.size() >= 8) this.ringing.removeFirst();
      this.ringing.addLast(instance);
      sounds.play(instance);
   }

   private static int interval(String mode) {
      return switch (mode) {
         case "Beep" -> 3;
         case "Bell" -> 10;
         case "Guardian" -> 60;
         default -> 4;
      };
   }
}
