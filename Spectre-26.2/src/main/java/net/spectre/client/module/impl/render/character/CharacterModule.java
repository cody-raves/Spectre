package net.spectre.client.module.impl.render.character;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.config.FriendStore;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.render.SpectralHoodLayer;
import net.spectre.client.module.impl.render.SpectralHoodModule;
import net.spectre.client.module.impl.render.character.kit.CharacterDesign;
import net.spectre.client.module.impl.render.character.kit.CharacterModel;
import net.spectre.client.module.impl.render.character.kit.Detail;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class CharacterModule extends ClientModule {
   private static final long FRIEND_REFRESH_NANOS = 1000000000L;
   private static final float MODEL_PIXEL = 0.05859375F;
   private static volatile CharacterModule active;
   private final ModeSetting character = this.addSetting("character", characterSetting());
   private final BooleanSetting friends = this.addSetting("friends", new BooleanSetting("On Friends", false));
   private final ModeSetting detail = this.addSetting(
      "detail", new ModeSetting("Detail", Detail.MEDIUM.label, Detail.LOW.label, Detail.MEDIUM.label, Detail.HIGH.label)
   );
   private final BooleanSetting heldItems = this.addSetting("held-items", new BooleanSetting("Held Items", false));
   private final Int2BooleanOpenHashMap friendCache = new Int2BooleanOpenHashMap();
   private long friendsCheckedAt;
   private int scanTicks;
   private WeakReference<ClientLevel> level = new WeakReference<>(null);

   public CharacterModule() {
      super("character-3d", "3D Character", "Replaces your player model with a 3D character.", ModuleCategory.VISUALS);
      this.addSetting("characters-folder", new ActionSetting("Open Characters Folder", this::openFolder));
      this.character.onChange(this::prepare);
      this.detail.onChange(this::prepare);
   }

   private static ModeSetting characterSetting() {
      CharacterCatalog.refresh();
      List<String> names = CharacterCatalog.names();
      return new ModeSetting("Character", names.get(0), names.toArray(new String[0]));
   }

   private void refreshCharacters() {
      if (CharacterCatalog.refresh()) {
         this.character.setModes(CharacterCatalog.names());
      }
   }

   private void openFolder() {
      Path dir = CustomCharacters.folder();

      try {
         Files.createDirectories(dir);
      } catch (IOException var3) {
         ModuleChat.send(this, ModuleChat.body().danger("Couldn't create ").text(dir.toString()));
         return;
      }

      this.refreshCharacters();
      Util.getPlatform().openPath(dir);
   }

   private void prepare() {
      if (this.isEnabled()) {
         CharacterCache.prepare(CharacterCatalog.byName(this.character.get()), Detail.byLabel(this.detail.get()));
      }
   }

   @Override
   protected void onEnable() {
      SpectralHoodModule headdress = ModuleRegistry.get(SpectralHoodModule.class);
      if (headdress != null) {
         headdress.setEnabled(false);
      }

      CharacterCache.listen();
      this.reset();
      this.refreshCharacters();
      this.prepare();
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, event -> {
         this.watchLevel();
         if (++this.scanTicks % 40 == 0) {
            this.refreshCharacters();
         }
      });
   }

   @Override
   protected void onDisable() {
      if (active == this) {
         active = null;
      }

      this.reset();
      CharacterCache.clear();
   }

   private void reset() {
      this.friendCache.clear();
      CharacterMotion.clear();
      this.level = new WeakReference<>(Minecraft.getInstance().level);
   }

   private void watchLevel() {
      ClientLevel current = Minecraft.getInstance().level;
      if (this.level.get() != current) {
         this.reset();
         CharacterCache.releaseTextures();
      }
   }

   public static void extract(Entity entity, AvatarRenderState state) {
      CharacterModule module = active;
      WornCharacter worn = module == null ? null : module.resolve(state);
      CharacterHolder holder = (CharacterHolder)state;
      holder.spectre$setCharacter(worn);
      if (worn != null) {
         Entity vehicle = entity.getVehicle();
         holder.spectre$setSeat(vehicle == null ? 0.0F : (vehicle instanceof LivingEntity ? 1.35F : 0.3F));
         if (state.nameTagAttachment != null) {
            CharacterModel model = worn.model;
            float over = model.maxY * model.scale - 32.0F;
            if (over > 0.0F) {
               state.nameTagAttachment = state.nameTagAttachment.add(0.0, over * 0.05859375F * state.scale, 0.0);
            }
         }
      }
   }

   static void baking(String file) {
      CharacterModule module = active;
      if (module != null) {
         ModuleChat.send(module, ModuleChat.body().text("Preparing ").name(file).text(", big models take a few seconds the first time"));
      }
   }

   static void notice(String file, List<String> limbs) {
      CharacterModule module = active;
      if (module != null) {
         ModuleChat.Body body = ModuleChat.body().name(file);
         if (limbs.isEmpty()) {
            body.text(" has no skeleton the client recognises, so it moves as one piece");
         } else {
            body.text(" only moves its ").value(String.join(" and ", limbs)).text(", the rest of its skeleton wasn't recognised");
         }

         ModuleChat.send(module, body);
      }
   }

   static void failed(CharacterDesign design, Throwable failure) {
      CharacterModule module = active;
      if (module != null) {
         Throwable cause = failure instanceof UncheckedIOException && failure.getCause() != null ? failure.getCause() : failure;
         String reason = !(cause instanceof IOException) && !(cause instanceof IllegalStateException)
            ? "the file couldn't be read, see the log"
            : cause.getMessage();
         ModuleChat.send(module, ModuleChat.body().text("Couldn't load ").name(design.name()).text(": ").danger(reason));
      }
   }

   static WornCharacter worn(EntityRenderState state) {
      return state instanceof CharacterHolder holder ? holder.spectre$character() : null;
   }

   static float seat(EntityRenderState state) {
      return state instanceof CharacterHolder holder ? holder.spectre$seat() : 0.0F;
   }

   public static boolean hidesModel(EntityRenderState state) {
      return worn(state) != null;
   }

   public static boolean hidesLayer(RenderLayer<?, ?> layer, EntityRenderState state) {
      return worn(state) == null
         ? false
         : layer instanceof HumanoidArmorLayer
            || layer instanceof CapeLayer
            || layer instanceof WingsLayer
            || layer instanceof StuckInBodyLayer
            || layer instanceof CustomHeadLayer
            || layer instanceof ParrotOnShoulderLayer
            || layer instanceof Deadmau5EarsLayer
            || layer instanceof SpectralHoodLayer
            || layer instanceof ItemInHandLayer && !showsHeldItems();
   }

   private static boolean showsHeldItems() {
      CharacterModule module = active;
      return module != null && module.heldItems.get();
   }

   static WornCharacter firstPerson() {
      CharacterModule module = active;
      if (module == null) {
         return null;
      } else {
         LocalPlayer self = Minecraft.getInstance().player;
         if (self != null && !self.isSpectator()) {
            WornCharacter worn = CharacterCache.ready(CharacterCatalog.byName(module.character.get()), Detail.byLabel(module.detail.get()));
            return worn != null && worn.model.firstPersonArms ? worn : null;
         } else {
            return null;
         }
      }
   }

   private WornCharacter resolve(AvatarRenderState state) {
      if (state.isSpectator) {
         return null;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return !this.wears(mc, state) ? null : CharacterCache.ready(CharacterCatalog.byName(this.character.get()), Detail.byLabel(this.detail.get()));
      }
   }

   private boolean wears(Minecraft mc, AvatarRenderState state) {
      LocalPlayer self = mc.player;
      if (self != null && mc.level != null) {
         if (state.id != self.getId()) {
            return this.friends.get() && this.isFriend(mc, state.id);
         } else {
            return FreecamModule.active == null && mc.options.getCameraType().isFirstPerson() ? mc.gui.screen() != null && !self.isSleeping() : true;
         }
      } else {
         return false;
      }
   }

   private boolean isFriend(Minecraft mc, int id) {
      long now = System.nanoTime();
      if (now - this.friendsCheckedAt > 1000000000L) {
         this.friendCache.clear();
         this.friendsCheckedAt = now;
      }

      if (this.friendCache.containsKey(id)) {
         return this.friendCache.get(id);
      } else {
         boolean friend = mc.level.getEntity(id) instanceof Player player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
         this.friendCache.put(id, friend);
         return friend;
      }
   }
}
