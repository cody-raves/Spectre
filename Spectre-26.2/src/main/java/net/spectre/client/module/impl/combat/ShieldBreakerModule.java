package net.spectre.client.module.impl.combat;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

@Environment(EnvType.CLIENT)
public final class ShieldBreakerModule extends ClientModule {
   private static final int LOST_TARGET_TICKS = 4;
   private static final int REARM_TICKS = 10;
   private final NumberSetting hitDelay = this.addSetting("hit-delay", new NumberSetting("Hit Delay", 1.0F, 1.0F, 6.0F).step(1.0F));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final NumberSetting backDelay = this.addSetting("back-delay", new NumberSetting("Back Delay", 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 1.0F, 0.0F, 3.0F).step(1.0F));
   private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("From Weapons Only", true));
   private final BooleanSetting holdAttack = this.addSetting("hold-attack", new BooleanSetting("Hold Attack", false));
   private final BooleanSetting skipFriends = this.addSetting("skip-friends", new BooleanSetting("Skip Friends", true));
   private ShieldBreakerModule.Phase phase = ShieldBreakerModule.Phase.IDLE;
   private int ticks;
   private int axeSlot = -1;
   private int returnSlot = -1;
   private int dueTick;
   private int armSerial;
   private int lostTicks;
   private int rearmTick;

   public ShieldBreakerModule() {
      super("shield-breaker", "Shield Breaker", "Switches to an axe and hits when the targeted player is blocking with a shield.", ModuleCategory.COMBAT);
      this.backDelay.visibleWhen(this.switchBack::get);
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
      this.rearmTick = 0;
      this.phase = ShieldBreakerModule.Phase.IDLE;
      this.axeSlot = -1;
      this.returnSlot = -1;
   }

   @Override
   protected void onDisable() {
      if (this.phase != ShieldBreakerModule.Phase.IDLE) {
         this.finish(this.switchBack.get());
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      this.ticks++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (this.phase != ShieldBreakerModule.Phase.IDLE && AutoInvTotemModule.wantsHotbar()) {
            this.finish(true);
         } else {
            switch (this.phase) {
               case IDLE:
                  this.tryArm(mc, player);
                  break;
               case ARMED:
                  this.tryHit(mc, player);
                  break;
               case RETURNING:
                  if (this.ticks >= this.dueTick && mc.gui.screen() == null) {
                     this.finish(true);
                  }
            }
         }
      } else {
         if (this.phase != ShieldBreakerModule.Phase.IDLE) {
            this.finish(false);
         }
      }
   }

   private void tryArm(Minecraft mc, LocalPlayer player) {
      if (this.ticks >= this.rearmTick && MeleeAttacks.canAct(mc) && !player.isUsingItem()) {
         if (!CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
            boolean clicked = MeleeAttacks.clickPending(mc);
            if (!this.holdAttack.get() || clicked || mc.options.keyAttack.isDown()) {
               ItemStack held = player.getMainHandItem();
               if (!MeleeAttacks.disablesShields(held)) {
                  if (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(held)) {
                     if (this.blockingTarget(MeleeAttacks.crosshairHit(mc), player) != null) {
                        int slot = bestAxe(player);
                        if (slot >= 0 && CombatMacroLock.tryAcquire(this)) {
                           if (clicked) {
                              MeleeAttacks.takeAttackClicks(mc);
                           }

                           this.returnSlot = player.getInventory().getSelectedSlot();
                           this.axeSlot = slot;
                           MeleeAttacks.select(player, slot);
                           this.dueTick = this.ticks + Math.max(1, this.hitDelay.getInt() + MeleeAttacks.spread(this.jitter.getInt()));
                           this.armSerial = MeleeAttacks.attackSerial();
                           this.lostTicks = 0;
                           this.phase = ShieldBreakerModule.Phase.ARMED;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void tryHit(Minecraft mc, LocalPlayer player) {
      if (player.getInventory().getSelectedSlot() != this.axeSlot) {
         this.finish(false);
      } else if (MeleeAttacks.attackSerial() != this.armSerial) {
         this.startReturn(this.backDelayTicks());
      } else if (this.ticks >= this.dueTick) {
         EntityHitResult hit = MeleeAttacks.canAct(mc) ? MeleeAttacks.crosshairHit(mc) : null;
         if (this.blockingTarget(hit, player) == null) {
            if (++this.lostTicks > 4) {
               this.startReturn(0);
            }
         } else if (MeleeAttacks.clickPending(mc)) {
            this.startReturn(this.backDelayTicks());
         } else {
            if (MeleeAttacks.attack(mc, hit)) {
               this.startReturn(this.backDelayTicks());
            } else if (++this.lostTicks > 4) {
               this.startReturn(0);
            }
         }
      }
   }

   private int backDelayTicks() {
      return this.backDelay.getInt() + MeleeAttacks.spread(this.jitter.getInt());
   }

   private void startReturn(int delayTicks) {
      if (!this.switchBack.get()) {
         this.finish(false);
      } else {
         this.phase = ShieldBreakerModule.Phase.RETURNING;
         this.dueTick = this.ticks + Math.max(1, delayTicks);
      }
   }

   private void finish(boolean restore) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (restore && player != null && this.returnSlot >= 0 && player.getInventory().getSelectedSlot() == this.axeSlot) {
         MeleeAttacks.select(player, this.returnSlot);
      }

      this.phase = ShieldBreakerModule.Phase.IDLE;
      this.axeSlot = -1;
      this.returnSlot = -1;
      this.rearmTick = this.ticks + 10;
      CombatMacroLock.release(this);
   }

   private Player blockingTarget(EntityHitResult hit, LocalPlayer player) {
      if (hit == null || !(hit.getEntity() instanceof Player target && MeleeAttacks.isHittable(player, target))) {
         return null;
      } else if (this.skipFriends.get() && MeleeAttacks.isFriend(target)) {
         return null;
      } else {
         return MeleeAttacks.shieldBlocks(target, player) ? target : null;
      }
   }

   private static int bestAxe(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      double bestDamage = Double.NEGATIVE_INFINITY;

      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (MeleeAttacks.disablesShields(stack) && !stack.nextDamageWillBreak()) {
            double damage = MeleeAttacks.attackDamage(stack);
            if (damage > bestDamage) {
               best = slot;
               bestDamage = damage;
            }
         }
      }

      return best;
   }

   @Environment(EnvType.CLIENT)
   private static enum Phase {
      IDLE,
      ARMED,
      RETURNING;
   }
}
