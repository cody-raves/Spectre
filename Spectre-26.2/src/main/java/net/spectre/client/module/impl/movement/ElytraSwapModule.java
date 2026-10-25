package net.spectre.client.module.impl.movement;

import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.KeyInputEvent;
import net.spectre.client.event.impl.MouseInputEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.movement.elytrafly.ElytraFlyInventory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public final class ElytraSwapModule extends ClientModule {
   private static final int[] SOURCE_INDICES = sourceIndices();
   private static final int MAX_WAIT_TICKS = 20;
   private static final int CHEST_SLOT_ID = 6;
   private static final Component SWAP_FAILED = ModuleChat.body().danger("Swap failed").text(", the item didn't move into the chest slot").build();
   private final KeySetting key = this.addSetting("key", new KeySetting("Key", -1));
   private final BooleanSetting swapBackOnLanding = this.addSetting("swap-back-on-landing", new BooleanSetting("Swap Back on Landing", true));
   private final NumberSetting landingDelay = this.addSetting("landing-delay", new NumberSetting("Landing Delay", 4.0F, 0.0F, 20.0F).step(1.0F));
   private boolean pending;
   private int pendingTicks;
   private boolean armed;
   private boolean glided;
   private int groundTicks;
   private int storedIndex = -1;
   private Item storedItem;

   public ElytraSwapModule() {
      super("elytra-swap", "Elytra Swap", "Swaps between your chestplate and elytra with one key.", ModuleCategory.MOVEMENT);
      this.usesNotifications();
      this.landingDelay.visibleWhen(this.swapBackOnLanding::get);
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.pending = false;
      this.pendingTicks = 0;
      this.disarm();
   }

   private void disarm() {
      this.armed = false;
      this.glided = false;
      this.groundTicks = 0;
      this.storedIndex = -1;
      this.storedItem = null;
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
         if (mc.gui.screen() == null && mc.player != null) {
            this.pending = true;
            this.pendingTicks = 0;
         }
      }
   }

   private void onPostTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null) {
         this.reset();
      } else if (this.pending) {
         String busy = this.busyReason(player);
         if (busy != null) {
            if (++this.pendingTicks > 20) {
               this.pending = false;
               ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", " + busy));
            }
         } else {
            this.pending = false;
            if (mc.gui.screen() == null) {
               this.swap(mc, player);
            }
         }
      } else {
         if (this.armed) {
            this.followFlight(mc, player);
         }
      }
   }

   private String busyReason(LocalPlayer player) {
      if (CombatMacroLock.heldByOther(this)) {
         return "another macro kept the inventory busy";
      } else {
         return player.isUsingItem() ? "you kept using an item" : null;
      }
   }

   private void followFlight(Minecraft mc, LocalPlayer player) {
      if (!this.swapBackOnLanding.get() || !player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
         this.disarm();
      } else if (player.isFallFlying()) {
         this.glided = true;
         this.groundTicks = 0;
      } else if (this.glided) {
         if (!player.onGround() && !player.isInWater()) {
            this.groundTicks = 0;
         } else if (this.groundTicks++ >= this.landingDelay.getInt()) {
            if (mc.gui.screen() == null && inventoryReady(player) && this.busyReason(player) == null) {
               if (!isBound(player, player.getItemBySlot(EquipmentSlot.CHEST))) {
                  int chestplate = this.chestplateToEquip(player);
                  if (chestplate == -1) {
                     ModuleChat.send(this, ModuleChat.body().danger("Can't swap back").text(", no ").name("chestplate").text(" left in your inventory"));
                  } else if (!equip(mc, player, chestplate)) {
                     ModuleChat.send(this, SWAP_FAILED);
                  }
               }

               this.disarm();
            }
         }
      }
   }

   private void swap(Minecraft mc, LocalPlayer player) {
      if (!player.isSpectator()) {
         if (!inventoryReady(player)) {
            ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", close the open container first"));
         } else {
            ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
            if (isBound(player, worn)) {
               ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", your chest piece has ").name("Curse of Binding"));
            } else if (worn.has(DataComponents.GLIDER)) {
               int chestplate = this.chestplateToEquip(player);
               if (chestplate == -1) {
                  ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no ").name("chestplate").text(" in your inventory"));
               } else if (!equip(mc, player, chestplate)) {
                  ModuleChat.send(this, SWAP_FAILED);
               }

               this.disarm();
            } else {
               int elytra = findElytra(player);
               if (elytra == -1) {
                  ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no usable ").name("elytra").text(" in your inventory"));
               } else {
                  Item takenOff = isChestplate(worn) ? worn.getItem() : null;
                  if (!equip(mc, player, elytra)) {
                     ModuleChat.send(this, SWAP_FAILED);
                  } else {
                     this.disarm();
                     if (takenOff != null) {
                        this.armed = this.swapBackOnLanding.get();
                        this.storedIndex = elytra;
                        this.storedItem = takenOff;
                     }
                  }
               }
            }
         }
      }
   }

   private int chestplateToEquip(LocalPlayer player) {
      if (this.storedIndex >= 0 && this.storedItem != null) {
         ItemStack stored = player.getInventory().getItem(this.storedIndex);
         if (stored.is(this.storedItem) && !isBound(player, stored)) {
            return this.storedIndex;
         }
      }

      return findBestChestplate(player);
   }

   private static boolean equip(Minecraft mc, LocalPlayer player, int index) {
      Item item = player.getInventory().getItem(index).getItem();
      if (!Inventory.isHotbarSlot(index) && index != 40) {
         ElytraFlyInventory.moveToChest(index);
      } else {
         mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, 6, index, ContainerInput.SWAP, player);
      }

      player.connection.send(new ServerboundContainerClosePacket(0));
      return player.getItemBySlot(EquipmentSlot.CHEST).is(item);
   }

   private static boolean inventoryReady(LocalPlayer player) {
      return player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
   }

   private static boolean isBound(LocalPlayer player, ItemStack stack) {
      return !player.isCreative() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
   }

   private static int findElytra(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getItem(index);
         if (LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST) && !isBound(player, stack)) {
            int left = durabilityLeft(stack);
            if (left > bestLeft) {
               best = index;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static int findBestChestplate(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      double bestScore = -1.0;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getItem(index);
         if (isChestplate(stack) && !isBound(player, stack)) {
            double score = armorScore(stack);
            int left = durabilityLeft(stack);
            if (score > bestScore || score == bestScore && left > bestLeft) {
               best = index;
               bestScore = score;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.has(DataComponents.GLIDER)) {
         Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private static double armorScore(ItemStack stack) {
      ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
      return modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST) + modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, EquipmentSlot.CHEST);
   }

   private static int durabilityLeft(ItemStack stack) {
      return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
   }

   private static int[] sourceIndices() {
      int[] indices = new int[37];
      int i = 0;

      while (i < 36) {
         indices[i] = i++;
      }

      indices[36] = 40;
      return indices;
   }
}
