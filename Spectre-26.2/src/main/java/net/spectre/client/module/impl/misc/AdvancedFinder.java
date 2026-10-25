package net.spectre.client.module.impl.misc;

import com.example.client.mixin.AccessorNetheriteClientLevel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.util.netherite.NetheriteChunkTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class AdvancedFinder {
   private static final double REACH = 5.0;
   private static final double PROBES_PER_TICK = 18.0;
   private static final double BURST = 600.0;
   private static final int RECOUNT_INTERVAL_TICKS = 10;
   private static final int MAX_REVEALED = 256;
   private final NetheriteFinderModule module;
   private final Map<NetheriteChunkTracker.Candidate, AdvancedFinder.Scan> scans = new HashMap<>();
   private final Set<NetheriteChunkTracker.Candidate> done = new HashSet<>();
   private final Set<BlockPos> revealed = new LinkedHashSet<>();
   private boolean armed;
   private int ticks;
   private double budget;
   private int recountCooldown;
   private static final int MISSING_REFRESH_TICKS = 10;
   private int handWarning;
   private String lastReason = "";
   private ClientLevel level;

   AdvancedFinder(NetheriteFinderModule module) {
      this.module = module;
   }

   boolean armed() {
      return this.armed;
   }

   Collection<BlockPos> revealed() {
      return this.revealed;
   }

   boolean isDone(NetheriteChunkTracker.Candidate section) {
      return this.done.contains(section);
   }

   float progress(NetheriteChunkTracker.Candidate section) {
      AdvancedFinder.Scan scan = this.armed ? this.scans.get(section) : null;
      return scan == null ? -1.0F : fraction(scan);
   }

   private static float fraction(AdvancedFinder.Scan scan) {
      return scan.total == 0 ? 1.0F : Math.min(1.0F, (float)scan.sent / scan.total);
   }

   void forEachProbe(Vec3 eye, double radius, int limit, float missingFrom, AdvancedFinder.ProbeVisitor visitor) {
      if (this.armed) {
         double radiusSq = radius * radius;
         int emitted = 0;

         for (AdvancedFinder.Scan scan : this.scans.values()) {
            if (!(fraction(scan) >= missingFrom)) {
               int baseX = scan.section.chunkX() << 4;
               int baseY = scan.section.sectionY() << 4;
               int baseZ = scan.section.chunkZ() << 4;

               for (int index = 0; index < 4096; index++) {
                  int at = scan.stamp[index];
                  if (at != 0) {
                     int x = baseX + (index & 15);
                     int y = baseY + (index >> 8);
                     int z = baseZ + (index >> 4 & 15);
                     double dx = x + 0.5 - eye.x;
                     double dy = y + 0.5 - eye.y;
                     double dz = z + 0.5 - eye.z;
                     if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                        visitor.visit(x, y, z, this.ticks - at + 1);
                        if (++emitted >= limit) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   void forEachMissing(Vec3 eye, double radius, int limit, float missingFrom, AdvancedFinder.MissingVisitor visitor) {
      if (this.armed) {
         double radiusSq = radius * radius;
         int emitted = 0;

         for (AdvancedFinder.Scan scan : this.scans.values()) {
            if (!(fraction(scan) < missingFrom)) {
               this.refreshMissing(scan);
               int baseX = scan.section.chunkX() << 4;
               int baseY = scan.section.sectionY() << 4;
               int baseZ = scan.section.chunkZ() << 4;

               for (int i = 0; i < scan.missingCount; i++) {
                  int index = scan.missing[i];
                  if (!scan.probed.get(index)) {
                     int x = baseX + (index & 15);
                     int y = baseY + (index >> 8);
                     int z = baseZ + (index >> 4 & 15);
                     double dx = x + 0.5 - eye.x;
                     double dy = y + 0.5 - eye.y;
                     double dz = z + 0.5 - eye.z;
                     if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                        visitor.visit(x, y, z);
                        if (++emitted >= limit) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void refreshMissing(AdvancedFinder.Scan scan) {
      if (this.level != null && this.ticks - scan.missingAt >= 10) {
         scan.missingAt = this.ticks;
         int baseX = scan.section.chunkX() << 4;
         int baseY = scan.section.sectionY() << 4;
         int baseZ = scan.section.chunkZ() << 4;
         MutableBlockPos cursor = new MutableBlockPos();
         int count = 0;

         for (int index = scan.probed.nextClearBit(0); index < 4096; index = scan.probed.nextClearBit(index + 1)) {
            cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
            if (worthProbing(this.level, cursor)) {
               scan.missing[count++] = index;
            }
         }

         scan.missingCount = count;
      }
   }

   void arm() {
      this.armed = true;
      this.lastReason = "";
   }

   void disarm(Minecraft mc) {
      this.armed = false;
      this.lastReason = "";
   }

   void reset(Minecraft mc) {
      this.disarm(mc);
      this.scans.clear();
      this.done.clear();
      this.revealed.clear();
      this.budget = 0.0;
      this.level = null;
   }

   boolean onRevealed(BlockPos pos) {
      NetheriteChunkTracker.Candidate section = new NetheriteChunkTracker.Candidate(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4);
      if (!this.revealed.add(pos)) {
         return false;
      } else {
         if (this.revealed.size() > 256) {
            Iterator<BlockPos> oldest = this.revealed.iterator();
            oldest.next();
            oldest.remove();
         }

         if (this.armed && this.scans.remove(section) != null) {
            this.done.add(section);
            ModuleChat.send(this.module, ModuleChat.body().text("Ancient debris ").good("found").text(" at ").coords(pos.getX(), pos.getY(), pos.getZ()));
         }

         return true;
      }
   }

   void maintain(Minecraft mc) {
      ClientLevel world = mc.level;
      if (world == this.level) {
         if (world != null) {
            this.revealed.removeIf(pos -> !world.getBlockState(pos).is(Blocks.ANCIENT_DEBRIS));
         }
      } else {
         this.scans.clear();
         this.done.clear();
         this.revealed.clear();
         this.level = world;
      }
   }

   void tick(Minecraft mc) {
      if (this.armed) {
         LocalPlayer player = mc.player;
         ClientLevel world = mc.level;
         if (player != null && world != null && player.connection != null && mc.gameMode != null) {
            this.ticks++;
            this.budget = Math.min(600.0, this.budget + 18.0);
            if (this.handWarning > 0) {
               this.handWarning--;
            }

            if (!NetheriteChunkTracker.isNether(world)) {
               this.hold("Not in the Nether");
            } else {
               this.lastReason = "";
               if (mc.gui.screen() == null && !player.isUsingItem() && !mc.gameMode.isDestroying()) {
                  Vec3 eye = player.getEyePosition();
                  this.refreshScans(world, eye);
                  if (!this.scans.isEmpty() && !(this.budget < 1.0)) {
                     List<AdvancedFinder.Probe> probes = this.collect(world, eye);
                     if (!probes.isEmpty()) {
                        int selected = player.getInventory().getSelectedSlot();
                        int slot = probeSlot(player, selected);
                        if (slot < 0) {
                           if (this.handWarning <= 0) {
                              this.handWarning = 200;
                              ModuleChat.send(this.module, ModuleChat.body().text("Hotbar needs an empty or non-tool slot"));
                           }
                        } else {
                           if (slot != selected) {
                              player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                           }

                           BlockPos last = null;

                           for (AdvancedFinder.Probe probe : probes) {
                              if (this.budget < 1.0) {
                                 break;
                              }

                              sendAction(player, world, Action.START_DESTROY_BLOCK, probe.pos());
                              probe.scan().probed.set(probe.index());
                              probe.scan().sent++;
                              probe.scan().stamp[probe.index()] = this.ticks;
                              probe.scan().dirty = true;
                              this.budget--;
                              last = probe.pos();
                           }

                           if (last != null) {
                              sendAction(player, world, Action.ABORT_DESTROY_BLOCK, last);
                           }

                           if (slot != selected) {
                              player.connection.send(new ServerboundSetCarriedItemPacket(selected));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void refreshScans(ClientLevel world, Vec3 eye) {
      Set<NetheriteChunkTracker.Candidate> live = new HashSet<>(NetheriteChunkTracker.snapshot());
      this.scans.keySet().retainAll(live);

      for (NetheriteChunkTracker.Candidate candidate : live) {
         if (!this.done.contains(candidate) && !this.scans.containsKey(candidate) && distanceToSectionSq(eye, candidate) <= 25.0) {
            this.scans.put(candidate, new AdvancedFinder.Scan(candidate, countProbeable(world, candidate)));
         }
      }

      if (this.recountCooldown-- <= 0) {
         this.recountCooldown = 10;
         Iterator<AdvancedFinder.Scan> iterator = this.scans.values().iterator();

         while (iterator.hasNext()) {
            AdvancedFinder.Scan scan = iterator.next();
            if (scan.dirty) {
               scan.dirty = false;
               if (!hasUnprobed(world, scan)) {
                  iterator.remove();
                  this.done.add(scan.section);
                  NetheriteChunkTracker.Candidate s = scan.section;
                  ModuleChat.send(
                     this.module,
                     ModuleChat.body().text("Section ").good("clean").text(" at ").coords(s.chunkX() * 16 + 8, s.sectionY() * 16 + 8, s.chunkZ() * 16 + 8)
                  );
               }
            }
         }
      }
   }

   private List<AdvancedFinder.Probe> collect(ClientLevel world, Vec3 eye) {
      List<AdvancedFinder.Probe> coarse = new ArrayList<>();
      List<AdvancedFinder.Probe> fine = new ArrayList<>();
      int span = (int)Math.ceil(5.0);
      int cx = (int)Math.floor(eye.x);
      int cy = (int)Math.floor(eye.y);
      int cz = (int)Math.floor(eye.z);
      MutableBlockPos cursor = new MutableBlockPos();

      for (AdvancedFinder.Scan scan : this.scans.values()) {
         int baseX = scan.section.chunkX() << 4;
         int baseY = scan.section.sectionY() << 4;
         int baseZ = scan.section.chunkZ() << 4;
         int minX = Math.max(baseX, cx - span);
         int maxX = Math.min(baseX + 15, cx + span);
         int minY = Math.max(baseY, cy - span);
         int maxY = Math.min(baseY + 15, cy + span);
         int minZ = Math.max(baseZ, cz - span);
         int maxZ = Math.min(baseZ + 15, cz + span);

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  int lx = x & 15;
                  int ly = y & 15;
                  int lz = z & 15;
                  int index = ly << 8 | lz << 4 | lx;
                  if (!scan.probed.get(index)) {
                     cursor.set(x, y, z);
                     double distanceSq = distanceToBlockSq(eye, cursor);
                     if (!(distanceSq > 25.0)) {
                        if (!worthProbing(world, cursor)) {
                           scan.probed.set(index);
                        } else {
                           AdvancedFinder.Probe probe = new AdvancedFinder.Probe(scan, index, cursor.immutable(), distanceSq);
                           if (lx % 3 == 1 && ly % 3 == 1 && lz % 3 == 1) {
                              coarse.add(probe);
                           } else {
                              fine.add(probe);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      coarse.sort(Comparator.comparingDouble(AdvancedFinder.Probe::distanceSq));
      fine.sort(Comparator.comparingDouble(AdvancedFinder.Probe::distanceSq));
      coarse.addAll(fine);
      return coarse;
   }

   private static int countProbeable(ClientLevel world, NetheriteChunkTracker.Candidate section) {
      int baseX = section.chunkX() << 4;
      int baseY = section.sectionY() << 4;
      int baseZ = section.chunkZ() << 4;
      MutableBlockPos cursor = new MutableBlockPos();
      int count = 0;

      for (int index = 0; index < 4096; index++) {
         cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
         if (worthProbing(world, cursor)) {
            count++;
         }
      }

      return count;
   }

   private static boolean hasUnprobed(ClientLevel world, AdvancedFinder.Scan scan) {
      int baseX = scan.section.chunkX() << 4;
      int baseY = scan.section.sectionY() << 4;
      int baseZ = scan.section.chunkZ() << 4;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int index = scan.probed.nextClearBit(0); index < 4096; index = scan.probed.nextClearBit(index + 1)) {
         cursor.set(baseX + (index & 15), baseY + (index >> 8), baseZ + (index >> 4 & 15));
         if (worthProbing(world, cursor)) {
            return true;
         }
      }

      return false;
   }

   private static boolean worthProbing(ClientLevel world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.ANCIENT_DEBRIS)
         ? state.isSolidRender() && state.getDestroySpeed(world, pos) > 0.0F
         : false;
   }

   private void hold(String reason) {
      if (!reason.equals(this.lastReason)) {
         this.lastReason = reason;
         ModuleChat.send(this.module, ModuleChat.body().text(reason));
      }
   }

   private static void sendAction(LocalPlayer player, ClientLevel world, Action action, BlockPos pos) {
      BlockStatePredictionHandler handler = ((AccessorNetheriteClientLevel)world).spectre$blockStatePredictionHandler();

      try (BlockStatePredictionHandler sequence = handler.startPredicting()) {
         player.connection.send(new ServerboundPlayerActionPacket(action, pos, Direction.UP, sequence.currentSequence()));
      }
   }

   private static double distanceToSectionSq(Vec3 from, NetheriteChunkTracker.Candidate section) {
      double minX = section.chunkX() * 16.0;
      double minY = section.sectionY() * 16.0;
      double minZ = section.chunkZ() * 16.0;
      double dx = Math.max(0.0, Math.max(minX - from.x, from.x - (minX + 16.0)));
      double dy = Math.max(0.0, Math.max(minY - from.y, from.y - (minY + 16.0)));
      double dz = Math.max(0.0, Math.max(minZ - from.z, from.z - (minZ + 16.0)));
      return dx * dx + dy * dy + dz * dz;
   }

   private static double distanceToBlockSq(Vec3 from, BlockPos pos) {
      double dx = Math.max(0.0, Math.max(pos.getX() - from.x, from.x - (pos.getX() + 1.0)));
      double dy = Math.max(0.0, Math.max(pos.getY() - from.y, from.y - (pos.getY() + 1.0)));
      double dz = Math.max(0.0, Math.max(pos.getZ() - from.z, from.z - (pos.getZ() + 1.0)));
      return dx * dx + dy * dy + dz * dz;
   }

   private static int probeSlot(LocalPlayer player, int selected) {
      if (!isTool(player.getInventory().getItem(selected))) {
         return selected;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
               return slot;
            }
         }

         for (int slotx = 0; slotx < 9; slotx++) {
            if (!isTool(player.getInventory().getItem(slotx))) {
               return slotx;
            }
         }

         return -1;
      }
   }

   private static boolean isTool(ItemStack stack) {
      return stack.has(DataComponents.TOOL);
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   interface MissingVisitor {
      void visit(int var1, int var2, int var3);
   }

   @Environment(EnvType.CLIENT)
   private record Probe(AdvancedFinder.Scan scan, int index, BlockPos pos, double distanceSq) {
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   interface ProbeVisitor {
      void visit(int var1, int var2, int var3, int var4);
   }

   @Environment(EnvType.CLIENT)
   private static final class Scan {
      final NetheriteChunkTracker.Candidate section;
      final BitSet probed = new BitSet(4096);
      final int[] stamp = new int[4096];
      final int total;
      final int[] missing = new int[4096];
      int missingCount;
      int missingAt = -1000;
      int sent;
      boolean dirty;

      Scan(NetheriteChunkTracker.Candidate section, int total) {
         this.section = section;
         this.total = total;
      }
   }
}
