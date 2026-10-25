package net.spectre.client.module.impl.render;

import java.util.function.LongPredicate;
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
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class StorageEspModule extends ClientModule implements ChunkScanner.Consumer {
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.25F, 256.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final StorageEspModule.Type chest = new StorageEspModule.Type(
      "Chest", true, -6530304, "chests", "chest-color", "chest-tracers", "chest-tracer-color"
   );
   private final StorageEspModule.Type enderChest = new StorageEspModule.Type(
      "Ender Chest", true, -9109249, "ender-chests", "ender-chest-color", "ender-chest-tracers", "ender-chest-tracer-color"
   );
   private final StorageEspModule.Type shulker = new StorageEspModule.Type(
      "Shulker Box", true, -7995234, "shulker-boxes", "shulker-box-color", "shulker-tracers", "shulker-tracer-color"
   );
   private final StorageEspModule.Type spawner = new StorageEspModule.Type(
      "Spawner", true, -7700826, "spawners", "spawner-color", "spawner-tracers", "spawner-tracer-color"
   );
   private final StorageEspModule.Type furnace = new StorageEspModule.Type(
      "Furnace", false, -10197916, "furnaces", "furnace-color", "furnace-tracers", "furnace-tracer-color"
   );
   private final StorageEspModule.Type barrel = new StorageEspModule.Type(
      "Barrel", true, -10204366, "barrels", "barrel-color", "barrel-tracers", "barrel-tracer-color"
   );
   private final StorageEspModule.Type enchanting = new StorageEspModule.Type(
      "Enchanting Table", true, -3670016, "enchanting-tables", "enchanting-table-color", "enchanting-tracers", "enchanting-tracer-color"
   );
   private final StorageEspModule.Type hopper = new StorageEspModule.Type(
      "Hopper", false, -11513776, "hoppers", "hopper-color", "hopper-tracers", "hopper-tracer-color"
   );
   private final StorageEspModule.Type[] types = new StorageEspModule.Type[]{
      this.chest, this.enderChest, this.shulker, this.spawner, this.furnace, this.barrel, this.enchanting, this.hopper
   };
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return ((StorageEspModule.Type)kind).color.argb();
      }

      @Override
      public int tracerColor(Object kind) {
         StorageEspModule.Type t = (StorageEspModule.Type)kind;
         return t.tracers.get() ? t.tracerColor.argb() : 0;
      }
   }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());

   public StorageEspModule() {
      super("storage-esp", "Storage ESP", "Highlights storage blocks and spawners with per-type colours.", ModuleCategory.RENDER);
   }

   private StorageEspModule.Type typeOf(Block block) {
      if (block instanceof ChestBlock) {
         return this.chest;
      } else if (block instanceof EnderChestBlock) {
         return this.enderChest;
      } else if (block instanceof ShulkerBoxBlock) {
         return this.shulker;
      } else if (block instanceof SpawnerBlock) {
         return this.spawner;
      } else if (block instanceof AbstractFurnaceBlock) {
         return this.furnace;
      } else if (block instanceof BarrelBlock) {
         return this.barrel;
      } else if (block instanceof EnchantingTableBlock) {
         return this.enchanting;
      } else {
         return block instanceof HopperBlock ? this.hopper : null;
      }
   }

   private void rescan() {
      if (this.isEnabled()) {
         this.layer.clear();
         ChunkScanner.rescan();
      }
   }

   private boolean anyTracer() {
      if (!this.sTracers.get()) {
         return false;
      } else {
         for (StorageEspModule.Type type : this.types) {
            if (type.enabled.get() && type.tracers.get()) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   protected void onEnable() {
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.layer.clear();
   }

   @Override
   public boolean wants(BlockState state) {
      StorageEspModule.Type type = this.typeOf(state.getBlock());
      return type != null && type.enabled.get();
   }

   @Override
   public void found(long pos, BlockState state) {
      this.layer.put(pos, this.typeOf(state.getBlock()));
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
      if (this.anyTracer()) {
         this.layer.renderTracers(this.style);
      }
   }

   @Environment(EnvType.CLIENT)
   private final class Type {
      final BooleanSetting enabled;
      final BooleanSetting tracers;
      final ColorSetting color;
      final ColorSetting tracerColor;

      Type(String label, boolean on, int argb, String idOn, String idColor, String idTracers, String idTracerColor) {
         this.enabled = StorageEspModule.this.addSetting(idOn, new BooleanSetting(label, on));
         this.color = StorageEspModule.this.addSetting(idColor, new ColorSetting(label + " Color", argb));
         this.tracers = StorageEspModule.this.addSetting(idTracers, new BooleanSetting(label + " Tracers", false));
         this.tracerColor = StorageEspModule.this.addSetting(idTracerColor, new ColorSetting(label + " Tracer Color", argb));
         this.color.visibleWhen(this.enabled::get);
         this.tracers.visibleWhen(() -> StorageEspModule.this.sTracers.get() && this.enabled.get());
         this.tracerColor.visibleWhen(() -> StorageEspModule.this.sTracers.get() && this.enabled.get() && this.tracers.get());
         this.enabled.onChange(StorageEspModule.this::rescan);
      }
   }
}
