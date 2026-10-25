package net.spectre.client.module.impl.render;

import com.example.client.mixin.AccessorAtmosphereClientLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class AtmosphereModule extends ClientModule {
   private static final String CUSTOM = "Custom";
   private static final String AURORA_ALWAYS = "Always";
   private static final String AURORA_COLD = "Cold Biomes";
   private static final String AURORA_OFF = "Off";
   private static final float MOON_SCALE = 1.4F;
   private static final AtmosphereModule.Theme[] THEMES = new AtmosphereModule.Theme[]{
      new AtmosphereModule.Theme("Borealis", 198414, 729648, 13073302, 3080090, 9063935, 13163263),
      new AtmosphereModule.Theme("Nebula", 459791, 2233916, 14053304, 16732120, 5991679, 15124735),
      new AtmosphereModule.Theme("Spectre", 657157, 2890512, 15239498, 16757306, 16730670, 16769200),
      new AtmosphereModule.Theme("Frost", 264724, 1454154, 11057384, 8382719, 15135999, 14544639),
      new AtmosphereModule.Theme("Crimson", 721926, 3018260, 15227490, 16724821, 16751164, 16765640)
   };
   private final ModeSetting theme = this.addSetting(
      "theme", new ModeSetting("Sky palette", "Follow GUI", "Follow GUI", "Default", "Midnight", "Rose", "Mint", "Amber", "Mono", "Teal Nebula", "Violet Aurora", "Borealis", "Nebula", "Spectre", "Frost", "Crimson", "Custom")
   );
   private final ModeSetting skyStyle=this.addSetting("sky-style",new ModeSetting("Sky style","Nebula","Nebula","Aurora","Both"));
   private final BooleanSetting satellites=this.addSetting("satellites",new BooleanSetting("Satellites",true));
   private final BooleanSetting ufos=this.addSetting("ufos",new BooleanSetting("UFO sightings",true));
   private final NumberSetting spaceDensity=this.addSetting("space-density",new NumberSetting("Sky object density",1,1,4).step(1));
   private final BooleanSetting ghostMoon=this.addSetting("ghost-moon",new BooleanSetting("Ghost moon",true));
   private final BooleanSetting spectralSun=this.addSetting("spectral-sun",new BooleanSetting("Spectral sun",true));
   private final NumberSetting sunGlow=this.addSetting("sun-glow",new NumberSetting("Sun corona",.8F,0,1).step(.05F));
   private final NumberSetting ghostMoonGlow=this.addSetting("ghost-moon-glow",new NumberSetting("Ghost moon glow",.6F,0,1).step(.05F));
   private final BooleanSetting nightSky=this.addSetting("night-sky",new BooleanSetting("Always night sky",false));
   private final SkyScenery scenery=new SkyScenery();
   private final ColorSetting nightColor = this.addSetting("night-color", new ColorSetting("Night Sky Color", -16047568));
   private final ColorSetting sunsetColor = this.addSetting("sunset-color", new ColorSetting("Sunset Color", -3703914));
   private final ColorSetting auroraColor = this.addSetting("aurora-color", new ColorSetting("Aurora Color", -13697126));
   private final ColorSetting auroraTopColor = this.addSetting("aurora-top-color", new ColorSetting("Aurora Top Color", -7713281));
   private final BooleanSetting gradientSky = this.addSetting("gradient-sky", new BooleanSetting("Gradient Sky", true));
   private final BooleanSetting keepInRain = this.addSetting("keep-in-rain", new BooleanSetting("Keep In Rain", true));
   private final BooleanSetting celestialGlow = this.addSetting("sun-moon-glow", new BooleanSetting("Sun & Moon Glow", true));
   private final BooleanSetting stars = this.addSetting("stars", new BooleanSetting("Stars", true));
   private final NumberSetting starBrightness = this.addSetting("star-brightness", new NumberSetting("Star Brightness", 1.3F, 0.5F, 2.0F).step(0.1F));
   private final BooleanSetting milkyWay = this.addSetting("milky-way", new BooleanSetting("Milky Way", true));
   private final NumberSetting milkyWayBrightness = this.addSetting(
      "milky-way-brightness", new NumberSetting("Milky Way Brightness", 1.0F, 0.2F, 2.0F).step(0.1F)
   );
   private final BooleanSetting shootingStars = this.addSetting("shooting-stars", new BooleanSetting("Shooting Stars", true));
   private final ModeSetting aurora = this.addSetting("aurora", new ModeSetting("Aurora", "Always", "Always", "Cold Biomes", "Off"));
   private final NumberSetting auroraBrightness = this.addSetting("aurora-brightness", new NumberSetting("Aurora Brightness", 1.0F, 0.2F, 2.0F).step(0.1F));
   private final NumberSetting auroraSpeed = this.addSetting("aurora-speed", new NumberSetting("Aurora Speed", 1.0F, 0.2F, 3.0F).step(0.1F));
   private final BooleanSetting cloudColors = this.addSetting("cloud-colors", new BooleanSetting("Cloud Colors", true));
   private final BooleanSetting endSky = this.addSetting("end-sky", new BooleanSetting("End Sky", true));
   private final BooleanSetting netherColors = this.addSetting("nether-colors", new BooleanSetting("Nether Colors", true));
   private final AtmosphereSky sky = new AtmosphereSky();
   private final AtmosphereStars starField = new AtmosphereStars();
   private final AtmosphereAurora auroraMesh = new AtmosphereAurora();
   private final PoseStack world = new PoseStack();
   private float night;
   private float rainBrightness = 1.0F;
   private float auroraHere;
   private long lastFrame;
   private boolean moonScaled;

   public AtmosphereModule() {
      super("atmosphere", "Spectre Sky", "GUI-matched nebulae and auroras, a ghost moon, shooting stars, satellites and UFO sightings.", ModuleCategory.VISUALS);
      this.ghostMoonGlow.visibleWhen(this.ghostMoon::get);
      this.sunGlow.visibleWhen(this.spectralSun::get);
      this.spaceDensity.visibleWhen(()->this.satellites.get()||this.ufos.get());
      this.nightColor.visibleWhen(() -> this.theme.is("Custom"));
      this.sunsetColor.visibleWhen(() -> this.theme.is("Custom"));
      this.auroraColor.visibleWhen(() -> this.theme.is("Custom"));
      this.auroraTopColor.visibleWhen(() -> this.theme.is("Custom"));
      this.starBrightness.visibleWhen(this.stars::get);
      this.milkyWayBrightness.visibleWhen(this.milkyWay::get);
      this.auroraBrightness.visibleWhen(() -> !this.aurora.is("Off"));
      this.auroraSpeed.visibleWhen(() -> !this.aurora.is("Off"));
   }

   public static AtmosphereModule get() {
      AtmosphereModule module = ModuleRegistry.get(AtmosphereModule.class);
      return module != null && module.isEnabled() ? module : null;
   }

   @Override
   protected void onDisable() {
      this.sky.close();
      this.starField.close();
      this.auroraMesh.close();
      this.scenery.close();
   }

   public void extract(ClientLevel level, float partialTick, SkyRenderState state) {
      if(this.gradientSky.get())state.shouldRenderDarkDisc=false;
      if(this.nightSky.get()){state.sunAngle=(float)Math.PI;state.moonAngle=0;state.starBrightness=.5F;}
      this.night = Mth.clamp(state.starBrightness * 2.0F, 0.0F, 1.0F);
      float rain = 1.0F - state.rainBrightness;
      float thunder = level.getThunderLevel(partialTick);
      if (this.keepInRain.get()) {
         this.night = Math.max(this.night, clearNight(state.sunAngle) * rain);
         state.starBrightness = Math.max(state.starBrightness, this.night * 0.5F);
         state.rainBrightness = 1.0F;
         rain = 0.0F;
         thunder = 0.0F;
      }

      this.rainBrightness = 1.0F - rain;
      AtmosphereModule.Theme colours = this.currentTheme();
      int flashTicks = ((AccessorAtmosphereClientLevel)level).spectre$atmosphereSkyFlash();
      this.sky
         .update(
            state.sunAngle,
            state.moonAngle,
            rain,
            thunder,
            flashTicks > 0 ? Math.min(1.0F, (flashTicks - partialTick) / 2.0F) : 0.0F,
            colours.nightZenith,
            colours.nightHorizon,
            colours.twilight
         );
   }

   private static float clearNight(float sunAngle) {
      float dark = Mth.clamp(0.75F - 2.0F * Mth.cos(sunAngle), 0.0F, 1.0F);
      return dark * dark;
   }

   public boolean skyDisc() {
      if (this.gradientSky.get() && this.sky.ready()) {
         this.sky.renderDome(this.world);
         return true;
      } else {
         return false;
      }
   }

   public boolean replacesSunrise() {
      return this.gradientSky.get() && this.sky.ready();
   }

   public boolean stars(PoseStack pose) {
      float clear = this.night * this.rainBrightness;
      if(!this.skyStyle.is("Aurora"))this.starField.renderNebulae(pose,clear*.5F,this.currentTheme().auroraLow);
      if (this.milkyWay.get()) {
         this.starField.renderBand(pose, clear * this.milkyWayBrightness.get(), this.currentTheme().milkyWay);
      }

      if (!this.stars.get()) {
         return false;
      } else {
         this.starField.renderStars(pose, clear, this.starBrightness.get());
         return true;
      }
   }

   public void beforeMoon(PoseStack pose) {
      this.moonScaled = this.celestialGlow.get();
      if (this.moonScaled) {
         pose.pushPose();
         pose.scale(1.4F, 1.0F, 1.4F);
      }
   }

   public void afterMoon(PoseStack pose) {
      if (this.moonScaled) {
         pose.popPose();
      }

      this.moonScaled = false;
   }

   public void afterCelestials() {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min((float)(now - this.lastFrame) / 1.0E9F, 0.1F);
      this.lastFrame = now;
      if (this.sky.ready()) {
         this.scenery.render(this.world,now,this.night*this.rainBrightness,this.currentTheme().auroraLow,this.satellites.get(),this.ufos.get(),Math.round(this.spaceDensity.get()));
         if (this.celestialGlow.get()) {
            this.sky.renderHalos(this.world, this.rainBrightness, this.night);
         }

         if (this.shootingStars.get()) {
            this.sky.renderMeteors(this.world, this.night, this.rainBrightness);
         }

         this.auroraHere = this.auroraHere + (this.auroraTarget() - this.auroraHere) * Math.min(1.0F, dt * 0.5F);
         if (this.auroraHere > 0.01F && this.night > 0.01F) {
            AtmosphereModule.Theme colours = this.currentTheme();
            float time = (float)(now % 3600000000000L) / 1.0E9F * this.auroraSpeed.get();
            float intensity = this.auroraHere * this.night * (0.35F + 0.65F * this.rainBrightness) * this.auroraBrightness.get();
            this.auroraMesh.render(this.world, intensity, time, colours.auroraLow, colours.auroraHigh);
         }
      }
   }

   private float auroraTarget() {
      if(this.skyStyle.is("Nebula"))return 0;
      if (this.aurora.is("Off")) {
         return 0.0F;
      } else if (this.aurora.is("Always")) {
         return 1.0F;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            return 0.0F;
         } else {
            BlockPos at = BlockPos.containing(mc.gameRenderer.mainCamera().position());
            return mc.level.getBiome(at).value().coldEnoughToSnow(at, mc.level.getSeaLevel()) ? 1.0F : 0.0F;
         }
      }
   }

   public void fog(Camera camera, ClientLevel level, Vector4f color) {
      if (level.dimension() == Level.OVERWORLD) {
         if (!this.gradientSky.get() || !this.sky.ready()) {
            return;
         }

         Vector3fc look = camera.forwardVector();
         int rgb = this.sky.horizonToward(look.x(), look.z());
         color.x = (rgb >> 16 & 0xFF) / 255.0F;
         color.y = (rgb >> 8 & 0xFF) / 255.0F;
         color.z = (rgb & 0xFF) / 255.0F;
      } else if (level.dimension() == Level.NETHER && this.netherColors.get()) {
         float luma = 0.2126F * color.x + 0.7152F * color.y + 0.0722F * color.z;
         color.x = Mth.clamp((luma + (color.x - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
         color.y = Mth.clamp((luma + (color.y - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
         color.z = Mth.clamp((luma + (color.z - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
      }
   }

   public int clouds(int argb) {
      Minecraft mc = Minecraft.getInstance();
      if (this.cloudColors.get() && this.sky.ready() && mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
         float golden = this.sky.golden();
         float dark = this.sky.nightness();
         int target = ARGB.srgbLerp(golden, ARGB.srgbLerp(dark, 16777215, this.currentTheme().nightHorizon), this.sky.twilightGlow());
         float amount = Math.max(golden * 0.75F, dark * 0.55F);
         return keepBrightness(argb, target, amount);
      } else {
         return argb;
      }
   }

   public boolean endSky() {
      if (!this.endSky.get()) {
         return false;
      } else {
         long now = System.nanoTime();
         this.sky.renderEndDome(this.world);
         PoseStack turning = new PoseStack();
         turning.mulPose(Axis.YP.rotationDegrees((float)(now % 1200000000000L) / 1.0E9F * 0.3F));
         this.starField.renderNebulae(turning, 1.0F);
         this.starField.renderStars(turning, 1.0F, 1.2F);
         return true;
      }
   }

   private AtmosphereModule.Theme currentTheme() {
      int accent=-1;
      if(this.theme.is("Follow GUI"))accent=ThemeStore.get().accent.rgb();
      else if(this.theme.is("Teal Nebula"))accent=0x56DCCB;
      else if(this.theme.is("Violet Aurora"))accent=0xA58BFA;
      else for(var preset:ThemeStore.PRESETS)if(this.theme.is(preset.name())){accent=preset.accent()&0xFFFFFF;break;}
      if(accent>=0)return new Theme("GUI",AtmosphereSky.scale(accent,.025F),AtmosphereSky.scale(accent,.12F),ARGB.srgbLerp(.25F,accent,0xE6BDFF),accent,ARGB.srgbLerp(.4F,accent,0xBCA2FF),ARGB.srgbLerp(.3F,accent,0xFFFFFF));
      for (AtmosphereModule.Theme each : THEMES) {
         if (this.theme.is(each.name)) {
            return each;
         }
      }

      int horizon = this.nightColor.rgb();
      return new AtmosphereModule.Theme(
         "Custom", AtmosphereSky.scale(horizon, 0.3F), horizon, this.sunsetColor.rgb(), this.auroraColor.rgb(), this.auroraTopColor.rgb(), 15263999
      );
   }

   public boolean renderGhostMoon(PoseStack pose,float visibility){
      if(!this.ghostMoon.get())return false;
      this.scenery.moon(pose,visibility,this.currentTheme().auroraLow,this.ghostMoonGlow.get());return true;
   }

   public boolean renderSpectralSun(PoseStack pose,float visibility){
      if(!this.spectralSun.get())return false;
      this.scenery.sun(pose,visibility,this.currentTheme().auroraLow,this.sunGlow.get());return true;
   }

   private static int keepBrightness(int argb, int target, float amount) {
      if (amount <= 0.0F) {
         return argb;
      } else {
         float r = (argb >> 16 & 0xFF) / 255.0F;
         float g = (argb >> 8 & 0xFF) / 255.0F;
         float b = (argb & 0xFF) / 255.0F;
         float tr = (target >> 16 & 0xFF) / 255.0F;
         float tg = (target >> 8 & 0xFF) / 255.0F;
         float tb = (target & 0xFF) / 255.0F;
         float scale = luminance(r, g, b) / Math.max(luminance(tr, tg, tb), 0.001F);
         r = Mth.lerp(amount, r, Math.min(1.0F, tr * scale));
         g = Mth.lerp(amount, g, Math.min(1.0F, tg * scale));
         b = Mth.lerp(amount, b, Math.min(1.0F, tb * scale));
         return argb & 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
      }
   }

   private static float luminance(float r, float g, float b) {
      return 0.2126F * r + 0.7152F * g + 0.0722F * b;
   }

   private static int channel(float value) {
      return Mth.clamp(Math.round(value * 255.0F), 0, 255);
   }

   @Environment(EnvType.CLIENT)
   private record Theme(String name, int nightZenith, int nightHorizon, int twilight, int auroraLow, int auroraHigh, int milkyWay) {
   }
}
