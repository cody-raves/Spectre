package net.spectre.client.module.impl.combat;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

@Environment(EnvType.CLIENT)
public final class TriggerbotModule extends ClientModule {
   private static final String CRITS_OFF = "Off";
   private static final String CRITS_SMART = "Smart";
   private static final String CRITS_ONLY = "Only";
   private static final int MIN_GAP_TICKS = 2;
   private static final int SMART_MAX_WAIT_TICKS = 12;
   private final NumberSetting reaction = this.addSetting("reaction", new NumberSetting("Reaction", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 1.0F, 0.0F, 4.0F).step(1.0F));
   private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Smart", "Off", "Smart", "Only"));
   private final BooleanSetting holdAttack = this.addSetting("hold-attack", new BooleanSetting("Hold Attack", false));
   private final BooleanSetting skipShielding = this.addSetting("skip-shielding", new BooleanSetting("Skip Shielding", true));
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
   private final BooleanSetting passives = this.addSetting("passives", new BooleanSetting("Passives", false));
   private final BooleanSetting invisible = this.addSetting("invisible", new BooleanSetting("Invisible", false));
   private final BooleanSetting skipFriends = this.addSetting("skip-friends", new BooleanSetting("Skip Friends", true));
   private final BooleanSetting anyItem = this.addSetting("any-item", new BooleanSetting("Any Item", false));
   private final BooleanSetting swords = this.addSetting("swords", new BooleanSetting("Swords", true));
   private final BooleanSetting axes = this.addSetting("axes", new BooleanSetting("Axes", true));
   private final BooleanSetting mace = this.addSetting("mace", new BooleanSetting("Mace", true));
   private int ticks;
   private int trackedId = -1;
   private int reactionDoneTick;
   private int chargedTick = -1;
   private int chargePause;
   private int lastHitTick;
   private int airborneTick = -1;

   public TriggerbotModule() {
      super("triggerbot", "Triggerbot", "Hits the entity under the crosshair once the swing is charged, with a reaction delay.", ModuleCategory.COMBAT);
      this.swords.visibleWhen(() -> !this.anyItem.get());
      this.axes.visibleWhen(() -> !this.anyItem.get());
      this.mace.visibleWhen(() -> !this.anyItem.get());
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.ticks = 0;
      this.trackedId = -1;
      this.reactionDoneTick = 0;
      this.chargedTick = -1;
      this.chargePause = 0;
      this.lastHitTick = -2;
      this.airborneTick = -1;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.ticks++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (!MeleeAttacks.canAct(mc)) {
         this.trackedId = -1;
      } else {
         this.trackCharge(player);
         this.airborneTick = player.onGround() ? -1 : (this.airborneTick < 0 ? this.ticks : this.airborneTick);
         EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
         Entity target = hit == null ? null : hit.getEntity();
         if (target != null && this.isTarget(player, target)) {
            if (target.getId() != this.trackedId) {
               this.trackedId = target.getId();
               this.reactionDoneTick = this.ticks + this.reaction.getInt() + MeleeAttacks.spread(this.jitter.getInt());
            }

            if (this.ticks >= this.reactionDoneTick
               && this.chargedTick >= 0
               && this.ticks - this.chargedTick >= this.chargePause
               && this.ticks - this.lastHitTick >= 2) {
               if (!this.holdAttack.get() || mc.options.keyAttack.isDown()) {
                  ItemStack held = player.getMainHandItem();
                  if (this.weaponAllowed(held) && !CombatMacroLock.heldByOther(this)) {
                     if (!(this.skipShielding.get() && target instanceof LivingEntity living)
                        || !MeleeAttacks.shieldBlocks(living, player)
                        || MeleeAttacks.disablesShields(held)) {
                        if (this.critsAllow(player, target)) {
                           if (!MeleeAttacks.clickPending(mc)) {
                              if (MeleeAttacks.attack(mc, hit)) {
                                 this.lastHitTick = this.ticks;
                                 this.chargedTick = -1;
                              }
                           }
                        }
                     }
                  }
               }
            }
         } else {
            this.trackedId = -1;
         }
      }
   }

   private void trackCharge(LocalPlayer player) {
      if (MeleeAttacks.charge(player) < 1.0F) {
         this.chargedTick = -1;
      } else if (this.chargedTick < 0) {
         this.chargedTick = this.ticks;
         this.chargePause = MeleeAttacks.spread(this.jitter.getInt());
      }
   }

   private boolean isTarget(LocalPlayer self, Entity entity) {
      if (!MeleeAttacks.isHittable(self, entity)) {
         return false;
      } else if (entity.isInvisible() && !this.invisible.get()) {
         return false;
      } else if (!(entity instanceof Player player)) {
         return entity instanceof Enemy ? this.hostiles.get() : this.passives.get();
      } else {
         return this.players.get() && (!this.skipFriends.get() || !MeleeAttacks.isFriend(player));
      }
   }

   private boolean weaponAllowed(ItemStack held) {
      return this.anyItem.get()
         ? true
         : this.swords.get() && held.is(ItemTags.SWORDS) || this.axes.get() && held.is(ItemTags.AXES) || this.mace.get() && held.is(Items.MACE);
   }

   private boolean critsAllow(LocalPlayer player, Entity target) {
      String var3 = this.crits.get();

      return switch (var3) {
         case "Only" -> MeleeAttacks.wouldCrit(player, target);
         case "Smart" -> MeleeAttacks.critStance(player, target) && !MeleeAttacks.wouldCrit(player, target)
            ? this.airborneTick >= 0 && this.ticks - this.airborneTick >= 12
            : true;
         default -> true;
      };
   }
}
