package net.spectre.client.util.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class PlaybackClock {
   static final long SNAP_MS = 1200L;
   static final long CATCHUP_MS = 1500L;
   private NowPlaying sample;
   private boolean running;
   private long durationMs;
   private double rate = 1.0;
   private double base;
   private long baseAt;
   private double correction;

   public void update(NowPlaying now, long nowMs) {
      if (now != this.sample) {
         NowPlaying previous = this.sample;
         this.sample = now;
         if (now != null && now.hasTimeline()) {
            double shown = this.position(nowMs);
            double target = now.positionAt(nowMs);
            boolean continuous = previous != null
               && previous.hasTimeline()
               && previous.sameTrack(now)
               && this.running
               && now.playing()
               && Math.abs(target - shown) < 1200.0 * Math.min(1.0, now.rate());
            this.base = continuous ? shown : target;
            this.correction = continuous ? target - shown : 0.0;
            this.baseAt = nowMs;
            this.running = now.playing();
            this.durationMs = now.durationMs();
            this.rate = now.rate();
         } else {
            this.running = false;
            this.durationMs = 0L;
            this.base = 0.0;
            this.correction = 0.0;
            this.baseAt = nowMs;
         }
      }
   }

   public long positionAt(long nowMs) {
      return this.durationMs <= 0L ? 0L : Math.max(0L, Math.min(this.durationMs, Math.round(this.position(nowMs))));
   }

   private double position(long nowMs) {
      if (this.durationMs <= 0L) {
         return 0.0;
      } else if (!this.running) {
         return this.base;
      } else {
         double elapsed = Math.max(0L, nowMs - this.baseAt);
         return this.base + elapsed * this.rate + this.correction * Math.min(1.0, elapsed / 1500.0);
      }
   }
}
