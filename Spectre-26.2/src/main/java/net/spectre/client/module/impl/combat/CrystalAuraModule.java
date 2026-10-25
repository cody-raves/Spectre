package net.spectre.client.module.impl.combat;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.crystal.Bypass;
import net.spectre.client.module.impl.combat.crystal.CrystalDamage;
import net.spectre.client.module.impl.combat.crystal.Crystals;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.impl.misc.AutoEatModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.pvp.Rotation;
import net.spectre.client.util.pvp.RotationUtils;
import net.spectre.client.util.pvp.ServerRotation;
import net.spectre.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CrystalAuraModule extends ClientModule {
   private static final int COUNTER_CAP = 1000;
   private static final int IDLE_TICKS = 10;
   private static final float FACE_PLACE_HEALTH = 10.0F;
   private static final double AIM_INSET = 0.05;
   private static final int MAX_TARGETS = 3;
   private static final int MAX_BREAK_ATTEMPTS = 3;
   private static final float LETHAL_BONUS = 20.0F;
   private static final float LOOK_BONUS = 2.0F;
   private static final int MAX_LEAD_TICKS = 5;
   private static final double MAX_STEP_SQR = 1.0;
   private static final float LOOK_MATCH_DEGREES = 2.0F;
   private static final double PLACE_BREAK_MARGIN = 0.1;
   private static final float ONE_LOOK_BONUS = 2.0F;
   private static final double GRAVITY = 0.08;
   private static final double AIR_DRAG = 0.98;
   private static final float OBSIDIAN_UPGRADE_BAR = 8.0F;
   private static final float OBSIDIAN_MIN_GAIN = 5.0F;
   private static final float OBSIDIAN_MIN_RATIO = 2.0F;
   private final BooleanSetting requireHold = this.addSetting("require-hold", new BooleanSetting("Require Hold", false));
   private final KeySetting holdKey = this.addSetting("hold-key", new KeySetting("Hold Key", 1003));
   private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 20.0F));
   private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 20.0F));
   private final NumberSetting breakRange = this.addSetting("break-range", new NumberSetting("Break Range", 3.0F, 1.0F, 6.0F));
   private final NumberSetting placeRange = this.addSetting("place-range", new NumberSetting("Place Range", 4.5F, 1.0F, 6.0F));
   private final NumberSetting targetRange = this.addSetting("target-range", new NumberSetting("Target Range", 8.0F, 1.0F, 12.0F));
   private final NumberSetting minDamage = this.addSetting("min-damage", new NumberSetting("Min Damage", 4.0F, 0.0F, 36.0F));
   private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 8.0F, 0.0F, 36.0F));
   private final BooleanSetting facePlace = this.addSetting("face-place", new BooleanSetting("Face Place", true));
   private final BooleanSetting skipFriends = this.addSetting("skip-friends", new BooleanSetting("Skip Friends", true));
   private final BooleanSetting ignoreTerrain = this.addSetting("ignore-terrain", new BooleanSetting("Ignore Terrain", true));
   private final ModeSetting damageMode = this.addSetting("damage-mode", new ModeSetting("Damage Mode", "Normal", "Normal", "Safe"));
   private final NumberSetting minPlaceRatio = this.addSetting("min-place-ratio", new NumberSetting("Min Place Ratio", 1.4F, 0.0F, 5.0F));
   private final NumberSetting minExplode = this.addSetting("min-explode", new NumberSetting("Min Explode", 2.5F, 0.0F, 20.0F));
   private final NumberSetting maxExplode = this.addSetting("max-explode", new NumberSetting("Max Explode", 9.0F, 0.0F, 36.0F));
   private final NumberSetting minExplodeRatio = this.addSetting("min-explode-ratio", new NumberSetting("Min Explode Ratio", 1.1F, 0.0F, 5.0F));
   private final NumberSetting forcePop = this.addSetting("force-pop", new NumberSetting("Force Pop", 1.0F, 0.0F, 10.0F));
   private final NumberSetting antiSelfPop = this.addSetting("anti-self-pop", new NumberSetting("Anti Self Pop", 1.0F, 0.0F, 10.0F));
   private final NumberSetting antiFriendPop = this.addSetting("anti-friend-pop", new NumberSetting("Anti Friend Pop", 1.0F, 0.0F, 10.0F));
   private final NumberSetting maxFriendPlace = this.addSetting("max-friend-place", new NumberSetting("Max Friend Place", 8.0F, 0.0F, 36.0F));
   private final NumberSetting maxFriendExplode = this.addSetting("max-friend-explode", new NumberSetting("Max Friend Explode", 12.0F, 0.0F, 36.0F));
   private final NumberSetting minFriendRatio = this.addSetting("min-friend-ratio", new NumberSetting("Min Friend Ratio", 2.0F, 0.0F, 5.0F));
   private final NumberSetting slowDamage = this.addSetting("slow-damage", new NumberSetting("Slow Damage", 3.0F, 0.0F, 20.0F));
   private final NumberSetting slowPlaceDelay = this.addSetting("slow-place-delay", new NumberSetting("Slow Place Delay", 10.0F, 0.0F, 20.0F));
   private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
   private final NumberSetting obsidianDelay = this.addSetting("obsidian-delay", new NumberSetting("Obsidian Delay", 1.0F, 0.0F, 20.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 0.0F, 0.0F, 4.0F));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final ModeSetting acProfile = this.addSetting("ac-profile", new ModeSetting("AC Profile", "Grim-Aldenz", "Off", "Grim-Aldenz"));
   private Bypass.Profile profile = Bypass.Profile.OFF;
   private boolean placeSlow;
   private boolean engaged;
   private int idleTicks;
   private int savedSlot = -1;
   private int ownSlot = -1;
   private int ticksSinceBreak = 1000;
   private int ticksSincePlace = 1000;
   private int ticksSinceSwitch = 1000;
   private int breakJitter;
   private int placeJitter;
   private int switchSettle = 1;
   private int pendingBreakId = -1;
   private int pendingBreakAge = 1000;
   private BlockPos pendingCrystalBase;
   private int pendingCrystalAge = 1000;
   private BlockPos pendingObsidian;
   private int pendingObsidianAge = 1000;
   private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
   private final Bypass.Budget budget = new Bypass.Budget();

   public CrystalAuraModule() {
      super("crystal-aura", "Crystal Aura", "Places and breaks end crystals for the most damage.", ModuleCategory.COMBAT);
      this.holdKey.visibleWhen(this.requireHold::get);
      this.obsidianDelay.visibleWhen(this.autoObsidian::get);
   }

   @Override
   protected void onEnable() {
      this.resetState();
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.disengage(mc, mc.player);
      this.resetState();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      this.ageCounters();
      this.profile = Bypass.Profile.of(this.acProfile.get());
      CrystalDamage.ignoreTerrain = this.ignoreTerrain.get();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && mc.gameMode != null && !player.isDeadOrDying()) {
         this.forgetGoneCrystals(mc.level);
         if (!this.canRun(mc, player)) {
            this.disengage(mc, player);
         } else if (!AutoInvTotemModule.wantsHotbar() && !CombatMacroLock.heldByOther(this)) {
            if (this.ownSlot != -1 && !this.ownsSelection(player)) {
               this.forgetSlots();
            }

            if (!player.isHandsBusy() && (this.ownsSelection(player) || mayTakeHand(player))) {
               if (player.isUsingItem() && !Bypass.allowAttackWhileUsing(this.profile)) {
                  this.idle(mc, player);
               } else {
                  List<LivingEntity> enemies = this.findTargets(mc, player);
                  if (enemies.isEmpty()) {
                     this.idle(mc, player);
                  } else {
                     CrystalAuraModule.Look look = !Bypass.requiresLook(this.profile)
                        ? CrystalAuraModule.Look.INSTANT
                        : (ServerRotation.correctable(player) ? CrystalAuraModule.Look.SILENT : CrystalAuraModule.Look.CROSSHAIR);
                     Rotation fixedLook = null;
                     if (look == CrystalAuraModule.Look.CROSSHAIR) {
                        fixedLook = RotationUtils.entityRotation(player);
                        if (!sameLook(ServerRotation.sent(), fixedLook)) {
                           this.idle(mc, player);
                           return;
                        }
                     }

                     int window = Bypass.confirmTicks(this.profile, Crystals.confirmWindow(mc, player));
                     int trust = Crystals.confirmWindow(mc, player);
                     EndCrystal lastHit = this.lastHit(mc.level, trust);
                     if (this.pendingCrystalBase != null && Crystals.hasCrystal(mc.level, this.pendingCrystalBase, lastHit)) {
                        this.pendingCrystalBase = null;
                     }

                     CrystalAuraModule.Fight fight = new CrystalAuraModule.Fight(
                        player,
                        mc.level,
                        enemies,
                        this.findFriends(mc, player),
                        window,
                        Crystals.latencyMs(mc, player),
                        fixedLook,
                        look == CrystalAuraModule.Look.SILENT ? 2.0F : 0.0F
                     );
                     CrystalAuraModule.Plan breakPlan = this.planBreak(fight, lastHit);
                     if (breakPlan != null && this.due(mc, breakPlan)) {
                        this.engage();
                        if (this.perform(mc, player, breakPlan, look)) {
                           if (Bypass.allowSameTickPlaceAndBreak(this.profile)) {
                              CrystalAuraModule.Plan refill = look != CrystalAuraModule.Look.INSTANT
                                 ? this.planRefill(fight, breakPlan.crystal())
                                 : this.planPlacement(fight, breakPlan.crystal(), true);
                              if (refill != null) {
                                 this.perform(mc, player, refill, look);
                              }

                              if (Bypass.allowMultiBreak(this.profile) && this.budget.allows(this.profile)) {
                                 CrystalAuraModule.Plan another = this.planBreak(fight, lastHit);
                                 if (another != null && another.crystal() != breakPlan.crystal()) {
                                    this.perform(mc, player, another, look);
                                 }
                              }

                              if (Bypass.allowMultiPlace(this.profile) && this.budget.allows(this.profile)) {
                                 CrystalAuraModule.Plan extra = this.planPlacement(fight, breakPlan.crystal(), true);
                                 if (extra != null && extra.target() != null && (refill == null || !extra.target().equals(refill.target()))) {
                                    this.perform(mc, player, extra, look);
                                 }
                              }
                           }
                        }
                     } else {
                        boolean baseComing = breakPlan != null || lastHit != null || this.pendingCrystalBase != null && this.pendingCrystalAge < trust;
                        CrystalAuraModule.Plan placePlan = this.planPlacement(fight, lastHit, !baseComing);
                        this.placeSlow = placePlan != null
                           && placePlan.step() == CrystalAuraModule.Step.CRYSTAL
                           && this.enemyDamage(fight, placePlan) <= this.slowDamage.get();
                        if (placePlan == null && lastHit != null) {
                           CrystalAuraModule.Plan retry = this.planBreak(fight, null);
                           if (retry != null && this.due(mc, retry)) {
                              this.engage();
                              this.perform(mc, player, retry, look);
                              return;
                           }
                        }

                        CrystalAuraModule.Plan next = placePlan != null ? placePlan : breakPlan;
                        if (next == null) {
                           this.idle(mc, player);
                        } else {
                           this.engage();
                           this.perform(mc, player, next, look);
                        }
                     }
                  }
               }
            } else {
               this.idle(mc, player);
            }
         } else {
            CombatMacroLock.release(this);
         }
      } else {
         this.disengage(mc, null);
      }
   }

   @EventHandler
   public void onAttack(PlayerAttackEntityEvent event) {
      if (event.target instanceof EndCrystal crystal && crystal.level().isClientSide()) {
         this.markBreak(crystal);
      }
   }

   private boolean canRun(Minecraft mc, LocalPlayer player) {
      return MeleeAttacks.canAct(mc) && !player.isSpectator() ? !this.requireHold.get() || Crystals.isKeyDown(mc, this.holdKey.value()) : false;
   }

   private static boolean mayTakeHand(LocalPlayer player) {
      ItemStack main = player.getMainHandItem();
      return main.is(Items.END_CRYSTAL) || main.is(Items.OBSIDIAN) || Crystals.isCombatItem(main);
   }

   private boolean ownsSelection(LocalPlayer player) {
      return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
   }

   private void engage() {
      this.engaged = true;
      this.idleTicks = 0;
      CombatMacroLock.tryAcquire(this);
   }

   private void idle(Minecraft mc, LocalPlayer player) {
      if (this.engaged && ++this.idleTicks >= 10) {
         this.disengage(mc, player);
      }
   }

   private void disengage(Minecraft mc, LocalPlayer player) {
      this.engaged = false;
      this.idleTicks = 0;
      CombatMacroLock.release(this);
      if (player == null) {
         this.forgetSlots();
      } else if (mc.gui.screen() == null && !AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
         this.restoreSlot(player);
      }
   }

   private void restoreSlot(LocalPlayer player) {
      if (this.ownSlot != -1) {
         Inventory inventory = player.getInventory();
         if (this.switchBack.get() && Inventory.isHotbarSlot(this.savedSlot) && this.savedSlot != this.ownSlot && inventory.getSelectedSlot() == this.ownSlot) {
            inventory.setSelectedSlot(this.savedSlot);
            this.ticksSinceSwitch = 0;
         }

         this.forgetSlots();
      }
   }

   private void forgetSlots() {
      this.savedSlot = -1;
      this.ownSlot = -1;
   }

   private void resetState() {
      this.engaged = false;
      this.idleTicks = 0;
      this.forgetSlots();
      this.ticksSinceBreak = 1000;
      this.ticksSincePlace = 1000;
      this.ticksSinceSwitch = 1000;
      this.breakJitter = 0;
      this.placeJitter = 0;
      this.switchSettle = 1;
      this.pendingBreakId = -1;
      this.pendingBreakAge = 1000;
      this.pendingCrystalBase = null;
      this.pendingCrystalAge = 1000;
      this.pendingObsidian = null;
      this.pendingObsidianAge = 1000;
      this.breakAttempts.clear();
      this.placeSlow = false;
      this.budget.reset();
      CombatMacroLock.release(this);
   }

   private void forgetGoneCrystals(ClientLevel level) {
      IntIterator ids = this.breakAttempts.keySet().iterator();

      while (ids.hasNext()) {
         if (level.getEntity(ids.nextInt()) == null) {
            ids.remove();
         }
      }
   }

   private float breakReach() {
      return Bypass.clampBreakRange(this.profile, this.breakRange.get());
   }

   private float placeReach() {
      return Bypass.clampPlaceRange(this.profile, this.placeRange.get());
   }

   private static boolean oneLook(Vec3 eye, AABB crystal, Vec3 placeAim, double reach) {
      Vec3 direction = placeAim.subtract(eye).normalize();
      return crystal.clip(eye, eye.add(direction.scale(reach))).isPresent();
   }

   private static Vec3 lead(LivingEntity entity, int ticks) {
      double dx = entity.getX() - entity.xo;
      double dz = entity.getZ() - entity.zo;
      if (ticks > 0 && !(dx * dx + dz * dz > 1.0)) {
         double dy = 0.0;
         if (!entity.onGround()) {
            double speed = entity.getY() - entity.yo;

            for (int tick = 0; tick < ticks; tick++) {
               speed = (speed - 0.08) * 0.98;
               dy += speed;
            }
         }

         return new Vec3(dx * ticks, dy, dz * ticks);
      } else {
         return Vec3.ZERO;
      }
   }

   private boolean canHostSoon(CrystalAuraModule.Fight fight, BlockPos base, Entity breaking) {
      BlockPos above = base.above();
      if (!fight.level.isEmptyBlock(above)) {
         return false;
      } else {
         AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);

         for (Entity entity : fight.level.getEntities(breaking, space)) {
            if (entity.isAlive()) {
               if (!(entity instanceof LivingEntity living) || !fight.enemies.contains(living)) {
                  return false;
               }

               AABB then = living.getBoundingBox().move(lead(living, fight.arriveLead));
               boolean over = then.minY >= space.maxY;
               boolean aside = then.maxX <= space.minX || then.minX >= space.maxX || then.maxZ <= space.minZ || then.minZ >= space.maxZ;
               if (!over && !aside) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private List<LivingEntity> findTargets(Minecraft mc, LocalPlayer player) {
      double range = this.targetRange.get() * this.targetRange.get();
      List<LivingEntity> enemies = new ArrayList<>();

      for (AbstractClientPlayer other : mc.level.players()) {
         if (MeleeAttacks.isHittable(player, other) && (!this.skipFriends.get() || !MeleeAttacks.isFriend(other)) && player.distanceToSqr(other) < range) {
            enemies.add(other);
         }
      }

      enemies.sort(Comparator.comparingDouble(enemy -> player.distanceToSqr(enemy)));
      return enemies.size() > 3 ? enemies.subList(0, 3) : enemies;
   }

   private List<LivingEntity> findFriends(Minecraft mc, LocalPlayer player) {
      if (!this.skipFriends.get()) {
         return List.of();
      } else {
         double range = this.targetRange.get() * this.targetRange.get();
         List<LivingEntity> friends = new ArrayList<>();

         for (AbstractClientPlayer other : mc.level.players()) {
            if (other != player && other.isAlive() && MeleeAttacks.isFriend(other) && player.distanceToSqr(other) < range) {
               friends.add(other);
            }
         }

         return friends;
      }
   }

   private EndCrystal lastHit(ClientLevel level, int trust) {
      if (this.pendingBreakId >= 0 && this.pendingBreakAge < trust) {
         return level.getEntity(this.pendingBreakId) instanceof EndCrystal crystal && crystal.isAlive() ? crystal : null;
      } else {
         return null;
      }
   }

   private CrystalAuraModule.Plan planBreak(CrystalAuraModule.Fight fight, EndCrystal skip) {
      float reach = this.breakReach();
      CrystalAuraModule.Side side = fight.side(CrystalAuraModule.Step.BREAK);
      CrystalAuraModule.Plan best = null;
      float bestScore = Float.NEGATIVE_INFINITY;
      AABB around = fight.player.getBoundingBox().inflate(reach + 2.0);

      for (EndCrystal crystal : fight.level.getEntitiesOfClass(EndCrystal.class, around)) {
         if (crystal != skip
            && crystal.isAlive()
            && this.breakAttempts.get(crystal.getId()) < 3
            && Bypass.mayRetryCrystal(this.profile, crystal.getId(), this.pendingBreakId, this.pendingBreakAge, fight.window)) {
            Vec3 eye = fight.eye;
            if (!(eye.distanceToSqr(TargetingUtils.closestPoint(eye, crystal.getBoundingBox())) > reach * reach)) {
               Vec3 center = crystal.position();
               if (!this.normalRank() || !(this.bound(side, center) + fight.lookBonus <= bestScore)) {
                  CrystalAuraModule.Plan plan = this.breakLook(fight, crystal);
                  if (plan != null) {
                     float score = this.score(side, center, bestScore - fight.lookBonus, false, true);
                     if (score != Float.NEGATIVE_INFINITY) {
                        score += fight.lookCredit(plan);
                        if (!(score <= bestScore)) {
                           best = plan;
                           bestScore = score;
                        }
                     }
                  }
               }
            }
         }
      }

      if (best == null) {
         return null;
      } else {
         int weapon = weaponRoute(fight.player);
         return weapon == -1 ? null : best.withRoute(weapon);
      }
   }

   private CrystalAuraModule.Plan breakLook(CrystalAuraModule.Fight fight, EndCrystal crystal) {
      if (fight.fixedLook != null) {
         CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, crystal.getBoundingBox().getCenter(), -2);
         return this.hitAlong(fight.player, plan, fight.eye, fight.fixedLook) != null ? plan : null;
      } else {
         BlockPos base = crystal.blockPosition().below();
         if (Crystals.isBase(fight.level.getBlockState(base))) {
            CrystalAuraModule.Plan throughBase = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, base, base, aimOnTop(fight.level, base), -2);
            if (this.hitAlong(fight.player, throughBase, fight.eye, fight.lookAt(throughBase.aim())) != null) {
               return throughBase;
            }
         }

         Vec3 nearest = TargetingUtils.closestPoint(fight.eye, crystal.getBoundingBox().deflate(0.05));
         CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, nearest, -2);
         return this.hitAlong(fight.player, plan, fight.eye, fight.lookAt(nearest)) != null ? plan : null;
      }
   }

   private static int weaponRoute(LocalPlayer player) {
      if (meleeDamage(player, player.getMainHandItem()) > 0.0) {
         return -2;
      } else {
         Inventory inventory = player.getInventory();
         int best = -1;
         double bestDamage = 0.0;

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            double damage = meleeDamage(player, inventory.getItem(slot));
            if (damage > bestDamage && slot != inventory.getSelectedSlot()) {
               bestDamage = damage;
               best = slot;
            }
         }

         return best;
      }
   }

   private static double meleeDamage(LocalPlayer player, ItemStack stack) {
      double damage = 1.0 + MeleeAttacks.attackDamage(stack);
      MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
      if (weakness != null) {
         damage -= 4.0 * (weakness.getAmplifier() + 1);
      }

      MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
      if (strength != null) {
         damage += 3.0 * (strength.getAmplifier() + 1);
      }

      return damage;
   }

   private CrystalAuraModule.Plan planRefill(CrystalAuraModule.Fight fight, EndCrystal broken) {
      ClientLevel level = fight.level;
      BlockPos base = broken.blockPosition().below();
      if (!Crystals.isBase(level.getBlockState(base))) {
         return null;
      } else if (base.equals(this.pendingCrystalBase) && this.pendingCrystalAge < fight.window) {
         return null;
      } else if (Crystals.route(fight.player, Items.END_CRYSTAL, level.getBlockState(base), this.savedSlot) != -2) {
         return null;
      } else if (!this.canHostSoon(fight, base, broken)) {
         return null;
      } else {
         return this.score(fight.side(CrystalAuraModule.Step.CRYSTAL), Crystals.explosionAt(base), Float.NEGATIVE_INFINITY, false, false)
               == Float.NEGATIVE_INFINITY
            ? null
            : new CrystalAuraModule.Plan(CrystalAuraModule.Step.CRYSTAL, null, base, base, aimOnTop(level, base), -2);
      }
   }

   private CrystalAuraModule.Plan planPlacement(CrystalAuraModule.Fight fight, EndCrystal breaking, boolean mayLayBase) {
      CrystalAuraModule.Plan crystal = this.planBase(fight, CrystalAuraModule.Step.CRYSTAL, breaking);
      if (this.autoObsidian.get() && mayLayBase) {
         float crystalDamage = crystal == null ? 0.0F : this.enemyDamage(fight, crystal);
         if (crystal != null && crystalDamage >= 8.0F) {
            return crystal;
         } else {
            CrystalAuraModule.Plan obsidian = this.planBase(fight, CrystalAuraModule.Step.OBSIDIAN, null);
            if (obsidian != null && crystal != null) {
               float obsidianDamage = this.enemyDamage(fight, obsidian);
               boolean upgrade = obsidianDamage >= crystalDamage + 5.0F && obsidianDamage >= crystalDamage * 2.0F;
               return upgrade ? obsidian : crystal;
            } else {
               return obsidian == null ? crystal : obsidian;
            }
         }
      } else {
         return crystal;
      }
   }

   private float enemyDamage(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
      Vec3 center = Crystals.explosionAt(plan.target());
      float most = 0.0F;

      for (CrystalDamage.Victim enemy : fight.side(plan.step()).enemies) {
         most = Math.max(most, enemy.damage(center, plan.step() == CrystalAuraModule.Step.OBSIDIAN));
      }

      return most;
   }

   private CrystalAuraModule.Plan planBase(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, EndCrystal breaking) {
      LocalPlayer player = fight.player;
      ClientLevel level = fight.level;
      Item item = step == CrystalAuraModule.Step.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
      BlockState anyBase = Blocks.OBSIDIAN.defaultBlockState();
      if (Crystals.route(player, item, anyBase, this.savedSlot) == -1) {
         return null;
      } else if (step == CrystalAuraModule.Step.OBSIDIAN && Crystals.route(player, Items.END_CRYSTAL, anyBase, this.savedSlot) == -1) {
         return null;
      } else {
         CrystalAuraModule.Side side = fight.side(step);
         float placeReach = this.placeReach();
         float breakReach = this.breakReach();
         Vec3 eye = fight.eye;
         BlockPos origin = BlockPos.containing(eye);
         int radius = Mth.ceil(placeReach);
         BlockPos pending = step == CrystalAuraModule.Step.CRYSTAL ? this.pendingCrystalBase : this.pendingObsidian;
         int pendingAge = step == CrystalAuraModule.Step.CRYSTAL ? this.pendingCrystalAge : this.pendingObsidianAge;
         List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
         MutableBlockPos cell = new MutableBlockPos();

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  cell.setWithOffset(origin, dx, dy, dz);
                  BlockState state = level.getBlockState(cell);
                  if (step == CrystalAuraModule.Step.CRYSTAL ? Crystals.isBase(state) : state.canBeReplaced()) {
                     BlockPos base = cell.immutable();
                     BlockPos clicked = step == CrystalAuraModule.Step.CRYSTAL ? base : base.below();
                     if ((step != CrystalAuraModule.Step.OBSIDIAN || level.getBlockState(clicked).isFaceSturdy(level, clicked, Direction.UP))
                        && (!base.equals(pending) || pendingAge >= fight.window)) {
                        Vec3 aim = aimOnTop(level, clicked);
                        if (!(eye.distanceToSqr(aim) > placeReach * placeReach)) {
                           AABB crystal = Crystals.boxOn(base);
                           double breakable = breakReach - 0.1;
                           if (!(eye.distanceToSqr(TargetingUtils.closestPoint(eye, crystal)) > breakable * breakable)) {
                              float bound = this.bound(side, Crystals.explosionAt(base));
                              if (bound != Float.NEGATIVE_INFINITY) {
                                 float cycle = step == CrystalAuraModule.Step.CRYSTAL && fight.lookBonus > 0.0F && oneLook(eye, crystal, aim, breakable)
                                    ? 2.0F
                                    : 0.0F;
                                 candidates.add(
                                    new CrystalAuraModule.Candidate(new CrystalAuraModule.Plan(step, null, clicked, base, aim, -2), bound + cycle, cycle)
                                 );
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
         CrystalAuraModule.Plan best = null;
         float bestScore = Float.NEGATIVE_INFINITY;

         for (CrystalAuraModule.Candidate candidate : candidates) {
            if (this.normalRank() && candidate.bound() + fight.lookBonus <= bestScore) {
               break;
            }

            CrystalAuraModule.Plan found = candidate.plan();
            float score = this.score(
               side, Crystals.explosionAt(found.target()), bestScore - fight.lookBonus - candidate.cycle(), step == CrystalAuraModule.Step.OBSIDIAN, false
            );
            if (score != Float.NEGATIVE_INFINITY
               && level.getWorldBorder().isWithinBounds(found.clicked())
               && this.canHostSoon(fight, found.target(), step == CrystalAuraModule.Step.CRYSTAL ? breaking : null)) {
               CrystalAuraModule.Plan plan = this.reachable(fight, found);
               if (plan != null) {
                  score += fight.lookCredit(plan) + candidate.cycle();
                  if (!(score <= bestScore)) {
                     best = plan;
                     bestScore = score;
                  }
               }
            }
         }

         if (best == null) {
            return null;
         } else {
            int route = Crystals.route(player, item, level.getBlockState(best.clicked()), this.savedSlot);
            return route == -1 ? null : best.withRoute(route);
         }
      }
   }

   private CrystalAuraModule.Plan reachable(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
      if (this.hitAlong(fight.player, plan, fight.eye, fight.lookAt(plan.aim())) != null) {
         return plan;
      } else if (plan.step() == CrystalAuraModule.Step.CRYSTAL && fight.fixedLook == null) {
         Vec3 middle = Vec3.atCenterOf(plan.clicked());

         for (Direction side : Plane.HORIZONTAL) {
            Vec3 face = middle.add(side.getStepX() * 0.45, 0.0, side.getStepZ() * 0.45);
            if (!((fight.eye.x - face.x) * side.getStepX() + (fight.eye.z - face.z) * side.getStepZ() <= 0.0)) {
               CrystalAuraModule.Plan aimed = new CrystalAuraModule.Plan(plan.step(), null, plan.clicked(), plan.target(), face, plan.route());
               if (this.hitAlong(fight.player, aimed, fight.eye, fight.lookAt(face)) != null) {
                  return aimed;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Vec3 aimOnTop(ClientLevel level, BlockPos pos) {
      double top = level.getBlockState(pos).getShape(level, pos).max(Axis.Y);
      return new Vec3(pos.getX() + 0.5, pos.getY() + top - 0.05, pos.getZ() + 0.5);
   }

   private float minimumWorthwhile(LivingEntity enemy) {
      return this.minimumWorthwhile(CrystalDamage.health(enemy));
   }

   private float bound(CrystalAuraModule.Side side, Vec3 center) {
      float best = Float.NEGATIVE_INFINITY;

      for (CrystalDamage.Victim enemy : side.enemies) {
         float upper = enemy.upperBound(center);
         if (!(upper < this.minimumWorthwhile(enemy.entity()))) {
            boolean mayKill = upper >= CrystalDamage.health(enemy.entity());
            best = Math.max(best, upper + (mayKill ? 20.0F : 0.0F));
         }
      }

      return best;
   }

   private float score(CrystalAuraModule.Side side, Vec3 center, float toBeat, boolean baseToCome, boolean breaking) {
      float selfDamage = side.self.damage(center, baseToCome);
      LivingEntity self = side.self.entity();
      float selfHealth = CrystalDamage.health(self);
      boolean creative = self instanceof Player player && player.isCreative();
      if (!creative && this.antiSelfPop.get() > 0.0F && selfDamage * this.antiSelfPop.get() >= selfHealth) {
         return Float.NEGATIVE_INFINITY;
      } else if (CrystalDamage.endangers((LocalPlayer)self, selfDamage)) {
         return Float.NEGATIVE_INFINITY;
      } else {
         float friendDamage = 0.0F;
         float friendHealth = -1.0F;

         for (CrystalDamage.Victim friend : side.friends) {
            float damage = friend.damage(center, baseToCome);
            if (damage > friendDamage) {
               friendDamage = damage;
               friendHealth = CrystalDamage.health(friend.entity());
            }
         }

         if (friendHealth >= 0.0F && this.antiFriendPop.get() > 0.0F && friendDamage * this.antiFriendPop.get() >= friendHealth) {
            return Float.NEGATIVE_INFINITY;
         } else {
            float bestDamage = Float.NEGATIVE_INFINITY;
            float bestHealth = 0.0F;
            boolean kills = false;

            for (CrystalDamage.Victim enemy : side.enemies) {
               float damage = enemy.damage(center, baseToCome);
               if (!(damage <= bestDamage)) {
                  bestDamage = damage;
                  bestHealth = CrystalDamage.health(enemy.entity());
                  kills = CrystalDamage.kills(enemy.entity(), damage);
               }
            }

            if (bestDamage < 0.0F) {
               return Float.NEGATIVE_INFINITY;
            } else {
               float maxSelf = breaking ? this.maxExplode.get() : this.maxSelfDamage.get();
               float maxFriend = breaking ? this.maxFriendExplode.get() : this.maxFriendPlace.get();
               float minRatio = breaking ? this.minExplodeRatio.get() : this.minPlaceRatio.get();
               boolean force = this.forcePop.get() > 0.0F && bestDamage * this.forcePop.get() >= bestHealth;
               if (!force && selfDamage > maxSelf && selfDamage >= bestDamage) {
                  return Float.NEGATIVE_INFINITY;
               } else if (!force && friendDamage > maxFriend && friendDamage >= bestDamage) {
                  return Float.NEGATIVE_INFINITY;
               } else {
                  float minimum = breaking ? this.minExplode.get() : this.minimumWorthwhile(bestHealth);
                  if (!force && bestDamage < minimum && !kills) {
                     return Float.NEGATIVE_INFINITY;
                  } else {
                     float rank = "Safe".equals(this.damageMode.get()) ? bestDamage / Math.max(selfDamage, 0.01F) : bestDamage;
                     if (!force && selfDamage > 0.0F && bestDamage / selfDamage < minRatio) {
                        rank *= bestDamage / selfDamage / minRatio;
                     }

                     if (!force && friendDamage > 0.0F && bestDamage / friendDamage < this.minFriendRatio.get()) {
                        rank *= bestDamage / friendDamage / this.minFriendRatio.get();
                     }

                     if (kills || force) {
                        rank += 20.0F;
                     }

                     return rank > toBeat ? rank : Float.NEGATIVE_INFINITY;
                  }
               }
            }
         }
      }
   }

   private float minimumWorthwhile(float health) {
      return this.facePlace.get() && health <= 10.0F ? 0.0F : Math.min(this.minDamage.get(), health);
   }

   private HitResult hitAlong(LocalPlayer player, CrystalAuraModule.Plan plan, Vec3 eye, Rotation look) {
      if (look == null) {
         return null;
      } else {
         Vec3 direction = Vec3.directionFromRotation(look.pitch(), look.yaw());
         if (plan.step() == CrystalAuraModule.Step.BREAK) {
            AABB box = plan.crystal().getBoundingBox();
            if (box.contains(eye)) {
               return new EntityHitResult(plan.crystal(), eye);
            } else {
               Vec3 at = box.clip(eye, eye.add(direction.scale(this.breakReach()))).orElse(null);
               return at != null && MeleeAttacks.lineIsClear(player, eye, at) ? new EntityHitResult(plan.crystal(), at) : null;
            }
         } else {
            ClientLevel level = (ClientLevel)player.level();
            BlockState clicked = level.getBlockState(plan.clicked());
            BlockHitResult hit = clicked.getShape(level, plan.clicked()).clip(eye, eye.add(direction.scale(this.placeReach())), plan.clicked());
            if (hit != null && MeleeAttacks.lineIsClear(player, eye, hit.getLocation())) {
               return plan.step() == CrystalAuraModule.Step.OBSIDIAN && !plan.target().equals(Crystals.placedObsidian(level, player, hit, clicked))
                  ? null
                  : hit;
            } else {
               return null;
            }
         }
      }
   }

   private boolean perform(Minecraft mc, LocalPlayer player, CrystalAuraModule.Plan plan, CrystalAuraModule.Look look) {
      Vec3 eye = player.getEyePosition();
      Rotation aim = RotationUtils.fromTo(eye, plan.aim());
      Rotation sent = ServerRotation.sent();
      Rotation before = look == CrystalAuraModule.Look.INSTANT ? aim : sent;

      Rotation next = switch (look) {
         case INSTANT -> aim;
         case SILENT -> sent;
         case CROSSHAIR -> RotationUtils.entityRotation(player);
      };
      HitResult hit = before != null && next != null ? this.hitAlong(player, plan, eye, before) : null;
      if (hit != null && next != before && this.hitAlong(player, plan, eye, next) == null) {
         hit = null;
      }

      boolean ready = plan.route() == -2 && this.due(mc, plan) && this.budget.allows(this.profile) && hit != null;
      if (look == CrystalAuraModule.Look.SILENT) {
         ServerRotation.request(ready ? sent : aim);
      }

      if (plan.route() != -2) {
         this.select(player, plan.route());
         return false;
      } else if (!ready) {
         return false;
      } else {
         if (plan.step() == CrystalAuraModule.Step.BREAK) {
            if (!Crystals.strike(mc, player, plan.crystal(), this.profile)) {
               return false;
            }

            this.markBreak(plan.crystal());
            this.breakAttempts.addTo(plan.crystal().getId(), 1);
            this.ticksSinceBreak = 0;
            this.breakJitter = this.rollJitter();
         } else {
            Crystals.rightClick(mc, player, (BlockHitResult)hit);
            if (plan.step() == CrystalAuraModule.Step.CRYSTAL) {
               this.pendingCrystalBase = plan.target();
               this.pendingCrystalAge = 0;
            } else {
               this.pendingObsidian = plan.target();
               this.pendingObsidianAge = 0;
            }

            this.ticksSincePlace = 0;
            this.placeJitter = this.rollJitter();
         }

         this.budget.note();
         return true;
      }
   }

   private static boolean sameLook(Rotation a, Rotation b) {
      return a != null && Math.abs(RotationUtils.yawDelta(a.yaw(), b.yaw())) <= 2.0F && Math.abs(a.pitch() - b.pitch()) <= 2.0F;
   }

   private boolean normalRank() {
      return !"Safe".equals(this.damageMode.get());
   }

   private int placeWait() {
      int delay = this.placeDelay.getInt();
      if (this.placeSlow) {
         delay = Math.max(delay, this.slowPlaceDelay.getInt());
      }

      return delay;
   }

   private boolean due(Minecraft mc, CrystalAuraModule.Plan plan) {
      return switch (plan.step()) {
         case BREAK -> this.ticksSinceBreak >= Bypass.minDelay(this.profile, this.breakDelay.getInt()) + this.breakJitter;
         case CRYSTAL -> this.placeSettled(mc) && this.ticksSincePlace >= Bypass.minDelay(this.profile, this.placeWait()) + this.placeJitter;
         case OBSIDIAN -> this.placeSettled(mc) && this.ticksSincePlace >= Bypass.minDelay(this.profile, this.obsidianDelay.getInt()) + this.placeJitter;
      };
   }

   private boolean placeSettled(Minecraft mc) {
      boolean digging = mc.gameMode.isDestroying() && !Bypass.allowPlaceWhileDigging(this.profile);
      return this.ticksSinceSwitch >= this.switchSettle && !digging;
   }

   private void select(LocalPlayer player, int slot) {
      if (this.ticksSinceSwitch >= Bypass.minSwitchInterval(this.profile) && Inventory.isHotbarSlot(slot)) {
         Inventory inventory = player.getInventory();
         if (this.savedSlot == -1) {
            this.savedSlot = inventory.getSelectedSlot();
         }

         inventory.setSelectedSlot(slot);
         this.ownSlot = slot;
         this.ticksSinceSwitch = 0;
         this.switchSettle = 1 + this.rollJitter();
      }
   }

   private int rollJitter() {
      int spread = this.jitter.getInt();
      return spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0;
   }

   private void markBreak(EndCrystal crystal) {
      this.pendingBreakId = crystal.getId();
      this.pendingBreakAge = 0;
   }

   private void ageCounters() {
      this.ticksSinceBreak = Math.min(this.ticksSinceBreak + 1, 1000);
      this.ticksSincePlace = Math.min(this.ticksSincePlace + 1, 1000);
      this.ticksSinceSwitch = Math.min(this.ticksSinceSwitch + 1, 1000);
      this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
      this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
      this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
   }

   @Environment(EnvType.CLIENT)
   private record Candidate(CrystalAuraModule.Plan plan, float bound, float cycle) {
   }

   @Environment(EnvType.CLIENT)
   private final class Fight {
      final LocalPlayer player;
      final ClientLevel level;
      final Vec3 eye;
      final List<LivingEntity> enemies;
      final List<LivingEntity> friends;
      final int window;
      final Rotation fixedLook;
      final Rotation serverLook;
      final float lookBonus;
      private final int breakLead;
      private final int placeLead;
      final int arriveLead;
      private CrystalAuraModule.Side breakSide;
      private CrystalAuraModule.Side placeSide;

      Fight(
         LocalPlayer player,
         ClientLevel level,
         List<LivingEntity> enemies,
         List<LivingEntity> friends,
         int window,
         int latencyMs,
         Rotation fixedLook,
         float lookBonus
      ) {
         this.player = player;
         this.level = level;
         this.eye = player.getEyePosition();
         this.enemies = enemies;
         this.friends = friends;
         this.window = window;
         this.fixedLook = fixedLook;
         this.serverLook = ServerRotation.sent();
         this.lookBonus = lookBonus;
         int roundTrip = latencyMs / 50;
         this.breakLead = Math.min(roundTrip + 1, 5);
         this.placeLead = Math.min(2 * roundTrip + 2, 5);
         this.arriveLead = Math.min(latencyMs / 100 + 1, 5);
      }

      CrystalAuraModule.Side side(CrystalAuraModule.Step step) {
         if (step == CrystalAuraModule.Step.BREAK) {
            if (this.breakSide == null) {
               this.breakSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.breakLead);
            }

            return this.breakSide;
         } else {
            if (this.placeSide == null) {
               this.placeSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.placeLead);
            }

            return this.placeSide;
         }
      }

      Rotation lookAt(Vec3 aim) {
         return this.fixedLook != null ? this.fixedLook : RotationUtils.fromTo(this.eye, aim);
      }

      float lookCredit(CrystalAuraModule.Plan plan) {
         return this.lookBonus > 0.0F && CrystalAuraModule.this.hitAlong(this.player, plan, this.eye, this.serverLook) != null ? this.lookBonus : 0.0F;
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum Look {
      INSTANT,
      SILENT,
      CROSSHAIR;
   }

   @Environment(EnvType.CLIENT)
   private record Plan(CrystalAuraModule.Step step, EndCrystal crystal, BlockPos clicked, BlockPos target, Vec3 aim, int route) {
      CrystalAuraModule.Plan withRoute(int newRoute) {
         return new CrystalAuraModule.Plan(this.step, this.crystal, this.clicked, this.target, this.aim, newRoute);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Side {
      final List<CrystalDamage.Victim> enemies = new ArrayList<>();
      final List<CrystalDamage.Victim> friends = new ArrayList<>();
      final CrystalDamage.Victim self;

      Side(LocalPlayer player, List<LivingEntity> targets, List<LivingEntity> friends, int leadTicks) {
         for (LivingEntity target : targets) {
            this.enemies.add(new CrystalDamage.Victim(target, CrystalAuraModule.lead(target, leadTicks)));
         }

         for (LivingEntity friend : friends) {
            this.friends.add(new CrystalDamage.Victim(friend, CrystalAuraModule.lead(friend, leadTicks)));
         }

         this.self = new CrystalDamage.Victim(player, CrystalAuraModule.lead(player, leadTicks));
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum Step {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }
}
