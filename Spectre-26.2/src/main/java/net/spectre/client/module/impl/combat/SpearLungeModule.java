package net.spectre.client.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.KeyInputEvent;
import net.spectre.client.event.impl.MouseInputEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

@Environment(EnvType.CLIENT)
public final class SpearLungeModule extends ClientModule {
   private static final int BUFFER_TICKS = 10;
   private final KeySetting key = this.addSetting("lunge-key", new KeySetting("Lunge Key", -1));
   private final NumberSetting cooldown = this.addSetting("cooldown", new NumberSetting("Cooldown", 12.0F, 0.0F, 40.0F).step(1.0F));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final NumberSetting backDelay = this.addSetting("back-delay", new NumberSetting("Back Delay", 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 1.0F, 0.0F, 4.0F).step(1.0F));
   private int ticks;
   private int readyTick;
   private boolean pending;
   private int pendingTicks;
   private boolean returning;
   private int spearSlot = -1;
   private int returnSlot = -1;
   private int returnTick;
   private LocalPlayer owner;

   public SpearLungeModule() {
      super("spear-swap", "Spear Swap", "Swaps to your Lunge spear, lunges and swaps back.", ModuleCategory.COMBAT);
      this.backDelay.visibleWhen(this.switchBack::get);
      this.jitter.visibleWhen(this.switchBack::get);
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.owner = Minecraft.getInstance().player;
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onTick);
   }

   @Override
   protected void onDisable() {
      if (this.returning) {
         this.finishReturn(this.switchBack.get());
      }

      this.reset();
   }

   private void reset() {
      this.pending = false;
      this.pendingTicks = 0;
      this.returning = false;
      this.spearSlot = -1;
      this.returnSlot = -1;
      this.readyTick = this.ticks;
      this.owner = null;
      CombatMacroLock.release(this);
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui.screen() == null && mc.player != null && mc.level != null) {
            if (!this.returning && !this.pending && this.ticks >= this.readyTick) {
               this.pending = true;
               this.pendingTicks = 0;
            }
         }
      }
   }

   private void onTick(TickEvent event) {
      this.ticks++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != this.owner) {
         this.reset();
         this.owner = player;
      }

      if (player != null && mc.level != null && mc.gameMode != null) {
         if (this.returning) {
            this.followReturn(mc, player);
         } else if (this.pending) {
            this.tryLunge(mc, player);
         }
      } else {
         if (this.returning || this.pending) {
            this.reset();
         }
      }
   }

   private void followReturn(Minecraft mc, LocalPlayer player) {
      if (player.getInventory().getSelectedSlot() != this.spearSlot) {
         this.finishReturn(false);
      } else {
         if ((this.ticks >= this.returnTick || AutoInvTotemModule.wantsHotbar()) && mc.gui.screen() == null) {
            this.finishReturn(true);
         }
      }
   }

   private void finishReturn(boolean restore) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (restore && player != null && this.returnSlot >= 0 && player.getInventory().getSelectedSlot() == this.spearSlot) {
         MeleeAttacks.select(player, this.returnSlot);
      }

      this.returning = false;
      this.spearSlot = -1;
      this.returnSlot = -1;
      CombatMacroLock.release(this);
   }

   private void tryLunge(Minecraft mc, LocalPlayer player) {
      if (++this.pendingTicks > 10) {
         this.pending = false;
      } else if (this.canLunge(mc, player)) {
         Inventory inventory = player.getInventory();
         int slot = bestSpear(inventory);
         if (slot < 0) {
            this.pending = false;
         } else {
            ItemStack spear = inventory.getItem(slot);
            PiercingWeapon weapon = spear.get(DataComponents.PIERCING_WEAPON);
            if (weapon != null && canJab(mc, player, spear) && CombatMacroLock.tryAcquire(this)) {
               this.pending = false;
               int previous = inventory.getSelectedSlot();
               MeleeAttacks.select(player, slot);
               mc.gameMode.piercingAttack(weapon);
               player.swing(InteractionHand.MAIN_HAND);
               this.readyTick = this.ticks + this.cooldown.getInt();
               if (previous != slot && this.switchBack.get()) {
                  this.returning = true;
                  this.spearSlot = slot;
                  this.returnSlot = previous;
                  this.returnTick = this.ticks + Math.max(1, this.backDelay.getInt() + MeleeAttacks.spread(this.jitter.getInt()));
               } else {
                  CombatMacroLock.release(this);
               }
            }
         }
      }
   }

   private boolean canLunge(Minecraft mc, LocalPlayer player) {
      if (!MeleeAttacks.canAct(mc) || mc.gameMode.isSpectator()) {
         return false;
      } else if (CombatMacroLock.heldByOther(this) || AutoInvTotemModule.wantsHotbar()) {
         return false;
      } else {
         return !player.isUsingItem() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()
            ? player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly
            : false;
      }
   }

   private static boolean canJab(Minecraft mc, LocalPlayer player, ItemStack spear) {
      return mc.missTime <= 0 && !player.isHandsBusy() ? spear.isItemEnabled(mc.level.enabledFeatures()) && !player.cannotAttackWithItem(spear, 0) : false;
   }

   private static int bestSpear(Inventory inventory) {
      int selected = inventory.getSelectedSlot();
      int best = -1;
      int bestLevel = 0;

      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.has(DataComponents.PIERCING_WEAPON) && !stack.nextDamageWillBreak()) {
            int level = lungeLevel(stack);
            if (level > bestLevel || level > 0 && level == bestLevel && slot == selected) {
               best = slot;
               bestLevel = level;
            }
         }
      }

      return best;
   }

   private static int lungeLevel(ItemStack stack) {
      for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
         if (((Holder)entry.getKey()).is(Enchantments.LUNGE)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }
}
