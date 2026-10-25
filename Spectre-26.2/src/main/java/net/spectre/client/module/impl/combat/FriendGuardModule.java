package net.spectre.client.module.impl.combat;

import net.spectre.client.chat.ModuleChat;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class FriendGuardModule extends ClientModule {
   private static final long HINT_COOLDOWN_MS = 1500L;
   private static volatile FriendGuardModule active;
   private long lastHintAt;

   public FriendGuardModule() {
      super("friend-guard", "Friend Guard", "Stops you from hitting players on your friends list.", ModuleCategory.COMBAT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.lastHintAt = 0L;
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      this.lastHintAt = 0L;
   }

   public static boolean intercepts(Player attacker, Entity target) {
      FriendGuardModule guard = active;
      if (!(guard != null && target instanceof Player friend) || friend == attacker) {
         return false;
      } else if (attacker == Minecraft.getInstance().player && MeleeAttacks.isFriend(friend)) {
         guard.hint(friend);
         return true;
      } else {
         return false;
      }
   }

   private void hint(Player friend) {
      if (this.notificationsOn()) {
         long now = System.currentTimeMillis();
         if (now - this.lastHintAt >= 1500L) {
            this.lastHintAt = now;
            ModuleChat.actionBar(this, ModuleChat.body().text("Held back your hit on ").name(friend.getGameProfile().name()));
         }
      }
   }
}
