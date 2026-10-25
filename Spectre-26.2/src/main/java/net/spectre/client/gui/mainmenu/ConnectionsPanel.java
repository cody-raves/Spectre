package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.spectre.client.account.Account;
import net.spectre.client.account.AccountType;
import net.spectre.client.account.Accounts;
import net.spectre.client.gui.SpectreDraw;
import net.spectre.client.proxy.Proxies;
import net.spectre.client.proxy.Proxy;
import net.spectre.client.proxy.ProxyChecker;
import net.spectre.client.proxy.ProxyType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

@Environment(EnvType.CLIENT)
public final class ConnectionsPanel {
   private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
   private static final int WINDOW_FLAGS = 447;
   private static final float ACCOUNTS_W = 820.0F;
   private static final float PROXIES_W = 860.0F;
   private static final float PAD = 24.0F;
   private static final float FIELD_H = 50.0F;
   private static final float ACCOUNT_ROW_H = 68.0F;
   private static final float PROXY_ROW_H = 46.0F;
   private static final float TAB_H = 40.0F;
   private static final float BAR_H = 30.0F;
   private static final float FACE = 46.0F;
   private static final float CHECK = 16.0F;
   private static final int ACCOUNT_ROWS = 6;
   private static final int PROXY_ROWS = 8;
   private static final float FADE = 0.15F;
   private static final float CONFIRM_FOR = 3.0F;
   static final int WINDOW = -15461610;
   private static final int ERROR = -1733516;
   private static final int GOOD = -6501498;
   private final Runnable onProxyChange;
   private final DuskWidgets ui = new DuskWidgets();
   private final MenuFrame frame = new MenuFrame();
   private boolean open;
   private boolean opening;
   private long openedAt;
   private ConnectionsPanel.Tab tab = ConnectionsPanel.Tab.ACCOUNTS;
   private String routeAtOpen = "";
   private Object armed;
   private float armedUntil;
   private AccountType adding = AccountType.CRACKED;
   private AccountType accountFilter;
   private final DuskWidgets.TextField crackedName = new DuskWidgets.TextField(16)
      .placeholder("Username")
      .allowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_");
   private final DuskWidgets.TextField token = new DuskWidgets.TextField(4096).placeholder("Minecraft access token").password(true);
   private CompletableFuture<?> microsoft;
   private String accountError;
   private final DuskWidgets.TextField proxyInput = new DuskWidgets.TextField(512).placeholder("host:port or socks5://user:pass@host:port");
   private String proxyMessage;
   private boolean proxyError;
   private ConnectionsPanel.Filter filter = ConnectionsPanel.Filter.ALL;
   private ProxyType typeFilter;
   private ConnectionsPanel.Sort sort = ConnectionsPanel.Sort.ADDED;
   private final Set<Proxy> selected = new HashSet<>();
   private boolean fileDialog;

   public ConnectionsPanel(Runnable onProxyChange) {
      this.onProxyChange = onProxyChange;
   }

   public void open(ConnectionsPanel.Tab tab) {
      if (!this.open) {
         this.open = true;
         this.opening = true;
         this.openedAt = System.nanoTime();
         this.routeAtOpen = route();
      }

      this.tab = tab;
      this.armed = null;
      if (tab == ConnectionsPanel.Tab.PROXIES) {
         this.proxyInput.focus();
      } else if (this.adding != AccountType.MICROSOFT) {
         this.field(this.adding).focus();
      }
   }

   public boolean isOpen() {
      return this.open;
   }

   public boolean keyPressed(int key) {
      if (!this.open) {
         return false;
      } else {
         if (key == 256) {
            this.close();
         }

         return true;
      }
   }

   public void render(float width, float height, float scale, float dt) {
      if (this.open) {
         ImGuiIO io = ImGui.getIO();
         boolean click = !this.opening && ImGui.isMouseClicked(0);
         this.opening = false;
         ImGui.setNextWindowPos(0.0F, 0.0F);
         ImGui.setNextWindowSize(width, height);
         ImGui.pushStyleVar(2, 0.0F, 0.0F);
         ImGui.pushStyleVar(4, 0.0F);
         ImGui.begin("##spectre_connections", 447);
         ImGui.popStyleVar(2);

         try {
            this.frame
               .begin(
                  ImGui.getWindowDrawList(),
                  ImGui.getForegroundDrawList(),
                  width,
                  height,
                  scale,
                  (float)(System.nanoTime() - this.openedAt) / 1.0E9F,
                  dt,
                  io.getMousePosX(),
                  io.getMousePosY(),
                  click
               );
            this.ui.begin(this.frame);

            try {
               this.draw();
            } finally {
               this.ui.end();
            }

            this.drawTooltip();
         } finally {
            ImGui.end();
         }
      }
   }

   private void close() {
      this.open = false;
      this.crackedName.setText("");
      this.token.setText("");
      this.proxyInput.setText("");
      this.selected.clear();
      if (this.microsoft != null) {
         Accounts.get().cancelMicrosoft();
      }

      if (!route().equals(this.routeAtOpen)) {
         this.onProxyChange.run();
      }
   }

   private void draw() {
      ImDrawList dl = this.frame.bg();
      float s = this.frame.scale();
      float sw = this.frame.width();
      float sh = this.frame.height();
      float fade = Math.min(1.0F, this.frame.time() / 0.15F);
      float a = 1.0F - (1.0F - fade) * (1.0F - fade);
      this.ui.alpha(a);
      DuskScene.veil(dl, sw, sh, 0.5F * a);
      boolean accounts = this.tab == ConnectionsPanel.Tab.ACCOUNTS;
      List<Proxy> shown = accounts ? List.of() : this.shownProxies();
      List<Account> listed = accounts ? this.shownAccounts() : List.of();
      float titleH = this.frame.textHeight(this.frame.itemFont());
      float lineH = this.frame.textHeight(this.frame.bodyFont());
      float listH = accounts ? Math.max(3, Math.min(listed.size(), 6)) * 68.0F * s : Math.max(1, Math.min(shown.size(), 8)) * 46.0F * s;
      float bars = accounts ? 96.0F * s : 60.0F * s + 22.0F * s;
      float w = Math.min((accounts ? 820.0F : 860.0F) * s, sw - 32.0F * s);
      float h = Math.min(24.0F * s + titleH + 16.0F * s + 50.0F * s + 12.0F * s + lineH + 18.0F * s + bars + listH + 16.0F * s, sh - 32.0F * s);
      float x = Math.round((sw - w) * 0.5F);
      float y = Math.round((sh - h) * 0.5F + (1.0F - a) * 6.0F * s);

      for (int i = 1; i <= 3; i++) {
         float g = i * 4.0F * s;
         dl.addRectFilled(x - g, y - g + 4.0F * s, x + w + g, y + h + g + 4.0F * s, SpectreDraw.col(-16777216, 0.08F * a));
      }

      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-15461610, a));
      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-923950, 0.1F * a), 0.0F, 0, Math.max(1.0F, s));
      float pad = 24.0F * s;
      this.frame.text(dl, this.frame.itemFont(), x + pad, y + pad * 0.8F, -923950, a, accounts ? "Accounts" : "Proxies");
      this.closeCross(x + w - 44.0F * s, y + pad * 0.8F + titleH * 0.5F - 16.0F * s, 32.0F * s, s, a);
      float top = y + pad + titleH + 16.0F * s;
      float listBottom = y + h - 16.0F * s;
      if (accounts) {
         this.accounts(listed, x, top, w, listBottom, s, a);
      } else {
         this.proxies(shown, x, top, w, listBottom, s, a);
      }
   }

   private void closeCross(float x, float y, float size, float s, float a) {
      ImDrawList dl = this.frame.bg();
      boolean over = this.ui.hovered(x, y, size, size);
      if (this.ui.pressed(x, y, size, size)) {
         this.close();
      }

      float k = this.ui.hover("close", over);
      float cx = x + size * 0.5F;
      float cy = y + size * 0.5F;
      float c = 6.0F * s;
      int color = SpectreDraw.col(SpectreDraw.mix(-7372954, -923950, k), a);
      float line = Math.max(1.2F, 1.6F * s);
      dl.addLine(cx - c, cy - c, cx + c, cy + c, color, line);
      dl.addLine(cx - c, cy + c, cx + c, cy - c, color, line);
   }

   private List<Account> shownAccounts() {
      List<Account> all = Accounts.get().list();
      return this.accountFilter == null ? all : all.stream().filter(account -> account.type() == this.accountFilter).toList();
   }

   private void accounts(List<Account> listed, float x, float y, float w, float bottom, float s, float a) {
      float pad = 24.0F * s;
      float fx = x + pad;
      float fw = w - 2.0F * pad;
      boolean busy = Accounts.get().busy();
      this.addTabs(fx, y, s, a);
      y += 54.0F * s;
      float fh = 50.0F * s;
      ImFont font = this.frame.bodyFont();
      String hint;
      if (this.adding == AccountType.MICROSOFT) {
         String label = this.microsoft != null ? "Waiting for the browser..." : "Sign in with Microsoft";
         if (this.box("microsoft", label, fx, y, fw, fh, this.microsoft == null && !busy, a)) {
            this.signInMicrosoft();
         }

         hint = "Opens Microsoft's sign-in in your browser";
      } else {
         DuskWidgets.TextField field = this.field(this.adding);
         if (this.ui.textField(field, fx, y, fw, fh) && !field.text().isBlank() && !busy) {
            this.add(field);
         }

         hint = this.adding == AccountType.CRACKED
            ? "Press Enter to add. Joins servers that don't check accounts."
            : "Press Enter to add. A token works until it expires, about a day.";
      }

      y += fh + 12.0F * s;
      if (this.microsoft != null) {
         float cancelW = this.frame.textWidth(font, "Cancel");
         this.text(DuskWidgets.fit(font, Accounts.get().status(), fw - cancelW - 16.0F * s), fx, y, -923950, a);
         if (this.link("cancel", "Cancel", fx + fw - cancelW, y, true, a)) {
            Accounts.get().cancelMicrosoft();
         }
      } else if (busy) {
         this.text(DuskWidgets.fit(font, Accounts.get().status(), fw), fx, y, -7372954, a);
      } else if (this.accountError != null) {
         this.text(DuskWidgets.fit(font, this.accountError, fw), fx, y, -1733516, a);
      } else {
         this.text(DuskWidgets.fit(font, hint, fw), fx, y, -7372954, a);
      }

      y += this.frame.textHeight(font) + 18.0F * s;
      this.accountFilters(fx, y, s, a);
      y += 42.0F * s;
      float listH = bottom - y;
      if (listed.isEmpty()) {
         this.centered(this.accountFilter == null ? "No accounts yet" : "No " + this.accountFilter.label() + " accounts", x, y, w, listH, a);
      } else {
         Account active = Accounts.get().active();
         float rowX = x + 8.0F * s;
         float rowW = w - 16.0F * s;
         float rowH = 68.0F * s;
         float scroll = this.ui.beginScroll("accounts", rowX, y, rowW, listH, listed.size() * rowH);

         for (int i = 0; i < listed.size(); i++) {
            Account account = listed.get(i);
            this.accountRow(account, account.equals(active), rowX, y - scroll + i * rowH, rowW, rowH, s, a);
         }

         this.ui.endScroll();
      }
   }

   private void addTabs(float x, float y, float s, float a) {
      ImDrawList dl = this.frame.bg();
      ImFont font = this.frame.menuFont();
      float h = 40.0F * s;
      float ty = y + (h - this.frame.textHeight(font)) * 0.5F;

      for (AccountType type : AccountType.values()) {
         String label = type.label();
         float tw = this.frame.textWidth(font, label);
         boolean over = this.ui.hovered(x - 6.0F * s, y, tw + 12.0F * s, h);
         if (this.ui.pressed(x - 6.0F * s, y, tw + 12.0F * s, h) && this.microsoft == null && type != this.adding) {
            this.adding = type;
            this.accountError = null;
            if (type != AccountType.MICROSOFT) {
               this.field(type).focus();
            }
         }

         float k = this.ui.hover(new ConnectionsPanel.Key("add", label), over);
         this.frame.text(dl, font, x, ty, type == this.adding ? -923950 : SpectreDraw.mix(-7372954, -923950, k), a, label);
         if (type == this.adding) {
            dl.addRectFilled(x, y + h - 2.0F * s, x + tw, y + h, SpectreDraw.col(-871878, a));
         }

         x += tw + 30.0F * s;
      }
   }

   private void accountFilters(float x, float y, float s, float a) {
      List<Account> all = Accounts.get().list();
      if (this.chip("all-accounts", "All", all.size(), this.accountFilter == null, x, y, a)) {
         this.accountFilter = null;
      }

      x += this.chipWidth("All", all.size()) + 18.0F * s;

      for (AccountType type : AccountType.values()) {
         int count = (int)all.stream().filter(account -> account.type() == type).count();
         if (this.chip(type, type.label(), count, this.accountFilter == type, x, y, a)) {
            this.accountFilter = type;
         }

         x += this.chipWidth(type.label(), count) + 18.0F * s;
      }
   }

   private void accountRow(Account account, boolean inUse, float x, float y, float w, float h, float s, float a) {
      ImDrawList dl = this.frame.bg();
      boolean over = this.ui.hovered(x, y, w, h);
      float k = this.ui.hover(account, over);
      if (k > 0.01F && !inUse) {
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-923950, 0.05F * k * a));
      }

      float face = 46.0F * s;
      float fx = x + 12.0F * s;
      PlayerFaces.draw(dl, account, fx, y + (h - face) * 0.5F, face, s, a);
      String name = account.name().isEmpty() ? account.type().label() + " account" : account.name();
      float right = this.removeCross(new ConnectionsPanel.Key(account, "remove"), x + w - 8.0F * s, y, h, k, s, a);
      if (right < 0.0F) {
         Accounts.get().remove(account);
      } else {
         if (inUse) {
            right = this.tag("Active", -1326998, right, y, h, a);
         }

         this.entry(this.frame.menuFont(), this.frame.listFont(), fx + face + 16.0F * s, y, h, right - 12.0F * s, name, account.type().label(), a);
         if (this.ui.pressed(x, y, w, h) && !inUse) {
            this.login(account);
         }
      }
   }

   private DuskWidgets.TextField field(AccountType type) {
      return type == AccountType.SESSION ? this.token : this.crackedName;
   }

   private void add(DuskWidgets.TextField field) {
      CompletableFuture<Account> added = field == this.token ? Accounts.get().addSession(field.text()) : Accounts.get().addCracked(field.text());
      this.track(added);
      added.thenRun(() -> field.setText(""));
   }

   private void login(Account account) {
      if (!Accounts.get().busy()) {
         this.track(Accounts.get().login(account));
      }
   }

   private void signInMicrosoft() {
      CompletableFuture<Account> signIn = Accounts.get().addMicrosoft();
      this.microsoft = signIn;
      this.track(signIn);
      signIn.whenComplete((account, failure) -> this.microsoft = null);
   }

   private void track(CompletableFuture<?> action) {
      this.accountError = null;
      action.whenComplete((result, failure) -> this.accountError = failure == null ? null : messageOf(failure));
   }

   private static String messageOf(Throwable failure) {
      Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
      String message = cause.getMessage();
      return message != null && !message.isBlank() ? message : "Login failed";
   }

   private List<Proxy> shownProxies() {
      List<Proxy> shown = new ArrayList<>();

      for (Proxy proxy : Proxies.get().list()) {
         if (this.filter.matches(proxy) && (this.typeFilter == null || proxy.type == this.typeFilter)) {
            shown.add(proxy);
         }
      }

      if (this.sort.order != null) {
         shown.sort(this.sort.order);
      }

      return shown;
   }

   private static int pingOrder(Proxy proxy) {
      if (proxy.ping >= 0) {
         return proxy.ping;
      } else {
         return proxy.ping == -2 ? 2147483646 : Integer.MAX_VALUE;
      }
   }

   private void proxies(List<Proxy> shown, float x, float y, float w, float bottom, float s, float a) {
      float pad = 24.0F * s;
      float fx = x + pad;
      float fw = w - 2.0F * pad;
      List<Proxy> all = Proxies.get().list();
      this.selected.retainAll(new HashSet<>(all));
      float fh = 50.0F * s;
      float fieldW = fw - fh - 8.0F * s;
      if (this.ui.textField(this.proxyInput, fx, y, fieldW, fh) && !this.proxyInput.text().isBlank()) {
         int added = Proxies.get().importText(this.proxyInput.text());
         this.message(added == 0 ? "Not added: already saved, or not host:port" : null, added == 0);
         if (added > 0) {
            this.proxyInput.setText("");
         }
      }

      float px = fx + fw - fh;
      if (this.pasteButton(px, y, fh, a)) {
         String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
         this.imported(Proxies.get().importText(clipboard), clipboard.isBlank() ? "The clipboard is empty" : "Nothing new on the clipboard");
      }

      if (this.ui.hoverOf("paste") > 0.5F) {
         this.frame.tooltip("Paste proxies from the clipboard", px + fh * 0.5F, y + fh + 6.0F * s);
      }

      y += 62.0F * s;
      ImFont font = this.frame.bodyFont();
      if (this.link("file", "Import file", fx, y, !this.fileDialog, a)) {
         this.importFile();
      }

      if (this.ui.hoverOf("file") > 0.5F) {
         this.frame
            .tooltip("A text file with one proxy per line", fx + this.frame.textWidth(font, "Import file") * 0.5F, y + this.frame.textHeight(font) + 8.0F * s);
      }

      float lx = fx + (this.frame.textWidth(font, "Import file") + 24.0F * s);
      int pending = ProxyChecker.get().pending();
      if (pending > 0) {
         this.statusRight("Checking, " + pending + " left", false, lx, fx + fw, y, a);
      } else if (this.proxyMessage != null) {
         this.statusRight(this.proxyMessage, this.proxyError, lx, fx + fw, y, a);
      }

      y += this.frame.textHeight(font) + 18.0F * s;
      this.filters(all, fx, y, fw, s, a);
      y += 30.0F * s + 10.0F * s;
      this.actions(shown, all, fx, y, fw, s, a);
      y += 30.0F * s + 12.0F * s;
      float listH = bottom - y;
      if (shown.isEmpty()) {
         this.centered(all.isEmpty() ? "No proxies yet" : "None match the filter", x, y, w, listH, a);
      } else {
         float rowX = x + 8.0F * s;
         float rowW = w - 16.0F * s;
         float rowH = 46.0F * s;
         float scroll = this.ui.beginScroll("proxies", rowX, y, rowW, listH, shown.size() * rowH);
         int first = Math.max(0, (int)(scroll / rowH));
         int last = Math.min(shown.size(), (int)((scroll + listH) / rowH) + 1);

         for (int i = first; i < last; i++) {
            this.proxyRow(shown.get(i), rowX, y - scroll + i * rowH, rowW, rowH, s, a);
         }

         this.ui.endScroll();
      }
   }

   private void filters(List<Proxy> all, float x, float y, float w, float s, float a) {
      int[] counts = new int[ConnectionsPanel.Filter.values().length];
      int[] types = new int[ProxyType.values().length];

      for (Proxy proxy : all) {
         for (ConnectionsPanel.Filter each : ConnectionsPanel.Filter.values()) {
            if (each.matches(proxy)) {
               counts[each.ordinal()]++;
            }
         }

         types[proxy.type.ordinal()]++;
      }

      float cx = x;

      for (ConnectionsPanel.Filter eachx : ConnectionsPanel.Filter.values()) {
         if (this.chip(eachx, eachx.label, counts[eachx.ordinal()], this.filter == eachx, cx, y, a)) {
            this.filter = eachx;
         }

         cx += this.chipWidth(eachx.label, counts[eachx.ordinal()]) + 18.0F * s;
      }

      cx += 10.0F * s;
      this.frame.bg().addLine(cx, y + 4.0F * s, cx, y + 30.0F * s - 4.0F * s, SpectreDraw.col(-923950, 0.12F * a), Math.max(1.0F, s));
      cx += 20.0F * s;

      for (ProxyType type : ProxyType.values()) {
         boolean on = this.typeFilter == type;
         if (this.chip(type, type.label(), types[type.ordinal()], on, cx, y, a)) {
            this.typeFilter = on ? null : type;
         }

         cx += this.chipWidth(type.label(), types[type.ordinal()]) + 18.0F * s;
      }

      ImFont font = this.frame.bodyFont();
      String label = "Sort: " + this.sort.label;
      float lw = this.frame.textWidth(font, label);
      if (this.link("sort", label, x + w - lw, y + (30.0F * s - this.frame.textHeight(font)) * 0.5F, true, a)) {
         this.sort = ConnectionsPanel.Sort.values()[(this.sort.ordinal() + 1) % ConnectionsPanel.Sort.values().length];
      }
   }

   private void actions(List<Proxy> shown, List<Proxy> all, float x, float y, float w, float s, float a) {
      ImFont font = this.frame.bodyFont();
      float cy = y + 30.0F * s * 0.5F;
      float ty = cy - this.frame.textHeight(font) * 0.5F;
      boolean allShown = !shown.isEmpty() && this.selected.containsAll(shown);
      if (this.checkbox("select-all", allShown, x + 12.0F * s, cy, s, a)) {
         if (allShown) {
            this.selected.removeAll(shown);
         } else {
            this.selected.addAll(shown);
         }
      }

      float lx = x + 12.0F * s + 16.0F * s + 18.0F * s;
      if (this.selected.isEmpty()) {
         if (this.link("check-all", "Check all", lx, ty, !shown.isEmpty(), a)) {
            ProxyChecker.get().check(shown);
         }

         lx += this.frame.textWidth(font, "Check all") + 22.0F * s;
         List<Proxy> dead = all.stream().filter(proxy -> proxy.ping == -1).toList();
         if (!dead.isEmpty()) {
            ConnectionsPanel.Key removeDead = new ConnectionsPanel.Key("bulk", "remove-dead");
            String label = this.armed(removeDead) ? "Remove " + dead.size() + " dead?" : "Remove dead";
            if (this.link(removeDead, label, lx, ty, true, a, this.armed(removeDead) ? -1733516 : -7372954) && this.confirm(removeDead)) {
               Proxies.get().removeAll(dead);
               this.message("Removed " + dead.size() + " dead", false);
            }
         }
      } else {
         List<Proxy> picked = List.copyOf(this.selected);
         String count = picked.size() + " selected";
         this.text(count, lx, ty, -923950, a);
         lx += this.frame.textWidth(font, count) + 22.0F * s;
         if (this.link("check-selected", "Check", lx, ty, true, a)) {
            ProxyChecker.get().check(picked);
         }

         lx += this.frame.textWidth(font, "Check") + 22.0F * s;
         if (this.link("copy-selected", "Copy", lx, ty, true, a)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", picked.stream().map(ConnectionsPanel::line).toList()));
            this.message("Copied " + picked.size() + (picked.size() == 1 ? " proxy" : " proxies"), false);
         }

         lx += this.frame.textWidth(font, "Copy") + 22.0F * s;
         ConnectionsPanel.Key removeSelected = new ConnectionsPanel.Key("bulk", "remove-selected");
         String remove = this.armed(removeSelected) ? "Remove " + picked.size() + "?" : "Remove";
         if (this.link(removeSelected, remove, lx, ty, true, a, this.armed(removeSelected) ? -1733516 : -7372954) && this.confirm(removeSelected)) {
            Proxies.get().removeAll(picked);
            this.selected.clear();
            this.message("Removed " + picked.size(), false);
         }

         lx += this.frame.textWidth(font, remove) + 22.0F * s;
         if (this.link("clear-selection", "Clear", lx, ty, true, a)) {
            this.selected.clear();
         }
      }
   }

   private void proxyRow(Proxy proxy, float x, float y, float w, float h, float s, float a) {
      ImDrawList dl = this.frame.bg();
      boolean switchable = proxy.enabled || proxy.isValid();
      boolean over = this.ui.hovered(x, y, w, h);
      float k = this.ui.hover(proxy, over);
      boolean picked = this.selected.contains(proxy);
      if (picked) {
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.07F * a));
      } else if (k > 0.01F && switchable) {
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-923950, 0.05F * k * a));
      }

      float cy = y + h * 0.5F;
      if (this.checkbox(new ConnectionsPanel.Key(proxy, "select"), picked, x + 12.0F * s, cy, s, a)) {
         if (picked) {
            this.selected.remove(proxy);
         } else {
            this.selected.add(proxy);
         }
      }

      proxyDot(dl, x + 52.0F * s, cy, proxy.enabled, 1.3F * s, a);
      float right = this.removeCross(new ConnectionsPanel.Key(proxy, "remove"), x + w - 8.0F * s, y, h, k, s, a);
      if (right < 0.0F) {
         Proxies.get().remove(proxy);
         this.selected.remove(proxy);
      } else {
         if (proxy.enabled) {
            right = this.tag("In use", -1326998, right, y, h, a);
         }

         right = this.ping(proxy, right, y, h, a);
         String login = proxy.username.isEmpty() ? "" : " · " + proxy.username;
         String detail = proxy.name.equals(proxy.displayAddress()) ? proxy.type.label() + login : proxy.type.label() + " · " + proxy.displayAddress() + login;
         this.entry(this.frame.bodyFont(), this.frame.detailFont(), x + 74.0F * s, y, h, right - 12.0F * s, proxy.name, detail, a);
         if (this.ui.pressed(x, y, w, h) && switchable) {
            Proxies.get().setEnabled(proxy, !proxy.enabled);
            this.message(null, false);
         }
      }
   }

   private float ping(Proxy proxy, float right, float y, float h, float a) {
      float column = right - 96.0F * this.frame.scale();
      if (ProxyChecker.get().isChecking(proxy)) {
         int dots = (int)(this.frame.time() * 3.0F) % 4;
         this.tag("Checking" + ".".repeat(dots) + " ".repeat(3 - dots), -7372954, right, y, h, a);
      } else if (proxy.ping >= 0) {
         this.tag(proxy.ping + " ms", -6501498, right, y, h, a);
      } else if (proxy.ping == -1) {
         this.tag("Dead", -1733516, right, y, h, a);
      }

      return column;
   }

   private static String line(Proxy proxy) {
      String login = proxy.username.isEmpty() ? "" : proxy.username + (proxy.password.isEmpty() ? "" : ":" + proxy.password) + "@";
      String scheme = proxy.type == ProxyType.SOCKS4 ? "socks4://" : (login.isEmpty() ? "" : "socks5://");
      return scheme + login + proxy.displayAddress();
   }

   private void imported(int added, String none) {
      this.message(added > 0 ? "Added " + added + (added == 1 ? " proxy" : " proxies") : none, added == 0);
   }

   private void importFile() {
      this.fileDialog = true;
      Thread thread = new Thread(() -> {
         String chosen = null;

         try {
            chosen = TinyFileDialogs.tinyfd_openFileDialog("Import proxies", null, null, null, false);
         } finally {
            String path = chosen;
            Minecraft.getInstance().execute(() -> {
               this.fileDialog = false;
               if (path != null) {
                  this.importFrom(Path.of(path));
               }
            });
         }
      }, "Spectre proxy import");
      thread.setDaemon(true);
      thread.start();
   }

   private void importFrom(Path file) {
      try {
         this.imported(Proxies.get().importText(Files.readString(file)), "No proxies found in " + file.getFileName());
      } catch (IOException var3) {
         this.message("Couldn't read " + file.getFileName(), true);
      }
   }

   private void message(String text, boolean error) {
      this.proxyMessage = text;
      this.proxyError = error;
   }

   private static String route() {
      Proxy proxy = Proxies.get().enabled();
      return proxy == null ? "" : proxy.type + " " + proxy.displayAddress() + " " + proxy.username + " " + proxy.password;
   }

   private void entry(ImFont nameFont, ImFont detailFont, float x, float y, float h, float right, String name, String detail, float a) {
      float nh = this.frame.textHeight(nameFont);
      float gap = 3.0F * this.frame.scale();
      boolean oneLine = detail.isEmpty();
      float ty = oneLine ? y + (h - nh) * 0.5F : y + (h - nh - gap - this.frame.textHeight(detailFont)) * 0.5F;
      this.frame.text(this.frame.bg(), nameFont, x, ty, -923950, a, DuskWidgets.fit(nameFont, name, right - x));
      if (!oneLine) {
         this.frame.text(this.frame.bg(), detailFont, x, ty + nh + gap, -7372954, a, DuskWidgets.fit(detailFont, detail, right - x));
      }
   }

   private float tag(String text, int color, float right, float y, float h, float a) {
      ImFont font = this.frame.bodyFont();
      float x = right - this.frame.textWidth(font, text);
      this.frame.text(this.frame.bg(), font, x, y + (h - this.frame.textHeight(font)) * 0.5F, color, a, text);
      return x - 14.0F * this.frame.scale();
   }

   private float removeCross(ConnectionsPanel.Key key, float right, float y, float h, float k, float s, float a) {
      ImDrawList dl = this.frame.bg();
      if (this.armed(key)) {
         ImFont font = this.frame.bodyFont();
         String text = "Remove?";
         float tw = this.frame.textWidth(font, text);
         float x = right - tw;
         float th = this.frame.textHeight(font);
         boolean over = this.ui.hovered(x - 6.0F * s, y, tw + 12.0F * s, h);
         if (this.ui.pressed(x - 6.0F * s, y, tw + 12.0F * s, h)) {
            this.armed = null;
            return -1.0F;
         } else {
            this.frame.text(dl, font, x, y + (h - th) * 0.5F, -1733516, over ? a : 0.85F * a, text);
            return x - 14.0F * s;
         }
      } else {
         float size = 28.0F * s;
         float x = right - size;
         float cy = y + h * 0.5F;
         boolean over = k > 0.5F && this.ui.hovered(x, cy - size * 0.5F, size, size);
         if (over && this.ui.pressed(x, cy - size * 0.5F, size, size)) {
            this.confirm(key);
         }

         float kc = this.ui.hover(key, over);
         if (k > 0.01F) {
            float cx = x + size * 0.5F;
            float c = 5.0F * s;
            float line = Math.max(1.2F, 1.5F * s);
            int color = SpectreDraw.col(SpectreDraw.mix(-7372954, -1733516, kc), k * a);
            dl.addLine(cx - c, cy - c, cx + c, cy + c, color, line);
            dl.addLine(cx - c, cy + c, cx + c, cy - c, color, line);
         }

         return x - 6.0F * s;
      }
   }

   private boolean checkbox(Object key, boolean checked, float x, float cy, float s, float a) {
      ImDrawList dl = this.frame.bg();
      float size = 16.0F * s;
      float y = cy - size * 0.5F;
      float reach = 8.0F * s;
      boolean over = this.ui.hovered(x - reach, y - reach, size + 2.0F * reach, size + 2.0F * reach);
      boolean clicked = over && this.ui.pressed(x - reach, y - reach, size + 2.0F * reach, size + 2.0F * reach);
      float k = this.ui.hover(key, over);
      if (checked) {
         dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-871878, a));
         dl.pathClear();
         dl.pathLineTo(x + size * 0.22F, y + size * 0.52F);
         dl.pathLineTo(x + size * 0.42F, y + size * 0.72F);
         dl.pathLineTo(x + size * 0.78F, y + size * 0.3F);
         dl.pathStroke(SpectreDraw.col(-16777216, a), 0, Math.max(1.4F, 2.0F * s));
      } else {
         dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-16777216, 0.3F * a));
         dl.addRect(x, y, x + size, y + size, SpectreDraw.col(SpectreDraw.mix(-923950, -871878, k), (0.3F + 0.4F * k) * a), 0.0F, 0, Math.max(1.0F, s));
      }

      return clicked;
   }

   private float chipWidth(String label, int count) {
      return this.frame.textWidth(this.frame.bodyFont(), label)
         + 7.0F * this.frame.scale()
         + this.frame.textWidth(this.frame.bodyFont(), Integer.toString(count));
   }

   private boolean chip(Object key, String label, int count, boolean on, float x, float y, float a) {
      ImDrawList dl = this.frame.bg();
      ImFont font = this.frame.bodyFont();
      float s = this.frame.scale();
      float w = this.chipWidth(label, count);
      float h = 30.0F * s;
      boolean over = this.ui.hovered(x - 4.0F * s, y, w + 8.0F * s, h);
      boolean clicked = over && this.ui.pressed(x - 4.0F * s, y, w + 8.0F * s, h);
      float k = this.ui.hover(key, over);
      float ty = y + (h - this.frame.textHeight(font)) * 0.5F;
      this.frame.text(dl, font, x, ty, on ? -1326998 : SpectreDraw.mix(-7372954, -923950, k), a, label);
      float lw = this.frame.textWidth(font, label) + 7.0F * s;
      this.frame.text(dl, font, x + lw, ty, -7372954, 0.8F * a, Integer.toString(count));
      return clicked;
   }

   private boolean box(Object key, String label, float x, float y, float w, float h, boolean enabled, float a) {
      ImDrawList dl = this.frame.bg();
      float s = this.frame.scale();
      float alpha = enabled ? a : 0.5F * a;
      boolean over = enabled && this.ui.hovered(x, y, w, h);
      boolean clicked = over && this.ui.pressed(x, y, w, h);
      float k = this.ui.hover(key, over);
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16777216, 0.35F * alpha));
      if (k > 0.01F) {
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.08F * k * alpha));
      }

      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(SpectreDraw.mix(-923950, -871878, k), (0.2F + 0.5F * k) * alpha), 0.0F, 0, Math.max(1.0F, s));
      ImFont font = this.frame.bodyFont();
      this.frame
         .text(
            dl,
            font,
            x + (w - this.frame.textWidth(font, label)) * 0.5F,
            y + (h - this.frame.textHeight(font)) * 0.5F,
            SpectreDraw.mix(-923950, -11654, k),
            alpha,
            label
         );
      return clicked;
   }

   private boolean pasteButton(float x, float y, float size, float a) {
      ImDrawList dl = this.frame.bg();
      float s = this.frame.scale();
      boolean over = this.ui.hovered(x, y, size, size);
      boolean clicked = over && this.ui.pressed(x, y, size, size);
      float k = this.ui.hover("paste", over);
      dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-16777216, 0.35F * a));
      if (k > 0.01F) {
         dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-871878, 0.08F * k * a));
      }

      dl.addRect(x, y, x + size, y + size, SpectreDraw.col(SpectreDraw.mix(-923950, -871878, k), (0.12F + 0.5F * k) * a), 0.0F, 0, Math.max(1.0F, s));
      float cx = x + size * 0.5F;
      float cy = y + size * 0.5F + s;
      float line = Math.max(1.2F, 1.5F * s);
      int color = SpectreDraw.col(SpectreDraw.mix(-7372954, -11654, k), a);
      dl.addRect(cx - 7.0F * s, cy - 8.0F * s, cx + 7.0F * s, cy + 9.0F * s, color, 2.0F * s, 0, line);
      dl.addRectFilled(cx - 3.5F * s, cy - 10.5F * s, cx + 3.5F * s, cy - 6.0F * s, color, s);
      dl.addLine(cx - 3.5F * s, cy - 1.0F * s, cx + 3.5F * s, cy - 1.0F * s, color, line);
      dl.addLine(cx - 3.5F * s, cy + 3.5F * s, cx + 2.0F * s, cy + 3.5F * s, color, line);
      return clicked;
   }

   private boolean link(Object key, String text, float x, float y, boolean enabled, float a) {
      return this.link(key, text, x, y, enabled, a, -7372954);
   }

   private boolean link(Object key, String text, float x, float y, boolean enabled, float a, int color) {
      ImFont font = this.frame.bodyFont();
      float s = this.frame.scale();
      float w = this.frame.textWidth(font, text);
      float h = this.frame.textHeight(font);
      boolean over = enabled && this.ui.hovered(x, y - 4.0F * s, w, h + 8.0F * s);
      boolean clicked = over && this.ui.pressed(x, y - 4.0F * s, w, h + 8.0F * s);
      float k = this.ui.hover(key, over);
      float alpha = enabled ? a : 0.4F * a;
      this.frame.text(this.frame.bg(), font, x, y, SpectreDraw.mix(color, -923950, k), alpha, text);
      if (k > 0.01F) {
         this.frame.bg().addLine(x, y + h + s, x + w * k, y + h + s, SpectreDraw.col(-923950, 0.6F * k * alpha), Math.max(1.0F, s));
      }

      return clicked;
   }

   private void statusRight(String status, boolean error, float left, float right, float y, float a) {
      ImFont font = this.frame.bodyFont();
      String shown = DuskWidgets.fit(font, status, right - left);
      this.text(shown, right - this.frame.textWidth(font, shown), y, error ? -1733516 : -7372954, a);
   }

   private void text(String text, float x, float y, int color, float a) {
      this.frame.text(this.frame.bg(), this.frame.bodyFont(), x, y, color, a, text);
   }

   private void centered(String text, float x, float y, float w, float h, float a) {
      ImFont font = this.frame.bodyFont();
      this.frame
         .text(this.frame.bg(), font, x + (w - this.frame.textWidth(font, text)) * 0.5F, y + (h - this.frame.textHeight(font)) * 0.5F, -7372954, a, text);
   }

   private void drawTooltip() {
      String text = this.frame.pendingTooltip();
      if (text != null) {
         ImDrawList dl = this.frame.fg();
         ImFont font = this.frame.detailFont();
         float s = this.frame.scale();
         float w = this.frame.textWidth(font, text) + 18.0F * s;
         float h = this.frame.textHeight(font) + 10.0F * s;
         float x = Math.max(6.0F * s, Math.min(this.frame.width() - w - 6.0F * s, this.frame.tooltipX() - w * 0.5F));
         float y = this.frame.tooltipY();
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16053748, 0.96F));
         dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-923950, 0.14F), 0.0F, 0, Math.max(1.0F, s));
         this.frame.text(dl, font, x + 9.0F * s, y + 5.0F * s, -923950, 1.0F, text);
      }
   }

   private boolean armed(Object key) {
      return key.equals(this.armed) && this.frame.time() < this.armedUntil;
   }

   private boolean confirm(Object key) {
      if (this.armed(key)) {
         this.armed = null;
         return true;
      } else {
         this.armed = key;
         this.armedUntil = this.frame.time() + 3.0F;
         return false;
      }
   }

   static void proxyDot(ImDrawList dl, float cx, float cy, boolean on, float s, float a) {
      if (on) {
         dl.addCircleFilled(cx, cy, 7.0F * s, SpectreDraw.col(-871878, 0.16F * a), 20);
         dl.addCircleFilled(cx, cy, 3.5F * s, SpectreDraw.col(-871878, a), 12);
      } else {
         dl.addCircle(cx, cy, 3.5F * s, SpectreDraw.col(-7372954, 0.8F * a), 12, Math.max(1.0F, s));
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum Filter {
      ALL("All"),
      WORKING("Working"),
      DEAD("Dead"),
      UNCHECKED("Unchecked");

      final String label;

      private Filter(String label) {
         this.label = label;
      }

      boolean matches(Proxy proxy) {
         return switch (this) {
            case ALL -> true;
            case WORKING -> proxy.ping >= 0;
            case DEAD -> proxy.ping == -1;
            case UNCHECKED -> proxy.ping == -2;
         };
      }
   }

   @Environment(EnvType.CLIENT)
   private record Key(Object owner, String what) {
   }

   @Environment(EnvType.CLIENT)
   private static enum Sort {
      ADDED("Added", null),
      PING("Ping", Comparator.comparingInt(ConnectionsPanel::pingOrder)),
      NAME("Name", Comparator.comparing(proxy -> proxy.name.toLowerCase(Locale.ROOT)));

      final String label;
      final Comparator<Proxy> order;

      private Sort(String label, Comparator<Proxy> order) {
         this.label = label;
         this.order = order;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Tab {
      ACCOUNTS,
      PROXIES;
   }
}
