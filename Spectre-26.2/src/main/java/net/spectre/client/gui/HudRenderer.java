package net.spectre.client.gui;

import com.example.client.mixin.AccessorHudToastInstance;
import com.example.client.mixin.AccessorHudToastManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.GpuTexture;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImFontGlyph;
import imgui.ImGui;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.Surface;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.ModuleRevision;
import net.spectre.client.module.impl.client.HotkeysModule;
import net.spectre.client.module.impl.client.HudModule;
import net.spectre.client.module.impl.client.RadarModule;
import net.spectre.client.module.impl.client.RegionMapModule;
import net.spectre.client.module.impl.client.SpotifyHudModule;
import net.spectre.client.module.impl.misc.AntiVanishModule;
import net.spectre.client.module.impl.misc.NameProtectModule;
import net.spectre.client.module.impl.misc.StaffListModule;
import net.spectre.client.util.spotify.AlbumArt;
import net.spectre.client.util.spotify.DisplayText;
import net.spectre.client.util.spotify.Lyrics;
import net.spectre.client.util.spotify.NowPlaying;
import net.spectre.client.util.spotify.PlaybackClock;
import net.spectre.client.util.spotify.SyncedLyrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public final class HudRenderer {
   private static final HudRenderer INSTANCE = new HudRenderer();
   private static final float CAP_H = 28.0F;
   private static final float PAD_X = 12.0F;
   private static final float GAP_UNIT = 4.0F;
   private static final float GAP_DIV = 10.0F;
   private static final float DIV_H = 12.0F;
   private static final float EMBLEM_H = 24.0F;
   static final float ANIM_SPEED = 12.0F;
   private static final float WIDTH_SPEED = 16.0F;
   private static final float INTRO_DURATION = 0.36F;
   private static final float INTRO_STAGGER = 0.04F;
   private static final float INTRO_SLIDE = 6.0F;
   private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
   private static final String MINUS = "−";
   private static final String INFINITY = "∞";
   private static final String[] SECONDS = IntStream.range(0, 60).mapToObj(i -> String.format(":%02d", i)).toArray(String[]::new);
   private final HudLayoutStore layout = HudLayoutStore.getInstance();
   private final ModuleListRows moduleRows = new ModuleListRows();
   private final TargetCard targetCard = new TargetCard();
   private final ArmorStatus armorStatus = new ArmorStatus();
   private final CooldownTray cooldownTray = new CooldownTray();
   private final Map<String, Int2ObjectOpenHashMap<StableWidth>> slots = new HashMap<>();
   private int slotCount;
   private float moduleListW;
   private float toastShift;
   private boolean layoutDirty;
   private long lastWheelNanos;
   private final HudModule hudModule;
   private final RegionMapModule regionMapModule;
   private final RadarModule radarModule;
   private float introTime;
   private boolean hudVisibleLastFrame;
   private String draggingId;
   private float dragOffsetX;
   private float dragOffsetY;
   private Float snapGuideX;
   private Float snapGuideY;
   private static int studioFrame = -2;
   private static final float HIDDEN_ALPHA = 0.35F;
   private long infoSecond = Long.MIN_VALUE;
   private int infoFps = -1;
   private int infoPing = -1;
   private List<HudRenderer.InfoItem> infoItems = List.of();
   private static final float ROWS_RADIUS = 6.0F;
   private static final int ROWS_SHADOW_LAYERS = 3;
   private static final int ROWS_TEXT = -1;
   private static final float COORD_ROW_H = 20.0F;
   private static final float COORD_PAD = 7.0F;
   private static final float COORD_ICON = 16.0F;
   private static final float COORD_GAP_ICON = 8.0F;
   private static final float COORD_GAP = 12.0F;
   private static final float COORD_ELSEWHERE_ALPHA = 0.62F;
   private static final String[] COORD_SLOTS = new String[]{"coords.0", "coords.1", "coords.2"};
   private final Map<String, Float> coordAnim = new HashMap<>();
   private static final float LIST_ROW_H = 18.0F;
   private static final float LIST_PAD = 6.0F;
   private static final String NO_MODULES = "No active modules";
   private static final float TOAST_GAP = 10.0F;
   private static final float POTION_ROW_H = 22.0F;
   private static final float POTION_PAD = 6.0F;
   private static final float ICON = 18.0F;
   private static final float GAP_ICON = 6.0F;
   private static final float GAP_DURATION = 14.0F;
   private static final String NO_EFFECTS = "No active effects";
   private static final float VANISH_ROW_H = 22.0F;
   private static final float VANISH_PAD = 8.0F;
   private static final float GAP_TAG = 8.0F;
   private static final String NO_VANISH = "No vanish alerts";
   private static final float STAFF_ROW_H = 22.0F;
   private static final float STAFF_HEADER_H = 20.0F;
   private static final float STAFF_PAD = 8.0F;
   private static final float STAFF_FACE = 16.0F;
   private static final float GAP_FACE = 6.0F;
   private static final float GAP_COUNT = 5.0F;
   private static final float GAP_STATE = 14.0F;
   private static final String NO_STAFF = "No staff online";
   private static final String STAFF_HEADER = "STAFF";
   private static final String WATCHING_YOU = "WATCHING YOU";
   private static final float SKIN_FACE_U = 0.125F;
   private static final float SKIN_HAT_U = 0.625F;
   private static final float SKIN_TOP_V = 0.125F;
   private static final float SKIN_PART = 0.125F;
   private final Map<UUID, Identifier> staffSkins = new HashMap<>();
   private final Set<GpuTexture> nearestSkins = Collections.newSetFromMap(new WeakHashMap<>());
   private static final float RADAR_DEFAULT_SIZE = 150.0F;
   private static final float[] RADAR_RINGS = new float[]{0.33333334F, 0.6666667F};
   private static final float RADAR_LETTER_INSET = 10.0F;
   private static final float RADAR_LETTER_CLEAR = 4.0F;
   private static final float RADAR_EDGE = 5.0F;
   private static final float RADAR_SELF = 3.0F;
   private static final float RADAR_MARKER_INSET = 1.5F;
   private static final float RADAR_MARKER_H = 5.0F;
   private static final float RADAR_MARKER_W = 3.5F;
   private static final float RADAR_DOT = 3.2F;
   private static final String[] RADAR_LETTERS = new String[]{"N", "E", "S", "W"};
   private static final int[] RADAR_LETTER_DX = new int[]{0, 1, 0, -1};
   private static final int[] RADAR_LETTER_DZ = new int[]{-1, 0, 1, 0};
   private static final int RADAR_DOT_EDGE = -1946157056;
   private final float[] radarLetterW = new float[4];
   private float radarLetterMid;
   private float radarLetterHalf;
   private ImFont radarLetterFont;
   private int radarLetterPx = -1;
   private static final float HOTKEY_ROW_H = 22.0F;
   private static final float HOTKEY_HEADER_H = 20.0F;
   private static final float HOTKEY_PAD = 8.0F;
   private static final float GAP_HOTKEY = 16.0F;
   private static final float KEYCAP_H = 16.0F;
   private static final float KEYCAP_PAD = 5.0F;
   private static final float KEYCAP_RADIUS = 4.0F;
   private static final String HOTKEYS_HEADER = "HOTKEYS";
   private static final String NO_HOTKEYS = "No keys bound";
   private List<HudRenderer.HotkeyLine> hotkeyLines = List.of();
   private long hotkeyRevision = Long.MIN_VALUE;
   private ImFont hotkeyFont;
   private int hotkeyTextPx = -1;
   private int hotkeyKeyPx = -1;
   private float hotkeyNameColumn;
   private float hotkeyKeyColumn;
   private float hotkeyHeaderW;
   private float hotkeyCapMid;
   private static final float SPOTIFY_PAD_Y = 8.0F;
   private static final float SPOTIFY_LINE_GAP = 2.0F;
   private static final float SPOTIFY_GLYPH = 10.0F;
   private static final float SPOTIFY_GLYPH_GAP = 10.0F;
   private static final float SPOTIFY_PAUSED = 0.62F;
   private static final float MARQUEE_SPEED = 30.0F;
   private static final float MARQUEE_HOLD = 2.4F;
   private static final float MARQUEE_GAP = 40.0F;
   private static final String ELLIPSIS = "...";
   private static final float CARD_PAD = 10.0F;
   private static final float CARD_MIN_WIDTH = 200.0F;
   private static final float CARD_DEFAULT_WIDTH = 320.0F;
   private static final float ART_GAP = 10.0F;
   private static final float ART_MIN_COLUMN = 128.0F;
   private static final float ART_RADIUS = 4.0F;
   private static final int ART_PLACEHOLDER = 318767103;
   private static final float LABEL_GAP = 3.0F;
   private static final float ARTIST_GAP = 1.0F;
   private static final float TRANSPORT_GAP = 7.0F;
   private static final float PROGRESS_GAP = 6.0F;
   private static final float PROGRESS_H = 2.0F;
   private static final float SKIP_GLYPH = 8.0F;
   private static final float PLAY_GLYPH = 10.0F;
   private static final float GLYPH_SPACING = 14.0F;
   private static final float GLYPH_MARGIN = 8.0F;
   private static final float LYRICS_GAP = 12.0F;
   private static final float LYRIC_ROW_GAP = 6.0F;
   private static final float LYRIC_LINE_GAP = 6.0F;
   private static final float LYRIC_SPRING = 13.0F;
   private static final float LYRIC_SETTLE_TIME = 1.1F;
   private static final float LYRIC_STAGGER = 0.045F;
   private static final int LYRIC_MAX_STAGGERED = 6;
   private static final int LYRIC_MAX_SHIFTS = 8;
   private static final float LYRIC_GLOW_TIME = 0.35F;
   private static final float LYRIC_ARRIVE_TIME = 0.3F;
   private static final float LYRIC_FADE_TIME = 0.32F;
   private static final float LYRIC_RISE = 8.0F;
   private static final int LYRIC_MAX_ROWS = 4;
   private static final float LYRIC_GROW_SPEED = 14.0F;
   private static final float LYRIC_SHRINK_SPEED = 9.0F;
   private static final float LYRIC_FAR_ALPHA = 0.6F;
   private static final float LYRIC_PAUSED_ALPHA = 0.7F;
   private static final float LYRIC_PAUSE_SPEED = 8.0F;
   private static final float LYRIC_BREATH_PERIOD = 1.6F;
   private static final long LYRIC_LEAD_MS = 200L;
   private static final long LYRIC_SWEEP_MS = 180L;
   private static final float LYRIC_SLOW_PACE = 2.0F;
   private static final float LYRIC_CHAR_MS = 90.0F;
   private static final float LYRIC_LIT_ALL = Float.POSITIVE_INFINITY;
   private static final long LYRIC_QUIET_MS = 700L;
   private static final float LYRIC_MIN_COVERAGE = 0.75F;
   private static final String[] NO_ROWS = new String[0];
   private static final NowPlaying SAMPLE_TRACK = new NowPlaying(
      NowPlaying.Status.PLAYING, "Golden Hour Drive", "Sample Artist", "", NowPlaying.Source.SESSION, 214000L, 84000L, 0L, 1.0, null
   );
   private static final Lyrics SAMPLE_LYRICS = new Lyrics(
      Lyrics.Status.SYNCED,
      SyncedLyrics.parse(
         "[01:21.00]Every light in the city knows my name\n[01:27.40]We keep on driving till the morning comes\n[01:33.80]Nothing but the radio and you\n"
      ),
      List.of(),
      0L
   );
   private float marqueeTime;
   private String marqueeTitle = "";
   private String marqueeArtist = "";
   private final PlaybackClock spotifyClock = new PlaybackClock();
   private NowPlaying spotifyFolded;
   private String spotifyTitle = "";
   private String spotifyArtist = "";
   private AlbumArt spotifyArt;
   private ThemeImages.Texture spotifyArtTexture;
   private final Map<Long, Float> capTops = new HashMap<>();
   private Lyrics lyricPrepared;
   private String[] lyricTexts = NO_ROWS;
   private String[][] lyricRows = new String[0][];
   private int lyricRowsPx;
   private int lyricRowsWidth;
   private HudRenderer.LyricWords[] lyricWords = new HudRenderer.LyricWords[0];
   private float lyricCharMs = 90.0F;
   private boolean lyricReadable = true;
   private float lyricViewH = -1.0F;
   private int lyricViewLines;
   private float lyricViewScale;
   private SyncedLyrics lyricSource;
   private int lyricStart;
   private final List<HudRenderer.LyricShift> lyricShifts = new ArrayList<>(8);
   private float lyricClock;
   private List<HudRenderer.LyricItem> lyricFadeItems;
   private float lyricFade = 1.0F;
   private List<HudRenderer.LyricItem> lyricLastItems = List.of();
   private List<HudRenderer.LyricItem> lyricLeaveItems;
   private float lyricLeave;
   private float[] lyricShown = new float[0];
   private float lyricBreath;
   private float lyricPlaying = 1.0F;
   private int lyricCurrentFirst = -1;
   private int lyricCurrentLast = -1;
   private float[] lyricBright = new float[0];
   private int lyricBrightLo;
   private int lyricBrightHi = -1;
   private float lyricBlockH;
   private long lyricLoadingSince;
   private ImFont glyphFont;
   private final Map<Integer, Boolean> glyphs = new HashMap<>();
   private static final float SNAP_THRESHOLD = 6.0F;

   private HudRenderer() {
      this.hudModule = ModuleRegistry.get(HudModule.class);
      this.regionMapModule = ModuleRegistry.get(RegionMapModule.class);
      this.radarModule = ModuleRegistry.get(RadarModule.class);
   }

   public static HudRenderer getInstance() {
      return INSTANCE;
   }

   static void markStudioPreview() {
      studioFrame = ImGuiManager.getInstance().frameCount();
   }

   static boolean studioPreview() {
      int age = ImGuiManager.getInstance().frameCount() - studioFrame;
      return age == 0 || age == 1;
   }

   boolean isDragging() {
      return this.draggingId != null;
   }

   void studioInput(float screenW, float screenH, boolean blocked) {
      markStudioPreview();
      if (!blocked || this.draggingId != null) {
         this.updateEditorInput(screenW, screenH);
      }
   }

   public void render() {
      Minecraft client = Minecraft.getInstance();
      if(client.gui.screen() instanceof net.spectre.client.media.MediaScreen)return;
      if (client.level == null) {
         this.releaseSpotifyArt();
         this.resetCombatElements();
      } else {
         boolean editorScreen = client.gui.screen() instanceof HudEditorScreen;
         boolean preview = studioPreview();
         boolean editMode = editorScreen || preview;
         if (client.gui.screen() instanceof ImGuiScreen && !preview) {
            if (this.draggingId != null || this.layoutDirty) {
               this.saveLayout();
            }

            this.draggingId = null;
            this.snapGuideX = null;
            this.snapGuideY = null;
            this.hudVisibleLastFrame = false;
         } else {
            boolean chatEditMode = client.gui.screen() instanceof ChatScreen;
            boolean acceptInput = editorScreen || chatEditMode;
            boolean showHudOverlays = shouldRenderHudOverlays(client);
            float dt = Math.max(ImGui.getIO().getDeltaTime(), 0.001F);
            float screenW = ImGui.getIO().getDisplaySizeX();
            float screenH = ImGui.getIO().getDisplaySizeY();
            this.flushLayoutIfIdle();
            boolean hasHudOverlays = computeHudOverlays();
            boolean regionMapEnabled = this.isRegionMapEnabled();
            HudLayoutStore.HudElementState regionMap = this.layout.element("region_map");
            boolean regionMapShown = regionMapEnabled && regionMap.visible;
            boolean hudVisible = this.hudModule != null && (editMode || this.hudModule.isEnabled());
            if (!hudVisible) {
               this.hudVisibleLastFrame = false;
               this.introTime = 0.0F;
               this.clearSlots();
               this.moduleListW = 0.0F;
               this.toastShift = 0.0F;
               this.releaseSpotifyArt();
               this.resetCombatElements();
               if ((hasHudOverlays || regionMapShown) && showHudOverlays) {
                  this.drawOverlayOnly(screenW, screenH, chatEditMode, regionMapShown);
               }
            } else {
               boolean showPing = editMode || this.hudModule.ping();
               boolean showFps = editMode || this.hudModule.fps();
               boolean showClock = editMode || this.hudModule.clock();
               boolean shadow = this.hudModule.shadow();
               this.updateIntro(editMode, dt);
               this.moduleRows.update(ModuleRegistry.enabled(), editMode, dt);
               if (this.slotCount > 256) {
                  this.clearSlots();
               }

               HudLayoutStore.HudElementState infoBar = this.layout.element("info_bar");
               HudLayoutStore.HudElementState coordinates = this.layout.element("coordinates");
               HudLayoutStore.HudElementState moduleList = this.layout.element("module_list");
               HudLayoutStore.HudElementState potionTracker = this.layout.element("potion_tracker");
               HudLayoutStore.HudElementState antiVanish = this.layout.element("anti_vanish");
               HudLayoutStore.HudElementState staffList = this.layout.element("staff_list");
               HudLayoutStore.HudElementState spotify = this.layout.element("spotify");
               HudLayoutStore.HudElementState radar = this.layout.element("radar");
               HudLayoutStore.HudElementState hotkeys = this.layout.element("hotkeys");
               HudLayoutStore.HudElementState target = this.layout.element("target_card");
               HudLayoutStore.HudElementState armor = this.layout.element("armor_status");
               HudLayoutStore.HudElementState cooldowns = this.layout.element("item_cooldowns");
               HudRenderer.InfoPlan infoPlan = this.planInfoBar(infoBar, this.buildInfoItems(showFps, showClock, showPing, client), dt);
               this.layout.updateBounds(infoBar.id, infoPlan.width(), infoPlan.height(), screenW, screenH);
               HudRenderer.CoordPlan coordsPlan = this.planCoordinates(coordinates, this.coordinateRows(client, editMode, dt), editMode, dt);
               this.layout.updateBounds(coordinates.id, coordsPlan.width(), coordsPlan.height(), screenW, screenH);
               HudRenderer.ListPlan listPlan = this.planModuleList(moduleList, editMode, dt);
               this.layout.updateBounds(moduleList.id, listPlan.width(), listPlan.height(), screenW, screenH);
               HudRenderer.PotionPlan potionPlan = this.planPotions(potionTracker, this.buildPotionRows(client), editMode, dt);
               this.layout.updateBounds(potionTracker.id, potionPlan.width(), potionPlan.height(), screenW, screenH);
               HudRenderer.VanishPlan vanishPlan = this.planVanish(antiVanish, editMode, dt);
               this.layout.updateBounds(antiVanish.id, vanishPlan.width(), vanishPlan.height(), screenW, screenH);
               HudRenderer.StaffPlan staffPlan = this.planStaff(staffList, editMode, dt);
               this.layout.updateBounds(staffList.id, staffPlan.width(), staffPlan.height(), screenW, screenH);
               HudRenderer.SpotifyPlan spotifyPlan = this.planSpotify(spotify, editMode, dt);
               this.layout.updateBounds(spotify.id, spotifyPlan.width(), spotifyPlan.height(), screenW, screenH);
               float radarSize = this.planRadar(radar, editMode);
               this.layout.updateBounds(radar.id, radarSize, radarSize, screenW, screenH);
               HudRenderer.HotkeyPlan hotkeyPlan = this.planHotkeys(hotkeys, editMode, dt);
               this.layout.updateBounds(hotkeys.id, hotkeyPlan.width(), hotkeyPlan.height(), screenW, screenH);
               boolean combatHud = this.hudModule.isEnabled() && !client.gui.hud.isHidden();
               boolean targetWanted = combatHud && target.visible && this.hudModule.targetCard();
               boolean armorWanted = combatHud && armor.visible && this.hudModule.armorStatus();
               boolean cooldownWanted = combatHud && cooldowns.visible && this.hudModule.itemCooldowns();
               float targetScale = scaleOf(target);
               float armorScale = scaleOf(armor);
               float cooldownScale = scaleOf(cooldowns);
               this.targetCard.update(client, this.hudModule, targetWanted, editMode, dt);
               this.layout.updateBounds(target.id, this.targetCard.planWidth(targetScale), this.targetCard.planHeight(targetScale), screenW, screenH);
               this.armorStatus.update(client, this.hudModule, armorWanted, editMode, dt);
               this.layout.updateBounds(armor.id, this.armorStatus.planWidth(armorScale), this.armorStatus.planHeight(armorScale), screenW, screenH);
               this.cooldownTray.update(client, this.hudModule, cooldownWanted, editMode, dt);
               this.layout
                  .updateBounds(cooldowns.id, this.cooldownTray.planWidth(cooldownScale), this.cooldownTray.planHeight(cooldownScale), screenW, screenH);
               if (regionMapEnabled || editMode) {
                  this.updateRegionMapBounds(screenW, screenH);
               }

               if (acceptInput) {
                  this.updateEditorInput(screenW, screenH);
               } else if (!preview) {
                  if (this.draggingId != null || this.layoutDirty) {
                     this.saveLayout();
                  }

                  this.draggingId = null;
                  this.snapGuideX = null;
                  this.snapGuideY = null;
               }

               float listShift = this.updateToastShift(client, moduleList, editMode, dt);
               ImGui.setNextWindowPos(0.0F, 0.0F, 1);
               ImGui.setNextWindowSize(screenW, screenH);
               int flags = 205215;
               if (!acceptInput) {
                  flags |= 197120;
               }

               ImGui.pushStyleVar(4, 0.0F);
               if (!ImGui.begin("##hud_overlay", flags)) {
                  ImGui.end();
                  ImGui.popStyleVar();
               } else {
                  ImGui.popStyleVar();
                  ImDrawList dl = ImGui.getWindowDrawList();
                  if (showHudOverlays) {
                     drawHudOverlays(dl, screenW, screenH);
                  }

                  boolean hudOn = this.hudModule.isEnabled();
                  boolean infoBarShown = hudOn && infoBar.visible && (this.hudModule.fps() || this.hudModule.clock() || this.hudModule.ping());
                  boolean coordsShown = hudOn && coordinates.visible;
                  boolean listShown = hudOn && moduleList.visible && this.hudModule.moduleList();
                  boolean potionsShown = hudOn && potionTracker.visible && this.hudModule.potions();
                  boolean vanishShown = hudOn && antiVanish.visible && AntiVanishModule.shouldShowHud();
                  boolean staffShown = hudOn && staffList.visible && StaffListModule.shouldShowHud();
                  boolean spotifyShown = hudOn && spotify.visible && SpotifyHudModule.shouldShowHud();
                  boolean gameHidden = gameHidesHud(client);
                  boolean radarShown = hudOn && radar.visible && RadarModule.shouldShowHud() && !gameHidden;
                  boolean hotkeysShown = hudOn && hotkeys.visible && HotkeysModule.shouldShowHud() && !gameHidden;
                  if (infoBarShown && !infoPlan.segments().isEmpty() || editMode) {
                     this.drawInfoBar(dl, infoBar, infoPlan, shadow, this.transform(infoBar, editMode, infoBarShown));
                  }

                  if (coordsShown && !coordsPlan.rows().isEmpty() || editMode) {
                     this.drawCoordinates(dl, coordinates, coordsPlan, shadow, this.transform(coordinates, editMode, coordsShown), screenW);
                  }

                  if (listShown && !listPlan.rows().isEmpty() || editMode) {
                     this.drawModuleList(dl, moduleList, listPlan, shadow, this.transform(moduleList, editMode, listShown).down(listShift), screenW);
                  }

                  if (potionsShown && !potionPlan.lines().isEmpty() || editMode) {
                     this.drawPotions(dl, potionTracker, potionPlan, shadow, this.transform(potionTracker, editMode, potionsShown));
                  }

                  if (vanishShown && !vanishPlan.lines().isEmpty() || editMode) {
                     this.drawVanish(dl, antiVanish, vanishPlan, shadow, this.transform(antiVanish, editMode, vanishShown));
                  }

                  if (staffShown || editMode) {
                     this.drawStaff(dl, staffList, staffPlan, shadow, this.transform(staffList, editMode, staffShown));
                  }

                  if (spotifyShown || editMode) {
                     this.drawSpotify(dl, spotify, spotifyPlan, shadow, this.transform(spotify, editMode, spotifyShown));
                  }

                  if (radarShown || editMode) {
                     this.drawRadar(dl, radar, radarSize, client, shadow, this.transform(radar, editMode, radarShown));
                  }

                  if (hotkeysShown && !hotkeyPlan.lines().isEmpty() || editMode) {
                     this.drawHotkeys(dl, hotkeys, hotkeyPlan, shadow, this.transform(hotkeys, editMode, hotkeysShown));
                  }

                  if (this.targetCard.visible()) {
                     HudRenderer.DrawTransform tr = this.transform(target, editMode, targetWanted);
                     this.targetCard
                        .draw(dl, target, Math.round(target.x + tr.xOffset()), Math.round(target.y + tr.yOffset()), tr.alpha(), shadow, this.hudModule);
                  }

                  if (this.armorStatus.visible()) {
                     HudRenderer.DrawTransform tr = this.transform(armor, editMode, armorWanted);
                     this.armorStatus.draw(dl, armor, Math.round(armor.x + tr.xOffset()), Math.round(armor.y + tr.yOffset()), tr.alpha(), shadow, client);
                  }

                  if (this.cooldownTray.visible()) {
                     HudRenderer.DrawTransform tr = this.transform(cooldowns, editMode, cooldownWanted);
                     this.cooldownTray
                        .draw(
                           dl, cooldowns, Math.round(cooldowns.x + tr.xOffset()), Math.round(cooldowns.y + tr.yOffset()), tr.alpha(), shadow, this.hudAccent()
                        );
                  }

                  if (regionMapShown || editMode) {
                     RegionMapRenderer.render(dl, this.regionMapModule, regionMap, editMode && !regionMapShown ? 0.35F : 1.0F);
                  }

                  if (editorScreen) {
                     this.drawEditorChrome(dl, screenW, screenH);
                  } else if (preview) {
                     this.drawSnapGuides(dl, screenW, screenH);
                  }

                  ImGui.end();
               }
            }
         }
      }
   }

   private HudRenderer.DrawTransform transform(HudLayoutStore.HudElementState state, boolean editMode, boolean shownInGame) {
      HudRenderer.DrawTransform tr = this.introTransform(state, editMode);
      return editMode && !shownInGame ? new HudRenderer.DrawTransform(tr.xOffset(), tr.yOffset(), tr.alpha() * 0.35F) : tr;
   }

   private void resetCombatElements() {
      this.targetCard.reset();
      this.armorStatus.reset();
      this.cooldownTray.reset();
   }

   private void saveLayout() {
      this.layout.save();
      this.layoutDirty = false;
   }

   private void flushLayoutIfIdle() {
      if (this.layoutDirty && System.nanoTime() - this.lastWheelNanos > 400000000L) {
         this.saveLayout();
      }
   }

   private void drawOverlayOnly(float screenW, float screenH, boolean acceptInput, boolean drawRegionMap) {
      if (drawRegionMap) {
         this.updateRegionMapBounds(screenW, screenH);
      }

      ImGui.setNextWindowPos(0.0F, 0.0F, 1);
      ImGui.setNextWindowSize(screenW, screenH);
      int flags = 205215;
      if (!acceptInput) {
         flags |= 197120;
      }

      ImGui.pushStyleVar(4, 0.0F);
      if (!ImGui.begin("##spectre_overlay_only", flags)) {
         ImGui.end();
         ImGui.popStyleVar();
      } else {
         ImGui.popStyleVar();
         if (acceptInput) {
            this.updateEditorInput(screenW, screenH);
         } else {
            if (this.draggingId != null || this.layoutDirty) {
               this.saveLayout();
            }

            this.draggingId = null;
         }

         ImDrawList dl = ImGui.getWindowDrawList();
         drawHudOverlays(dl, screenW, screenH);
         if (drawRegionMap && this.regionMapModule != null) {
            RegionMapRenderer.render(dl, this.regionMapModule, this.layout.element("region_map"));
         }

         ImGui.end();
      }
   }

   private boolean isRegionMapEnabled() {
      return this.regionMapModule != null && this.regionMapModule.isEnabled();
   }

   private void updateRegionMapBounds(float screenW, float screenH) {
      if (this.regionMapModule != null) {
         HudLayoutStore.HudElementState state = this.layout.element("region_map");
         RegionMapRenderer.Size size = RegionMapRenderer.measureSize(this.regionMapModule, scaleOf(state));
         this.layout.updateBounds("region_map", size.width(), size.height(), screenW, screenH);
      }
   }

   public boolean hasWork() {
      Minecraft client = Minecraft.getInstance();
      if(client.gui.screen() instanceof net.spectre.client.media.MediaScreen)return false;
      if (client.level == null) {
         this.resetCombatElements();
         return false;
      } else {
         return client.gui.screen() instanceof HudEditorScreen
            || studioPreview()
            || this.hudModule != null && (this.hudModule.isEnabled() || this.hudVisibleLastFrame)
            || this.isRegionMapEnabled()
            || computeHudOverlays();
      }
   }

   private static boolean shouldRenderHudOverlays(Minecraft client) {
      return client.gui.screen() == null || client.gui.screen() instanceof ChatScreen;
   }

   private static boolean gameHidesHud(Minecraft client) {
      return client.gui.hud.isHidden() || client.debugEntries.isOverlayVisible();
   }

   private static boolean computeHudOverlays() {
      for (HudOverlayProvider provider : ModuleRegistry.instancesOf(HudOverlayProvider.class)) {
         if (provider.isHudOverlayEnabled()) {
            return true;
         }
      }

      return false;
   }

   private static void drawHudOverlays(ImDrawList dl, float screenW, float screenH) {
      for (HudOverlayProvider provider : ModuleRegistry.instancesOf(HudOverlayProvider.class)) {
         if (provider.isHudOverlayEnabled()) {
            provider.renderHudOverlay(dl, screenW, screenH);
         }
      }
   }

   private void updateIntro(boolean editMode, float dt) {
      float end = 0.56F;
      if (editMode) {
         this.introTime = end;
         this.hudVisibleLastFrame = true;
      } else if (!this.hudVisibleLastFrame) {
         this.introTime = 0.0F;
         this.hudVisibleLastFrame = true;
      } else {
         this.introTime = Math.min(this.introTime + dt, end);
      }
   }

   private HudRenderer.DrawTransform introTransform(HudLayoutStore.HudElementState state, boolean editMode) {
      if (editMode) {
         return HudRenderer.DrawTransform.identity();
      } else {
         float t = clamp01((this.introTime - introDelay(state.id)) / 0.36F);
         float eased = easeOutCubic(t);
         float slide = 6.0F * scaleOf(state) * (1.0F - eased);

         return switch (state.anchorX) {
            case 1 -> new HudRenderer.DrawTransform(0.0F, -slide, eased);
            case 2 -> new HudRenderer.DrawTransform(slide, 0.0F, eased);
            default -> new HudRenderer.DrawTransform(-slide, 0.0F, eased);
         };
      }
   }

   private static float introDelay(String id) {
      return switch (id) {
         case "info_bar" -> 0.04F;
         case "coordinates" -> 0.08F;
         case "anti_vanish" -> 0.12F;
         case "spotify" -> 0.12F;
         case "module_list" -> 0.16F;
         case "staff_list" -> 0.16F;
         case "hotkeys" -> 0.16F;
         case "radar" -> 0.19999999F;
         case "potion_tracker" -> 0.19999999F;
         case "armor_status" -> 0.16F;
         case "item_cooldowns" -> 0.12F;
         case "target_card" -> 0.08F;
         default -> 0.0F;
      };
   }

   private float slot(String key, int px, float target, float dt) {
      Int2ObjectOpenHashMap<StableWidth> bySize = this.slots.computeIfAbsent(key, k -> new Int2ObjectOpenHashMap());
      StableWidth width = (StableWidth)bySize.get(px);
      if (width == null) {
         width = new StableWidth();
         bySize.put(px, width);
         this.slotCount++;
      }

      return width.update(target, dt);
   }

   private void removeSlot(String key, int px) {
      Int2ObjectOpenHashMap<StableWidth> bySize = this.slots.get(key);
      if (bySize != null && bySize.remove(px) != null) {
         this.slotCount--;
      }
   }

   private void clearSlots() {
      this.slots.clear();
      this.slotCount = 0;
   }

   private static String number(long value) {
      return value < 0L ? "−" + -value : Long.toString(value);
   }

   private List<HudRenderer.InfoItem> buildInfoItems(boolean showFps, boolean showClock, boolean showPing, Minecraft client) {
      long second = showClock ? System.currentTimeMillis() / 1000L : -1L;
      int fps = showFps ? Math.max(0, client.getFps()) : -1;
      int ping = -1;
      if (showPing) {
         ClientPacketListener conn = client.getConnection();
         int latency = 0;
         if (conn != null && client.player != null) {
            latency = readTabLatency(client, conn);
            if (latency <= 0) {
               latency = readServerPing(conn);
            }
         }

         ping = Math.max(0, latency);
      }

      if (second == this.infoSecond && fps == this.infoFps && ping == this.infoPing) {
         return this.infoItems;
      } else {
         this.infoSecond = second;
         this.infoFps = fps;
         this.infoPing = ping;
         List<HudRenderer.InfoItem> items = new ArrayList<>(3);
         if (showClock) {
            LocalTime now = LocalTime.now();
            items.add(new HudRenderer.InfoItem("info.time", now.format(CLOCK_FORMAT), SECONDS[now.getSecond()], null));
         }

         if (showFps) {
            items.add(new HudRenderer.InfoItem("info.fps", String.valueOf(fps), null, "fps"));
         }

         if (showPing) {
            items.add(new HudRenderer.InfoItem("info.ping", String.valueOf(ping), null, "ms"));
         }

         this.infoItems = List.copyOf(items);
         return this.infoItems;
      }
   }

   private HudRenderer.InfoPlan planInfoBar(HudLayoutStore.HudElementState state, List<HudRenderer.InfoItem> items, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      List<HudRenderer.Segment> segments = new ArrayList<>(items.size());
      float total = 12.0F * s * 2.0F;

      for (int i = 0; i < items.size(); i++) {
         HudRenderer.InfoItem item = items.get(i);
         float valueW = HudChrome.tabularWidth(t.value(), t.valuePx(), item.value());
         float dimW = item.dim() != null ? HudChrome.tabularWidth(t.value(), t.valuePx(), item.dim()) : 0.0F;
         float content = valueW + dimW;
         if (item.unit() != null) {
            content += 4.0F * s + HudChrome.width(t.label(), t.labelPx(), item.unit());
         }

         float slotW = this.slot(item.slot(), t.valuePx(), (float)Math.ceil(content), dt);
         segments.add(new HudRenderer.Segment(item, valueW, dimW, content, slotW));
         total += slotW;
      }

      if (segments.size() > 1) {
         total += (segments.size() - 1) * (10.0F * s * 2.0F + 1.0F);
      }

      float emblemW = 0.0F;
      HudEmblem.Icon emblem = this.hudModule != null && this.hudModule.logo() && !segments.isEmpty() ? HudEmblem.forHeight(Math.round(24.0F * s)) : null;
      if (emblem != null) {
         emblemW = emblem.width() + 6.0F * s + HudChrome.width(t.label(), t.labelPx(), "SPECTRE");
         total += emblemW + 10.0F * s * 2.0F + 1.0F;
      }

      return new HudRenderer.InfoPlan(segments, t, (float)Math.round(total), (float)Math.round(28.0F * s), emblemW);
   }

   private void drawInfoBar(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.InfoPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         Surface surface = hudSurface(state);
         boolean guard = HudChrome.needsTextGuard(surface);
         HudChrome.card(dl, surface, x, y, w, h, hudRounding() * s, a, shadow, s);
         HudRenderer.Type t = plan.type();
         float valueTop = y + (float)Math.floor((h - t.valuePx()) * 0.5F);
         float labelTop = valueTop + HudChrome.baselineOffset(t.value(), t.valuePx(), t.label(), t.labelPx());
         float cx = x + 12.0F * s;
         HudEmblem.Icon emblem = plan.emblemW() > 0.0F ? HudEmblem.forHeight(Math.round(24.0F * s)) : null;
         if (emblem != null) {
            float ex = Math.round(cx);
            float ey = Math.round(y + (h - emblem.height()) * 0.5F);
            if(this.hudModule.mascot())HudEmblem.draw(dl, ex, ey, emblem.height(), a);
            HudChrome.text(dl, t.label(), t.labelPx(), ex + emblem.width() + 6.0F * s, labelTop,
               HudChrome.scaleAlpha(this.hudAccent(), a), "SPECTRE", guard);
            cx += plan.emblemW();
            HudChrome.divider(dl, cx + 10.0F * s, y + h * 0.5F, 12.0F * s, a);
            cx += 10.0F * s * 2.0F + 1.0F;
         }

         List<HudRenderer.Segment> segments = plan.segments();

         for (int i = 0; i < segments.size(); i++) {
            HudRenderer.Segment seg = segments.get(i);
            HudRenderer.InfoItem item = seg.item();
            float vx = item.unit() != null ? cx + seg.slotW() - seg.contentW() : cx;
            HudChrome.tabular(dl, t.value(), t.valuePx(), vx, valueTop, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a), item.value(), guard);
            vx += seg.valueW();
            if (item.dim() != null) {
               HudChrome.tabular(dl, t.value(), t.valuePx(), vx, valueTop, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), item.dim(), guard);
               vx += seg.dimW();
            }

            if (item.unit() != null) {
               HudChrome.text(dl, t.label(), t.labelPx(), vx + 4.0F * s, labelTop, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), item.unit(), guard);
            }

            cx += seg.slotW();
            if (i < segments.size() - 1) {
               HudChrome.divider(dl, cx + 10.0F * s, y + h * 0.5F, 12.0F * s, a);
               cx += 10.0F * s * 2.0F + 1.0F;
            }
         }
      }
   }

   private static float rowsRadius(float s) {
      return Math.min(hudRounding(), 6.0F) * s;
   }

   private void drawRows(
      ImDrawList dl, List<HudRenderer.Step> steps, Surface surface, float x, float y, float w, float h, float radius, float s, boolean shadow
   ) {
      if (shadow) {
         drawRowsShadow(dl, steps, radius, s);
      }

      int fill = GuiPalette.fill(surface);

      for (int i = 0; i < steps.size(); i++) {
         HudRenderer.Step q = steps.get(i);
         HudRenderer.Step up = i > 0 ? steps.get(i - 1) : null;
         HudRenderer.Step dn = i < steps.size() - 1 ? steps.get(i + 1) : null;
         int corners = convexCorners(q, up, dn);
         int color = HudChrome.scaleAlpha(fill, q.alpha());
         if (corners == 256) {
            dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), color);
         } else {
            dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), color, radius, corners);
         }

         SurfaceBackground.drawPart(dl, surface, x, y, w, h, q.l(), q.t(), q.r() - q.l(), q.b() - q.t(), q.alpha(), radius, corners);
         float stepH = q.b() - q.t();
         if (up != null && up.l() < q.l() - 0.5F) {
            fillet(dl, q.l(), q.t(), -1, 1, Math.min(radius, Math.min(q.l() - up.l(), stepH)), color);
         }

         if (up != null && up.r() > q.r() + 0.5F) {
            fillet(dl, q.r(), q.t(), 1, 1, Math.min(radius, Math.min(up.r() - q.r(), stepH)), color);
         }

         if (dn != null && dn.l() < q.l() - 0.5F) {
            fillet(dl, q.l(), q.b(), -1, -1, Math.min(radius, Math.min(q.l() - dn.l(), stepH)), color);
         }

         if (dn != null && dn.r() > q.r() + 0.5F) {
            fillet(dl, q.r(), q.b(), 1, -1, Math.min(radius, Math.min(dn.r() - q.r(), stepH)), color);
         }
      }
   }

   private static int convexCorners(HudRenderer.Step q, HudRenderer.Step up, HudRenderer.Step dn) {
      int flags = 0;
      if (up == null || up.l() > q.l() + 0.5F) {
         flags |= 16;
      }

      if (up == null || up.r() < q.r() - 0.5F) {
         flags |= 32;
      }

      if (dn == null || dn.l() > q.l() + 0.5F) {
         flags |= 64;
      }

      if (dn == null || dn.r() < q.r() - 0.5F) {
         flags |= 128;
      }

      return flags == 0 ? 256 : flags;
   }

   private static void fillet(ImDrawList dl, float px, float py, int dirX, int dirY, float r, int color) {
      if (!(r < 1.0F)) {
         float cx = px + dirX * r;
         float cy = py + dirY * r;
         float a0 = dirX < 0 ? 0.0F : (float) Math.PI;
         float a1 = dirY > 0 ? (dirX < 0 ? (float) (-Math.PI / 2) : (float) (Math.PI * 3.0 / 2.0)) : (float) (Math.PI / 2);
         dl.pathClear();
         dl.pathLineTo(px, py);
         dl.pathArcTo(cx, cy, r, a0, a1, 8);
         dl.pathFillConcave(color);
      }
   }

   private static void drawRowsShadow(ImDrawList dl, List<HudRenderer.Step> steps, float radius, float s) {
      for (int k = 3; k >= 1; k--) {
         float spread = k * Math.max(1.0F, s);

         for (int i = 0; i < steps.size(); i++) {
            HudRenderer.Step q = steps.get(i);
            boolean first = i == 0;
            boolean last = i == steps.size() - 1;
            int corners = convexCorners(q, first ? null : steps.get(i - 1), last ? null : steps.get(i + 1));
            float top = q.t() - (first ? spread : 0.0F);
            float bottom = q.b() + (last ? spread + Math.max(1.0F, s) : 0.0F);
            int color = HudChrome.scaleAlpha(218103808, q.alpha());
            if (corners == 256) {
               dl.addRectFilled(q.l() - spread, top, q.r() + spread, bottom, color);
            } else {
               dl.addRectFilled(q.l() - spread, top, q.r() + spread, bottom, color, radius + spread, corners);
            }
         }
      }
   }

   private static boolean alignsRight(HudLayoutStore.HudElementState state, float screenW) {
      return switch (state.anchorX) {
         case 0 -> false;
         case 2 -> true;
         default -> state.x + state.width * 0.5F >= screenW * 0.5F;
      };
   }

   private List<HudRenderer.CoordRow> coordinateRows(Minecraft client, boolean editMode, float dt) {
      long x = 0L;
      long y = 0L;
      long z = 0L;
      if (client.player != null) {
         x = (long)Math.floor(client.player.getX());
         y = (long)Math.floor(client.player.getY());
         z = (long)Math.floor(client.player.getZ());
      }

      boolean inNether = client.level != null && client.level.dimension() == Level.NETHER;
      boolean inEnd = client.level != null && client.level.dimension() == Level.END;
      boolean overworldOn = this.hudModule.coordinates();
      boolean netherOn = this.hudModule.netherCoordinates();
      float k = 1.0F - (float)Math.exp(-12.0F * dt);
      List<HudRenderer.CoordRow> rows = new ArrayList<>(2);
      if (inEnd) {
         this.addCoordRow(rows, "end", "end_stone", true, overworldOn || netherOn, editMode, new long[]{x, y, z}, k);
      } else {
         long[] overworld = inNether ? new long[]{x * 8L, y, z * 8L} : new long[]{x, y, z};
         long[] nether = inNether ? new long[]{x, y, z} : new long[]{Math.floorDiv(x, 8), y, Math.floorDiv(z, 8)};
         this.addCoordRow(rows, "overworld", "grass_block_side", !inNether, overworldOn, editMode, overworld, k);
         this.addCoordRow(rows, "nether", "netherrack", inNether, netherOn, editMode, nether, k);
      }

      return rows;
   }

   private void addCoordRow(List<HudRenderer.CoordRow> rows, String key, String block, boolean here, boolean enabled, boolean editMode, long[] xyz, float k) {
      float target = !enabled && !editMode ? 0.0F : 1.0F;
      float anim = this.coordAnim.getOrDefault(key, target);
      anim += (target - anim) * k;
      if (Math.abs(anim - target) < 0.005F) {
         anim = target;
      }

      this.coordAnim.put(key, anim);
      if (!(anim <= 0.005F)) {
         rows.add(new HudRenderer.CoordRow(block, here, enabled, new String[]{number(xyz[0]), number(xyz[1]), number(xyz[2])}, anim));
      }
   }

   private static float coordIconSize(float s) {
      return s >= 1.0F ? 16.0F * Math.max(1.0F, (float)Math.floor(s + 0.25F)) : Math.round(16.0F * s);
   }

   private HudRenderer.CoordPlan planCoordinates(HudLayoutStore.HudElementState state, List<HudRenderer.CoordRow> rows, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 20.0F * s;
      float[] valueW = new float[3];
      float height = 0.0F;

      for (HudRenderer.CoordRow row : rows) {
         for (int i = 0; i < 3; i++) {
            valueW[i] = Math.max(valueW[i], HudChrome.tabularWidth(t.text(), t.textPx(), row.values()[i]));
         }

         height += rowFull * smooth(row.anim());
      }

      float[] slotW = new float[3];
      float total = 7.0F * s * 2.0F + Math.min(coordIconSize(s), rowFull - 2.0F) + 8.0F * s + 12.0F * s * 2.0F;

      for (int i = 0; i < 3; i++) {
         slotW[i] = this.slot(COORD_SLOTS[i], t.textPx(), (float)Math.ceil(valueW[i]), dt);
         total += slotW[i];
      }

      if (editMode) {
         height = Math.max(height, rowFull);
      }

      return new HudRenderer.CoordPlan(rows, t, slotW, (float)Math.round(total), (float)Math.max(1, Math.round(height)));
   }

   private void drawCoordinates(
      ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.CoordPlan plan, boolean shadow, HudRenderer.DrawTransform tr, float screenW
   ) {
      float a = tr.alpha();
      if (!(a <= 0.001F) && !plan.rows().isEmpty()) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         boolean right = alignsRight(state, screenW);
         float rowFull = 20.0F * s;
         List<HudRenderer.Step> steps = new ArrayList<>(plan.rows().size());
         float cy = y;

         for (int i = 0; i < plan.rows().size(); i++) {
            HudRenderer.CoordRow row = plan.rows().get(i);
            float open = smooth(row.anim());
            float rowH = rowFull * open;
            float top = Math.round(cy);
            float bottom = Math.round(cy + rowH);
            cy += rowH;
            if (!(bottom <= top)) {
               float slide = Math.round(w * (1.0F - open));
               float l = right ? x + slide : x - slide;
               float rowAlpha = a * (row.enabled() ? 1.0F : 0.35F) * Math.min(1.0F, row.anim() * 1.5F);
               steps.add(new HudRenderer.Step(i, l, top, l + w, bottom, rowAlpha));
            }
         }

         this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
         float icon = Math.min(coordIconSize(s), rowFull - 2.0F);
         float iconR = 2.5F * s;
         float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);

         for (HudRenderer.Step q : steps) {
            HudRenderer.CoordRow row = plan.rows().get(q.item());
            float stepH = q.b() - q.t();
            float shift = (rowFull - stepH) * 0.5F;
            float contentAlpha = q.alpha() * row.anim();
            boolean clip = stepH < rowFull - 0.5F;
            if (clip) {
               dl.pushClipRect(q.l(), q.t(), q.r(), q.b(), true);
            }

            float ix = Math.round(q.l() + 7.0F * s);
            float iy = Math.round(q.t() + (rowFull - icon) * 0.5F - shift);
            ThemeImages.Texture texture = GameIcons.block(row.block());
            if (texture != null) {
               dl.addImageRounded(texture.id(), ix, iy, ix + icon, iy + icon, 0.0F, 0.0F, 1.0F, 1.0F, HudChrome.scaleAlpha(-1, contentAlpha), iconR, 240);
            }

            float cx = ix + icon + 8.0F * s;
            float valueTop = Math.round(q.t() + textInset - shift);
            int color = HudChrome.scaleAlpha(-1, contentAlpha * (row.here() ? 1.0F : 0.62F));

            for (int ixx = 0; ixx < 3; ixx++) {
               HudChrome.tabular(dl, t.text(), t.textPx(), cx, valueTop, color, row.values()[ixx], true);
               cx += plan.slotW()[ixx] + 12.0F * s;
            }

            if (clip) {
               dl.popClipRect();
            }
         }
      }
   }

   private static float rowWidth(float nameW, float s) {
      return Math.round(12.0F * s + nameW);
   }

   private HudRenderer.ListPlan planModuleList(HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 18.0F * s;
      List<ModuleListRows.Row> rows = this.moduleRows.ordered(t.text(), t.textPx(), HudChrome::width, state.anchorBottom);
      float widest = 0.0F;
      float height = 0.0F;

      for (int i = 0; i < rows.size(); i++) {
         ModuleListRows.Row row = rows.get(i);
         widest = Math.max(widest, row.nameW());
         height += rowFull * smooth(row.anim());
      }

      if (rows.isEmpty()) {
         this.moduleListW = editMode ? rowWidth(HudChrome.width(t.label(), t.labelPx(), "No active modules"), s) : 0.0F;
         return new HudRenderer.ListPlan(rows, t, (float)Math.max(1, Math.round(this.moduleListW)), (float)Math.round(rowFull));
      } else {
         if (editMode) {
            height = Math.max(height, rowFull);
         }

         float target = rowWidth(widest, s);
         if (!(target >= this.moduleListW) && !(Math.abs(target - this.moduleListW) < 0.5F)) {
            this.moduleListW = this.moduleListW + (target - this.moduleListW) * (1.0F - (float)Math.exp(-16.0F * dt));
         } else {
            this.moduleListW = target;
         }

         return new HudRenderer.ListPlan(rows, t, (float)Math.round(this.moduleListW), (float)Math.max(1, Math.round(height)));
      }
   }

   private void drawModuleList(
      ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.ListPlan plan, boolean shadow, HudRenderer.DrawTransform tr, float screenW
   ) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         boolean right = alignsRight(state, screenW);
         float rowFull = 18.0F * s;
         List<HudRenderer.Step> steps = new ArrayList<>(Math.max(1, plan.rows().size()));
         if (plan.rows().isEmpty()) {
            float rowW = rowWidth(HudChrome.width(t.label(), t.labelPx(), "No active modules"), s);
            float l = right ? x + w - rowW : x;
            steps.add(new HudRenderer.Step(-1, l, y, l + rowW, (float)Math.round(y + rowFull), a));
         } else {
            float cy = y;

            for (int i = 0; i < plan.rows().size(); i++) {
               ModuleListRows.Row row = plan.rows().get(i);
               float open = smooth(row.anim());
               float rowH = rowFull * open;
               float top = Math.round(cy);
               float bottom = Math.round(cy + rowH);
               cy += rowH;
               if (!(bottom <= top)) {
                  float rowW = rowWidth(row.nameW(), s);
                  float slide = Math.round(rowW * (1.0F - open));
                  float l = right ? x + w - rowW + slide : x - slide;
                  steps.add(new HudRenderer.Step(i, l, top, l + rowW, bottom, a * Math.min(1.0F, row.anim() * 1.5F)));
               }
            }
         }

         this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
         float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);
         float pad = 6.0F * s;

         for (HudRenderer.Step q : steps) {
            if (q.item() < 0) {
               float labelW = HudChrome.width(t.label(), t.labelPx(), "No active modules");
               float tx = right ? q.r() - pad - labelW : q.l() + pad;
               HudChrome.text(
                  dl,
                  t.label(),
                  t.labelPx(),
                  tx,
                  q.t() + (float)Math.floor((rowFull - t.labelPx()) * 0.5F),
                  HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
                  "No active modules",
                  true
               );
            } else {
               ModuleListRows.Row row = plan.rows().get(q.item());
               float stepH = q.b() - q.t();
               float tx = right ? q.r() - pad - row.nameW() : q.l() + pad;
               float ty = q.t() + textInset - (rowFull - stepH) * 0.5F;
               boolean clip = stepH < rowFull - 0.5F;
               if (clip) {
                  dl.pushClipRect(q.l(), q.t(), q.r(), q.b(), true);
               }

               HudChrome.text(
                  dl, t.text(), t.textPx(), Math.round(tx), Math.round(ty), HudChrome.scaleAlpha(-1, a * row.anim() * row.anim()), row.module().name(), true
               );
               if (clip) {
                  dl.popClipRect();
               }
            }
         }
      }
   }

   private float updateToastShift(Minecraft client, HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float target = !editMode && this.draggingId == null ? toastClearance(client, state) : 0.0F;
      if (!editMode && !(Math.abs(target - this.toastShift) < 0.5F)) {
         this.toastShift = this.toastShift + (target - this.toastShift) * (1.0F - (float)Math.exp(-12.0F * dt));
      } else {
         this.toastShift = target;
      }

      return this.toastShift;
   }

   private static float toastClearance(Minecraft client, HudLayoutStore.HudElementState state) {
      if (state.anchorX == 2 && !state.anchorBottom && !client.gui.hud.isHidden()) {
         Window window = client.getWindow();
         int gui = window.getGuiScale();
         int guiW = window.getGuiScaledWidth();
         float bottom = 0.0F;

         for (Object entry : ((AccessorHudToastManager)client.gui.toastManager()).spectre$hudVisibleToasts()) {
            AccessorHudToastInstance instance = (AccessorHudToastInstance)entry;
            Toast toast = instance.spectre$hudToast();
            float shown = instance.spectre$hudVisibility() == Visibility.HIDE ? instance.spectre$hudVisiblePortion() : 1.0F;
            float left = toast.xPos(guiW, shown) * gui;
            if (!(left >= state.x + state.width) && !(left + toast.width() * gui <= state.x)) {
               bottom = Math.max(bottom, (toast.yPos(instance.spectre$hudFirstSlot()) + toast.height()) * gui);
            }
         }

         return bottom <= 0.0F ? 0.0F : Mth.clamp(bottom + 10.0F * ImGuiManager.getInstance().uiScale() - state.y, 0.0F, bottom);
      } else {
         return 0.0F;
      }
   }

   private static float iconSize(float s) {
      return s >= 1.0F ? 18.0F * Math.max(1.0F, (float)Math.floor(s + 0.25F)) : Math.round(18.0F * s);
   }

   private HudRenderer.PotionPlan planPotions(HudLayoutStore.HudElementState state, List<HudRenderer.PotionRow> rows, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 22.0F * s;
      List<HudRenderer.PotionRow> sorted = new ArrayList<>(rows);
      sorted.sort(Comparator.comparingInt(HudRenderer.PotionRow::ticks).thenComparing(HudRenderer.PotionRow::name));
      if (state.anchorBottom) {
         Collections.reverse(sorted);
      }

      List<HudRenderer.PotionLine> lines = new ArrayList<>(sorted.size());
      float target = 0.0F;
      float fixed = 6.0F * s * 2.0F + Math.min(iconSize(s), rowFull - 2.0F) + 6.0F * s + 14.0F * s;

      for (HudRenderer.PotionRow row : sorted) {
         float nameW = HudChrome.width(t.text(), t.textPx(), row.name());
         float ampW = row.amplifier().isEmpty() ? 0.0F : HudChrome.width(t.text(), t.textPx(), row.amplifier());
         float durationW = row.infinite() ? HudChrome.width(t.text(), t.textPx(), "∞") : HudChrome.tabularWidth(t.text(), t.textPx(), row.duration());
         lines.add(new HudRenderer.PotionLine(row, nameW, ampW, durationW));
         target = Math.max(target, fixed + nameW + (ampW > 0.0F ? 4.0F * s + ampW : 0.0F) + durationW);
      }

      if (lines.isEmpty()) {
         this.removeSlot("potions", t.textPx());
         float w = editMode ? 6.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No active effects") : 1.0F;
         return new HudRenderer.PotionPlan(lines, t, (float)Math.round(w), (float)Math.round(rowFull));
      } else {
         float w = this.slot("potions", t.textPx(), (float)Math.ceil(target), dt);
         return new HudRenderer.PotionPlan(lines, t, (float)Math.round(w), (float)Math.round(rowFull * lines.size()));
      }
   }

   private void drawPotions(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.PotionPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         float rowFull = 22.0F * s;
         float pad = 6.0F * s;
         int count = Math.max(1, plan.lines().size());
         List<HudRenderer.Step> steps = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            steps.add(
               new HudRenderer.Step(plan.lines().isEmpty() ? -1 : i, x, (float)Math.round(y + rowFull * i), x + w, (float)Math.round(y + rowFull * (i + 1)), a)
            );
         }

         this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
         if (plan.lines().isEmpty()) {
            HudChrome.text(
               dl,
               t.label(),
               t.labelPx(),
               x + pad,
               y + (float)Math.floor((rowFull - t.labelPx()) * 0.5F),
               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
               "No active effects",
               true
            );
         } else {
            float icon = Math.min(iconSize(s), rowFull - 2.0F);
            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);

            for (HudRenderer.Step q : steps) {
               HudRenderer.PotionLine line = plan.lines().get(q.item());
               HudRenderer.PotionRow row = line.row();
               float ix = Math.round(q.l() + pad);
               float iy = Math.round(q.t() + (rowFull - icon) * 0.5F);
               float iconAlpha = a;
               if (!row.infinite() && row.ticks() <= 200) {
                  iconAlpha = a * (0.55F + 0.45F * Math.abs((float)Math.cos(row.ticks() * Math.PI / 10.0)));
               }

               ThemeImages.Texture texture = GameIcons.effect(row.effect());
               if (texture != null) {
                  dl.addImage(texture.id(), ix, iy, ix + icon, iy + icon, 0.0F, 0.0F, 1.0F, 1.0F, HudChrome.scaleAlpha(-1, iconAlpha));
               } else {
                  int color = GuiPalette.abgr(0xFF000000 | row.effect().value().getColor());
                  dl.addCircleFilled(ix + icon * 0.5F, iy + icon * 0.5F, icon * 0.3F, HudChrome.scaleAlpha(color, iconAlpha), 16);
               }

               float ty = q.t() + textInset;
               float tx = ix + icon + 6.0F * s;
               HudChrome.text(dl, t.text(), t.textPx(), tx, ty, HudChrome.scaleAlpha(-1, a), row.name(), true);
               if (line.ampW() > 0.0F) {
                  HudChrome.text(
                     dl, t.text(), t.textPx(), tx + line.nameW() + 4.0F * s, ty, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), row.amplifier(), true
                  );
               }

               float dx = q.r() - pad - line.durationW();
               if (row.infinite()) {
                  HudChrome.text(dl, t.text(), t.textPx(), dx, ty, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), "∞", true);
               } else {
                  int color = row.expiringSoon() ? HudChrome.WARN : HudChrome.TEXT_LABEL;
                  HudChrome.tabular(dl, t.text(), t.textPx(), dx, ty, HudChrome.scaleAlpha(color, a), row.duration(), true);
               }
            }
         }
      }
   }

   private HudRenderer.VanishPlan planVanish(HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 22.0F * s;
      List<HudRenderer.VanishLine> lines = new ArrayList<>();

      for (AntiVanishModule.HudEntry entry : AntiVanishModule.hudEntries()) {
         String tag = AntiVanishModule.hudTag(entry);
         String value = AntiVanishModule.hudValue(entry);
         boolean urgent = "ALERT".equals(tag) || "VANISH".equals(tag);
         lines.add(new HudRenderer.VanishLine(tag, value, urgent, HudChrome.width(t.label(), t.labelPx(), tag), HudChrome.width(t.text(), t.textPx(), value)));
      }

      if (state.anchorBottom) {
         Collections.reverse(lines);
      }

      if (lines.isEmpty()) {
         this.removeSlot("vanish", t.textPx());
         float w = editMode ? 8.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No vanish alerts") : 1.0F;
         return new HudRenderer.VanishPlan(lines, t, 0.0F, (float)Math.round(w), (float)Math.round(rowFull));
      } else {
         float tagColumn = 0.0F;
         float valueW = 0.0F;

         for (HudRenderer.VanishLine line : lines) {
            tagColumn = Math.max(tagColumn, line.tagW());
            valueW = Math.max(valueW, line.valueW());
         }

         float target = 8.0F * s * 2.0F + tagColumn + 8.0F * s + valueW;
         float w = this.slot("vanish", t.textPx(), (float)Math.ceil(target), dt);
         return new HudRenderer.VanishPlan(lines, t, tagColumn, (float)Math.round(w), (float)Math.round(rowFull * lines.size()));
      }
   }

   private void drawVanish(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.VanishPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         float rowFull = 22.0F * s;
         float pad = 8.0F * s;
         int count = Math.max(1, plan.lines().size());
         List<HudRenderer.Step> steps = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            steps.add(
               new HudRenderer.Step(plan.lines().isEmpty() ? -1 : i, x, (float)Math.round(y + rowFull * i), x + w, (float)Math.round(y + rowFull * (i + 1)), a)
            );
         }

         this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
         if (plan.lines().isEmpty()) {
            HudChrome.text(
               dl,
               t.label(),
               t.labelPx(),
               x + pad,
               y + (float)Math.floor((rowFull - t.labelPx()) * 0.5F),
               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
               "No vanish alerts",
               true
            );
         } else {
            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);

            for (HudRenderer.Step q : steps) {
               HudRenderer.VanishLine line = plan.lines().get(q.item());
               float ty = q.t() + textInset;
               float labelTop = ty + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx());
               float tx = q.l() + pad;
               HudChrome.text(
                  dl, t.label(), t.labelPx(), tx, labelTop, HudChrome.scaleAlpha(line.urgent() ? HudChrome.WARN : HudChrome.TEXT_LABEL, a), line.tag(), true
               );
               HudChrome.text(dl, t.text(), t.textPx(), tx + plan.tagColumn() + 8.0F * s, ty, HudChrome.scaleAlpha(-1, a), line.value(), true);
            }
         }
      }
   }

   private HudRenderer.StaffPlan planStaff(HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 22.0F * s;
      StaffListModule.HudList list = StaffListModule.hudRows();
      List<HudRenderer.StaffLine> lines = new ArrayList<>(list.rows().size() + 1);

      for (StaffListModule.HudRow row : list.rows()) {
         int face = this.staffFace(row.id(), row.nameHidden());
         String status = row.presence() == StaffListModule.Presence.VANISHED
            ? "VANISHED"
            : (row.presence() == StaffListModule.Presence.SPECTATOR ? "SPECTATOR" : "");
         float statusW = status.isEmpty() ? 0.0F : HudChrome.width(t.label(), t.labelPx(), status);
         if (row.sameServer()) {
            String value = row.nearby() ? row.distanceLabel() : row.sameServerAge();
            float valueW = HudChrome.tabularWidth(t.text(), t.textPx(), value);
            float serverTagW = HudChrome.width(t.label(), t.labelPx(), "WATCHING YOU");
            float nameW = HudChrome.width(t.text(), t.textPx(), row.name()) + (status.isEmpty() ? 0.0F : 8.0F * s + statusW);
            lines.add(
               new HudRenderer.StaffLine(
                  row.name(),
                  face,
                  "WATCHING YOU",
                  false,
                  nameW,
                  serverTagW + 4.0F * s + valueW,
                  true,
                  row.nearby(),
                  row.alertLevel(),
                  status,
                  statusW,
                  value,
                  valueW,
                  serverTagW
               )
            );
         } else {
            lines.add(HudRenderer.StaffLine.plain(row.name(), face, status, false, HudChrome.width(t.text(), t.textPx(), row.name()), statusW));
         }
      }

      if (list.more() > 0) {
         String more = "+" + list.more() + " more";
         lines.add(HudRenderer.StaffLine.plain(more, 0, "", true, HudChrome.width(t.label(), t.labelPx(), more), 0.0F));
      }

      if (state.anchorBottom) {
         Collections.reverse(lines);
      }

      if (lines.isEmpty()) {
         this.removeSlot("staff", t.textPx());
         boolean placeholder = editMode || StaffListModule.shouldShowHud();
         float w = placeholder ? 8.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No staff online") : 1.0F;
         return new HudRenderer.StaffPlan(lines, t, "", (float)Math.round(w), (float)Math.round(rowFull));
      } else {
         String count = Integer.toString(list.rows().size() + list.more());
         float headerW = HudChrome.width(t.label(), t.labelPx(), "STAFF") + 5.0F * s + HudChrome.tabularWidth(t.text(), t.textPx(), count);
         float nameColumn = 0.0F;
         float stateColumn = 0.0F;

         for (HudRenderer.StaffLine line : lines) {
            nameColumn = Math.max(nameColumn, line.nameW());
            stateColumn = Math.max(stateColumn, line.stateW());
         }

         float rowW = faceSize(s) + 6.0F * s + nameColumn + (stateColumn > 0.0F ? 14.0F * s + stateColumn : 0.0F);
         float target = 8.0F * s * 2.0F + Math.max(headerW, rowW);
         float w = this.slot("staff", t.textPx(), (float)Math.ceil(target), dt);
         return new HudRenderer.StaffPlan(lines, t, count, (float)Math.round(w), (float)Math.round(20.0F * s + rowFull * lines.size()));
      }
   }

   private void drawStaff(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.StaffPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         float rowFull = 22.0F * s;
         float pad = 8.0F * s;
         float labelInset = (float)Math.floor((rowFull - t.labelPx()) * 0.5F);
         if (plan.lines().isEmpty()) {
            List<HudRenderer.Step> empty = List.of(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + rowFull), a));
            this.drawRows(dl, empty, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
            HudChrome.text(dl, t.label(), t.labelPx(), x + pad, y + labelInset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), "No staff online", true);
         } else {
            float headerH = 20.0F * s;
            List<HudRenderer.Step> steps = new ArrayList<>(plan.lines().size() + 1);
            steps.add(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + headerH), a));

            for (int i = 0; i < plan.lines().size(); i++) {
               steps.add(new HudRenderer.Step(i, x, (float)Math.round(y + headerH + rowFull * i), x + w, (float)Math.round(y + headerH + rowFull * (i + 1)), a));
            }

            this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);

            for (int i = 1; i < steps.size(); i++) {
               HudRenderer.Step q = steps.get(i);
               HudRenderer.StaffLine line = plan.lines().get(q.item());
               if (line.server()) {
                  int corners = convexCorners(q, steps.get(i - 1), i < steps.size() - 1 ? steps.get(i + 1) : null);
                  float glow = 0.14F;
                  if (line.alert() > 0.0F) {
                     float elapsed = (1.0F - line.alert()) * 4000.0F;
                     float wave = 0.5F + 0.5F * (float)Math.cos(elapsed * 2.0 * Math.PI / 500.0);
                     glow += 0.24F * line.alert() * wave;
                  }

                  int tint = HudChrome.scaleAlpha(HudChrome.WARN, a * glow);
                  if (corners == 256) {
                     dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), tint);
                  } else {
                     dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), tint, rowsRadius(s), corners);
                  }

                  dl.addRectFilled(q.l() + 3.0F * s, q.t() + 4.0F * s, q.l() + 5.0F * s, q.b() - 4.0F * s, HudChrome.scaleAlpha(HudChrome.WARN, a), 1.0F * s);
               }
            }

            HudRenderer.Step head = steps.get(0);
            float countTop = head.t() + (float)Math.floor((head.b() - head.t() - t.textPx()) * 0.5F);
            HudChrome.text(
               dl,
               t.label(),
               t.labelPx(),
               x + pad,
               countTop + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx()),
               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
               "STAFF",
               true
            );
            HudChrome.tabular(
               dl,
               t.text(),
               t.textPx(),
               x + pad + HudChrome.width(t.label(), t.labelPx(), "STAFF") + 5.0F * s,
               countTop,
               HudChrome.scaleAlpha(-1, a),
               plan.count(),
               true
            );
            float face = faceSize(s);
            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);
            float nameX = pad + face + 6.0F * s;

            for (int ix = 1; ix < steps.size(); ix++) {
               HudRenderer.Step q = steps.get(ix);
               HudRenderer.StaffLine line = plan.lines().get(q.item());
               if (line.more()) {
                  HudChrome.text(
                     dl, t.label(), t.labelPx(), q.l() + nameX, q.t() + labelInset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), line.name(), true
                  );
               } else {
                  if (line.face() != 0) {
                     drawFace(dl, line.face(), Math.round(q.l() + pad), Math.round(q.t() + (q.b() - q.t() - face) * 0.5F), face, s, a);
                  }

                  float ty = q.t() + textInset;
                  float labelTop = ty + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx());
                  HudChrome.text(dl, t.text(), t.textPx(), q.l() + nameX, ty, HudChrome.scaleAlpha(-1, a), line.name(), true);
                  if (line.server()) {
                     if (!line.note().isEmpty()) {
                        HudChrome.text(
                           dl,
                           t.label(),
                           t.labelPx(),
                           q.l() + nameX + line.nameW() - line.noteW(),
                           labelTop,
                           HudChrome.scaleAlpha(HudChrome.WARN, a),
                           line.note(),
                           true
                        );
                     }

                     float sx = q.r() - pad - line.stateW();
                     HudChrome.text(dl, t.label(), t.labelPx(), sx, labelTop, HudChrome.scaleAlpha(HudChrome.WARN, a), line.state(), true);
                     HudChrome.tabular(
                        dl,
                        t.text(),
                        t.textPx(),
                        sx + line.serverTagW() + 4.0F * s,
                        ty,
                        HudChrome.scaleAlpha(line.nearby() ? -1 : HudChrome.TEXT_LABEL, a),
                        line.value(),
                        true
                     );
                  } else if (!line.state().isEmpty()) {
                     HudChrome.text(
                        dl, t.label(), t.labelPx(), q.r() - pad - line.stateW(), labelTop, HudChrome.scaleAlpha(HudChrome.WARN, a), line.state(), true
                     );
                  }
               }
            }
         }
      }
   }

   private static float faceSize(float s) {
      int size = Math.round(Math.round(16.0F * s) / 8.0F) * 8;
      if (size > 22.0F * s - 4.0F * s) {
         size -= 8;
      }

      return Math.max(8, size);
   }

   private static void drawFace(ImDrawList dl, int texture, float x, float y, float size, float s, float a) {
      float r = Math.max(1.0F, (float)Math.round(2.0F * s));
      int tint = HudChrome.scaleAlpha(-1, a);
      dl.addImageRounded(texture, x, y, x + size, y + size, 0.125F, 0.125F, 0.25F, 0.25F, tint, r, 240);
      dl.addImageRounded(texture, x, y, x + size, y + size, 0.625F, 0.125F, 0.75F, 0.25F, tint, r, 240);
      dl.addRect(x, y, x + size, y + size, HudChrome.scaleAlpha(402653183, a), r, 0, 1.0F);
   }

   int staffFace(UUID id, boolean hidden) {
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener conn = mc.getConnection();
      PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
      Identifier skin;
      if (info != null) {
         skin = info.getSkin().body().texturePath();
         this.staffSkins.put(id, skin);
         if (hidden) {
            skin = NameProtectModule.hiddenHead(id, info.getSkin()).body().texturePath();
         }
      } else if (hidden) {
         skin = NameProtectModule.hiddenHead(id, DefaultPlayerSkin.get(id)).body().texturePath();
      } else {
         skin = this.staffSkins.get(id);
         if (skin == null) {
            skin = DefaultPlayerSkin.get(id).body().texturePath();
         }
      }

      GpuTexture texture;
      try {
         texture = mc.getTextureManager().getTexture(skin).getTexture();
      } catch (IllegalStateException var10) {
         return 0;
      }

      if (texture instanceof GlTexture gl && !gl.isClosed()) {
         if (this.nearestSkins.add(texture)) {
            int bound = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, gl.glId());
            GL11.glTexParameteri(3553, 10241, 9728);
            GL11.glTexParameteri(3553, 10240, 9728);
            GL11.glBindTexture(3553, bound);
         }

         return gl.glId();
      } else {
         return 0;
      }
   }

   private float planRadar(HudLayoutStore.HudElementState state, boolean editMode) {
      if (!editMode && !RadarModule.shouldShowHud()) {
         return 1.0F;
      } else {
         float size = this.radarModule != null ? this.radarModule.size() : 150.0F;
         return Math.max(2.0F, (float)Math.round(size * scaleOf(state)));
      }
   }

   private void drawRadar(ImDrawList dl, HudLayoutStore.HudElementState state, float size, Minecraft client, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F) && !(size <= 1.0F)) {
         float s = scaleOf(state);
         HudRenderer.Type t = HudRenderer.Type.of(s);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float r = size * 0.5F;
         float cx = x + r;
         float cy = y + r;
         int accent = this.hudAccent();
         Surface surface = hudSurface(state);
         if (shadow) {
            drawDiscShadow(dl, cx, cy, r, s, a);
         }

         dl.addCircleFilled(cx, cy, r, HudChrome.scaleAlpha(GuiPalette.fill(surface), a), 0);
         SurfaceBackground.drawDisc(dl, surface, cx, cy, r, a);
         dl.addCircle(cx, cy, r - 0.5F, HudChrome.scaleAlpha(402653183, a), 0, 1.0F);
         this.measureRadarLetters(t.label(), t.labelPx());
         float letterR = r - 10.0F * s - this.radarLetterHalf;
         float reach = letterR - this.radarLetterHalf - 4.0F * s;
         float rangeR = r - 5.0F * s;
         int hairline = HudChrome.scaleAlpha(536870911, a);

         for (float ring : RADAR_RINGS) {
            if (rangeR * ring < reach) {
               dl.addCircle(cx, cy, rangeR * ring, hairline, 0, 1.0F);
            }
         }

         float hx = (float)Math.floor(cx) + 0.5F;
         float hy = (float)Math.floor(cy) + 0.5F;
         dl.addLine(hx, cy - reach, hx, cy + reach, hairline, 1.0F);
         dl.addLine(cx - reach, hy, cx + reach, hy, hairline, 1.0F);
         LocalPlayer player = client.player;
         boolean rotate = this.radarModule == null || this.radarModule.rotate();
         float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         double yaw = player != null ? Math.toRadians(player.getViewYRot(partial)) : 0.0;
         float sin = (float)Math.sin(yaw);
         float cos = (float)Math.cos(yaw);

         for (int i = 0; i < RADAR_LETTERS.length; i++) {
            int wx = RADAR_LETTER_DX[i];
            int wz = RADAR_LETTER_DZ[i];
            float ox = rotate ? -wx * cos - wz * sin : wx;
            float oy = rotate ? wx * sin - wz * cos : wz;
            int color = i == 0 ? accent : HudChrome.TEXT_LABEL;
            HudChrome.text(
               dl,
               t.label(),
               t.labelPx(),
               Math.round(cx + ox * letterR - this.radarLetterW[i] * 0.5F),
               Math.round(cy + oy * letterR - this.radarLetterMid),
               HudChrome.scaleAlpha(color, a),
               RADAR_LETTERS[i],
               true
            );
         }

         if (this.radarModule != null && player != null && client.level != null) {
            float range = Math.max(1.0F, this.radarModule.range());
            double rangeSq = (double)range * range;
            float perBlock = rangeR / range;
            double px0 = Mth.lerp((double)partial, player.xo, player.getX());
            double pz0 = Mth.lerp((double)partial, player.zo, player.getZ());
            float dot = 3.2F * s;
            float edge = Math.max(1.0F, 0.75F * s);
            int dotEdge = HudChrome.scaleAlpha(-1946157056, a);
            int dotColor = HudChrome.scaleAlpha(GuiPalette.abgr(this.radarModule.dotColor()), a);

            for (AbstractClientPlayer other : client.level.players()) {
               if (other != player && this.radarModule.shows(other)) {
                  double dx = Mth.lerp((double)partial, other.xo, other.getX()) - px0;
                  double dz = Mth.lerp((double)partial, other.zo, other.getZ()) - pz0;
                  if (!(dx * dx + dz * dz > rangeSq)) {
                     float ox = rotate ? (float)(-dx * cos - dz * sin) : (float)dx;
                     float oy = rotate ? (float)(dx * sin - dz * cos) : (float)dz;
                     float bx = cx + ox * perBlock;
                     float by = cy + oy * perBlock;
                     dl.addCircleFilled(bx, by, dot + edge, dotEdge, 12);
                     dl.addCircleFilled(bx, by, dot, dotColor, 12);
                  }
               }
            }
         }

         dl.addCircleFilled(cx, cy, 6.0F * s, HudChrome.scaleAlpha(accent, a * 0.25F), 0);
         dl.addCircleFilled(cx, cy, 3.0F * s, HudChrome.scaleAlpha(accent, a), 0);
         float ux = rotate ? 0.0F : -sin;
         float uy = rotate ? -1.0F : cos;
         float tip = r - 1.0F - 1.5F * s;
         float base = tip - 5.0F * s;
         float half = 3.5F * s;
         dl.addTriangleFilled(
            cx + ux * tip,
            cy + uy * tip,
            cx + ux * base - uy * half,
            cy + uy * base + ux * half,
            cx + ux * base + uy * half,
            cy + uy * base - ux * half,
            HudChrome.scaleAlpha(accent, a)
         );
      }
   }

   private static void drawDiscShadow(ImDrawList dl, float cx, float cy, float r, float s, float a) {
      float sp = Math.max(1.0F, s);
      int color = HudChrome.scaleAlpha(218103808, a);

      for (int k = 3; k >= 1; k--) {
         dl.addCircleFilled(cx, cy + sp * 0.5F, r + (k + 0.5F) * sp, color, 0);
      }
   }

   private void measureRadarLetters(ImFont font, int px) {
      if (font != this.radarLetterFont || px != this.radarLetterPx) {
         this.radarLetterFont = font;
         this.radarLetterPx = px;

         for (int i = 0; i < RADAR_LETTERS.length; i++) {
            this.radarLetterW[i] = HudChrome.width(font, px, RADAR_LETTERS[i]);
         }

         float capTop = this.capTop(font, px);
         float ascent = HudChrome.ascent(font, px);
         this.radarLetterMid = (capTop + ascent) * 0.5F;
         this.radarLetterHalf = (ascent - capTop) * 0.5F;
      }
   }

   private HudRenderer.HotkeyPlan planHotkeys(HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      float rowFull = 22.0F * s;
      List<HudRenderer.HotkeyLine> lines = !editMode && !HotkeysModule.shouldShowHud() ? List.of() : this.hotkeyLinesFor(t);
      if (lines.isEmpty()) {
         this.removeSlot("hotkeys", t.textPx());
         float w = editMode ? 8.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No keys bound") : 1.0F;
         return new HudRenderer.HotkeyPlan(lines, t, (float)Math.round(w), (float)Math.round(rowFull));
      } else {
         float rows = this.hotkeyNameColumn + 16.0F * s + keycapWidth(this.hotkeyKeyColumn, s);
         float target = 8.0F * s * 2.0F + Math.max(this.hotkeyHeaderW, rows);
         float w = this.slot("hotkeys", t.textPx(), (float)Math.ceil(target), dt);
         return new HudRenderer.HotkeyPlan(lines, t, (float)Math.round(w), (float)Math.round(20.0F * s + rowFull * lines.size()));
      }
   }

   private List<HudRenderer.HotkeyLine> hotkeyLinesFor(HudRenderer.Type t) {
      long revision = ModuleRevision.current();
      if (revision == this.hotkeyRevision && t.text() == this.hotkeyFont && t.textPx() == this.hotkeyTextPx && t.labelPx() == this.hotkeyKeyPx) {
         return this.hotkeyLines;
      } else {
         this.hotkeyRevision = revision;
         this.hotkeyFont = t.text();
         this.hotkeyTextPx = t.textPx();
         this.hotkeyKeyPx = t.labelPx();
         List<HudRenderer.HotkeyLine> lines = new ArrayList<>();
         float nameColumn = 0.0F;
         float keyColumn = 0.0F;

         for (ClientModule module : ModuleRegistry.all()) {
            int key = module.keybind();
            if (key != -1) {
               String label = ClickGuiRenderer.keyName(key).toUpperCase(Locale.ROOT);
               float nameW = HudChrome.width(t.text(), t.textPx(), module.name());
               float keyW = HudChrome.width(t.text(), t.labelPx(), label);
               lines.add(new HudRenderer.HotkeyLine(module.name(), label, module.isEnabled(), nameW, keyW));
               nameColumn = Math.max(nameColumn, nameW);
               keyColumn = Math.max(keyColumn, keyW);
            }
         }

         this.hotkeyNameColumn = nameColumn;
         this.hotkeyKeyColumn = keyColumn;
         this.hotkeyHeaderW = HudChrome.width(t.label(), t.labelPx(), "HOTKEYS");
         this.hotkeyCapMid = (this.capTop(t.text(), t.labelPx()) + HudChrome.ascent(t.text(), t.labelPx())) * 0.5F;
         this.hotkeyLines = List.copyOf(lines);
         return this.hotkeyLines;
      }
   }

   private void drawHotkeys(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.HotkeyPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F)) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         HudRenderer.Type t = plan.type();
         float rowFull = 22.0F * s;
         float pad = 8.0F * s;
         if (plan.lines().isEmpty()) {
            List<HudRenderer.Step> empty = List.of(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + rowFull), a));
            this.drawRows(dl, empty, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
            HudChrome.text(
               dl,
               t.label(),
               t.labelPx(),
               x + pad,
               y + (float)Math.floor((rowFull - t.labelPx()) * 0.5F),
               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
               "No keys bound",
               true
            );
         } else {
            float headerH = 20.0F * s;
            List<HudRenderer.Step> steps = new ArrayList<>(plan.lines().size() + 1);
            steps.add(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + headerH), a));

            for (int i = 0; i < plan.lines().size(); i++) {
               steps.add(new HudRenderer.Step(i, x, (float)Math.round(y + headerH + rowFull * i), x + w, (float)Math.round(y + headerH + rowFull * (i + 1)), a));
            }

            this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);
            HudRenderer.Step head = steps.get(0);
            float countTop = head.t() + (float)Math.floor((head.b() - head.t() - t.textPx()) * 0.5F);
            float labelTop = countTop + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx());
            HudChrome.text(dl, t.label(), t.labelPx(), x + pad, labelTop, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), "HOTKEYS", true);
            float capW = keycapWidth(this.hotkeyKeyColumn, s);
            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);

            for (int i = 1; i < steps.size(); i++) {
               HudRenderer.Step q = steps.get(i);
               HudRenderer.HotkeyLine line = plan.lines().get(q.item());
               HudChrome.text(
                  dl, t.text(), t.textPx(), q.l() + pad, q.t() + textInset, HudChrome.scaleAlpha(line.on() ? -1 : HudChrome.TEXT_LABEL, a), line.name(), true
               );
               this.drawKeycap(dl, t, line, q.r() - pad, (q.t() + q.b()) * 0.5F, capW, s, a);
            }
         }
      }
   }

   private void drawKeycap(ImDrawList dl, HudRenderer.Type t, HudRenderer.HotkeyLine line, float right, float centerY, float capW, float s, float a) {
      float capH = keycapHeight(s);
      float l = Math.round(right - capW);
      float top = Math.round(centerY - capH * 0.5F);
      float r = Math.min(hudRounding(), 4.0F) * s;
      dl.addRectFilled(l, top, l + capW, top + capH, HudChrome.scaleAlpha(452984831, a), r);
      dl.addRect(l, top, l + capW, top + capH, HudChrome.scaleAlpha(402653183, a), r, 0, 1.0F);
      HudChrome.text(
         dl,
         t.text(),
         t.labelPx(),
         Math.round(l + (capW - line.keyW()) * 0.5F),
         Math.round(top + capH * 0.5F - this.hotkeyCapMid),
         HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
         line.key(),
         true
      );
   }

   private static float keycapHeight(float s) {
      return Math.round(16.0F * s);
   }

   private static float keycapWidth(float keyW, float s) {
      return Math.max(keycapHeight(s), (float)Math.ceil(keyW + 5.0F * s * 2.0F));
   }

   private HudRenderer.SpotifyPlan planSpotify(HudLayoutStore.HudElementState state, boolean editMode, float dt) {
      float s = scaleOf(state);
      HudRenderer.Type t = HudRenderer.Type.of(s);
      if (!editMode && !SpotifyHudModule.shouldShowHud()) {
         this.removeSlot("spotify", t.textPx());
         this.releaseSpotifyArt();
         this.lyricBlockH = 0.0F;
         return new HudRenderer.SpotifyPlan(
            t,
            HudRenderer.SpotifyLook.HIDDEN,
            HudRenderer.SpotifyGlyph.NONE,
            "",
            "",
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            false,
            null,
            HudRenderer.SpotifyColors.DEFAULT,
            1.0F,
            (float)Math.round(28.0F * s)
         );
      } else {
         SpotifyHudModule module = SpotifyHudModule.get();
         SpotifyHudModule active = SpotifyHudModule.active();
         NowPlaying now = active != null ? active.nowPlaying() : NowPlaying.WAITING;
         boolean track = now.active() && now.hasTrack();
         boolean lyricsSeen = editMode || state.visible;
         if (track && now.source() == NowPlaying.Source.SESSION) {
            return this.planSpotifyCard(module, now, false, lyricsSeen, t, s, editMode, dt);
         } else if (editMode && !track) {
            return this.planSpotifyCard(module, SAMPLE_TRACK, true, true, t, s, true, dt);
         } else {
            this.releaseSpotifyArt();
            this.lyricBlockH = 0.0F;
            return this.planSpotifyCompact(module, now, t, s, editMode, dt);
         }
      }
   }

   private HudRenderer.SpotifyPlan planSpotifyCompact(SpotifyHudModule module, NowPlaying now, HudRenderer.Type t, float s, boolean editMode, float dt) {
      boolean withArtist = module == null || module.showArtist();
      String artist = "";
      HudRenderer.SpotifyGlyph glyph = HudRenderer.SpotifyGlyph.NONE;
      boolean idle = false;
      String title;
      switch (now.status()) {
         case PLAYING:
         case PAUSED:
            glyph = now.playing() ? HudRenderer.SpotifyGlyph.PLAYING : HudRenderer.SpotifyGlyph.PAUSED;
            this.foldTrack(now, t.text());
            title = now.hasTrack() ? this.spotifyTitle : "Not playing";
            if (withArtist) {
               artist = this.spotifyArtist;
            }
            break;
         case NOT_RUNNING:
            title = "No desktop media playing";
            idle = true;
            break;
         case UNSUPPORTED:
            title = "Desktop media · Windows only";
            idle = true;
            break;
         default:
            title = "Nothing playing";
            idle = true;
      }

      ImFont titleFont = idle ? t.label() : t.text();
      int titlePx = idle ? t.labelPx() : t.textPx();
      float titleW = HudChrome.width(titleFont, titlePx, title);
      float artistW = artist.isEmpty() ? 0.0F : HudChrome.width(t.label(), t.labelPx(), artist);
      float glyphW = glyph == HudRenderer.SpotifyGlyph.NONE ? 0.0F : 20.0F * s;
      float fixed = 12.0F * s * 2.0F + glyphW;
      float maxText = Math.max(40.0F * s, (module != null ? module.cardWidth() : 320.0F) * s - fixed);
      float textW = Math.min(Math.max(titleW, artistW), maxText);
      float w = this.slot("spotify", t.textPx(), (float)Math.ceil(fixed + textW), dt);
      float height = artist.isEmpty() ? 28.0F * s : 8.0F * s * 2.0F + t.textPx() + 2.0F * s + t.labelPx();
      float avail = Math.max(1.0F, w - fixed);
      boolean scroll = module == null || module.scrollTitles();
      float loopGap = 40.0F * s;
      float[] offsets = this.marquee(title, artist, titleW, artistW, avail, scroll, now.playing() || editMode, loopGap, s, dt);
      return new HudRenderer.SpotifyPlan(
         t,
         idle ? HudRenderer.SpotifyLook.IDLE : HudRenderer.SpotifyLook.COMPACT,
         glyph,
         title,
         artist,
         titleW,
         artistW,
         offsets[0],
         offsets[1],
         loopGap,
         scroll,
         null,
         HudRenderer.SpotifyColors.of(module),
         (float)Math.round(w),
         (float)Math.round(height)
      );
   }

   private void drawSpotify(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.SpotifyPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {
      float a = tr.alpha();
      if (!(a <= 0.001F) && plan.look() != HudRenderer.SpotifyLook.HIDDEN) {
         float s = scaleOf(state);
         float x = Math.round(state.x + tr.xOffset());
         float y = Math.round(state.y + tr.yOffset());
         float w = state.width;
         float h = state.height;
         Surface surface = hudSurface(state);
         boolean guard = HudChrome.needsTextGuard(surface);
         HudChrome.card(dl, surface, x, y, w, h, hudRounding() * s, a, shadow, s);
         if (plan.look() == HudRenderer.SpotifyLook.CARD) {
            this.drawSpotifyCard(dl, plan, x, y, w, h, s, a, guard);
         } else {
            this.drawSpotifyCompact(dl, plan, x, y, w, h, s, a, guard);
         }
      }
   }

   private void drawSpotifyCompact(ImDrawList dl, HudRenderer.SpotifyPlan plan, float x, float y, float w, float h, float s, float a, boolean guard) {
      HudRenderer.Type t = plan.type();
      float cx = x + 12.0F * s;
      if (plan.glyph() != HudRenderer.SpotifyGlyph.NONE) {
         this.drawPlayState(dl, cx, y + h * 0.5F, 10.0F * s, plan.glyph() == HudRenderer.SpotifyGlyph.PLAYING, a);
         cx += 20.0F * s;
      }

      float avail = Math.max(1.0F, x + w - 12.0F * s - cx);
      if (plan.look() == HudRenderer.SpotifyLook.IDLE) {
         float top = y + (float)Math.floor((h - t.labelPx()) * 0.5F);
         marqueeLine(
            dl,
            t.label(),
            t.labelPx(),
            cx,
            top,
            avail,
            plan.titleW(),
            0.0F,
            plan.loopGap(),
            false,
            plan.title(),
            HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
            guard
         );
      } else {
         float dim = plan.glyph() == HudRenderer.SpotifyGlyph.PAUSED ? 0.62F : 1.0F;
         boolean twoLines = !plan.artist().isEmpty();
         float contentH = twoLines ? t.textPx() + 2.0F * s + t.labelPx() : t.textPx();
         float titleTop = y + (float)Math.floor((h - contentH) * 0.5F);
         marqueeLine(
            dl,
            t.text(),
            t.textPx(),
            cx,
            titleTop,
            avail,
            plan.titleW(),
            plan.titleOffset(),
            plan.loopGap(),
            plan.scroll(),
            plan.title(),
            HudChrome.scaleAlpha(plan.colors().title(), a * dim),
            guard
         );
         if (twoLines) {
            float artistTop = titleTop + t.textPx() + 2.0F * s;
            marqueeLine(
               dl,
               t.label(),
               t.labelPx(),
               cx,
               artistTop,
               avail,
               plan.artistW(),
               plan.artistOffset(),
               plan.loopGap(),
               plan.scroll(),
               plan.artist(),
               HudChrome.scaleAlpha(plan.colors().artist(), a),
               guard
            );
         }
      }
   }

   private void drawPlayState(ImDrawList dl, float left, float centerY, float size, boolean playing, float a) {
      float top = Math.round(centerY - size * 0.5F);
      float bottom = top + Math.round(size);
      if (playing) {
         dl.addTriangleFilled(left, top, left + size * 0.9F, (top + bottom) * 0.5F, left, bottom, HudChrome.scaleAlpha(this.hudAccent(), a));
      } else {
         float bar = Math.max(2.0F, (float)Math.round(size * 0.3F));
         float gap = Math.max(2.0F, (float)Math.round(size * 0.25F));
         int color = HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a);
         float l = Math.round(left + (size - bar * 2.0F - gap) * 0.5F);
         dl.addRectFilled(l, top, l + bar, bottom, color, 1.0F);
         dl.addRectFilled(l + bar + gap, top, l + bar * 2.0F + gap, bottom, color, 1.0F);
      }
   }

   private HudRenderer.SpotifyPlan planSpotifyCard(
      SpotifyHudModule module, NowPlaying track, boolean sample, boolean lyricsSeen, HudRenderer.Type t, float s, boolean editMode, float dt
   ) {
      boolean withArtist = module == null || module.showArtist();
      boolean withArt = module == null || module.showAlbumArt();
      boolean withProgress = module == null || module.showProgress();
      boolean withLyrics = module == null || module.showLyrics();
      boolean scroll = module == null || module.scrollTitles();
      long nowMs = System.currentTimeMillis();
      this.foldTrack(track, t.text());
      String title = this.spotifyTitle;
      String artist = withArtist ? this.spotifyArtist : "";
      boolean timeline = track.hasTimeline();
      long position = track.positionMs();
      if (!sample) {
         this.spotifyClock.update(track, nowMs);
         position = this.spotifyClock.positionAt(nowMs);
      }

      float capTop = this.capTop(t.label(), t.labelPx());
      float transportTop = t.labelPx() + 3.0F * s + t.textPx() + (artist.isEmpty() ? 0.0F : 1.0F * s + t.labelPx()) + 7.0F * s;
      boolean progressBar = withProgress && timeline;
      float columnBottom = progressBar ? transportTop + t.labelPx() + 6.0F * s + progressHeight(s) : transportTop + HudChrome.ascent(t.label(), t.labelPx());
      float header = Math.round(columnBottom - capTop);
      float pad = 10.0F * s;
      float w = Math.round(Math.max(200.0F, module != null ? module.cardWidth() : 320.0F) * s);
      boolean art = withArt && w - pad * 2.0F - header - 10.0F * s >= 128.0F * s;
      float column = w - pad * 2.0F - (art ? header + 10.0F * s : 0.0F);
      String elapsed = "";
      String remaining = "";
      float progress = 0.0F;
      if (timeline) {
         long elapsedSeconds = position / 1000L;
         long totalSeconds = (track.durationMs() + 500L) / 1000L;
         elapsed = formatSeconds(elapsedSeconds);
         remaining = "−" + formatSeconds(Math.max(0L, totalSeconds - elapsedSeconds));
         progress = clamp01((float)position / (float)track.durationMs());
      }

      float titleW = HudChrome.width(t.text(), t.textPx(), title);
      float artistW = artist.isEmpty() ? 0.0F : HudChrome.width(t.label(), t.labelPx(), artist);
      float loopGap = 40.0F * s;
      float[] offsets = this.marquee(title, artist, titleW, artistW, column, scroll, track.playing() || editMode, loopGap, s, dt);
      boolean lyricsWanted = sample ? withLyrics && timeline : lyricsSeen && module != null && module.wantsLyrics(track);
      Lyrics lyrics = !lyricsWanted ? null : (sample ? SAMPLE_LYRICS : module.lyricsFor(track));
      boolean online = module == null || module.lyricsOnline();
      int lines = module == null ? 2 : module.lyricLines();
      SpotifyHudModule.LyricMotion motion = module == null ? SpotifyHudModule.LyricMotion.WAVE : module.lyricMotion();
      SpotifyHudModule.LyricHighlight highlight = module == null ? SpotifyHudModule.LyricHighlight.OFF : module.lyricHighlight();
      HudRenderer.LyricView view = this.planLyrics(lyrics, lyricsWanted, online, position, lines, motion, highlight, t, w - pad * 2.0F, s, dt, nowMs);
      float playing = track.playing() ? 1.0F : 0.0F;
      this.lyricPlaying = this.lyricPlaying + (playing - this.lyricPlaying) * (1.0F - (float)Math.exp(-8.0F * dt));
      float lyricAlpha = 0.7F + 0.3F * this.lyricPlaying;
      HudRenderer.SpotifyCard card = new HudRenderer.SpotifyCard(
         track, sample, art, header, pad - capTop, column, timeline, elapsed, remaining, progress, progressBar, view, lyricAlpha
      );
      float height = pad * 2.0F + header + view.height();
      return new HudRenderer.SpotifyPlan(
         t,
         HudRenderer.SpotifyLook.CARD,
         HudRenderer.SpotifyGlyph.NONE,
         title,
         artist,
         titleW,
         artistW,
         offsets[0],
         offsets[1],
         loopGap,
         scroll,
         card,
         HudRenderer.SpotifyColors.of(module),
         w,
         (float)Math.round(height)
      );
   }

   private void drawSpotifyCard(ImDrawList dl, HudRenderer.SpotifyPlan plan, float x, float y, float w, float h, float s, float a, boolean guard) {
      HudRenderer.SpotifyCard card = plan.card();
      HudRenderer.Type t = plan.type();
      float pad = 10.0F * s;
      boolean playing = card.track().playing();
      float left = x + pad;
      if (card.art()) {
         this.drawAlbumArt(dl, card, left, y + pad, card.header(), s, a);
         left += card.header() + 10.0F * s;
      } else {
         this.releaseSpotifyArt();
      }

      float column = card.column();
      float top = y + card.textTop();
      HudChrome.text(
         dl, t.label(), t.labelPx(), left, Math.round(top), HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), playing ? "NOW PLAYING" : "PAUSED", guard
      );
      top += t.labelPx() + 3.0F * s;
      marqueeLine(
         dl,
         t.text(),
         t.textPx(),
         left,
         Math.round(top),
         column,
         plan.titleW(),
         plan.titleOffset(),
         plan.loopGap(),
         plan.scroll(),
         plan.title(),
         HudChrome.scaleAlpha(plan.colors().title(), a),
         guard
      );
      top += t.textPx();
      if (!plan.artist().isEmpty()) {
         top += 1.0F * s;
         marqueeLine(
            dl,
            t.label(),
            t.labelPx(),
            left,
            Math.round(top),
            column,
            plan.artistW(),
            plan.artistOffset(),
            plan.loopGap(),
            plan.scroll(),
            plan.artist(),
            HudChrome.scaleAlpha(plan.colors().artist(), a),
            guard
         );
         top += t.labelPx();
      }

      top = Math.round(top + 7.0F * s);
      this.drawTransport(dl, t, card, left, top, column, s, a, guard);
      if (card.progressBar()) {
         float barTop = Math.round(top + t.labelPx() + 6.0F * s);
         this.drawProgress(dl, left, barTop, column, progressHeight(s), card.progress(), playing, a);
      }

      HudRenderer.LyricView lyrics = card.lyrics();
      if (lyrics.height() > 0.5F) {
         float blockTop = Math.round(y + pad + card.header() + 12.0F * s);
         this.drawLyrics(dl, t, lyrics, plan.colors(), x + pad, blockTop, w - pad * 2.0F, y + h - Math.max(1.0F, s), s, a * card.lyricAlpha(), guard);
      }
   }

   private void drawAlbumArt(ImDrawList dl, HudRenderer.SpotifyCard card, float x, float y, float size, float s, float a) {
      float r = Math.min(hudRounding() * 0.5F, 4.0F) * s;
      if (card.sample()) {
         this.releaseSpotifyArt();
      }

      ThemeImages.Texture texture = card.sample() ? null : this.spotifyArtTexture(card.track().art());
      if (texture != null) {
         dl.addImageRounded(texture.id(), x, y, x + size, y + size, 0.0F, 0.0F, 1.0F, 1.0F, HudChrome.scaleAlpha(-1, a), r, 240);
      } else {
         dl.addRectFilled(x, y, x + size, y + size, HudChrome.scaleAlpha(318767103, a), r);
         float bar = Math.max(2.0F, (float)Math.round(size * 0.07F));
         float gap = Math.max(2.0F, (float)Math.round(bar * 0.8F));
         float base = Math.round(y + size * 0.62F);
         float l = Math.round(x + (size - bar * 3.0F - gap * 2.0F) * 0.5F);
         int color = HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a * 0.45F);
         float[] heights = new float[]{0.2F, 0.34F, 0.26F};

         for (int i = 0; i < heights.length; i++) {
            float bx = l + i * (bar + gap);
            dl.addRectFilled(bx, base - Math.round(size * heights[i]), bx + bar, base, color, 1.0F);
         }
      }

      dl.addRect(x, y, x + size, y + size, HudChrome.scaleAlpha(402653183, a), r, 0, 1.0F);
   }

   private void drawTransport(
      ImDrawList dl, HudRenderer.Type t, HudRenderer.SpotifyCard card, float left, float top, float column, float s, float a, boolean guard
   ) {
      float elapsedW = 0.0F;
      float remainingW = 0.0F;
      if (card.timeline()) {
         elapsedW = HudChrome.tabularWidth(t.label(), t.labelPx(), card.elapsed());
         remainingW = HudChrome.tabularWidth(t.label(), t.labelPx(), card.remaining());
         int color = HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a);
         HudChrome.tabular(dl, t.label(), t.labelPx(), left, top, color, card.elapsed(), guard);
         HudChrome.tabular(dl, t.label(), t.labelPx(), Math.round(left + column - remainingW), top, color, card.remaining(), guard);
      }

      float skip = Math.max(4.0F, (float)Math.round(8.0F * s));
      float play = Math.max(5.0F, (float)Math.round(10.0F * s));
      float spacing = Math.round(14.0F * s);
      float cluster = skip * 2.0F + play + spacing * 2.0F;
      float centre = left + column * 0.5F;
      float margin = 8.0F * s;
      if (!(centre - cluster * 0.5F < left + elapsedW + margin) && !(centre + cluster * 0.5F > left + column - remainingW - margin)) {
         float centerY = top + (this.capTop(t.label(), t.labelPx()) + HudChrome.ascent(t.label(), t.labelPx())) * 0.5F;
         float playLeft = Math.round(centre - play * 0.5F);
         int muted = HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a);
         drawSkipGlyph(dl, playLeft - spacing - skip, centerY, skip, false, muted);
         drawPlayPauseGlyph(dl, playLeft, centerY, play, card.track().playing(), HudChrome.scaleAlpha(HudChrome.TEXT_NAME, a));
         drawSkipGlyph(dl, playLeft + play + spacing, centerY, skip, true, muted);
      }
   }

   private static void drawSkipGlyph(ImDrawList dl, float left, float centerY, float size, boolean next, int color) {
      float top = Math.round(centerY - size * 0.5F);
      float bottom = top + size;
      float mid = (top + bottom) * 0.5F;
      float bar = Math.max(1.0F, (float)Math.round(size * 0.2F));
      if (next) {
         dl.addTriangleFilled(left, top, left + size - bar, mid, left, bottom, color);
         dl.addRectFilled(left + size - bar, top, left + size, bottom, color);
      } else {
         dl.addRectFilled(left, top, left + bar, bottom, color);
         dl.addTriangleFilled(left + size, top, left + size, bottom, left + bar, mid, color);
      }
   }

   private static void drawPlayPauseGlyph(ImDrawList dl, float left, float centerY, float size, boolean playing, int color) {
      float top = Math.round(centerY - size * 0.5F);
      float bottom = top + size;
      if (playing) {
         float bar = Math.max(2.0F, (float)Math.round(size * 0.3F));
         float gap = Math.max(2.0F, (float)Math.round(size * 0.24F));
         float l = Math.round(left + (size - bar * 2.0F - gap) * 0.5F);
         dl.addRectFilled(l, top, l + bar, bottom, color, 1.0F);
         dl.addRectFilled(l + bar + gap, top, l + bar * 2.0F + gap, bottom, color, 1.0F);
      } else {
         float inset = Math.round(size * 0.08F);
         dl.addTriangleFilled(left + inset, top, left + inset + size * 0.86F, (top + bottom) * 0.5F, left + inset, bottom, color);
      }
   }

   private void drawProgress(ImDrawList dl, float left, float top, float width, float height, float progress, boolean playing, float a) {
      float r = height * 0.5F;
      dl.addRectFilled(left, top, left + width, top + height, HudChrome.scaleAlpha(452984831, a), r);
      float fill = Math.round(width * clamp01(progress));
      if (!(fill < 1.0F)) {
         dl.addRectFilled(left, top, left + fill, top + height, HudChrome.scaleAlpha(playing ? this.hudAccent() : HudChrome.TEXT_LABEL, a), r);
      }
   }

   private static float progressHeight(float s) {
      return Math.max(1.0F, (float)Math.round(2.0F * s));
   }

   private HudRenderer.LyricView planLyrics(
      Lyrics lyrics,
      boolean wanted,
      boolean online,
      long position,
      int lines,
      SpotifyHudModule.LyricMotion motion,
      SpotifyHudModule.LyricHighlight highlight,
      HudRenderer.Type t,
      float innerW,
      float s,
      float dt,
      long nowMs
   ) {
      List<HudRenderer.LyricItem> items = List.of();
      String status = null;
      float content = -1.0F;
      boolean live = false;
      if (!wanted) {
         this.lyricLoadingSince = 0L;
         content = 0.0F;
      } else {
         if (lyrics == null || lyrics.status() != Lyrics.Status.LOADING) {
            this.lyricLoadingSince = 0L;
         } else if (this.lyricLoadingSince == 0L) {
            this.lyricLoadingSince = nowMs;
         }

         if (lyrics != null) {
            switch (lyrics.status()) {
               case LOADING:
                  status = nowMs - this.lyricLoadingSince < 700L ? null : "Loading lyrics";
                  break;
               case SYNCED:
                  this.prepareLyrics(lyrics, t.text());
                  if (this.lyricReadable) {
                     items = new ArrayList<>(16);
                     float viewH = this.lyricViewHeight(lyrics.synced(), lines, t, innerW, s);
                     this.syncedItems(items, lyrics.synced(), position, viewH, motion, highlight, t, innerW, s, dt);
                     content = viewH - (t.textPx() - HudChrome.ascent(t.text(), t.textPx()));
                     live = true;
                  } else {
                     status = "Lyrics in a script the HUD font lacks";
                  }
                  break;
               case PLAIN:
                  status = "No synced lyrics";
                  break;
               case INSTRUMENTAL:
                  status = "Instrumental";
                  break;
               case NOT_FOUND:
                  status = online ? "No lyrics found" : "No saved lyrics";
                  break;
               case UNAVAILABLE:
                  status = "Lyrics unavailable";
            }
         }
      }

      if (status != null) {
         content = Math.round((t.textPx() - t.labelPx()) * 0.5F) + HudChrome.ascent(t.label(), t.labelPx());
      }

      if (live) {
         this.lyricLastItems = items;
         this.lyricLeaveItems = null;
      } else {
         items = this.leavingItems(status == null, t, s, dt);
      }

      float target = content < 0.0F ? this.lyricBlockH : (content > 0.0F ? 12.0F * s + Math.round(content) : 0.0F);
      return new HudRenderer.LyricView(items, status, this.easeLyricBlock(target, dt));
   }

   private List<HudRenderer.LyricItem> leavingItems(boolean quiet, HudRenderer.Type t, float s, float dt) {
      this.lyricSource = null;
      if (!this.lyricLastItems.isEmpty()) {
         this.lyricLeaveItems = this.lyricLastItems;
         this.lyricLeave = 0.0F;
         this.lyricLastItems = List.of();
      }

      if (this.lyricLeaveItems == null) {
         return List.of();
      } else {
         this.lyricLeave = Math.min(1.0F, this.lyricLeave + dt / 0.32F);
         if (quiet && !(this.lyricLeave >= 1.0F)) {
            List<HudRenderer.LyricItem> items = new ArrayList<>(this.lyricLeaveItems.size());
            addFaded(items, this.lyricLeaveItems, smooth(this.lyricLeave), 8.0F * s);
            return items;
         } else {
            this.lyricLeaveItems = null;
            return List.of();
         }
      }
   }

   private static void addFaded(List<HudRenderer.LyricItem> items, List<HudRenderer.LyricItem> frozen, float fade, float rise) {
      for (HudRenderer.LyricItem item : frozen) {
         float alpha = item.alpha() * (1.0F - fade);
         if (!(alpha <= 0.004F)) {
            items.add(new HudRenderer.LyricItem(item.text(), item.top() - rise * fade, item.highlight(), alpha, item.progress(), item.fill()));
         }
      }
   }

   private void syncedItems(
      List<HudRenderer.LyricItem> items,
      SyncedLyrics synced,
      long position,
      float viewH,
      SpotifyHudModule.LyricMotion motion,
      SpotifyHudModule.LyricHighlight highlight,
      HudRenderer.Type t,
      float innerW,
      float s,
      float dt
   ) {
      float lineGap = 6.0F * s;
      int currentLast = synced.indexAt(position + 200L);
      int currentFirst = synced.firstWithTimeOf(currentLast);
      int start = Math.max(0, currentFirst);
      if (synced != this.lyricSource) {
         this.lyricSource = synced;
         this.lyricStart = start;
         this.lyricShifts.clear();
         this.lyricClock = 0.0F;
         this.lyricFadeItems = null;
         this.lyricFade = 1.0F;
         this.lyricShown = new float[synced.size() * 2];
         this.lyricBright = new float[synced.size()];
         this.lyricBrightLo = 0;
         this.lyricBrightHi = -1;

         for (int i = Math.max(0, currentFirst); i <= currentLast; i++) {
            this.lyricBright[i] = 1.0F;
         }

         if (currentLast >= 0) {
            this.lyricBrightLo = Math.max(0, currentFirst);
            this.lyricBrightHi = currentLast;
         }
      }

      this.lyricClock += dt;
      this.lyricBreath = (this.lyricBreath + dt) % 1.6F;
      this.lyricCurrentFirst = currentFirst;
      this.lyricCurrentLast = currentLast;
      float stagger = motion == SpotifyHudModule.LyricMotion.WAVE ? 0.045F : 0.0F;
      if (start != this.lyricStart) {
         float distance = start > this.lyricStart ? this.restDistance(this.lyricStart, start, t, innerW, s) : -1.0F;
         if (motion != SpotifyHudModule.LyricMotion.FADE && distance > 0.0F && distance <= viewH + lineGap + 0.5F) {
            if (this.lyricShifts.size() == 8) {
               this.lyricShifts.remove(0);
            }

            this.lyricShifts.add(new HudRenderer.LyricShift(distance, this.lyricClock, start, stagger));
         } else {
            this.lyricFadeItems = this.lyricLastItems;
            this.lyricFade = 0.0F;
            this.lyricShifts.clear();
            this.showView(synced, start, viewH, t, innerW, s);
         }

         this.lyricStart = start;
      }

      this.updateBrightness(currentFirst, currentLast, dt);
      this.lyricFade = Math.min(1.0F, this.lyricFade + dt / 0.32F);
      if (this.lyricFade >= 1.0F) {
         this.lyricFadeItems = null;
      }

      float settled = 1.37F;
      this.lyricShifts.removeIf(shift -> this.lyricClock - shift.at() > settled);
      if (this.lyricShifts.isEmpty()) {
         this.lyricClock = 0.0F;
      }

      float rise = 8.0F * s;
      if (this.lyricFadeItems != null) {
         float fade = smooth(this.lyricFade);
         addFaded(items, this.lyricFadeItems, fade, rise);
         this.addLyricView(items, synced, this.lyricStart, fade, rise * (1.0F - fade), viewH, position, highlight, t, innerW, s, dt);
      } else {
         this.addLyricView(items, synced, this.lyricStart, 1.0F, 0.0F, viewH, position, highlight, t, innerW, s, dt);
      }
   }

   private void addLyricView(
      List<HudRenderer.LyricItem> items,
      SyncedLyrics synced,
      int from,
      float alpha,
      float rise,
      float viewH,
      long position,
      SpotifyHudModule.LyricHighlight mode,
      HudRenderer.Type t,
      float innerW,
      float s,
      float dt
   ) {
      ImFont font = t.text();
      int px = t.textPx();
      float rowGap = 6.0F * s;
      float lineGap = 6.0F * s;
      float slot = px + lineGap;
      boolean karaoke = mode == SpotifyHudModule.LyricHighlight.KARAOKE;
      float restTop = 0.0F;

      for (int i = from; i < synced.size() && restTop <= viewH + slot * 2.0F; i++) {
         String[] rows = this.rowsFor(i, font, px, innerW);
         int count = Math.max(1, rows.length);
         float lineTop = restTop + this.lyricOffset(i) + rise;
         float depth = 1.0F - 0.39999998F * clamp01(lineTop / slot - 1.0F);
         float highlight = this.lyricGlow(i, mode);
         float progress = rows.length == 0 ? this.breakProgress(synced, i, position) : 1.0F;

         for (int r = 0; r < count; r++) {
            float rowRest = restTop + r * (px + rowGap);
            float shown = this.updateShown(i, r, rowRest + px <= viewH + 0.5F, dt);
            float top = lineTop + r * (px + rowGap);
            float rowAlpha = alpha * depth * smooth(shown) * topFade(top, s) * bottomFade(top + px - viewH, px);
            if (!(rowAlpha <= 0.004F)) {
               float fill = karaoke && rows.length > 0 ? this.rowFill(synced, i, rows, r, font, px, position) : 0.0F;
               items.add(new HudRenderer.LyricItem(rows.length == 0 ? null : rows[r], top, highlight, rowAlpha, progress, fill));
            }
         }

         restTop += lineHeight(rows, px, rowGap) + lineGap;
      }

      float lineRest = 0.0F;

      for (int i = from - 1; i >= 0; i--) {
         String[] rows = this.rowsFor(i, font, px, innerW);
         int count = Math.max(1, rows.length);
         lineRest -= lineHeight(rows, px, rowGap) + lineGap;
         float lineTop = lineRest + this.lyricOffset(i) + rise;
         if (topFade(lineTop + (count - 1) * (px + rowGap), s) <= 0.0F) {
            break;
         }

         float highlight = this.lyricGlow(i, mode);
         float progress = rows.length == 0 ? this.breakProgress(synced, i, position) : 1.0F;

         for (int rx = 0; rx < count; rx++) {
            float top = lineTop + rx * (px + rowGap);
            float rowAlpha = alpha * smooth(this.lyricShown[i * 2 + rx]) * topFade(top, s);
            if (!(rowAlpha <= 0.004F)) {
               float fill = karaoke && rows.length > 0 ? this.rowFill(synced, i, rows, rx, font, px, position) : 0.0F;
               items.add(new HudRenderer.LyricItem(rows.length == 0 ? null : rows[rx], top, highlight, rowAlpha, progress, fill));
            }
         }
      }
   }

   private float lyricOffset(int index) {
      float offset = 0.0F;

      for (int k = 0; k < this.lyricShifts.size(); k++) {
         HudRenderer.LyricShift shift = this.lyricShifts.get(k);
         float delay = Math.max(0, Math.min(6, index - shift.start())) * shift.stagger();
         float time = this.lyricClock - shift.at() - delay;
         offset += time <= 0.0F ? shift.distance() : shift.distance() * springLeft(time);
      }

      return offset;
   }

   private static float springLeft(float time) {
      float wt = 13.0F * time;
      return (1.0F + wt) * (float)Math.exp(-wt);
   }

   private static float topFade(float top, float s) {
      return top >= 0.0F ? 1.0F : smooth(1.0F + top / (12.0F * s * 0.75F));
   }

   private static float bottomFade(float below, int px) {
      return below <= 0.5F ? 1.0F : smooth(1.0F - below / (px * 0.6F));
   }

   private float updateShown(int index, int row, boolean fits, float dt) {
      int k = index * 2 + row;
      float step = dt / 0.3F;
      float shown = fits ? Math.min(1.0F, this.lyricShown[k] + step) : Math.max(0.0F, this.lyricShown[k] - step);
      this.lyricShown[k] = shown;
      return shown;
   }

   private void showView(SyncedLyrics synced, int from, float viewH, HudRenderer.Type t, float innerW, float s) {
      Arrays.fill(this.lyricShown, 0.0F);
      int px = t.textPx();
      float rowGap = 6.0F * s;
      float restTop = 0.0F;

      for (int i = from; i < synced.size() && restTop < viewH; i++) {
         String[] rows = this.rowsFor(i, t.text(), px, innerW);
         int r = 0;

         for (int count = Math.max(1, rows.length); r < count; r++) {
            if (restTop + r * (px + rowGap) + px <= viewH + 0.5F) {
               this.lyricShown[i * 2 + r] = 1.0F;
            }
         }

         restTop += lineHeight(rows, px, rowGap) + 6.0F * s;
      }
   }

   private float restDistance(int from, int to, HudRenderer.Type t, float innerW, float s) {
      float rowGap = 6.0F * s;
      float distance = 0.0F;

      for (int i = from; i < to; i++) {
         distance += lineHeight(this.rowsFor(i, t.text(), t.textPx(), innerW), t.textPx(), rowGap) + 6.0F * s;
      }

      return distance;
   }

   private void updateBrightness(int currentFirst, int currentLast, float dt) {
      int lo = this.lyricBrightHi >= 0 ? this.lyricBrightLo : Integer.MAX_VALUE;
      int hi = this.lyricBrightHi;
      if (currentLast >= 0) {
         lo = Math.min(lo, Math.max(0, currentFirst));
         hi = Math.max(hi, currentLast);
      }

      float step = dt / 0.35F;
      int lit = Integer.MAX_VALUE;
      int last = -1;

      for (int i = Math.max(0, lo); i <= hi && i < this.lyricBright.length; i++) {
         boolean sung = i >= currentFirst && i <= currentLast;
         float bright = sung ? Math.min(1.0F, this.lyricBright[i] + step) : Math.max(0.0F, this.lyricBright[i] - step);
         this.lyricBright[i] = bright;
         if (bright > 0.0F) {
            lit = Math.min(lit, i);
            last = i;
         }
      }

      this.lyricBrightLo = last < 0 ? 0 : lit;
      this.lyricBrightHi = last;
   }

   private float lyricGlow(int index, SpotifyHudModule.LyricHighlight mode) {
      if (mode != SpotifyHudModule.LyricHighlight.OFF && index >= 0 && index < this.lyricCurrentFirst) {
         return 1.0F;
      } else {
         return index >= 0 && index < this.lyricBright.length ? smooth(this.lyricBright[index]) : 0.0F;
      }
   }

   private float breakProgress(SyncedLyrics synced, int index, long position) {
      if (index >= this.lyricCurrentFirst && index <= this.lyricCurrentLast) {
         int next = synced.lastWithTimeOf(index) + 1;
         if (next >= synced.size()) {
            return 1.0F;
         } else {
            long begin = synced.line(index).timeMs();
            long end = synced.line(next).timeMs() - 200L;
            return end <= begin ? 1.0F : clamp01((float)(position - begin) / (float)(end - begin));
         }
      } else {
         return 1.0F;
      }
   }

   private float rowFill(SyncedLyrics synced, int index, String[] rows, int row, ImFont font, int px, long position) {
      if (index < this.lyricCurrentFirst) {
         return Float.POSITIVE_INFINITY;
      } else if (index > this.lyricCurrentLast) {
         return 0.0F;
      } else {
         String text = rows[row];
         int rowStart = 0;
         if (row > 0) {
            String line = this.lyricTexts[index];
            rowStart = rows[0].length();

            while (rowStart < line.length() && line.charAt(rowStart) == ' ') {
               rowStart++;
            }
         }

         float lit = this.litChars(synced, index, position) - rowStart;
         if (lit <= 0.0F) {
            return 0.0F;
         } else if (lit >= text.length()) {
            return Float.POSITIVE_INFINITY;
         } else {
            int whole = (int)lit;
            if (whole > 0 && Character.isLowSurrogate(text.charAt(whole))) {
               whole--;
            }

            int next = whole + Character.charCount(text.codePointAt(whole));
            float x0 = HudChrome.width(font, px, text.substring(0, whole));
            float x1 = HudChrome.width(font, px, text.substring(0, next));
            return x0 + (x1 - x0) * clamp01((lit - whole) / (next - whole));
         }
      }
   }

   private float litChars(SyncedLyrics synced, int index, long position) {
      HudRenderer.LyricWords words = this.lyricWords[index];
      if (words == null) {
         this.timeWords(synced, synced.firstWithTimeOf(index));
         words = this.lyricWords[index];
      }

      float lit = 0.0F;
      long[] start = words.start();

      for (int k = 0; k < start.length && position >= start[k]; k++) {
         float swept = smooth((float)(position - start[k]) / (float)words.sweep()[k]);
         lit = words.from()[k] + (words.to()[k] - words.from()[k]) * swept;
      }

      return lit;
   }

   private void timeWords(SyncedLyrics synced, int first) {
      int last = synced.lastWithTimeOf(first);
      int next = last + 1;
      long begin = synced.line(first).timeMs();
      long handover = next < synced.size() ? synced.line(next).timeMs() - 200L : Long.MAX_VALUE;
      int letters = 0;

      for (int i = first; i <= last; i++) {
         letters += letterCount(this.lyricTexts[i]);
      }

      long window = next < synced.size()
         ? Math.min(handover - begin, (long)Math.round(letters * this.lyricCharMs * 2.0F))
         : Math.round(letters * this.lyricCharMs);
      window = Math.max(0L, window);
      int done = 0;

      for (int i = first; i <= last; i++) {
         String text = this.lyricTexts[i];
         int count = text.isEmpty() ? 0 : text.length() - letterCount(text) + 1;
         int[] from = new int[count];
         int[] to = new int[count];
         long[] start = new long[count];
         long[] sweep = new long[count];
         List<Long> timed = synced.line(i).wordTimes();
         boolean own = timed.size() == count;
         int k = 0;

         for (int c = 0; k < count; k++) {
            from[k] = c;
            c = text.indexOf(32, c);
            if (c < 0) {
               c = text.length();
            }

            to[k] = c++;
            long at = own ? timed.get(k) : begin + window * done / letters;
            done += to[k] - from[k];
            long until = own ? (k + 1 < count ? timed.get(k + 1) : Math.min(handover, at + 180L)) : begin + window * done / letters;
            start[k] = at;
            sweep[k] = Math.max(1L, Math.min(180L, until - at));
         }

         this.lyricWords[i] = new HudRenderer.LyricWords(from, to, start, sweep);
      }
   }

   private float lyricPace(SyncedLyrics synced) {
      float[] paces = new float[synced.size()];
      int n = 0;
      int first = 0;

      while (first < synced.size()) {
         int last = synced.lastWithTimeOf(first);
         int letters = 0;

         for (int i = first; i <= last; i++) {
            letters += letterCount(this.lyricTexts[i]);
         }

         if (letters > 0 && last + 1 < synced.size()) {
            paces[n++] = (float)(synced.line(last + 1).timeMs() - synced.line(first).timeMs()) / letters;
         }

         first = last + 1;
      }

      if (n == 0) {
         return 90.0F;
      } else {
         Arrays.sort(paces, 0, n);
         return paces[n / 2];
      }
   }

   private static int letterCount(String text) {
      int letters = text.length();

      for (int i = 0; i < text.length(); i++) {
         if (text.charAt(i) == ' ') {
            letters--;
         }
      }

      return letters;
   }

   private float lyricViewHeight(SyncedLyrics synced, int lines, HudRenderer.Type t, float innerW, float s) {
      int px = t.textPx();
      this.prepareRows(px, innerW);
      if (this.lyricViewH >= 0.0F && this.lyricViewLines == lines && this.lyricViewScale == s) {
         return this.lyricViewH;
      } else {
         int slots = lines;
         int start = 0;

         while (start < synced.size()) {
            int rows = 0;
            int i = start;

            for (int last = synced.lastWithTimeOf(start); i <= last; i++) {
               rows += Math.max(1, this.rowsFor(i, t.text(), px, innerW).length);
            }

            slots = Math.max(slots, Math.min(4, rows));
            start = synced.lastWithTimeOf(start) + 1;
         }

         this.lyricViewH = slots * px + (slots - 1) * 6.0F * s;
         this.lyricViewLines = lines;
         this.lyricViewScale = s;
         return this.lyricViewH;
      }
   }

   private static float lineHeight(String[] rows, int px, float rowGap) {
      return rows.length <= 1 ? px : rows.length * px + (rows.length - 1) * rowGap;
   }

   private float easeLyricBlock(float target, float dt) {
      float diff = target - this.lyricBlockH;
      if (Math.abs(diff) < 0.5F) {
         this.lyricBlockH = target;
      } else {
         float speed = diff > 0.0F ? 14.0F : 9.0F;
         this.lyricBlockH = this.lyricBlockH + diff * (1.0F - (float)Math.exp(-speed * dt));
      }

      return this.lyricBlockH;
   }

   private void prepareLyrics(Lyrics lyrics, ImFont font) {
      if (lyrics != this.lyricPrepared) {
         this.lyricPrepared = lyrics;
         this.lyricViewH = -1.0F;
         SyncedLyrics synced = lyrics.synced();
         this.lyricTexts = new String[synced.size()];
         this.lyricRows = new String[synced.size()][];
         this.lyricWords = new HudRenderer.LyricWords[synced.size()];
         int letters = 0;
         int missing = 0;
         IntPredicate drawable = cpx -> this.hudHasGlyph(font, cpx);

         for (int i = 0; i < synced.size(); i++) {
            String text = DisplayText.fold(synced.line(i).text(), drawable);
            this.lyricTexts[i] = text;
            int k = 0;

            while (k < text.length()) {
               int cp = text.codePointAt(k);
               k += Character.charCount(cp);
               if (Character.isLetter(cp)) {
                  letters++;
                  if (cp >= 128 && !this.hudHasGlyph(font, cp)) {
                     missing++;
                  }
               }
            }
         }

         this.lyricReadable = missing <= letters * 0.25F;
         this.lyricCharMs = this.lyricPace(synced);
      }
   }

   private boolean hudHasGlyph(ImFont font, int cp) {
      if (cp < 128 || font == null) {
         return true;
      } else if (cp > 65535) {
         return false;
      } else {
         if (font != this.glyphFont) {
            this.glyphFont = font;
            this.glyphs.clear();
         }

         Boolean known = this.glyphs.get(cp);
         if (known == null) {
            if (this.glyphs.size() > 1024) {
               this.glyphs.clear();
            }

            known = font.findGlyphNoFallback(cp).isValidPtr();
            this.glyphs.put(cp, known);
         }

         return known;
      }
   }

   private void prepareRows(int px, float maxW) {
      int widthKey = (int)maxW;
      if (px != this.lyricRowsPx || widthKey != this.lyricRowsWidth) {
         Arrays.fill(this.lyricRows, null);
         this.lyricRowsPx = px;
         this.lyricRowsWidth = widthKey;
         this.lyricViewH = -1.0F;
      }
   }

   private String[] rowsFor(int index, ImFont font, int px, float maxW) {
      this.prepareRows(px, maxW);
      String[] rows = this.lyricRows[index];
      if (rows == null) {
         rows = wrapLyric(font, px, this.lyricTexts[index], this.lyricRowsWidth);
         this.lyricRows[index] = rows;
      }

      return rows;
   }

   private static String[] wrapLyric(ImFont font, int px, String text, float maxW) {
      if (text.isEmpty()) {
         return NO_ROWS;
      } else if (HudChrome.width(font, px, text) <= maxW) {
         return new String[]{text};
      } else {
         int best = -1;
         float bestW = Float.MAX_VALUE;

         for (int i = text.indexOf(32); i > 0; i = text.indexOf(32, i + 1)) {
            float firstW = HudChrome.width(font, px, text.substring(0, i).stripTrailing());
            if (firstW > maxW) {
               break;
            }

            float secondW = HudChrome.width(font, px, text.substring(i + 1).stripLeading());
            if (secondW <= maxW && Math.max(firstW, secondW) < bestW) {
               bestW = Math.max(firstW, secondW);
               best = i;
            }
         }

         if (best > 0) {
            return new String[]{text.substring(0, best).stripTrailing(), text.substring(best + 1).stripLeading()};
         } else {
            int cut = fitPrefix(font, px, text, maxW);
            String first = text.substring(0, cut).stripTrailing();
            String rest = text.substring(cut).stripLeading();
            return rest.isEmpty() ? new String[]{first} : new String[]{first, HudChrome.width(font, px, rest) <= maxW ? rest : ellipsize(font, px, rest, maxW)};
         }
      }
   }

   private static int fitPrefix(ImFont font, int px, String text, float maxW) {
      int lastBreak = -1;

      for (int i = text.indexOf(32); i > 0 && !(HudChrome.width(font, px, text.substring(0, i)) > maxW); i = text.indexOf(32, i + 1)) {
         lastBreak = i;
      }

      if (lastBreak > 0) {
         return lastBreak + 1;
      } else {
         int lo = 1;
         int hi = text.length();

         while (lo < hi) {
            int mid = lo + hi + 1 >>> 1;
            if (HudChrome.width(font, px, text.substring(0, mid)) <= maxW) {
               lo = mid;
            } else {
               hi = mid - 1;
            }
         }

         if (lo > 1 && lo < text.length() && Character.isHighSurrogate(text.charAt(lo - 1))) {
            lo--;
         }

         return lo;
      }
   }

   private void drawLyrics(
      ImDrawList dl,
      HudRenderer.Type t,
      HudRenderer.LyricView view,
      HudRenderer.SpotifyColors colors,
      float left,
      float top,
      float width,
      float bottom,
      float s,
      float a,
      boolean guard
   ) {
      float clipTop = top - 12.0F * s + Math.max(1.0F, 2.0F * s);
      if (!(bottom <= clipTop) && !(a <= 0.001F)) {
         float clipLeft = (float)Math.floor(left - 2.0F);
         float clipRight = (float)Math.ceil(left + width + 2.0F);
         dl.pushClipRect(clipLeft, clipTop, clipRight, bottom, true);
         if (view.status() != null) {
            float statusTop = Math.round(top + (t.textPx() - t.labelPx()) * 0.5F);
            HudChrome.text(dl, t.label(), t.labelPx(), left, statusTop, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a * 0.85F), view.status(), guard);
         }

         for (HudRenderer.LyricItem item : view.items()) {
            float alpha = a * item.alpha();
            if (!(alpha <= 0.004F)) {
               float rowTop = top + item.top();
               if (item.text() == null) {
                  float centerY = rowTop + (this.capTop(t.text(), t.textPx()) + HudChrome.ascent(t.text(), t.textPx())) * 0.5F;
                  this.drawLyricBreak(dl, colors, left, centerY, s, item.highlight(), item.progress(), alpha);
               } else {
                  int color = HudChrome.scaleAlpha(SpectreTheme.lerpColor(colors.upcoming(), colors.lyric(), item.highlight()), alpha);
                  int lit = HudChrome.scaleAlpha(colors.lit(), alpha);
                  float edge = Math.min(clipRight, (float)Math.floor(left) + Math.round(item.fill()));
                  if (item.fill() <= 0.0F) {
                     HudChrome.text(dl, t.text(), t.textPx(), left, Math.round(rowTop), color, item.text(), guard);
                  } else if (edge >= clipRight) {
                     HudChrome.text(dl, t.text(), t.textPx(), left, Math.round(rowTop), lit, item.text(), guard);
                  } else {
                     dl.pushClipRect(clipLeft, clipTop, edge, bottom, true);
                     HudChrome.text(dl, t.text(), t.textPx(), left, Math.round(rowTop), lit, item.text(), guard);
                     dl.popClipRect();
                     dl.pushClipRect(edge, clipTop, clipRight, bottom, true);
                     HudChrome.text(dl, t.text(), t.textPx(), left, Math.round(rowTop), color, item.text(), guard);
                     dl.popClipRect();
                  }
               }
            }
         }

         dl.popClipRect();
      }
   }

   private void drawLyricBreak(
      ImDrawList dl, HudRenderer.SpotifyColors colors, float left, float centerY, float s, float highlight, float progress, float alpha
   ) {
      float r = Math.max(1.0F, 1.6F * s);
      float step = Math.max(4.0F, (float)Math.round(6.0F * s));
      float breath = 0.5F - 0.5F * (float)Math.cos(this.lyricBreath / 1.6F * 2.0 * Math.PI);

      for (int i = 0; i < 3; i++) {
         float lit = smooth(progress * 3.0F - i) * highlight;
         int color = HudChrome.scaleAlpha(SpectreTheme.lerpColor(colors.upcoming(), colors.lit(), lit), alpha);
         float radius = r * (1.0F + 0.3F * lit + 0.12F * breath * highlight);
         dl.addCircleFilled(left + r + i * step, centerY, radius, color, 12);
      }
   }

   private void foldTrack(NowPlaying now, ImFont font) {
      if (now != this.spotifyFolded) {
         this.spotifyFolded = now;
         IntPredicate drawable = cp -> this.hudHasGlyph(font, cp);
         String title = DisplayText.fold(now.title(), drawable);
         this.spotifyTitle = title.isEmpty() ? now.title() : title;
         this.spotifyArtist = DisplayText.fold(now.artist(), drawable);
      }
   }

   private float[] marquee(
      String title, String artist, float titleW, float artistW, float avail, boolean scroll, boolean advancing, float loopGap, float s, float dt
   ) {
      if (!title.equals(this.marqueeTitle) || !artist.equals(this.marqueeArtist)) {
         this.marqueeTitle = title;
         this.marqueeArtist = artist;
         this.marqueeTime = 0.0F;
      }

      float titleOffset = 0.0F;
      float artistOffset = 0.0F;
      float longest = Math.max(titleW > avail ? titleW : 0.0F, artistW > avail ? artistW : 0.0F);
      if (scroll && longest > 0.0F) {
         float speed = 30.0F * s;
         float period = 2.4F + (longest + loopGap) / speed;
         this.marqueeTime %= period;
         if (advancing || this.marqueeTime > 2.4F) {
            this.marqueeTime = (this.marqueeTime + dt) % period;
         }

         float travelled = Math.max(0.0F, this.marqueeTime - 2.4F) * speed;
         if (titleW > avail) {
            titleOffset = Math.min(travelled, titleW + loopGap);
         }

         if (artistW > avail) {
            artistOffset = Math.min(travelled, artistW + loopGap);
         }
      } else {
         this.marqueeTime = 0.0F;
      }

      return new float[]{titleOffset, artistOffset};
   }

   private static void marqueeLine(
      ImDrawList dl,
      ImFont font,
      int px,
      float x,
      float top,
      float avail,
      float textW,
      float offset,
      float loopGap,
      boolean scroll,
      String text,
      int color,
      boolean guard
   ) {
      if (textW <= avail + 0.5F) {
         HudChrome.text(dl, font, px, x, top, color, text, guard);
      } else if (!scroll) {
         HudChrome.text(dl, font, px, x, top, color, ellipsize(font, px, text, avail), guard);
      } else {
         dl.pushClipRect(x, top - 2.0F, x + avail, top + px + 3.0F, true);
         HudChrome.text(dl, font, px, x - offset, top, color, text, guard);
         if (offset > 0.0F) {
            HudChrome.text(dl, font, px, x - offset + textW + loopGap, top, color, text, guard);
         }

         dl.popClipRect();
      }
   }

   private static String ellipsize(ImFont font, int px, String text, float maxW) {
      float room = maxW - HudChrome.width(font, px, "...");
      int lo = 0;
      int hi = text.length();

      while (lo < hi) {
         int mid = lo + hi + 1 >>> 1;
         if (HudChrome.width(font, px, text.substring(0, mid)) <= room) {
            lo = mid;
         } else {
            hi = mid - 1;
         }
      }

      return text.substring(0, lo).stripTrailing() + "...";
   }

   private static String formatSeconds(long totalSeconds) {
      long seconds = Math.max(0L, totalSeconds);
      long hours = seconds / 3600L;
      long minutes = seconds / 60L % 60L;
      StringBuilder out = new StringBuilder(8);
      if (hours > 0L) {
         out.append(hours).append(':').append(minutes < 10L ? "0" : "");
      }

      out.append(minutes).append(':');
      if (seconds % 60L < 10L) {
         out.append('0');
      }

      return out.append(seconds % 60L).toString();
   }

   private float capTop(ImFont font, int px) {
      if (font != null && !(font.getFontSize() <= 0.0F)) {
         long key = (long)System.identityHashCode(font) << 16 ^ px;
         Float cached = this.capTops.get(key);
         if (cached != null) {
            return cached;
         } else {
            ImFontGlyph glyph = font.findGlyphNoFallback(72);
            float top = glyph.isValidPtr() ? glyph.getY0() * px / font.getFontSize() : px * 0.2F;
            float value = Math.max(0.0F, (float)Math.round(top));
            if (this.capTops.size() > 64) {
               this.capTops.clear();
            }

            this.capTops.put(key, value);
            return value;
         }
      } else {
         return Math.round(px * 0.2F);
      }
   }

   private ThemeImages.Texture spotifyArtTexture(AlbumArt art) {
      if (art == this.spotifyArt) {
         return this.spotifyArtTexture;
      } else {
         this.releaseSpotifyArt();
         if (art == null) {
            return null;
         } else {
            this.spotifyArt = art;
            ByteBuffer pixels = MemoryUtil.memAlloc(art.byteSize());

            try {
               art.copyTo(pixels);
               pixels.flip();
               this.spotifyArtTexture = ThemeImages.fromPixels(pixels, art.size(), art.size(), false);
            } catch (RuntimeException var7) {
               this.spotifyArtTexture = null;
            } finally {
               MemoryUtil.memFree(pixels);
            }

            return this.spotifyArtTexture;
         }
      }
   }

   private void releaseSpotifyArt() {
      if (this.spotifyArtTexture != null) {
         GL11.glDeleteTextures(this.spotifyArtTexture.id());
      }

      this.spotifyArtTexture = null;
      this.spotifyArt = null;
   }

   private List<HudRenderer.PotionRow> buildPotionRows(Minecraft client) {
      if (client.player == null) {
         return List.of();
      } else {
         List<HudRenderer.PotionRow> rows = new ArrayList<>();

         for (MobEffectInstance instance : client.player.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            String name = I18n.get(effect.value().getDescriptionId());
            int amp = instance.getAmplifier();
            String amplifier = amp > 0 ? toRomanNumeral(amp + 1) : "";
            int ticks = instance.getDuration();
            if (ticks >= 0 && ticks != Integer.MAX_VALUE && !instance.isInfiniteDuration()) {
               int seconds = Math.max(0, ticks / 20);
               String formatted = seconds / 60 + ":" + String.format("%02d", seconds % 60);
               rows.add(new HudRenderer.PotionRow(effect, name, amplifier, formatted, false, seconds < 30, ticks));
            } else {
               rows.add(new HudRenderer.PotionRow(effect, name, amplifier, "", true, false, Integer.MAX_VALUE));
            }
         }

         return rows;
      }
   }

   private static String toRomanNumeral(int n) {
      return switch (n) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         case 6 -> "VI";
         case 7 -> "VII";
         case 8 -> "VIII";
         case 9 -> "IX";
         case 10 -> "X";
         default -> String.valueOf(n);
      };
   }

   private int hudAccent() {
      return this.hudModule != null && this.hudModule.customAccent() ? GuiPalette.abgr(this.hudModule.accentArgb()) : GuiPalette.accent();
   }

   static Surface hudSurface(HudLayoutStore.HudElementState state) {
      return HudTheme.get().forElementId(state.id);
   }

   static float hudRounding() {
      return HudTheme.get().rounding.get();
   }

   private void updateEditorInput(float screenW, float screenH) {
      float mouseX = ImGui.getMousePosX();
      float mouseY = ImGui.getMousePosY();
      if (this.draggingId != null) {
         if (ImGui.isMouseDown(0)) {
            HudLayoutStore.HudElementState dragging = this.layout.element(this.draggingId);
            float rawX = mouseX - this.dragOffsetX;
            float rawY = mouseY - this.dragOffsetY;
            HudRenderer.Snap xSnap = bestSnap(rawX, dragging.width, this.snapAnchorsX(this.draggingId, screenW));
            HudRenderer.Snap ySnap = bestSnap(rawY, dragging.height, this.snapAnchorsY(this.draggingId, screenH));
            this.snapGuideX = xSnap != null ? xSnap.guideAt() : null;
            this.snapGuideY = ySnap != null ? ySnap.guideAt() : null;
            this.layout.moveTo(this.draggingId, xSnap != null ? xSnap.position() : rawX, ySnap != null ? ySnap.position() : rawY, screenW, screenH);
         } else {
            this.draggingId = null;
            this.snapGuideX = null;
            this.snapGuideY = null;
            this.saveLayout();
         }
      } else if (!ImGui.isAnyItemHovered()) {
         float wheel = ImGui.getIO().getMouseWheel();
         if (wheel != 0.0F) {
            List<HudLayoutStore.HudElementState> states = this.layout.orderedElements();

            for (int i = states.size() - 1; i >= 0; i--) {
               HudLayoutStore.HudElementState s = states.get(i);
               if (contains(s, mouseX, mouseY)) {
                  float next = Math.max(0.5F, Math.min(3.0F, s.scale + wheel * 0.05F));
                  if (next != s.scale) {
                     s.scale = next;
                     this.layoutDirty = true;
                     this.lastWheelNanos = System.nanoTime();
                  }
                  break;
               }
            }
         }

         if (ImGui.isMouseClicked(0)) {
            List<HudLayoutStore.HudElementState> states = this.layout.orderedElements();

            for (int ix = states.size() - 1; ix >= 0; ix--) {
               HudLayoutStore.HudElementState s = states.get(ix);
               if (contains(s, mouseX, mouseY)) {
                  this.draggingId = s.id;
                  this.dragOffsetX = mouseX - s.x;
                  this.dragOffsetY = mouseY - s.y;
                  break;
               }
            }
         }
      }
   }

   private List<Float> snapAnchorsX(String excludeId, float screenW) {
      List<Float> anchors = new ArrayList<>(List.of(0.0F, screenW, screenW * 0.5F));

      for (HudLayoutStore.HudElementState s : this.layout.orderedElements()) {
         if (!s.id.equals(excludeId) && s.positioned) {
            anchors.add(s.x);
            anchors.add(s.x + s.width);
            anchors.add(s.x + s.width * 0.5F);
         }
      }

      return anchors;
   }

   private List<Float> snapAnchorsY(String excludeId, float screenH) {
      List<Float> anchors = new ArrayList<>(List.of(0.0F, screenH, screenH * 0.5F));

      for (HudLayoutStore.HudElementState s : this.layout.orderedElements()) {
         if (!s.id.equals(excludeId) && s.positioned) {
            anchors.add(s.y);
            anchors.add(s.y + s.height);
            anchors.add(s.y + s.height * 0.5F);
         }
      }

      return anchors;
   }

   private static HudRenderer.Snap bestSnap(float raw, float size, List<Float> anchors) {
      float[] offsets = new float[]{0.0F, size * 0.5F, size};
      float bestDelta = 6.0F;
      HudRenderer.Snap best = null;

      for (float anchor : anchors) {
         for (float offset : offsets) {
            float delta = Math.abs(raw + offset - anchor);
            if (delta < bestDelta) {
               bestDelta = delta;
               best = new HudRenderer.Snap(anchor - offset, anchor);
            }
         }
      }

      return best;
   }

   private void drawSnapGuides(ImDrawList dl, float screenW, float screenH) {
      if (this.draggingId != null) {
         int guideColor = HudChrome.scaleAlpha(this.hudAccent(), 0.8F);
         if (this.snapGuideX != null) {
            dl.addLine(this.snapGuideX, 0.0F, this.snapGuideX, screenH, guideColor, 1.0F);
         }

         if (this.snapGuideY != null) {
            dl.addLine(0.0F, this.snapGuideY, screenW, this.snapGuideY, guideColor, 1.0F);
         }
      }
   }

   private void drawEditorChrome(ImDrawList dl, float screenW, float screenH) {
      float mouseX = ImGui.getMousePosX();
      float mouseY = ImGui.getMousePosY();
      float u = ImGuiManager.getInstance().uiScale();
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      int size = HudChrome.px(14.0F, u);
      this.drawSnapGuides(dl, screenW, screenH);
      float e = Math.round(Math.max(2.0F, 3.0F * u));
      List<HudLayoutStore.HudElementState> states = this.layout.orderedElements();
      List<float[]> placed = new ArrayList<>(states.size());

      for (HudLayoutStore.HudElementState state : states) {
         boolean active = state.id.equals(this.draggingId);
         boolean hovered = contains(state, mouseX, mouseY);
         float emphasis = active ? 1.0F : (hovered ? 0.85F : 0.4F);
         float ox = Math.round(state.x);
         float oy = Math.round(state.y);
         dl.addRect(
            ox - e,
            oy - e,
            ox + state.width + e,
            oy + state.height + e,
            HudChrome.scaleAlpha(this.hudAccent(), emphasis),
            hudRounding() * scaleOf(state) + e,
            0,
            active ? 1.5F : 1.0F
         );
         this.drawEditorLabel(dl, font, size, u, state, active || hovered, states, placed, screenW, screenH);
      }

      drawEditorHint(dl, font, size, u, screenW, screenH);
   }

   private static void drawEditorHint(ImDrawList dl, ImFont font, int size, float u, float screenW, float screenH) {
      String hint = "Drag to move  ·  Scroll to resize  ·  Click the eye to hide";
      float w = Math.round(HudChrome.width(font, size, hint) + 12.0F * u * 2.0F);
      float h = Math.round(28.0F * u);
      float x = Math.round((screenW - w) * 0.5F);
      float y = Math.round(screenH - h - 96.0F * u);
      float r = hudRounding() * u;
      dl.addRectFilled(x, y, x + w, y + h, HudChrome.CHIP_FILL, r);
      dl.addRect(x, y, x + w, y + h, 402653183, r, 0, 1.0F);
      HudChrome.text(dl, font, size, x + 12.0F * u, y + (float)Math.floor((h - size) * 0.5F), HudChrome.TEXT_LABEL, hint, false);
   }

   private void drawEditorLabel(
      ImDrawList dl,
      ImFont font,
      int size,
      float u,
      HudLayoutStore.HudElementState state,
      boolean highlighted,
      List<HudLayoutStore.HudElementState> states,
      List<float[]> placed,
      float screenW,
      float screenH
   ) {
      String label = editorLabel(state.id);
      float a = highlighted ? 1.0F : 0.6F;
      float padX = 8.0F * u;
      float eyeSize = 12.0F * u;
      float chipH = Math.round(20.0F * u);
      float textW = HudChrome.width(font, size, label);
      float chipW = Math.round(padX + textW + 8.0F * u + eyeSize + padX);
      float[] chip = placeChip(state, chipW, chipH, 7.0F * u, states, placed, screenW, screenH);
      placed.add(chip);
      float chipX = chip[0];
      float chipY = chip[1];
      float r = 6.0F * u;
      dl.addRectFilled(chipX, chipY, chipX + chipW, chipY + chipH, HudChrome.scaleAlpha(HudChrome.CHIP_FILL, a), r);
      dl.addRect(chipX, chipY, chipX + chipW, chipY + chipH, HudChrome.scaleAlpha(402653183, a), r, 0, 1.0F);
      int textColor = HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a);
      HudChrome.text(dl, font, size, chipX + padX, chipY + (float)Math.floor((chipH - size) * 0.5F), textColor, label, false);
      float eyeX = chipX + padX + textW + 8.0F * u;
      float eyeY = chipY + (chipH - eyeSize) * 0.5F;
      ImGui.setCursorPos(eyeX, eyeY);
      if (ImGui.invisibleButton("##vis_" + state.id, eyeSize, eyeSize)) {
         state.visible = !state.visible;
         this.saveLayout();
      }

      boolean eyeHovered = ImGui.isItemHovered();
      float ecx = eyeX + eyeSize * 0.5F;
      float ecy = eyeY + eyeSize * 0.5F;
      float er = eyeSize * 0.34F;
      int eyeColor = eyeHovered ? HudChrome.TEXT_VALUE : HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, state.visible ? a : a * 0.6F);
      SpectreWidgets.eye(dl, ecx, ecy, er, eyeColor);
      if (!state.visible) {
         dl.addLine(ecx - er, ecy - er, ecx + er, ecy + er, eyeColor, Math.max(1.0F, 1.4F * u));
      }
   }

   private static float[] placeChip(
      HudLayoutStore.HudElementState state,
      float w,
      float h,
      float gap,
      List<HudLayoutStore.HudElementState> states,
      List<float[]> placed,
      float screenW,
      float screenH
   ) {
      float inset = 4.0F;
      float left = state.x;
      float right = state.x + state.width - w;
      float alignX = alignsRight(state, screenW) ? right : left;
      float midY = state.y + (state.height - h) * 0.5F;
      float[][] candidates = new float[][]{
         {alignX, state.y - gap - h},
         {alignX, state.y + state.height + gap},
         {state.x + state.width + gap, Math.min(midY, state.y)},
         {state.x - gap - w, Math.min(midY, state.y)}
      };

      for (float[] c : candidates) {
         float x = Math.round(c[0]);
         float y = Math.round(c[1]);
         if (!(x < inset) && !(y < inset) && !(x + w > screenW - inset) && !(y + h > screenH - inset) && !overlapsAny(x, y, w, h, state, states, placed, gap)) {
            return new float[]{x, y, w, h};
         }
      }

      float x = Math.round(Math.max(inset, Math.min(alignX, screenW - inset - w)));
      float y = candidates[0][1] >= inset ? candidates[0][1] : candidates[1][1];
      return new float[]{x, Math.round(Math.max(inset, Math.min(y, screenH - inset - h))), w, h};
   }

   private static boolean overlapsAny(
      float x, float y, float w, float h, HudLayoutStore.HudElementState self, List<HudLayoutStore.HudElementState> states, List<float[]> placed, float margin
   ) {
      for (HudLayoutStore.HudElementState other : states) {
         if (other != self
            && other.positioned
            && overlaps(x, y, w, h, other.x - margin * 0.5F, other.y - margin * 0.5F, other.width + margin, other.height + margin)) {
            return true;
         }
      }

      for (float[] chip : placed) {
         if (overlaps(x, y, w, h, chip[0], chip[1], chip[2], chip[3])) {
            return true;
         }
      }

      return false;
   }

   private static boolean overlaps(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
      return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
   }

   private static boolean contains(HudLayoutStore.HudElementState state, float x, float y) {
      return !state.positioned ? false : x >= state.x && x <= state.x + state.width && y >= state.y && y <= state.y + state.height;
   }

   static String editorLabel(String id) {
      return switch (id) {
         case "info_bar" -> "Info Bar";
         case "coordinates" -> "Coordinates";
         case "module_list" -> "Module List";
         case "potion_tracker" -> "Potions";
         case "region_map" -> "Region Map";
         case "anti_vanish" -> "Anti Vanish";
         case "staff_list" -> "Staff List";
         case "spotify" -> "Media";
         case "radar" -> "Radar";
         case "hotkeys" -> "Hotkeys";
         case "target_card" -> "Target HUD";
         case "armor_status" -> "Armor HUD";
         case "item_cooldowns" -> "Cooldown";
         default -> id;
      };
   }

   private static int readTabLatency(Minecraft client, ClientPacketListener connection) {
      PlayerInfo info = connection.getPlayerInfo(connection.getLocalGameProfile().id());
      if (info == null) {
         info = connection.getPlayerInfo(client.player.getUUID());
      }

      if (info == null) {
         info = connection.getPlayerInfoIgnoreCase(connection.getLocalGameProfile().name());
      }

      if (info == null) {
         info = connection.getPlayerInfoIgnoreCase(client.player.getName().getString());
      }

      return info == null ? 0 : Math.max(0, info.getLatency());
   }

   private static int readServerPing(ClientPacketListener connection) {
      ServerData serverData = connection.getServerData();
      return serverData != null && serverData.ping > 0L && serverData.ping <= 60000L ? (int)serverData.ping : 0;
   }

   private static float easeOutCubic(float value) {
      float t = clamp01(value);
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static float smooth(float value) {
      float t = clamp01(value);
      return t * t * (3.0F - 2.0F * t);
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static float elementScale(HudLayoutStore.HudElementState state) {
      return !Float.isNaN(state.scale) && !Float.isInfinite(state.scale) ? Math.max(0.5F, Math.min(3.0F, state.scale)) : 1.0F;
   }

   static float scaleOf(HudLayoutStore.HudElementState state) {
      return elementScale(state) * ImGuiManager.getInstance().uiScale();
   }

   @Environment(EnvType.CLIENT)
   private record CoordPlan(List<HudRenderer.CoordRow> rows, HudRenderer.Type type, float[] slotW, float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record CoordRow(String block, boolean here, boolean enabled, String[] values, float anim) {
   }

   @Environment(EnvType.CLIENT)
   private record DrawTransform(float xOffset, float yOffset, float alpha) {
      private static HudRenderer.DrawTransform identity() {
         return new HudRenderer.DrawTransform(0.0F, 0.0F, 1.0F);
      }

      private HudRenderer.DrawTransform down(float dy) {
         return dy == 0.0F ? this : new HudRenderer.DrawTransform(this.xOffset, this.yOffset + dy, this.alpha);
      }
   }

   @Environment(EnvType.CLIENT)
   private record HotkeyLine(String name, String key, boolean on, float nameW, float keyW) {
   }

   @Environment(EnvType.CLIENT)
   private record HotkeyPlan(List<HudRenderer.HotkeyLine> lines, HudRenderer.Type type, float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record InfoItem(String slot, String value, String dim, String unit) {
   }

   @Environment(EnvType.CLIENT)
   private record InfoPlan(List<HudRenderer.Segment> segments, HudRenderer.Type type, float width, float height, float emblemW) {
   }

   @Environment(EnvType.CLIENT)
   private record ListPlan(List<ModuleListRows.Row> rows, HudRenderer.Type type, float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record LyricItem(String text, float top, float highlight, float alpha, float progress, float fill) {
   }

   @Environment(EnvType.CLIENT)
   private record LyricShift(float distance, float at, int start, float stagger) {
   }

   @Environment(EnvType.CLIENT)
   private record LyricView(List<HudRenderer.LyricItem> items, String status, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record LyricWords(int[] from, int[] to, long[] start, long[] sweep) {
   }

   @Environment(EnvType.CLIENT)
   private record PotionLine(HudRenderer.PotionRow row, float nameW, float ampW, float durationW) {
   }

   @Environment(EnvType.CLIENT)
   private record PotionPlan(List<HudRenderer.PotionLine> lines, HudRenderer.Type type, float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record PotionRow(Holder<MobEffect> effect, String name, String amplifier, String duration, boolean infinite, boolean expiringSoon, int ticks) {
   }

   @Environment(EnvType.CLIENT)
   private record Segment(HudRenderer.InfoItem item, float valueW, float dimW, float contentW, float slotW) {
   }

   @Environment(EnvType.CLIENT)
   private record Snap(float position, float guideAt) {
   }

   @Environment(EnvType.CLIENT)
   private record SpotifyCard(
      NowPlaying track,
      boolean sample,
      boolean art,
      float header,
      float textTop,
      float column,
      boolean timeline,
      String elapsed,
      String remaining,
      float progress,
      boolean progressBar,
      HudRenderer.LyricView lyrics,
      float lyricAlpha
   ) {
   }

   @Environment(EnvType.CLIENT)
   private record SpotifyColors(int title, int artist, int lyric, int upcoming, int lit) {
      static final HudRenderer.SpotifyColors DEFAULT = new HudRenderer.SpotifyColors(
         HudChrome.TEXT_NAME, HudChrome.TEXT_LABEL, HudChrome.TEXT_VALUE, HudChrome.TEXT_LABEL, HudChrome.TEXT_VALUE
      );

      static HudRenderer.SpotifyColors of(SpotifyHudModule module) {
         if (module == null) {
            return DEFAULT;
         } else {
            SpotifyHudModule.LyricHighlight mode = module.lyricHighlight();
            int lyric = GuiPalette.abgr(module.lyricsColor());
            int highlight = GuiPalette.abgr(module.highlightColor());
            return new HudRenderer.SpotifyColors(
               GuiPalette.abgr(module.titleColor()),
               GuiPalette.abgr(module.artistColor()),
               mode == SpotifyHudModule.LyricHighlight.LINE ? highlight : lyric,
               GuiPalette.abgr(module.upcomingLyricsColor()),
               mode == SpotifyHudModule.LyricHighlight.OFF ? lyric : highlight
            );
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum SpotifyGlyph {
      NONE,
      PLAYING,
      PAUSED;
   }

   @Environment(EnvType.CLIENT)
   private static enum SpotifyLook {
      HIDDEN,
      IDLE,
      COMPACT,
      CARD;
   }

   @Environment(EnvType.CLIENT)
   private record SpotifyPlan(
      HudRenderer.Type type,
      HudRenderer.SpotifyLook look,
      HudRenderer.SpotifyGlyph glyph,
      String title,
      String artist,
      float titleW,
      float artistW,
      float titleOffset,
      float artistOffset,
      float loopGap,
      boolean scroll,
      HudRenderer.SpotifyCard card,
      HudRenderer.SpotifyColors colors,
      float width,
      float height
   ) {
   }

   @Environment(EnvType.CLIENT)
   private record StaffLine(
      String name,
      int face,
      String state,
      boolean more,
      float nameW,
      float stateW,
      boolean server,
      boolean nearby,
      float alert,
      String note,
      float noteW,
      String value,
      float valueW,
      float serverTagW
   ) {
      static HudRenderer.StaffLine plain(String name, int face, String state, boolean more, float nameW, float stateW) {
         return new HudRenderer.StaffLine(name, face, state, more, nameW, stateW, false, false, 0.0F, "", 0.0F, "", 0.0F, 0.0F);
      }
   }

   @Environment(EnvType.CLIENT)
   private record StaffPlan(List<HudRenderer.StaffLine> lines, HudRenderer.Type type, String count, float width, float height) {
   }

   @Environment(EnvType.CLIENT)
   private record Step(int item, float l, float t, float r, float b, float alpha) {
   }

   @Environment(EnvType.CLIENT)
   private record Type(ImFont value, int valuePx, ImFont text, int textPx, ImFont label, int labelPx) {
      static HudRenderer.Type of(float scale) {
         ImGuiManager m = ImGuiManager.getInstance();
         return new HudRenderer.Type(
            m.hudValueFont(), HudChrome.px(18.0F, scale), m.hudTextFont(), HudChrome.px(17.0F, scale), m.hudLabelFont(), HudChrome.px(14.0F, scale)
         );
      }
   }

   @Environment(EnvType.CLIENT)
   private record VanishLine(String tag, String value, boolean urgent, float tagW, float valueW) {
   }

   @Environment(EnvType.CLIENT)
   private record VanishPlan(List<HudRenderer.VanishLine> lines, HudRenderer.Type type, float tagColumn, float width, float height) {
   }
}
