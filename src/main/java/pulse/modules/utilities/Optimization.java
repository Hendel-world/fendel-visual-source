package pulse.modules.utilities;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.particle.ParticlesMode;
import pulse.events.ClientTickEvent;
import pulse.module.ClientModule;
import pulse.module.ModuleCategory;
import pulse.module.ModuleInfo;
import pulse.module.ModuleRegistry;
import pulse.settings.BooleanSetting;
import pulse.settings.SliderSetting;

@ModuleInfo(a = "Optimization", b = "FPS Boost с быстрым профилем графики и многопоточной сборкой чанков", c = ModuleCategory.UTILITIES)
public final class Optimization extends ClientModule {
   public static Optimization INSTANCE;
   public final BooleanSetting minecraftSettings = new BooleanSetting("Настройки Minecraft", true);
   public final SliderSetting chunkDistance = new SliderSetting("Прогрузка чанков", 8.0F, 2.0F, 32.0F, 1.0F);
   public final BooleanSetting fastGraphics = new BooleanSetting("Быстрая графика", true);
   public final BooleanSetting fpsBoostPreset = new BooleanSetting("FPS Boost", true);
   public final BooleanSetting multithreadedChunks = new BooleanSetting("Многопоточная сборка чанков", true);
   public final SliderSetting fpsLimit = new SliderSetting("FPS в игре", 260.0F, 60.0F, 260.0F, 10.0F);
   public final BooleanSetting dynamicFps = new BooleanSetting("Динамический FPS", true);
   public final SliderSetting bgFps = new SliderSetting("FPS в фоне", 10.0F, 5.0F, 60.0F, 5.0F);
   public final BooleanSetting limitParticles = new BooleanSetting("Лимит частиц", true);
   public final SliderSetting particleDistance = new SliderSetting("Дистанция частиц", 32.0F, 8.0F, 128.0F, 4.0F);
   public final SliderSetting particleCap = new SliderSetting("Потолок частиц", 1500.0F, 100.0F, 5000.0F, 100.0F);
   public final BooleanSetting simpleLeaves = new BooleanSetting("Упрощённая листва", true);

   private int tickCounter;
   private int currentParticleCount;
   private int originalViewDistance = -1;
   private boolean settingsCaptured;
   private boolean originalAo = true;
   private int originalMaxFps = -1;
   private boolean wasUnfocused;
   private Boolean originalRawMouse;
   private Boolean originalVsync;
   private GraphicsMode originalGraphicsMode;
   private CloudRenderMode originalCloudMode;
   private ParticlesMode originalParticlesMode;
   private ChunkBuilderMode originalChunkBuilderMode;
   private int originalCloudDistance = -1;
   private int originalBiomeBlend = -1;
   private double originalEntityDistance = -1.0D;
   private double originalChunkFade = -1.0D;

   public Optimization() {
      INSTANCE = this;
      this.collectSettings();
   }

   @Override
   public void onEnable() {
      super.onEnable();
      INSTANCE = this;
      this.settingsCaptured = this.captureMinecraftSettings();
      if (this.settingsCaptured) {
         this.applyLowLatencySettings();
         this.applyMinecraftSettings();
         this.reloadWorldRenderer();
      }
   }

   @Override
   public void onDisable() {
      INSTANCE = null;
      super.onDisable();
      this.restoreMinecraftSettings();
      if (this.settingsCaptured && c.options != null) {
         try {
            if (this.originalMaxFps > 0) {
               c.options.getMaxFps().setValue(this.originalMaxFps);
            }
            if (this.originalRawMouse != null) {
               c.options.getRawMouseInput().setValue(this.originalRawMouse);
            }
            if (this.originalVsync != null) {
               c.options.getEnableVsync().setValue(this.originalVsync);
            }
         } catch (Throwable ignored) {
         }
      }

      this.originalViewDistance = -1;
      this.originalMaxFps = -1;
      this.originalRawMouse = null;
      this.originalVsync = null;
      this.originalGraphicsMode = null;
      this.originalCloudMode = null;
      this.originalParticlesMode = null;
      this.originalChunkBuilderMode = null;
      this.originalCloudDistance = -1;
      this.originalBiomeBlend = -1;
      this.originalEntityDistance = -1.0D;
      this.originalChunkFade = -1.0D;
      this.settingsCaptured = false;
      this.wasUnfocused = false;
      this.reloadWorldRenderer();
   }

   @EventHandler
   public void onTick(ClientTickEvent event) {
      if (!this.settingsCaptured && c.options != null) {
         this.settingsCaptured = this.captureMinecraftSettings();
         if (this.settingsCaptured) {
            this.applyLowLatencySettings();
            this.applyMinecraftSettings();
            this.reloadWorldRenderer();
         }
      }
      if (c.player == null || c.world == null) {
         return;
      }

      this.tickCounter++;
      this.currentParticleCount = 0;
      if (this.tickCounter % 20 == 0) {
         this.applyMinecraftSettings();
      }

      if (this.dynamicFps.get() && c.options != null) {
         try {
            if (!c.isWindowFocused()) {
               if (!this.wasUnfocused) {
                  c.options.getMaxFps().setValue(this.bgFps.roundedInt());
                  this.wasUnfocused = true;
               }
            } else if (this.wasUnfocused) {
               c.options.getMaxFps().setValue(this.fpsLimit.roundedInt());
               this.wasUnfocused = false;
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private boolean captureMinecraftSettings() {
      if (c.options == null) {
         return false;
      }
      try {
         this.originalViewDistance = c.options.getViewDistance().getValue();
         this.originalAo = c.options.getAo().getValue();
         this.originalMaxFps = c.options.getMaxFps().getValue();
         this.originalGraphicsMode = c.options.getPreset().getValue();
         this.originalCloudMode = c.options.getCloudRenderMode().getValue();
         this.originalCloudDistance = c.options.getCloudRenderDistance().getValue();
         this.originalParticlesMode = c.options.getParticles().getValue();
         this.originalEntityDistance = c.options.getEntityDistanceScaling().getValue();
         this.originalBiomeBlend = c.options.getBiomeBlendRadius().getValue();
         this.originalChunkFade = c.options.getChunkFade().getValue();
         this.originalChunkBuilderMode = c.options.getChunkBuilderMode().getValue();
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private void applyLowLatencySettings() {
      if (c.options == null) {
         return;
      }
      try {
         this.originalRawMouse = c.options.getRawMouseInput().getValue();
         this.originalVsync = c.options.getEnableVsync().getValue();
         c.options.getRawMouseInput().setValue(true);
         c.options.getEnableVsync().setValue(false);
         c.options.getMaxFps().setValue(this.fpsLimit.roundedInt());
      } catch (Throwable ignored) {
      }
   }

   private void applyMinecraftSettings() {
      if (c.options == null) {
         return;
      }
      if (!this.minecraftSettings.get()) {
         this.restoreMinecraftSettings();
         return;
      }

      try {
         int targetDistance = this.chunkDistance.roundedInt();
         if (c.options.getViewDistance().getValue() != targetDistance) {
            c.options.getViewDistance().setValue(targetDistance);
         }

         GraphicsMode targetGraphics = this.fastGraphics.get() ? GraphicsMode.FAST : this.originalGraphicsMode;
         if (targetGraphics != null && c.options.getPreset().getValue() != targetGraphics) {
            c.options.applyGraphicsMode(targetGraphics);
         }
         boolean targetAo = this.fastGraphics.get() ? false : this.originalAo;
         if (c.options.getAo().getValue() != targetAo) {
            c.options.getAo().setValue(targetAo);
         }

         if (this.fpsBoostPreset.get()) {
            this.setIfChanged(c.options.getCloudRenderMode(), CloudRenderMode.OFF);
            this.setIfChanged(c.options.getCloudRenderDistance(), 0);
            this.setIfChanged(c.options.getParticles(), ParticlesMode.MINIMAL);
            this.setIfChanged(c.options.getEntityDistanceScaling(), 0.5D);
            this.setIfChanged(c.options.getBiomeBlendRadius(), 0);
            this.setIfChanged(c.options.getChunkFade(), 0.0D);
         } else {
            this.restoreVisualPerformanceOptions();
         }

         ChunkBuilderMode targetBuilder = this.multithreadedChunks.get()
            ? ChunkBuilderMode.NEARBY
            : this.originalChunkBuilderMode;
         if (targetBuilder != null) {
            this.setIfChanged(c.options.getChunkBuilderMode(), targetBuilder);
         }
      } catch (Throwable ignored) {
      }
   }

   private void restoreMinecraftSettings() {
      if (!this.settingsCaptured || c.options == null) {
         return;
      }
      try {
         if (this.originalViewDistance > 0) {
            this.setIfChanged(c.options.getViewDistance(), this.originalViewDistance);
         }
         if (this.originalGraphicsMode != null && c.options.getPreset().getValue() != this.originalGraphicsMode) {
            c.options.applyGraphicsMode(this.originalGraphicsMode);
         }
         this.setIfChanged(c.options.getAo(), this.originalAo);
         this.restoreVisualPerformanceOptions();
         if (this.originalChunkBuilderMode != null) {
            this.setIfChanged(c.options.getChunkBuilderMode(), this.originalChunkBuilderMode);
         }
      } catch (Throwable ignored) {
      }
   }

   private void restoreVisualPerformanceOptions() {
      if (c.options == null) {
         return;
      }
      try {
         if (this.originalCloudMode != null) {
            this.setIfChanged(c.options.getCloudRenderMode(), this.originalCloudMode);
         }
         if (this.originalCloudDistance >= 0) {
            this.setIfChanged(c.options.getCloudRenderDistance(), this.originalCloudDistance);
         }
         if (this.originalParticlesMode != null) {
            this.setIfChanged(c.options.getParticles(), this.originalParticlesMode);
         }
         if (this.originalEntityDistance >= 0.0D) {
            this.setIfChanged(c.options.getEntityDistanceScaling(), this.originalEntityDistance);
         }
         if (this.originalBiomeBlend >= 0) {
            this.setIfChanged(c.options.getBiomeBlendRadius(), this.originalBiomeBlend);
         }
         if (this.originalChunkFade >= 0.0D) {
            this.setIfChanged(c.options.getChunkFade(), this.originalChunkFade);
         }
      } catch (Throwable ignored) {
      }
   }

   private <T> void setIfChanged(net.minecraft.client.option.SimpleOption<T> option, T value) {
      if (option.getValue() != value && !option.getValue().equals(value)) {
         option.setValue(value);
      }
   }

   private void reloadWorldRenderer() {
      if (c.worldRenderer != null) {
         try {
            c.worldRenderer.reload();
         } catch (Throwable ignored) {
         }
      }
   }

   public static boolean shouldSpawnParticle(double x, double y, double z) {
      Optimization opt = INSTANCE;
      if (opt != null && opt.k() && opt.limitParticles.get()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player == null) {
            return true;
         }

         double dx = client.player.getX() - x;
         double dy = client.player.getY() - y;
         double dz = client.player.getZ() - z;
         double max = opt.particleDistance.get();
         if (dx * dx + dy * dy + dz * dz > max * max) {
            return false;
         }

         if (opt.currentParticleCount >= opt.particleCap.roundedInt()) {
            return false;
         }

         opt.currentParticleCount++;
         return true;
      }
      return true;
   }

   public static int getTargetFps(int defaultFps) {
      Optimization opt = INSTANCE;
      if (opt != null && opt.k() && opt.dynamicFps.get()) {
         MinecraftClient client = MinecraftClient.getInstance();
         return !client.isWindowFocused() ? opt.bgFps.roundedInt() : defaultFps;
      }
      return defaultFps;
   }
}
