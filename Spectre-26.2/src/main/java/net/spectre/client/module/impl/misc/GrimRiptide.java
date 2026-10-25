package net.spectre.client.module.impl.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class GrimRiptide {
   public static final int MIN_TICKS = 9;

   private GrimRiptide() {
   }

   public static boolean clientAccepts() {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null && mc.level != null && mc.isSameThread()) {
         if (!player.isInWaterOrRain() && !mc.level.isRaining()) {
            AABB box = player.getBoundingBox().inflate(0.1);
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
               BlockState state = mc.level.getBlockState(pos);
               if (state.getFluidState().is(Fluids.WATER)) {
                  return true;
               }

               if (state.getFluidState().is(Fluids.FLOWING_WATER)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isRiptide(ItemStack stack) {
      if (!stack.is(Items.TRIDENT)) {
         return false;
      } else {
         for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> enchantment = (Holder<Enchantment>)entry.getKey();
            if (enchantment.is(Enchantments.RIPTIDE) && entry.getIntValue() > 0) {
               return true;
            }
         }

         return false;
      }
   }
}
