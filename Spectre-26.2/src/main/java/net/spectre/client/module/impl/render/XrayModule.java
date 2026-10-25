package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BlockColorListSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class XrayModule extends ClientModule {
   private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
   private static final List<String> ORES = List.of(
      "minecraft:coal_ore",
      "minecraft:deepslate_coal_ore",
      "minecraft:iron_ore",
      "minecraft:deepslate_iron_ore",
      "minecraft:gold_ore",
      "minecraft:deepslate_gold_ore",
      "minecraft:lapis_ore",
      "minecraft:deepslate_lapis_ore",
      "minecraft:redstone_ore",
      "minecraft:deepslate_redstone_ore",
      "minecraft:diamond_ore",
      "minecraft:deepslate_diamond_ore",
      "minecraft:emerald_ore",
      "minecraft:deepslate_emerald_ore",
      "minecraft:copper_ore",
      "minecraft:deepslate_copper_ore",
      "minecraft:nether_gold_ore",
      "minecraft:nether_quartz_ore",
      "minecraft:ancient_debris"
   );
   private final BlockColorListSetting blocks = this.addSetting("blocks", BlockColorListSetting.blockList("Blocks", ORES));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 10.0F, 0.0F, 100.0F).step(1.0F));
   private final BooleanSetting exposedOnly = this.addSetting("exposed-only", new BooleanSetting("Exposed Only", false));
   public static volatile XrayModule.Snapshot active;
   public static final ThreadLocal<float[]> CURRENT_ALPHA = ThreadLocal.withInitial(() -> new float[]{1.0F});
   private static final int RELOAD_DELAY_TICKS = 6;
   private int reloadDelay = -1;

   public XrayModule() {
      super("xray", "Xray", "Hide or fade everything but a block whitelist to see ores through terrain.", ModuleCategory.RENDER);
      this.usesNotifications();
      this.blocks.onChange(this::scheduleReload);
      this.opacity.onChange(this::scheduleReload);
      this.exposedOnly.onChange(this::scheduleReload);
   }

   @Override
   protected void onEnable() {
      if (SODIUM) {
         ModuleChat.send(this, ModuleChat.body().text("Sodium detected; terrain Xray ").danger("isn't supported").text(" with it yet"));
         this.requestDisable();
      } else {
         this.rebuildSnapshot();
         this.reloadWorld();
      }
   }

   @Override
   protected void onDisable() {
      active = null;
      this.reloadDelay = -1;
      this.reloadWorld();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (this.reloadDelay >= 0 && --this.reloadDelay <= 0) {
         this.reloadDelay = -1;
         this.rebuildSnapshot();
         this.reloadWorld();
      }
   }

   private void scheduleReload() {
      if (this.isEnabled()) {
         this.reloadDelay = 6;
      }
   }

   private void reloadWorld() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.levelRenderer != null) {
         mc.levelExtractor.allChanged();
      }
   }

   private void rebuildSnapshot() {
      active = new XrayModule.Snapshot(new ReferenceOpenHashSet(this.blocks.enabledBlocks()), this.exposedOnly.get(), this.opacity.get() / 100.0F);
   }

   @Environment(EnvType.CLIENT)
   public static final class Snapshot {
      private static final Direction[] DIRECTIONS = Direction.values();
      private static final ThreadLocal<MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(MutableBlockPos::new);
      private final ReferenceOpenHashSet<Block> whitelist;
      private final boolean exposedOnly;
      private final float opacity;

      private Snapshot(ReferenceOpenHashSet<Block> whitelist, boolean exposedOnly, float opacity) {
         this.whitelist = whitelist;
         this.exposedOnly = exposedOnly;
         this.opacity = opacity;
      }

      public boolean isWhitelisted(Block block) {
         return this.whitelist.contains(block);
      }

      public float alphaFor(net.minecraft.world.level.BlockGetter region, BlockState state, BlockPos pos) {
         return !this.whitelist.contains(state.getBlock()) || this.exposedOnly && !this.isExposed(region, pos) ? this.opacity : 1.0F;
      }

      private boolean isExposed(net.minecraft.world.level.BlockGetter region, BlockPos pos) {
         MutableBlockPos scratch = SCRATCH_POS.get();

         for (Direction direction : DIRECTIONS) {
            scratch.setWithOffset(pos, direction);
            if (!region.getBlockState(scratch).isSolidRender()) {
               return true;
            }
         }

         return false;
      }
   }
}
