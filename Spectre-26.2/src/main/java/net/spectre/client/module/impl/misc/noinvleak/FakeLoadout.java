package net.spectre.client.module.impl.misc.noinvleak;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class FakeLoadout {
   public static final int UNKNOWN = 41;
   private static final Item[] HOTBAR = new Item[]{
      Items.IRON_SWORD,
      Items.DIAMOND_SWORD,
      Items.NETHERITE_SWORD,
      Items.IRON_PICKAXE,
      Items.DIAMOND_PICKAXE,
      Items.NETHERITE_PICKAXE,
      Items.IRON_AXE,
      Items.DIAMOND_AXE,
      Items.NETHERITE_AXE,
      Items.IRON_SHOVEL,
      Items.DIAMOND_SHOVEL,
      Items.NETHERITE_SHOVEL,
      Items.BOW,
      Items.WATER_BUCKET,
      Items.COBBLESTONE,
      Items.TORCH,
      Items.COOKED_BEEF,
      Items.GOLDEN_CARROT,
      Items.BREAD,
      Items.ENDER_PEARL,
      Items.OAK_PLANKS,
      Items.DIRT,
      Items.FLINT_AND_STEEL
   };
   private static final Item[] MAIN = new Item[]{
      Items.COBBLESTONE,
      Items.STONE,
      Items.DIRT,
      Items.OAK_LOG,
      Items.OAK_PLANKS,
      Items.STICK,
      Items.COAL,
      Items.IRON_INGOT,
      Items.GOLD_INGOT,
      Items.DIAMOND,
      Items.REDSTONE,
      Items.LAPIS_LAZULI,
      Items.ARROW,
      Items.STRING,
      Items.BONE,
      Items.ROTTEN_FLESH,
      Items.GUNPOWDER,
      Items.SAND,
      Items.GRAVEL,
      Items.WHEAT_SEEDS,
      Items.WHEAT,
      Items.LADDER,
      Items.OBSIDIAN,
      Items.GLASS,
      Items.LEATHER,
      Items.FEATHER,
      Items.FLINT,
      Items.SPIDER_EYE,
      Items.APPLE,
      Items.COOKED_PORKCHOP,
      Items.BUCKET,
      Items.BOOK,
      Items.PAPER,
      Items.SUGAR_CANE,
      Items.CRAFTING_TABLE,
      Items.CHEST,
      Items.FURNACE
   };
   private static final Item[] OFFHAND = new Item[]{Items.SHIELD, Items.TORCH, Items.WATER_BUCKET, Items.ARROW, Items.COOKED_BEEF, Items.GOLDEN_APPLE};
   private static final Item[][] ARMOR = new Item[][]{
      {Items.IRON_BOOTS, Items.CHAINMAIL_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS},
      {Items.IRON_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
      {Items.IRON_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
      {Items.IRON_HELMET, Items.CHAINMAIL_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET}
   };
   private static final int FIRST_ARMOR = 36;
   private final ItemStack[] stacks = new ItemStack[42];
   private long seed;

   public void reseed() {
      this.seed = ThreadLocalRandom.current().nextLong();
      Arrays.fill(this.stacks, null);
   }

   public ItemStack get(int slot) {
      if (slot < 0 || slot > 41) {
         slot = 41;
      }

      ItemStack stack = this.stacks[slot];
      if (stack == null) {
         this.stacks[slot] = stack = this.roll(slot);
      }

      return stack;
   }

   private ItemStack roll(int slot) {
      Item item;
      if (slot < 9) {
         item = this.pick(HOTBAR, slot);
      } else if (slot < 36 || slot == 41) {
         item = this.pick(MAIN, slot);
      } else if (slot == 40) {
         item = this.pick(OFFHAND, slot);
      } else {
         item = ARMOR[slot - 36][(int)Long.remainderUnsigned(mix(this.seed ^ 167L), ARMOR[0].length)];
      }

      ItemStack stack = new ItemStack(item);
      if (stack.getMaxStackSize() > 1) {
         stack.setCount(1 + (int)Long.remainderUnsigned(mix(this.seed ^ slot ^ 81L), stack.getMaxStackSize()));
      } else if (stack.isDamageableItem()) {
         stack.setDamageValue((int)Long.remainderUnsigned(mix(this.seed ^ slot ^ 82L), stack.getMaxDamage() / 2));
      }

      return stack;
   }

   private Item pick(Item[] pool, int slot) {
      return pool[(int)Long.remainderUnsigned(mix(this.seed ^ slot * -7046029254386353131L), pool.length)];
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }
}
