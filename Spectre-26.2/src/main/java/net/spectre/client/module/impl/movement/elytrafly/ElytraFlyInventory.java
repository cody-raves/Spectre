package net.spectre.client.module.impl.movement.elytrafly;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public final class ElytraFlyInventory {
   public static final int OFFHAND = 40;
   private static final int CHEST_ARMOR_ID = 6;
   private static int previousSlot = -1;

   private ElytraFlyInventory() {
   }

   public static int find(Predicate<ItemStack> isGood) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? -1 : find(isGood, 0, player.getInventory().getContainerSize() - 1);
   }

   public static int find(Predicate<ItemStack> isGood, int start, int end) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return -1;
      } else {
         Inventory inventory = player.getInventory();

         for (int i = start; i <= end; i++) {
            if (isGood.test(inventory.getItem(i))) {
               return i;
            }
         }

         return -1;
      }
   }

   public static int findInHotbar(Predicate<ItemStack> isGood) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return -1;
      } else if (isGood.test(player.getOffhandItem())) {
         return 40;
      } else {
         return isGood.test(player.getMainHandItem()) ? player.getInventory().getSelectedSlot() : find(isGood, 0, 8);
      }
   }

   public static boolean isHotbar(int index) {
      return index >= 0 && index <= 8;
   }

   public static void moveToHotbar(int fromIndex, int hotbarSlot) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         move(player, indexToId(player, fromIndex), indexToId(player, hotbarSlot));
      }
   }

   public static void moveToChest(int fromIndex) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         int fromId = indexToId(player, fromIndex);
         move(player, fromId, fromId == -1 ? -1 : 6);
      }
   }

   private static void move(LocalPlayer player, int fromId, int toId) {
      MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
      if (gameMode != null && fromId != -1 && toId != -1) {
         AbstractContainerMenu menu = player.containerMenu;
         boolean hadEmptyCursor = menu.getCarried().isEmpty();
         gameMode.handleContainerInput(menu.containerId, fromId, 0, ContainerInput.PICKUP, player);
         gameMode.handleContainerInput(menu.containerId, toId, 0, ContainerInput.PICKUP, player);
         if (hadEmptyCursor && !menu.getCarried().isEmpty()) {
            gameMode.handleContainerInput(menu.containerId, fromId, 0, ContainerInput.PICKUP, player);
         }
      }
   }

   private static int indexToId(LocalPlayer player, int index) {
      if (player.containerMenu != player.inventoryMenu) {
         return -1;
      } else if (index >= 0 && index <= 8) {
         return 36 + index;
      } else if (index >= 9 && index < 36) {
         return index;
      } else if (index >= 36 && index <= 39) {
         return 8 - (index - 36);
      } else {
         return index == 40 ? 45 : -1;
      }
   }

   public static boolean swap(int slot, boolean swapBack) {
      if (slot == 40) {
         return true;
      } else if (slot >= 0 && slot <= 8) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && mc.gameMode != null) {
            if (swapBack && previousSlot == -1) {
               previousSlot = player.getInventory().getSelectedSlot();
            } else if (!swapBack) {
               previousSlot = -1;
            }

            player.getInventory().setSelectedSlot(slot);
            ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).spectre$elytraFlySyncSelected();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean swapBack() {
      if (previousSlot == -1) {
         return false;
      } else {
         boolean result = swap(previousSlot, false);
         previousSlot = -1;
         return result;
      }
   }

   public static void chestSwap() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         ItemStack current = player.getItemBySlot(EquipmentSlot.CHEST);
         Equippable equippable = current.get(DataComponents.EQUIPPABLE);
         if (current.has(DataComponents.GLIDER)) {
            equipChestplate(player);
         } else if (equippable != null && equippable.slot().getIndex() == EquipmentSlot.CHEST.getIndex()) {
            equipElytra(player);
         } else if (!equipChestplate(player)) {
            equipElytra(player);
         }
      }
   }

   private static boolean equipChestplate(LocalPlayer player) {
      NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
      int bestSlot = -1;

      for (int i = 0; i < items.size(); i++) {
         Item item = items.get(i).getItem();
         if (item == Items.DIAMOND_CHESTPLATE) {
            bestSlot = i;
         } else if (item == Items.NETHERITE_CHESTPLATE) {
            bestSlot = i;
            break;
         }
      }

      if (bestSlot != -1) {
         equip(player, bestSlot);
      }

      return bestSlot != -1;
   }

   private static void equipElytra(LocalPlayer player) {
      NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();

      for (int i = 0; i < items.size(); i++) {
         if (items.get(i).has(DataComponents.GLIDER)) {
            equip(player, i);
            break;
         }
      }
   }

   private static void equip(LocalPlayer player, int slot) {
      if (indexToId(player, slot) != -1) {
         moveToChest(slot);
         player.connection.send(new ServerboundContainerClosePacket(0));
      }
   }
}
