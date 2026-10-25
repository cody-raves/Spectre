package net.spectre.client.module.impl.misc;

import java.util.ArrayList;
import java.util.List;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.event.impl.WorldEntityAddEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

@Environment(EnvType.CLIENT)
public final class AntiTrapModule extends ClientModule {
   private static final int SWEEP_INTERVAL_TICKS = 20;
   private final BooleanSetting removeExisting = this.addSetting("remove-existing", new BooleanSetting("Remove Existing", true));
   private final BooleanSetting preventSpawn = this.addSetting("prevent-spawn", new BooleanSetting("Prevent Spawn", true));
   private final BooleanSetting armorStands = this.addSetting("armor-stands", new BooleanSetting("Armor Stands", true));
   private final BooleanSetting chestMinecarts = this.addSetting("chest-minecarts", new BooleanSetting("Chest Minecarts", true));
   private final List<Entity> pendingSpawns = new ArrayList<>();

   public AntiTrapModule() {
      super("anti-trap", "Anti Trap", "Allows you to escape from armor stands and chest minecarts.", ModuleCategory.DONUT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.pendingSpawns.clear();
      this.on(WorldEntityAddEvent.TYPE, this::onEntityAdded);
      this.on(TickEvent.TYPE, this::onTick);
      if (this.removeExisting.get()) {
         this.removeTrapEntities();
      }
   }

   @Override
   protected void onDisable() {
      this.pendingSpawns.clear();
   }

   private void onEntityAdded(WorldEntityAddEvent event) {
      if (this.preventSpawn.get()) {
         Entity entity = event.entity;
         if (this.isTrapEntity(entity)) {
            this.pendingSpawns.add(entity);
         }
      }
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      LocalPlayer player = mc.player;
      if (level != null && player != null) {
         if (!this.pendingSpawns.isEmpty()) {
            for (Entity entity : this.pendingSpawns) {
               if (!entity.isRemoved() && entity.level() == level) {
                  Entity idHolder = level.getEntity(entity.getId());
                  if ((idHolder == null || idHolder == entity) && this.isTrapEntity(entity) && canRemove(mc, player, entity)) {
                     entity.discard();
                  }
               }
            }

            this.pendingSpawns.clear();
         }

         if (player.tickCount % 20 == 0) {
            for (Entity entityx : this.collectTrapEntities(mc, level, player)) {
               entityx.discard();
            }
         }
      } else {
         this.pendingSpawns.clear();
      }
   }

   private void removeTrapEntities() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != null) {
         List<Entity> traps = this.collectTrapEntities(mc, level, mc.player);

         for (Entity entity : traps) {
            entity.discard();
         }

         if (!traps.isEmpty()) {
            ModuleChat.send(this, ModuleChat.body().good("Removed").text(" ").value(traps.size()).text(traps.size() == 1 ? " trap entity" : " trap entities"));
         }
      }
   }

   private List<Entity> collectTrapEntities(Minecraft mc, ClientLevel level, LocalPlayer player) {
      List<Entity> traps = new ArrayList<>();

      for (Entity entity : level.entitiesForRendering()) {
         if (this.isTrapEntity(entity) && canRemove(mc, player, entity)) {
            traps.add(entity);
         }
      }

      return traps;
   }

   private boolean isTrapEntity(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         EntityType<?> type = entity.getType();
         return this.armorStands.get() && type == net.minecraft.world.entity.EntityTypes.ARMOR_STAND ? true : this.chestMinecarts.get() && type == net.minecraft.world.entity.EntityTypes.CHEST_MINECART;
      }
   }

   private static boolean canRemove(Minecraft mc, LocalPlayer player, Entity entity) {
      if (entity == mc.getCameraEntity()) {
         return false;
      } else {
         return player == null ? true : entity != player && !entity.hasIndirectPassenger(player);
      }
   }
}
