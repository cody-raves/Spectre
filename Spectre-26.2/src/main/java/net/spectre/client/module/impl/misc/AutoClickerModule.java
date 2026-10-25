package net.spectre.client.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AutoClickerModule extends ClientModule {
   private static final String CLICK = "Click";
   private static final String HOLD = "Hold";
   private static final String OFF = "Off";
   private static final double TICK_MS = 50.0;
   private static final double PACE_MIN_MS = 500.0;
   private static final double PACE_MAX_MS = 1500.0;
   private static final double PACE_EASE = 0.3;
   private static final double HOLD_MEAN_MS = 55.0;
   private static final double HOLD_SPREAD_MS = 18.0;
   private static final double HOLD_MIN_MS = 20.0;
   private static final double HOLD_MAX_MS = 110.0;
   private static final double HOLD_MAX_GAP = 0.7;
   private static final double GAP_MIN_FACTOR = 0.6;
   private static final double GAP_MAX_FACTOR = 2.2;
   private final ModeSetting left = this.addSetting("left", new ModeSetting("Left", "Click", "Click", "Hold", "Off"));
   private final NumberSetting leftMin = this.addSetting("left-min-cps", new NumberSetting("Left Min CPS", 9.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting leftMax = this.addSetting("left-max-cps", new NumberSetting("Left Max CPS", 13.0F, 1.0F, 20.0F).step(1.0F));
   private final ModeSetting right = this.addSetting("right", new ModeSetting("Right", "Off", "Click", "Hold", "Off"));
   private final NumberSetting rightMin = this.addSetting("right-min-cps", new NumberSetting("Right Min CPS", 8.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting rightMax = this.addSetting("right-max-cps", new NumberSetting("Right Max CPS", 12.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting randomization = this.addSetting("randomization", new NumberSetting("Randomization", 15.0F, 0.0F, 40.0F).step(1.0F));
   private final BooleanSetting onlyWhileHeld = this.addSetting("only-while-held", new BooleanSetting("Only While Held", false));
   private final BooleanSetting breakBlocks = this.addSetting("break-blocks", new BooleanSetting("Break Blocks", true));
   private final AutoClickerModule.Button leftButton = new AutoClickerModule.Button(true, this.left, this.leftMin, this.leftMax, mc -> mc.options.keyAttack);
   private final AutoClickerModule.Button rightButton = new AutoClickerModule.Button(false, this.right, this.rightMin, this.rightMax, mc -> mc.options.keyUse);

   public AutoClickerModule() {
      super("auto-clicker", "Auto Clicker", "Clicks the left and/or right mouse button at a randomised, human CPS.", ModuleCategory.MISC);
      this.leftMin.visibleWhen(() -> this.left.is("Click"));
      this.leftMax.visibleWhen(() -> this.left.is("Click"));
      this.rightMin.visibleWhen(() -> this.right.is("Click"));
      this.rightMax.visibleWhen(() -> this.right.is("Click"));
      this.randomization.visibleWhen(this::anyClicking);
      this.onlyWhileHeld.visibleWhen(this::anyClicking);
      this.breakBlocks.visibleWhen(() -> this.left.is("Click"));
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1) {
         this.onlyWhileHeld.set(false);
      }
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.leftButton.handBack(mc);
      this.rightButton.handBack(mc);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      boolean allowed = MeleeAttacks.canAct(mc) && mc.gui.overlay() == null && !CombatMacroLock.heldByOther(this);
      this.leftButton.tick(mc, allowed);
      this.rightButton.tick(mc, allowed);
   }

   private boolean anyClicking() {
      return this.left.is("Click") || this.right.is("Click");
   }

   private static boolean aimsAtBlock(Minecraft mc) {
      return mc.hitResult instanceof BlockHitResult hit
         && hit.getType() == Type.BLOCK
         && mc.level != null
         && !mc.level.getBlockState(hit.getBlockPos()).isAir();
   }

   private static boolean physicallyDown(Minecraft mc, KeyMapping mapping) {
      if (mapping.isUnbound()) {
         return false;
      } else {
         Key key = KeyMappingHelper.getBoundKeyOf(mapping);
         int value = key.getValue();
         if (value < 0) {
            return false;
         } else {
            return switch (key.getType()) {
               case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), value) == 1;
               case KEYSYM -> InputConstants.isKeyDown(mc.getWindow(), value);
               default -> mapping.isDown();
            };
         }
      }
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   @Environment(EnvType.CLIENT)
   private final class Button {
      private final boolean isLeft;
      private final ModeSetting mode;
      private final NumberSetting minCps;
      private final NumberSetting maxCps;
      private final Function<Minecraft, KeyMapping> mapping;
      private boolean driving;
      private boolean running;
      private double clock;
      private double nextPress;
      private double releaseAt;
      private double rate;
      private double pace;
      private double repaceAt;

      Button(boolean isLeft, ModeSetting mode, NumberSetting minCps, NumberSetting maxCps, Function<Minecraft, KeyMapping> mapping) {
         this.isLeft = isLeft;
         this.mode = mode;
         this.minCps = minCps;
         this.maxCps = maxCps;
         this.mapping = mapping;
      }

      void tick(Minecraft mc, boolean allowed) {
         if (allowed && !this.mode.is("Off")) {
            KeyMapping key = this.mapping.apply(mc);
            if (this.mode.is("Hold")) {
               this.running = false;
               this.driving = true;
               key.setDown(true);
            } else if (!key.isUnbound()
               && (!AutoClickerModule.this.onlyWhileHeld.get() || AutoClickerModule.physicallyDown(mc, key))
               && (!this.isLeft || !AutoClickerModule.this.breakBlocks.get() || !AutoClickerModule.aimsAtBlock(mc))
               && (this.isLeft || !mc.player.isUsingItem())) {
               if (!this.running) {
                  this.start();
               }

               this.clock += 50.0;
               if (this.isLeft) {
                  mc.missTime = 0;
               }

               Key bound = KeyMappingHelper.getBoundKeyOf(key);

               while (this.nextPress <= this.clock) {
                  KeyMapping.click(bound);
                  double gap = this.nextGap();
                  this.releaseAt = this.nextPress + this.holdFor(gap);
                  this.nextPress += gap;
               }

               this.driving = true;
               key.setDown(this.clock < this.releaseAt);
            } else {
               this.handBack(mc);
            }
         } else {
            this.handBack(mc);
         }
      }

      private void start() {
         double lo = Math.min(this.minCps.get(), this.maxCps.get());
         double hi = Math.max(this.minCps.get(), this.maxCps.get());
         ThreadLocalRandom random = ThreadLocalRandom.current();
         this.running = true;
         this.clock = 0.0;
         this.releaseAt = 0.0;
         this.pace = lo + random.nextDouble() * (hi - lo);
         this.rate = this.pace;
         this.repaceAt = 500.0 + random.nextDouble() * 1000.0;
         this.nextPress = this.nextGap();
      }

      private double nextGap() {
         double lo = Math.min(this.minCps.get(), this.maxCps.get());
         double hi = Math.max(this.minCps.get(), this.maxCps.get());
         ThreadLocalRandom random = ThreadLocalRandom.current();
         if (this.clock >= this.repaceAt) {
            this.pace = lo + random.nextDouble() * (hi - lo);
            this.repaceAt = this.clock + 500.0 + random.nextDouble() * 1000.0;
         }

         this.rate = AutoClickerModule.clamp(this.rate + (this.pace - this.rate) * 0.3, lo, hi);
         double gap = 1000.0 / this.rate;
         double spread = AutoClickerModule.this.randomization.get() / 100.0;
         if (spread <= 0.0) {
            return gap;
         } else {
            double factor = 1.0 + random.nextGaussian() * spread;
            if (random.nextDouble() < spread * 0.25) {
               factor += 0.4 + random.nextDouble() * 0.6;
            }

            return gap * AutoClickerModule.clamp(factor, 0.6, 2.2);
         }
      }

      private double holdFor(double gap) {
         if (AutoClickerModule.this.randomization.get() <= 0.0F) {
            return gap * 0.5;
         } else {
            double hold = 55.0 + ThreadLocalRandom.current().nextGaussian() * 18.0;
            return AutoClickerModule.clamp(hold, 20.0, Math.min(110.0, gap * 0.7));
         }
      }

      void handBack(Minecraft mc) {
         this.running = false;
         if (this.driving) {
            this.driving = false;
            KeyMapping key = this.mapping.apply(mc);
            key.setDown(mc.gui.screen() == null && AutoClickerModule.physicallyDown(mc, key));
         }
      }
   }
}
