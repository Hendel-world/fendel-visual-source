package pulse.modules.visuals;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import pulse.client.MinecraftContext;
import pulse.module.ClientModule;
import pulse.module.ModuleCategory;
import pulse.module.ModuleInfo;
import pulse.module.ModuleRegistry;

@ModuleInfo(a = "See Invisible", b = "Мягкая подсветка невидимых игроков", c = ModuleCategory.VISUALS)
public final class SeeInvisible extends ClientModule {
   public static final int OUTLINE_MARKER = 0x01FFFFFF;

   public static boolean isEnabledForWorld() {
      return MinecraftContext.c != null
         && MinecraftContext.c.player != null
         && !MinecraftContext.c.player.isSpectator()
         && ModuleRegistry.SEE_INVISIBLE != null
         && ModuleRegistry.SEE_INVISIBLE.isEnabled();
   }

   public static boolean isSelfCosmeticEnabled() {
      return isEnabledForWorld();
   }

   public static boolean shouldRenderInvisiblePlayer(PlayerEntity player) {
      return player instanceof AbstractClientPlayerEntity
         && isEnabledForWorld()
         && player != MinecraftContext.c.player
         && player.isInvisible();
   }
}
