package net.spectre.client.module.impl.misc;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.service.humanize.HumanInventoryTiming;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class AutoReplenishModule extends ClientModule {
   private static final int TRACKED_SLOTS = 10;
   private static final int OFFHAND_TRACKED_SLOT = 9;
   private static final int OFFHAND_SWAP_BUTTON = 40;
   private static final int MAIN_INVENTORY_FIRST = 9;
   private static final int MAIN_INVENTORY_LAST = 35;
   private static final double FAST_MOVEMENT_THRESHOLD_SQ = 0.06;
   private static final String MODE_INSTANT = "Instant";
   private static final String MODE_LEGIT = "Legit";
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Instant", "Instant", "Legit"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 1.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting threshold = this.addSetting("threshold", new NumberSetting("Threshold", 8.0F, 1.0F, 64.0F).step(1.0F));
   private final BooleanSetting offhand = this.addSetting("offhand", new BooleanSetting("Offhand", true));
   private final ItemStack[] lastKnown = new ItemStack[10];
   private final ItemStack[] pendingRefill = new ItemStack[10];
   private static final int QUIET_TICKS_BEFORE_OPEN = 10;
   private LocalPlayer trackedPlayer;
   private int stage;
   private int delayTimer;
   private int quietTicks;
   private int lastSelectedSlot = -1;
   private int activeSlot = -1;
   private ItemStack activeReference = ItemStack.EMPTY;
   private boolean wasScreenOpen;

   public AutoReplenishModule() {
      super("auto-replenish", "Auto Replenish", "Refills a hotbar or offhand slot from the main inventory once it runs low.", ModuleCategory.MISC);
      this.delay.visibleWhen(() -> this.mode.is("Instant"));
      this.mode.onChange(this::resetJob);
   }

   @Override
   protected void onEnable() {
      this.resetJob();
   }

   @Override
   protected void onDisable() {
      this.resetJob();
      this.trackedPlayer = null;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null) {
         if (player != this.trackedPlayer) {
            this.resync(player);
         }

         this.updateTracking(mc, player);
         if (this.delayTimer > 0) {
            this.delayTimer--;
         } else if (this.mode.is("Instant")) {
            this.refillNow(mc, player);
         } else {
            for (int transitions = 0; transitions < 3; transitions++) {
               switch (this.stage) {
                  case 0:
                     if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen)) {
                        return;
                     }

                     if (player.hasInfiniteMaterials()) {
                        return;
                     }

                     if (this.isMovingFast(player)) {
                        return;
                     }

                     if (player.isUsingItem() || mc.gameMode.isDestroying()) {
                        return;
                     }

                     if (this.quietTicks < 10) {
                        return;
                     }

                     int slot = this.nextPending(player);
                     if (slot < 0) {
                        return;
                     }

                     this.activeSlot = slot;
                     this.activeReference = this.pendingRefill[slot];
                     this.pendingRefill[slot] = ItemStack.EMPTY;
                     this.wasScreenOpen = mc.gui.screen() instanceof InventoryScreen;
                     this.stage = this.wasScreenOpen ? 2 : 1;
                     break;
                  case 1:
                     mc.gui.setScreen(new InventoryScreen(player));
                     this.stage = 2;
                     this.delayTimer = HumanInventoryTiming.openGuiDelayTicks();
                     return;
                  case 2:
                     if (mc.gui.screen() instanceof InventoryScreen screen) {
                        if (this.stillNeedsRefill(player)) {
                           Slot source = this.findSourceSlot(screen.getMenu(), player);
                           if (source != null) {
                              int button = this.activeSlot == 9 ? 40 : this.activeSlot;
                              mc.gameMode.handleContainerInput(screen.getMenu().containerId, source.index, button, ContainerInput.SWAP, player);
                           }
                        }

                        this.stage = 3;
                        this.delayTimer = HumanInventoryTiming.closeGuiDelayTicks();
                        return;
                     }

                     if (this.activeSlot >= 0 && !this.activeReference.isEmpty() && this.pendingRefill[this.activeSlot].isEmpty()) {
                        this.pendingRefill[this.activeSlot] = this.activeReference;
                     }

                     this.resetJob();
                     return;
                  case 3:
                     if (!this.wasScreenOpen && mc.gui.screen() instanceof InventoryScreen) {
                        mc.player.closeContainer();
                     }

                     this.resetJob();
                     return;
                  default:
                     this.resetJob();
               }
            }
         }
      }
   }

   private void refillNow(Minecraft mc, LocalPlayer player) {
      if (mc.gui.screen() == null && !player.hasInfiniteMaterials() && !player.isUsingItem()) {
         int slot = this.nextPending(player);
         if (slot >= 0) {
            this.activeSlot = slot;
            this.activeReference = this.pendingRefill[slot];
            this.pendingRefill[slot] = ItemStack.EMPTY;
            AbstractContainerMenu menu = player.inventoryMenu;
            Slot source = this.findSourceSlot(menu, player);
            if (source != null && menu.getCarried().isEmpty()) {
               int button = this.activeSlot == 9 ? 40 : this.activeSlot;
               mc.gameMode.handleContainerInput(menu.containerId, source.index, button, ContainerInput.SWAP, player);
               this.delayTimer = this.delay.getInt();
            }

            this.activeSlot = -1;
            this.activeReference = ItemStack.EMPTY;
         }
      }
   }

   private void resync(LocalPlayer player) {
      this.trackedPlayer = player;

      for (int i = 0; i < 10; i++) {
         ItemStack current = i == 9 ? player.getOffhandItem() : player.getInventory().getItem(i);
         this.lastKnown[i] = current.copy();
         this.pendingRefill[i] = ItemStack.EMPTY;
      }

      this.resetJob();
   }

   private void updateTracking(Minecraft mc, LocalPlayer player) {
      boolean foreignScreenOpen = this.stage == 0 && mc.gui.screen() instanceof AbstractContainerScreen;
      boolean infinite = player.hasInfiniteMaterials();
      boolean changed = false;
      int selectedSlot = player.getInventory().getSelectedSlot();
      if (selectedSlot != this.lastSelectedSlot) {
         this.lastSelectedSlot = selectedSlot;
         changed = true;
      }

      for (int i = 0; i < 10; i++) {
         if (i == 9 && !this.offhand.get()) {
            this.lastKnown[i] = player.getOffhandItem().copy();
            this.pendingRefill[i] = ItemStack.EMPTY;
         } else {
            ItemStack current = i == 9 ? player.getOffhandItem() : player.getInventory().getItem(i);
            ItemStack previous = this.lastKnown[i];
            if (!ItemStack.matches(previous, current)) {
               changed = true;
            }

            this.lastKnown[i] = current.copy();
            if (!current.is(Items.TOTEM_OF_UNDYING) && !previous.is(Items.TOTEM_OF_UNDYING)) {
               if (infinite) {
                  this.pendingRefill[i] = ItemStack.EMPTY;
               } else if (!foreignScreenOpen) {
                  boolean sameItem = previous.isEmpty() || current.getItem() == previous.getItem();
                  if (current.isEmpty() && !previous.isEmpty()) {
                     this.pendingRefill[i] = previous.copy();
                  } else if (!previous.isEmpty() && !previous.isStackable() && !sameItem) {
                     this.pendingRefill[i] = previous.copy();
                  } else if (!current.isStackable()
                     || current.isEmpty()
                     || !sameItem
                     || current.getCount() > this.threshold.getInt()
                     || !this.pendingRefill[i].isEmpty() && this.pendingRefill[i].getItem() != current.getItem()) {
                     if (!current.isEmpty() && current.getCount() > this.threshold.getInt()) {
                        this.pendingRefill[i] = ItemStack.EMPTY;
                     }
                  } else {
                     this.pendingRefill[i] = current.copy();
                  }
               }
            }
         }
      }

      this.quietTicks = changed ? 0 : Math.min(this.quietTicks + 1, 1000);
   }

   private int nextPending(LocalPlayer player) {
      for (int i = 0; i < 10; i++) {
         if (!this.pendingRefill[i].isEmpty() && this.hasSource(player, i, this.pendingRefill[i])) {
            return i;
         }
      }

      return -1;
   }

   private boolean stillNeedsRefill(LocalPlayer player) {
      ItemStack current = this.activeSlot == 9 ? player.getOffhandItem() : player.getInventory().getItem(this.activeSlot);
      if (current.isEmpty()) {
         return true;
      } else {
         return current.getItem() != this.activeReference.getItem() ? true : current.isStackable() && current.getCount() <= this.threshold.getInt();
      }
   }

   private boolean hasSource(LocalPlayer player, int trackedSlot, ItemStack reference) {
      ItemStack targetNow = trackedSlot == 9 ? player.getOffhandItem() : player.getInventory().getItem(trackedSlot);

      for (int i = 9; i <= 35; i++) {
         if (isViableSource(player.getInventory().getItem(i), targetNow, reference)) {
            return true;
         }
      }

      return false;
   }

   private Slot findSourceSlot(AbstractContainerMenu menu, LocalPlayer player) {
      ItemStack targetNow = this.activeSlot == 9 ? player.getOffhandItem() : player.getInventory().getItem(this.activeSlot);
      int bestCount = ItemStack.isSameItem(targetNow, this.activeReference) ? targetNow.getCount() : 0;
      Slot best = null;

      for (Slot slot : menu.slots) {
         if (slot.index >= 9 && slot.index <= 35) {
            ItemStack candidate = slot.getItem();
            if (matchesReference(candidate, this.activeReference) && candidate.getCount() > bestCount) {
               best = slot;
               bestCount = candidate.getCount();
            }
         }
      }

      return best;
   }

   private static boolean isViableSource(ItemStack candidate, ItemStack targetNow, ItemStack reference) {
      int have = ItemStack.isSameItem(targetNow, reference) ? targetNow.getCount() : 0;
      return matchesReference(candidate, reference) && candidate.getCount() > have;
   }

   private static boolean matchesReference(ItemStack candidate, ItemStack reference) {
      if (candidate.isEmpty()) {
         return false;
      } else {
         return reference.isStackable()
            ? ItemStack.isSameItemSameComponents(candidate, reference)
            : ItemStack.matchesIgnoringComponents(candidate, reference, type -> type == DataComponents.DAMAGE);
      }
   }

   private boolean isMovingFast(LocalPlayer player) {
      if (player.isSprinting()) {
         return true;
      } else if (player.isPassenger()) {
         Entity vehicle = player.getRootVehicle();
         double dx = vehicle.getX() - vehicle.xo;
         double dz = vehicle.getZ() - vehicle.zo;
         return dx * dx + dz * dz > 0.06;
      } else {
         Vec3 velocity = player.getDeltaMovement();
         return velocity.x * velocity.x + velocity.z * velocity.z > 0.06;
      }
   }

   private void resetJob() {
      this.stage = 0;
      this.delayTimer = 0;
      this.activeSlot = -1;
      this.activeReference = ItemStack.EMPTY;
      this.wasScreenOpen = false;
   }
}
