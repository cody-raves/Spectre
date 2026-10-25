package net.spectre.client.account;

import com.example.client.mixin.AccessorAccountsMinecraft;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class GameSession {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private YggdrasilAuthenticationService mojangAuth;

   GameSession() {
   }

   GameSession.Prepared prepare(AccountType type, Credentials credentials) {
      Minecraft mc = Minecraft.getInstance();
      boolean offline = type == AccountType.CRACKED;
      User user = new User(credentials.name(), credentials.uuid(), credentials.accessToken(), Optional.empty(), Optional.empty());
      UserApiService api = offline ? UserApiService.OFFLINE : this.mojangAuth(mc).createUserApiService(credentials.accessToken());

      UserProperties properties;
      try {
         properties = api.fetchProperties();
      } catch (AuthenticationException var9) {
         LOGGER.warn("[Spectre] Couldn't fetch user properties for {}", credentials.name(), var9);
         properties = UserApiService.OFFLINE_PROPERTIES;
      }

      ProfileResult profile = offline ? null : mc.services().sessionService().fetchProfile(credentials.uuid(), true);
      return new GameSession.Prepared(user, api, properties, profile, offline);
   }

   void apply(GameSession.Prepared prepared) {
      Minecraft mc = Minecraft.getInstance();
      AccessorAccountsMinecraft game = (AccessorAccountsMinecraft)mc;
      game.spectre$setUser(prepared.user());
      game.spectre$setUserApiService(prepared.api());
      game.spectre$setUserPropertiesFuture(CompletableFuture.completedFuture(prepared.properties()));
      game.spectre$setProfileFuture(CompletableFuture.completedFuture(prepared.profile()));
      var friends = this.mojangAuth(mc).createFriendsService(prepared.user().getAccessToken());
      var updates = new net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler(friends, mc);
      var social = new PlayerSocialManager(mc, prepared.api(), friends, updates);
      game.spectre$friendUpdates().close();
      game.spectre$setFriendUpdates(updates);
      game.spectre$setPlayerSocialManager(social);
      if (!prepared.offline() && social.isFriendListEnabled()) updates.start();
      game.spectre$setProfileKeyPairManager(
         prepared.offline()
            ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
            : ProfileKeyPairManager.create(prepared.api(), prepared.user(), mc.gameDirectory.toPath())
      );
      game.spectre$setReportingContext(ReportingContext.create(ReportEnvironment.local(), prepared.api()));
   }

   private YggdrasilAuthenticationService mojangAuth(Minecraft mc) {
      if (this.mojangAuth == null) {
         this.mojangAuth = new YggdrasilAuthenticationService(mc.getProxy());
      }

      return this.mojangAuth;
   }

   @Environment(EnvType.CLIENT)
   record Prepared(User user, UserApiService api, UserProperties properties, ProfileResult profile, boolean offline) {
   }
}
