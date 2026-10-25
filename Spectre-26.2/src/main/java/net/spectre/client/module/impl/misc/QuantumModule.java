package net.spectre.client.module.impl.misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.BlockUpdateEvent;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.quantum.ChunkSnapshotStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class QuantumModule extends ClientModule {
   private static final int SCAN_RADIUS = 8;
   private static final int FLAG_RADIUS = 9;
   private static final double MARKER_Y = 63.0;
   private static final float LINE_WIDTH = 1.5F;
   private static final Set<Block> IGNORED_NEW_BLOCKS = Set.of(
      Blocks.FIRE,
      Blocks.FROSTED_ICE,
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
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.OAK_LEAVES,
      Blocks.ACACIA_LEAVES,
      Blocks.BIRCH_LEAVES,
      Blocks.JUNGLE_LEAVES,
      Blocks.DARK_OAK_LEAVES,
      Blocks.PALE_OAK_LEAVES,
      Blocks.CHERRY_LEAVES,
      Blocks.AZALEA_LEAVES,
      Blocks.FLOWERING_AZALEA_LEAVES,
      Blocks.MOSSY_COBBLESTONE,
      Blocks.RAW_COPPER_BLOCK,
      Blocks.STONE,
      Blocks.OBSERVER,
      Blocks.REPEATER,
      Blocks.COMPARATOR,
      Blocks.REDSTONE_TORCH,
      Blocks.REDSTONE_LAMP,
      Blocks.REDSTONE_WIRE,
      Blocks.DROPPER
   );
   private static final Set<Block> IGNORED_OLD_BLOCKS = Set.of(Blocks.AMETHYST_BLOCK, Blocks.STONE, Blocks.FROSTED_ICE);
   private final BooleanSetting advancedDebug = this.addSetting("advanced-debug", new BooleanSetting("Advanced Debug", false));
   private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Render", true));
   private final ColorSetting fillColor = this.addSetting("side-color", new ColorSetting("Fill Color", 687810600));
   private final ColorSetting lineColor = this.addSetting("line-color", new ColorSetting("Line Color", -55256));
   private final ChunkSnapshotStore snapshots = new ChunkSnapshotStore(Blocks.AIR.defaultBlockState());
   private final Set<ChunkPos> justSent = new HashSet<>();
   private final List<BlockPos> changedBlocks = new ArrayList<>();
   private Set<ChunkPos> previousRange = Set.of();
   private ChunkPos lastPos;
   private ClientLevel sessionLevel;

   public QuantumModule() {
      super("quantum", "Quantum", "Finds player activity in resent chunks.", ModuleCategory.BASE_RAID);
      this.usesNotifications().set(false);
      this.fillColor.visibleWhen(() -> this.render.get() && this.advancedDebug.get());
      this.lineColor.visibleWhen(() -> this.render.get() && this.advancedDebug.get());
   }

   @Override
   protected void onEnable() {
      this.sessionLevel = null;
      this.resetSession();
   }

   @Override
   protected void onDisable() {
      this.snapshots.clear();
   }

   private void resetSession() {
      this.snapshots.clear();
      this.justSent.clear();
      this.changedBlocks.clear();
      this.previousRange = Set.of();
      this.lastPos = null;
   }

   private void ensureSession(ClientLevel level) {
      if (level != this.sessionLevel) {
         this.sessionLevel = level;
         this.resetSession();
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         this.ensureSession(mc.level);
         if (LevelUtil.isOverworld(mc.level)) {
            ChunkPos current = mc.player.chunkPosition();
            if (this.lastPos != null && !this.lastPos.equals(current)) {
               Set<ChunkPos> range = chunksInRange(current);

               for (ChunkPos pos : range) {
                  if (!this.previousRange.contains(pos)) {
                     this.snapshots.store(pos.x(), pos.z(), extractSections(mc.level.getChunk(pos.x(), pos.z())));
                  }
               }

               this.justSent.removeIf(posx -> !range.contains(posx));
               Set<Long> keys = new HashSet<>();

               for (ChunkPos posx : range) {
                  keys.add(ChunkSnapshotStore.chunkKey(posx.x(), posx.z()));
               }

               this.snapshots.retain(keys::contains);
               this.previousRange = range;
            }

            this.lastPos = current;
         }
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
         Minecraft mc = Minecraft.getInstance();
         ClientLevel level = mc.level;
         if (mc.player != null && level != null) {
            this.ensureSession(level);
            if (LevelUtil.isOverworld(level)) {
               ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
               if (chunksInRange(mc.player.chunkPosition()).contains(pos)) {
                  if (LevelUtil.isLoaded(level, pos.x(), pos.z())) {
                     PalettedContainer<BlockState>[] current = extractSections(level.getChunk(pos.x(), pos.z()));

                     for (ChunkSnapshotStore.BlockChange change : this.snapshots.compare(pos.x(), pos.z(), current, level.getMinSectionY())) {
                        Block newBlock = change.newState().getBlock();
                        Block oldBlock = change.oldState().getBlock();
                        if (!IGNORED_NEW_BLOCKS.contains(newBlock) && !IGNORED_OLD_BLOCKS.contains(oldBlock)) {
                           this.onActivity(pos, change, newBlock, oldBlock);
                           break;
                        }
                     }

                     this.justSent.add(pos);
                     this.snapshots.store(pos.x(), pos.z(), current);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && LevelUtil.isOverworld(level)) {
         BlockPos pos = event.pos;
         int sectionIndex = (pos.getY() >> 4) - level.getMinSectionY();
         this.snapshots.update(pos.getX() >> 4, pos.getZ() >> 4, sectionIndex, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, event.newState);
      }
   }

   private void onActivity(ChunkPos pos, ChunkSnapshotStore.BlockChange change, Block newBlock, Block oldBlock) {
      ModuleChat.Body notice = ModuleChat.body().text("Activity ").good("detected").text(" in chunk ").value(pos.x() + ", " + pos.z());
      if (this.advancedDebug.get()) {
         notice.text(": ").name(oldBlock.getName().getString()).text(" changed to ").name(newBlock.getName().getString());
      }

      ModuleChat.send(this, notice);
      this.changedBlocks.add(new BlockPos(change.worldX(), change.worldY(), change.worldZ()));
      RenderUtilModule renderUtil = ModuleRegistry.get(RenderUtilModule.class);
      if (renderUtil != null) {
         renderUtil.addSourceChunk(pos);
         List<ChunkPos> area = new ArrayList<>();

         for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
               area.add(new ChunkPos(pos.x() + dx, pos.z() + dz));
            }
         }

         if (!renderUtil.hasFlaggedChunks()) {
            renderUtil.addFlaggedChunks(area);
         } else {
            List<ChunkPos> overlap = new ArrayList<>();

            for (ChunkPos candidate : area) {
               if (renderUtil.isFlagged(candidate)) {
                  overlap.add(candidate);
               }
            }

            renderUtil.clearFlaggedChunks();
            renderUtil.addFlaggedChunks(overlap.isEmpty() ? area : overlap);
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (this.render.get() && this.advancedDebug.get() && mc.player != null && mc.level != null && LevelUtil.isOverworld(mc.level)) {
         GizmoStyle blockStyle = GizmoStyle.strokeAndFill(this.lineColor.argb(), 1.5F, this.fillColor.argb());

         for (BlockPos pos : this.changedBlocks) {
            Gizmos.cuboid(new AABB(pos), blockStyle).setAlwaysOnTop();
         }

         GizmoStyle sentStyle = GizmoStyle.strokeAndFill(-16711936, 1.5F, 671153920);

         for (ChunkPos pos : chunksInRange(mc.player.chunkPosition())) {
            if (this.justSent.contains(pos)) {
               double x = pos.x() * 16.0;
               double z = pos.z() * 16.0;
               Gizmos.cuboid(new AABB(x, 63.0, z, x + 16.0, 63.0, z + 16.0), sentStyle).setAlwaysOnTop();
            }
         }
      }
   }

   static Set<ChunkPos> chunksInRange(ChunkPos center) {
      Set<ChunkPos> range = new LinkedHashSet<>();

      for (int dz = -8; dz <= 8; dz++) {
         int cut = Math.max(0, 3 - (8 - Math.abs(dz)));
         int halfWidth = 8 - cut;

         for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            range.add(new ChunkPos(center.x() + dx, center.z() + dz));
         }
      }

      return range;
   }

   private static PalettedContainer<BlockState>[] extractSections(LevelChunk chunk) {
      LevelChunkSection[] sections = chunk.getSections();
      PalettedContainer<BlockState>[] out = new PalettedContainer[sections.length];

      for (int index = 6; index <= 23 && index < sections.length; index++) {
         LevelChunkSection section = sections[index];
         if (section != null && !section.hasOnlyAir()) {
            out[index] = section.getStates().copy();
         }
      }

      return out;
   }
}
