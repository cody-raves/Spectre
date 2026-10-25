package net.spectre.client.module.impl.combat;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import java.util.concurrent.ThreadLocalRandom;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class AutoInvTotemModule extends ClientModule {
   private static final String INVENTORY = "Inventory";
   private static final String HOTBAR_SWAP = "Hotbar Swap";
   private static final int MIN_CONFIRM_TICKS = 10;
   private static final int MAX_CONFIRM_TICKS = 40;
   private static final int PASS_COOLDOWN_TICKS = 4;
   private static final int GIVE_UP_COOLDOWN_TICKS = 10;
   private static final int MAX_PASS_SWAPS = 2;
   private static final int MAX_RESELECTS = 2;
   private static final int MAX_STEPS_PER_TICK = 3;
   private static AutoInvTotemModule instance;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Inventory", "Inventory", "Hotbar Swap"));
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
   private final NumberSetting reactionDelay = this.addSetting("reaction-delay", new NumberSetting("Reaction Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting swapDelay = this.addSetting("swap-delay", new NumberSetting("Swap Delay", 1.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting closeDelay = this.addSetting("close-delay", new NumberSetting("Close Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting switchBackDelay = this.addSetting("switch-back-delay", new NumberSetting("Switch Back Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting jitter = this.addSetting("jitter", new NumberSetting("Jitter", 0.0F, 0.0F, 3.0F).step(1.0F));
   private AutoInvTotemModule.Step step = AutoInvTotemModule.Step.IDLE;
   private int tick;
   private int readyAt;
   private int reactionAt = -1;
   private int idleUntil;
   private int confirmDeadline = -1;
   private boolean armed;
   private boolean openedInventory;
   private int passSwaps;
   private int totemSlot = -1;
   private int previousSlot = -1;
   private int reselects;

   public AutoInvTotemModule() {
      super("autoinvtotem", "Auto Inventory Totem", "Refills the offhand totem after a pop, through the inventory or a hotbar swap.", ModuleCategory.COMBAT);
      instance = this;
      this.hotbarRefill.visibleWhen(() -> this.mode.is("Inventory"));
      this.hotbarSlot.visibleWhen(() -> this.mode.is("Inventory") && this.hotbarRefill.get());
      this.closeDelay.visibleWhen(() -> this.mode.is("Inventory"));
      this.switchBackDelay.visibleWhen(() -> this.mode.is("Hotbar Swap"));
   }

   public static boolean wantsHotbar() {
      AutoInvTotemModule module = instance;
      if (module == null || !module.isEnabled() || !module.mode.is("Hotbar Swap")) {
         return false;
      } else if (module.step != AutoInvTotemModule.Step.IDLE) {
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         return player != null && mc.gui.screen() == null && !module.awaitingServer(player) && module.needsOffhand(player) && findHotbarTotem(player) != -1;
      }
   }

   @Override
   protected void onEnable() {
      this.armed = false;
      this.confirmDeadline = -1;
      this.idleUntil = 0;
      this.endSequence();
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null) {
         if (this.openedInventory && mc.gui.screen() instanceof InventoryScreen) {
            player.closeContainer();
         }

         if ((this.step == AutoInvTotemModule.Step.HOTBAR_SWAP || this.step == AutoInvTotemModule.Step.HOTBAR_RETURN)
            && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
            this.restoreSlot(player.getInventory());
         }
      }

      this.endSequence();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.tick++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null && !player.isDeadOrDying() && !player.isSpectator()) {
         if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            this.armed = true;
         }

         if (this.step == AutoInvTotemModule.Step.IDLE) {
            this.tryStart(mc, player);
         }

         for (int i = 0; i < 3 && this.step != AutoInvTotemModule.Step.IDLE && this.tick >= this.readyAt; i++) {
            switch (this.step) {
               case INVENTORY_SWAP:
                  this.inventorySwap(mc, player);
                  break;
               case INVENTORY_CLOSE:
                  this.closeInventory(mc, player);
                  break;
               case HOTBAR_SWAP:
                  this.hotbarSwap(mc, player);
                  break;
               case HOTBAR_RETURN:
                  this.hotbarReturn(mc, player);
                  break;
               default:
                  this.endSequence();
            }
         }
      } else {
         this.confirmDeadline = -1;
         this.endSequence();
      }
   }

   private void tryStart(Minecraft mc, LocalPlayer player) {
      if (!this.awaitingServer(player)) {
         this.confirmDeadline = -1;
         boolean hotbarMode = this.mode.is("Hotbar Swap");
         boolean hasWork = hotbarMode
            ? mc.gui.screen() == null && this.needsOffhand(player) && findHotbarTotem(player) != -1
            : !player.hasInfiniteMaterials() && (mc.gui.screen() == null || mc.gui.screen() instanceof InventoryScreen) && this.inventoryHasWork(player);
         if (!hasWork) {
            this.reactionAt = -1;
         } else {
            if (this.reactionAt < 0) {
               this.reactionAt = this.tick + this.withJitter(this.reactionDelay);
            }

            if (this.tick >= this.reactionAt && this.tick >= this.idleUntil) {
               if (hotbarMode) {
                  this.startHotbarSwap(player);
               } else {
                  this.startInventoryPass(mc, player);
               }
            }
         }
      }
   }

   private void startInventoryPass(Minecraft mc, LocalPlayer player) {
      this.openedInventory = !(mc.gui.screen() instanceof InventoryScreen);
      if (this.openedInventory) {
         mc.gui.setScreen(new InventoryScreen(player));
      }

      this.passSwaps = 0;
      this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, this.swapDelay);
   }

   private void inventorySwap(Minecraft mc, LocalPlayer player) {
      if (mc.gui.screen() instanceof InventoryScreen screen) {
         AbstractContainerMenu var8 = screen.getMenu();
         Inventory inventory = player.getInventory();
         int source = -1;
         int button = -1;
         if (this.needsOffhand(player)) {
            source = this.findOffhandSource(inventory);
            button = 40;
         } else if (this.needsHotbarRefill(inventory)) {
            source = findMainInventoryTotem(inventory);
            button = this.refillSlot();
         }

         if (source != -1 && this.passSwaps < 2 && var8.getCarried().isEmpty()) {
            mc.gameMode.handleContainerInput(var8.containerId, menuSlot(source), button, ContainerInput.SWAP, player);
            this.passSwaps++;
            if (this.inventoryHasWork(player) && this.passSwaps < 2) {
               this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, this.swapDelay);
            } else {
               this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay);
            }
         } else {
            this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay);
         }
      } else {
         this.finishPass();
      }
   }

   private void closeInventory(Minecraft mc, LocalPlayer player) {
      if (this.openedInventory && mc.gui.screen() instanceof InventoryScreen) {
         player.closeContainer();
      }

      this.finishPass();
   }

   private void finishPass() {
      this.idleUntil = this.tick + 4;
      this.endSequence();
   }

   private boolean inventoryHasWork(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      return this.needsOffhand(player) && this.findOffhandSource(inventory) != -1
         ? true
         : this.needsHotbarRefill(inventory) && findMainInventoryTotem(inventory) != -1;
   }

   private boolean needsHotbarRefill(Inventory inventory) {
      return this.hotbarRefill.get() && !inventory.getItem(this.refillSlot()).is(Items.TOTEM_OF_UNDYING);
   }

   private int refillSlot() {
      return Math.max(0, Math.min(8, this.hotbarSlot.getInt() - 1));
   }

   private int findOffhandSource(Inventory inventory) {
      int main = findMainInventoryTotem(inventory);
      if (main != -1) {
         return main;
      } else {
         int reserved = this.hotbarRefill.get() ? this.refillSlot() : -1;

         for (int i = 0; i < 9; i++) {
            if (i != reserved && inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
               return i;
            }
         }

         return reserved != -1 && inventory.getItem(reserved).is(Items.TOTEM_OF_UNDYING) ? reserved : -1;
      }
   }

   private static int findMainInventoryTotem(Inventory inventory) {
      for (int i = 9; i < 36; i++) {
         if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return -1;
   }

   private static int menuSlot(int inventoryIndex) {
      return Inventory.isHotbarSlot(inventoryIndex) ? 36 + inventoryIndex : inventoryIndex;
   }

   private void startHotbarSwap(LocalPlayer player) {
      if (CombatMacroLock.tryAcquire(this)) {
         Inventory inventory = player.getInventory();
         this.totemSlot = findHotbarTotem(player);
         this.previousSlot = inventory.getSelectedSlot();
         this.reselects = 0;
         inventory.setSelectedSlot(this.totemSlot);
         this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay);
      }
   }

   private void hotbarSwap(Minecraft mc, LocalPlayer player) {
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) {
         this.endSequence();
      } else if (mc.gui.screen() != null) {
         this.readyAt = this.tick + 1;
      } else {
         Inventory inventory = player.getInventory();
         if (!this.needsOffhand(player)) {
            this.restoreSlot(inventory);
            this.endSequence();
         } else if (inventory.getSelectedSlot() == this.totemSlot && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).spectre$elytraFlySyncSelected();
            connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            this.confirmDeadline = this.tick + confirmWindow(connection, player);
            this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, this.switchBackDelay);
         } else {
            int slot = findHotbarTotem(player);
            if (slot != -1 && this.reselects++ < 2) {
               this.totemSlot = slot;
               inventory.setSelectedSlot(slot);
               this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay);
            } else {
               this.restoreSlot(inventory);
               this.idleUntil = this.tick + 10;
               this.endSequence();
            }
         }
      }
   }

   private void hotbarReturn(Minecraft mc, LocalPlayer player) {
      if (mc.gui.screen() != null) {
         this.readyAt = this.tick + 1;
      } else {
         this.restoreSlot(player.getInventory());
         this.endSequence();
      }
   }

   private void restoreSlot(Inventory inventory) {
      if (Inventory.isHotbarSlot(this.previousSlot) && this.previousSlot != this.totemSlot && inventory.getSelectedSlot() == this.totemSlot) {
         inventory.setSelectedSlot(this.previousSlot);
      }
   }

   private static int findHotbarTotem(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
         return inventory.getSelectedSlot();
      } else {
         for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
               return i;
            }
         }

         return -1;
      }
   }

   private static int confirmWindow(ClientPacketListener connection, LocalPlayer player) {
      PlayerInfo info = connection.getPlayerInfo(player.getUUID());
      int latencyMs = info != null ? info.getLatency() : 0;
      return Mth.clamp(latencyMs / 25 + 4, 10, 40);
   }

   private boolean awaitingServer(LocalPlayer player) {
      return this.confirmDeadline >= 0 && this.tick < this.confirmDeadline && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   private boolean needsOffhand(LocalPlayer player) {
      return this.armed && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   private void schedule(AutoInvTotemModule.Step next, NumberSetting delay) {
      this.step = next;
      this.readyAt = this.tick + this.withJitter(delay);
   }

   private int withJitter(NumberSetting delay) {
      int spread = this.jitter.getInt();
      return delay.getInt() + (spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0);
   }

   private void endSequence() {
      this.step = AutoInvTotemModule.Step.IDLE;
      this.reactionAt = -1;
      this.openedInventory = false;
      this.passSwaps = 0;
      this.totemSlot = -1;
      this.previousSlot = -1;
      this.reselects = 0;
      CombatMacroLock.release(this);
   }

   @Environment(EnvType.CLIENT)
   private static enum Step {
      IDLE,
      INVENTORY_SWAP,
      INVENTORY_CLOSE,
      HOTBAR_SWAP,
      HOTBAR_RETURN;
   }
}
