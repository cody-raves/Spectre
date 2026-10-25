package net.spectre.client.module.impl.misc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.render.FreelookModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.StringSetting;
import net.spectre.client.webhook.WebhookConfig;
import net.spectre.client.webhook.WebhookPayloadBuilder;
import net.spectre.client.webhook.WebhookService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SpawnerProtectModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   public static volatile SpawnerProtectModule active;
   private static final int SCAN_RADIUS = 16;
   private static final double RETARGET_DIST_SQR = 256.0;
   private static final int CHEST_SEARCH_RADIUS = 4;
   private static final double CHEST_ARRIVE_DISTANCE = 4.0;
   private static final int BREAK_COOLDOWN_TICKS = 6;
   private static final int RIGHT_CLICK_AFTER_TICKS = 60;
   private static final int NO_TARGET_WAIT_TICKS = 40;
   private static final int DEPOSIT_WAIT_TICKS = 15;
   private static final int EXIT_WAIT_TICKS = 40;
   private static final int CHEST_OPEN_RETRY_TICKS = 12;
   private static final int SHOP_START_TICKS = 30;
   private static final int SHOP_RETRY_TICKS = 60;
   private static final int SHOP_TIMEOUT_TICKS = 100;
   private static final int PLACE_START_TICKS = 10;
   private static final double SPAWN_HALF_SIZE = 100.0;
   private static final double WHITELIST_RADIUS = 8.0;
   private static final double OWN_BREAK_RADIUS = 6.0;
   private static final int MIN_DETECTION_Y = -64;
   private static final int SHOP_END_CATEGORY_SLOT = 11;
   private static final int SHOP_ENDER_CHEST_SLOT = 9;
   private static final int SHOP_CONFIRM_SLOT = 25;
   private static final String DISCONNECT_REASON = "[SpawnerProtect] Görev tamamlandı, güvenli çıkış yapıldı.";
   private static final ExecutorService WEBHOOK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "Spectre-SpawnerProtect-Webhook");
      thread.setDaemon(true);
      return thread;
   });
   private final NumberSetting targetStackCount = this.addSetting("stack-to-deposit", new NumberSetting("Stack To Deposit", 3.0F, 1.0F, 30.0F).step(1.0F));
   private final NumberSetting scanRange = this.addSetting("trigger-range", new NumberSetting("Trigger Range", 64.0F, 16.0F, 128.0F).step(1.0F));
   private final NumberSetting rotationSpeed = this.addSetting("rotation-speed", new NumberSetting("Rotation Speed", 15.0F, 1.0F, 30.0F).step(1.0F));
   private final BooleanSetting detectBlockUpdates = this.addSetting("detect-block-updates", new BooleanSetting("Detect Block Updates", true));
   private final StringSetting whitelist = this.addSetting("whitelist", new StringSetting("Whitelist", ""));
   private final NumberSetting breakRange = this.addSetting("break-range", new NumberSetting("Break Range", 5.5F, 1.0F, 8.0F).step(0.5F));
   private final NumberSetting blockUpdateThreshold = this.addSetting(
      "block-update-threshold", new NumberSetting("Block Update Threshold", 5.0F, 1.0F, 20.0F).step(1.0F)
   );
   private final NumberSetting blockUpdateWindow = this.addSetting(
      "block-update-window", new NumberSetting("Block Update Window", 60.0F, 1.0F, 600.0F).step(1.0F)
   );
   private final StringSetting webhookUrl = this.addSetting("webhook-url", new StringSetting("Webhook URL", ""));
   private SpawnerProtectModule.State currentState = SpawnerProtectModule.State.WAITING_FOR_STRANGER;
   private BlockPos targetBlock;
   private BlockPos targetChest;
   private int chestTick;
   private int breakCooldown;
   private int lagWaitTicks;
   private boolean strangerDetected;
   private int blockBreakCounter;
   private long lastBreakNanos;
   private boolean hasLastBreak;
   private int shopSequence;
   private int shopTick;
   private int miningTicks;
   private boolean forwardHeld;
   private boolean sneakHeld;
   private boolean serverMenuOpenedSinceKeybinds;
   private boolean spawnerScanEmptyThisTick;
   private String whitelistRaw;
   private Set<String> whitelistNames = Set.of();

   public SpawnerProtectModule() {
      super("spawner-protect", "Spawner Protect", "Mines your spawners into an ender chest when someone gets close.", ModuleCategory.DONUT);
      this.usesNotifications();
      this.whitelist.privateValue();
      this.webhookUrl.privateValue();
      this.whitelist.getImString().inputData.isResizable = true;
   }

   @Override
   protected void onEnable() {
      this.resetModule();
      this.serverMenuOpenedSinceKeybinds = false;
      active = this;
      this.on(TickEvent.TYPE, this::onTick);
   }

   @Override
   protected void onDisable() {
      active = null;
      this.serverMenuOpenedSinceKeybinds = false;
      this.stopMovement();
      Minecraft mc = Minecraft.getInstance();
      this.updateSneak(mc.player, false);
      MultiPlayerGameMode gameMode = mc.gameMode;
      if (this.currentState != SpawnerProtectModule.State.WAITING_FOR_STRANGER && mc.player != null && gameMode != null && gameMode.isDestroying()) {
         gameMode.stopDestroyBlock();
      }
   }

   private void resetModule() {
      this.currentState = SpawnerProtectModule.State.WAITING_FOR_STRANGER;
      this.strangerDetected = false;
      this.lagWaitTicks = 0;
      this.blockBreakCounter = 0;
      this.hasLastBreak = false;
      this.resetState();
      this.forwardHeld = false;
      this.sneakHeld = false;
   }

   private void resetState() {
      this.targetChest = null;
      this.targetBlock = null;
      this.chestTick = 0;
      this.breakCooldown = 0;
      this.miningTicks = 0;
   }

   public boolean isDriving() {
      return this.isEnabled() && this.currentState != SpawnerProtectModule.State.WAITING_FOR_STRANGER;
   }

   public boolean forwardKey(boolean physical) {
      return this.isDriving() ? this.forwardHeld : physical;
   }

   public boolean sneakKey(boolean physical) {
      return this.isDriving() ? this.sneakHeld : physical;
   }

   public boolean unusedMoveKey(boolean physical) {
      return !this.isDriving() && physical;
   }

   public boolean blocksMouseTurn() {
      if (!this.isDriving()) {
         return false;
      } else {
         FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
         if (freecam != null && freecam.isEnabled()) {
            return false;
         } else {
            FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
            return freelook == null || !freelook.cameraMode();
         }
      }
   }

   public void onKeysReleased(Screen screen) {
      this.forwardHeld = false;
      this.sneakHeld = false;
      if (screen instanceof AbstractContainerScreen<?> container && container.getMenu().containerId != 0) {
         this.serverMenuOpenedSinceKeybinds = true;
      }
   }

   public boolean blocksVanillaAttack() {
      return this.isDriving();
   }

   public boolean onVanillaContinueAttack() {
      Minecraft mc = Minecraft.getInstance();
      boolean firstPassAfterMenu = this.serverMenuOpenedSinceKeybinds && mc.gui.screen() == null;
      if (firstPassAfterMenu) {
         this.serverMenuOpenedSinceKeybinds = false;
      }

      if (!this.isDriving()) {
         return false;
      } else {
         LocalPlayer player = mc.player;
         MultiPlayerGameMode gameMode = mc.gameMode;
         if (firstPassAfterMenu && player != null && gameMode != null && !player.isUsingItem()) {
            gameMode.stopDestroyBlock();
         }

         return true;
      }
   }

   public void onBlockUpdatePacket(ClientboundBlockUpdatePacket packet) {
      if (this.canDetect()) {
         this.checkBlockUpdate(packet.getPos(), packet.getBlockState(), "Blok Kırılması");
      }
   }

   public void onSectionBlocksUpdatePacket(ClientboundSectionBlocksUpdatePacket packet) {
      if (this.canDetect()) {
         packet.runUpdates((pos, state) -> this.checkBlockUpdate(pos, state, "Çoklu Blok Kırılması"));
      }
   }

   private boolean canDetect() {
      Minecraft mc = Minecraft.getInstance();
      return !this.strangerDetected && mc.player != null && mc.level != null && !isNearSpawn(mc.player) ? this.detectBlockUpdates.get() : false;
   }

   private void checkBlockUpdate(BlockPos pos, BlockState newState, String reason) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      LocalPlayer player = mc.player;
      if (!this.strangerDetected && level != null && player != null) {
         Vec3 center = Vec3.atCenterOf(pos);

         for (AbstractClientPlayer other : level.players()) {
            if (other.getEyePosition().distanceTo(center) < 8.0 && this.isWhitelisted(other.getName().getString())) {
               return;
            }
         }

         if (pos.getY() > -64) {
            if (newState.isAir()) {
               BlockState oldState = level.getBlockState(pos);
               if (!oldState.isAir() && !(oldState.getBlock() instanceof ShulkerBoxBlock)) {
                  if (!(player.getEyePosition().distanceTo(center) < 6.0)) {
                     double dx = player.getX() - pos.getX();
                     double dz = player.getZ() - pos.getZ();
                     double horizontal = Math.sqrt(dx * dx + dz * dz);
                     if (!(horizontal > this.scanRange.get())) {
                        long now = System.nanoTime();
                        if (!this.hasLastBreak || now - this.lastBreakNanos > this.blockUpdateWindow.getInt() * 1000000000L) {
                           this.blockBreakCounter = 0;
                        }

                        this.blockBreakCounter++;
                        this.lastBreakNanos = now;
                        this.hasLastBreak = true;
                        if (this.blockBreakCounter >= this.blockUpdateThreshold.getInt()) {
                           this.strangerDetected = true;
                           this.currentState = SpawnerProtectModule.State.WORKING;
                           int shownDistance = (int)horizontal;
                           ModuleChat.send(
                              this,
                              ModuleChat.body()
                                 .danger("Remote detection")
                                 .text(": a block was broken nearby (")
                                 .value(shownDistance + "m")
                                 .text(" horizontally, Y ")
                                 .value(pos.getY())
                                 .text(")")
                           );
                           this.sendWebhook(
                              "\ud83d\udea8 **UZAK TESPİT ("
                                 + reason
                                 + "):** Görünmez veya çok uzaktaki bir oyuncu blok kırdı! (Yatay Mesafe: "
                                 + shownDistance
                                 + "m, Y: "
                                 + pos.getY()
                                 + ")"
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isWhitelisted(String name) {
      return this.whitelistNames().contains(name.toLowerCase(Locale.ROOT));
   }

   private Set<String> whitelistNames() {
      String raw = this.whitelist.get();
      if (!raw.equals(this.whitelistRaw)) {
         Set<String> parsed = new HashSet<>();

         for (String entry : raw.split(",")) {
            String name = entry.trim();
            if (!name.isEmpty()) {
               parsed.add(name.toLowerCase(Locale.ROOT));
            }
         }

         this.whitelistNames = parsed;
         this.whitelistRaw = raw;
      }

      return this.whitelistNames;
   }

   private static boolean isNearSpawn(LocalPlayer player) {
      return player != null && Math.abs(player.getX()) < 100.0 && Math.abs(player.getZ()) < 100.0;
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      MultiPlayerGameMode gameMode = mc.gameMode;
      if (player != null && level != null && gameMode != null) {
         this.spawnerScanEmptyThisTick = false;
         if (isNearSpawn(player)) {
            if (this.strangerDetected) {
               this.resetModule();
            }
         } else if (this.currentState != SpawnerProtectModule.State.WAITING_FOR_STRANGER) {
            if (this.currentState != SpawnerProtectModule.State.OPENING_CHEST
               && this.currentState != SpawnerProtectModule.State.DEPOSITING_ITEMS
               && this.currentState != SpawnerProtectModule.State.BUYING_ECHEST) {
               this.updateSneak(player, true);
            }

            this.handleRotation(player, level);
            switch (this.currentState) {
               case WORKING:
                  this.handleWorking(player, level, gameMode);
                  break;
               case GOING_TO_CHEST:
                  this.handleGoingToChest(player, level);
                  break;
               case OPENING_CHEST:
                  this.handleOpeningChest(player, gameMode);
                  break;
               case DEPOSITING_ITEMS:
                  this.handleDepositing(player, level, gameMode);
                  break;
               case FINAL_EXIT:
                  this.handleFinalExit(mc);
                  break;
               case BUYING_ECHEST:
                  this.handleBuyingEChest(mc, player, gameMode);
                  break;
               case PLACING_ECHEST:
                  this.handlePlacingEChest(player, level, gameMode);
            }
         }
      }
   }

   private void handleRotation(LocalPlayer player, ClientLevel level) {
      Vec3 target = null;
      if (this.currentState == SpawnerProtectModule.State.WORKING) {
         ItemEntity dropped = findDroppedSpawner(player, level);
         if (dropped != null) {
            target = dropped.position();
         } else {
            if (this.needsNewTarget(player, level)) {
               this.targetBlock = this.findRandomSpawner(player, level);
               this.miningTicks = 0;
            }

            if (this.targetBlock != null) {
               target = Vec3.atCenterOf(this.targetBlock);
            }
         }
      } else if ((this.currentState == SpawnerProtectModule.State.GOING_TO_CHEST || this.currentState == SpawnerProtectModule.State.OPENING_CHEST)
         && this.targetChest != null) {
         target = Vec3.atCenterOf(this.targetChest);
      }

      if (target != null) {
         this.smoothLook(player, target);
      }
   }

   private void smoothLook(LocalPlayer player, Vec3 target) {
      Vec3 eyes = player.getEyePosition();
      double dx = target.x - eyes.x;
      double dy = target.y - eyes.y;
      double dz = target.z - eyes.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float step = this.rotationSpeed.get();
      player.setYRot(player.getYRot() + Mth.clamp(Mth.wrapDegrees(targetYaw - player.getYRot()), -step, step));
      player.setXRot(player.getXRot() + Mth.clamp(Mth.wrapDegrees(targetPitch - player.getXRot()), -step, step));
   }

   private void handleWorking(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
      if (player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
      }

      ItemEntity dropped = findDroppedSpawner(player, level);
      if (dropped != null) {
         this.lagWaitTicks = 0;
         this.stopBreaking();
         this.forwardHeld = true;
      } else if (getSpawnerCount(player) >= this.targetStackCount.getInt() * 64) {
         this.goToChest(player, level);
      } else {
         if (this.needsNewTarget(player, level)) {
            this.targetBlock = this.findRandomSpawner(player, level);
            this.miningTicks = 0;
         }

         if (this.targetBlock == null) {
            this.stopMovement();
            if (this.lagWaitTicks < 40) {
               this.lagWaitTicks++;
            } else {
               if (getSpawnerCount(player) > 0) {
                  this.goToChest(player, level);
               } else {
                  this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
                  this.lagWaitTicks = 0;
               }
            }
         } else {
            this.lagWaitTicks = 0;
            double dist = player.getEyePosition().distanceTo(Vec3.atCenterOf(this.targetBlock));
            if (dist <= this.breakRange.get()) {
               this.forwardHeld = false;
               this.miningTicks++;
               if (this.miningTicks > 60) {
                  gameMode.useItemOn(
                     player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(this.targetBlock), Direction.UP, this.targetBlock, false)
                  );
                  player.swing(InteractionHand.MAIN_HAND);
                  this.miningTicks = 0;
               }

               if (this.breakCooldown <= 0) {
                  gameMode.continueDestroyBlock(this.targetBlock, Direction.UP);
                  player.swing(InteractionHand.MAIN_HAND);
                  this.breakCooldown = 6;
               } else {
                  this.breakCooldown--;
               }
            } else {
               this.stopBreaking();
               this.forwardHeld = true;
            }
         }
      }
   }

   private boolean needsNewTarget(LocalPlayer player, ClientLevel level) {
      return this.targetBlock == null
         || level.getBlockState(this.targetBlock).getBlock() != Blocks.SPAWNER
         || player.blockPosition().distSqr(this.targetBlock) > 256.0;
   }

   private BlockPos findRandomSpawner(LocalPlayer player, ClientLevel level) {
      if (this.spawnerScanEmptyThisTick) {
         return null;
      } else {
         BlockPos found = findRandomBlock(player, level, Blocks.SPAWNER, 16);
         this.spawnerScanEmptyThisTick = found == null;
         return found;
      }
   }

   private void goToChest(LocalPlayer player, ClientLevel level) {
      this.stopMovement();
      this.targetChest = findNearestBlock(player, level, Blocks.ENDER_CHEST, 4);
      if (this.targetChest != null) {
         this.currentState = SpawnerProtectModule.State.GOING_TO_CHEST;
      } else if (getEnderChestCount(player) > 0) {
         this.currentState = SpawnerProtectModule.State.PLACING_ECHEST;
         this.shopTick = 0;
      } else {
         this.currentState = SpawnerProtectModule.State.BUYING_ECHEST;
         this.shopSequence = 0;
         this.shopTick = 0;
      }
   }

   private void handleGoingToChest(LocalPlayer player, ClientLevel level) {
      if (this.targetChest == null) {
         this.targetChest = findNearestBlock(player, level, Blocks.ENDER_CHEST, 4);
      }

      if (this.targetChest == null) {
         this.currentState = SpawnerProtectModule.State.WORKING;
      } else {
         this.forwardHeld = true;
         if (player.blockPosition().closerThan(this.targetChest, 4.0)) {
            this.stopMovement();
            this.currentState = SpawnerProtectModule.State.OPENING_CHEST;
         }
      }
   }

   private void handleOpeningChest(LocalPlayer player, MultiPlayerGameMode gameMode) {
      this.updateSneak(player, false);
      if (this.chestTick % 12 == 0 && this.targetChest != null) {
         gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(this.targetChest), Direction.UP, this.targetChest, false));
      }

      this.chestTick++;
      if (player.containerMenu instanceof ChestMenu) {
         this.chestTick = 0;
         this.currentState = SpawnerProtectModule.State.DEPOSITING_ITEMS;
         this.lagWaitTicks = 0;
      }
   }

   private void handleDepositing(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
      if (this.lagWaitTicks < 15) {
         this.lagWaitTicks++;
      } else if (player.containerMenu instanceof ChestMenu menu) {
         int var10 = menu.slots.size() - 36;
         boolean hasSpace = false;

         for (int deposited = 0; deposited < var10; deposited++) {
            ItemStack stack = menu.getSlot(deposited).getItem();
            if (stack.isEmpty() || stack.getItem() == Items.SPAWNER && stack.getCount() < stack.getMaxStackSize()) {
               hasSpace = true;
               break;
            }
         }

         boolean depositedx = false;

         for (int i = 0; i < 36; i++) {
            int slotId = var10 + i;
            if (menu.getSlot(slotId).getItem().getItem() == Items.SPAWNER) {
               if (!hasSpace) {
                  this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
                  this.lagWaitTicks = 0;
                  return;
               }

               gameMode.handleContainerInput(menu.containerId, slotId, 0, ContainerInput.QUICK_MOVE, player);
               depositedx = true;
               break;
            }
         }

         if (!depositedx) {
            player.closeContainer();
            if (findNearestBlock(player, level, Blocks.SPAWNER, 16) != null) {
               this.currentState = SpawnerProtectModule.State.WORKING;
            } else {
               this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
               this.lagWaitTicks = 0;
            }
         }
      }
   }

   private void handleBuyingEChest(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
      this.shopTick++;
      if (this.shopSequence > 0 && !(mc.gui.screen() instanceof AbstractContainerScreen) && this.shopTick > 60) {
         ModuleChat.send(this, ModuleChat.body().text("Shop menu closed, ").warn("retrying").text(" from the start"));
         this.shopSequence = 0;
         this.shopTick = 0;
      } else if (this.shopTick >= 30) {
         switch (this.shopSequence) {
            case 0:
               ClientPacketListener connection = mc.getConnection();
               if (connection == null) {
                  return;
               }

               ModuleChat.send(this, ModuleChat.body().text("Opening the shop: ").name("/shop"));
               connection.sendCommand("shop");
               this.shopSequence = 1;
               this.shopTick = 0;
               break;
            case 1:
               if (screenTitleContains(mc.gui.screen(), "SHOP")) {
                  ModuleChat.send(this, ModuleChat.body().text("Shop: selecting the ").name("End").text(" category"));
                  clickSlot(player, gameMode, 11);
                  this.shopSequence = 2;
                  this.shopTick = 0;
               }
               break;
            case 2:
               if (screenTitleContains(mc.gui.screen(), "END")) {
                  ModuleChat.send(this, ModuleChat.body().text("Shop: selecting the ").name("Ender Chest"));
                  clickSlot(player, gameMode, 9);
                  this.shopSequence = 3;
                  this.shopTick = 0;
               }
               break;
            case 3:
               if (screenTitleContains(mc.gui.screen(), "ENDER CHEST")) {
                  ModuleChat.send(this, "Shop: confirming the purchase");
                  clickSlot(player, gameMode, 25);
                  this.shopSequence = 4;
                  this.shopTick = 0;
               }
               break;
            case 4:
               if (getEnderChestCount(player) > 0) {
                  ModuleChat.send(this, ModuleChat.body().name("Ender Chest").text(" ").good("bought"));
                  player.closeContainer();
                  this.currentState = SpawnerProtectModule.State.PLACING_ECHEST;
                  this.shopTick = 0;
               } else if (this.shopTick > 100) {
                  ModuleChat.send(this, ModuleChat.body().text("Purchase ").danger("failed").text(" (timed out)"));
                  player.closeContainer();
                  this.shopSequence = 0;
                  this.shopTick = 0;
               }
         }
      }
   }

   private static boolean screenTitleContains(Screen screen, String text) {
      return screen != null && screen.getTitle().getString().toUpperCase(Locale.ROOT).contains(text);
   }

   private static void clickSlot(LocalPlayer player, MultiPlayerGameMode gameMode, int slot) {
      gameMode.handleContainerInput(player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, player);
   }

   private void handlePlacingEChest(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
      this.shopTick++;
      if (this.shopTick >= 10) {
         Inventory inventory = player.getInventory();
         int slot = -1;

         for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).getItem() == Items.ENDER_CHEST) {
               slot = i;
               break;
            }
         }

         if (slot == -1) {
            for (int ix = 9; ix < 36; ix++) {
               if (inventory.getItem(ix).getItem() == Items.ENDER_CHEST) {
                  gameMode.handleContainerInput(player.containerMenu.containerId, ix, 0, ContainerInput.QUICK_MOVE, player);
                  this.shopTick = 0;
                  return;
               }
            }

            this.currentState = SpawnerProtectModule.State.BUYING_ECHEST;
            this.shopSequence = 0;
         } else {
            inventory.setSelectedSlot(slot);
            BlockPos feet = player.blockPosition();
            BlockPos place = null;

            for (Direction direction : Direction.values()) {
               if (direction != Direction.UP && direction != Direction.DOWN) {
                  BlockPos candidate = feet.relative(direction);
                  if (level.getBlockState(candidate).canBeReplaced()) {
                     place = candidate;
                     break;
                  }
               }
            }

            if (place != null) {
               gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(place), Direction.UP, place, false));
               player.swing(InteractionHand.MAIN_HAND);
               this.targetChest = place;
               this.currentState = SpawnerProtectModule.State.GOING_TO_CHEST;
            } else {
               ModuleChat.send(this, ModuleChat.body().danger("No space").text(" to place the ").name("Ender Chest"));
               this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
               this.lagWaitTicks = 0;
            }
         }
      }
   }

   private void handleFinalExit(Minecraft mc) {
      if (this.lagWaitTicks == 0) {
         this.sendWebhook("\ud83c\udfc1 **TAMAMLANDI.** Eşyaların kaydedilmesi için bekleniyor...");
         ModuleChat.send(this, ModuleChat.body().good("Done").text(": waiting ").value("2s").text(" for the server to save the items, then logging out"));
      }

      this.lagWaitTicks++;
      if (this.lagWaitTicks > 40) {
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            connection.getConnection().disconnect(Component.literal("[SpawnerProtect] Görev tamamlandı, güvenli çıkış yapıldı."));
         }

         this.resetModule();
      }
   }

   private void updateSneak(LocalPlayer player, boolean sneak) {
      if (player != null) {
         player.setShiftKeyDown(sneak);
      }

      this.sneakHeld = sneak;
   }

   private void stopBreaking() {
      this.breakCooldown = 0;
   }

   private void stopMovement() {
      this.stopBreaking();
      this.forwardHeld = false;
   }

   private static ItemEntity findDroppedSpawner(LocalPlayer player, ClientLevel level) {
      ItemEntity best = null;
      float bestDistance = 0.0F;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof ItemEntity item && item.getItem().getItem() == Items.SPAWNER) {
            float distance = player.distanceTo(item);
            if (!(distance >= 16.0F) && (best == null || distance < bestDistance)) {
               best = item;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private static BlockPos findRandomBlock(LocalPlayer player, ClientLevel level, Block block, int radius) {
      BlockPos origin = player.blockPosition();
      int maxDistSqr = radius * radius;
      List<BlockPos> matches = new ArrayList<>();
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               if (x * x + y * y + z * z <= maxDistSqr) {
                  pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                  if (level.getBlockState(pos).getBlock() == block) {
                     matches.add(pos.immutable());
                  }
               }
            }
         }
      }

      return matches.isEmpty() ? null : matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
   }

   private static BlockPos findNearestBlock(LocalPlayer player, ClientLevel level, Block block, int radius) {
      BlockPos origin = player.blockPosition();
      BlockPos nearest = null;
      double minDistSqr = Double.MAX_VALUE;
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
               if (level.getBlockState(pos).getBlock() == block) {
                  double distSqr = x * x + y * y + z * z;
                  if (distSqr < minDistSqr) {
                     minDistSqr = distSqr;
                     nearest = pos.immutable();
                  }
               }
            }
         }
      }

      return nearest;
   }

   private static int getSpawnerCount(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int count = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.getItem() == Items.SPAWNER) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private static int getEnderChestCount(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int count = 0;

      for (int i = 0; i < 45; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.getItem() == Items.ENDER_CHEST) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private void sendWebhook(String message) {
      String url = this.webhookUrl.get().trim();
      if (!url.isEmpty()) {
         if (!isValidWebhookUrl(url)) {
            LOGGER.warn("[Spectre] Spawner Protect webhook URL is invalid; nothing was sent");
         } else {
            WebhookConfig config = new WebhookConfig(url);
            WebhookPayloadBuilder payload = new WebhookPayloadBuilder().content(message);
            WEBHOOK_EXECUTOR.execute(() -> {
               try {
                  SpawnerProtectModule.WebhookHolder.SERVICE.send(config, payload);
               } catch (LinkageError | RuntimeException var3x) {
                  LOGGER.warn("[Spectre] Spawner Protect webhook failed ({})", var3x.getClass().getSimpleName());
               }
            });
         }
      }
   }

   private static boolean isValidWebhookUrl(String url) {
      try {
         URI uri = new URI(url);
         String scheme = uri.getScheme();
         return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
      } catch (URISyntaxException var3) {
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum State {
      WAITING_FOR_STRANGER,
      WORKING,
      GOING_TO_CHEST,
      OPENING_CHEST,
      DEPOSITING_ITEMS,
      FINAL_EXIT,
      BUYING_ECHEST,
      PLACING_ECHEST;
   }

   @Environment(EnvType.CLIENT)
   private static final class WebhookHolder {
      static final WebhookService SERVICE = new WebhookService();
   }
}
