package net.spectre.client.util.playerbypass;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class ChunkEntryGuard {
   private final LongOpenHashSet waiting = new LongOpenHashSet();
   private boolean inChunk;
   private long chunk;
   private boolean entryPending;

   void reset() {
      this.inChunk = false;
      this.entryPending = false;
      this.waiting.clear();
   }

   void update(long playerChunk, LongPredicate arrived, LongPredicate flagged, ProtectedChunkStore store) {
      if (!this.inChunk || playerChunk != this.chunk) {
         this.inChunk = true;
         this.chunk = playerChunk;
         this.entryPending = true;
         this.waiting.clear();
      }

      if (this.entryPending) {
         if (arrived.test(playerChunk)) {
            this.entryPending = false;
            if (!flagged.test(playerChunk)) {
               ProtectedChunkStore.forEachInArea(ProtectedChunkStore.chunkX(playerChunk), ProtectedChunkStore.chunkZ(playerChunk), keyx -> {
                  if (!arrived.test(keyx)) {
                     this.waiting.add(keyx);
                  } else if (!flagged.test(keyx)) {
                     store.protect(keyx);
                  }
               });
            }
         }
      } else if (!this.waiting.isEmpty()) {
         LongIterator keys = this.waiting.iterator();

         while (keys.hasNext()) {
            long key = keys.nextLong();
            if (arrived.test(key)) {
               keys.remove();
               if (!flagged.test(key)) {
                  store.protect(key);
               }
            }
         }
      }
   }
}
