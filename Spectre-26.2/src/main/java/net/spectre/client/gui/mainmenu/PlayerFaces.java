package net.spectre.client.gui.mainmenu;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import imgui.ImDrawList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.spectre.client.account.Account;
import net.spectre.client.account.AccountType;
import net.spectre.client.gui.SpectreDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache.RenderInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
final class PlayerFaces {
   private static final float FACE_U = 0.125F;
   private static final float HAT_U = 0.625F;
   private static final float TOP_V = 0.125F;
   private static final float PART = 0.125F;
   private static final Map<UUID, Supplier<PlayerSkin>> SKINS = new HashMap<>();
   private static final Set<GpuTexture> NEAREST = Collections.newSetFromMap(new WeakHashMap<>());

   private PlayerFaces() {
   }

   static void draw(ImDrawList dl, Account account, float x, float y, float size, float s, float a) {
      UUID id;
      try {
         id = UUID.fromString(account.uuid());
      } catch (IllegalArgumentException var9) {
         placeholder(dl, x, y, size, s, a);
         return;
      }

      draw(dl, id, account.type() == AccountType.CRACKED, x, y, size, s, a);
   }

   static void draw(ImDrawList dl, UUID id, boolean offline, float x, float y, float size, float s, float a) {
      int texture = texture(skin(id, offline).body().texturePath());
      if (texture == 0) {
         placeholder(dl, x, y, size, s, a);
      } else {
         float r = Math.max(1.0F, (float)Math.round(3.0F * s));
         int tint = SpectreDraw.col(-1, a);
         dl.addImageRounded(texture, x, y, x + size, y + size, 0.125F, 0.125F, 0.25F, 0.25F, tint, r, 240);
         dl.addImageRounded(texture, x, y, x + size, y + size, 0.625F, 0.125F, 0.75F, 0.25F, tint, r, 240);
      }
   }

   private static void placeholder(ImDrawList dl, float x, float y, float size, float s, float a) {
      dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-7372954, 0.3F * a), Math.round(3.0F * s));
   }

   private static PlayerSkin skin(UUID id, boolean offline) {
      return offline ? DefaultPlayerSkin.get(id) : SKINS.computeIfAbsent(id, key -> {
         Supplier<RenderInfo> lookup = Minecraft.getInstance().playerSkinRenderCache().createLookup(ResolvableProfile.createUnresolved(key));
         return () -> lookup.get().playerSkin();
      }).get();
   }

   private static int texture(Identifier path) {
      GpuTexture texture;
      try {
         texture = Minecraft.getInstance().getTextureManager().getTexture(path).getTexture();
      } catch (IllegalStateException var4) {
         return 0;
      }

      if (texture instanceof GlTexture gl && !gl.isClosed()) {
         if (NEAREST.add(texture)) {
            int bound = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, gl.glId());
            GL11.glTexParameteri(3553, 10241, 9728);
            GL11.glTexParameteri(3553, 10240, 9728);
            GL11.glBindTexture(3553, bound);
         }

         return gl.glId();
      } else {
         return 0;
      }
   }
}
