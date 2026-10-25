package net.spectre.client.module.impl.misc;

import java.util.Set;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class AutoEatModule extends ClientModule {
   private static final Set<Item> NEVER_EAT = Set.of(
      Items.ROTTEN_FLESH,
      Items.SPIDER_EYE,
      Items.POISONOUS_POTATO,
      Items.PUFFERFISH,
      Items.CHICKEN,
      Items.CHORUS_FRUIT,
      Items.SUSPICIOUS_STEW,
      Items.ENCHANTED_GOLDEN_APPLE
   );
   private static final int NOT_FOUND = -1;
   private static final int OFFHAND_SLOT = -2;
   private static AutoEatModule instance;
   private final NumberSetting hunger = this.addSetting("hunger", new NumberSetting("Hunger", 16.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting health = this.addSetting("health", new NumberSetting("Health", 10.0F, 0.0F, 20.0F).step(1.0F));
   private boolean eating;
   private int previousSlot;

   public AutoEatModule() {
      super("auto-eat", "Auto Eat", "Eats hotbar or offhand food once hunger or health drops too low.", ModuleCategory.MISC);
      instance = this;
   }

   @Override
   protected void onDisable() {
      this.stop(Minecraft.getInstance());
   }

   public static boolean isEating() {
      AutoEatModule module = instance;
      return module != null && module.isEnabled() && module.eating;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
         this.stop(mc);
      } else if (this.eating || !player.isUsingItem()) {
         if (this.eating && player.isUsingItem()) {
            mc.options.keyUse.setDown(true);
         } else if (mc.gameMode.isDestroying()) {
            this.stop(mc);
         } else if (!this.shouldEat(player)) {
            this.stop(mc);
         } else {
            int slot = this.findBestSlot(player);
            if (slot == -1) {
               this.stop(mc);
            } else {
               if (!this.eating) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
                  this.eating = true;
               }

               if (slot == -2) {
                  mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
               } else {
                  player.getInventory().setSelectedSlot(slot);
               }

               mc.options.keyUse.setDown(true);
            }
         }
      }
   }

   private boolean shouldEat(LocalPlayer player) {
      boolean hungry = player.getFoodData().getFoodLevel() <= this.hunger.getInt();
      boolean hurt = this.health.getInt() > 0 && player.getHealth() <= this.health.getInt();
      return hungry || hurt;
   }

   private int findBestSlot(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      float bestScore = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         FoodProperties food = foodOf(player, inventory.getItem(slot));
         if (food != null) {
            float score = food.nutrition() + food.saturation();
            if (score > bestScore) {
               bestScore = score;
               best = slot;
            }
         }
      }

      FoodProperties offhandFood = foodOf(player, player.getOffhandItem());
      if (offhandFood != null && offhandFood.nutrition() + offhandFood.saturation() > bestScore) {
         best = -2;
      }

      return best;
   }

   private static FoodProperties foodOf(LocalPlayer player, ItemStack stack) {
      if (!stack.isEmpty() && !NEVER_EAT.contains(stack.getItem())) {
         FoodProperties food = stack.get(DataComponents.FOOD);
         return food != null && player.canEat(food.canAlwaysEat()) ? food : null;
      } else {
         return null;
      }
   }

   private void stop(Minecraft mc) {
      if (this.eating) {
         this.eating = false;
         mc.options.keyUse.setDown(false);
         if (mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.previousSlot);
         }
      }
   }
}
