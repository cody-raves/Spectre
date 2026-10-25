package net.spectre.client.module.impl.movement;

import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.combat.WTapRecodeModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SprintModule extends ClientModule {
   public static volatile SprintModule active;
   private final ModeSetting mode = this.addSetting("sprint-mode", new ModeSetting("Sprint Mode", "Strict", "Strict", "Rage"));
   private final BooleanSetting keepSprint = this.addSetting("keep-sprint", new BooleanSetting("Keep Sprint", false));
   private final BooleanSetting unsprintOnHit = this.addSetting("unsprint-on-hit", new BooleanSetting("Unsprint On Hit", false));
   private final BooleanSetting unsprintInWater = this.addSetting("unsprint-in-water", new BooleanSetting("Unsprint In Water", true));
   private final BooleanSetting permaSprint = this.addSetting("sprint-while-stationary", new BooleanSetting("Sprint While Stationary", false));

   public SprintModule() {
      super("sprint", "Sprint", "Automatically sprints.", ModuleCategory.MOVEMENT);
      this.unsprintInWater.visibleWhen(() -> this.mode.is("Rage"));
      this.permaSprint.visibleWhen(() -> this.mode.is("Rage"));
   }

   @Override
   protected void onEnable() {
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, EventPriority.HIGH, this::onPostTick);
      this.on(PacketSendEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   private void onPostTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && !isWTapTapping()) {
         if (!this.unsprintInWater.get() || !player.isInWater()) {
            player.setSprinting(this.shouldSprint());
         }
      }
   }

   private void onPacketSend(PacketSendEvent event) {
      if (this.unsprintOnHit.get()) {
         if (event.packet instanceof ServerboundAttackPacket packet) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            ClientPacketListener connection = mc.getConnection();
            if (mc.isSameThread() && player != null && connection != null && isAttack(packet)) {
               connection.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
               player.setSprinting(false);
            }
         }
      }
   }

   public void onPacketSent(Packet<?> sent) {
      if (this.unsprintOnHit.get() && this.keepSprint.get()) {
         if (sent instanceof ServerboundAttackPacket packet) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            ClientPacketListener connection = mc.getConnection();
            if (mc.isSameThread() && player != null && connection != null && isAttack(packet)) {
               if (!isWTapTapping()) {
                  if (this.shouldSprint() && !player.isSprinting()) {
                     connection.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
                     player.setSprinting(true);
                  }
               }
            }
         }
      }
   }

   public boolean shouldSprint() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.isEnabled() && player != null) {
         boolean rage = this.mode.is("Rage");
         float movement = rage ? Math.abs(player.zza) + Math.abs(player.xxa) : player.zza;
         double threshold = player.isUnderWater() ? 1.0E-5F : 0.8;
         if (!(movement <= threshold) || rage && this.permaSprint.get()) {
            if (rage) {
               return true;
            } else if (player.isMobilityRestricted()) {
               return false;
            } else if (!player.getAbilities().flying && player.isInShallowWater()) {
               return false;
            } else if (player.isPassenger()) {
               Entity vehicle = player.getVehicle();
               return vehicle != null && vehicle.canSprint() && vehicle.isLocalInstanceAuthoritative();
            } else {
               return player.getFoodData().hasEnoughFood() && (!player.horizontalCollision || player.minorHorizontalCollision);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean rageSprint() {
      return this.isEnabled() && this.mode.is("Rage") && !isWTapTapping();
   }

   public boolean unsprintInWater() {
      return this.isEnabled() && this.unsprintInWater.get();
   }

   public boolean stopSprinting() {
      return !this.isEnabled() || !this.keepSprint.get();
   }

   private static boolean isAttack(ServerboundAttackPacket packet) { return true; }

   private static boolean isWTapTapping() {
      WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
      return wTap != null && wTap.isTapping();
   }
}
