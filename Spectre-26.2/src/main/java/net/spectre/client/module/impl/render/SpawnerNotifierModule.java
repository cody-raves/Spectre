package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;
import net.spectre.client.SpectreClientMod;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.esp.BlockLayer;
import net.spectre.client.esp.ChunkScanner;
import net.spectre.client.esp.EspStyle;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SpawnerNotifierModule extends ClientModule implements ChunkScanner.Consumer {
   private static final String SPAWNER = "Spawner";
   private final BooleanSetting sSound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final BooleanSetting sRender = this.addSetting("render", new BooleanSetting("Render", true));
   private final ColorSetting sColor = this.addSetting("color", new ColorSetting("Color", -587246251));
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.1F, 256.0F);
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return SpawnerNotifierModule.this.sColor.argb();
      }

      @Override
      public int tracerColor(Object kind) {
         return 0;
      }
   }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
   private final LongOpenHashSet notified = new LongOpenHashSet();

   public SpawnerNotifierModule() {
      super("spawner-notifier", "Spawner Notifier", "Scans chunks for mob spawners and highlights them.", ModuleCategory.DONUT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.layer.clear();
      this.notified.clear();
   }

   @Override
   public boolean wants(BlockState state) {
      return state.is(Blocks.SPAWNER);
   }

   @Override
   public void found(long pos, BlockState state) {
      this.layer.put(pos, "Spawner");
      if (this.notified.add(pos)) {
         this.notify(BlockPos.of(pos));
      }
   }

   @Override
   public void removed(long pos) {
      this.layer.remove(pos);
   }

   @Override
   public void reset() {
      this.layer.clear();
      this.notified.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      this.layer.retainChunk(chunkKey, stillThere);
   }

   private void notify(BlockPos pos) {
      Minecraft mc = Minecraft.getInstance();
      if (this.notificationsOn()) {
         ModuleChat.send(this, foundMessage(mc, pos));
      }

      if (this.sSound.get() && mc.player != null && mc.level != null) {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SpectreClientMod.SPAWNER_NOTIFY_SOUND, 1.0F, 1.0F));
      }
   }

   private static ModuleChat.Body foundMessage(Minecraft mc, BlockPos pos) {
      String mob = entityName(mc, pos);
      ModuleChat.Body body = mob == null ? ModuleChat.body().warn("Unknown") : ModuleChat.body().name(mob);
      body.text(" spawner ").good("found").text(" at ").coords(pos.getX(), pos.getY(), pos.getZ());
      if (mc.player != null) {
         long distance = Math.round(mc.player.position().distanceTo(Vec3.atCenterOf(pos)));
         body.text(" (").value(distance + "m").text(")");
      }

      return body;
   }

   private static String entityName(Minecraft mc, BlockPos pos) {
      if (mc.level != null && mc.level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
         Entity entity = spawner.getSpawner().getOrCreateDisplayEntity(mc.level, pos);
         return entity == null ? null : entity.getType().getDescription().getString();
      } else {
         return null;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sRender.get()) {
         this.layer.render(event, this.style);
      }
   }
}
