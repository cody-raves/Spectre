package net.spectre.client.util.seedscan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class WgRandom {
   private long lo;
   private long hi;

   WgRandom() {
   }

   private WgRandom(long lo, long hi) {
      this.lo = lo;
      this.hi = hi;
   }

   WgRandom copy() {
      return new WgRandom(this.lo, this.hi);
   }

   boolean sameState(WgRandom other) {
      return this.lo == other.lo && this.hi == other.hi;
   }

   long stateHash() {
      return this.lo * 31L + this.hi;
   }

   private static long mix(long l) {
      l = (l ^ l >>> 30) * -4658895280553007687L;
      l = (l ^ l >>> 27) * -7723592293110705685L;
      return l ^ l >>> 31;
   }

   void setSeed(long seed) {
      long m = seed ^ 7640891576956012809L;
      long n = m + -7046029254386353131L;
      this.lo = mix(m);
      this.hi = mix(n);
      if ((this.lo | this.hi) == 0L) {
         this.lo = -7046029254386353131L;
         this.hi = 7640891576956012809L;
      }
   }

   private long step() {
      long l = this.lo;
      long m = this.hi;
      long n = Long.rotateLeft(l + m, 17) + l;
      m ^= l;
      this.lo = Long.rotateLeft(l, 49) ^ m ^ m << 21;
      this.hi = Long.rotateLeft(m, 28);
      return n;
   }

   void skip(int steps) {
      for (int i = 0; i < steps; i++) {
         this.step();
      }
   }

   int next(int bits) {
      return (int)(this.step() >>> 64 - bits);
   }

   int nextInt(int bound) {
      if ((bound & bound - 1) == 0) {
         return (int)((long)bound * this.next(31) >> 31);
      } else {
         int j;
         int k;
         do {
            j = this.next(31);
            k = j % bound;
         } while (j - k + (bound - 1) < 0);

         return k;
      }
   }

   int between(int min, int max) {
      return this.nextInt(max - min + 1) + min;
   }

   long nextLong() {
      int i = this.next(32);
      int j = this.next(32);
      return ((long)i << 32) + j;
   }

   float nextFloat() {
      return this.next(24) * 5.9604645E-8F;
   }

   double nextDouble() {
      int i = this.next(26);
      int j = this.next(27);
      return (((long)i << 27) + j) * 1.110223E-16F;
   }

   long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
      this.setSeed(worldSeed);
      long m = this.nextLong() | 1L;
      long n = this.nextLong() | 1L;
      long seed = blockX * m + blockZ * n ^ worldSeed;
      this.setSeed(seed);
      return seed;
   }

   void setFeatureSeed(long decorationSeed, int index, int step) {
      this.setSeed(decorationSeed + index + 10000L * step);
   }
}
