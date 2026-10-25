package net.spectre.client.module.impl.render;

import java.util.Set;
import java.util.function.LongPredicate;
import net.spectre.client.esp.BlockLayer;
import net.spectre.client.esp.ChunkScanner;
import net.spectre.client.esp.EspStyle;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BlockColorListSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class BlockEspModule extends ClientModule implements ChunkScanner.Consumer {
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.2F, 2048.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final BlockColorListSetting sBlockColors = this.addSetting(BlockColorListSetting.createDefault());
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return BlockEspModule.this.sBlockColors.colorArgb((Block)kind);
      }

      @Override
      public int tracerColor(Object kind) {
         return BlockEspModule.this.sBlockColors.tracerColorArgb((Block)kind);
      }
   }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
   private Set<String> scannedIds = Set.of();

   public BlockEspModule() {
      super("block-esp", "Block ESP", "Highlights chosen blocks; touching blocks merge into one shape.", ModuleCategory.RENDER);
      this.sBlockColors.onChange(this::rescan);
   }

   private void rescan() {
      if (this.isEnabled()) {
         Set<String> ids = this.sBlockColors.enabledIds();
         if (!ids.equals(this.scannedIds)) {
            boolean removed = !ids.containsAll(this.scannedIds);
            this.scannedIds = ids;
            if (removed) {
               this.layer.clear();
            }

            ChunkScanner.rescan();
         }
      }
   }

   @Override
   protected void onEnable() {
      this.scannedIds = this.sBlockColors.enabledIds();
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.layer.clear();
   }

   @Override
   public boolean wants(BlockState state) {
      return this.sBlockColors.isEnabled(state.getBlock());
   }

   @Override
   public void found(long pos, BlockState state) {
      this.layer.put(pos, state.getBlock());
   }

   @Override
   public void removed(long pos) {
      this.layer.remove(pos);
   }

   @Override
   public void reset() {
      this.layer.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      this.layer.retainChunk(chunkKey, stillThere);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.sTracers.get() && this.sBlockColors.hasAnyTracerEnabled()) {
         this.layer.renderTracers(this.style);
      }
   }
}
