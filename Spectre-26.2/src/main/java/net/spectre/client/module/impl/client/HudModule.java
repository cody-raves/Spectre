package net.spectre.client.module.impl.client;

import java.util.List;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HudModule extends ClientModule {
   private final BooleanSetting mascot = this.addSetting("mascot", new BooleanSetting("HUD Bar Mascot", true));
   private final BooleanSetting coordinates = this.addSetting("coordinates", new BooleanSetting("Overworld Coords", true));
   private final BooleanSetting netherCoordinates = this.addSetting("nether-coordinates", new BooleanSetting("Nether Coords", true));
   private final BooleanSetting ping = this.addSetting("ping", new BooleanSetting("Ping", true));
   private final BooleanSetting fps = this.addSetting("fps", new BooleanSetting("FPS", true));
   private final BooleanSetting clock = this.addSetting("clock", new BooleanSetting("Clock", true));
   private final BooleanSetting logo = this.addSetting("logo", new BooleanSetting("Logo", true));
   private final BooleanSetting moduleList = this.addSetting("module-list", new BooleanSetting("Module List", true));
   private final BooleanSetting potions = this.addSetting("potions", new BooleanSetting("Potions", true));
   private final BooleanSetting targetCard = this.addSetting("target-card", new BooleanSetting("Target HUD", true));
   private final BooleanSetting targetPlayersOnly = this.addSetting("target-players-only", new BooleanSetting("Players Only", false));
   private final BooleanSetting targetOnLook = this.addSetting("target-on-look", new BooleanSetting("Show On Look", false));
   private final BooleanSetting targetGear = this.addSetting("target-gear", new BooleanSetting("Show Gear", true));
   private final BooleanSetting armorStatus = this.addSetting("armor-status", new BooleanSetting("Armor HUD", true));
   private final ModeSetting armorLayout = this.addSetting("armor-layout", new ModeSetting("Armor Layout", "Row", "Row", "Column"));
   private final ModeSetting armorWear = this.addSetting("armor-wear", new ModeSetting("Wear As", "Gauge", "Gauge", "Percent", "Gauge + Percent"));
   private final BooleanSetting armorHands = this.addSetting("armor-hands", new BooleanSetting("Include Hands", false));
   private final BooleanSetting itemCooldowns = this.addSetting("item-cooldowns", new BooleanSetting("Cooldown", true));
   private final ModeSetting cooldownDial = this.addSetting("cooldown-dial", new ModeSetting("Cooldown Dial", "Halo", "Halo", "Sandbar"));
   private final ModeSetting toastStyle = this.addSetting("toast-style", new ModeSetting("Toast Style", "Bar", "Bar", "Card", "Chip"));
   private final BooleanSetting shadow = this.addSetting("shadow", new BooleanSetting("Shadow", true));
   private final BooleanSetting customAccent = this.addSetting("custom-accent", new BooleanSetting("Custom Accent", false));
   private final ColorSetting accentColor = this.addSetting("accent-color", new ColorSetting("Accent Color", -13338378));

   public HudModule() {
      super("hud", "HUD", "Module list, coordinates and status overlays.", ModuleCategory.CLIENT);
      this.accentColor.visibleWhen(this.customAccent::get);
      this.targetPlayersOnly.visibleWhen(this.targetCard::get);
      this.targetOnLook.visibleWhen(this.targetCard::get);
      this.targetGear.visibleWhen(this.targetCard::get);
      this.armorLayout.visibleWhen(this.armorStatus::get);
      this.armorWear.visibleWhen(this.armorStatus::get);
      this.armorHands.visibleWhen(this.armorStatus::get);
      this.cooldownDial.visibleWhen(this.itemCooldowns::get);
   }

   @Override
   public boolean enabledByDefault() {
      return true;
   }

   public boolean mascot() { return this.mascot.get(); }

   public boolean coordinates() {
      return this.coordinates.get();
   }

   public boolean netherCoordinates() {
      return this.netherCoordinates.get();
   }

   public boolean ping() {
      return this.ping.get();
   }

   public boolean fps() {
      return this.fps.get();
   }

   public boolean clock() {
      return this.clock.get();
   }

   public boolean logo() {
      return this.logo.get();
   }

   public boolean moduleList() {
      return this.moduleList.get();
   }

   public boolean potions() {
      return this.potions.get();
   }

   public boolean targetCard() {
      return this.targetCard.get();
   }

   public boolean targetPlayersOnly() {
      return this.targetPlayersOnly.get();
   }

   public boolean targetOnLook() {
      return this.targetOnLook.get();
   }

   public boolean targetGear() {
      return this.targetGear.get();
   }

   public boolean armorStatus() {
      return this.armorStatus.get();
   }

   public boolean armorColumn() {
      return this.armorLayout.is("Column");
   }

   public boolean armorHands() {
      return this.armorHands.get();
   }

   public boolean itemCooldowns() {
      return this.itemCooldowns.get();
   }

   public boolean cooldownSandbar() {
      return this.cooldownDial.is("Sandbar");
   }

   public boolean shadow() {
      return this.shadow.get();
   }

   public boolean customAccent() {
      return this.customAccent.get();
   }

   public int accentArgb() {
      return this.accentColor.argb();
   }

   public HudModule.ArmorWear armorWear() {
      String var1 = this.armorWear.get();

      return switch (var1) {
         case "Percent" -> HudModule.ArmorWear.PERCENT;
         case "Gauge + Percent" -> HudModule.ArmorWear.BOTH;
         default -> HudModule.ArmorWear.GAUGE;
      };
   }

   public HudModule.ToastStyle toastStyle() {
      String var1 = this.toastStyle.get();

      return switch (var1) {
         case "Card" -> HudModule.ToastStyle.CARD;
         case "Chip" -> HudModule.ToastStyle.CHIP;
         default -> HudModule.ToastStyle.BAR;
      };
   }

   public List<Setting> elementSettings(String elementId) {
      return switch (elementId) {
         case "info_bar" -> List.of(this.clock, this.fps, this.ping);
         case "coordinates" -> List.of(this.coordinates, this.netherCoordinates);
         case "module_list" -> List.of(this.moduleList);
         case "potion_tracker" -> List.of(this.potions);
         case "target_card" -> List.of(this.targetCard, this.targetPlayersOnly, this.targetOnLook, this.targetGear);
         case "armor_status" -> List.of(this.armorStatus, this.armorLayout, this.armorWear, this.armorHands);
         case "item_cooldowns" -> List.of(this.itemCooldowns, this.cooldownDial);
         case "toggle_toasts" -> List.of(this.toastStyle);
         default -> List.of();
      };
   }

   @Environment(EnvType.CLIENT)
   public static enum ArmorWear {
      GAUGE,
      PERCENT,
      BOTH;
   }

   @Environment(EnvType.CLIENT)
   public static enum ToastStyle {
      BAR,
      CARD,
      CHIP;
   }
}
