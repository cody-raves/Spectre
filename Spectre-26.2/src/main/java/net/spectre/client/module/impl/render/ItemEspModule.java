package net.spectre.client.module.impl.render;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ItemColorListSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class ItemEspModule extends ClientModule {
   private static ItemEspModule instance;
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 128.0F, 10.0F, 512.0F).step(1.0F));
   private final BooleanSetting sOutline = this.addSetting("outline", new BooleanSetting("Outline", true));
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final ItemColorListSetting sItemColors = this.addSetting(ItemColorListSetting.createDefault());

   public ItemEspModule() {
      super("item-esp", "Item ESP", "Highlights selected dropped items with model outlines and tracers.", ModuleCategory.RENDER);
      instance = this;
   }

   public static boolean shouldGlow(Entity entity) {
      ItemEspModule module = instance;
      return module != null && module.isEnabled() && module.sOutline.get() ? entity instanceof ItemEntity item && module.wants(item, 1.0F) : false;
   }

   public static int glowColor(Entity entity) {
      ItemEspModule module = instance;
      return module != null && entity instanceof ItemEntity item ? module.sItemColors.colorArgb(item.getItem().getItem()) & 16777215 : 16777215;
   }

   private boolean wants(ItemEntity item, float partialTick) {
      if (item.isRemoved()) {
         return false;
      } else {
         Item kind = item.getItem().getItem();
         if (!this.sItemColors.isEnabled(kind)) {
            return false;
         } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
               return true;
            } else {
               float range = Math.max(1.0F, this.sRange.get());
               return item.getPosition(partialTick).distanceToSqr(mc.player.getEyePosition(partialTick)) <= (double)range * range;
            }
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sTracers.get() && this.sItemColors.hasAnyEnabled()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof ItemEntity item && this.wants(item, event.partialTick)) {
                  Vec3 pos = item.getPosition(event.partialTick);
                  TracerEngine.draw(pos, this.sItemColors.colorArgb(item.getItem().getItem()));
               }
            }
         }
      }
   }
}
