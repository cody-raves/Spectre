package net.spectre.client.module.impl.combat;

import com.example.client.mixin.AccessorAbstractContainerScreen;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class HoverTotemModule extends ClientModule {
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));

   public HoverTotemModule() {
      super("hovertotem", "Hover Totem", "Swaps a hovered inventory totem into the offhand or selected hotbar slot.", ModuleCategory.COMBAT);
      this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.gameMode != null && mc.gui.screen() instanceof InventoryScreen screen) {
         Slot hovered = ((AccessorAbstractContainerScreen)screen).spectre$getHoveredSlot();
         if (hovered != null && hovered.getItem().is(Items.TOTEM_OF_UNDYING)) {
            int swapButton;
            if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
               swapButton = 40;
            } else {
               if (!this.hotbarRefill.get()) {
                  return;
               }

               swapButton = this.hotbarSlot.getInt() - 1;
               if (mc.player.getInventory().getItem(swapButton).is(Items.TOTEM_OF_UNDYING)) {
                  return;
               }
            }

            mc.gameMode.handleContainerInput(screen.getMenu().containerId, hovered.index, swapButton, ContainerInput.SWAP, mc.player);
         }
      }
   }
}
