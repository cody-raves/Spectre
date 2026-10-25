package net.spectre.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.spectre.client.addon.AddonManager;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.ModuleConfigStore;
import net.spectre.client.config.ProfileAutoLoad;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.esp.ChunkScanner;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.BorderlessFullscreen;
import net.spectre.client.gui.HudLayoutStore;
import net.spectre.client.gui.HudRenderer;
import net.spectre.client.gui.ImGuiScreen;
import net.spectre.client.gui.LoadingScreens;
import net.spectre.client.gui.StaffGhost;
import net.spectre.client.gui.ToggleToasts;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.noinvleak.StreamOverlay;
import net.spectre.client.util.BlockEntityLeakTracker;
import net.spectre.client.util.combat.CombatTarget;
import net.spectre.client.util.netherite.NetheriteChunkTracker;
import net.spectre.client.util.playerbypass.PlayerBypassLightTracker;
import net.spectre.client.util.playerbypass.ProtectedChunkStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SpectreClientMod implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   public static final Identifier SPAWNER_NOTIFY_SOUND_ID = Identifier.fromNamespaceAndPath("spectre", "spawner_notify");
   public static final SoundEvent SPAWNER_NOTIFY_SOUND = SoundEvent.createVariableRangeEvent(SPAWNER_NOTIFY_SOUND_ID);
   public static final Category KEY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("spectre", "controls"));
   private static KeyMapping openGuiKey;
   private final ModuleKeybinds keybinds = new ModuleKeybinds();
   private static final Object LEAK_TRACKER_OWNER = new Object();
   private static final Object PLAYER_BYPASS_TRACKER_OWNER = new Object();
   private static final Object NETHERITE_TRACKER_OWNER = new Object();

   public void onInitializeClient() {
      net.spectre.client.media.MediaPlayer.register();
      LOGGER.info("[Spectre] Initialising");
      net.spectre.client.map.AtlasResources.install();
      net.spectre.client.builder.SchematicPreview.INSTANCE.install();
      ClientTickEvents.END_CLIENT_TICK.register(net.spectre.client.builder.CreativeBuilder.INSTANCE::tick);
      AddonManager.init();
      ImGuiManager.getInstance().setHudRenderer(HudRenderer.getInstance()::render, HudRenderer.getInstance()::hasWork);
      ImGuiManager.getInstance().setStreamRenderer(StreamOverlay::drawFakes, StreamOverlay::hasFakes);
      ImGuiManager.getInstance().setToastRenderer(ToggleToasts::render, ToggleToasts::hasWork);
      ImGuiManager.getInstance().setOverlayRenderer(LoadingScreens::render, LoadingScreens::hasWork);
      ImGuiManager.getInstance().setAlertRenderer(StaffGhost::render, StaffGhost::hasWork);
      Render2DEvent.TYPE.listen(StaffGhost.class, StaffGhost::renderNote);
      ChunkScanner.init();
      PacketReceiveNettyEvent.TYPE.listen(LEAK_TRACKER_OWNER, event -> BlockEntityLeakTracker.ingest(event.packet));
      PacketAppliedEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onPacketApplied(event.packet));
      TickEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onTick());
      TickEvent.TYPE.listen(BorderlessFullscreen.class, event -> BorderlessFullscreen.tick());
      CombatTarget.install();
      PacketAppliedEvent.TYPE.listen(NETHERITE_TRACKER_OWNER, event -> NetheriteChunkTracker.onPacketApplied(event.packet));
      ToggleToasts.install();
      ProfileAutoLoad.install();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         HudLayoutStore.getInstance().save();
         ModuleConfigStore.getInstance().save();
         ThemeStore.get().save();
         HudTheme.get().save();
         ProtectedChunkStore.get().flushAndWait();
         StreamOverlay.stop();
      });
      openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.spectre.open_gui", Type.KEYSYM, 344, KEY_CATEGORY));
      ModuleConfigStore.getInstance().load();
      AddonManager.configLoaded();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         ModuleConfigStore.getInstance().applyPendingEnabled();

         while (openGuiKey.consumeClick()) {
            if (client.gui.screen() instanceof ImGuiScreen) {
               client.gui.setScreen(null);
            } else {
               client.gui.setScreen(new ImGuiScreen());
            }
         }

         this.keybinds.update(ModuleRegistry.all(), SpectreClientMod::isBoundKeyDown, client.gui.screen() == null && client.isWindowActive() && client.player != null);
         ModuleConfigStore.getInstance().tickAutosave();
      });
   }

   private static boolean isBoundKeyDown(int key) {
      Minecraft client = Minecraft.getInstance();
      return key >= 1000 ? GLFW.glfwGetMouseButton(client.getWindow().handle(), key - 1000) == 1 : InputConstants.isKeyDown(client.getWindow(), key);
   }
}
