package net.spectre.client.util.seedscan;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class BlobJudge {
   public static final int JUDGED_MIN_Y = 0;
   public static final int JUDGED_MAX_Y = 30;
   public static final double MIN_MATCH = 0.6;
   public static final int MIN_JUDGED = 8;
   public static final int MAX_FILLED = 3;
   public static final int MAX_CLUSTER = 20000;
   public static final int SHELL_MODEL_CAVE = 16;
   public static final int SHELL_JUDGED = 32;
   public static final int SHELL_STONE = 64;
   private static final Set<Block> NATURAL = set(
      Blocks.CALCITE,
      Blocks.SMOOTH_BASALT,
      Blocks.AMETHYST_BLOCK,
      Blocks.BUDDING_AMETHYST,
      Blocks.DRIPSTONE_BLOCK,
      Blocks.POINTED_DRIPSTONE,
      Blocks.MOSS_BLOCK,
      Blocks.MOSS_CARPET,
      Blocks.ROOTED_DIRT,
      Blocks.CLAY,
      Blocks.SCULK,
      Blocks.SCULK_VEIN,
      Blocks.SCULK_CATALYST,
      Blocks.SCULK_SENSOR,
      Blocks.SCULK_SHRIEKER,
      Blocks.MAGMA_BLOCK,
      Blocks.NETHERRACK,
      Blocks.CRYING_OBSIDIAN
   );
   private static final Set<Block> FAKES = set(
      Blocks.COAL_ORE,
      Blocks.DEEPSLATE_COAL_ORE,
      Blocks.IRON_ORE,
      Blocks.DEEPSLATE_IRON_ORE,
      Blocks.COPPER_ORE,
      Blocks.DEEPSLATE_COPPER_ORE,
      Blocks.GOLD_ORE,
      Blocks.DEEPSLATE_GOLD_ORE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.RAW_IRON_BLOCK,
      Blocks.RAW_COPPER_BLOCK,
      Blocks.INFESTED_STONE,
      Blocks.INFESTED_DEEPSLATE,
      Blocks.MOSSY_COBBLESTONE,
      Blocks.OBSIDIAN,
      Blocks.CHEST,
      Blocks.ENDER_CHEST
   );
   private static final Map<Block, Boolean> STRUCTURE = new ConcurrentHashMap<>();

   private BlobJudge() {
   }

   public static List<BlobJudge.Cluster> clusters(int chunkX, int chunkZ, BlobJudge.Vanilla vanilla) {
      List<BlobJudge.Cluster> clusters = new ArrayList<>();
      VanillaBand band = vanilla.band(chunkX, chunkZ);
      if (band == null) {
         return clusters;
      } else {
         BlobJudge.Bands bands = new BlobJudge.Bands(vanilla);

         for (byte kind : new byte[]{4, 5, 6, 8}) {
            int bit = VBlock.familyBit(kind);
            LongOpenHashSet seen = new LongOpenHashSet();

            for (int y = 0; y <= 30; y++) {
               for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                  for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                     if ((band.families(x, y, z) & bit) != 0) {
                        long start = BlockPos.asLong(x, y, z);
                        if (!seen.contains(start)) {
                           BlobJudge.Cluster cluster = cluster(start, kind, bands, seen);
                           if (BlockPos.getX(cluster.owner()) >> 4 == chunkX && BlockPos.getZ(cluster.owner()) >> 4 == chunkZ) {
                              clusters.add(cluster);
                           }
                        }
                     }
                  }
               }
            }
         }

         return clusters;
      }
   }

   private static BlobJudge.Cluster cluster(long start, byte kind, BlobJudge.Bands vanilla, LongOpenHashSet seen) {
      int bit = VBlock.familyBit(kind);
      LongArrayList cells = new LongArrayList();
      LongOpenHashSet in = new LongOpenHashSet();
      LongArrayFIFOQueue open = new LongArrayFIFOQueue();
      open.enqueue(start);
      in.add(start);
      boolean incomplete = false;
      boolean outOfBand = false;
      boolean judgedLayers = false;
      long owner = start;

      while (!open.isEmpty()) {
         long pos = open.dequeueLong();
         cells.add(pos);
         int cy = BlockPos.getY(pos);
         if (cy >= 0 && cy <= 30) {
            if (!judgedLayers || before(pos, owner)) {
               owner = pos;
            }

            judgedLayers = true;
         }

         if (cells.size() > 20000) {
            outOfBand = true;
            break;
         }

         int px = BlockPos.getX(pos);
         int py = BlockPos.getY(pos);
         int pz = BlockPos.getZ(pos);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dy != 0 || dz != 0) {
                     int x = px + dx;
                     int y = py + dy;
                     int z = pz + dz;
                     long next = BlockPos.asLong(x, y, z);
                     if (!in.contains(next)) {
                        if (!VanillaBand.inBand(y)) {
                           outOfBand = true;
                        } else {
                           VanillaBand band = vanilla.at(x, z);
                           if (band == null) {
                              incomplete = true;
                           } else if ((band.families(x, y, z) & bit) != 0) {
                              in.add(next);
                              open.enqueue(next);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      seen.addAll(in);
      long[] none = new long[0];
      if (outOfBand) {
         return new BlobJudge.Cluster(kind, owner, BlobJudge.Status.OUT_OF_BAND, none, none, none, new byte[0]);
      } else if (incomplete) {
         return new BlobJudge.Cluster(kind, owner, BlobJudge.Status.INCOMPLETE, none, none, none, new byte[0]);
      } else {
         LongArrayList judged = new LongArrayList();
         LongOpenHashSet shell = new LongOpenHashSet();

         for (int i = 0; i < cells.size(); i++) {
            long posx = cells.getLong(i);
            int px = BlockPos.getX(posx);
            int py = BlockPos.getY(posx);
            int pz = BlockPos.getZ(posx);
            if (py >= 0 && py <= 30 && vanilla.at(px, pz).certain(px, py, pz) == kind) {
               judged.add(posx);
            }

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 1; dy++) {
                  for (int dzx = -1; dzx <= 1; dzx++) {
                     long next = BlockPos.asLong(px + dx, py + dy, pz + dzx);
                     if (!in.contains(next)) {
                        shell.add(next);
                     }
                  }
               }
            }
         }

         long[] shellCells = shell.toLongArray();
         ByteArrayList flags = new ByteArrayList(shellCells.length);

         for (long posx : shellCells) {
            int x = BlockPos.getX(posx);
            int y = BlockPos.getY(posx);
            int z = BlockPos.getZ(posx);
            int flag = 0;
            VanillaBand band = vanilla.at(x, z);
            if (band != null && VanillaBand.inBand(y)) {
               flag = band.families(x, y, z);
               byte certain = band.certain(x, y, z);
               if (VBlock.isOpen(certain)) {
                  flag |= 16;
               }

               if (certain == 3) {
                  flag |= 64;
               }

               if (y >= 0 && y <= 30) {
                  flag |= 32;
               }
            }

            flags.add((byte)flag);
         }

         return new BlobJudge.Cluster(kind, owner, null, cells.toLongArray(), judged.toLongArray(), shellCells, flags.toByteArray());
      }
   }

   public static BlobJudge.Verdict judge(BlobJudge.Cluster cluster, BlobJudge.Client client, int minChanges) {
      byte kind = cluster.kind();
      long owner = cluster.owner();
      if (cluster.unfinished() != null) {
         return verdict(cluster.unfinished(), kind, owner);
      } else {
         boolean structure = false;

         for (long pos : cluster.cells()) {
            BlockState state = client.state(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
            if (state == null) {
               return verdict(BlobJudge.Status.INCOMPLETE, kind, owner);
            }

            if (open(state)) {
               return verdict(BlobJudge.Status.OPEN, kind, owner);
            }

            structure |= isStructure(state.getBlock());
         }

         long[] shell = cluster.shell();
         byte[] flags = cluster.shellFlags();
         int filled = 0;

         for (int i = 0; i < shell.length; i++) {
            BlockState statex = client.state(BlockPos.getX(shell[i]), BlockPos.getY(shell[i]), BlockPos.getZ(shell[i]));
            if (statex == null) {
               return verdict(BlobJudge.Status.INCOMPLETE, kind, owner);
            }

            if (open(statex)) {
               return verdict(BlobJudge.Status.OPEN, kind, owner);
            }

            structure |= isStructure(statex.getBlock());
            if ((flags[i] & 16) != 0) {
               filled++;
            }
         }

         if (structure) {
            return verdict(BlobJudge.Status.STRUCTURE, kind, owner);
         } else if (filled > 3) {
            return verdict(BlobJudge.Status.TERRAIN, kind, owner);
         } else {
            Block block = VBlock.familyBlock(kind);
            int judged = 0;
            int matches = 0;
            int fakes = 0;
            LongArrayList inside = new LongArrayList();
            LongArrayList outside = new LongArrayList();

            for (long pos : cluster.judged()) {
               Block shown = client.state(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos)).getBlock();
               if (shown == block) {
                  judged++;
                  matches++;
               } else if (FAKES.contains(shown)) {
                  fakes++;
               } else if (!NATURAL.contains(shown)) {
                  judged++;
                  inside.add(pos);
               }
            }

            for (int i = 0; i < shell.length; i++) {
               if ((flags[i] & 32) != 0 && (flags[i] & 16) == 0) {
                  int x = BlockPos.getX(shell[i]);
                  int y = BlockPos.getY(shell[i]);
                  int z = BlockPos.getZ(shell[i]);
                  BlockState statexx = client.state(x, y, z);
                  byte shown = VBlock.of(statexx);
                  int shownBit = VBlock.familyBit(shown);
                  if (shownBit != 0) {
                     if ((flags[i] & shownBit) == 0 && (shown != 8 || !nearOpen(client, x, y, z))) {
                        outside.add(shell[i]);
                     }
                  } else if ((flags[i] & 64) != 0
                     && shown != 3
                     && !FAKES.contains(statexx.getBlock())
                     && !NATURAL.contains(statexx.getBlock())
                     && !nearOpen(client, x, y, z)) {
                     outside.add(shell[i]);
                  }
               }
            }

            if (judged + fakes > 0 && fakes * 10 > (judged + fakes) * 3) {
               return verdict(BlobJudge.Status.HIDDEN, kind, owner);
            } else if (judged < 8) {
               return verdict(BlobJudge.Status.TOO_FEW, kind, owner);
            } else if (matches < 0.6 * judged) {
               return verdict(BlobJudge.Status.MISPREDICTED, kind, owner);
            } else {
               long[] changes = new long[inside.size() + outside.size()];

               for (int ix = 0; ix < inside.size(); ix++) {
                  changes[ix] = inside.getLong(ix);
               }

               for (int ix = 0; ix < outside.size(); ix++) {
                  changes[inside.size() + ix] = outside.getLong(ix);
               }

               BlobJudge.Status status = changes.length >= Math.max(1, minChanges) ? BlobJudge.Status.ALERT : BlobJudge.Status.CLEAN;
               return new BlobJudge.Verdict(status, kind, owner, changes);
            }
         }
      }
   }

   private static BlobJudge.Verdict verdict(BlobJudge.Status status, byte kind, long owner) {
      return new BlobJudge.Verdict(status, kind, owner, new long[0]);
   }

   public static boolean open(BlockState state) {
      return state.isAir() || !state.getFluidState().isEmpty();
   }

   private static boolean nearOpen(BlobJudge.Client client, int x, int y, int z) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               BlockState state = client.state(x + dx, y + dy, z + dz);
               if (state == null || open(state)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean before(long a, long b) {
      if (BlockPos.getY(a) != BlockPos.getY(b)) {
         return BlockPos.getY(a) < BlockPos.getY(b);
      } else {
         return BlockPos.getZ(a) != BlockPos.getZ(b) ? BlockPos.getZ(a) < BlockPos.getZ(b) : BlockPos.getX(a) < BlockPos.getX(b);
      }
   }

   static boolean isStructure(Block block) {
      Boolean known = STRUCTURE.get(block);
      return known != null ? known : STRUCTURE.computeIfAbsent(block, BlobJudge::structureBlock);
   }

   private static boolean structureBlock(Block block) {
      if (block != Blocks.BONE_BLOCK
         && block != Blocks.SPAWNER
         && block != Blocks.TRIAL_SPAWNER
         && block != Blocks.VAULT
         && block != Blocks.END_PORTAL_FRAME
         && block != Blocks.REINFORCED_DEEPSLATE
         && block != Blocks.SEA_LANTERN) {
         String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
         return path.contains("stone_brick")
            || path.contains("tuff") && !path.equals("tuff")
            || path.contains("copper") && !path.contains("ore") && !path.equals("raw_copper_block")
            || path.startsWith("deepslate_brick")
            || path.startsWith("deepslate_tile")
            || path.startsWith("cracked_deepslate")
            || path.startsWith("polished_deepslate")
            || path.equals("chiseled_deepslate")
            || path.startsWith("suspicious_")
            || path.contains("prismarine")
            || path.endsWith("sponge")
            || path.contains("terracotta")
            || path.startsWith("mud_brick");
      } else {
         return true;
      }
   }

   private static Set<Block> set(Block... blocks) {
      Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
      Collections.addAll(set, blocks);
      return set;
   }

   @Environment(EnvType.CLIENT)
   private static final class Bands implements BlobJudge.Vanilla {
      private final BlobJudge.Vanilla vanilla;
      private int lastX = Integer.MIN_VALUE;
      private int lastZ = Integer.MIN_VALUE;
      private VanillaBand last;

      Bands(BlobJudge.Vanilla vanilla) {
         this.vanilla = vanilla;
      }

      VanillaBand at(int x, int z) {
         return this.band(x >> 4, z >> 4);
      }

      @Override
      public VanillaBand band(int chunkX, int chunkZ) {
         if (chunkX != this.lastX || chunkZ != this.lastZ) {
            this.lastX = chunkX;
            this.lastZ = chunkZ;
            this.last = this.vanilla.band(chunkX, chunkZ);
         }

         return this.last;
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Client {
      BlockState state(int var1, int var2, int var3);
   }

   @Environment(EnvType.CLIENT)
   public record Cluster(byte kind, long owner, BlobJudge.Status unfinished, long[] cells, long[] judged, long[] shell, byte[] shellFlags) {
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      ALERT,
      CLEAN,
      OPEN,
      STRUCTURE,
      TERRAIN,
      MISPREDICTED,
      HIDDEN,
      TOO_FEW,
      INCOMPLETE,
      OUT_OF_BAND;
   }

   @Environment(EnvType.CLIENT)
   public interface Vanilla {
      VanillaBand band(int var1, int var2);
   }

   @Environment(EnvType.CLIENT)
   public record Verdict(BlobJudge.Status status, byte kind, long owner, long[] changes) {
      public boolean alert() {
         return this.status == BlobJudge.Status.ALERT;
      }
   }
}
