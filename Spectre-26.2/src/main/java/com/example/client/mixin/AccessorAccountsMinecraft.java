package com.example.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface AccessorAccountsMinecraft {
   @Accessor("remoteFriendListUpdateHandler")
   net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler spectre$friendUpdates();

   @Mutable
   @Accessor("remoteFriendListUpdateHandler")
   void spectre$setFriendUpdates(net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler updates);

   @Mutable
   @Accessor("user")
   void spectre$setUser(User var1);

   @Mutable
   @Accessor("userApiService")
   void spectre$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("userPropertiesFuture")
   void spectre$setUserPropertiesFuture(CompletableFuture<UserProperties> var1);

   @Mutable
   @Accessor("profileFuture")
   void spectre$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("playerSocialManager")
   void spectre$setPlayerSocialManager(PlayerSocialManager var1);

   @Mutable
   @Accessor("profileKeyPairManager")
   void spectre$setProfileKeyPairManager(ProfileKeyPairManager var1);

   @Accessor("reportingContext")
   void spectre$setReportingContext(ReportingContext var1);
}
