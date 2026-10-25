package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.BlockUpdateEvent;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.LevelUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LayerLightEventListener;

@Environment(EnvType.CLIENT)
public final class PrimeChunkFinderModule extends ClientModule {
   private static final int SECTION_BYTES = 2048;
   private static final int MAX_FLAGGED_CHUNKS = 256;
   private static final int AMETHYST_MAX_LIGHT = 5;
   private static final int AMETHYST_REACH = 5;
   private static final long REDSTONE_NOTIFY_COOLDOWN_MS = 15000L;
   private static final int ORE_LIGHT = 9;
   private static final long[] NO_ORES = new long[0];
   private static final int LAMP_LIGHT = 14;
   private static final int[] NO_LAMPS = new int[0];
   private static final long GONE_LAMP_MS = 5000L;
   private static final int MAX_TRACKED_CHUNKS = 5000;
   private static final int SNAPSHOT_RADIUS = 32;
   private static final byte[] ZERO_SECTION = new byte[2048];
   private static final double MAX_Y = 0.0;
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk Color", 1342242650));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final LinkedHashMap<Long, Map<Integer, byte[]>> chunkLight = new LinkedHashMap<>(256, 0.75F, true);
   private final LongOpenHashSet passedChunks = new LongOpenHashSet();
   private final LongLinkedOpenHashSet flaggedChunks = new LongLinkedOpenHashSet();
   private final LongOpenHashSet shownChunks = new LongOpenHashSet();
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final LongOpenHashSet checkedSections = new LongOpenHashSet();
   private final LongOpenHashSet amethystSections = new LongOpenHashSet();
   private final Long2ObjectOpenHashMap<long[]> oreSections = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<int[]> lampSections = new Long2ObjectOpenHashMap();
   private final Long2LongOpenHashMap goneLamps = new Long2LongOpenHashMap();
   private final MutableBlockPos cursor = new MutableBlockPos();
   private final Long2LongOpenHashMap redstoneNotified = new Long2LongOpenHashMap();

   public PrimeChunkFinderModule() {
      super("prime-chunk-finder", "Prime Chunk Finder", "Flags chunks with light and redstone changes below Y 0.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.sound.visibleWhen(notifications::get);
      this.redstoneNotified.defaultReturnValue(-4611686018427387904L);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !(mc.player.getY() >= 0.0)) {
         this.reset();
         this.snapshotLoadedChunks();
         this.on(PacketReceiveNettyEvent.TYPE, this::onPacket);
      } else {
         warnDeepslateOnly(mc);
         this.requestDisable();
      }
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private static void warnDeepslateOnly(Minecraft mc) {
      ToastManager toasts = mc.gui.toastManager();
      if (toasts.getToast(PrimeChunkFinderModule.DeepslateToast.class, Toast.NO_TOKEN) == null) {
         toasts.addToast(new PrimeChunkFinderModule.DeepslateToast());
      }
   }

   private void onPacket(PacketReceiveNettyEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.packet instanceof ClientboundLoginPacket) {
         mc.execute(() -> {
            if (this.isEnabled()) {
               this.reset();
            }
         });
      } else if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
         int chunkX = packet.getX();
         int chunkZ = packet.getZ();
         ClientboundLightUpdatePacketData data = packet.getLightData();
         mc.execute(() -> {
            if (this.isEnabled() && mc.level != null) {
               this.baselineChunk(chunkX, chunkZ, data);
            }
         });
      } else if (event.packet instanceof ClientboundLightUpdatePacket packet) {
         int chunkX = packet.getX();
         int chunkZ = packet.getZ();
         ClientboundLightUpdatePacketData data = packet.getLightData();
         mc.execute(() -> {
            if (this.isEnabled() && mc.level != null) {
               this.detectChanges(chunkX, chunkZ, data);
            }
         });
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (event.oldState != event.newState && Minecraft.getInstance().level != null) {
         this.rememberGoneLamp(event.pos, event.oldState, event.newState);
         this.detectRedstone(event.pos, event.oldState, event.newState);
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.reset();
      } else {
         if (mc.player != null && mc.player.getY() < 0.0) {
            this.passedChunks.add(mc.player.chunkPosition().pack());
         }

         this.forgetOldLamps();
         this.refreshShown(mc.level);
      }
   }

   private void refreshShown(ClientLevel level) {
      this.shownChunks.clear();
      LongListIterator var2 = this.flaggedChunks.iterator();

      while (var2.hasNext()) {
         long key = (Long)var2.next();
         if (LevelUtil.isLoaded(level, ChunkPos.getX(key), ChunkPos.getZ(key))) {
            this.shownChunks.add(key);
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (!this.shownChunks.isEmpty() && Minecraft.getInstance().level != null) {
         int fill = this.chunkColor.argb();
         ChunkOverlay.draw(this.shownChunks, fill, event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.shownChunks, 63.0, ARGB.opaque(fill));
         }
      }
   }

   private void flagChunk(long key) {
      this.flaggedChunks.addAndMoveToLast(key);
      this.shownChunks.add(key);

      while (this.flaggedChunks.size() > 256) {
         this.flaggedChunks.removeFirstLong();
      }
   }

   private void snapshotLoadedChunks() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int centerX = mc.player.getBlockX() >> 4;
         int centerZ = mc.player.getBlockZ() >> 4;

         for (int chunkX = centerX - 32; chunkX <= centerX + 32; chunkX++) {
            for (int chunkZ = centerZ - 32; chunkZ <= centerZ + 32; chunkZ++) {
               Map<Integer, byte[]> sections = this.snapshotChunk(chunkX, chunkZ);
               if (sections != null) {
                  this.chunkLight.put(ChunkPos.pack(chunkX, chunkZ), sections);
               }
            }
         }

         this.trimChunkCache();
      }
   }

   private Map<Integer, byte[]> snapshotChunk(int chunkX, int chunkZ) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return null;
      } else {
         ClientChunkCache chunkSource = level.getChunkSource();
         if (!chunkSource.hasChunk(chunkX, chunkZ)) {
            return null;
         } else {
            LayerLightEventListener blockLight = chunkSource.getLightEngine().getLayerListener(LightLayer.BLOCK);
            Map<Integer, byte[]> sections = this.createZeroedChunk();

            for (int sectionY = this.bottomSection(); sectionY <= this.topSection(); sectionY++) {
               DataLayer layer = blockLight.getDataLayerData(SectionPos.of(chunkX, sectionY, chunkZ));
               if (layer != null && !layer.isDefinitelyFilledWith(0)) {
                  sections.put(sectionY, Arrays.copyOf(layer.getData(), 2048));
               }
            }

            return sections;
         }
      }
   }

   private void baselineChunk(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data) {
      Map<Integer, byte[]> sections = this.createZeroedChunk();
      this.applyInitializedSections(data, sections, chunkX, chunkZ, false, null);
      this.applyUninitializedSections(data, sections, chunkX, chunkZ, false, null);
      this.chunkLight.put(ChunkPos.pack(chunkX, chunkZ), sections);
      this.trimChunkCache();
   }

   private void detectChanges(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      this.checkedSections.clear();
      this.amethystSections.clear();
      this.oreSections.clear();
      this.lampSections.clear();
      Map<Integer, byte[]> sections = this.chunkLight.get(key);
      if (sections == null) {
         sections = this.snapshotChunk(chunkX, chunkZ);
      }

      boolean compare = sections != null && !this.isOwnChunk(key);
      if (sections == null) {
         sections = this.createZeroedChunk();
      }

      PrimeChunkFinderModule.ChangeSummary summary = new PrimeChunkFinderModule.ChangeSummary();
      this.applyInitializedSections(data, sections, chunkX, chunkZ, compare, summary);
      this.applyUninitializedSections(data, sections, chunkX, chunkZ, compare, summary);
      this.chunkLight.put(key, sections);
      this.trimChunkCache();
      if (summary.count != 0) {
         this.flagChunk(key);
         if (this.notificationsOn()) {
            ChallengeToast.addOrUpdate(
               new ItemStack(Items.EMERALD_BLOCK), this.name(), Component.literal(summary.x + ", " + summary.y + ", " + summary.z), this.sound.get()
            );
         }
      }
   }

   private void rememberGoneLamp(BlockPos pos, BlockState previous, BlockState state) {
      if (isLamp(previous)) {
         int light = previous.getLightEmission();
         if (!isLamp(state) || state.getLightEmission() < light) {
            this.goneLamps.put(pos.asLong(), Util.getMillis() + 5000L << 4 | light);
         }
      }
   }

   private void forgetOldLamps() {
      if (!this.goneLamps.isEmpty()) {
         long now = Util.getMillis();
         LongIterator it = this.goneLamps.values().iterator();

         while (it.hasNext()) {
            if (it.nextLong() >>> 4 < now) {
               it.remove();
            }
         }
      }
   }

   private void detectRedstone(BlockPos pos, BlockState previous, BlockState state) {
      if (!(pos.getY() >= 0.0) && (isRedstone(state) || isRedstone(previous))) {
         long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
         if (!this.isOwnChunk(key)) {
            this.flagChunk(key);
            long now = Util.getMillis();
            if (this.notificationsOn() && now - this.redstoneNotified.get(key) >= 15000L) {
               this.redstoneNotified.put(key, now);
               ChallengeToast.addOrUpdate(
                  new ItemStack(Items.REDSTONE),
                  this.name(),
                  Component.literal("Redstone " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                  this.sound.get()
               );
            }
         }
      }
   }

   private static boolean isRedstone(BlockState state) {
      Block block = state.getBlock();
      return block instanceof RedStoneWireBlock
         || block instanceof DiodeBlock
         || block instanceof RedstoneTorchBlock
         || block instanceof RedstoneLampBlock
         || block instanceof ObserverBlock
         || block instanceof PistonBaseBlock
         || block instanceof PistonHeadBlock
         || block instanceof MovingPistonBlock
         || block instanceof DispenserBlock
         || block instanceof HopperBlock
         || block instanceof NoteBlock
         || block instanceof DaylightDetectorBlock
         || block instanceof LeverBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof TripWireHookBlock
         || block instanceof TargetBlock
         || block instanceof CrafterBlock
         || block instanceof PoweredBlock;
   }

   private boolean isOwnChunk(long key) {
      if (this.passedChunks.contains(key)) {
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.player != null && mc.player.chunkPosition().pack() == key;
      }
   }

   private void applyInitializedSections(
      ClientboundLightUpdatePacketData data,
      Map<Integer, byte[]> sections,
      int chunkX,
      int chunkZ,
      boolean compare,
      PrimeChunkFinderModule.ChangeSummary summary
   ) {
      BitSet mask = data.getBlockYMask();
      List<byte[]> updates = data.getBlockUpdates();
      int next = 0;

      for (int bit = mask.nextSetBit(0); bit >= 0 && next < updates.size(); bit = mask.nextSetBit(bit + 1)) {
         byte[] update = updates.get(next++);
         int sectionY = this.lightSectionY(bit);
         if (this.isWorldSection(sectionY) && update.length == 2048) {
            this.updateSection(sections, chunkX, chunkZ, sectionY, Arrays.copyOf(update, 2048), compare, summary);
         }
      }
   }

   private void applyUninitializedSections(
      ClientboundLightUpdatePacketData data,
      Map<Integer, byte[]> sections,
      int chunkX,
      int chunkZ,
      boolean compare,
      PrimeChunkFinderModule.ChangeSummary summary
   ) {
      BitSet empty = data.getEmptyBlockYMask();

      for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
         int sectionY = this.lightSectionY(bit);
         if (this.isWorldSection(sectionY)) {
            this.updateSection(sections, chunkX, chunkZ, sectionY, ZERO_SECTION, compare, summary);
         }
      }
   }

   private void updateSection(
      Map<Integer, byte[]> sections, int chunkX, int chunkZ, int sectionY, byte[] light, boolean compare, PrimeChunkFinderModule.ChangeSummary summary
   ) {
      byte[] previous = sections.get(sectionY);
      if (compare && previous != null && summary != null) {
         this.compareSection(previous, light, chunkX, chunkZ, sectionY, summary);
      }

      sections.put(sectionY, light);
   }

   private void compareSection(byte[] previous, byte[] light, int chunkX, int chunkZ, int sectionY, PrimeChunkFinderModule.ChangeSummary summary) {
      for (int i = 0; i < 2048; i++) {
         int was = previous[i] & 255;
         int now = light[i] & 255;
         int changed = was ^ now;
         if ((changed & 15) != 0) {
            this.recordChange(chunkX, chunkZ, sectionY, i << 1, was & 15, now & 15, summary);
         }

         if ((changed & 240) != 0) {
            this.recordChange(chunkX, chunkZ, sectionY, (i << 1) + 1, was >>> 4 & 15, now >>> 4 & 15, summary);
         }
      }
   }

   private void recordChange(int chunkX, int chunkZ, int sectionY, int index, int oldValue, int newValue, PrimeChunkFinderModule.ChangeSummary summary) {
      int x = (chunkX << 4) + (index & 15);
      int y = (sectionY << 4) + (index >>> 8 & 15);
      int z = (chunkZ << 4) + (index >>> 4 & 15);
      int value = Math.max(oldValue, newValue);
      if (!this.fromAmethyst(x, y, z, oldValue, newValue) && !this.fromRedstoneOre(x, y, z, value) && !this.fromLamp(x, y, z, value)) {
         summary.add(x, y, z, oldValue, newValue);
      }
   }

   private boolean fromLamp(int x, int y, int z, int value) {
      if (value > 14) {
         return false;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         if (level == null) {
            return false;
         } else {
            int reach = 14 - value;

            for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
               for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
                  for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                     for (int lamp : this.lamps(level, sx, sy, sz)) {
                        int index = lamp >>> 4;
                        int lx = (sx << 4) + (index & 15);
                        int ly = (sy << 4) + (index >>> 8);
                        int lz = (sz << 4) + (index >>> 4 & 15);
                        if (Math.abs(lx - x) + Math.abs(ly - y) + Math.abs(lz - z) <= (lamp & 15) - value) {
                           return true;
                        }
                     }
                  }
               }
            }

            long now = Util.getMillis();
            ObjectIterator var19 = this.goneLamps.long2LongEntrySet().iterator();

            while (var19.hasNext()) {
               Entry gone = (Entry)var19.next();
               long packed = gone.getLongValue();
               if (packed >>> 4 >= now) {
                  long pos = gone.getLongKey();
                  int distance = Math.abs(BlockPos.getX(pos) - x) + Math.abs(BlockPos.getY(pos) - y) + Math.abs(BlockPos.getZ(pos) - z);
                  if (distance <= (int)(packed & 15L) - value) {
                     return true;
                  }
               }
            }

            return false;
         }
      }
   }

   private int[] lamps(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
      long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
      int[] cached = (int[])this.lampSections.get(key);
      if (cached != null) {
         return cached;
      } else {
         int[] found = scanLamps(level, sectionX, sectionY, sectionZ);
         this.lampSections.put(key, found);
         return found;
      }
   }

   private static int[] scanLamps(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getMinSectionY() && sectionY <= level.getMaxSectionY()) {
         LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return NO_LAMPS;
         } else {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (!section.hasOnlyAir() && section.getStates().maybeHas(PrimeChunkFinderModule::isLamp)) {
               IntArrayList lamps = new IntArrayList();

               for (int index = 0; index < 4096; index++) {
                  BlockState state = section.getBlockState(index & 15, index >>> 8, index >>> 4 & 15);
                  if (isLamp(state)) {
                     lamps.add(index << 4 | state.getLightEmission());
                  }
               }

               return lamps.isEmpty() ? NO_LAMPS : lamps.toIntArray();
            } else {
               return NO_LAMPS;
            }
         }
      } else {
         return NO_LAMPS;
      }
   }

   private static boolean isLamp(BlockState state) {
      return state.is(Blocks.TORCH)
         || state.is(Blocks.WALL_TORCH)
         || state.is(Blocks.SOUL_TORCH)
         || state.is(Blocks.SOUL_WALL_TORCH)
         || state.is(Blocks.COPPER_TORCH)
         || state.is(Blocks.COPPER_WALL_TORCH)
         || CaveVines.hasGlowBerries(state);
   }

   private boolean fromAmethyst(int x, int y, int z, int oldValue, int newValue) {
      if (Math.max(oldValue, newValue) > 5) {
         return false;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         if (level != null && this.amethystSectionNear(level, x, y, z)) {
            for (int dx = -5; dx <= 5; dx++) {
               int restX = 5 - Math.abs(dx);

               for (int dy = -restX; dy <= restX; dy++) {
                  int restY = restX - Math.abs(dy);

                  for (int dz = -restY; dz <= restY; dz++) {
                     if (isAmethyst(level.getBlockState(this.cursor.set(x + dx, y + dy, z + dz)))) {
                        return true;
                     }
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean amethystSectionNear(ClientLevel level, int x, int y, int z) {
      for (int sx = x - 5 >> 4; sx <= x + 5 >> 4; sx++) {
         for (int sy = y - 5 >> 4; sy <= y + 5 >> 4; sy++) {
            for (int sz = z - 5 >> 4; sz <= z + 5 >> 4; sz++) {
               long key = SectionPos.asLong(sx, sy, sz);
               if (this.checkedSections.add(key) && sectionHasAmethyst(level, sx, sy, sz)) {
                  this.amethystSections.add(key);
               }

               if (this.amethystSections.contains(key)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean sectionHasAmethyst(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getMinSectionY() && sectionY <= level.getMaxSectionY()) {
         LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return false;
         } else {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            return !section.hasOnlyAir() && section.getStates().maybeHas(PrimeChunkFinderModule::isAmethyst);
         }
      } else {
         return false;
      }
   }

   private boolean fromRedstoneOre(int x, int y, int z, int value) {
      if (value > 9) {
         return false;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         if (level == null) {
            return false;
         } else {
            int reach = 9 - value;

            for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
               for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
                  for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                     for (long ore : this.exposedOres(level, sx, sy, sz)) {
                        int distance = Math.abs(BlockPos.getX(ore) - x) + Math.abs(BlockPos.getY(ore) - y) + Math.abs(BlockPos.getZ(ore) - z);
                        if (distance <= reach) {
                           return true;
                        }
                     }
                  }
               }
            }

            return false;
         }
      }
   }

   private long[] exposedOres(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
      long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
      long[] cached = (long[])this.oreSections.get(key);
      if (cached != null) {
         return cached;
      } else {
         long[] found = this.scanExposedOres(level, sectionX, sectionY, sectionZ);
         this.oreSections.put(key, found);
         return found;
      }
   }

   private long[] scanExposedOres(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
      if (sectionY >= level.getMinSectionY() && sectionY <= level.getMaxSectionY()) {
         LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return NO_ORES;
         } else {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (!section.hasOnlyAir() && section.getStates().maybeHas(PrimeChunkFinderModule::isRedstoneOre)) {
               LongArrayList ores = new LongArrayList();

               for (int ly = 0; ly < 16; ly++) {
                  for (int lz = 0; lz < 16; lz++) {
                     for (int lx = 0; lx < 16; lx++) {
                        if (isRedstoneOre(section.getBlockState(lx, ly, lz))) {
                           int wx = (sectionX << 4) + lx;
                           int wy = (sectionY << 4) + ly;
                           int wz = (sectionZ << 4) + lz;
                           if (this.exposed(level, wx, wy, wz)) {
                              ores.add(BlockPos.asLong(wx, wy, wz));
                           }
                        }
                     }
                  }
               }

               return ores.isEmpty() ? NO_ORES : ores.toLongArray();
            } else {
               return NO_ORES;
            }
         }
      } else {
         return NO_ORES;
      }
   }

   private boolean exposed(ClientLevel level, int x, int y, int z) {
      for (Direction direction : Direction.values()) {
         BlockState neighbour = level.getBlockState(this.cursor.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()));
         if (neighbour.isAir() || !neighbour.canOcclude()) {
            return true;
         }
      }

      return false;
   }

   private static boolean isRedstoneOre(BlockState state) {
      return state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE);
   }

   private static boolean isAmethyst(BlockState state) {
      return state.is(Blocks.AMETHYST_CLUSTER)
         || state.is(Blocks.LARGE_AMETHYST_BUD)
         || state.is(Blocks.MEDIUM_AMETHYST_BUD)
         || state.is(Blocks.SMALL_AMETHYST_BUD)
         || state.is(Blocks.BUDDING_AMETHYST)
         || state.is(Blocks.AMETHYST_BLOCK);
   }

   private Map<Integer, byte[]> createZeroedChunk() {
      Map<Integer, byte[]> sections = new HashMap<>();

      for (int sectionY = this.bottomSection(); sectionY <= this.topSection(); sectionY++) {
         sections.put(sectionY, ZERO_SECTION);
      }

      return sections;
   }

   private int lightSectionY(int index) {
      ClientLevel level = Minecraft.getInstance().level;
      return level == null ? Integer.MIN_VALUE : level.getChunkSource().getLightEngine().getMinLightSection() + index;
   }

   private int bottomSection() {
      return Minecraft.getInstance().level.getMinSectionY();
   }

   private int topSection() {
      return Minecraft.getInstance().level.getMaxSectionY();
   }

   private boolean isWorldSection(int sectionY) {
      return sectionY >= this.bottomSection() && sectionY <= this.topSection();
   }

   private void trimChunkCache() {
      while (this.chunkLight.size() > 5000) {
         Iterator<Long> keys = this.chunkLight.keySet().iterator();
         keys.next();
         keys.remove();
      }
   }

   private void reset() {
      this.chunkLight.clear();
      this.passedChunks.clear();
      this.flaggedChunks.clear();
      this.shownChunks.clear();
      this.redstoneNotified.clear();
      this.lampSections.clear();
      this.goneLamps.clear();
   }

   @Environment(EnvType.CLIENT)
   private static final class ChangeSummary {
      int count;
      int x;
      int y;
      int z;
      int oldValue;
      int newValue;

      void add(int x, int y, int z, int oldValue, int newValue) {
         if (this.count == 0 || Math.abs(newValue - oldValue) > Math.abs(this.newValue - this.oldValue)) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldValue = oldValue;
            this.newValue = newValue;
         }

         this.count++;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class DeepslateToast extends ChallengeToast {
      private static final Component TITLE = Component.literal("Deepslate Level Only");
      private static final Component MESSAGE = Component.literal("Go below Y 0 first.");

      DeepslateToast() {
         super(new ItemStack(Items.DEEPSLATE), TITLE, MESSAGE, true);
      }
   }
}
