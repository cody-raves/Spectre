package net.spectre.client.module.impl.misc;

import com.example.client.mixin.AccessorMovementClientInput;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.movement.AutoWalkModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class FreecamModule extends ClientModule {
   private static final int WALK_FWD = 1;
   private static final int WALK_BACK = 2;
   private static final int WALK_LEFT = 4;
   private static final int WALK_RIGHT = 8;
   private static final int WALK_JUMP = 16;
   private static final int WALK_SNEAK = 32;
   private static final int WALK_SPRINT = 64;
   private static final int WALK_FLAT = 15;
   private static final int LEARN_TICKS = 40;
   private static final int CHUNK_CAP = 32;
   private static final float SCROLL_STEP = 1.2F;
   public static volatile FreecamModule active;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 1.0F, 0.1F, 50.0F).step(0.1F));
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical", 1.0F, 0.2F, 5.0F).step(0.05F));
   private final BooleanSetting smoothing = this.addSetting("smoothing", new BooleanSetting("Smooth", true));
   private final BooleanSetting showOwnBody = this.addSetting("show-own-body", new BooleanSetting("Body", true));
   private final BooleanSetting showHands = this.addSetting("show-hands", new BooleanSetting("Hands", true));
   private final BooleanSetting bodyFollowsKeys = this.addSetting("body-uses-movement-keys", new BooleanSetting("Keys Move Body", false));
   private final BooleanSetting keepWalking = this.addSetting("keep-walking", new BooleanSetting("Keep Walking", true));
   private final BooleanSetting scrollSpeed = this.addSetting("scroll-speed", new BooleanSetting("Scroll Speed", true));
   private final NumberSetting chunkDistance = this.addSetting("chunk-distance", new NumberSetting("Chunks", 12.0F, 2.0F, 32.0F).step(1.0F));
   private Vec3 pos = Vec3.ZERO;
   private Vec3 prev = Vec3.ZERO;
   private float yaw;
   private float pitch;
   private boolean flyingBefore;
   private boolean cullBefore;
   private CameraType viewBefore;
   private boolean viewPushed;
   private ChunkPos focus;
   private int radius = -1;
   private boolean waiting;
   private boolean live;
   private int walk;
   private int learn;

   public FreecamModule() {
      super("freecam", "Freecam", "Detached camera.", ModuleCategory.MISC);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.waiting = true;
      this.live = false;
      this.on(TickEvent.TYPE, this::onTick);
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      boolean wasLive = this.live;
      this.waiting = false;
      this.live = false;
      if (active == this) {
         active = null;
      }

      this.walk = 0;
      this.learn = 0;
      if (this.viewPushed) {
         mc.options.setCameraType(this.viewBefore);
         this.viewPushed = false;
      }

      if (wasLive) {
         mc.smartCull = this.cullBefore;
         if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
         }

         this.releaseChunks(mc);
         if (mc.player != null) {
            mc.player.getAbilities().flying = this.flyingBefore;
         }
      }
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      this.open(mc);
      LocalPlayer player = mc.player;
      if (this.live && player != null) {
         if (this.learn > 0) {
            this.learn--;
            this.absorb(player, false);
         }

         this.prev = this.pos;
         if (player.getAbilities().instabuild) {
            player.getAbilities().flying = false;
         }

         this.stepCamera(mc.options);
         this.followChunks(mc);
      }
   }

   private void open(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (this.waiting && player != null && mc.level != null) {
         this.flyingBefore = player.getAbilities().flying;
         this.pos = this.prev = new Vec3(player.getX(), player.getY() + player.getEyeHeight(Pose.STANDING), player.getZ());
         this.yaw = player.getYRot();
         this.pitch = player.getXRot();
         this.viewPushed = false;
         if (this.showOwnBody.get()) {
            this.viewBefore = mc.options.getCameraType();
            if (this.viewBefore.isFirstPerson()) {
               mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
               this.viewPushed = true;
            }
         }

         this.waiting = false;
         this.live = true;
         active = this;
         this.walk = 0;
         this.absorb(player, true);
         this.learn = 40;
         this.focus = null;
         this.radius = -1;
         this.cullBefore = mc.smartCull;
         mc.smartCull = false;
         this.followChunks(mc);
         drive(mc);
      }
   }

   private void stepCamera(Options options) {
      double ahead = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
      double side = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
      double rise = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
      double step = this.speed.get() * (this.smoothing.get() ? 0.5 : 1.0);
      Vec3 look = Vec3.directionFromRotation(0.0F, this.yaw);
      Vec3 left = new Vec3(look.z, 0.0, -look.x);
      this.pos = this.pos.add((look.x * ahead + left.x * side) * step, rise * step * this.verticalSpeed.get(), (look.z * ahead + left.z * side) * step);
   }

   private void absorb(LocalPlayer player, boolean opening) {
      Options options = Minecraft.getInstance().options;
      Input keys = player.input.keyPresses;
      boolean carry = this.keepWalking.get();
      boolean typed = carry && (opening || this.bodyFollowsKeys.get());
      if (typed) {
         this.setWalk(1, options.keyUp.isDown() || keys.forward());
         this.setWalk(2, options.keyDown.isDown() || keys.backward());
         this.setWalk(4, options.keyLeft.isDown() || keys.left());
         this.setWalk(8, options.keyRight.isDown() || keys.right());
         this.setWalk(16, options.keyJump.isDown() || keys.jump());
         this.setWalk(32, options.keyShift.isDown() || keys.shift() || opening && player.isShiftKeyDown());
         this.setWalk(64, options.keySprint.isDown() || keys.sprint());
      } else if (player.isShiftKeyDown()) {
         this.walk |= 32;
      }

      AutoWalkModule autoWalk = ModuleRegistry.get(AutoWalkModule.class);
      if (autoWalk != null && autoWalk.isEnabled()) {
         this.walk |= 1;
      }

      if (carry) {
         if (player.zza > 0.02F) {
            this.walk |= 1;
         }

         if (player.zza < -0.02F) {
            this.walk |= 2;
         }

         if (player.xxa > 0.02F) {
            this.walk |= 4;
         }

         if (player.xxa < -0.02F) {
            this.walk |= 8;
         }

         if (!opening || (this.walk & 15) == 0) {
            this.aimFromMotion(player);
         }
      }
   }

   private void setWalk(int bit, boolean on) {
      if (on) {
         this.walk |= bit;
      }
   }

   private void aimFromMotion(LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      double vx = motion.x;
      double vz = motion.z;
      if (!(vx * vx + vz * vz < 1.0E-8)) {
         Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
         double along = vx * look.x + vz * look.z;
         double across = vz * look.x - vx * look.z;
         if (Math.abs(along) >= Math.abs(across)) {
            if (along > 0.01) {
               this.walk |= 1;
            } else if (along < -0.01) {
               this.walk |= 2;
            }
         } else if (across > 0.01) {
            this.walk |= 4;
         } else if (across < -0.01) {
            this.walk |= 8;
         }
      }
   }

   public static void drive(Minecraft mc) {
      FreecamModule module = active;
      if (module != null && mc.player != null) {
         module.pushWalk(mc);
      }
   }

   private void pushWalk(Minecraft mc) {
      Options options = mc.options;
      boolean liveKeys = this.bodyFollowsKeys.get();
      boolean forward = this.on(1) || liveKeys && options.keyUp.isDown();
      boolean back = this.on(2) || liveKeys && options.keyDown.isDown();
      boolean left = this.on(4) || liveKeys && options.keyLeft.isDown();
      boolean right = this.on(8) || liveKeys && options.keyRight.isDown();
      boolean jump = this.on(16);
      boolean sneak = this.on(32) || liveKeys && options.keyShift.isDown();
      boolean sprint = this.on(64) || liveKeys && options.keySprint.isDown();
      if (liveKeys) {
         if (options.keyDown.isDown()) {
            this.walk &= -2;
         }

         if (options.keyUp.isDown()) {
            this.walk &= -3;
         }

         if (options.keyRight.isDown()) {
            this.walk &= -5;
         }

         if (options.keyLeft.isDown()) {
            this.walk &= -9;
         }
      }

      LocalPlayer player = mc.player;
      player.input.keyPresses = new Input(forward, back, left, right, jump, sneak, sprint);
      float sideways = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);
      float ahead = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
      ((AccessorMovementClientInput)player.input).spectre$setMoveVector(new Vec2(sideways, ahead).normalized());
      player.setShiftKeyDown(sneak);
   }

   private boolean on(int bit) {
      return (this.walk & bit) != 0;
   }

   private void followChunks(Minecraft mc) {
      if (this.live && mc.level != null && mc.player != null) {
         ClientChunkCache cache = mc.level.getChunkSource();
         ChunkPos camera = new ChunkPos(Mth.floor(this.pos.x) >> 4, Mth.floor(this.pos.z) >> 4);
         ChunkPos feet = mc.player.chunkPosition();
         int span = Math.max(Math.abs(camera.x() - feet.x()), Math.abs(camera.z() - feet.z()));
         int preference = Mth.clamp(this.chunkDistance.getInt(), 2, 32);
         ChunkPos next = span > preference ? new ChunkPos(feet.x() + (camera.x() - feet.x()) / 2, feet.z() + (camera.z() - feet.z()) / 2) : camera;
         int needed = Mth.clamp(Math.max(preference, span / 2 + 3), 2, 32);
         if (needed != this.radius) {
            cache.updateViewRadius(needed);
            this.radius = needed;
         }

         if (this.focus == null || next.x() != this.focus.x() || next.z() != this.focus.z()) {
            cache.updateViewCenter(next.x(), next.z());
            this.focus = next;
            if (mc.levelRenderer != null) {
               mc.levelRenderer.sectionOcclusionGraph().invalidate();
            }
         }
      }
   }

   private void releaseChunks(Minecraft mc) {
      this.focus = null;
      this.radius = -1;
      if (mc.level != null) {
         ClientChunkCache cache = mc.level.getChunkSource();
         if (mc.player != null) {
            ChunkPos feet = mc.player.chunkPosition();
            cache.updateViewCenter(feet.x(), feet.z());
         }

         cache.updateViewRadius(mc.options.getEffectiveRenderDistance());
         if (mc.levelRenderer != null) {
            mc.levelRenderer.sectionOcclusionGraph().invalidate();
         }
      }
   }

   public boolean drawsSelf(Entity entity, Player viewer) {
      Minecraft mc = Minecraft.getInstance();
      return this.showOwnBody.get() && mc.player != null && entity == mc.player && viewer == mc.player;
   }

   public boolean keepHands() {
      return this.showHands.get();
   }

   public boolean showBody() {
      return this.showOwnBody.get();
   }

   public static boolean scroll(double amount) {
      FreecamModule module = active;
      if (module != null && module.scrollSpeed.get() && amount != 0.0) {
         module.speed.set(module.speed.get() * (amount > 0.0 ? 1.2F : 0.8333333F));
         return true;
      } else {
         return false;
      }
   }

   public void turn(double yRot, double xRot) {
      this.yaw += (float)yRot * 0.15F;
      this.pitch = Mth.clamp(this.pitch + (float)xRot * 0.15F, -90.0F, 90.0F);
   }

   public double getX(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prev.x, this.pos.x);
   }

   public double getY(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prev.y, this.pos.y);
   }

   public double getZ(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prev.z, this.pos.z);
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }
}
