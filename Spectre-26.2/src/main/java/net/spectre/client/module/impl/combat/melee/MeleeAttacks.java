package net.spectre.client.module.impl.combat.melee;

import com.example.client.mixin.AccessorTriggerbotKeyMapping;
import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.config.FriendStore;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.AutoEatModule;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class MeleeAttacks {
   public static final float CRIT_CHARGE = 0.9F;
   private static final double LINE_EPSILON_SQ = 0.001;
   private static int attackerId;
   private static int attackTick = -1;
   private static int attackSerial;

   private MeleeAttacks() {
   }

   public static void noteAttack(Player player) {
      attackerId = System.identityHashCode(player);
      attackTick = player.tickCount;
      attackSerial++;
   }

   public static boolean attackedThisTick(Player player) {
      return attackTick == player.tickCount && attackerId == System.identityHashCode(player);
   }

   public static int attackSerial() {
      return attackSerial;
   }

   public static boolean clickPending(Minecraft mc) {
      return ((AccessorTriggerbotKeyMapping)mc.options.keyAttack).spectre$triggerbotClickCount() > 0;
   }

   public static void takeAttackClicks(Minecraft mc) {
      KeyMapping attack = mc.options.keyAttack;

      while (attack.consumeClick()) {
      }
   }

   public static boolean canAct(Minecraft mc) {
      if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
         return false;
      } else if (AutoEatModule.isEating()) {
         return false;
      } else {
         FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
         return freecam == null || !freecam.isEnabled();
      }
   }

   public static EntityHitResult crosshairHit(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (player.raycastHitResult(1.0F, player) instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
            return lineIsClear(player, player.getEyePosition(), hit.getLocation()) ? hit : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean lineIsClear(LocalPlayer player, Vec3 from, Vec3 to) {
      BlockHitResult block = player.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
      return block.getType() == Type.MISS || block.getLocation().distanceToSqr(from) >= from.distanceToSqr(to) - 0.001;
   }

   public static boolean attack(Minecraft mc, EntityHitResult hit) {
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null) {
         return false;
      } else if (mc.missTime > 0 || attackedThisTick(player)) {
         return false;
      } else if (!player.isHandsBusy() && !player.isUsingItem() && !player.isSpectator()) {
         ItemStack held = player.getMainHandItem();
         if (held.has(DataComponents.PIERCING_WEAPON)) {
            return false;
         } else if (held.isItemEnabled(mc.level.enabledFeatures()) && !player.cannotAttackWithItem(held, 0)) {
            Entity target = hit.getEntity();
            if (target.isRemoved()) {
               return false;
            } else {
               AttackRange range = held.get(DataComponents.ATTACK_RANGE);
               boolean inReach = range != null
                  ? range.isInRange(player, hit.getLocation())
                  : player.isWithinEntityInteractionRange(target.getBoundingBox(), 0.0);
               if (!inReach) {
                  return false;
               } else {
                  mc.gameMode.attack(player, target);
                  player.swing(InteractionHand.MAIN_HAND);
                  return true;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isHittable(LocalPlayer self, Entity entity) {
      if (!(entity instanceof LivingEntity living && entity != self && !(entity instanceof ArmorStand))) {
         return false;
      } else {
         return living.isAlive() && entity.isAttackable() && self.getVehicle() != entity
            ? !(entity instanceof Player player && (player.isSpectator() || player.isCreative()))
            : false;
      }
   }

   public static float charge(Player player) {
      return player.getAttackStrengthScale(0.5F);
   }

   public static boolean wouldCrit(Player player, Entity target) {
      return charge(player) > 0.9F && player.fallDistance > 0.0 && critStance(player, target);
   }

   public static boolean critStance(Player player, Entity target) {
      return !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.isMobilityRestricted()
         && !player.isPassenger()
         && !player.isSprinting()
         && target instanceof LivingEntity;
   }

   public static boolean shieldBlocks(LivingEntity target, Player attacker) {
      ItemStack shield = target.getItemBlockingWith();
      if (shield == null) {
         return false;
      } else {
         BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
         if (blocks == null) {
            return false;
         } else {
            DamageSource source = attacker.level().damageSources().playerAttack(attacker);
            if (blocks.bypassedBy().map(tag -> tag.contains(source.typeHolder())).orElse(false)) {
               return false;
            } else {
               Vec3 facing = target.calculateViewVector(0.0F, target.getYHeadRot());
               Vec3 toAttacker = attacker.position().subtract(target.position());
               Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z).normalize();
               double angle = Math.acos(Mth.clamp(flat.dot(facing), -1.0, 1.0));
               return blocks.resolveBlockedDamage(source, 1.0F, angle) > 0.0F;
            }
         }
      }
   }

   public static boolean disablesShields(ItemStack stack) {
      Weapon weapon = stack.get(DataComponents.WEAPON);
      return weapon != null && weapon.disableBlockingForSeconds() > 0.0F;
   }

   public static boolean isMeleeWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
   }

   public static double attackDamage(ItemStack stack) {
      double[] total = new double[]{0.0};
      stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
         if (Attributes.ATTACK_DAMAGE.equals(attribute) && modifier.operation() == Operation.ADD_VALUE) {
            total[0] += modifier.amount();
         }
      });
      return total[0];
   }

   public static boolean isFriend(Entity entity) {
      return entity instanceof Player player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
   }

   public static void select(LocalPlayer player, int slot) {
      if (Inventory.isHotbarSlot(slot)) {
         player.getInventory().setSelectedSlot(slot);
      }
   }

   public static int spread(int maxTicks) {
      return maxTicks > 0 ? ThreadLocalRandom.current().nextInt(maxTicks + 1) : 0;
   }
}
