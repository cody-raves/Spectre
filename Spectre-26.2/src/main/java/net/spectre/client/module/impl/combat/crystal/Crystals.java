package net.spectre.client.module.impl.combat.crystal;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.util.Set;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Fail;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class Crystals {
   public static final int READY = -2;
   public static final int NO_ROUTE = -1;
   private static final int MIN_CONFIRM_TICKS = 4;
   private static final int MAX_CONFIRM_TICKS = 20;
   private static final Set<Block> USABLE_BLOCKS = Set.of(
      Blocks.RESPAWN_ANCHOR, Blocks.NOTE_BLOCK, Blocks.LEVER, Blocks.REPEATER, Blocks.REDSTONE_WIRE, Blocks.CAKE, Blocks.COMPOSTER, Blocks.DRAGON_EGG
   );

   private Crystals() {
   }

   public static boolean isBase(BlockState state) {
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   public static Vec3 explosionAt(BlockPos base) {
      return Vec3.atBottomCenterOf(base.above());
   }

   public static AABB boxOn(BlockPos base) {
      return net.minecraft.world.entity.EntityTypes.END_CRYSTAL.getDimensions().makeBoundingBox(explosionAt(base));
   }

   public static boolean hasCrystal(Level level, BlockPos base, Entity ignore) {
      for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, new AABB(base.above()))) {
         if (crystal != ignore) {
            return true;
         }
      }

      return false;
   }

   public static boolean canHost(Level level, BlockPos base, Entity ignore) {
      BlockPos above = base.above();
      if (!level.isEmptyBlock(above)) {
         return false;
      } else {
         AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);

         for (Entity entity : level.getEntities(ignore, space)) {
            if (entity.isAlive()) {
               return false;
            }
         }

         return true;
      }
   }

   public static BlockPos obsidianTarget(ClientLevel level, LocalPlayer player, BlockHitResult hit, BlockState clicked) {
      BlockPos target = placedObsidian(level, player, hit, clicked);
      return target != null && canHost(level, target, null) ? target : null;
   }

   public static BlockPos placedObsidian(ClientLevel level, LocalPlayer player, BlockHitResult hit, BlockState clicked) {
      if (!player.isSecondaryUseActive() && reactsToUse(level, hit.getBlockPos(), clicked)) {
         return null;
      } else {
         BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN), hit);
         if (!context.canPlace()) {
            return null;
         } else {
            BlockPos target = context.getClickedPos();
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)) {
               return null;
            } else {
               return !level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), target, CollisionContext.placementContext(player)) ? null : target;
            }
         }
      }
   }

   private static boolean reactsToUse(Level level, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      if (block instanceof EntityBlock || USABLE_BLOCKS.contains(block)) {
         return true;
      } else {
         return !state.is(BlockTags.DOORS)
               && !state.is(BlockTags.TRAPDOORS)
               && !state.is(BlockTags.FENCE_GATES)
               && !state.is(BlockTags.BUTTONS)
               && !state.is(BlockTags.CAULDRONS)
               && !state.is(BlockTags.CANDLE_CAKES)
               && !state.is(BlockTags.FLOWER_POTS)
            ? state.getMenuProvider(level, pos) != null
            : true;
      }
   }

   public static int route(LocalPlayer player, Item item, BlockState clicked, int preferredSlot) {
      ItemStack main = player.getMainHandItem();
      if (main.is(item)) {
         return -2;
      } else {
         boolean inOffhand = player.getOffhandItem().is(item);
         if (inOffhand && letsClickThrough(main, clicked)) {
            return -2;
         } else {
            Inventory inventory = player.getInventory();
            int slot = hotbarSlotOf(inventory, item);
            if (slot != -1) {
               return slot;
            } else if (!inOffhand) {
               return -1;
            } else if (Inventory.isHotbarSlot(preferredSlot) && letsClickThrough(inventory.getItem(preferredSlot), clicked)) {
               return preferredSlot;
            } else {
               for (int var8 = 0; var8 < Inventory.getSelectionSize(); var8++) {
                  if (letsClickThrough(inventory.getItem(var8), clicked)) {
                     return var8;
                  }
               }

               return -1;
            }
         }
      }
   }

   public static boolean letsClickThrough(ItemStack stack, BlockState clicked) {
      if (stack.isEmpty()) {
         return true;
      } else if (!stack.has(DataComponents.CONSUMABLE) && !stack.has(DataComponents.BLOCKS_ATTACKS) && !stack.has(DataComponents.KINETIC_WEAPON)) {
         Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.swappable()
            ? false
            : stack.is(ItemTags.SWORDS) || stack.is(Items.MACE) || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(ItemTags.AXES) && isBase(clicked);
      } else {
         return false;
      }
   }

   public static boolean isCombatItem(ItemStack stack) {
      return stack.isEmpty() || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TOTEM_OF_UNDYING);
   }

   public static int hotbarSlotOf(Inventory inventory, Item item) {
      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         if (inventory.getItem(slot).is(item)) {
            return slot;
         }
      }

      return -1;
   }

   public static boolean strike(Minecraft mc, LocalPlayer player, Entity target, Bypass.Profile profile) {
      if (player == null || mc.gameMode == null || target == null || target.isRemoved() || player.isSpectator()) {
         return false;
      } else if (profile != null && profile.grim()) {
         mc.gameMode.attack(player, target);
         player.swing(InteractionHand.MAIN_HAND);
         return true;
      } else {
         return MeleeAttacks.attack(mc, new EntityHitResult(target, target.getBoundingBox().getCenter()));
      }
   }

   public static void rightClick(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
      for (InteractionHand hand : InteractionHand.values()) {
         ItemStack stack = player.getItemInHand(hand);
         if (!stack.isItemEnabled(mc.level.enabledFeatures())) {
            return;
         }

         int countBefore = stack.getCount();
         InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
         if (result instanceof Success success) {
            if (success.swingSource() == SwingSource.CLIENT) {
               player.swing(hand);
               if (!stack.isEmpty() && (stack.getCount() != countBefore || player.hasInfiniteMaterials())) {
                  mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
               }
            }

            return;
         }

         if (result instanceof Fail) {
            return;
         }

         if (!stack.isEmpty() && mc.gameMode.useItem(player, hand) instanceof Success success) {
            if (success.swingSource() == SwingSource.CLIENT) {
               player.swing(hand);
            }

            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            return;
         }
      }
   }

   public static int confirmWindow(Minecraft mc, LocalPlayer player) {
      return Mth.clamp(latencyMs(mc, player) / 50 + 3, 4, 20);
   }

   public static int latencyMs(Minecraft mc, LocalPlayer player) {
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) {
         return 0;
      } else {
         PlayerInfo info = connection.getPlayerInfo(player.getUUID());
         return info == null ? 0 : Math.max(info.getLatency(), 0);
      }
   }

   public static boolean isKeyDown(Minecraft mc, int key) {
      if (!ModuleKeybinds.isBindable(key)) {
         return false;
      } else {
         return key >= 1000 ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key - 1000) == 1 : InputConstants.isKeyDown(mc.getWindow(), key);
      }
   }

   public static boolean isPhysicallyDown(Minecraft mc, KeyMapping mapping) {
      Key key;
      try {
         key = InputConstants.getKey(mapping.saveString());
      } catch (IllegalArgumentException var4) {
         return false;
      }

      int value = key.getValue();
      if (value < 0) {
         return false;
      } else {
         return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), value) == 1;
            case KEYSYM -> InputConstants.isKeyDown(mc.getWindow(), value);
            default -> false;
         };
      }
   }
}
