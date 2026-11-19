package pulse.cosmetic;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import pulse.effects.SeeInvisibleCosmeticRenderer;
import pulse.modules.visuals.SeeInvisible;
import ru.pulse.cosmetic.render.CosmeticRenderer;
import pulse.render.system.ClientPipelines;

public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
   public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
      super(context);
   }

   @Override
   public void render(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light, PlayerEntityRenderState state, float limbAngle, float tickDelta) {
      MinecraftClient client = MinecraftClient.getInstance();
      AbstractClientPlayerEntity player = client.player;
      if (player == null || client.world == null || commandQueue == null || state == null || state.spectator) {
         return;
      }

      boolean self = state.id == player.getId();
      if (!self) {
         Entity renderedEntity = client.world.getEntityById(state.id);
         if (!(renderedEntity instanceof AbstractClientPlayerEntity target)
            || !SeeInvisible.shouldRenderInvisiblePlayer(target)) {
            return;
         }
         commandQueue.submitCustom(matrices, ClientPipelines.QUAD, (entry, vertexConsumer) -> {
            MatrixStack localMatrices = new MatrixStack();
            localMatrices.multiplyPositionMatrix(entry.getPositionMatrix());
            SeeInvisibleCosmeticRenderer.render(localMatrices, vertexConsumer, this.getContextModel());
         });
         return;
      }

      if (SeeInvisible.isSelfCosmeticEnabled()) {
         commandQueue.submitCustom(matrices, ClientPipelines.QUAD, (entry, vertexConsumer) -> {
            MatrixStack localMatrices = new MatrixStack();
            localMatrices.multiplyPositionMatrix(entry.getPositionMatrix());
            SeeInvisibleCosmeticRenderer.render(localMatrices, vertexConsumer, this.getContextModel());
         });
      }

      List<Integer> selected = LocalCosmetics.selectedIndices();
      if (selected.isEmpty()) {
         return;
      }

      for (Integer selectedIndex : selected) {
         if (selectedIndex == null || "cape".equals(LocalCosmetics.type(selectedIndex))) {
            continue;
         }

         ru.pulse.cosmetic.model.CosmeticModel model = LocalCosmetics.modelFor(selectedIndex);
         if (model == null || model.getTextureId() == null) {
            continue;
         }

         RenderLayer layer = RenderLayers.entityCutoutNoCull(model.getTextureId());
         commandQueue.submitCustom(matrices, layer, (entry, vertexConsumer) -> {
            MatrixStack localMatrices = new MatrixStack();
            localMatrices.multiplyPositionMatrix(entry.getPositionMatrix());
            CosmeticRenderer.getInstance().renderCosmetic(model, player, localMatrices, vertexConsumer, light, this.getContextModel(), tickDelta);
         });
      }
   }
}
