package net.spectre.client.module.impl.render;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.NameProtectModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.Setting;
import net.spectre.client.util.Ballistics;
import net.spectre.client.util.combat.CombatTarget;
import net.spectre.client.util.glyph.GlyphCanvas;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class TrajectoriesModule extends ClientModule implements HudOverlayProvider {
   private static final String PATH_BEADS = "Beads";
   private static final String PATH_DASHES = "Dashes";
   private static final String PATH_SOLID = "Solid";
   private static final String COLOR_THEME = "Theme";
   private static final String COLOR_MANUAL = "Manual";
   private static final String MARKER_ANKH = "Ankh";
   private static final String MARKER_SUN = "Sun Disk";
   private static final String MARKER_NONE = "None";
   private static final int WHITE = -1;
   private static final float HEAD_LIFT = 0.35F;
   private static final float SHEEN_LIFT = 0.8F;
   private static final float PLATE_LIFT = 0.55F;
   private static final float HIT_LIFT = 0.5F;
   private static final int VOLLEY_CAP = 3;
   private static final int TRACK_CAP = 24;
   private static final int TRACK_LIFETIME = 200;
   private static final double RESTING_SPEED_SQR = 1.0E-4;
   private static final double AIM_DRIFT = 0.001;
   private static final double AIM_SWAY = 5.0E-4;
   private static final float BOW_MIN_PULL = 0.1F;
   private static final float BOW_SPEED = 3.0F;
   private static final int TRIDENT_WINDUP = 10;
   private static final float CROSSBOW_ARROW_SPEED = 3.15F;
   private static final float CROSSBOW_ROCKET_SPEED = 1.6F;
   private static final float MULTISHOT_FAN = 10.0F;
   private static final float BOTTLE_SPEED = 0.7F;
   private static final float LOB_TILT = -20.0F;
   private static final double MUZZLE_DROP = 0.1;
   private static final double ROCKET_DROP = 0.15;
   private static final int ROCKET_FUSE_SLACK = 6;
   private static final double BEAD_PERIOD = 0.42;
   private static final double BEAD_ANGLE = 0.011;
   private static final double BEAD_MIN = 0.035;
   private static final double BEAD_MAX = 0.2;
   private static final double DASH_PERIOD = 0.72;
   private static final double DASH_LENGTH = 0.44;
   private static final double SOLID_PIECE = 0.6;
   private static final double FLOW_SPEED = 1.3;
   private static final double FADE_IN = 1.4;
   private static final double SHEEN_SPEED = 9.0;
   private static final double SHEEN_REACH = 1.6;
   private static final double SHEEN_REST = 14.0;
   private static final float HALO_ALPHA = 0.22F;
   private static final float HALO_WIDTH = 2.8F;
   private static final double HALO_NEAR = 24.0;
   private static final double HALO_FAR = 40.0;
   private static final int AIM_PIECES = 200;
   private static final int TRACK_PIECES = 110;
   private static final float GHOST_ALPHA = 0.38F;
   private static final float STRAY_ALPHA = 0.7F;
   private static final float FLYING_ALPHA = 0.85F;
   private static final float UNFURL_SECONDS = 0.32F;
   private static final double MARKER_UNIT = 0.5;
   private static final double MARKER_LIFT = 0.025;
   private static final int RIM_STEPS = 24;
   private static final int ORBIT_ARCS = 10;
   private static final int SUN_RAYS = 12;
   private static final long TARGET_MEMORY_MS = 6000L;
   private static final long LABEL_TTL_NANOS = 250000000L;
   private static final long EPOCH = System.nanoTime();
   private static final String PEARL_PREDICT_ID = "predict";
   private static final String PEARL_OTHERS_ID = "others-pearls";
   private static final double[] RIM_COS = new double[25];
   private static final double[] RIM_SIN = new double[25];
   private static final double DIAGONAL = 1.0 / Math.sqrt(3.0);
   private static final double[][] BURST_RAYS = new double[][]{
      {1.0, 0.0, 0.0},
      {-1.0, 0.0, 0.0},
      {0.0, 1.0, 0.0},
      {0.0, -1.0, 0.0},
      {0.0, 0.0, 1.0},
      {0.0, 0.0, -1.0},
      {DIAGONAL, DIAGONAL, DIAGONAL},
      {-DIAGONAL, DIAGONAL, DIAGONAL},
      {DIAGONAL, -DIAGONAL, DIAGONAL},
      {DIAGONAL, DIAGONAL, -DIAGONAL},
      {-DIAGONAL, -DIAGONAL, DIAGONAL},
      {-DIAGONAL, DIAGONAL, -DIAGONAL},
      {DIAGONAL, -DIAGONAL, -DIAGONAL},
      {-DIAGONAL, -DIAGONAL, -DIAGONAL}
   };
   private final BooleanSetting aimPreview = this.addSetting("aim-preview", new BooleanSetting("Aim Preview", true));
   private final BooleanSetting ghostArc = this.addSetting("ghost-arc", new BooleanSetting("Ghost Arc", true));
   private final BooleanSetting inFlight = this.addSetting("in-flight", new BooleanSetting("In Flight", true));
   private final BooleanSetting othersShots = this.addSetting("others-shots", new BooleanSetting("Others' Shots", true));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 64.0F, 16.0F, 160.0F).step(8.0F));
   private final BooleanSetting bows = this.addSetting("bows", new BooleanSetting("Bows & Crossbows", true));
   private final BooleanSetting tridents = this.addSetting("tridents", new BooleanSetting("Tridents", true));
   private final BooleanSetting throwables = this.addSetting("throwables", new BooleanSetting("Throwables", true));
   private final BooleanSetting pearls = this.addSetting("pearls", new BooleanSetting("Ender Pearls", true));
   private final ModeSetting pathStyle = this.addSetting("path-style", new ModeSetting("Path", "Beads", "Beads", "Dashes", "Solid"));
   private final BooleanSetting flow = this.addSetting("flow", new BooleanSetting("Flow", true));
   private final ModeSetting colorMode = this.addSetting("color", new ModeSetting("Color", "Theme", "Theme", "Manual"));
   private final ColorSetting pathColor = this.addSetting("path-color", new ColorSetting("Path Color", -13338378));
   private final NumberSetting thickness = this.addSetting("thickness", new NumberSetting("Thickness", 2.25F, 0.5F, 5.0F).step(0.25F));
   private final BooleanSetting halo = this.addSetting("halo", new BooleanSetting("Halo", true));
   private final BooleanSetting throughWalls = this.addSetting("through-walls", new BooleanSetting("Through Walls", true));
   private final ModeSetting marker = this.addSetting("marker", new ModeSetting("Marker", "Ankh", "Ankh", "Sun Disk", "None"));
   private final NumberSetting markerScale = this.addSetting("marker-scale", new NumberSetting("Marker Scale", 1.0F, 0.5F, 2.0F).step(0.1F));
   private final BooleanSetting targetBox = this.addSetting("target-box", new BooleanSetting("Target Box", true));
   private final ColorSetting hitColor = this.addSetting("hit-color", new ColorSetting("Hit Color", -6635270));
   private final BooleanSetting flightTime = this.addSetting("flight-time", new BooleanSetting("Flight Time", true));
   private final TrajectoriesModule.Aim[] volley = new TrajectoriesModule.Aim[3];
   private int volleySize;
   private boolean volleyGhost;
   private Item aimItem;
   private long aimSince = -1L;
   private long markerSince = -1L;
   private final Int2ObjectOpenHashMap<TrajectoriesModule.Track> tracks = new Int2ObjectOpenHashMap();
   private final ArrayDeque<TrajectoriesModule.Track> spareTracks = new ArrayDeque<>();
   private int sweep;
   private ClientLevel level;
   private final GlyphCanvas canvas = new GlyphCanvas();
   private final List<TrajectoriesModule.Label> labels = new ArrayList<>();
   private long labelsAt;
   private int headTint;
   private int tailTint;
   private int haloTint;
   private int markTint;
   private int ringTint;
   private int sheenTint;
   private int plateTint;
   private int hitTint;
   private boolean onTop;
   private Vec3 camera = Vec3.ZERO;
   private double strokeTotal;
   private double strokeSheen;
   private float strokeAlpha;
   private float strokeWidth;
   private boolean strokeFade;
   private boolean strokeHalo;
   private double gx;
   private double gy;
   private double gz;
   private double rx;
   private double ry;
   private double rz;
   private double ux;
   private double uy;
   private double uz;

   public TrajectoriesModule() {
      super("trajectories", "Trajectories", "Shows where your projectiles will land.", ModuleCategory.RENDER);

      for (int i = 0; i < 3; i++) {
         this.volley[i] = new TrajectoriesModule.Aim();
      }

      this.ghostArc.visibleWhen(this.aimPreview::get);
      this.othersShots.visibleWhen(this.inFlight::get);
      this.range.visibleWhen(this.inFlight::get);
      this.flow.visibleWhen(() -> !this.pathStyle.is("Solid"));
      this.pathColor.visibleWhen(() -> this.colorMode.is("Manual"));
      this.markerScale.visibleWhen(() -> !this.marker.is("None"));
      this.hitColor.visibleWhen(() -> this.targetBox.get() && this.colorMode.is("Manual"));
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      this.on(Render3DEvent.TYPE, this::onRender);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.forgetAim();
      this.dropTracks();
      this.labels.clear();
      this.level = null;
   }

   private void syncLevel(ClientLevel world) {
      if (world != this.level) {
         this.reset();
         this.level = world;
      }
   }

   private void forgetAim() {
      for (TrajectoriesModule.Aim aim : this.volley) {
         aim.forget();
      }

      this.volleySize = 0;
      this.volleyGhost = false;
      this.aimItem = null;
      this.aimSince = -1L;
      this.markerSince = -1L;
   }

   private void dropTracks() {
      if (!this.tracks.isEmpty()) {
         ObjectIterator var1 = this.tracks.values().iterator();

         while (var1.hasNext()) {
            TrajectoriesModule.Track track = (TrajectoriesModule.Track)var1.next();
            this.recycle(track);
         }

         this.tracks.clear();
      }
   }

   private void recycle(TrajectoriesModule.Track track) {
      track.shot = null;
      track.departed = false;
      track.forget();
      if (this.spareTracks.size() < 24) {
         this.spareTracks.push(track);
      }
   }

   private void onTick(TickEvent event) {
      this.sweep++;
      Minecraft mc = Minecraft.getInstance();
      ClientLevel world = mc.level;
      LocalPlayer player = mc.player;
      this.syncLevel(world);
      if (world != null && player != null && this.inFlight.get()) {
         double reach = this.range.get() * this.range.get();
         PearlTrajectoryModule pearlModule = activePearlModule();
         boolean otherPearlsTaken = pearlModule != null && pearlToggle(pearlModule, "others-pearls");
         boolean others = this.othersShots.get();
         boolean captions = this.flightTime.get();
         Vec3 eye = player.getEyePosition();
         int budget = 24;

         for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof Projectile shot && !shot.isRemoved()) {
               Entity owner = shot.getOwner();
               boolean own = owner == player;
               if (own || others) {
                  boolean pearlTaken = pearlModule != null && (owner == null || own || otherPearlsTaken);
                  Ballistics.Body body = this.bodyOf(shot, pearlTaken);
                  if (body != null && !(shot.distanceToSqr(player) > reach)) {
                     Vec3 motion = shot.getDeltaMovement();
                     if (!(motion.lengthSqr() < 1.0E-4) && !resting(shot)) {
                        if (budget-- == 0) {
                           break;
                        }

                        TrajectoriesModule.Track track = (TrajectoriesModule.Track)this.tracks.get(shot.getId());
                        if (track == null) {
                           track = this.spareTracks.isEmpty() ? new TrajectoriesModule.Track() : this.spareTracks.pop();
                           this.tracks.put(shot.getId(), track);
                        }

                        track.shot = shot;
                        track.seen = this.sweep;
                        track.flight
                           .launch(body, shot.position(), motion)
                           .bounds(shot.getBbWidth(), shot.getBbHeight())
                           .gravity(shot.isNoGravity() ? 0.0 : body.gravity())
                           .wet(shot.isInWater())
                           .age(shot.tickCount)
                           .lifetime(200)
                           .ignoring(owner, shot)
                           .leftShooter(track.departed)
                           .solve(world);
                        track.departed = track.flight.hasLeftShooter();
                        track.caption(eye, captions && (own || track.flight.outcome() == Ballistics.Outcome.ENTITY));
                     }
                  }
               }
            }
         }

         if (!this.tracks.isEmpty()) {
            this.tracks.values().removeIf(this::stale);
         }
      } else {
         this.dropTracks();
      }
   }

   private boolean stale(TrajectoriesModule.Track track) {
      if (track.seen == this.sweep) {
         return false;
      } else {
         this.recycle(track);
         return true;
      }
   }

   private static boolean resting(Projectile shot) {
      double dx = shot.getX() - shot.xo;
      double dy = shot.getY() - shot.yo;
      double dz = shot.getZ() - shot.zo;
      return shot.tickCount > 1 && dx * dx + dy * dy + dz * dz < 1.0E-4;
   }

   private Ballistics.Body bodyOf(Projectile shot, boolean pearlsTaken) {
      if (shot instanceof ThrownTrident trident) {
         return this.tridents.get() && !trident.isNoPhysics() ? Ballistics.Body.TRIDENT : null;
      } else if (shot instanceof AbstractArrow arrow) {
         return this.bows.get() && !arrow.isNoPhysics() ? Ballistics.Body.ARROW : null;
      } else if (!(shot instanceof ThrownEnderpearl)) {
         if (!this.throwables.get()) {
            return null;
         } else if (shot instanceof AbstractThrownPotion) {
            return Ballistics.Body.FLASK;
         } else if (shot instanceof ThrownExperienceBottle) {
            return Ballistics.Body.BOTTLE;
         } else {
            return !(shot instanceof Snowball) && !(shot instanceof ThrownEgg) ? null : Ballistics.Body.ORB;
         }
      } else {
         return this.pearls.get() && !pearlsTaken ? Ballistics.Body.ORB : null;
      }
   }

   private static PearlTrajectoryModule activePearlModule() {
      PearlTrajectoryModule pearl = ModuleRegistry.get(PearlTrajectoryModule.class);
      return pearl != null && pearl.isEnabled() ? pearl : null;
   }

   private static boolean pearlToggle(PearlTrajectoryModule pearl, String id) {
      for (Setting setting : pearl.settings()) {
         if (id.equals(setting.id()) && setting instanceof BooleanSetting toggle) {
            return toggle.get();
         }
      }

      return true;
   }

   private void onRender(Render3DEvent event) {
      this.labels.clear();
      Minecraft mc = Minecraft.getInstance();
      ClientLevel world = mc.level;
      LocalPlayer player = mc.player;
      this.syncLevel(world);
      if (world != null && player != null) {
         long now = System.nanoTime();
         this.labelsAt = now;
         float time = (float)(now - EPOCH) / 1.0E9F;
         float partialTick = event.partialTick;
         this.choosePalette();
         this.onTop = this.throughWalls.get();
         this.canvas.begin(mc.gameRenderer.mainCamera());
         this.camera = mc.gameRenderer.mainCamera().position();
         LivingEntity focus = this.targetBox.get() ? CombatTarget.current(6000L) : null;
         if (this.aimPreview.get()) {
            this.drawAim(world, player, partialTick, now, time, focus);
         } else if (this.aimItem != null || this.volleySize > 0) {
            this.forgetAim();
         }

         if (this.inFlight.get()) {
            this.drawTracks(player, partialTick, time, focus);
         }

         this.canvas.submit(this.onTop);
      }
   }

   private void drawTracks(LocalPlayer player, float partialTick, float time, LivingEntity focus) {
      ObjectIterator var5 = this.tracks.values().iterator();

      while (var5.hasNext()) {
         TrajectoriesModule.Track track = (TrajectoriesModule.Track)var5.next();
         Projectile shot = track.shot;
         if (shot != null && !shot.isRemoved()) {
            boolean own = shot.getOwner() == player;
            float alpha = own ? 1.0F : 0.7F;
            this.drawPath(track.flight, shot.getPosition(partialTick), Double.MAX_VALUE, alpha * 0.85F, false, false, own, time);
            this.drawImpact(track.flight, 1.0F, alpha, time, partialTick, focus);
            this.addLabel(track);
         }
      }
   }

   private void choosePalette() {
      boolean manual = this.colorMode.is("Manual");
      int base = manual ? this.pathColor.argb() : CosmeticAccent.argb();
      this.headTint = ARGB.srgbLerp(0.35F, base, -1);
      this.tailTint = base;
      this.haloTint = base;
      this.markTint = base;
      this.ringTint = this.headTint;
      this.sheenTint = ARGB.srgbLerp(0.8F, base, -1);
      this.plateTint = ARGB.srgbLerp(0.55F, base, -1);
      this.hitTint = manual ? this.hitColor.argb() : ARGB.srgbLerp(0.5F, base, -1);
   }

   private void drawAim(ClientLevel world, LocalPlayer player, float partialTick, long now, float time, LivingEntity focus) {
      ItemStack stack = heldLauncher(player);
      this.volleySize = 0;
      this.volleyGhost = false;
      if (!stack.isEmpty()) {
         this.loadVolley(world, player, stack, partialTick);
      }

      if (this.volleySize == 0) {
         if (this.aimItem != null) {
            this.forgetAim();
         }
      } else {
         Item item = stack.getItem();
         if (item != this.aimItem) {
            this.aimItem = item;
            this.aimSince = now;
            this.markerSince = -1L;
         }

         float unfurl = easeOutExpo((float)(now - this.aimSince) / 1.0E9F / 0.32F);
         Ballistics.Flight lead = this.volley[0].flight;
         if (unfurl < 1.0F || lead.outcome() == Ballistics.Outcome.OPEN) {
            this.markerSince = -1L;
         } else if (this.markerSince < 0L) {
            this.markerSince = now;
         }

         float grow = this.markerSince < 0L ? 0.0F : settle((float)(now - this.markerSince) / 1.0E9F);
         float alpha = this.volleyGhost ? 0.38F : 1.0F;

         for (int i = 0; i < this.volleySize; i++) {
            Ballistics.Flight flight = this.volley[i].flight;
            this.drawPath(flight, null, flight.length() * unfurl, alpha, true, !this.volleyGhost, true, time);
            if (grow > 0.0F) {
               this.drawImpact(flight, grow, alpha, time, partialTick, focus);
            }
         }

         if (!this.volleyGhost && unfurl >= 1.0F) {
            this.addLabel(this.volley[0]);
         }
      }
   }

   private static ItemStack heldLauncher(LocalPlayer player) {
      if (player.isUsingItem()) {
         return player.getUseItem();
      } else {
         ItemStack main = player.getMainHandItem();
         return launches(main.getItem()) ? main : player.getOffhandItem();
      }
   }

   private static boolean launches(Item item) {
      return item instanceof ProjectileWeaponItem
         || item instanceof TridentItem
         || item instanceof SnowballItem
         || item instanceof EggItem
         || item instanceof EnderpearlItem
         || item instanceof ThrowablePotionItem
         || item instanceof ExperienceBottleItem;
   }

   private void loadVolley(ClientLevel world, LocalPlayer player, ItemStack stack, float partialTick) {
      Item item = stack.getItem();
      boolean drawing = player.isUsingItem() && player.getUseItem() == stack;
      if (item instanceof BowItem) {
         if (!this.bows.get() || player.getProjectile(stack).isEmpty() || !drawing && !this.ghostArc.get()) {
            return;
         }

         float pull = drawing ? bowPull(player.getTicksUsingItem() + partialTick) : 1.0F;
         this.volleyGhost = !drawing || pull < 0.1F;
         this.aimed(world, player, partialTick, Ballistics.Body.ARROW, Math.max(pull, 0.1F) * 3.0F, 0.0F);
      } else if (item instanceof CrossbowItem) {
         if (this.bows.get()) {
            this.crossbowVolley(world, player, partialTick, stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY));
         }
      } else if (item instanceof TridentItem) {
         if (!this.tridents.get() || !drawing && !this.ghostArc.get()) {
            return;
         }

         if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH)) {
            return;
         }

         this.volleyGhost = !drawing || player.getTicksUsingItem() < 10;
         this.aimed(world, player, partialTick, Ballistics.Body.TRIDENT, 2.5F, 0.0F);
      } else if (item instanceof EnderpearlItem) {
         PearlTrajectoryModule pearlModule = activePearlModule();
         if (this.pearls.get() && (pearlModule == null || !pearlToggle(pearlModule, "predict"))) {
            this.aimed(world, player, partialTick, Ballistics.Body.ORB, EnderpearlItem.PROJECTILE_SHOOT_POWER, 0.0F);
         }
      } else if (this.throwables.get()) {
         this.lob(world, player, partialTick, item);
      }
   }

   private void lob(ClientLevel world, LocalPlayer player, float partialTick, Item item) {
      if (item instanceof SnowballItem) {
         this.aimed(world, player, partialTick, Ballistics.Body.ORB, SnowballItem.PROJECTILE_SHOOT_POWER, 0.0F);
      } else if (item instanceof EggItem) {
         this.aimed(world, player, partialTick, Ballistics.Body.ORB, 1.5F, 0.0F);
      } else if (item instanceof ThrowablePotionItem) {
         this.aimed(world, player, partialTick, Ballistics.Body.FLASK, ThrowablePotionItem.PROJECTILE_SHOOT_POWER, -20.0F);
      } else if (item instanceof ExperienceBottleItem) {
         this.aimed(world, player, partialTick, Ballistics.Body.BOTTLE, 0.7F, -20.0F);
      }
   }

   private static float bowPull(float ticks) {
      float seconds = ticks / 20.0F;
      return Math.min(1.0F, (seconds * seconds + seconds * 2.0F) / 3.0F);
   }

   private void aimed(ClientLevel world, LocalPlayer player, float partialTick, Ballistics.Body body, float speed, float tilt) {
      float pitchDeg = player.getViewXRot(partialTick);
      double pitch = Math.toRadians(pitchDeg);
      double yaw = Math.toRadians(player.getViewYRot(partialTick));
      double lift = Math.toRadians(pitchDeg + tilt);
      Vec3 aim = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(lift), Math.cos(yaw) * Math.cos(pitch)).normalize().scale(speed);
      Vec3 carried = player.getKnownMovement();
      Vec3 motion = aim.add(carried.x, player.onGround() ? 0.0 : carried.y, carried.z);
      this.release(world, player, body, muzzle(player, partialTick, 0.1), motion, 240);
   }

   private void crossbowVolley(ClientLevel world, LocalPlayer player, float partialTick, ChargedProjectiles loaded) {
      if (!loaded.isEmpty()) {
         List<ItemStack> rounds = loaded.items().stream().map(net.minecraft.world.item.ItemStackTemplate::create).toList();
         float speed = loaded.contains(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
         int count = rounds.size();
         float gap = count > 1 ? 20.0F / (count - 1) : 0.0F;
         float centre = (count - 1) % 2 * gap / 2.0F;
         Vec3 look = player.getViewVector(partialTick);
         Vec3 up = player.getUpVector(partialTick);

         for (int i = 0; i < count; i++) {
            ItemStack round = rounds.get(i);
            if (!round.isEmpty()) {
               float offset = centre + ((i & 1) == 1 ? -1.0F : 1.0F) * ((i + 1) / 2) * gap;
               Vec3 motion = swing(look, up, offset).normalize().scale(speed);
               if (round.is(Items.FIREWORK_ROCKET)) {
                  this.release(world, player, Ballistics.Body.ROCKET, muzzle(player, partialTick, 0.15), motion, rocketFuse(round));
               } else {
                  this.release(world, player, Ballistics.Body.ARROW, muzzle(player, partialTick, 0.1), motion, 240);
               }
            }
         }
      }
   }

   private static int rocketFuse(ItemStack rocket) {
      Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
      int flight = 1 + (fireworks != null ? fireworks.flightDuration() : 0);
      return 10 * flight + 6;
   }

   private static Vec3 swing(Vec3 vector, Vec3 axis, double degrees) {
      if (degrees == 0.0) {
         return vector;
      } else {
         double angle = Math.toRadians(degrees);
         double cos = Math.cos(angle);
         double sin = Math.sin(angle);
         Vec3 k = axis.normalize();
         return vector.scale(cos).add(k.cross(vector).scale(sin)).add(k.scale(k.dot(vector) * (1.0 - cos)));
      }
   }

   private static Vec3 muzzle(LocalPlayer player, float partialTick, double drop) {
      Vec3 feet = player.getPosition(partialTick);
      return new Vec3(feet.x, feet.y + player.getEyeHeight() - drop, feet.z);
   }

   private void release(ClientLevel world, LocalPlayer player, Ballistics.Body body, Vec3 from, Vec3 motion, int life) {
      if (this.volleySize != 3) {
         TrajectoriesModule.Aim aim = this.volley[this.volleySize++];
         if (!aim.matches(body, from, motion, life, this.sweep)) {
            aim.hold(body, from, motion, life, this.sweep);
            aim.flight.launch(body, from, motion).lifetime(life).ignoring(player, null).solve(world);
            aim.caption(from, this.flightTime.get());
         }
      }
   }

   private void drawPath(Ballistics.Flight flight, Vec3 head, double reveal, float alpha, boolean aim, boolean shine, boolean glow, float time) {
      int count = flight.size();
      if (count >= 2 && !(alpha < 0.02F)) {
         double total = flight.length();
         if (head != null) {
            double hx = head.x - flight.x(0);
            double hy = head.y - flight.y(0);
            double hz = head.z - flight.z(0);
            total += Math.sqrt(hx * hx + hy * hy + hz * hz);
         }

         if (!(total < 1.0E-4)) {
            double limit = Math.min(reveal, total);
            boolean solid = this.pathStyle.is("Solid");
            boolean dashes = this.pathStyle.is("Dashes");
            double unit = solid ? 0.6 : (dashes ? 0.72 : 0.42);
            double spread = Math.max(1.0, total / (unit * (aim ? 200 : 110)));
            double period = (dashes ? 0.72 : 0.42) * spread;
            double dash = 0.44 * spread;
            double pieceLength = 0.6 * spread;
            double phase = !solid && this.flow.get() ? time * 1.3 % period : 0.0;
            this.strokeTotal = total;
            this.strokeAlpha = alpha;
            this.strokeFade = aim;
            this.strokeSheen = shine ? time * 9.0 % (total + 14.0) - 1.6 : Double.NaN;
            this.strokeWidth = this.thickness.get();
            this.strokeHalo = glow && this.halo.get();
            double ax;
            double ay;
            double az;
            int next;
            if (head != null) {
               ax = head.x;
               ay = head.y;
               az = head.z;
               next = 0;
            } else {
               ax = flight.x(0);
               ay = flight.y(0);
               az = flight.z(0);
               next = 1;
            }

            double walked = 0.0;

            for (int i = next; i < count && walked < limit; i++) {
               double bx = flight.x(i);
               double by = flight.y(i);
               double bz = flight.z(i);
               double dx = bx - ax;
               double dy = by - ay;
               double dz = bz - az;
               double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (seg > 1.0E-6) {
                  double end = Math.min(walked + seg, limit);
                  if (solid) {
                     int pieces = Math.max(1, (int)Math.ceil((end - walked) / pieceLength));
                     double piece = (end - walked) / pieces;

                     for (int p = 0; p < pieces; p++) {
                        double u0 = walked + p * piece;
                        this.stroke(ax, ay, az, dx, dy, dz, (u0 - walked) / seg, (u0 + piece - walked) / seg, u0 + piece * 0.5);
                     }
                  } else {
                     for (double d = Math.floor((walked - phase) / period) * period + phase; d < end; d += period) {
                        double u0 = Math.max(d, walked);
                        double length = dashes ? dash : this.beadLength(ax, ay, az, dx, dy, dz, (u0 - walked) / seg);
                        double u1 = Math.min(d + length, end);
                        if (u1 > u0) {
                           this.stroke(ax, ay, az, dx, dy, dz, (u0 - walked) / seg, (u1 - walked) / seg, (u0 + u1) * 0.5);
                        }
                     }
                  }
               }

               walked += seg;
               ax = bx;
               ay = by;
               az = bz;
            }
         }
      }
   }

   private double beadLength(double ax, double ay, double az, double dx, double dy, double dz, double t) {
      double px = ax + dx * t - this.camera.x;
      double py = ay + dy * t - this.camera.y;
      double pz = az + dz * t - this.camera.z;
      return Mth.clamp(Math.sqrt(px * px + py * py + pz * pz) * 0.011, 0.035, 0.2);
   }

   private void stroke(double ax, double ay, double az, double dx, double dy, double dz, double t0, double t1, double along) {
      float alpha = this.strokeAlpha;
      if (this.strokeFade) {
         alpha *= smooth((float)(along / 1.4));
      }

      if (!(alpha < 0.02F)) {
         float progress = (float)(along / this.strokeTotal);
         int core = ARGB.srgbLerp(progress, this.headTint, this.tailTint);
         if (!Double.isNaN(this.strokeSheen)) {
            double glint = 1.0 - Math.abs(along - this.strokeSheen) / 1.6;
            if (glint > 0.0) {
               core = ARGB.srgbLerp((float)(glint * 0.7), core, this.sheenTint);
            }
         }

         double x0 = ax + dx * t0;
         double y0 = ay + dy * t0;
         double z0 = az + dz * t0;
         double x1 = ax + dx * t1;
         double y1 = ay + dy * t1;
         double z1 = az + dz * t1;
         if (this.strokeHalo) {
            float glow = alpha * 0.22F * this.haloFade(x0, y0, z0);
            if (glow > 0.0F) {
               this.canvas.line(x0, y0, z0, x1, y1, z1, ARGB.multiplyAlpha(this.haloTint, glow), this.strokeWidth * 2.8F);
            }
         }

         this.canvas.line(x0, y0, z0, x1, y1, z1, ARGB.multiplyAlpha(core, alpha), this.strokeWidth);
      }
   }

   private float haloFade(double x, double y, double z) {
      double px = x - this.camera.x;
      double py = y - this.camera.y;
      double pz = z - this.camera.z;
      double distanceSqr = px * px + py * py + pz * pz;
      if (distanceSqr <= 576.0) {
         return 1.0F;
      } else {
         return distanceSqr >= 1600.0 ? 0.0F : 1.0F - smooth((float)((Math.sqrt(distanceSqr) - 24.0) / 16.0));
      }
   }

   private void drawImpact(Ballistics.Flight flight, float grow, float alpha, float time, float partialTick, LivingEntity focus) {
      switch (flight.outcome()) {
         case ENTITY:
            if (this.targetBox.get()) {
               this.drawTargetBox(flight.struck(), alpha, time, partialTick, focus);
            }
            break;
         case BLOCK:
            this.drawMarker(flight, grow, alpha, time);
            break;
         case BURST:
            this.drawBurst(flight.impact(), grow, alpha);
      }
   }

   private void drawMarker(Ballistics.Flight flight, float grow, float alpha, float time) {
      if (!this.marker.is("None") && !(grow <= 0.01F)) {
         Direction face = flight.face();
         Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
         Vec3 up = new Vec3(0.0, 1.0, 0.0);
         if (face.getAxis() == Axis.Y) {
            Vec3 heading = flight.heading();
            up = heading.x * heading.x + heading.z * heading.z < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(heading.x, 0.0, heading.z).normalize();
         }

         Vec3 right = up.cross(normal).normalize();
         Vec3 centre = flight.impact().add(normal.scale(0.025));
         double scale = 0.5 * this.markerScale.get() * grow;
         this.gx = centre.x;
         this.gy = centre.y;
         this.gz = centre.z;
         this.rx = right.x * scale;
         this.ry = right.y * scale;
         this.rz = right.z * scale;
         this.ux = up.x * scale;
         this.uy = up.y * scale;
         this.uz = up.z * scale;
         if (this.marker.is("Sun Disk")) {
            this.drawSunDisk(alpha, time);
         } else {
            this.drawAnkh(alpha, time);
         }
      }
   }

   private void drawAnkh(float alpha, float time) {
      int gold = ARGB.multiplyAlpha(this.markTint, alpha);
      this.disc(0.78, ARGB.multiplyAlpha(this.plateTint, alpha * 0.16F));
      this.orbit(0.9, time * 0.6, ARGB.multiplyAlpha(this.ringTint, alpha * 0.85F), this.thickness.get() * 0.75F);
      this.facet(this.at(-0.06, 0.02), this.at(0.06, 0.02), this.at(0.15, -0.62), this.at(-0.15, -0.62), gold);
      this.facet(this.at(-0.06, 0.09), this.at(-0.06, -0.03), this.at(-0.37, -0.08), this.at(-0.37, 0.14), gold);
      this.facet(this.at(0.06, 0.09), this.at(0.06, -0.03), this.at(0.37, -0.08), this.at(0.37, 0.14), gold);

      for (int k = 0; k < 24; k++) {
         double c0 = RIM_COS[k];
         double s0 = RIM_SIN[k];
         double c1 = RIM_COS[k + 1];
         double s1 = RIM_SIN[k + 1];
         this.facet(
            this.at(0.19 * c0, 0.34 + 0.27 * s0),
            this.at(0.19 * c1, 0.34 + 0.27 * s1),
            this.at(0.1 * c1, 0.34 + 0.17 * s1),
            this.at(0.1 * c0, 0.34 + 0.17 * s0),
            gold
         );
      }
   }

   private void drawSunDisk(float alpha, float time) {
      int gold = ARGB.multiplyAlpha(this.markTint, alpha);
      float width = this.thickness.get();
      this.disc(0.95, ARGB.multiplyAlpha(this.plateTint, alpha * 0.12F));
      this.disc(0.3, gold);
      int ring = ARGB.multiplyAlpha(this.ringTint, alpha);
      Vec3 previous = this.at(0.42, 0.0);

      for (int k = 1; k <= 24; k++) {
         Vec3 point = this.at(0.42 * RIM_COS[k], 0.42 * RIM_SIN[k]);
         this.line(previous, point, ring, width);
         previous = point;
      }

      double spin = time * 0.35;

      for (int k = 0; k < 12; k++) {
         double angle = spin + (Math.PI * 2) * k / 12.0;
         double cos = Math.cos(angle);
         double sin = Math.sin(angle);
         double tip = (k & 1) == 0 ? 0.8 : 0.66;
         this.line(this.at(0.52 * cos, 0.52 * sin), this.at(tip * cos, tip * sin), gold, width);
      }
   }

   private void drawBurst(Vec3 centre, float grow, float alpha) {
      int color = ARGB.multiplyAlpha(this.markTint, alpha);
      double reach = 0.55 * this.markerScale.get() * grow;
      float width = this.thickness.get();

      for (double[] ray : BURST_RAYS) {
         Vec3 dir = new Vec3(ray[0], ray[1], ray[2]);
         this.line(centre.add(dir.scale(reach * 0.25)), centre.add(dir.scale(reach)), color, width);
      }
   }

   private void drawTargetBox(Entity entity, float alpha, float time, float partialTick, LivingEntity focus) {
      if (entity != null && !entity.isRemoved()) {
         Vec3 shift = entity.getPosition(partialTick).subtract(entity.position());
         AABB box = entity.getBoundingBox().move(shift).inflate(0.06);
         if (!box.contains(this.camera)) {
            boolean locked = entity == focus;
            float pulse = locked ? 0.5F + 0.5F * Mth.sin(time * 6.5F) : 0.0F;
            int edge = ARGB.multiplyAlpha(locked ? ARGB.srgbLerp(0.35F * pulse, this.hitTint, -1) : this.hitTint, alpha);
            GizmoProperties fill = Gizmos.cuboid(box, GizmoStyle.fill(ARGB.multiplyAlpha(edge, locked ? 0.14F + 0.12F * pulse : 0.1F)));
            if (this.onTop) {
               fill.setAlwaysOnTop();
            }

            this.brackets(box, locked ? 0.34 + 0.06 * pulse : 0.26, edge, this.thickness.get() * (locked ? 1.2F : 1.0F));
         }
      }
   }

   private void brackets(AABB box, double reach, int color, float width) {
      double lx = (box.maxX - box.minX) * reach;
      double ly = (box.maxY - box.minY) * reach;
      double lz = (box.maxZ - box.minZ) * reach;

      for (int corner = 0; corner < 8; corner++) {
         boolean east = (corner & 1) != 0;
         boolean top = (corner & 2) != 0;
         boolean south = (corner & 4) != 0;
         double x = east ? box.maxX : box.minX;
         double y = top ? box.maxY : box.minY;
         double z = south ? box.maxZ : box.minZ;
         Vec3 tip = new Vec3(x, y, z);
         this.line(tip, new Vec3(east ? x - lx : x + lx, y, z), color, width);
         this.line(tip, new Vec3(x, top ? y - ly : y + ly, z), color, width);
         this.line(tip, new Vec3(x, y, south ? z - lz : z + lz), color, width);
      }
   }

   private void disc(double radius, int color) {
      Vec3 centre = this.at(0.0, 0.0);

      for (int k = 0; k < 24; k++) {
         this.facet(centre, centre, this.at(radius * RIM_COS[k], radius * RIM_SIN[k]), this.at(radius * RIM_COS[k + 1], radius * RIM_SIN[k + 1]), color);
      }
   }

   private void orbit(double radius, double spin, int color, float width) {
      double slice = Math.PI / 5;
      double span = slice * 0.55;

      for (int k = 0; k < 10; k++) {
         double start = spin + k * slice;
         Vec3 previous = this.at(radius * Math.cos(start), radius * Math.sin(start));

         for (int s = 1; s <= 3; s++) {
            double angle = start + span * s / 3.0;
            Vec3 point = this.at(radius * Math.cos(angle), radius * Math.sin(angle));
            this.line(previous, point, color, width);
            previous = point;
         }
      }
   }

   private Vec3 at(double x, double y) {
      return new Vec3(this.gx + this.rx * x + this.ux * y, this.gy + this.ry * x + this.uy * y, this.gz + this.rz * x + this.uz * y);
   }

   private void facet(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
      double ex = c.x - a.x;
      double ey = c.y - a.y;
      double ez = c.z - a.z;
      double fx = d.x - b.x;
      double fy = d.y - b.y;
      double fz = d.z - b.z;
      double nx = ey * fz - ez * fy;
      double ny = ez * fx - ex * fz;
      double nz = ex * fy - ey * fx;
      double facing = nx * (this.camera.x - a.x) + ny * (this.camera.y - a.y) + nz * (this.camera.z - a.z);
      if (facing >= 0.0) {
         this.canvas.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, color);
      } else {
         this.canvas.quad(d.x, d.y, d.z, c.x, c.y, c.z, b.x, b.y, b.z, a.x, a.y, a.z, color);
      }
   }

   private void line(Vec3 from, Vec3 to, int color, float width) {
      this.canvas.line(from.x, from.y, from.z, to.x, to.y, to.z, color, width);
   }

   private void addLabel(TrajectoriesModule.Plot plot) {
      if (this.flightTime.get() && plot.seconds != null) {
         Ballistics.Flight flight = plot.flight;
         Vec3 impact = flight.impact();
         Entity struck = flight.struck();
         Vec3 anchor = struck != null ? new Vec3(impact.x, struck.getBoundingBox().maxY + 0.35, impact.z) : impact.add(0.0, 0.55, 0.0);
         this.labels.add(new TrajectoriesModule.Label(anchor, plot.seconds, plot.detail));
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.flightTime.get();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      if (!this.labels.isEmpty() && System.nanoTime() - this.labelsAt <= 250000000L) {
         Minecraft mc = Minecraft.getInstance();

         for (TrajectoriesModule.Label label : this.labels) {
            float[] screen = WorldLabel.project(mc, label.pos(), screenW, screenH);
            if (screen != null) {
               WorldLabel.drawText(dl, screen[0], screen[1], label.primary(), label.secondary(), 1.0F);
            }
         }
      }
   }

   private static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static float easeOutExpo(float t) {
      return t >= 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, -10.0 * Math.max(0.0F, t));
   }

   private static float settle(float seconds) {
      return 1.0F - (float)(Math.exp(-5.5 * seconds) * Math.cos(8.5 * seconds));
   }

   static {
      for (int i = 0; i <= 24; i++) {
         double angle = (Math.PI * 2) * i / 24.0;
         RIM_COS[i] = Math.cos(angle);
         RIM_SIN[i] = Math.sin(angle);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Aim extends TrajectoriesModule.Plot {
      Ballistics.Body body;
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      int life;
      int stamp;
      boolean held;

      boolean matches(Ballistics.Body body, Vec3 from, Vec3 motion, int life, int stamp) {
         return this.held
            && this.stamp == stamp
            && this.body == body
            && this.life == life
            && Math.abs(this.x - from.x) < 0.001
            && Math.abs(this.y - from.y) < 0.001
            && Math.abs(this.z - from.z) < 0.001
            && Math.abs(this.vx - motion.x) < 5.0E-4
            && Math.abs(this.vy - motion.y) < 5.0E-4
            && Math.abs(this.vz - motion.z) < 5.0E-4;
      }

      void hold(Ballistics.Body body, Vec3 from, Vec3 motion, int life, int stamp) {
         this.body = body;
         this.x = from.x;
         this.y = from.y;
         this.z = from.z;
         this.vx = motion.x;
         this.vy = motion.y;
         this.vz = motion.z;
         this.life = life;
         this.stamp = stamp;
         this.held = true;
      }

      @Override
      void forget() {
         super.forget();
         this.held = false;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Label(Vec3 pos, String primary, String secondary) {
   }

   @Environment(EnvType.CLIENT)
   private static class Plot {
      final Ballistics.Flight flight = new Ballistics.Flight();
      String seconds;
      String detail;

      void caption(Vec3 from, boolean wanted) {
         this.seconds = null;
         this.detail = null;
         if (wanted && this.flight.outcome() != Ballistics.Outcome.OPEN && this.flight.size() >= 2) {
            Entity struck = this.flight.struck();
            this.seconds = String.format(Locale.ROOT, "%.2fs", this.flight.ticks() / 20.0F);
            this.detail = struck != null
               ? NameProtectModule.protect(struck.getName()).getString()
               : String.format(Locale.ROOT, "%.0fm", from.distanceTo(this.flight.impact()));
         }
      }

      void forget() {
         this.flight.clear();
         this.seconds = null;
         this.detail = null;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Track extends TrajectoriesModule.Plot {
      Projectile shot;
      int seen;
      boolean departed;
   }
}
