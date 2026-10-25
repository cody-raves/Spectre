package net.spectre.client.module.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.crystal.Bypass;
import net.spectre.client.module.impl.combat.crystal.CrystalDamage;
import net.spectre.client.module.impl.combat.crystal.Crystals;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.impl.misc.AutoEatModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class CrystalMacroModule extends ClientModule {
   private static final int MIN_SWITCH_INTERVAL = 2;
   private static final int COUNTER_CAP = 1000;
   private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 20.0F));
   private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 20.0F));
   private final NumberSetting breakRange = this.addSetting("break-range", new NumberSetting("Break Range", 3.0F, 1.0F, 4.5F));
   private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
   private final NumberSetting obsidianDelay = this.addSetting("obsidian-delay", new NumberSetting("Obsidian Delay", 3.0F, 1.0F, 20.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 0.0F, 0.0F, 4.0F));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final ModeSetting acProfile = this.addSetting("ac-profile", new ModeSetting("AC Profile", "Grim-Aldenz", "Off", "Grim-Aldenz"));
   private boolean engaged;
   private int savedSlot = -1;
   private int ownSlot = -1;
   private int ticksSinceAction = 1000;
   private int ticksSinceSwitch = 1000;
   private int extraDelay;
   private int switchSettle = 1;
   private int pendingBreakId = -1;
   private int pendingBreakAge = 1000;
   private BlockPos pendingCrystalBase;
   private int pendingCrystalAge = 1000;
   private BlockPos pendingObsidian;
   private int pendingObsidianAge = 1000;
   private final Bypass.Budget budget = new Bypass.Budget();

   public CrystalMacroModule() {
      super("crystal-macro", "Crystal Macro", "Breaks and places crystals while you hold right-click.", ModuleCategory.COMBAT);
      this.obsidianDelay.visibleWhen(this.autoObsidian::get);
   }

   @Override
   protected void onEnable() {
      this.resetState();
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.endHold(mc);
      LocalPlayer player = mc.player;
      if (player != null && mc.gui.screen() == null && !hotbarTakenByOther()) {
         this.restoreSlot(player);
      }

      this.resetState();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      this.ageCounters();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()) {
         this.endHold(mc);
         this.forgetSlots();
      } else if (!this.holdActive(mc, player)) {
         this.endHold(mc);
         if (mc.gui.screen() == null && !hotbarTakenByOther()) {
            this.restoreSlot(player);
         }
      } else if (!AutoInvTotemModule.wantsHotbar() && !CombatMacroLock.heldByOther(this)) {
         if (this.engaged) {
            if (!this.keepEngaged(player)) {
               this.endHold(mc);
               return;
            }
         } else if (player.isUsingItem() && !Bypass.allowAttackWhileUsing(this.profile()) || !this.ownsSelection(player) && !activationAllows(player)) {
            return;
         }

         CrystalMacroModule.Plan plan = this.plan(mc, player);
         if (this.engaged || plan != null || this.ownsSelection(player)) {
            this.engaged = true;
            CombatMacroLock.tryAcquire(this);
            suppressVanillaUse(mc);
            if (plan != null
               && !player.isHandsBusy()
               && (!player.isUsingItem() || Bypass.allowAttackWhileUsing(this.profile()))
               && this.budget.allows(this.profile())) {
               switch (plan.step()) {
                  case BREAK:
                     this.breakCrystal(mc, plan);
                     break;
                  case CRYSTAL:
                     if (this.useOnCrosshair(mc, player, this.placeDelay, plan)) {
                        this.pendingCrystalBase = plan.target();
                        this.pendingCrystalAge = 0;
                     }
                     break;
                  case OBSIDIAN:
                     if (this.useOnCrosshair(mc, player, this.obsidianDelay, plan)) {
                        this.pendingObsidian = plan.target();
                        this.pendingObsidianAge = 0;
                     }
               }
            }
         }
      } else {
         CombatMacroLock.release(this);
         if (this.engaged) {
            suppressVanillaUse(mc);
         }
      }
   }

   @EventHandler
   public void onAttack(PlayerAttackEntityEvent event) {
      if (event.target instanceof EndCrystal crystal && crystal.level().isClientSide()) {
         this.markBreak(crystal);
      }
   }

   private boolean holdActive(Minecraft mc, LocalPlayer player) {
      return MeleeAttacks.canAct(mc) && mc.isWindowActive() && !player.isSpectator() && Crystals.isPhysicallyDown(mc, mc.options.keyUse);
   }

   private static boolean hotbarTakenByOther() {
      return AutoEatModule.isEating() || AutoInvTotemModule.wantsHotbar();
   }

   private boolean keepEngaged(LocalPlayer player) {
      if (this.ownsSelection(player)) {
         return true;
      } else {
         this.forgetSlots();
         return activationAllows(player);
      }
   }

   private boolean ownsSelection(LocalPlayer player) {
      return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
   }

   private static boolean activationAllows(LocalPlayer player) {
      ItemStack main = player.getMainHandItem();
      return main.is(Items.END_CRYSTAL) || player.getOffhandItem().is(Items.END_CRYSTAL) && Crystals.isCombatItem(main);
   }

   private void endHold(Minecraft mc) {
      CombatMacroLock.release(this);
      if (this.engaged) {
         this.engaged = false;
         mc.options.keyUse.setDown(mc.gui.screen() == null && Crystals.isPhysicallyDown(mc, mc.options.keyUse));
      }
   }

   private void restoreSlot(LocalPlayer player) {
      if (this.ownSlot != -1) {
         Inventory inventory = player.getInventory();
         if (this.switchBack.get() && Inventory.isHotbarSlot(this.savedSlot) && this.savedSlot != this.ownSlot && inventory.getSelectedSlot() == this.ownSlot) {
            inventory.setSelectedSlot(this.savedSlot);
            this.noteSwitch();
         }

         this.forgetSlots();
      }
   }

   private void forgetSlots() {
      this.savedSlot = -1;
      this.ownSlot = -1;
   }

   private void resetState() {
      this.engaged = false;
      this.forgetSlots();
      this.ticksSinceAction = 1000;
      this.ticksSinceSwitch = 1000;
      this.extraDelay = 0;
      this.switchSettle = 1;
      this.pendingBreakId = -1;
      this.pendingBreakAge = 1000;
      this.pendingCrystalBase = null;
      this.pendingCrystalAge = 1000;
      this.pendingObsidian = null;
      this.pendingObsidianAge = 1000;
      this.budget.reset();
      CombatMacroLock.release(this);
   }

   private Bypass.Profile profile() {
      return Bypass.Profile.of(this.acProfile.get());
   }

   private static void suppressVanillaUse(Minecraft mc) {
      KeyMapping use = mc.options.keyUse;
      use.setDown(false);

      while (use.consumeClick()) {
      }
   }

   private CrystalMacroModule.Plan plan(Minecraft mc, LocalPlayer player) {
      HitResult hit = mc.hitResult;
      int window = Bypass.confirmTicks(this.profile(), Crystals.confirmWindow(mc, player));
      if (this.pendingCrystalBase != null && Crystals.hasCrystal(mc.level, this.pendingCrystalBase, null)) {
         this.pendingCrystalBase = null;
      }

      if (hit instanceof EntityHitResult) {
         EntityHitResult entityHit = MeleeAttacks.crosshairHit(mc);
         if (entityHit == null || !(entityHit.getEntity() instanceof EndCrystal crystal && crystal.isAlive())) {
            return null;
         } else if (!Bypass.mayRetryCrystal(this.profile(), crystal.getId(), this.pendingBreakId, this.pendingBreakAge, window)) {
            return null;
         } else {
            float range = Bypass.clampBreakRange(this.profile(), this.breakRange.get());
            if (player.getEyePosition().distanceTo(entityHit.getLocation()) > range) {
               return null;
            } else {
               return CrystalDamage.endangers(player, CrystalDamage.estimate(crystal.position(), player))
                  ? null
                  : CrystalMacroModule.Plan.forBreak(entityHit, crystal);
            }
         }
      } else if (hit instanceof BlockHitResult blockHit && blockHit.getType() == Type.BLOCK) {
         ClientLevel level = mc.level;
         BlockPos pos = blockHit.getBlockPos();
         if (!level.getWorldBorder().isWithinBounds(pos)) {
            return null;
         } else {
            BlockState state = level.getBlockState(pos);
            if (Crystals.isBase(state)) {
               if (pos.equals(this.pendingCrystalBase) && this.pendingCrystalAge < window) {
                  return null;
               } else if (!Crystals.canHost(level, pos, null)) {
                  return null;
               } else {
                  int route = Crystals.route(player, Items.END_CRYSTAL, state, this.savedSlot);
                  return route == -1 ? null : CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.CRYSTAL, blockHit, pos, route);
               }
            } else if (!this.autoObsidian.get()) {
               return null;
            } else if (Crystals.route(player, Items.END_CRYSTAL, Blocks.OBSIDIAN.defaultBlockState(), this.savedSlot) == -1) {
               return null;
            } else {
               int route = Crystals.route(player, Items.OBSIDIAN, state, this.savedSlot);
               if (route == -1) {
                  return null;
               } else {
                  BlockPos target = Crystals.obsidianTarget(level, player, blockHit, state);
                  return target != null && (!target.equals(this.pendingObsidian) || this.pendingObsidianAge >= window)
                     ? CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.OBSIDIAN, blockHit, target, route)
                     : null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private boolean breakCrystal(Minecraft mc, CrystalMacroModule.Plan plan) {
      if (this.ticksSinceAction < this.delayTicks(this.breakDelay)) {
         return false;
      } else {
         LocalPlayer player = mc.player;
         if (player != null && Crystals.strike(mc, player, plan.crystal(), this.profile())) {
            KeyMapping attack = mc.options.keyAttack;

            while (attack.consumeClick()) {
            }

            this.markBreak(plan.crystal());
            this.markAction();
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean useOnCrosshair(Minecraft mc, LocalPlayer player, NumberSetting delay, CrystalMacroModule.Plan plan) {
      if (plan.route() != -2) {
         this.select(player, plan.route());
         return false;
      } else if (this.ticksSinceSwitch < this.switchSettle || this.ticksSinceAction < this.delayTicks(delay)) {
         return false;
      } else if (mc.gameMode.isDestroying() && !Bypass.allowPlaceWhileDigging(this.profile())) {
         return false;
      } else {
         Crystals.rightClick(mc, player, plan.blockHit());
         this.markAction();
         return true;
      }
   }

   private void select(LocalPlayer player, int slot) {
      if (this.ticksSinceSwitch >= Bypass.minSwitchInterval(this.profile()) && Inventory.isHotbarSlot(slot)) {
         Inventory inventory = player.getInventory();
         if (this.savedSlot == -1) {
            this.savedSlot = inventory.getSelectedSlot();
         }

         inventory.setSelectedSlot(slot);
         this.ownSlot = slot;
         this.noteSwitch();
      }
   }

   private void noteSwitch() {
      this.ticksSinceSwitch = 0;
      this.switchSettle = 1 + this.rollJitter();
   }

   private int delayTicks(NumberSetting delay) {
      return Bypass.minDelay(this.profile(), delay.getInt()) + this.extraDelay;
   }

   private void markAction() {
      this.ticksSinceAction = 0;
      this.extraDelay = this.rollJitter();
      this.budget.note();
   }

   private int rollJitter() {
      int spread = this.jitter.getInt();
      return spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0;
   }

   private void markBreak(EndCrystal crystal) {
      this.pendingBreakId = crystal.getId();
      this.pendingBreakAge = 0;
   }

   private void ageCounters() {
      this.ticksSinceAction = Math.min(this.ticksSinceAction + 1, 1000);
      this.ticksSinceSwitch = Math.min(this.ticksSinceSwitch + 1, 1000);
      this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
      this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
      this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
   }

   @Environment(EnvType.CLIENT)
   private record Plan(CrystalMacroModule.Step step, EntityHitResult entityHit, EndCrystal crystal, BlockHitResult blockHit, BlockPos target, int route) {
      static CrystalMacroModule.Plan forBreak(EntityHitResult entityHit, EndCrystal crystal) {
         return new CrystalMacroModule.Plan(CrystalMacroModule.Step.BREAK, entityHit, crystal, null, null, -2);
      }

      static CrystalMacroModule.Plan forPlace(CrystalMacroModule.Step step, BlockHitResult blockHit, BlockPos target, int route) {
         return new CrystalMacroModule.Plan(step, null, null, blockHit, target, route);
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum Step {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }
}
