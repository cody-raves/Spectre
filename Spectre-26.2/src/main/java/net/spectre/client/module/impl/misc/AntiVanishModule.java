package net.spectre.client.module.impl.misc;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.StaffGhost;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.util.NameFilter;
import net.spectre.client.util.antivanish.AntiVanishHeuristics;
import net.spectre.client.util.antivanish.AntiVanishText;
import net.spectre.client.util.staff.ShardTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class AntiVanishModule extends ClientModule {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final String PROBE_COMMAND = "minecraft:msg";
   private static final int SENSOR_RANGE = 64;
   private static final long SIGNAL_WINDOW_MS = 15000L;
   private static final long DETECTION_TTL_MS = 20000L;
   private static final long CRITICAL_COOLDOWN_MS = 12000L;
   private static final long ANNOUNCE_COOLDOWN_MS = 1500L;
   private static final long ANNOUNCE_WINDOW_MS = 4000L;
   private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
   private static final int MAX_OBSERVATIONS_PER_TICK = 512;
   private static final int CRITICAL_SCORE = 35;
   private static final long SELF_PLACE_TTL_MS = 6000L;
   private static final long CONTAINER_SELF_GRACE_MS = 2500L;
   private static final long SPELL_SETTLE_MS = 1000L;
   private final ConcurrentLinkedQueue<AntiVanishModule.Observation> observations = new ConcurrentLinkedQueue<>();
   private final Map<UUID, AntiVanishModule.KnownPlayer> knownPlayers = new HashMap<>();
   private final Map<String, AntiVanishModule.Detection> detections = new LinkedHashMap<>();
   private final Map<String, Long> signalCooldowns = new HashMap<>();
   private final Map<String, Long> announceCooldowns = new HashMap<>();
   private final Deque<Long> announceTimes = new ArrayDeque<>();
   private final Map<Long, Long> selfPlacedBlocks = new ConcurrentHashMap<>();
   private final Map<UUID, Long> confirmedDepartures = new HashMap<>();
   private final Map<Long, Long> automatedMechanisms = new HashMap<>();
   private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<>();
   private final Deque<AntiVanishModule.Signal> signals = new ArrayDeque<>();
   private final Deque<AntiVanishModule.ExplosionEvent> recentExplosions = new ArrayDeque<>();
   private final Deque<AntiVanishModule.PendingVanish> pendingVanishes = new ArrayDeque<>();
   private final Map<String, AntiVanishModule.HiddenSpell> hiddenSpells = new LinkedHashMap<>();
   private final Deque<AntiVanishModule.RecentMessage> recentMessages = new ArrayDeque<>();
   private boolean serverSendsLeaveMessages;
   private final Set<Integer> completionRequestIds = ConcurrentHashMap.newKeySet();
   private volatile List<String> pendingCompletionNames;
   private int nextCompletionId = 30000;
   private static final long RECENT_MESSAGE_TTL_MS = 8000L;
   private static final long TRUSTED_LISTED_MS = 1500L;
   private final Map<UUID, Long> listedSinceMs = new HashMap<>();
   private final ShardTracker shard = new ShardTracker();
   private int tickCounter;
   private volatile long lastLocalActionMs;
   private long lastContainerActivityMs;
   private long lastCriticalMs;
   private long criticalUntilMs;
   private String criticalWatcher = "";
   private final Set<UUID> pendingChimes = new HashSet<>();
   private UUID ghostId;
   private String ghostName;

   public AntiVanishModule() {
      super("anti-vanish", "Anti Vanish", "Detects vanished players.", ModuleCategory.DONUT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.resetRuntime();
      if (MC.player != null && MC.level != null) {
         this.trackListedPlayers();
      }
   }

   @Override
   protected void onDisable() {
      this.resetRuntime();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (MC.player != null && MC.level != null && MC.getConnection() != null) {
         if (this.shard.moved(MC)) {
            this.resetRuntime();
         }

         this.playPendingChime();
         this.tickCounter++;
         if (MC.player.containerMenu != MC.player.inventoryMenu) {
            this.lastContainerActivityMs = System.currentTimeMillis();
         }

         this.drainObservations();
         this.processPendingVanishes();
         this.processCompletionProbe();
         if (this.tickCounter % 100 == 0) {
            this.sendCompletionProbe();
         }

         if (this.tickCounter % 10 == 0) {
            this.trackListedPlayers();
            this.reviewSpells();
         }

         if (this.tickCounter % 5 == 0) {
            this.scanInvisiblePlayers();
         }

         this.pruneState();
      } else {
         this.resetRuntime();
      }
   }

   @EventHandler
   public void onPacketSend(PacketSendEvent event) {
      this.handleOutbound(event.packet);
   }

   private void handleOutbound(Packet<?> packet) {
      if (packet != null) {
         if (packet instanceof ServerboundUseItemOnPacket useOn) {
            this.lastLocalActionMs = System.currentTimeMillis();
            if (useOn.getHitResult() != null && useOn.getHitResult().getBlockPos() != null) {
               long until = System.currentTimeMillis() + 6000L;
               BlockPos hit = useOn.getHitResult().getBlockPos();
               BlockPos adjacent = hit.relative(useOn.getHitResult().getDirection());
               this.selfPlacedBlocks.put(hit.asLong(), until);
               this.selfPlacedBlocks.put(adjacent.asLong(), until);
               String itemPath = "";
               if (MC.player != null && useOn.getHand() != null) {
                  Identifier itemId = BuiltInRegistries.ITEM.getKey(MC.player.getItemInHand(useOn.getHand()).getItem());
                  itemPath = AntiVanishHeuristics.path(itemId == null ? "" : itemId.toString());
               }

               markSelfMultiBlockFootprint(hit, itemPath, this.selfPlacedBlocks, until);
               markSelfMultiBlockFootprint(adjacent, itemPath, this.selfPlacedBlocks, until);
            }
         } else {
            if (packet instanceof ServerboundPlayerActionPacket
               || packet instanceof ServerboundUseItemPacket
               || packet instanceof ServerboundInteractPacket
               || packet instanceof ServerboundSwingPacket) {
               this.lastLocalActionMs = System.currentTimeMillis();
            }
         }
      }
   }

   static void markSelfMultiBlockFootprint(BlockPos pos, String path, Map<Long, Long> targets, long until) {
      if (pos != null && targets != null) {
         targets.put(pos.asLong(), until);
         String id = path == null ? "" : path;
         boolean vertical = id.endsWith("_door") && !id.endsWith("trapdoor")
            || id.contains("sunflower")
            || id.contains("lilac")
            || id.contains("rose_bush")
            || id.contains("peony")
            || id.contains("tall_grass")
            || id.contains("large_fern")
            || id.contains("pitcher_plant");
         if (vertical) {
            targets.put(pos.above().asLong(), until);
            targets.put(pos.below().asLong(), until);
         }

         if (id.endsWith("_bed")) {
            targets.put(pos.offset(1, 0, 0).asLong(), until);
            targets.put(pos.offset(-1, 0, 0).asLong(), until);
            targets.put(pos.offset(0, 0, 1).asLong(), until);
            targets.put(pos.offset(0, 0, -1).asLong(), until);
         }
      }
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (this.handleInbound(event.packet)) {
         event.setCancelled(true);
      }
   }

   private boolean handleInbound(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
         if (remove.profileIds().size() < 4) {
            for (UUID id : remove.profileIds()) {
               this.observations.offer(AntiVanishModule.Observation.tabRemove(id));
            }
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info && info.actions().contains(Action.UPDATE_LISTED)) {
         int unlisted = 0;

         for (Entry entry : info.entries()) {
            if (entry != null && !entry.listed()) {
               unlisted++;
            }
         }

         if (unlisted > 0 && unlisted < 4) {
            for (Entry entryx : info.entries()) {
               if (entryx != null && !entryx.listed()) {
                  this.observations.offer(AntiVanishModule.Observation.tabHide(entryx.profileId()));
               }
            }
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info && info.actions().contains(Action.UPDATE_GAME_MODE)) {
         this.queueGamemodeUpdates(info);
      } else if (packet instanceof ClientboundSystemChatPacket chat) {
         String text = chat.content() == null ? "" : chat.content().getString();
         if (!text.isBlank()) {
            this.observations.offer(AntiVanishModule.Observation.systemChat(text));
         }

         String departedName = departedPlayerName(chat.content());
         if (!departedName.isBlank()) {
            this.observations.offer(AntiVanishModule.Observation.playerLeft(departedName));
         }
      } else {
         if (packet instanceof ClientboundCommandSuggestionsPacket suggestions && this.completionRequestIds.remove(suggestions.id())) {
            List<String> names = new ArrayList<>();

            for (Suggestion suggestion : suggestions.toSuggestions().getList()) {
               String textx = suggestion.getText();
               if (textx != null && !textx.isBlank()) {
                  names.add(textx.trim());
               }
            }

            this.pendingCompletionNames = names;
            return true;
         }

         if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            this.observations.offer(AntiVanishModule.Observation.entityMetadata(metadata.id()));
         } else if (packet instanceof ClientboundExplodePacket explosion) {
            this.observations.offer(AntiVanishModule.Observation.explosion(explosion.center(), explosion.radius()));
         } else if (packet instanceof ClientboundSoundEntityPacket sound) {
            this.observations.offer(AntiVanishModule.Observation.entitySound(sound.getId(), soundId(sound.getSound().value().location())));
         } else if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particles.getParticle().getType());
            this.observations
               .offer(
                  AntiVanishModule.Observation.position(
                     AntiVanishModule.ObservationType.PARTICLE, particles.getX(), particles.getY(), particles.getZ(), id == null ? "" : id.toString()
                  )
               );
         } else if (packet instanceof ClientboundSoundPacket sound) {
            this.onSoundPacket(sound);
         }
      }

      return false;
   }

   private void onSoundPacket(ClientboundSoundPacket packet) {
      if (packet != null && packet.getSound() != null && packet.getSound().value() != null) {
         String id = soundId(packet.getSound().value().location());
         this.observations
            .offer(AntiVanishModule.Observation.position(AntiVanishModule.ObservationType.POSITIONAL_SOUND, packet.getX(), packet.getY(), packet.getZ(), id));
      }
   }

   public static boolean shouldShowHud() {
      AntiVanishModule module = instance();
      return module != null && module.isEnabled() && module.hasHudContent();
   }

   private boolean hasHudContent() {
      long now = System.currentTimeMillis();
      if (now < this.criticalUntilMs) {
         return true;
      } else {
         for (AntiVanishModule.Detection detection : this.detections.values()) {
            if (detection.expiresAt > now && detectionWorthShowing(detection)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean detectionWorthShowing(AntiVanishModule.Detection detection) {
      if (detection == null) {
         return false;
      } else {
         String name = detection.name == null ? "" : detection.name.trim();
         return !name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name) && !"CRITICAL".equalsIgnoreCase(name);
      }
   }

   public static String hudTag(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "WATCH";
      } else if ("CRITICAL".equalsIgnoreCase(entry.name())) {
         return "ALERT";
      } else {
         String lower = entry.reason() == null ? "" : entry.reason().toLowerCase(Locale.ROOT);
         if (lower.startsWith("vanish event")) {
            return "VANISH";
         } else if (lower.startsWith("invisible entity")) {
            return "INVIS";
         } else if (lower.startsWith("suspicious sound")) {
            return "SOUND";
         } else {
            return lower.startsWith("ghost particle") ? "PARTICLE" : "WATCH";
         }
      }
   }

   public static String hudValue(AntiVanishModule.HudEntry entry) {
      if (entry == null) {
         return "Staff";
      } else if ("CRITICAL".equalsIgnoreCase(entry.name())) {
         return shortName(entry.reason());
      } else {
         String name = entry.name() == null ? "" : entry.name().trim();
         return !name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name) ? shortName(name) : "Staff";
      }
   }

   private static String shortName(String name) {
      String shown = name == null ? "" : NameFilter.apply(name.trim());
      return shown.length() <= 16 ? shown : shown.substring(0, 16);
   }

   public static List<AntiVanishModule.HudEntry> hudEntries() {
      AntiVanishModule module = instance();
      return module != null && shouldShowHud() ? module.hudSnapshot() : List.of();
   }

   private static AntiVanishModule instance() {
      return ModuleRegistry.get(AntiVanishModule.class);
   }

   private void drainObservations() {
      for (int i = 0; i < 512; i++) {
         AntiVanishModule.Observation observation = this.observations.poll();
         if (observation == null) {
            break;
         }

         this.processObservation(observation);
      }

      while (this.observations.size() > 4096) {
         this.observations.poll();
      }
   }

   private void processObservation(AntiVanishModule.Observation observation) {
      switch (observation.type) {
         case TAB_REMOVE:
            this.handleTabRemoval(observation.profileId);
            break;
         case TAB_HIDE:
            this.handleTabHidden(observation.profileId);
            break;
         case PLAYER_LEFT:
            this.serverSendsLeaveMessages = true;
            this.confirmDeparture(observation.detail);
            break;
         case SYSTEM_CHAT:
            this.cacheRecentMessage(observation.detail);
            break;
         case ENTITY_METADATA:
            this.inspectInvisibleEntity(observation.entityId);
            break;
         case POSITIONAL_SOUND:
            this.inspectPositionalSound(observation);
            break;
         case ENTITY_SOUND:
            this.inspectEntitySound(observation);
            break;
         case PARTICLE:
            this.inspectParticle(observation);
            break;
         case EXPLOSION:
            this.rememberExplosion(observation);
            break;
         case GAMEMODE:
            this.announceGamemode(observation.profileId, observation.detail);
      }
   }

   private void trackListedPlayers() {
      if (MC.getConnection() != null) {
         long now = System.currentTimeMillis();

         for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().id() != null) {
               UUID uuid = info.getProfile().id();
               if (MC.player == null || !MC.player.getUUID().equals(uuid)) {
                  this.listedSinceMs.putIfAbsent(uuid, now);
                  String name = info.getProfile().name();
                  if (name != null && !this.knownPlayers.containsKey(uuid)) {
                     this.knownPlayers.put(uuid, new AntiVanishModule.KnownPlayer(uuid, name));
                  }

                  if (name != null && !this.hiddenSpells.isEmpty()) {
                     this.endSpell(name, false, 1000L);
                  }
               }
            }
         }

         if (this.listedSinceMs.size() > 1024 || this.knownPlayers.size() > 1024) {
            Set<UUID> connected = new HashSet<>();

            for (PlayerInfo infox : MC.getConnection().getOnlinePlayers()) {
               if (infox != null && infox.getProfile() != null) {
                  connected.add(infox.getProfile().id());
               }
            }

            this.listedSinceMs.keySet().retainAll(connected);
            this.knownPlayers.keySet().retainAll(connected);
         }
      }
   }

   private boolean trustedListed(UUID uuid) {
      Long since = uuid == null ? null : this.listedSinceMs.get(uuid);
      return since != null && System.currentTimeMillis() - since >= 1500L;
   }

   private boolean credibleSubject(UUID uuid, String name) {
      return uuid != null && AntiVanishText.isUsername(name) && StaffListModule.isStaffName(name) && this.trustedListed(uuid);
   }

   private void handleTabRemoval(UUID uuid) {
      if (uuid != null && !MC.player.getUUID().equals(uuid)) {
         String name = this.knownName(uuid);
         if (this.credibleSubject(uuid, name) && AntiVanishText.isPlausiblePlayerName(name)) {
            this.pendingVanishes.removeIf(pending -> pending.uuid.equals(uuid));
            this.pendingVanishes.addLast(new AntiVanishModule.PendingVanish(uuid, name, this.tickCounter + 20));
         }
      }
   }

   private void handleTabHidden(UUID uuid) {
      if (uuid != null && !MC.player.getUUID().equals(uuid)) {
         AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
         String name = known != null && known.name != null ? known.name : this.knownName(uuid);
         if (this.credibleSubject(uuid, name) && AntiVanishText.isPlausiblePlayerName(name)) {
            if (!this.recentMessageNames(name)) {
               this.reportHidden(uuid, name, false, "Vanish Event: hidden from TAB", "hidden from TAB", 100);
            }
         }
      }
   }

   private String knownName(UUID uuid) {
      AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
      if (known != null && known.name != null && !known.name.isBlank()) {
         return known.name;
      } else {
         if (MC.getConnection() != null) {
            PlayerInfo info = MC.getConnection().getPlayerInfo(uuid);
            if (info != null && info.getProfile() != null) {
               return info.getProfile().name();
            }
         }

         return null;
      }
   }

   private UUID knownUuid(String name) {
      for (AntiVanishModule.KnownPlayer known : this.knownPlayers.values()) {
         if (known.name.equalsIgnoreCase(name)) {
            return known.uuid;
         }
      }

      return null;
   }

   private void confirmDeparture(String displayedName) {
      long now = System.currentTimeMillis();

      for (AntiVanishModule.KnownPlayer known : this.knownPlayers.values()) {
         if (AntiVanishText.containsPlayerName(displayedName, known.name)) {
            this.confirmedDepartures.put(known.uuid, now + 5000L);
            this.pendingVanishes.removeIf(pending -> pending.uuid.equals(known.uuid));
            this.hiddenSpells.remove(spellKey(known.name));
            AntiVanishModule.Detection detection = this.detections.get(known.uuid.toString());
            if (detection != null && tabDepartureReason(detection.reason)) {
               this.detections.remove(known.uuid.toString());
            }

            this.signals
               .removeIf(
                  signal -> signal.type == AntiVanishModule.SignalType.VANISH
                     && signal.subject.equalsIgnoreCase(known.name)
                     && tabDepartureReason(signal.reason)
               );
         }
      }
   }

   private void processPendingVanishes() {
      while (!this.pendingVanishes.isEmpty() && this.pendingVanishes.peekFirst().dueTick <= this.tickCounter) {
         AntiVanishModule.PendingVanish pending = this.pendingVanishes.removeFirst();
         if (MC.getConnection().getPlayerInfo(pending.uuid) == null) {
            long now = System.currentTimeMillis();
            if (this.confirmedDepartures.getOrDefault(pending.uuid, 0L) <= now) {
               if (this.recentMessageNames(pending.name)) {
                  this.serverSendsLeaveMessages = true;
               } else {
                  Player remaining = MC.level.getPlayerByUUID(pending.uuid);
                  if (remaining != null && !remaining.isRemoved()) {
                     this.reportHidden(pending.uuid, pending.name, false, "Vanish Event: entity remained", "off TAB, still nearby", 100);
                  } else {
                     String reason = "Vanish Event: silent TAB disappearance";
                     int score = silentTabRemovalScore(this.serverSendsLeaveMessages);
                     if (this.serverSendsLeaveMessages) {
                        this.reportHidden(pending.uuid, pending.name, false, reason, "left TAB silently", score);
                     } else {
                        AntiVanishModule.RegionProof proof = this.regionProof(pending.uuid);
                        if (proof != null) {
                           this.signals.addLast(new AntiVanishModule.Signal(AntiVanishModule.SignalType.VANISH, pending.name, proof, reason, score, now));
                           this.evaluateCritical();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static int silentTabRemovalScore(boolean serverSendsLeaveMessages) {
      return serverSendsLeaveMessages ? 100 : 30;
   }

   static boolean tabDepartureReason(String reason) {
      if (reason == null) {
         return false;
      } else {
         String lower = reason.toLowerCase(Locale.ROOT);
         return lower.startsWith("vanish event:") && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
      }
   }

   private void cacheRecentMessage(String text) {
      if (text != null && !text.isBlank()) {
         long now = System.currentTimeMillis();
         this.recentMessages.addLast(new AntiVanishModule.RecentMessage(text, now));

         while (!this.recentMessages.isEmpty() && (now - this.recentMessages.peekFirst().atMs > 8000L || this.recentMessages.size() > 64)) {
            this.recentMessages.removeFirst();
         }
      }
   }

   private boolean recentMessageNames(String name) {
      if (name != null && !name.isBlank()) {
         long now = System.currentTimeMillis();

         for (AntiVanishModule.RecentMessage message : this.recentMessages) {
            if (now - message.atMs <= 8000L && AntiVanishText.looksLikeLeaveMessage(message.text, name)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void sendCompletionProbe() {
      if (MC.getConnection() != null) {
         String command = "minecraft:msg";
         int id = this.nextCompletionId++;
         if (this.nextCompletionId > 40000) {
            this.nextCompletionId = 30000;
         }

         this.completionRequestIds.add(id);

         while (this.completionRequestIds.size() > 8) {
            this.completionRequestIds.remove(this.completionRequestIds.iterator().next());
         }

         try {
            MC.getConnection().send(new ServerboundCommandSuggestionPacket(id, command.trim() + " "));
         } catch (Throwable var4) {
         }
      }
   }

   private void processCompletionProbe() {
      List<String> current = this.pendingCompletionNames;
      if (current != null) {
         this.pendingCompletionNames = null;
         if (MC.getConnection() != null && MC.player != null) {
            Set<String> tabNames = new HashSet<>();

            for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
               if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                  tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
               }
            }

            String self = MC.player.getName().getString();
            List<String> hidden = new ArrayList<>();

            for (String name : current) {
               if (StaffListModule.isStaffName(name)
                  && AntiVanishText.isPlausiblePlayerName(name)
                  && !name.equalsIgnoreCase(self)
                  && !tabNames.contains(name.toLowerCase(Locale.ROOT))
                  && !this.recentMessageNames(name)) {
                  hidden.add(name);
               }
            }

            if (!hidden.isEmpty() && hidden.size() <= 3) {
               for (String namex : hidden) {
                  this.reportHidden(this.knownUuid(namex), namex, false, "Vanish Event: hidden but targetable", "off TAB, still targetable", 90);
               }
            }
         }
      }
   }

   private static String departedPlayerName(Component component) {
      if (component != null && component.getContents() instanceof TranslatableContents translated && "multiplayer.player.left".equals(translated.getKey())) {
         Object[] args = translated.getArgs();
         if (args.length != 0 && args[0] != null) {
            return args[0] instanceof Component name ? name.getString() : String.valueOf(args[0]);
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   private void reportHidden(UUID uuid, String name, boolean spectator, String reason, String detail, int score) {
      this.upsertDetection(detectionKey(uuid, name), name, reason, score, System.currentTimeMillis() + 20000L);
      AntiVanishModule.HiddenSpell spell = this.hiddenSpells.get(spellKey(name));
      if (spell == null || spell.spectator != spectator) {
         this.hiddenSpells.put(spellKey(name), new AntiVanishModule.HiddenSpell(uuid, name, spectator, detail, this.regionProof(uuid), this.tickCounter));
      }
   }

   private void reviewSpells() {
      Iterator<AntiVanishModule.HiddenSpell> it = this.hiddenSpells.values().iterator();

      while (it.hasNext()) {
         AntiVanishModule.HiddenSpell spell = it.next();
         if (spell.spectator && MC.getConnection().getPlayerInfo(spell.uuid) == null) {
            it.remove();
         } else if (!spell.watched && spell.sinceTick < this.tickCounter) {
            AntiVanishModule.RegionProof proof = this.regionProof(spell.uuid);
            if (proof == null && !spell.told) {
               proof = spell.proof;
            }

            if (proof != null) {
               spell.told = true;
               spell.watched = true;
               this.watchingYou(spell.name, proof);
            } else if (!spell.told) {
               spell.told = true;
               this.news(spell.name, spell.how(), spell.detail);
            }
         }
      }
   }

   private boolean endSpell(String name, boolean spectator, long settleMs) {
      AntiVanishModule.HiddenSpell spell = this.hiddenSpells.get(spellKey(name));
      if (spell != null && spell.spectator == spectator && System.currentTimeMillis() - spell.sinceMs >= settleMs) {
         this.hiddenSpells.remove(spellKey(name));
         this.detections.remove(detectionKey(spell.uuid, spell.name));
         if (spell.told) {
            this.news(spell.name, ModuleChat.body().text("is ").good("visible again").build(), "");
         }

         return true;
      } else {
         return false;
      }
   }

   private AntiVanishModule.RegionProof regionProof(UUID uuid) {
      if (uuid == null) {
         return null;
      } else {
         Player body = MC.level.getPlayerByUUID(uuid);
         if (body != null && body != MC.player) {
            return new AntiVanishModule.RegionProof(uuid, Math.round(body.distanceTo(MC.player)));
         } else {
            return StaffListModule.isInYourRegion(uuid) ? new AntiVanishModule.RegionProof(uuid, -1) : null;
         }
      }
   }

   private static String spellKey(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   private static String detectionKey(UUID uuid, String name) {
      return uuid != null ? uuid.toString() : name;
   }

   private void scanInvisiblePlayers() {
      double rangeSq = this.sensorRangeSq();

      for (Player player : MC.level.players()) {
         if (player != null && player != MC.player && player.isInvisible() && !(player.distanceToSqr(MC.player) > rangeSq) && this.realPlayer(player)) {
            this.triggerSensor(AntiVanishModule.SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
         }
      }
   }

   private void inspectInvisibleEntity(int entityId) {
      if (MC.level.getEntity(entityId) instanceof Player player && player != MC.player && player.isInvisible()) {
         if (!(player.distanceToSqr(MC.player) > this.sensorRangeSq())) {
            if (this.realPlayer(player)) {
               this.triggerSensor(AntiVanishModule.SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
            }
         }
      }
   }

   private boolean realPlayer(Player player) {
      return this.credibleSubject(player.getUUID(), profileName(player));
   }

   private static String profileName(Player player) {
      return player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
   }

   private void inspectPositionalSound(AntiVanishModule.Observation observation) {
      if (AntiVanishHeuristics.suspiciousSound(observation.detail)) {
         Vec3 source = observation.position();
         long now = System.currentTimeMillis();
         if (this.nearPlayer(source)
            && !this.hasVisibleCause(source)
            && !this.isExplosionRelated(source)
            && !this.isPoweredMechanism(source, observation.detail)
            && now - this.lastLocalActionMs >= 1000L
            && !this.recentlySelfPlaced(source)
            && !this.villagerToggledDoor(observation.detail, source)
            && (!this.selfContainerActive() || !isContainerSignal(observation.detail))) {
            this.triggerSensor(AntiVanishModule.SignalType.SOUND, this.locatedSubject(source), "Suspicious Sound: " + shortId(observation.detail), 14, 3000L);
         }
      }
   }

   private void inspectEntitySound(AntiVanishModule.Observation observation) {
      if (AntiVanishHeuristics.suspiciousSound(observation.detail)) {
         if (MC.level.getEntity(observation.entityId) instanceof Player player
            && player != MC.player
            && player.isInvisible()
            && player.distanceToSqr(MC.player) <= this.sensorRangeSq()
            && this.realPlayer(player)) {
            this.triggerSensor(AntiVanishModule.SignalType.SOUND, player, "Suspicious Sound: invisible source", 16, 3000L);
         }
      }
   }

   private void inspectParticle(AntiVanishModule.Observation observation) {
      if (AntiVanishHeuristics.suspiciousParticle(observation.detail)) {
         Vec3 source = observation.position();
         long now = System.currentTimeMillis();
         if (this.nearPlayer(source)
            && !this.hasVisibleCause(source)
            && !this.isExplosionRelated(source)
            && now - this.lastLocalActionMs >= 900L
            && !this.hasAmbientParticleSource(source, observation.detail)) {
            String particle = shortId(observation.detail);
            if (!particle.equals("block") && !particle.contains("smoke") || !this.nearSelf(source, 6.25)) {
               if (!particle.equals("block") && !particle.contains("smoke") || this.particleBurstReady(particle, now)) {
                  this.triggerSensor(
                     AntiVanishModule.SignalType.PARTICLE, this.locatedSubject(source), "Ghost Particle: " + shortId(observation.detail), 16, 3000L
                  );
               }
            }
         }
      }
   }

   private boolean villagerToggledDoor(String blockId, Vec3 source) {
      String path = AntiVanishHeuristics.path(blockId);
      boolean doorLike = path.contains("door") && !path.contains("trapdoor") || path.contains("fence_gate");
      if (doorLike && source != null && MC.level != null) {
         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity.getType() == net.minecraft.world.entity.EntityTypes.VILLAGER && entity.position().distanceToSqr(source) <= 9.0) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean recentlySelfPlaced(Vec3 source) {
      if (source == null) {
         return false;
      } else {
         Long until = this.selfPlacedBlocks.get(BlockPos.containing(source).asLong());
         return until != null && until > System.currentTimeMillis();
      }
   }

   private void rememberExplosion(AntiVanishModule.Observation observation) {
      long now = System.currentTimeMillis();
      this.recentExplosions
         .addLast(new AntiVanishModule.ExplosionEvent(observation.position(), Math.max(2.0, parseDouble(observation.detail, 4.0) + 4.0), now));

      while (this.recentExplosions.size() > 8) {
         this.recentExplosions.removeFirst();
      }
   }

   private boolean particleBurstReady(String particle, long now) {
      Deque<Long> burst = this.weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque<>());
      burst.addLast(now);

      while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
         burst.removeFirst();
      }

      if (burst.size() < 2) {
         return false;
      } else {
         burst.clear();
         return true;
      }
   }

   private void triggerSensor(AntiVanishModule.SignalType type, String where, String reason, int weight, long cooldownMs) {
      this.addSignal(type, where, null, reason, weight, cooldownMs);
   }

   private void triggerSensor(AntiVanishModule.SignalType type, Player player, String reason, int weight, long cooldownMs) {
      this.addSignal(type, profileName(player), this.regionProof(player.getUUID()), reason, weight, cooldownMs);
   }

   private void addSignal(AntiVanishModule.SignalType type, String subject, AntiVanishModule.RegionProof proof, String reason, int weight, long cooldownMs) {
      long now = System.currentTimeMillis();
      String cooldownKey = type.name() + "|" + subject.toLowerCase(Locale.ROOT);
      long last = this.signalCooldowns.getOrDefault(cooldownKey, 0L);
      if (now - last >= cooldownMs) {
         this.signalCooldowns.put(cooldownKey, now);
         this.signals.addLast(new AntiVanishModule.Signal(type, subject, proof, reason, weight, now));
         this.upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + 20000L);
         this.announceTrigger(cooldownKey, subject, proof != null, reason);
         this.evaluateCritical();
      }
   }

   private void evaluateCritical() {
      long now = System.currentTimeMillis();
      this.pruneSignals(now);
      EnumMap<AntiVanishModule.SignalType, Integer> strongest = new EnumMap<>(AntiVanishModule.SignalType.class);
      AntiVanishModule.Signal watcher = null;

      for (AntiVanishModule.Signal signal : this.signals) {
         strongest.merge(signal.type, signal.weight, Math::max);
         if (signal.proof != null) {
            watcher = signal;
         }
      }

      int score = strongest.values().stream().mapToInt(Integer::intValue).sum();
      if (watcher != null && strongest.size() >= 2 && score >= 35) {
         if (now - this.lastCriticalMs >= 12000L) {
            this.watchingYou(watcher.subject, watcher.proof);
         }
      }
   }

   private void watchingYou(String name, AntiVanishModule.RegionProof proof) {
      long now = System.currentTimeMillis();
      this.lastCriticalMs = now;
      this.criticalUntilMs = now + 7000L;
      this.criticalWatcher = name;
      this.pendingChimes.add(proof.id());
      this.ghostId = proof.id();
      this.ghostName = name;
   }

   private void playPendingChime() {
      if (!this.pendingChimes.isEmpty()) {
         boolean sirenCovers = this.pendingChimes.stream().allMatch(StaffListModule::alarmedRecently);
         this.pendingChimes.clear();
         if (!sirenCovers) {
            if (MC.getSoundManager() != null) {
               MC.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 1.0F));
            }

            StaffGhost.watching(this.ghostId, this.ghostName, 0);
         }
      }
   }

   private void news(String name, Component what, String detail) {
      ModuleChat.Body line = ModuleChat.body().name(NameFilter.apply(name)).text(" ").append(what);
      if (!detail.isEmpty()) {
         line.text(" (" + detail + ")");
      }

      ModuleChat.send(this, line);
   }

   private void queueGamemodeUpdates(ClientboundPlayerInfoUpdatePacket info) {
      if (!info.actions().contains(Action.ADD_PLAYER)) {
         for (Entry entry : info.entries()) {
            if (entry != null && entry.gameMode() != null && entry.profileId() != null) {
               this.observations.offer(AntiVanishModule.Observation.gamemode(entry.profileId(), entry.gameMode().getName()));
            }
         }
      }
   }

   private void announceGamemode(UUID uuid, String gameMode) {
      if (uuid != null && (MC.player == null || !MC.player.getUUID().equals(uuid))) {
         AntiVanishModule.KnownPlayer known = this.knownPlayers.get(uuid);
         if (known != null && StaffListModule.isStaffName(known.name)) {
            if (GameType.SPECTATOR.getName().equals(gameMode)) {
               if (this.credibleSubject(uuid, known.name)) {
                  this.reportHidden(uuid, known.name, true, "Vanish Event: switched to spectator", "", 100);
                  return;
               }
            } else if (this.endSpell(known.name, true, 0L)) {
               return;
            }

            ModuleChat.send(this, StaffListModule.switchedTo(ModuleChat.body().name(NameFilter.apply(known.name)).text(" "), gameMode));
         }
      }
   }

   private void announceTrigger(String eventKey, String subject, boolean named, String reason) {
      long now = System.currentTimeMillis();
      Long last = this.announceCooldowns.get(eventKey);
      if (last == null || now - last >= 1500L) {
         while (!this.announceTimes.isEmpty() && now - this.announceTimes.peekFirst() > 4000L) {
            this.announceTimes.removeFirst();
         }

         if (this.announceTimes.size() < 6) {
            this.announceCooldowns.put(eventKey, now);
            this.announceTimes.addLast(now);
            int colon = reason.indexOf(": ");
            ModuleChat.Body line = colon < 0
               ? ModuleChat.body().warn(reason)
               : ModuleChat.body().warn(reason.substring(0, colon)).text(reason.substring(colon));
            boolean located = subject != null && !subject.isBlank() && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
            if (located) {
               line.text(" (");
               if (named) {
                  line.name(NameFilter.apply(subject));
               } else {
                  line.value(subject);
               }

               line.text(")");
            }

            ModuleChat.send(this, line);
         }
      }
   }

   private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
      AntiVanishModule.Detection existing = this.detections.get(key);
      if (existing == null) {
         this.detections.put(key, new AntiVanishModule.Detection(name, reason, score, expiresAt));
      } else {
         existing.name = name;
         existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
         if (score >= existing.score) {
            existing.reason = reason;
            existing.score = score;
         }
      }
   }

   private List<AntiVanishModule.HudEntry> hudSnapshot() {
      long now = System.currentTimeMillis();
      List<AntiVanishModule.HudEntry> out = new ArrayList<>();
      if (now < this.criticalUntilMs) {
         out.add(new AntiVanishModule.HudEntry("CRITICAL", this.criticalWatcher));
      }

      Set<String> seenRows = new HashSet<>();
      this.detections
         .values()
         .stream()
         .filter(detection -> detection.expiresAt > now)
         .filter(AntiVanishModule::detectionWorthShowing)
         .sorted(
            Comparator.<AntiVanishModule.Detection>comparingInt(detection -> detection.score)
               .reversed()
               .thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER)
         )
         .map(detection -> new AntiVanishModule.HudEntry(detection.name, detection.reason))
         .filter(entry -> seenRows.add(hudTag(entry) + "\u0000" + hudValue(entry)))
         .limit(4L)
         .forEach(out::add);
      return List.copyOf(out);
   }

   private void pruneState() {
      long now = System.currentTimeMillis();
      this.detections.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
      this.signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
      this.announceCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
      this.selfPlacedBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.confirmedDepartures.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.automatedMechanisms.entrySet().removeIf(entry -> entry.getValue() <= now);
      this.weakParticleBursts.values().removeIf(burst -> {
         while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
            burst.removeFirst();
         }

         return burst.isEmpty();
      });

      while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
         this.recentExplosions.removeFirst();
      }

      this.pruneSignals(now);
   }

   private void pruneSignals(long now) {
      while (!this.signals.isEmpty() && now - this.signals.peekFirst().timeMs > 15000L) {
         this.signals.removeFirst();
      }
   }

   private boolean nearPlayer(Vec3 source) {
      return source != null && source.distanceToSqr(MC.player.position()) <= this.sensorRangeSq();
   }

   private boolean nearSelf(Vec3 source, double maxDistSq) {
      return source != null && MC.player != null && source.distanceToSqr(MC.player.position()) < maxDistSq;
   }

   private boolean selfContainerActive() {
      return System.currentTimeMillis() - this.lastContainerActivityMs < 2500L;
   }

   private static boolean isContainerSignal(String id) {
      String path = AntiVanishHeuristics.path(id);
      return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
   }

   private String locatedSubject(Vec3 source) {
      if (source != null && MC.player != null) {
         Vec3 me = MC.player.position();
         double dx = source.x - me.x;
         double dy = source.y - me.y;
         double dz = source.z - me.z;
         long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
         String dir = compass(dx, dz);
         return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
      } else {
         return "nearby";
      }
   }

   private static String compass(double dx, double dz) {
      String ns = dz < -1.0 ? "N" : (dz > 1.0 ? "S" : "");
      String ew = dx > 1.0 ? "E" : (dx < -1.0 ? "W" : "");
      return ns + ew;
   }

   private boolean hasVisibleCause(Vec3 source) {
      if (source == null) {
         return true;
      } else {
         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity != MC.player) {
               if (entity instanceof Player player) {
                  if (!player.isInvisible() && entity.position().distanceToSqr(source) <= 16.0) {
                     return true;
                  }
               } else if (entity instanceof Projectile && entity.position().distanceToSqr(source) <= 16.0) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean isExplosionRelated(Vec3 source) {
      if (source == null) {
         return false;
      } else {
         long now = System.currentTimeMillis();

         while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
            this.recentExplosions.removeFirst();
         }

         for (AntiVanishModule.ExplosionEvent explosion : this.recentExplosions) {
            if (explosion.center.distanceToSqr(source) <= explosion.radius * explosion.radius) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasAmbientParticleSource(Vec3 source, String particleId) {
      if (source != null && shortId(particleId).contains("smoke")) {
         BlockPos center = BlockPos.containing(source);

         for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
               for (int dz = -2; dz <= 2; dz++) {
                  BlockPos pos = center.offset(dx, dy, dz);
                  Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
                  String path = shortId(id == null ? "" : id.toString());
                  if (path.contains("campfire")
                     || path.contains("furnace")
                     || path.contains("smoker")
                     || path.contains("torch")
                     || path.contains("fire")
                     || path.contains("candle")
                     || path.contains("respawn_anchor")) {
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

   private boolean isPoweredMechanism(Vec3 source, String soundId) {
      String path = shortId(soundId);
      if (!path.contains("door") && !path.contains("trapdoor")) {
         return false;
      } else {
         BlockPos center = BlockPos.containing(source);
         long now = System.currentTimeMillis();

         for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = center.offset(0, dy, 0);
            BlockState state = MC.level.getBlockState(pos);
            long key = pos.asLong();
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            if (powered || MC.level.hasNeighborSignal(pos)) {
               this.automatedMechanisms.put(key, now + 5000L);
               return true;
            }

            if (this.automatedMechanisms.getOrDefault(key, 0L) > now) {
               return true;
            }
         }

         return false;
      }
   }

   private double sensorRangeSq() {
      double r = 64.0;
      return r * r;
   }

   private static double parseDouble(String value, double fallback) {
      try {
         return Double.parseDouble(value);
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static String soundId(Identifier id) {
      return id == null ? "" : id.toString();
   }

   private static String shortId(String id) {
      return AntiVanishHeuristics.path(id);
   }

   private void resetRuntime() {
      this.observations.clear();
      this.knownPlayers.clear();
      this.detections.clear();
      this.signalCooldowns.clear();
      this.announceCooldowns.clear();
      this.announceTimes.clear();
      this.selfPlacedBlocks.clear();
      this.confirmedDepartures.clear();
      this.automatedMechanisms.clear();
      this.weakParticleBursts.clear();
      this.signals.clear();
      this.recentExplosions.clear();
      this.pendingVanishes.clear();
      this.hiddenSpells.clear();
      this.shard.clear();
      this.tickCounter = 0;
      this.lastLocalActionMs = 0L;
      this.lastContainerActivityMs = 0L;
      this.lastCriticalMs = 0L;
      this.criticalUntilMs = 0L;
      this.criticalWatcher = "";
      this.pendingChimes.clear();
      this.ghostId = null;
      this.ghostName = null;
      this.recentMessages.clear();
      this.listedSinceMs.clear();
      this.serverSendsLeaveMessages = false;
      this.completionRequestIds.clear();
      this.pendingCompletionNames = null;
      this.nextCompletionId = 30000;
   }

   @Environment(EnvType.CLIENT)
   private static final class Detection {
      String name;
      String reason;
      int score;
      long expiresAt;

      Detection(String name, String reason, int score, long expiresAt) {
         this.name = name;
         this.reason = reason;
         this.score = score;
         this.expiresAt = expiresAt;
      }
   }

   @Environment(EnvType.CLIENT)
   private record ExplosionEvent(Vec3 center, double radius, long timeMs) {
   }

   @Environment(EnvType.CLIENT)
   private static final class HiddenSpell {
      final UUID uuid;
      final String name;
      final boolean spectator;
      final String detail;
      final AntiVanishModule.RegionProof proof;
      final int sinceTick;
      final long sinceMs = System.currentTimeMillis();
      boolean told;
      boolean watched;

      HiddenSpell(UUID uuid, String name, boolean spectator, String detail, AntiVanishModule.RegionProof proof, int sinceTick) {
         this.uuid = uuid;
         this.name = name;
         this.spectator = spectator;
         this.detail = detail;
         this.proof = proof;
         this.sinceTick = sinceTick;
      }

      Component how() {
         return this.spectator
            ? StaffListModule.switchedTo(ModuleChat.body(), GameType.SPECTATOR.getName()).build()
            : ModuleChat.body().danger("vanished").build();
      }
   }

   @Environment(EnvType.CLIENT)
   public record HudEntry(String name, String reason) {
   }

   @Environment(EnvType.CLIENT)
   private record KnownPlayer(UUID uuid, String name) {
   }

   @Environment(EnvType.CLIENT)
   private record Observation(AntiVanishModule.ObservationType type, UUID profileId, int entityId, double x, double y, double z, String detail) {
      static AntiVanishModule.Observation tabRemove(UUID id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.TAB_REMOVE, id, -1, 0.0, 0.0, 0.0, "");
      }

      static AntiVanishModule.Observation tabHide(UUID id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.TAB_HIDE, id, -1, 0.0, 0.0, 0.0, "");
      }

      static AntiVanishModule.Observation gamemode(UUID id, String mode) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.GAMEMODE, id, -1, 0.0, 0.0, 0.0, mode);
      }

      static AntiVanishModule.Observation playerLeft(String name) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.PLAYER_LEFT, null, -1, 0.0, 0.0, 0.0, name);
      }

      static AntiVanishModule.Observation systemChat(String text) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.SYSTEM_CHAT, null, -1, 0.0, 0.0, 0.0, text);
      }

      static AntiVanishModule.Observation entityMetadata(int id) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.ENTITY_METADATA, null, id, 0.0, 0.0, 0.0, "");
      }

      static AntiVanishModule.Observation explosion(Vec3 center, float radius) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.EXPLOSION, null, -1, center.x, center.y, center.z, Float.toString(radius));
      }

      static AntiVanishModule.Observation entitySound(int id, String sound) {
         return new AntiVanishModule.Observation(AntiVanishModule.ObservationType.ENTITY_SOUND, null, id, 0.0, 0.0, 0.0, sound);
      }

      static AntiVanishModule.Observation position(AntiVanishModule.ObservationType type, double x, double y, double z, String detail) {
         return new AntiVanishModule.Observation(type, null, -1, x, y, z, detail);
      }

      Vec3 position() {
         return new Vec3(this.x, this.y, this.z);
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum ObservationType {
      TAB_REMOVE,
      TAB_HIDE,
      PLAYER_LEFT,
      SYSTEM_CHAT,
      ENTITY_METADATA,
      POSITIONAL_SOUND,
      ENTITY_SOUND,
      PARTICLE,
      EXPLOSION,
      GAMEMODE;
   }

   @Environment(EnvType.CLIENT)
   private record PendingVanish(UUID uuid, String name, int dueTick) {
   }

   @Environment(EnvType.CLIENT)
   private record RecentMessage(String text, long atMs) {
   }

   @Environment(EnvType.CLIENT)
   private record RegionProof(UUID id, int distance) {
   }

   @Environment(EnvType.CLIENT)
   private record Signal(AntiVanishModule.SignalType type, String subject, AntiVanishModule.RegionProof proof, String reason, int weight, long timeMs) {
   }

   @Environment(EnvType.CLIENT)
   private static enum SignalType {
      VANISH,
      INVISIBLE,
      PARTICLE,
      SOUND;
   }
}
