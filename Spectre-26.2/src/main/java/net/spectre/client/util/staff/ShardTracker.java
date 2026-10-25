package net.spectre.client.util.staff;

import net.spectre.client.gui.DonutGoliathMapData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

@Environment(EnvType.CLIENT)
public final class ShardTracker {
   private static final int OFF_MAP = Integer.MIN_VALUE;
   private ClientLevel level;
   private LocalPlayer self;
   private String brand;
   private int goliath = Integer.MIN_VALUE;

   public boolean moved(Minecraft mc) {
      ClientLevel nowLevel = mc.level;
      LocalPlayer nowSelf = mc.player;
      if (nowLevel != null && nowSelf != null) {
         ClientPacketListener connection = mc.getConnection();
         String nowBrand = connection == null ? null : connection.serverBrand();
         int nowGoliath = goliath(nowLevel, nowSelf);
         boolean moved = this.level != null
            && (
               nowLevel != this.level
                  || nowSelf != this.self && !this.self.isDeadOrDying()
                  || nowGoliath != this.goliath
                  || nowBrand != null && this.brand != null && !nowBrand.equals(this.brand)
            );
         this.level = nowLevel;
         this.self = nowSelf;
         this.brand = nowBrand;
         this.goliath = nowGoliath;
         return moved;
      } else {
         this.clear();
         return false;
      }
   }

   public void clear() {
      this.level = null;
      this.self = null;
      this.brand = null;
      this.goliath = Integer.MIN_VALUE;
   }

   private static int goliath(ClientLevel level, LocalPlayer self) {
      return level.dimension() != Level.OVERWORLD ? Integer.MIN_VALUE : DonutGoliathMapData.goliathAt(self.getX(), self.getZ());
   }
}
