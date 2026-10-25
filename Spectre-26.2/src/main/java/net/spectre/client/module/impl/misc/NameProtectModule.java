package net.spectre.client.module.impl.misc;

import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.nameprotect.AliasPolicy;
import net.spectre.client.module.impl.misc.nameprotect.NameMatcher;
import net.spectre.client.module.impl.misc.nameprotect.ProtectedText;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.StringSetting;
import net.spectre.client.util.NameFilter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class NameProtectModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final String OTHERS_KEEP = "Keep";
   private static final String OTHERS_SAME = "Same Label";
   private static final String OTHERS_NUMBERED = "Numbered";
   private static final String DEFAULT_SELF_ALIAS = "You";
   private static final int DEPARTED_MEMORY = 256;
   private static final int SEEN_MEMORY = 8192;
   private static final int MIN_OTHER_NAME_LENGTH = 3;
   private static final int NPC_UUID_VERSION = 2;
   private static final int CHAT_REFRESH_INTERVAL_TICKS = 20;
   private static final int REFUSAL_NOTICE_DELAY_TICKS = 30;
   private static final int RECENT_CHAT_LINES = 100;
   private static final int DISPLAY_CACHE_SLOTS = 64;
   private static volatile NameMatcher active;
   private static volatile NameProtectModule running;
   private static volatile int generation;
   private static volatile boolean failureLogged;
   private static final Object[] cacheInputs = new Object[64];
   private static final Object[] cacheOutputs = new Object[64];
   private static NameMatcher cacheMatcher;
   private static final String[] recentChat = new String[100];
   private static int recentChatNext;
   private final StringSetting selfAlias = this.addSetting("self-alias", new StringSetting("Your Alias", "You"));
   private final StringSetting extraNames = this.addSetting("extra-names", new StringSetting("Extra Names", ""));
   private final ModeSetting others = this.addSetting("others", new ModeSetting("Others", "Keep", "Keep", "Same Label", "Numbered"));
   private final ModeSetting othersLabel = this.addSetting("others-label", new ModeSetting("Others Label", "Player", "Player", "Anonymous", "Unknown"));
   private final LinkedHashMap<String, String> knownOthers = new LinkedHashMap<>();
   private final Map<String, Integer> numbers = new HashMap<>();
   private int nextNumber = 1;
   private final LinkedHashSet<String> seenSkeletons = new LinkedHashSet<>();
   private WeakReference<ClientPacketListener> trackedConnection = new WeakReference<>(null);
   private volatile boolean dirty = true;
   private volatile boolean settingsEdited;
   private int builtListSize = -1;
   private String builtProfileName;
   private NameMatcher matcher = NameMatcher.EMPTY;
   private String refusedAlias;
   private AliasPolicy.Refusal refusalReason;
   private String noticedAlias;
   private int refusalNoticeDelay;
   private boolean chatRefreshPending;
   private int chatRefreshCooldown;

   public NameProtectModule() {
      super("name-protect", "Name Protect", "Shows aliases instead of player names on your screen.", ModuleCategory.MISC);
      this.usesNotifications();
      this.othersLabel.visibleWhen(() -> !this.others.is("Keep"));
      this.extraNames.privateValue();
      this.selfAlias.onChange(this::markEdited);
      this.extraNames.onChange(this::markEdited);
      this.others.onChange(this::markEdited);
      this.othersLabel.onChange(this::markEdited);
   }

   public static Component protect(Component text) {
      NameMatcher current = active;
      if (current != null && text != null) {
         boolean cache = onRenderThread();
         if (cache && cached(current, text) instanceof Component hit) {
            return hit;
         } else {
            Component shown;
            try {
               shown = ProtectedText.rewrite(current, text);
            } catch (RuntimeException var5) {
               reportFailure(var5);
               return text;
            }

            if (cache) {
               remember(text, shown);
            }

            return shown;
         }
      } else {
         return text;
      }
   }

   public static boolean hidesName(String name) {
      NameMatcher current = active;
      return current != null && name != null && current.aliasAt(name, 0, name.length()) != null;
   }

   public static PlayerSkin hiddenHead(UUID profileId, PlayerSkin real) {
      SkinChangeModule skins = ModuleRegistry.get(SkinChangeModule.class);
      if (skins != null && skins.isEnabled()) {
         PlayerSkin worn = skins.skinFor(profileId, real);
         if (worn != real) {
            return worn;
         }
      }

      return DefaultPlayerSkin.get(profileId);
   }

   public static FormattedText protectText(FormattedText text) {
      NameMatcher current = active;
      if (current != null && text != null) {
         boolean cache = onRenderThread();
         if (cache && cached(current, text) instanceof FormattedText hit) {
            return hit;
         } else {
            FormattedText shown;
            try {
               shown = ProtectedText.rewrite(current, text);
            } catch (RuntimeException var5) {
               reportFailure(var5);
               return text;
            }

            if (cache) {
               remember(text, shown);
            }

            return shown;
         }
      } else {
         return text;
      }
   }

   public static FormattedText protectChatLine(FormattedText text) {
      NameMatcher current = active;
      if (current != null && text != null) {
         try {
            if (onRenderThread()) {
               recentChat[recentChatNext] = text.getString();
               recentChatNext = (recentChatNext + 1) % 100;
            }

            return ProtectedText.rewrite(current, text);
         } catch (RuntimeException var3) {
            reportFailure(var3);
            return text;
         }
      } else {
         return text;
      }
   }

   public static int generation() {
      return generation;
   }

   public static void onPlayerInfoApplied(ClientboundPlayerInfoUpdatePacket packet) {
      NameProtectModule module = running;
      if (module != null && packet != null) {
         EnumSet<Action> actions = packet.actions();
         if (actions.contains(Action.ADD_PLAYER) || actions.contains(Action.UPDATE_LISTED)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.isSameThread()) {
               module.syncConnection(mc);
               module.rebuild();
            }
         }
      }
   }

   private static String filterName(String name) {
      NameMatcher current = active;
      return current != null ? current.replace(name) : name;
   }

   private static void reportFailure(RuntimeException failure) {
      if (!failureLogged) {
         failureLogged = true;
         LOGGER.error("Name Protect could not rewrite a text; showing it unchanged", failure);
      }
   }

   private static boolean onRenderThread() {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.isSameThread();
   }

   private static Object cached(NameMatcher current, Object input) {
      if (cacheMatcher != current) {
         clearDisplayCache();
         cacheMatcher = current;
         return null;
      } else {
         int slot = System.identityHashCode(input) & 63;
         return cacheInputs[slot] == input ? cacheOutputs[slot] : null;
      }
   }

   private static void remember(Object input, Object output) {
      int slot = System.identityHashCode(input) & 63;
      cacheInputs[slot] = input;
      cacheOutputs[slot] = output;
   }

   private static void clearDisplayCache() {
      Arrays.fill(cacheInputs, null);
      Arrays.fill(cacheOutputs, null);
      cacheMatcher = null;
   }

   @Override
   protected void onEnable() {
      running = this;
      Minecraft mc = Minecraft.getInstance();
      this.syncConnection(mc);
      this.settingsEdited = false;
      this.rebuild();
      NameFilter.set(NameProtectModule::filterName);
      this.chatRefreshPending = true;
      this.chatRefreshCooldown = 0;
      this.on(TickEvent.TYPE, Stage.PRE, this::onTick);
   }

   @Override
   protected void onDisable() {
      running = null;
      active = null;
      generation++;
      this.matcher = NameMatcher.EMPTY;
      NameFilter.set(null);
      this.chatRefreshPending = false;
      this.dirty = true;
      this.refusedAlias = null;
      this.noticedAlias = null;
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.execute(() -> {
            clearDisplayCache();
            Arrays.fill(recentChat, null);
            if (mc.gui != null) {
               mc.gui.hud.getChat().rescaleChat();
            }
         });
      }
   }

   private void markEdited() {
      this.settingsEdited = true;
      this.dirty = true;
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      this.syncConnection(mc);
      if (listSize(mc.getConnection()) != this.builtListSize) {
         this.dirty = true;
      }

      if (!Objects.equals(profileName(mc), this.builtProfileName)) {
         this.dirty = true;
      }

      if (this.dirty) {
         this.rebuild();
      }

      this.announceRefusal(mc);
      if (this.chatRefreshCooldown > 0) {
         this.chatRefreshCooldown--;
      }

      if (this.chatRefreshPending && this.chatRefreshCooldown == 0 && !chatOpen(mc) && mc.gui != null) {
         this.chatRefreshPending = false;
         this.chatRefreshCooldown = 20;
         mc.gui.hud.getChat().rescaleChat();
      }
   }

   private void syncConnection(Minecraft mc) {
      ClientPacketListener connection = mc != null ? mc.getConnection() : null;
      if (connection != this.trackedConnection.get()) {
         this.trackedConnection = new WeakReference<>(connection);
         this.knownOthers.clear();
         this.numbers.clear();
         this.nextNumber = 1;
         this.builtListSize = -1;
         this.dirty = true;
      }
   }

   private void rebuild() {
      this.dirty = false;
      boolean edited = this.settingsEdited;
      this.settingsEdited = false;
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener connection = mc != null ? mc.getConnection() : null;
      User user = mc != null ? mc.getUser() : null;
      String accountName = NameMatcher.nameCore(user != null ? user.getName() : null);
      this.builtProfileName = profileName(mc);
      String profile = NameMatcher.nameCore(this.builtProfileName);
      this.builtListSize = listSize(connection);
      List<String> extras = new ArrayList<>();

      for (String raw : this.extraNames.get().split("[,;\\s]+")) {
         String extra = NameMatcher.nameCore(raw);
         if (extra != null) {
            extras.add(extra);
         }
      }

      List<String> ownNames = new ArrayList<>(extras.size() + 2);
      if (accountName != null) {
         ownNames.add(accountName);
      }

      if (profile != null) {
         ownNames.add(profile);
      }

      ownNames.addAll(extras);
      this.rememberOnline(connection, accountName, profile);
      String ownAlias = this.resolveOwnAlias(extras);
      Map<String, String> table = new LinkedHashMap<>();

      for (String own : ownNames) {
         put(table, own, ownAlias);
      }

      if (!this.others.is("Keep")) {
         this.trackOthers(connection, ownNames);
         String label = this.othersLabel.get();
         boolean numbered = this.others.is("Numbered");

         for (Entry<String, String> known : this.knownOthers.entrySet()) {
            put(table, known.getValue(), numbered ? label + " " + this.numbers.get(known.getKey()) : label);
         }
      }

      this.publish(new NameMatcher(table), edited);
   }

   private void publish(NameMatcher next, boolean edited) {
      NameMatcher previous = this.matcher;
      this.matcher = next;
      active = next;
      if (!next.sameMapping(previous)) {
         generation++;
         if (edited || recentChatMentions(next.newNamesSince(previous))) {
            this.chatRefreshPending = true;
         }
      }
   }

   private static boolean recentChatMentions(NameMatcher fresh) {
      if (fresh.isEmpty()) {
         return false;
      } else {
         for (String line : recentChat) {
            if (fresh.mentionsAny(line)) {
               return true;
            }
         }

         return false;
      }
   }

   private String resolveOwnAlias(List<String> extras) {
      String alias = AliasPolicy.sanitize(this.selfAlias.get());
      if (!alias.isEmpty() && !alias.equalsIgnoreCase("You")) {
         Set<String> extraSkeletons = new HashSet<>();

         for (String extra : extras) {
            extraSkeletons.add(AliasPolicy.skeleton(extra));
         }

         AliasPolicy.Refusal refusal = AliasPolicy.check(alias, skeleton -> this.seenSkeletons.contains(skeleton) || extraSkeletons.contains(skeleton));
         if (refusal == null) {
            this.refusedAlias = null;
            return alias;
         } else {
            if (!alias.equals(this.refusedAlias)) {
               this.refusedAlias = alias;
               this.refusalNoticeDelay = 30;
            }

            this.refusalReason = refusal;
            return "You";
         }
      } else {
         this.refusedAlias = null;
         return alias.isEmpty() ? "You" : alias;
      }
   }

   private void announceRefusal(Minecraft mc) {
      if (this.refusedAlias == null) {
         this.noticedAlias = null;
      } else if (!this.refusedAlias.equals(this.noticedAlias)) {
         if (this.refusalNoticeDelay > 0) {
            this.refusalNoticeDelay--;
         } else if (mc.player != null) {
            this.noticedAlias = this.refusedAlias;
            ModuleChat.send(
               this,
               ModuleChat.body()
                  .text("Alias ")
                  .name(this.refusedAlias)
                  .text(" ")
                  .danger("not used")
                  .text(": " + this.refusalReason.reason() + ", showing ")
                  .name("You")
                  .text(" instead")
            );
         }
      }
   }

   private void rememberOnline(ClientPacketListener connection, String accountName, String profile) {
      if (connection != null) {
         for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile gameProfile = info.getProfile();
            String name = NameMatcher.nameCore(gameProfile != null ? gameProfile.name() : null);
            if (name != null && !name.equalsIgnoreCase(accountName) && !name.equalsIgnoreCase(profile)) {
               String skeleton = AliasPolicy.skeleton(name);
               this.seenSkeletons.remove(skeleton);
               this.seenSkeletons.add(skeleton);
            }
         }

         Iterator<String> oldest = this.seenSkeletons.iterator();

         while (this.seenSkeletons.size() > 8192 && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
         }
      }
   }

   private void trackOthers(ClientPacketListener connection, List<String> own) {
      if (connection != null) {
         int online = 0;

         for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && (profile.id() == null || profile.id().version() != 2)) {
               String name = NameMatcher.nameCore(profile.name());
               if (isLikelyPlayer(name) && !isOwn(name, own)) {
                  String key = name.toLowerCase(Locale.ROOT);
                  this.knownOthers.remove(key);
                  this.knownOthers.put(key, name);
                  this.numbers.computeIfAbsent(key, k -> this.nextNumber++);
                  online++;
               }
            }
         }

         Iterator<String> oldest = this.knownOthers.keySet().iterator();

         while (this.knownOthers.size() > online + 256 && oldest.hasNext()) {
            this.numbers.remove(oldest.next());
            oldest.remove();
         }
      }
   }

   private static void put(Map<String, String> table, String rawName, String alias) {
      String name = NameMatcher.nameCore(rawName);
      if (name != null && !alias.equalsIgnoreCase(name)) {
         table.putIfAbsent(name, alias);
      }
   }

   private static boolean isLikelyPlayer(String name) {
      if (name != null && name.length() >= 3) {
         for (int i = 0; i < name.length(); i++) {
            if (Character.isLetter(name.charAt(i))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isOwn(String name, List<String> own) {
      for (String ownName : own) {
         if (ownName.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private static String profileName(Minecraft mc) {
      return mc != null && mc.player != null ? mc.player.getGameProfile().name() : null;
   }

   private static int listSize(ClientPacketListener connection) {
      return connection != null ? connection.getListedOnlinePlayers().size() : -1;
   }

   private static boolean chatOpen(Minecraft mc) {
      return mc.gui != null && mc.gui.hud.getChat().isChatFocused();
   }
}
