package net.spectre.client.module.impl.render;

import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class HitParticlesModule extends ClientModule {
   private static final String TYPE_CRIT = "Crit";
   private static final String TYPE_MAGIC_CRIT = "Magic Crit";
   private static final String TYPE_SPARK = "Spark";
   private static final String TYPE_FLAME = "Flame";
   private static final String TYPE_SOUL_FLAME = "Soul Flame";
   private static final String TYPE_END_ROD = "End Rod";
   private static final String TYPE_TOTEM = "Totem";
   private static final String TYPE_HEART = "Heart";
   private static final String TYPE_DUST = "Dust";
   private static final String ORIGIN_IMPACT = "Impact";
   private static final String ORIGIN_BODY = "Body";
   private static final String TARGETS_LIVING = "Living";
   private static final String TARGETS_PLAYERS = "Players";
   private static final String TARGETS_ALL = "All";
   private static final String COLOR_CUSTOM = "Custom";
   private static final String COLOR_ACCENT = "Accent";
   private static final double LIFT = 0.25;
   private final ModeSetting type = this.addSetting(
      "type", new ModeSetting("Type", "Crit", "Crit", "Magic Crit", "Spark", "Flame", "Soul Flame", "End Rod", "Totem", "Heart", "Dust")
   );
   private final NumberSetting amount = this.addSetting("amount", new NumberSetting("Amount", 10.0F, 1.0F, 40.0F).step(1.0F));
   private final NumberSetting spread = this.addSetting("spread", new NumberSetting("Spread", 1.0F, 0.25F, 3.0F).step(0.25F));
   private final ModeSetting origin = this.addSetting("origin", new ModeSetting("Origin", "Impact", "Impact", "Body"));
   private final BooleanSetting onlyCrits = this.addSetting("only-crits", new BooleanSetting("Only Crits", false));
   private final ModeSetting targets = this.addSetting("targets", new ModeSetting("Targets", "Living", "Living", "Players", "All"));
   private final ModeSetting dustColorMode = this.addSetting("dust-color-mode", new ModeSetting("Color", "Accent", "Custom", "Accent"));
   private final ColorSetting dustColor = this.addSetting("dust-color", new ColorSetting("Custom Color", -657929));
   private final NumberSetting dustSize = this.addSetting("dust-size", new NumberSetting("Dust Size", 1.0F, 0.5F, 2.0F).step(0.1F));
   private final RandomSource random = RandomSource.create();

   public HitParticlesModule() {
      super("hit-particles", "Hit Particles", "Spawns particles where your hits land.", ModuleCategory.VISUALS);
      this.dustColorMode.visibleWhen(() -> this.type.is("Dust"));
      this.dustColor.visibleWhen(() -> this.type.is("Dust") && this.dustColorMode.is("Custom"));
      this.dustSize.visibleWhen(() -> this.type.is("Dust"));
   }

   @Override
   protected void onEnable() {
      this.on(PlayerAttackEntityEvent.TYPE, Stage.PRE, EventPriority.LOWEST, this::onAttack);
   }

   private void onAttack(PlayerAttackEntityEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         LocalPlayer player = mc.player;
         ClientLevel level = mc.level;
         Entity target = event.target;
         if (player != null && level != null && target != null && target != player) {
            if (target.isAttackable() && this.accepts(target)) {
               if (!this.onlyCrits.get() || isCritical(player, target)) {
                  this.spawnBurst(mc, level, player, target);
               }
            }
         }
      }
   }

   private boolean accepts(Entity target) {
      String var2 = this.targets.get();

      return switch (var2) {
         case "Players" -> target instanceof Player;
         case "All" -> true;
         default -> target instanceof LivingEntity;
      };
   }

   private static boolean isCritical(LocalPlayer player, Entity target) {
      return player.getAttackStrengthScale(0.5F) > 0.9F
         && player.fallDistance > 0.0
         && !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.isMobilityRestricted()
         && !player.isPassenger()
         && !player.isSprinting()
         && target instanceof LivingEntity;
   }

   private void spawnBurst(Minecraft mc, ClientLevel level, LocalPlayer player, Entity target) {
      HitParticlesModule.Burst burst = this.burst();
      AABB box = target.getBoundingBox();
      Vec3 center = box.getCenter();
      boolean body = this.origin.is("Body");
      Vec3 impact = body ? null : impactPoint(mc, player, target, box);
      Vec3 fallbackOut = player.getLookAngle().reverse();
      double spreadScale = this.spread.get();
      double jitter = burst.jitter() * spreadScale;
      int count = this.amount.getInt();

      for (int i = 0; i < count; i++) {
         Vec3 from = body ? this.randomPointIn(box) : impact;
         Vec3 out = from.subtract(center);
         out = out.lengthSqr() < 1.0E-6 ? fallbackOut : out.normalize();
         Vec3 dir = out.scale(0.8).add(this.randomUnit()).add(0.0, 0.25, 0.0).normalize();
         double speed = burst.speed() * spreadScale * (0.6 + 0.4 * this.random.nextDouble());
         level.addParticle(
            burst.particle(),
            true,
            false,
            from.x + this.random.nextGaussian() * jitter,
            from.y + this.random.nextGaussian() * jitter,
            from.z + this.random.nextGaussian() * jitter,
            dir.x * speed,
            dir.y * speed,
            dir.z * speed
         );
      }
   }

   private HitParticlesModule.Burst burst() {
      String var1 = this.type.get();

      return switch (var1) {
         case "Magic Crit" -> new HitParticlesModule.Burst(ParticleTypes.ENCHANTED_HIT, 0.8, 0.1);
         case "Spark" -> new HitParticlesModule.Burst(ParticleTypes.ELECTRIC_SPARK, 0.16, 0.1);
         case "Flame" -> new HitParticlesModule.Burst(ParticleTypes.FLAME, 0.035, 0.12);
         case "Soul Flame" -> new HitParticlesModule.Burst(ParticleTypes.SOUL_FIRE_FLAME, 0.035, 0.12);
         case "End Rod" -> new HitParticlesModule.Burst(ParticleTypes.END_ROD, 0.09, 0.1);
         case "Totem" -> new HitParticlesModule.Burst(ParticleTypes.TOTEM_OF_UNDYING, 0.45, 0.1);
         case "Heart" -> new HitParticlesModule.Burst(ParticleTypes.HEART, 0.0, 0.35);
         case "Dust" -> new HitParticlesModule.Burst(new DustParticleOptions(this.dustRgb(), this.dustSize.get()), 1.0, 0.3);
         default -> new HitParticlesModule.Burst(ParticleTypes.CRIT, 0.8, 0.1);
      };
   }

   private int dustRgb() {
      int argb = this.dustColorMode.is("Accent") ? CosmeticAccent.argb() : this.dustColor.argb();
      return argb & 16777215;
   }

   private static Vec3 impactPoint(Minecraft mc, LocalPlayer player, Entity target, AABB box) {
      if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == target) {
         return hit.getLocation();
      } else {
         Vec3 eye = player.getEyePosition();
         return new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ));
      }
   }

   private Vec3 randomPointIn(AABB box) {
      return new Vec3(
         box.minX + box.getXsize() * (0.1 + 0.8 * this.random.nextDouble()),
         box.minY + box.getYsize() * (0.1 + 0.8 * this.random.nextDouble()),
         box.minZ + box.getZsize() * (0.1 + 0.8 * this.random.nextDouble())
      );
   }

   private Vec3 randomUnit() {
      Vec3 v = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian());
      return v.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 1.0, 0.0) : v.normalize();
   }

   @Environment(EnvType.CLIENT)
   private record Burst(ParticleOptions particle, double speed, double jitter) {
   }
}
