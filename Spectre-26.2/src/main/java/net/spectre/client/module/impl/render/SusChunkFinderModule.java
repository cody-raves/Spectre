package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.function.LongConsumer;
import java.util.*;
import net.spectre.client.util.SuspectObjects;
import net.spectre.client.util.SuspectObjects.Category;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.gui.SuspectPanel;
import net.spectre.client.event.impl.Render2DEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.GeodeGlowScan;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.ActivityClues;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

@Environment(EnvType.CLIENT)
public final class SusChunkFinderModule extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 3000000L;
   private static final int MIN_SCANNED_NEIGHBOURS = 3;
   private static final int NO_PACKET_SENSITIVITY = 1;
   private static final int VISIBLE_REFRESH_TICKS = 20;
   private static final int PACKET_MIN_Y = -16;
   private static final int PACKET_MAX_Y = 30;
   private static final String METHOD_CLASSIC = "Classic";
   private static final String METHOD_ANBS = "ANBS+ Scan";
   private static final int GLOW_CELLS_PER_POINT = 12;
   private final ModeSetting method = this.addSetting("method", new ModeSetting("Method", "Classic", "Classic", "ANBS+ Scan", "Activity clues"));
   private final java.util.EnumMap<ActivityClues.Kind,BooleanSetting> clueKinds=new java.util.EnumMap<>(ActivityClues.Kind.class);
   private final NumberSetting clueThreshold=this.addSetting("clue-threshold",new NumberSetting("Bee clue points",2,1,8).step(1));
   private final BooleanSetting naturalContext=this.addSetting("natural-context",new BooleanSetting("Include short / immature growth",false));
   private final NumberSetting kelpLength=this.addSetting("kelp-length",new NumberSetting("Minimum kelp length",16,2,64).step(1));
   private final NumberSetting vineLength=this.addSetting("vine-length",new NumberSetting("Minimum vine length",16,2,64).step(1));
   private final NumberSetting caveVineLength=this.addSetting("cave-vine-length",new NumberSetting("Minimum cave vine length",20,2,64).step(1));
   private final NumberSetting bambooLength=this.addSetting("bamboo-length",new NumberSetting("Minimum bamboo height",14,2,32).step(1));
   private final NumberSetting matureClusters=this.addSetting("mature-clusters",new NumberSetting("Minimum mature crystals",12,2,128).step(1));
   private final NumberSetting maturePercent=this.addSetting("mature-percent",new NumberSetting("Minimum mature crystal %",75,10,100).step(5));
   private final java.util.Map<Long,ActivityClues.Summary> activityReports=new java.util.HashMap<>();
   private final NumberSetting spreadRadius = this.addSetting("spread-radius", new NumberSetting("Simulation Distance", 5.0F, 2.0F, 16.0F).step(1.0F));
   private final NumberSetting sensitivity = this.addSetting("packet-sensitivity", new NumberSetting("Sensitivity", 6.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting objectRange = this.addSetting("object-range",new NumberSetting("Object / inspect range",128,16,256).step(16));
   private final BooleanSetting inspectPanel = this.addSetting("inspect-panel",new BooleanSetting("Look-to-inspect panel",true));
   private final EnumMap<Category,BooleanSetting> objectKinds = new EnumMap<>(Category.class);
   private final EnumMap<Category,ColorSetting> objectColors = new EnumMap<>(Category.class);
   private final Map<Long,List<SuspectObjects.Found>> objectReports = new HashMap<>();
   private final Map<Long,List<BlockPos>> lightReports = new HashMap<>();
   private List<SuspectObjects.Marker> markers = List.of();
   private int objectAge;
   private boolean detailLimited;
   private SuspectObjects.Focus focused;
   private final GeodeGlowScan glow = new GeodeGlowScan();
   private final Long2IntOpenHashMap heat = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap vetoes = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap chunkScore = new Long2IntOpenHashMap();
   private final Long2BooleanOpenHashMap chunkUngrown = new Long2BooleanOpenHashMap();
   private final Long2BooleanOpenHashMap chunkHasPacket = new Long2BooleanOpenHashMap();
   private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
   private boolean visibleDirty = true;
   private int visibleAge;
   private ClientLevel lastLevel;
   private int appliedRadius;

   public SusChunkFinderModule() {
      super("sus-chunk-finder", "Sus Chunk Finder", "Colored object outlines for containers, machines, redstone and base clues. Look at a group for an inventory-style breakdown. Amethyst and growth clues are not proof of a base.", ModuleCategory.BASE_RAID);
      this.method.onChange(this::rescan);
      this.sensitivity.onChange(() -> this.visibleDirty = true);
      this.spreadRadius.visibleWhen(()->this.method.is("ANBS+ Scan"));
      this.sensitivity.visibleWhen(()->this.method.is("ANBS+ Scan"));
      this.clueThreshold.onChange(()->this.visibleDirty=true);
      this.naturalContext.onChange(()->this.visibleDirty=true);
      for(var kind:ActivityClues.Kind.values()){
         var setting=this.addSetting("clue-"+kind.name().toLowerCase(java.util.Locale.ROOT).replace('_','-'),new BooleanSetting(kind.label,true));
         setting.onChange(this::rescan);this.clueKinds.put(kind,setting);
      }
      for(var category:Category.values()){
         var toggle=this.addSetting("objects-"+category.id(),new BooleanSetting(category.label,true));
         toggle.onChange(this::rescan);objectKinds.put(category,toggle);
         objectColors.put(category,this.addSetting("color-"+category.id(),new ColorSetting(category.label+" color",category.color)));
      }
   }

   private void rescan() {
      this.clearAll();
      this.queueLoadedChunks();
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      this.lastLevel = Minecraft.getInstance().level;
      this.queueLoadedChunks();
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.activityReports.clear();
      this.objectReports.clear();this.lightReports.clear();this.markers=List.of();this.focused=null;this.objectAge=0;this.detailLimited=false;
      this.heat.clear();
      this.vetoes.clear();
      this.chunkScore.clear();
      this.chunkUngrown.clear();
      this.chunkHasPacket.clear();
      this.scannedChunks.clear();
      this.queue.clear();
      this.queued.clear();
      this.visible = new Long2IntOpenHashMap();
      this.visibleDirty = true;
      this.appliedRadius = this.spreadRadius.getInt();
   }

   private void queueLoadedChunks() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int radius = mc.options.renderDistance().get() + 1;
         ChunkPos center = mc.player.chunkPosition();

         for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
               if (LevelUtil.isLoaded(mc.level, x, z)) {
                  this.enqueue(ChunkPos.pack(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         this.enqueue(ChunkPos.pack(chunk.getX(), chunk.getZ()));
      } else if (event.packet instanceof ClientboundLightUpdatePacket light && this.method.is("ANBS+ Scan")) {
         this.enqueue(ChunkPos.pack(light.getX(), light.getZ()));
      } else if(event.packet instanceof net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket forget){
         this.forgetChunk(ChunkPos.pack(forget.pos().x(),forget.pos().z()));
      } else {
         if(event.packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket block)blockChanged(block.getPos(),block.getBlockState());
         else if(event.packet instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket section)section.runUpdates(this::blockChanged);
      }
   }

   private void forgetChunk(long key) {
      this.activityReports.remove(key);
      this.objectReports.remove(key);this.lightReports.remove(key);this.objectAge=5;
      int oldHeat = this.chunkScore.remove(key);
      boolean oldUngrown = this.chunkUngrown.remove(key);
      int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
      if (oldHeat > 0) forArea(cx, cz, this.appliedRadius, area -> subtract(this.heat, area, oldHeat));
      if (oldUngrown) forArea(cx, cz, this.appliedRadius, area -> subtract(this.vetoes, area, 1));
      this.chunkHasPacket.remove(key);
      this.scannedChunks.remove(key);
      this.visible.remove(key);
      this.visibleDirty = true;
      // A queued entry may still exist; the tick skips it while its chunk is unloaded.
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = level;
         this.queueLoadedChunks();
      }

      if (level != null) {
         if (this.spreadRadius.getInt() != this.appliedRadius) {
            this.clearAll();
            this.queueLoadedChunks();
         }

         long deadline = System.nanoTime() + 3000000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               this.updateChunk(key, level.getChunk(cx, cz), level);
               this.scannedChunks.add(key);
               this.visibleDirty = true;
            }
         }

         if (this.visibleDirty || ++this.visibleAge >= 20) {
            this.visible = this.computeVisible(level);
            this.visibleDirty = false;
            this.visibleAge = 0;
         }
         if(++this.objectAge>=5){this.objectAge=0;this.refreshObjects(level);}
      }
   }

   private void updateChunk(long key, LevelChunk chunk, ClientLevel level) {
      var found=new ArrayList<SuspectObjects.Found>();var summary=new ActivityClues.Summary();
      int sectionY=chunk.getMinSectionY();
      for(var section:chunk.getSections()){
         int baseY=sectionY++*16;
         if(section!=null&&!section.hasOnlyAir()&&section.maybeHas(s->SuspectObjects.category(s)!=null)){
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){
               var state=section.getBlockState(x,y,z);var category=SuspectObjects.category(state);
               if(category==null)continue;
               if(selectedClue(state))summary.add(state);
               if(objectKinds.get(category).get()&&(category!=Category.GROWTH||selectedClue(state)))
                  found.add(new SuspectObjects.Found(new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z),state,category));
            }
         }
      }
      if(found.isEmpty())objectReports.remove(key);else objectReports.put(key,List.copyOf(found));
      if(summary.empty())this.activityReports.remove(key);else this.activityReports.put(key,summary);
      if(this.method.is("Activity clues")){
         return;
      }
      int r = this.spreadRadius.getInt();
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      int oldHeat = this.chunkScore.getOrDefault(key, 0);
      boolean oldUngrown = this.chunkUngrown.getOrDefault(key, false);
      if (oldHeat > 0) {
         forArea(cx, cz, r, area -> subtract(this.heat, area, oldHeat));
      }

      if (oldUngrown) {
         forArea(cx, cz, r, area -> subtract(this.vetoes, area, 1));
      }

      this.chunkHasPacket.put(key, hasBlocksInRange(chunk, -16, 30));
      SusChunkFinderModule.ChunkScore score = this.scoreChunk(chunk, level);
      this.chunkScore.put(key, score.selfHeat());
      this.chunkUngrown.put(key, score.hasUngrown());
      if (score.hasUngrown()) {
         forArea(cx, cz, r, area -> this.vetoes.addTo(area, 1));
      }

      if (score.selfHeat() > 0) {
         forArea(cx, cz, r, area -> this.heat.addTo(area, score.selfHeat()));
      }
   }

   private static void forArea(int cx, int cz, int r, LongConsumer action) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            action.accept(ChunkPos.pack(cx + dx, cz + dz));
         }
      }
   }

   private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
      int left = map.get(key) - amount;
      if (left > 0) {
         map.put(key, left);
      } else {
         map.remove(key);
      }
   }

   private static boolean hasBlocksInRange(LevelChunk chunk, int minY, int maxY) {
      LevelChunkSection[] sections = chunk.getSections();
      int minSectionY = chunk.getMinSectionY();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section != null && !section.hasOnlyAir()) {
            int baseY = minSectionY + i << 4;
            if (baseY + 15 >= minY && baseY <= maxY) {
               for (int y = 0; y < 16; y++) {
                  int worldY = baseY + y;
                  if (worldY >= minY && worldY <= maxY) {
                     for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                           if (!section.getBlockState(x, y, z).isAir()) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private SusChunkFinderModule.ChunkScore scoreChunk(LevelChunk chunk, ClientLevel level) {
      if (this.method.is("ANBS+ Scan")) {
         var hits=new ArrayList<BlockPos>();
         int cells=this.glow.scan(level,chunk,pos->{if(hits.size()<2048)hits.add(BlockPos.of(pos));});
         long key=ChunkPos.pack(chunk.getPos().x(),chunk.getPos().z());
         if(hits.isEmpty())lightReports.remove(key);else lightReports.put(key,List.copyOf(hits));
         return new SusChunkFinderModule.ChunkScore(cells / 12, false);
      } else {
         LevelChunkSection[] sections = chunk.getSections();
         int minSectionY = chunk.getMinSectionY();
         ChunkPos pos = chunk.getPos();
         boolean paletteOnly = isDonutFolia();
         int grown = 0;
         int ungrown = 0;
         boolean sawBud = false;
         boolean sawCluster = false;
         MutableBlockPos cursor = new MutableBlockPos();

         for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir()) {
               if (paletteOnly) {
                  if (!sawBud && section.maybeHas(SusChunkFinderModule::isBud)) {
                     sawBud = true;
                  }

                  if (!sawCluster && section.maybeHas(statex -> statex.is(Blocks.AMETHYST_CLUSTER))) {
                     sawCluster = true;
                  }
               } else if (section.maybeHas(SusChunkFinderModule::isAmethystGrowth)) {
                  int baseY = minSectionY + i << 4;

                  for (int x = 0; x < 16; x++) {
                     for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                           BlockState state = section.getBlockState(x, y, z);
                           if (state.is(Blocks.AMETHYST_CLUSTER)) {
                              grown++;
                           } else if (isBud(state) && state.hasProperty(BlockStateProperties.FACING)) {
                              Direction facing = state.getValue(BlockStateProperties.FACING);
                              cursor.set(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z).move(facing.getOpposite());
                              if (level.getBlockState(cursor).is(Blocks.BUDDING_AMETHYST)) {
                                 ungrown++;
                              } else {
                                 grown++;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (paletteOnly) {
            if (sawBud) {
               ungrown++;
            } else if (sawCluster) {
               grown++;
            }
         }

         return new SusChunkFinderModule.ChunkScore(grown, ungrown > 0);
      }
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isAmethystGrowth(BlockState state) {
      return state.is(Blocks.AMETHYST_CLUSTER) || isBud(state);
   }

   private static boolean isDonutFolia() {
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      if (connection == null) {
         return false;
      } else {
         String brand = connection.serverBrand();
         return brand != null && brand.contains("DonutFolia");
      }
   }

   private Long2IntOpenHashMap computeVisible(ClientLevel level) {
      if(this.method.is("Activity clues")){
         var result=new Long2IntOpenHashMap();
         this.activityReports.forEach((key,report)->{
            if(LevelUtil.isLoaded(level,ChunkPos.getX(key),ChunkPos.getZ(key))&&(report.score()>=this.clueThreshold.getInt()||this.naturalContext.get()))result.put(key.longValue(),report.score());
         });return result;
      }
      Long2IntOpenHashMap eligible = new Long2IntOpenHashMap();
      ObjectIterator withPacket = this.heat.long2IntEntrySet().iterator();

      while (withPacket.hasNext()) {
         Entry entry = (Entry)withPacket.next();
         long key = entry.getLongKey();
         if (!this.vetoes.containsKey(key)) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (LevelUtil.isLoaded(level, cx, cz) && this.scannedNeighbours(cx, cz) >= 3) {
               eligible.put(key, entry.getIntValue());
            }
         }
      }

      int withPacketx = this.sensitivity.getInt();
      int reach = this.appliedRadius;
      Long2IntOpenHashMap kept = new Long2IntOpenHashMap();
      ObjectIterator var6 = eligible.long2IntEntrySet().iterator();

      while (var6.hasNext()) {
         Entry entry = (Entry)var6.next();
         long key = entry.getLongKey();
         int value = entry.getIntValue();
         int threshold = this.chunkHasPacket.getOrDefault(key, true) ? withPacketx : 1;
         if (value >= threshold && isPeak(eligible, key, value, reach)) {
            kept.put(key, value);
         }
      }

      return kept;
   }

   private static boolean isPeak(Long2IntOpenHashMap field, long key, int value, int reach) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if ((dx != 0 || dz != 0) && field.get(ChunkPos.pack(cx + dx, cz + dz)) > value) {
               return false;
            }
         }
      }

      return true;
   }

   private int scannedNeighbours(int cx, int cz) {
      int count = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && this.scannedChunks.contains(ChunkPos.pack(cx + dx, cz + dz))) {
               count++;
            }
         }
      }

      return count;
   }

   private void refreshObjects(ClientLevel level) {
      var mc=Minecraft.getInstance();if(mc.player==null)return;
      Vec3 eye=mc.player.getEyePosition();double rangeSq=objectRange.get()*objectRange.get();
      var candidates=new ArrayList<SuspectObjects.Found>();
      var geodes=new HashMap<BlockPos,SuspectObjects.Found>();
      var plants=new HashMap<BlockPos,SuspectObjects.Found>();
      for(var entry:objectReports.entrySet()){
         long key=entry.getKey();if(!LevelUtil.isLoaded(level,ChunkPos.getX(key),ChunkPos.getZ(key)))continue;
         for(var found:entry.getValue()){
            if(Vec3.atCenterOf(found.pos()).distanceToSqr(eye)>rangeSq)continue;
            if(found.category()==Category.GROWTH){
               var kind=ActivityClues.kind(found.state());
               if(kind==ActivityClues.Kind.KELP||kind==ActivityClues.Kind.VINES||kind==ActivityClues.Kind.CAVE_VINES||kind==ActivityClues.Kind.BAMBOO){plants.put(found.pos(),found);continue;}
               var clue=new ActivityClues.Summary();clue.add(found.state());
               var chunkReport=activityReports.get(key);
               if(!naturalContext.get()&&(clue.score()==0||chunkReport==null||chunkReport.score()<clueThreshold.getInt()))continue;
            }
            if(found.category()==Category.GEODES){
               if(clueKinds.get(ActivityClues.Kind.AMETHYST).get())geodes.put(found.pos(),found);
            }else candidates.add(found);
         }
      }
      candidates.sort(Comparator.comparingDouble(f->Vec3.atCenterOf(f.pos()).distanceToSqr(eye)));
      detailLimited=candidates.size()>2048;
      var next=new ArrayList<SuspectObjects.Marker>();
      for(int i=0;i<Math.min(2048,candidates.size());i++)next.add(SuspectObjects.block(candidates.get(i),level));
      next.addAll(SuspectObjects.geodes(geodes,matureClusters.getInt(),maturePercent.getInt(),naturalContext.get()));
      next.addAll(SuspectObjects.plants(plants,kind->switch(kind){case KELP->kelpLength.getInt();case VINES->vineLength.getInt();case CAVE_VINES->caveVineLength.getInt();case BAMBOO->bambooLength.getInt();default->Integer.MAX_VALUE;},naturalContext.get()));
      if(objectKinds.get(Category.DISPLAYS).get())for(var entity:level.entitiesForRendering()){
         net.minecraft.world.item.Item icon=null;
         if(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)icon=net.minecraft.world.item.Items.ARMOR_STAND;
         else if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame)icon=net.minecraft.world.item.Items.ITEM_FRAME;
         if(icon!=null&&entity.isAlive()&&entity.distanceToSqr(eye)<=rangeSq){
            var box=entity.getBoundingBox();next.add(new SuspectObjects.Marker(box,List.of(box),Category.DISPLAYS,Map.of(SuspectObjects.Icon.item(icon),1),icon.getDefaultInstance().getHoverName().getString()));
         }
      }
      if(method.is("ANBS+ Scan")&&objectKinds.get(Category.ANOMALIES).get())for(var entry:lightReports.entrySet()){
         if(!visible.containsKey(entry.getKey()))continue;
         for(var pos:entry.getValue())if(Vec3.atCenterOf(pos).distanceToSqr(eye)<=rangeSq){
            var box=new AABB(pos);next.add(new SuspectObjects.Marker(box,List.of(box),Category.ANOMALIES,Map.of(SuspectObjects.Icon.item(net.minecraft.world.item.Items.AMETHYST_SHARD),1),"Light anomaly"));
         }
      }
      next.sort(Comparator.comparingDouble(m->m.bounds().distanceToSqr(eye)));
      if(next.size()>2048){detailLimited=true;next.subList(2048,next.size()).clear();}
      markers=List.copyOf(next);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
      var eye=mc.gameRenderer.mainCamera().position();var lines=FoglessLines.forFrame(event.partialTick);
      for(var marker:markers){
         if(marker.bounds().distanceToSqr(eye)>objectRange.get()*objectRange.get())continue;
         if(event.frustum!=null&&!event.frustum.isVisible(marker.bounds()))continue;
         int color=objectColors.get(marker.category()).argb()|0xFF000000;
         for(var box:marker.shapes())lines.box(box.inflate(.008),color,1.8F);
      }
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      focused=null;var mc=Minecraft.getInstance();
      if(mc.level==null||mc.player==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!inspectPanel.get())return;
      var camera=mc.gameRenderer.mainCamera();var f=camera.forwardVector();
      focused=SuspectObjects.focus(markers,camera.position(),new Vec3(f.x(),f.y(),f.z()),objectRange.get());
      if(focused!=null)SuspectPanel.draw(event.graphics,focused,c->objectColors.get(c).argb(),detailLimited);
   }

   private boolean selectedClue(BlockState state){var kind=ActivityClues.kind(state);return kind!=null&&this.clueKinds.get(kind).get();}
   private void blockChanged(net.minecraft.core.BlockPos pos,BlockState state){
      long key=ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4);
      if(this.method.is("Activity clues")) {
         if(this.activityReports.containsKey(key)||this.objectReports.containsKey(key)||SuspectObjects.category(state)!=null)this.enqueue(key);
      } else {
         this.enqueue(key);
         // A bud can attach to a support block in the adjacent chunk. Removing or
         // replacing that support changes the bud's classification there too.
         int cx=pos.getX()>>4, cz=pos.getZ()>>4;
         if((pos.getX()&15)==0)this.enqueue(ChunkPos.pack(cx-1,cz));
         if((pos.getX()&15)==15)this.enqueue(ChunkPos.pack(cx+1,cz));
         if((pos.getZ()&15)==0)this.enqueue(ChunkPos.pack(cx,cz-1));
         if((pos.getZ()&15)==15)this.enqueue(ChunkPos.pack(cx,cz+1));
      }
   }

   @Environment(EnvType.CLIENT)
   private record ChunkScore(int selfHeat, boolean hasUngrown) {
   }
}
