package net.spectre.client.module.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.anchor.AnchorGuard;
import net.spectre.client.module.impl.combat.crystal.Crystals;
import net.spectre.client.module.impl.misc.AutoEatModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.pvp.Rotation;
import net.spectre.client.util.pvp.RotationUtils;
import net.spectre.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class SingleAnchorMacroModule extends ClientModule {
   private static final String SINGLE = "Single";
   private static final String DOUBLE = "Double";
   private static final String DETONATE = "Detonate";
   private static final String CANCEL = "Cancel";
   private static final int CLEAR_TIMEOUT_TICKS = 20;
   private static final int MAX_STEPS_PER_TICK = 4;
   private static final int GUARD_AIM_TICKS = 5;
   private static final int GUARD_TRIES = 2;
   private static final int SETTLE_TICKS = 5;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Single", "Single", "Double"));
   private final NumberSetting safetySlot = this.addSetting("safety-slot", new NumberSetting("Safety Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
   private final NumberSetting switchDelay = this.addSetting("switch-delay", new NumberSetting("Switch Delay", 1.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting chargeDelay = this.addSetting("charge-delay", new NumberSetting("Charge Delay", 1.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting explodeDelay = this.addSetting("explode-delay", new NumberSetting("Explode Delay", 1.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting cycleDelay = this.addSetting("cycle-delay", new NumberSetting("Cycle Delay", 2.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 1.0F, 1.0F, 10.0F).step(1.0F));
   private final BooleanSetting instantRefill = this.addSetting("double-anchor", new BooleanSetting("Double Anchor", false));
   private final BooleanSetting blastGuard = this.addSetting("safe-anchor", new BooleanSetting("Safe Anchor", false));
   private final NumberSetting guardSlot = this.addSetting("guard-slot", new NumberSetting("Guard Slot", 9.0F, 1.0F, 9.0F).step(1.0F));
   private final ModeSetting withoutGuard = this.addSetting("without-guard", new ModeSetting("If Unguarded", "Detonate", "Detonate", "Cancel"));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", false));
   private final NumberSetting switchBackDelay = this.addSetting("switch-back-delay", new NumberSetting("Switch Back Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 0.0F, 0.0F, 3.0F).step(1.0F));
   private SingleAnchorMacroModule.Step step = SingleAnchorMacroModule.Step.IDLE;
   private int tick;
   private int readyAt;
   private int lastClickTick = -1;
   private int clearDeadline;
   private int refillDeadline;
   private int previousSlot = -1;
   private int ownSlot = -1;
   private int cyclesDone;
   private BlockPos anchorPos;
   private ClientLevel activeLevel;
   private AnchorGuard.Spot guard;
   private Item guardItem;
   private Block guardBlock;
   private BlockState guardReported;
   private int guardTries;
   private int guardDeadline;
   private int aimDeadline;
   private boolean aimedAway;
   private int settleDeadline = -1;

   public SingleAnchorMacroModule() {
      super("anchor-macro", "Anchor Macro", "Places, charges and blows up respawn anchors on right-click.", ModuleCategory.COMBAT);
      this.cycleDelay.visibleWhen(() -> this.mode.is("Double"));
      this.placeDelay.visibleWhen(() -> this.mode.is("Double") || this.blastGuard.get());
      this.instantRefill.visibleWhen(() -> this.mode.is("Double"));
      this.guardSlot.visibleWhen(this.blastGuard::get);
      this.withoutGuard.visibleWhen(this.blastGuard::get);
      this.switchBackDelay.visibleWhen(this.switchBack::get);
   }

   @Override
   protected void onEnable() {
      this.stop();
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      if (this.step != SingleAnchorMacroModule.Step.IDLE && mc.player != null && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
         Inventory inventory = mc.player.getInventory();
         if (this.switchBackDue(inventory)) {
            inventory.setSelectedSlot(this.previousSlot);
         }
      }

      this.stop();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.tick++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()) {
         this.stop();
      } else if (mc.gui.screen() != null) {
         if (this.step != SingleAnchorMacroModule.Step.IDLE && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK) {
            this.wrapUp(player.getInventory());
         }
      } else if (!AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
         if (this.step != SingleAnchorMacroModule.Step.IDLE && (this.hotbarMovedByPlayer(player.getInventory()) || mc.level != this.activeLevel)) {
            this.stop();
         }

         if (this.step != SingleAnchorMacroModule.Step.IDLE || this.tryStart(mc, player)) {
            swallowUseKey(mc);
            if (this.step == SingleAnchorMacroModule.Step.PLACE_GUARD) {
               this.aimAtGuard(player);
            }

            for (int i = 0;
               i < 4 && this.step != SingleAnchorMacroModule.Step.IDLE && this.tick >= this.readyAt && (!this.step.click || this.lastClickTick != this.tick);
               i++
            ) {
               this.runStep(mc, player);
            }
         }
      } else {
         this.stop();
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (this.step == SingleAnchorMacroModule.Step.AWAIT_GUARD && this.guard != null) {
         BlockPos spot = this.guard.pos();
         if (event.packet instanceof ClientboundBlockUpdatePacket update) {
            if (update.getPos().equals(spot)) {
               this.guardReported = update.getBlockState();
            }
         } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            section.runUpdates((pos, state) -> {
               if (pos.equals(spot)) {
                  this.guardReported = state;
               }
            });
         }
      }
   }

   private boolean tryStart(Minecraft mc, LocalPlayer player) {
      if (CombatMacroLock.heldByOther(this)) {
         return false;
      } else if (!player.isSpectator() && !player.isHandsBusy() && !player.isUsingItem() && canClick(player) && !mc.gameMode.isDestroying()) {
         if (mc.level.dimension() == Level.NETHER) {
            return false;
         } else if (player.getOffhandItem().is(Items.GLOWSTONE)) {
            return false;
         } else {
            BlockHitResult hit = crosshairBlock(mc);
            if (hit == null) {
               return false;
            } else {
               Inventory inventory = player.getInventory();
               boolean hasGlowstone = findHotbar(inventory, Items.GLOWSTONE) != -1;
               BlockState state = mc.level.getBlockState(hit.getBlockPos());
               boolean onAnchor = state.is(Blocks.RESPAWN_ANCHOR);
               if (onAnchor) {
                  if (chargeOf(state) == 0 && !hasGlowstone) {
                     return false;
                  }
               } else if (!player.getMainHandItem().is(Items.RESPAWN_ANCHOR) || !hasGlowstone) {
                  return false;
               }

               if (takeUsePress(mc) && CombatMacroLock.tryAcquire(this)) {
                  this.previousSlot = inventory.getSelectedSlot();
                  this.ownSlot = -1;
                  this.cyclesDone = 0;
                  this.lastClickTick = -1;
                  this.activeLevel = mc.level;
                  if (onAnchor) {
                     this.targetAnchor(inventory, hit.getBlockPos(), state);
                  } else {
                     this.anchorPos = null;
                     this.scheduleNow(SingleAnchorMacroModule.Step.PLACE);
                  }

                  return true;
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   private static boolean takeUsePress(Minecraft mc) {
      KeyMapping use = mc.options.keyUse;
      return use.consumeClick() || use.isDown();
   }

   private static void swallowUseKey(Minecraft mc) {
      KeyMapping use = mc.options.keyUse;
      use.setDown(false);

      while (use.consumeClick()) {
      }
   }

   private void runStep(Minecraft mc, LocalPlayer player) {
      Inventory inventory = player.getInventory();
      switch (this.step) {
         case SELECT_ANCHOR:
            this.selectThen(inventory, findHotbar(inventory, Items.RESPAWN_ANCHOR), SingleAnchorMacroModule.Step.PLACE, this.placeDelay);
            break;
         case PLACE:
            this.place(mc, player);
            break;
         case SELECT_GLOWSTONE:
            this.selectThen(inventory, findHotbar(inventory, Items.GLOWSTONE), SingleAnchorMacroModule.Step.CHARGE, this.chargeDelay);
            break;
         case CHARGE:
            this.charge(mc, player);
            break;
         case SELECT_GUARD:
            this.selectGuard(mc, player);
            break;
         case PLACE_GUARD:
            this.placeGuard(mc, player);
            break;
         case AWAIT_GUARD:
            this.awaitGuard(mc, player);
            break;
         case SELECT_SAFETY:
            this.selectThen(inventory, this.findSafetySlot(inventory), SingleAnchorMacroModule.Step.DETONATE, this.explodeDelay);
            break;
         case DETONATE:
            this.detonate(mc, player);
            break;
         case SELECT_REFILL:
            this.selectThen(inventory, findHotbar(inventory, Items.RESPAWN_ANCHOR), SingleAnchorMacroModule.Step.QUICK_DETONATE, this.explodeDelay);
            break;
         case QUICK_DETONATE:
            this.quickDetonate(mc, player);
            break;
         case AWAIT_REFILL:
            this.awaitRefill(mc, player);
            break;
         case AWAIT_CLEAR:
            this.awaitClear(mc, player);
            break;
         case SWITCH_BACK:
            if (this.switchBackDue(inventory)) {
               inventory.setSelectedSlot(this.previousSlot);
            }

            this.stop();
            break;
         default:
            this.stop();
      }
   }

   private void selectThen(Inventory inventory, int slot, SingleAnchorMacroModule.Step next, NumberSetting delay) {
      if (slot == -1) {
         this.wrapUp(inventory);
      } else {
         if (inventory.getSelectedSlot() == slot) {
            this.scheduleNow(next);
         } else {
            inventory.setSelectedSlot(slot);
            this.ownSlot = slot;
            this.schedule(next, delay);
         }
      }
   }

   private void place(Minecraft mc, LocalPlayer player) {
      BlockHitResult hit = crosshairBlock(mc);
      if (hit != null && canClick(player)) {
         BlockState aimed = mc.level.getBlockState(hit.getBlockPos());
         if (aimed.is(Blocks.RESPAWN_ANCHOR)) {
            this.targetAnchor(player.getInventory(), hit.getBlockPos(), aimed);
         } else if (!player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
            this.wrapUp(player.getInventory());
         } else {
            this.click(mc, player, hit);
            this.anchorPos = placedAnchor(mc.level, hit);
            if (this.anchorPos == null) {
               this.wrapUp(player.getInventory());
            } else {
               this.schedule(SingleAnchorMacroModule.Step.SELECT_GLOWSTONE, this.switchDelay);
            }
         }
      } else {
         this.wrapUp(player.getInventory());
      }
   }

   private void charge(Minecraft mc, LocalPlayer player) {
      BlockHitResult hit = this.anchorUnderCrosshair(mc);
      if (hit != null && canClick(player)) {
         if (chargeOf(mc.level.getBlockState(this.anchorPos)) == 0) {
            if (!player.getMainHandItem().is(Items.GLOWSTONE)) {
               this.wrapUp(player.getInventory());
               return;
            }

            this.click(mc, player, hit);
         }

         this.schedule(this.afterCharge(player.getInventory()), this.switchDelay);
      } else {
         this.wrapUp(player.getInventory());
      }
   }

   private void selectGuard(Minecraft mc, LocalPlayer player) {
      Inventory inventory = player.getInventory();
      if (chargeOf(mc.level.getBlockState(this.anchorPos)) == 0) {
         this.wrapUp(inventory);
      } else {
         AnchorGuard.Spot spot = AnchorGuard.find(mc.level, player, this.anchorPos, this.sightline(mc));
         int slot = this.guardSlotIn(inventory);
         if (spot != null && spot.standing()) {
            this.guarded(inventory);
         } else if (spot != null && slot != -1 && !player.isPassenger()) {
            this.guard = spot;
            this.guardItem = inventory.getItem(slot).getItem();
            this.guardBlock = Block.byItem(this.guardItem);
            this.guardTries = 0;
            this.selectThen(inventory, slot, SingleAnchorMacroModule.Step.PLACE_GUARD, this.placeDelay);
            this.aimDeadline = this.readyAt + 5;
            this.aimAtGuard(player);
         } else {
            this.skipGuard(inventory);
         }
      }
   }

   private void placeGuard(Minecraft mc, LocalPlayer player) {
      Inventory inventory = player.getInventory();
      if (canClick(player) && chargeOf(mc.level.getBlockState(this.anchorPos)) != 0) {
         AnchorGuard.Spot spot = AnchorGuard.find(mc.level, player, this.anchorPos, this.sightline(mc));
         if (spot != null && spot.standing()) {
            this.guarded(inventory);
         } else if (spot != null && !player.isPassenger() && player.getMainHandItem().is(this.guardItem)) {
            this.guard = spot;
            Rotation sent = ServerRotation.sent();
            BlockHitResult hit = sent == null ? null : AnchorGuard.hitAlong(mc.level, player, sent, spot);
            if (hit == null) {
               if (this.tick >= this.aimDeadline) {
                  this.skipGuard(inventory);
               } else {
                  this.aimAtGuard(player);
                  this.readyAt = this.tick + 1;
               }
            } else {
               ServerRotation.request(sent);
               this.guardReported = null;
               this.click(mc, player, hit);
               this.guardTries++;
               this.step = SingleAnchorMacroModule.Step.AWAIT_GUARD;
               this.readyAt = this.tick + 1;
               this.guardDeadline = this.tick + Crystals.confirmWindow(mc, player);
            }
         } else {
            this.skipGuard(inventory);
         }
      } else {
         this.wrapUp(inventory);
      }
   }

   private void awaitGuard(Minecraft mc, LocalPlayer player) {
      Inventory inventory = player.getInventory();
      if (this.guard == null) {
         this.skipGuard(inventory);
      } else {
         boolean shown = mc.level.getBlockState(this.guard.pos()).is(this.guardBlock);
         if (shown && this.guardReported != null && this.guardReported.is(this.guardBlock)) {
            this.guarded(inventory);
         } else if (!shown) {
            if (this.guardTries < 2) {
               this.step = SingleAnchorMacroModule.Step.PLACE_GUARD;
               this.readyAt = this.tick + 1;
               this.aimDeadline = this.readyAt + 5;
            } else {
               this.skipGuard(inventory);
            }
         } else if (this.tick >= this.guardDeadline) {
            this.skipGuard(inventory);
         } else {
            this.readyAt = this.tick + 1;
         }
      }
   }

   private void guarded(Inventory inventory) {
      this.guard = null;
      this.scheduleNow(this.detonationStep(inventory));
   }

   private void skipGuard(Inventory inventory) {
      this.guard = null;
      if (this.withoutGuard.is("Cancel")) {
         this.wrapUp(inventory);
      } else {
         this.scheduleNow(this.detonationStep(inventory));
      }
   }

   private void aimAtGuard(LocalPlayer player) {
      if (this.guard != null && !this.guard.standing()) {
         ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), this.guard.aim()));
         this.aimedAway = true;
      }
   }

   private void detonate(Minecraft mc, LocalPlayer player) {
      BlockHitResult hit = this.anchorUnderCrosshair(mc);
      if (hit == null
         || !canClick(player)
         || chargeOf(mc.level.getBlockState(this.anchorPos)) == 0
         || player.getMainHandItem().is(Items.GLOWSTONE)
         || player.getOffhandItem().is(Items.GLOWSTONE)) {
         this.wrapUp(player.getInventory());
      } else if (this.lookSettled(mc, player)) {
         this.click(mc, player, hit);
         this.cyclesDone++;
         if (this.mode.is("Double") && this.cyclesDone < 2) {
            this.schedule(SingleAnchorMacroModule.Step.AWAIT_CLEAR, this.cycleDelay);
            this.clearDeadline = this.readyAt + 20;
         } else {
            this.wrapUp(player.getInventory());
         }
      }
   }

   private void quickDetonate(Minecraft mc, LocalPlayer player) {
      BlockHitResult hit = this.anchorUnderCrosshair(mc);
      if (hit == null
         || !canClick(player)
         || chargeOf(mc.level.getBlockState(this.anchorPos)) == 0
         || !player.getMainHandItem().is(Items.RESPAWN_ANCHOR)
         || player.getOffhandItem().is(Items.GLOWSTONE)) {
         this.wrapUp(player.getInventory());
      } else if (this.lookSettled(mc, player)) {
         this.click(mc, player, hit);
         this.click(mc, player, hit);
         player.swing(InteractionHand.MAIN_HAND);
         this.cyclesDone++;
         this.step = SingleAnchorMacroModule.Step.AWAIT_REFILL;
         this.readyAt = this.tick + 1;
         this.refillDeadline = this.tick + Crystals.confirmWindow(mc, player);
      }
   }

   private void awaitRefill(Minecraft mc, LocalPlayer player) {
      BlockState state = mc.level.getBlockState(this.anchorPos);
      boolean anchor = state.is(Blocks.RESPAWN_ANCHOR);
      if (anchor && chargeOf(state) == 0) {
         this.schedule(SingleAnchorMacroModule.Step.SELECT_GLOWSTONE, this.switchDelay);
      } else if (this.tick < this.refillDeadline) {
         this.readyAt = this.tick + 1;
      } else if (anchor) {
         this.wrapUp(player.getInventory());
      } else {
         this.scheduleNow(SingleAnchorMacroModule.Step.AWAIT_CLEAR);
         this.clearDeadline = this.tick + 20;
      }
   }

   private boolean lookSettled(Minecraft mc, LocalPlayer player) {
      if (this.aimedAway && !player.isPassenger()) {
         Rotation sent = ServerRotation.sent();
         if (sent != null && AnchorGuard.looksAt(mc.level, player, sent, this.anchorPos)) {
            this.aimedAway = false;
            this.settleDeadline = -1;
            return true;
         } else {
            if (this.settleDeadline < 0) {
               this.settleDeadline = this.tick + 5;
            }

            if (this.tick >= this.settleDeadline) {
               this.wrapUp(player.getInventory());
            } else {
               this.readyAt = this.tick + 1;
            }

            return false;
         }
      } else {
         return true;
      }
   }

   private void awaitClear(Minecraft mc, LocalPlayer player) {
      if (mc.level.getBlockState(this.anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
         if (this.tick >= this.clearDeadline) {
            this.wrapUp(player.getInventory());
         } else {
            this.readyAt = this.tick + 1;
         }
      } else {
         BlockHitResult hit = crosshairBlock(mc);
         if (hit == null) {
            this.wrapUp(player.getInventory());
         } else {
            Inventory inventory = player.getInventory();
            boolean hasGlowstone = findHotbar(inventory, Items.GLOWSTONE) != -1;
            BlockState aimed = mc.level.getBlockState(hit.getBlockPos());
            if (aimed.is(Blocks.RESPAWN_ANCHOR)) {
               if (chargeOf(aimed) <= 0 && !hasGlowstone) {
                  this.wrapUp(inventory);
               } else {
                  this.targetAnchor(inventory, hit.getBlockPos(), aimed);
               }
            } else if (hasGlowstone && findHotbar(inventory, Items.RESPAWN_ANCHOR) != -1) {
               this.anchorPos = null;
               this.scheduleNow(SingleAnchorMacroModule.Step.SELECT_ANCHOR);
            } else {
               this.wrapUp(inventory);
            }
         }
      }
   }

   private void targetAnchor(Inventory inventory, BlockPos pos, BlockState state) {
      this.anchorPos = pos.immutable();
      this.scheduleNow(chargeOf(state) > 0 ? this.afterCharge(inventory) : SingleAnchorMacroModule.Step.SELECT_GLOWSTONE);
   }

   private SingleAnchorMacroModule.Step afterCharge(Inventory inventory) {
      return this.blastGuard.get() ? SingleAnchorMacroModule.Step.SELECT_GUARD : this.detonationStep(inventory);
   }

   private SingleAnchorMacroModule.Step detonationStep(Inventory inventory) {
      boolean refill = this.instantRefill.get()
         && this.mode.is("Double")
         && this.cyclesDone == 0
         && findHotbar(inventory, Items.RESPAWN_ANCHOR) != -1
         && findHotbar(inventory, Items.GLOWSTONE) != -1;
      return refill ? SingleAnchorMacroModule.Step.SELECT_REFILL : SingleAnchorMacroModule.Step.SELECT_SAFETY;
   }

   private void wrapUp(Inventory inventory) {
      if (this.switchBackDue(inventory)) {
         this.schedule(SingleAnchorMacroModule.Step.SWITCH_BACK, this.switchBackDelay);
      } else {
         this.stop();
      }
   }

   private boolean switchBackDue(Inventory inventory) {
      return this.switchBack.get()
         && Inventory.isHotbarSlot(this.previousSlot)
         && this.ownSlot != -1
         && this.ownSlot != this.previousSlot
         && inventory.getSelectedSlot() == this.ownSlot;
   }

   private boolean hotbarMovedByPlayer(Inventory inventory) {
      return inventory.getSelectedSlot() != (this.ownSlot != -1 ? this.ownSlot : this.previousSlot);
   }

   private void stop() {
      this.step = SingleAnchorMacroModule.Step.IDLE;
      this.anchorPos = null;
      this.activeLevel = null;
      this.previousSlot = -1;
      this.ownSlot = -1;
      this.cyclesDone = 0;
      this.guard = null;
      this.guardItem = null;
      this.guardBlock = null;
      this.guardReported = null;
      this.guardTries = 0;
      this.aimedAway = false;
      this.settleDeadline = -1;
      CombatMacroLock.release(this);
   }

   private void schedule(SingleAnchorMacroModule.Step next, NumberSetting delay) {
      this.step = next;
      int spread = this.jitter.getInt();
      this.readyAt = this.tick + delay.getInt() + (spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0);
   }

   private void scheduleNow(SingleAnchorMacroModule.Step next) {
      this.step = next;
      this.readyAt = this.tick;
   }

   private void click(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
      ItemStack held = player.getMainHandItem();
      int countBefore = held.getCount();
      if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit) instanceof Success success && success.swingSource() == SwingSource.CLIENT) {
         player.swing(InteractionHand.MAIN_HAND);
         if (!held.isEmpty() && (held.getCount() != countBefore || player.hasInfiniteMaterials())) {
            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
         }
      }

      this.lastClickTick = this.tick;
   }

   private static boolean canClick(LocalPlayer player) {
      return !player.isSecondaryUseActive();
   }

   private static BlockHitResult crosshairBlock(Minecraft mc) {
      return mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK ? hit : null;
   }

   private BlockHitResult anchorUnderCrosshair(Minecraft mc) {
      BlockHitResult hit = crosshairBlock(mc);
      if (hit != null && this.anchorPos != null && hit.getBlockPos().equals(this.anchorPos)) {
         return mc.level.getBlockState(this.anchorPos).is(Blocks.RESPAWN_ANCHOR) ? hit : null;
      } else {
         return null;
      }
   }

   private Vec3 sightline(Minecraft mc) {
      BlockHitResult hit = this.anchorUnderCrosshair(mc);
      return hit != null ? hit.getLocation() : Vec3.atCenterOf(this.anchorPos);
   }

   private static BlockPos placedAnchor(ClientLevel level, BlockHitResult hit) {
      BlockPos clicked = hit.getBlockPos();
      if (level.getBlockState(clicked).is(Blocks.RESPAWN_ANCHOR)) {
         return clicked.immutable();
      } else {
         BlockPos onFace = clicked.relative(hit.getDirection());
         return level.getBlockState(onFace).is(Blocks.RESPAWN_ANCHOR) ? onFace : null;
      }
   }

   private static int chargeOf(BlockState state) {
      return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
   }

   private int findSafetySlot(Inventory inventory) {
      int configured = Math.max(0, Math.min(8, this.safetySlot.getInt() - 1));
      if (!inventory.getItem(configured).is(Items.GLOWSTONE)) {
         return configured;
      } else {
         int totem = findHotbar(inventory, Items.TOTEM_OF_UNDYING);
         if (totem != -1) {
            return totem;
         } else {
            for (int i = 0; i < 9; i++) {
               if (!inventory.getItem(i).is(Items.GLOWSTONE)) {
                  return i;
               }
            }

            return -1;
         }
      }
   }

   private int guardSlotIn(Inventory inventory) {
      int obsidian = findHotbar(inventory, Items.OBSIDIAN);
      if (obsidian != -1) {
         return obsidian;
      } else {
         int configured = Math.max(0, Math.min(8, this.guardSlot.getInt() - 1));
         ItemStack chosen = inventory.getItem(configured);
         if (!chosen.is(Items.GLOWSTONE) && AnchorGuard.isGuardBlock(chosen)) {
            return configured;
         } else {
            int glowstone = findHotbar(inventory, Items.GLOWSTONE);
            return glowstone != -1 && countHotbar(inventory, Items.GLOWSTONE) > this.chargesStillNeeded() ? glowstone : -1;
         }
      }
   }

   private int chargesStillNeeded() {
      return this.mode.is("Double") && this.cyclesDone == 0 ? 1 : 0;
   }

   private static int findHotbar(Inventory inventory, Item item) {
      for (int i = 0; i < 9; i++) {
         if (inventory.getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   private static int countHotbar(Inventory inventory, Item item) {
      int count = 0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @Environment(EnvType.CLIENT)
   private static enum Step {
      IDLE(false),
      SELECT_ANCHOR(false),
      PLACE(true),
      SELECT_GLOWSTONE(false),
      CHARGE(true),
      SELECT_GUARD(false),
      PLACE_GUARD(true),
      AWAIT_GUARD(false),
      SELECT_SAFETY(false),
      DETONATE(true),
      SELECT_REFILL(false),
      QUICK_DETONATE(true),
      AWAIT_REFILL(false),
      AWAIT_CLEAR(false),
      SWITCH_BACK(false);

      final boolean click;

      private Step(boolean click) {
         this.click = click;
      }
   }
}
