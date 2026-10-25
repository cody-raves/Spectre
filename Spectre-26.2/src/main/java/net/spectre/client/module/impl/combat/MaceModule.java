package net.spectre.client.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class MaceModule extends ClientModule {
   private static final String PREFER_DENSITY = "Density";
   private static final String PREFER_BREACH = "Breach";
   private static final String PREFER_WIND_BURST = "Wind Burst";
   private static final String PREFER_ANY = "Any";
   private static final String SMASH_DIRECT = "Direct";
   private static final String SMASH_DELAYED = "Delayed";
   private static final double REACH_MARGIN = 0.35;
   private static final int PREFERRED_WEIGHT = 100;
   private final BooleanSetting autoSwap = this.addSetting("auto-swap", new BooleanSetting("Auto Swap", true));
   private final ModeSetting prefer = this.addSetting("prefer", new ModeSetting("Prefer", "Density", "Density", "Breach", "Wind Burst", "Any"));
   private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("From Weapons Only", true));
   private final BooleanSetting swapBack = this.addSetting("swap-back", new BooleanSetting("Swap Back", true));
   private final NumberSetting backDelay = this.addSetting("back-delay", new NumberSetting("Back Delay", 2.0F, 1.0F, 20.0F).step(1.0F));
   private final BooleanSetting smashAssist = this.addSetting("smash-assist", new BooleanSetting("Smash Assist", false));
   private final ModeSetting smashMode = this.addSetting("smash-mode", new ModeSetting("Smash Mode", "Direct", "Direct", "Delayed"));
   private final NumberSetting minFall = this.addSetting("min-fall", new NumberSetting("Min Fall (blocks)", 4.0F, 2.0F, 30.0F).step(1.0F));
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
   private final BooleanSetting passives = this.addSetting("passives", new BooleanSetting("Passives", false));
   private final BooleanSetting invisible = this.addSetting("invisible", new BooleanSetting("Invisible", false));
   private final BooleanSetting skipFriends = this.addSetting("skip-friends", new BooleanSetting("Skip Friends", true));
   private int ticks;
   private int maceSlot = -1;
   private int returnSlot = -1;
   private int backTick;

   public MaceModule() {
      super("mace", "Mace", "Swaps to a mace for smash hits while falling, with an optional smash assist.", ModuleCategory.COMBAT);
      this.prefer.visibleWhen(this.autoSwap::get);
      this.weaponsOnly.visibleWhen(this.autoSwap::get);
      this.swapBack.visibleWhen(this.autoSwap::get);
      this.backDelay.visibleWhen(() -> this.autoSwap.get() && this.swapBack.get());
      this.smashMode.visibleWhen(this.smashAssist::get);
      this.minFall.visibleWhen(this.smashAssist::get);
      this.players.visibleWhen(this.smashAssist::get);
      this.hostiles.visibleWhen(this.smashAssist::get);
      this.passives.visibleWhen(this.smashAssist::get);
      this.invisible.visibleWhen(this.smashAssist::get);
      this.skipFriends.visibleWhen(this.smashAssist::get);
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
      this.maceSlot = -1;
      this.returnSlot = -1;
   }

   @Override
   protected void onDisable() {
      this.endSwap(this.swapBack.get());
   }

   public void beforeAttack(Player attacker, Entity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && attacker == player && target instanceof LivingEntity living) {
         int slot = this.swapSlotFor(player, living);
         if (slot >= 0 && CombatMacroLock.tryAcquire(this)) {
            this.returnSlot = player.getInventory().getSelectedSlot();
            this.maceSlot = slot;
            MeleeAttacks.select(player, slot);
            if (this.swapBack.get()) {
               this.backTick = this.ticks + Math.max(1, this.backDelay.getInt());
            } else {
               this.maceSlot = -1;
               this.returnSlot = -1;
               CombatMacroLock.release(this);
            }
         }
      }
   }

   private int swapSlotFor(LocalPlayer player, LivingEntity target) {
      if (this.autoSwap.get() && MaceItem.canSmashAttack(player) && !AutoInvTotemModule.wantsHotbar()) {
         ItemStack held = player.getMainHandItem();
         if (held.is(Items.MACE)) {
            return -1;
         } else if (this.weaponsOnly.get() && !MeleeAttacks.isMeleeWeapon(held)) {
            return -1;
         } else {
            return !MeleeAttacks.shieldBlocks(target, player) && !CombatMacroLock.heldByOther(this) ? this.bestMace(player) : -1;
         }
      } else {
         return -1;
      }
   }

   private int bestMace(LocalPlayer player) {
      ResourceKey<Enchantment> preferred = this.preferredEnchantment();
      Inventory inventory = player.getInventory();
      int best = -1;
      int bestScore = -1;

      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.is(Items.MACE) && !stack.nextDamageWillBreak()) {
            int score = 0;

            for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
               boolean wanted = preferred != null && ((Holder)entry.getKey()).is(preferred);
               score += wanted ? entry.getIntValue() * 100 : entry.getIntValue();
            }

            if (score > bestScore) {
               best = slot;
               bestScore = score;
            }
         }
      }

      return best;
   }

   private ResourceKey<Enchantment> preferredEnchantment() {
      String var1 = this.prefer.get();

      return switch (var1) {
         case "Density" -> Enchantments.DENSITY;
         case "Breach" -> Enchantments.BREACH;
         case "Wind Burst" -> Enchantments.WIND_BURST;
         default -> null;
      };
   }

   private void endSwap(boolean restore) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (restore && player != null && this.maceSlot >= 0 && this.returnSlot >= 0 && player.getInventory().getSelectedSlot() == this.maceSlot) {
         MeleeAttacks.select(player, this.returnSlot);
      }

      this.maceSlot = -1;
      this.returnSlot = -1;
      CombatMacroLock.release(this);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.ticks++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (this.smashAssist.get()) {
            this.trySmash(mc, player);
         }

         if (this.maceSlot >= 0) {
            if (player.getInventory().getSelectedSlot() != this.maceSlot) {
               this.endSwap(false);
            } else if ((this.ticks >= this.backTick || AutoInvTotemModule.wantsHotbar()) && mc.gui.screen() == null && !MeleeAttacks.attackedThisTick(player)) {
               this.endSwap(true);
            }
         }
      } else {
         if (this.maceSlot >= 0) {
            this.endSwap(false);
         }
      }
   }

   private void trySmash(Minecraft mc, LocalPlayer player) {
      if (MeleeAttacks.canAct(mc) && MaceItem.canSmashAttack(player)) {
         if (!(player.fallDistance < this.minFall.get()) && !(player.getDeltaMovement().y >= 0.0)) {
            EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
            if (hit != null && hit.getEntity() instanceof LivingEntity target && this.isSmashTarget(player, target)) {
               boolean maceReady = player.getMainHandItem().is(Items.MACE) || this.swapSlotFor(player, target) >= 0;
               if (maceReady && !(MeleeAttacks.charge(player) <= 0.9F)) {
                  if (!this.smashMode.is("Delayed") || !canHoldOneTick(player, target)) {
                     if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.clickPending(mc)) {
                        MeleeAttacks.attack(mc, hit);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isSmashTarget(LocalPlayer player, LivingEntity target) {
      if (!MeleeAttacks.isHittable(player, target)) {
         return false;
      } else if (target.isInvisible() && !this.invisible.get()) {
         return false;
      } else {
         boolean wanted;
         if (target instanceof Player other) {
            wanted = this.players.get() && (!this.skipFriends.get() || !MeleeAttacks.isFriend(other));
         } else {
            wanted = target instanceof Enemy ? this.hostiles.get() : this.passives.get();
         }

         return wanted && !MeleeAttacks.shieldBlocks(target, player);
      }
   }

   private static boolean canHoldOneTick(LocalPlayer player, LivingEntity target) {
      Vec3 motion = player.getDeltaMovement();
      if (!player.level().noCollision(player, player.getBoundingBox().move(motion))) {
         return false;
      } else {
         double reach = player.entityInteractionRange() - 0.35;
         if (reach <= 0.0) {
            return false;
         } else {
            Vec3 nextEye = player.getEyePosition().add(motion);
            Vec3 targetStep = target.position().subtract(target.xo, target.yo, target.zo);
            AABB nextBox = target.getBoundingBox().move(targetStep).inflate(target.getPickRadius());
            if (nextBox.contains(nextEye)) {
               return true;
            } else {
               Optional<Vec3> aim = nextBox.clip(nextEye, nextEye.add(player.getViewVector(1.0F).scale(reach)));
               return aim.isPresent() && MeleeAttacks.lineIsClear(player, nextEye, aim.get());
            }
         }
      }
   }
}
