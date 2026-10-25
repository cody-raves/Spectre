package net.spectre.client.util.quantum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

@Environment(EnvType.CLIENT)
public final class ChunkSnapshotStore {
   public static final int MIN_SECTION_INDEX = 6;
   public static final int MAX_SECTION_INDEX = 23;
   private static final Strategy<BlockState> STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
   private final Map<Long, PalettedContainer<BlockState>[]> snapshots = new HashMap<>();
   private final BlockState air;

   public ChunkSnapshotStore(BlockState air) {
      this.air = air;
   }

   public static long chunkKey(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | chunkZ & 4294967295L;
   }

   public void store(int chunkX, int chunkZ, PalettedContainer<BlockState>[] sections) {
      this.snapshots.put(chunkKey(chunkX, chunkZ), sections);
   }

   public void retain(LongPredicate keep) {
      this.snapshots.keySet().removeIf(key -> !keep.test(key));
   }

   public void clear() {
      this.snapshots.clear();
   }

   public void update(int chunkX, int chunkZ, int sectionIndex, int localX, int localY, int localZ, BlockState state) {
      if (sectionIndex >= 6 && sectionIndex <= 23) {
         PalettedContainer<BlockState>[] sections = this.snapshots.get(chunkKey(chunkX, chunkZ));
         if (sections != null && sectionIndex < sections.length) {
            PalettedContainer<BlockState> section = sections[sectionIndex];
            if (section == null) {
               if (state == this.air) {
                  return;
               }

               section = new PalettedContainer<>(this.air, STRATEGY);
               sections[sectionIndex] = section;
            }

            section.set(localX, localY, localZ, state);
         }
      }
   }

   public List<ChunkSnapshotStore.BlockChange> compare(int chunkX, int chunkZ, PalettedContainer<BlockState>[] current, int minSectionY) {
      List<ChunkSnapshotStore.BlockChange> changes = new ArrayList<>();
      PalettedContainer<BlockState>[] old = this.snapshots.get(chunkKey(chunkX, chunkZ));
      if (this.isAllAir(old)) {
         return changes;
      } else {
         for (int index = 6; index <= 23; index++) {
            PalettedContainer<BlockState> before = index < old.length ? old[index] : null;
            PalettedContainer<BlockState> after = index < current.length ? current[index] : null;
            if (before != null || after != null) {
               int baseY = (index + minSectionY) * 16;

               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState oldState = before != null ? before.get(x, y, z) : this.air;
                        BlockState newState = after != null ? after.get(x, y, z) : this.air;
                        if (oldState != newState) {
                           changes.add(new ChunkSnapshotStore.BlockChange(chunkX * 16 + x, baseY + y, chunkZ * 16 + z, newState, oldState));
                        }
                     }
                  }
               }
            }
         }

         return changes;
      }
   }

   private boolean isAllAir(PalettedContainer<BlockState>[] sections) {
      if (sections == null) {
         return true;
      } else {
         for (int index = 6; index <= 23 && index < sections.length; index++) {
            if (sections[index] != null) {
               return false;
            }
         }

         return true;
      }
   }

   @Environment(EnvType.CLIENT)
   public record BlockChange(int worldX, int worldY, int worldZ, BlockState newState, BlockState oldState) {
   }
}
