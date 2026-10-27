package net.spectre.client.module.impl.misc;

import imgui.ImDrawList;
import imgui.ImFont;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.SpectreWidgets;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.StringSetting;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.SuspectObjects;
import net.spectre.client.util.SuspectObjects.Category;

/**
 * Repeats {@code /rtp} and scores the area you land in for received-world clues
 * (containers, hoppers, machines, redstone, spawners). Scores are leads to
 * investigate together with Sus Chunk Finder, never proof of a base.
 */
@Environment(EnvType.CLIENT)
public final class AutoRtpModule extends ClientModule implements HudOverlayProvider {
   private static final double LANDED_DISTANCE_SQ = 25.0;
   private static final long SCAN_BUDGET_NANOS = 2000000L;
   private static final int MAX_CONSECUTIVE_TIMEOUTS = 3;
   private static final int MAX_HITS = 20;
   private static final int MAX_CHUNK_RETRIES = 4;
   private static final int NATURAL_SCORE_CAP = 5;
   private static final int DETAIL_LINES = 3;

   private final StringSetting command = this.addSetting("command", new StringSetting("RTP Command", "rtp"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (s)", 3.0F, 1.0F, 30.0F).step(0.5F));
   private final NumberSetting timeout = this.addSetting("timeout", new NumberSetting("RTP Timeout (s)", 15.0F, 1.0F, 60.0F).step(1.0F));
   private final NumberSetting settle = this.addSetting("settle", new NumberSetting("Settle Time (s)", 2.0F, 0.0F, 10.0F).step(0.5F));
   private final NumberSetting radius = this.addSetting("radius", new NumberSetting("Scan Radius (chunks)", 1.0F, 0.0F, 4.0F).step(1.0F));
   private final NumberSetting threshold = this.addSetting("threshold", new NumberSetting("Report Score", 4.0F, 1.0F, 100.0F).step(1.0F));
   private final BooleanSetting stopOnHit = this.addSetting("stop-on-hit", new BooleanSetting("Stop On Hit", true));
   private final NumberSetting maxRtps = this.addSetting("max-rtps", new NumberSetting("Max RTPs", 20.0F, 0.0F, 500.0F).step(1.0F));
   private final BooleanSetting includeNatural = this.addSetting("include-natural", new BooleanSetting("Include Natural Clues", false));
   private final BooleanSetting logScans = this.addSetting("log-scans", new BooleanSetting("Log Every Scan", false));

   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongArrayList deferred = new LongArrayList();
   private final Long2IntOpenHashMap retries = new Long2IntOpenHashMap();
   private final EnumMap<Category, Integer> counts = new EnumMap<>(Category.class);
   private final List<Hit> hits = new ArrayList<>();

   private Step step = Step.WAIT;
   private long nextAtMs;
   private long rtpDeadlineMs;
   private long settleDeadlineMs;
   private long scanDeadlineMs;
   private Vec3 rtpStart;
   private int attempt;
   private int scans;
   private int failStreak;
   private int lastScore;
   private int landingX;
   private int landingY;
   private int landingZ;
   private String lastDetail = "";

   public AutoRtpModule() {
      super("auto-rtp", "Auto RTP", "Random-teleports in a loop and scores each landing area for containers, hoppers, machines and redstone clues. Scores are leads to investigate, not proof of a base.", ModuleCategory.DONUT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.getConnection() == null) {
         this.requestDisable();
         return;
      }
      this.resetRun();
      this.step = AutoRtpModule.Step.WAIT;
      this.nextAtMs = System.currentTimeMillis() + this.delayMs();
   }

   @Override
   protected void onDisable() {
      this.clearScan();
   }

   private void resetRun() {
      this.attempt = 0;
      this.scans = 0;
      this.failStreak = 0;
      this.lastScore = 0;
      this.lastDetail = "";
      this.hits.clear();
      this.clearScan();
   }

   private void clearScan() {
      this.queue.clear();
      this.deferred.clear();
      this.retries.clear();
      this.counts.clear();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || mc.getConnection() == null) {
         this.requestDisable();
         return;
      }

      long now = System.currentTimeMillis();
      switch (this.step) {
         case WAIT:
            if (now >= this.nextAtMs) {
               String cmd = this.command.get().trim();
               while (cmd.startsWith("/")) {
                  cmd = cmd.substring(1);
               }
               if (cmd.isEmpty()) {
                  ModuleChat.send(this, ModuleChat.body().warn("RTP command is empty; stopping."));
                  this.requestDisable();
                  return;
               }
               mc.getConnection().sendCommand(cmd);
               this.attempt++;
               this.rtpStart = mc.player.position();
               this.rtpDeadlineMs = now + this.timeoutMs();
               this.step = AutoRtpModule.Step.TELEPORT;
            }
            break;
         case TELEPORT:
            if (mc.player.position().distanceToSqr(this.rtpStart) > LANDED_DISTANCE_SQ) {
               Vec3 landing = mc.player.position();
               this.landingX = (int)Math.floor(landing.x);
               this.landingY = (int)Math.floor(landing.y);
               this.landingZ = (int)Math.floor(landing.z);
               this.failStreak = 0;
               this.settleDeadlineMs = now + this.settleMs();
               this.step = AutoRtpModule.Step.SETTLE;
            } else if (now >= this.rtpDeadlineMs) {
               this.failStreak++;
               if (this.failStreak >= MAX_CONSECUTIVE_TIMEOUTS) {
                  ModuleChat.send(this, ModuleChat.body().warn("no teleport after ").value(this.failStreak).text(" tries; stopping."));
                  this.requestDisable();
                  return;
               }
               ModuleChat.send(this, ModuleChat.body().warn("teleport timed out; retrying."));
               this.step = AutoRtpModule.Step.WAIT;
               this.nextAtMs = now + this.delayMs();
            }
            break;
         case SETTLE:
            if (now >= this.settleDeadlineMs) {
               this.buildScanQueue(mc.level);
               this.scanDeadlineMs = now + this.settleMs() * 3L + 5000L;
               this.step = AutoRtpModule.Step.SCAN;
            }
            break;
         case SCAN:
            if (this.scanChunks(mc.level, now)) {
               this.evaluate(now);
            }
            break;
      }
   }

   private void buildScanQueue(ClientLevel level) {
      this.clearScan();
      int r = this.radius.getInt();
      ChunkPos center = Minecraft.getInstance().player.chunkPosition();

      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            this.queue.enqueue(ChunkPos.pack(center.x() + dx, center.z() + dz));
         }
      }
   }

   /** Returns true once every queued chunk was scanned or the deadline passed. */
   private boolean scanChunks(ClientLevel level, long now) {
      long budget = System.nanoTime() + SCAN_BUDGET_NANOS;
      this.deferred.clear();

      while (!this.queue.isEmpty() && System.nanoTime() < budget) {
         long key = this.queue.dequeueLong();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         if (!LevelUtil.isLoaded(level, cx, cz)) {
            this.deferred.add(key);
            continue;
         }
         this.scanChunk(level.getChunk(cx, cz));
      }

      for (int i = 0; i < this.deferred.size(); i++) {
         long key = this.deferred.getLong(i);
         int tries = this.retries.get(key) + 1;
         this.retries.put(key, tries);
         if (tries < MAX_CHUNK_RETRIES) {
            this.queue.enqueue(key);
         }
      }

      return this.queue.isEmpty() || now >= this.scanDeadlineMs;
   }

   private void scanChunk(LevelChunk chunk) {
      for (LevelChunkSection section : chunk.getSections()) {
         if (section == null || section.hasOnlyAir()) {
            continue;
         }
         if (!section.maybeHas(state -> SuspectObjects.category(state) != null)) {
            continue;
         }
         for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  Category category = SuspectObjects.category(section.getBlockState(x, y, z));
                  if (category != null) {
                     this.counts.merge(category, 1, Integer::sum);
                  }
               }
            }
         }
      }
   }

   private void evaluate(long now) {
      int score = this.score();
      this.scans++;
      this.lastScore = score;
      this.lastDetail = this.detail();
      boolean hit = score >= this.threshold.getInt();

      if (hit) {
         if (this.hits.size() >= MAX_HITS) {
            this.hits.remove(0);
         }
         this.hits.add(new AutoRtpModule.Hit(this.landingX, this.landingY, this.landingZ, score, this.lastDetail));
         ModuleChat.send(this, ModuleChat.body()
            .good("clue").text(" score ").value(score).text(" at ").coords(this.landingX, this.landingY, this.landingZ)
            .text(this.lastDetail.isEmpty() ? "" : " · " + this.lastDetail));
      } else if (this.logScans.get()) {
         ModuleChat.send(this, ModuleChat.body()
            .text("scan ").value(this.scans).text(" score ").value(score)
            .text(" at ").coords(this.landingX, this.landingY, this.landingZ));
      }

      int max = this.maxRtps.getInt();
      if (hit && this.stopOnHit.get()) {
         ModuleChat.send(this, ModuleChat.body().text("stopping on hit after ").value(this.attempt).text(" rtp(s)."));
         this.requestDisable();
         return;
      }
      if (max > 0 && this.attempt >= max) {
         ModuleChat.send(this, ModuleChat.body().text("finished ").value(this.attempt).text(" rtp(s), ").value(this.hits.size()).text(" hit(s)."));
         this.requestDisable();
         return;
      }

      this.step = AutoRtpModule.Step.WAIT;
      this.nextAtMs = now + this.delayMs();
   }

   private int score() {
      int total = 0;
      int natural = 0;
      for (var entry : this.counts.entrySet()) {
         int count = entry.getValue();
         int contribution = switch (entry.getKey()) {
            case SPAWNERS -> 6 * count;
            case HOPPERS -> 3 * count;
            case MACHINES, ENDER_STORAGE -> 2 * count;
            case CONTAINERS, REDSTONE, DISPLAYS -> count;
            case GEODES, GROWTH -> this.includeNatural.get() ? count : 0;
            default -> 0;
         };
         if (entry.getKey() == Category.GEODES || entry.getKey() == Category.GROWTH) {
            natural += contribution;
         } else {
            total += contribution;
         }
      }
      if (this.includeNatural.get()) {
         total += Math.min(natural, NATURAL_SCORE_CAP);
      }
      return total;
   }

   private String detail() {
      var entries = new ArrayList<>(this.counts.entrySet());
      entries.removeIf(entry -> entry.getValue() <= 0
         || entry.getKey() == Category.GEODES || entry.getKey() == Category.GROWTH);
      entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
      var parts = new ArrayList<String>();
      for (var entry : entries) {
         parts.add(entry.getKey().label + " x" + entry.getValue());
      }
      if (parts.size() > DETAIL_LINES) {
         int extra = parts.size() - DETAIL_LINES;
         return String.join(", ", parts.subList(0, DETAIL_LINES)) + " +" + extra + " more";
      }
      return String.join(", ", parts);
   }

   private long delayMs() {
      return (long)(this.delay.get() * 1000.0);
   }

   private long timeoutMs() {
      return (long)(this.timeout.get() * 1000.0);
   }

   private long settleMs() {
      return (long)(this.settle.get() * 1000.0);
   }

   @Override
   public boolean isHudOverlayEnabled() {
      var hud = this.showInHud();
      return this.isEnabled() && (hud == null || hud.get());
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      String cap = this.maxRtps.getInt() > 0 ? "/" + this.maxRtps.getInt() : "";
      String line1 = String.format(Locale.ROOT, "Auto RTP %d%s · %s · score %d/%d · hits %d",
         this.attempt, cap, this.phase(), this.lastScore, this.threshold.getInt(), this.hits.size());
      String line2 = this.hits.isEmpty() ? "" : this.hits.getLast().hudText();

      float lineHeight = SpectreWidgets.lineHeight(font);
      float width = Math.max(SpectreWidgets.textWidth(font, line1), SpectreWidgets.textWidth(font, line2));
      float x = screenW - width - 8.0F;
      float y = screenH - lineHeight * 2.0F - 12.0F;
      SpectreWidgets.text(dl, font, x + 1.0F, y + 1.0F, -1610612736, line1);
      SpectreWidgets.text(dl, font, x, y, -3618616, line1);
      if (!line2.isEmpty()) {
         SpectreWidgets.text(dl, font, x + 1.0F, y + lineHeight + 1.0F, -1610612736, line2);
         SpectreWidgets.text(dl, font, x, y + lineHeight, -3618616, line2);
      }
   }

   private String phase() {
      return this.step.name().toLowerCase(Locale.ROOT);
   }

   @Environment(EnvType.CLIENT)
   private enum Step {
      WAIT,
      TELEPORT,
      SETTLE,
      SCAN;
   }

   @Environment(EnvType.CLIENT)
   private record Hit(int x, int y, int z, int score, String detail) {
      String hudText() {
         return "hit " + this.x + ", " + this.y + ", " + this.z + " (" + this.score + ")";
      }
   }
}
