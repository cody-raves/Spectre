package net.spectre.client.gui.mainmenu;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MenuAction {
   SINGLEPLAYER("Singleplayer"),
   MULTIPLAYER("Multiplayer"),
   REALMS("Realms"),
   OPTIONS("Options"),
   QUIT("Quit"),
   LANGUAGE("Language"),
   ACCESSIBILITY("Accessibility"),
   SPECTRE("Spectre Client"),
   MODS("Mods Folder"),
   ACCOUNTS("Accounts");

   public static final List<MenuAction> MAIN = List.of(SINGLEPLAYER, MULTIPLAYER, REALMS, OPTIONS, QUIT);
   private final String label;

   private MenuAction(String label) {
      this.label = label;
   }

   public String label() {
      return this.label;
   }
}
