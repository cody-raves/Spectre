package net.spectre.client.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.crystal.Crystals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CrystalOptimizerModule extends ClientModule {
   private static final int SAMPLE_CAPACITY = 64;
   private static final double RANGE_SLACK = 1.0;
   private final ItemStack[] heldSamples = new ItemStack[64];
   private final int[] sampleTicks = new int[64];
   private int sampleCursor;
   private int sampleCount;
   private int ticks;
   private int trackedSince;
   private LocalPlayer trackedPlayer;
   private ClientLevel trackedLevel;
   private boolean attackSent;
   private final CrystalOptimizerModule.AttackProbe attackProbe = new CrystalOptimizerModule.AttackProbe();

   public CrystalOptimizerModule() {
      super("crystal-optimizer", "Crystal Optimizer", "Removes crystals client-side as soon as your hit breaks them.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onTick(TickEvent event) {
      this.attackSent = false;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      if (player != null && level != null) {
         if (player != this.trackedPlayer || level != this.trackedLevel) {
            this.reset();
            this.trackedPlayer = player;
            this.trackedLevel = level;
         }

         this.ticks++;
         this.sample(player.getMainHandItem());
      } else {
         if (this.trackedPlayer != null || this.sampleCount > 0) {
            this.reset();
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPacketSend(PacketSendEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         if (event.packet instanceof ServerboundAttackPacket interact) {
            this.attackSent = this.attackProbe.isAttack(interact);
         } else if (event.packet instanceof ServerboundSetCarriedItemPacket carried) {
            LocalPlayer player = mc.player;
            if (player != null && player == this.trackedPlayer && Inventory.isHotbarSlot(carried.getSlot())) {
               this.sample(player.getInventory().getItem(carried.getSlot()));
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onAttack(PlayerAttackEntityEvent event) {
      boolean sent = this.attackSent;
      this.attackSent = false;
      if (sent && event.target instanceof EndCrystal crystal) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         ClientLevel level = mc.level;
         if (player != null && level != null && player == this.trackedPlayer && crystal.level() == level) {
            if (this.breaksOnServer(mc, player, level, crystal)) {
               level.removeEntity(crystal.getId(), RemovalReason.DISCARDED);
            }
         }
      }
   }

   private boolean breaksOnServer(Minecraft mc, LocalPlayer player, ClientLevel level, EndCrystal crystal) {
      if (crystal.isRemoved() || crystal.getBeamTarget() != null) {
         return false;
      } else if (!player.isSpectator() && !player.isDeadOrDying() && !player.isAutoSpinAttack()) {
         ItemStack weapon = player.getMainHandItem();
         if (weapon.isItemEnabled(level.enabledFeatures()) && !player.cannotAttackWithItem(weapon, 0)) {
            if (!level.getWorldBorder().isWithinBounds(crystal.blockPosition())) {
               return false;
            } else {
               return !player.isWithinAttackRange(player.getMainHandItem(), crystal.getBoundingBox(), 1.0)
                  ? false
                  : enchantsAddDamage(weapon) || this.recentHandsDealDamage(mc, player, weapon);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean enchantsAddDamage(ItemStack weapon) {
      float bonus = 0.0F;

      for (Entry<Holder<Enchantment>> entry : weapon.getEnchantments().entrySet()) {
         Enchantment enchantment = (Enchantment)((Holder)entry.getKey()).value();

         for (ConditionalEffect<EnchantmentValueEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.DAMAGE)) {
            if (effect.requirements().isPresent() || !(effect.effect() instanceof AddValue add)) {
               return false;
            }

            bonus += add.value().calculate(entry.getIntValue());
         }
      }

      return bonus > 0.0F;
   }

   private boolean recentHandsDealDamage(Minecraft mc, LocalPlayer player, ItemStack held) {
      if (!dealsDamage(player, held)) {
         return false;
      } else {
         int window = Crystals.confirmWindow(mc, player);
         boolean complete = this.ticks - this.trackedSince >= window;
         ItemStack checked = held;

         int walked;
         for (walked = 0; walked < this.sampleCount; walked++) {
            int index = Math.floorMod(this.sampleCursor - 1 - walked, 64);
            if (this.ticks - this.sampleTicks[index] > window) {
               break;
            }

            ItemStack sample = this.heldSamples[index];
            if (sample != checked) {
               if (!dealsDamage(player, sample)) {
                  return false;
               }

               checked = sample;
            }
         }

         if (walked == 64) {
            complete = false;
         }

         return complete || dealsDamage(player, ItemStack.EMPTY);
      }
   }

   private static boolean dealsDamage(LocalPlayer player, ItemStack mainHand) {
      AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, changed -> {});
      damage.setBaseValue(player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));

      for (MobEffectInstance effect : player.getActiveEffects()) {
         effect.getEffect().value().createModifiers(effect.getAmplifier(), (attribute, modifier) -> include(damage, attribute, modifier));
      }

      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         ItemStack stack = slot == EquipmentSlot.MAINHAND ? mainHand : player.getItemBySlot(slot);
         if (!stack.isEmpty() && !stack.isBroken()) {
            stack.forEachModifier(slot, (attribute, modifier) -> include(damage, attribute, modifier));
         }
      }

      return (float)damage.getValue() > 0.0F;
   }

   private static void include(AttributeInstance damage, Holder<Attribute> attribute, AttributeModifier modifier) {
      if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
         damage.removeModifier(modifier.id());
         damage.addTransientModifier(modifier);
      }
   }

   private void sample(ItemStack stack) {
      this.heldSamples[this.sampleCursor] = stack;
      this.sampleTicks[this.sampleCursor] = this.ticks;
      this.sampleCursor = (this.sampleCursor + 1) % 64;
      if (this.sampleCount < 64) {
         this.sampleCount++;
      }
   }

   private void reset() {
      Arrays.fill(this.heldSamples, null);
      this.sampleCursor = 0;
      this.sampleCount = 0;
      this.trackedSince = this.ticks;
      this.trackedPlayer = null;
      this.trackedLevel = null;
      this.attackSent = false;
   }

   @Environment(EnvType.CLIENT)
   private static final class AttackProbe {
      boolean isAttack(ServerboundAttackPacket packet) { return true; }
   }
}
