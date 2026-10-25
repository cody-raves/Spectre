package net.spectre.client.module.impl.misc;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.util.antivanish.AntiVanishText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class LegitAutoToolModule extends ClientModule {
   private static final int SWITCH_DELAY_TICKS = 2;
   private static final String SHARD_NAME = "shard pickaxe";
   private static final String SHARD_LORE = "breaks 9 blocks at once";
   private final BooleanSetting drill = this.addSetting("use-drill-for-instabreak", new BooleanSetting("Drill Instabreak", true));
   private final BooleanSetting delayed = this.addSetting("delay", new BooleanSetting("Delay", true));
   private BlockPos target;
   private int pending = -1;
   private int delay;
   private int origin = -1;
   private int ourSlot = -1;
   private int returnDelay;

   public LegitAutoToolModule() {
      super("legit-auto-tool", "Legit Auto Tool", "Auto tool that only switches hotbar slots like a player.", ModuleCategory.MISC);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      if (this.origin != -1 && Minecraft.getInstance().player != null) {
         this.select(this.origin);
      }

      this.reset();
   }

   private void reset() {
      this.target = null;
      this.pending = -1;
      this.delay = 0;
      this.origin = -1;
      this.ourSlot = -1;
      this.returnDelay = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         int selected = player.getInventory().getSelectedSlot();
         if (this.origin != -1 && selected != this.ourSlot) {
            this.origin = -1;
         }

         BlockPos aimed = mc.gui.screen() == null && mc.options.keyAttack.isDown() ? aimedBlock(mc) : null;
         BlockState state = aimed == null ? null : mc.level.getBlockState(aimed);
         if (aimed != null && !state.isAir()) {
            if (!aimed.equals(this.target)) {
               this.target = aimed.immutable();
               this.pending = -1;
               this.delay = 0;
            }

            int shard = shardSlot(player.getInventory(), state, selected);
            if (this.drill.get()) {
               int drillSlot = this.drillSlot(mc, state, aimed, shard);
               if (drillSlot != -1) {
                  if (drillSlot != selected) {
                     this.select(drillSlot);
                  }

                  this.pending = -1;
                  return;
               }
            }

            int best = this.bestSlot(mc, state, aimed, selected, shard);
            if (best == selected) {
               this.pending = -1;
            } else {
               if (this.pending != best) {
                  this.pending = best;
                  this.delay = this.switchDelay();
               }

               if (this.delay > 0) {
                  this.delay--;
               } else {
                  this.select(best);
                  this.pending = -1;
               }
            }
         } else {
            this.target = null;
            this.pending = -1;
            this.delay = 0;
            this.giveBack();
         }
      } else {
         this.reset();
      }
   }

   private static BlockPos aimedBlock(Minecraft mc) {
      return mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
   }

   private int drillSlot(Minecraft mc, BlockState state, BlockPos pos, int shard) {
      if (shard != -1) {
         return breakingDelta(mc, shard, state, pos) >= 1.0F ? shard : -1;
      } else {
         Inventory inventory = mc.player.getInventory();

         for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.NETHERITE_PICKAXE) && !stack.nextDamageWillBreak() && breakingDelta(mc, slot, state, pos) >= 1.0F) {
               return slot;
            }
         }

         return -1;
      }
   }

   private int bestSlot(Minecraft mc, BlockState state, BlockPos pos, int selected, int shard) {
      Inventory inventory = mc.player.getInventory();
      ItemStack held = inventory.getItem(selected);
      boolean dropHeld = selected == this.ourSlot && this.origin != -1 && held.nextDamageWillBreak();
      int best = selected;
      float bestDelta = dropHeld ? -1.0F : breakingDelta(mc, selected, state, pos);

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (slot != selected && !stack.isEmpty() && !stack.nextDamageWillBreak()) {
            float delta = breakingDelta(mc, slot, state, pos);
            if (delta > bestDelta) {
               bestDelta = delta;
               best = slot;
            }
         }
      }

      return shard != -1 && inventory.getItem(best).is(ItemTags.PICKAXES) ? shard : best;
   }

   private static int shardSlot(Inventory inventory, BlockState state, int selected) {
      if (isShardFor(inventory.getItem(selected), state)) {
         return selected;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && isShardFor(inventory.getItem(slot), state)) {
               return slot;
            }
         }

         return -1;
      }
   }

   private static boolean isShardFor(ItemStack stack, BlockState state) {
      return !stack.nextDamageWillBreak() && stack.isCorrectToolForDrops(state) && isShardPickaxe(stack);
   }

   private static boolean isShardPickaxe(ItemStack stack) {
      if (!stack.is(ItemTags.PICKAXES)) {
         return false;
      } else if (AntiVanishText.normalize(stack.getHoverName().getString()).contains("shard pickaxe")) {
         return true;
      } else {
         for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            if (AntiVanishText.normalize(line.getString()).contains("breaks 9 blocks at once")) {
               return true;
            }
         }

         return false;
      }
   }

   private static float breakingDelta(Minecraft mc, int slot, BlockState state, BlockPos pos) {
      LocalPlayer player = mc.player;
      Inventory inventory = player.getInventory();
      int previous = inventory.getSelectedSlot();
      inventory.setSelectedSlot(slot);

      float progress;
      try {
         progress = state.getDestroyProgress(player, mc.level, pos);
      } finally {
         inventory.setSelectedSlot(previous);
      }

      ItemStack stack = inventory.getItem(slot);
      float base = stack.getDestroySpeed(state);
      if (base > 1.0F && progress > 0.0F) {
         float synced = (float)player.getAttributeValue(Attributes.MINING_EFFICIENCY);
         double[] own = new double[]{0.0};
         stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute == Attributes.MINING_EFFICIENCY) {
               own[0] += modifier.amount();
            }
         });
         progress *= (base + (float)own[0]) / (base + synced);
      }

      return progress;
   }

   private void select(int slot) {
      Inventory inventory = Minecraft.getInstance().player.getInventory();
      if (this.origin == -1) {
         this.origin = inventory.getSelectedSlot();
      }

      inventory.setSelectedSlot(slot);
      this.ourSlot = slot;
      this.returnDelay = this.switchDelay();
   }

   private int switchDelay() {
      return this.delayed.get() ? 2 : 0;
   }

   private void giveBack() {
      if (this.origin != -1) {
         if (this.returnDelay > 0) {
            this.returnDelay--;
         } else {
            this.select(this.origin);
            this.origin = -1;
         }
      }
   }
}
