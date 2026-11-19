package ru.pulse.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pulse.modules.visuals.SeeInvisible;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {
   @Shadow
   protected M model;
   @Unique private static boolean fendel$softHighlight;

   protected LivingEntityRendererMixin() {
      super((Context)null);
   }

   @Inject(require = 0, method = "updateRenderState", at = @At("TAIL"))
   private void revealHolyWorldInvisiblePlayers(T entity, S state, float tickDelta, CallbackInfo callbackInfo) {
      if (entity instanceof net.minecraft.entity.player.PlayerEntity player && SeeInvisible.shouldRenderInvisiblePlayer(player)) {
         state.invisible = false;
         state.invisibleToPlayer = false;
         state.outlineColor = SeeInvisible.OUTLINE_MARKER;
         state.displayName = null;
      }
   }

   @Inject(require = 0, method = "render", at = @At("HEAD"), cancellable = true)
   private void onRenderEntity(S s, MatrixStack MatrixStackVar, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraRenderState, CallbackInfo callbackInfo) {
      fendel$softHighlight = s.outlineColor == SeeInvisible.OUTLINE_MARKER;
      if (fendel$softHighlight) {
         s.outlineColor = 0;
      }
   }

   @ModifyArg(
      require = 1,
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
      ),
      index = 6
   )
   private int fendel$setSeeInvisibleOpacity(int color) {
      return fendel$softHighlight ? (color & 0x00FFFFFF) | 0x1A000000 : color;
   }

   private static Text getServerDisplayName(MinecraftClient MinecraftClientVar, String str) {
      PlayerListEntry PlayerListEntryVarGetPlayerListEntry;
      return (Text)(MinecraftClientVar.getNetworkHandler() != null
            && MinecraftClientVar.player != null
            && (PlayerListEntryVarGetPlayerListEntry = MinecraftClientVar.getNetworkHandler().getPlayerListEntry(MinecraftClientVar.player.getUuid())) != null
            && PlayerListEntryVarGetPlayerListEntry.getDisplayName() != null
         ? PlayerListEntryVarGetPlayerListEntry.getDisplayName()
         : Text.literal(str));
   }
}
