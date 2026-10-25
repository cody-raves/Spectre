package net.spectre.client.module.impl.misc;

import java.util.Locale;
import java.util.Optional;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.KeyInputEvent;
import net.spectre.client.event.impl.MouseInputEvent;
import net.spectre.client.input.ModuleKeybinds;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class CoordSnapperModule extends ClientModule {
   private static final String TARGET_LOOKED_AT = "Looked At";
   private static final String TARGET_OWN_POSITION = "Own Position";
   private static final String FORMAT_SPACES = "X Y Z";
   private static final String FORMAT_COMMAS = "X, Y, Z";
   private static final String FORMAT_TP = "/tp Command";
   private static final double STEP_PAST_BLOCK = 0.001;
   private final KeySetting key = this.addSetting("key", new KeySetting("Key", -1));
   private final ModeSetting target = this.addSetting("target", new ModeSetting("Target", "Looked At", "Looked At", "Own Position"));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 256.0F, 16.0F, 512.0F).step(1.0F));
   private final BooleanSetting stopAtFluids = this.addSetting("stop-at-fluids", new BooleanSetting("Stop at Fluids", false));
   private final ModeSetting format = this.addSetting("format", new ModeSetting("Format", "X Y Z", "X Y Z", "X, Y, Z", "/tp Command"));
   private final BooleanSetting includeDimension = this.addSetting("include-dimension", new BooleanSetting("Include Dimension", true));
   private final BooleanSetting confirm = this.addSetting("confirm", new BooleanSetting("Chat Confirmation", true));

   public CoordSnapperModule() {
      super("coord-snapper", "Coord Snapper", "Copies the coordinates you look at or stand on with one key.", ModuleCategory.MISC);
      this.range.visibleWhen(() -> this.target.is("Looked At"));
      this.stopAtFluids.visibleWhen(() -> this.target.is("Looked At"));
      BooleanSetting notifications = this.usesNotifications();
      this.confirm.visibleWhen(notifications::get);
   }

   @Override
   protected void onEnable() {
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui.screen() == null && mc.player != null && mc.level != null) {
            this.snap(mc, mc.player, mc.level);
         }
      }
   }

   private void snap(Minecraft mc, LocalPlayer player, ClientLevel level) {
      BlockPos pos;
      if (this.target.is("Own Position")) {
         pos = player.blockPosition();
      } else {
         pos = this.raycast(mc, player, level);
         if (pos == null) {
            ModuleChat.send(
               this, ModuleChat.body().danger("Nothing copied").text(": no loaded block within ").value(this.range.getInt()).text(" blocks of your aim")
            );
            return;
         }

         if (this.format.is("/tp Command")) {
            pos = pos.above();
         }
      }

      String text = this.formatCoords(pos, level.dimension());
      mc.keyboardHandler.setClipboard(text);
      if (this.confirm.get()) {
         ModuleChat.send(this, ModuleChat.body().good("Copied").text(" ").value(text));
      }
   }

   private BlockPos raycast(Minecraft mc, LocalPlayer player, ClientLevel level) {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      Vec3 origin;
      Vec3 direction;
      if (freecam != null && freecam.isEnabled()) {
         Camera camera = mc.gameRenderer.mainCamera();
         origin = camera.position();
         direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
      } else {
         origin = player.getEyePosition(1.0F);
         direction = player.getViewVector(1.0F);
      }

      Vec3 end = origin.add(direction.scale(this.range.get()));
      Fluid fluids = this.stopAtFluids.get() ? Fluid.ANY : Fluid.NONE;
      ClipContext context = new ClipContext(origin, end, Block.OUTLINE, fluids, player);
      BlockPos viewBlock = BlockPos.containing(origin);
      if (startsInside(context, level, viewBlock, origin)) {
         Optional<Vec3> exit = new AABB(viewBlock).clip(end, origin);
         if (exit.isEmpty()) {
            return null;
         }

         Vec3 start = exit.get().add(direction.scale(0.001));
         context = new ClipContext(start, end, Block.OUTLINE, fluids, player);
      }

      BlockHitResult hit = level.clip(context);
      return hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
   }

   private static boolean startsInside(ClipContext context, ClientLevel level, BlockPos pos, Vec3 point) {
      return contains(context.getBlockShape(level.getBlockState(pos), level, pos), pos, point)
         || contains(context.getFluidShape(level.getFluidState(pos), level, pos), pos, point);
   }

   private static boolean contains(VoxelShape shape, BlockPos pos, Vec3 point) {
      for (AABB box : shape.toAabbs()) {
         if (box.move(pos).contains(point)) {
            return true;
         }
      }

      return false;
   }

   private String formatCoords(BlockPos pos, ResourceKey<Level> dimension) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      boolean withDimension = this.includeDimension.get();
      String var7 = this.format.get();

      return switch (var7) {
         case "/tp Command" -> withDimension
            ? String.format(Locale.ROOT, "/execute in %s run tp @s %d %d %d", dimension.identifier(), x, y, z)
            : String.format(Locale.ROOT, "/tp %d %d %d", x, y, z);
         case "X, Y, Z" -> String.format(Locale.ROOT, "%d, %d, %d", x, y, z) + dimensionSuffix(dimension, withDimension);
         default -> String.format(Locale.ROOT, "%d %d %d", x, y, z) + dimensionSuffix(dimension, withDimension);
      };
   }

   private static String dimensionSuffix(ResourceKey<Level> dimension, boolean enabled) {
      return enabled ? " (" + dimensionName(dimension) + ")" : "";
   }

   private static String dimensionName(ResourceKey<Level> dimension) {
      if (Level.OVERWORLD.equals(dimension)) {
         return "Overworld";
      } else if (Level.NETHER.equals(dimension)) {
         return "Nether";
      } else {
         return Level.END.equals(dimension) ? "End" : dimension.identifier().toString();
      }
   }
}
