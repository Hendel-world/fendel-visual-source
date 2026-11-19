package pulse.effects;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Local-only cosmetic geometry for See Invisible, submitted through Minecraft's queued consumer. */
public final class SeeInvisibleCosmeticRenderer {
   private static final float TAU = (float)(Math.PI * 2.0);
   private static final int STRAW = 0xFFE2B86B;
   private static final int STRAW_LIGHT = 0xFFF3D590;
   private static final int STRAW_DARK = 0xFFC99449;

   private SeeInvisibleCosmeticRenderer() {
   }

   public static void render(MatrixStack matrices, VertexConsumer consumer, PlayerEntityModel playerModel) {
      Matrix4f basePose = matrices.peek().getPositionMatrix();

      MatrixStack feet = new MatrixStack();
      feet.multiplyPositionMatrix(basePose);
      feet.translate(0.0F, 1.40F, 0.0F);
      Matrix4f feetPose = feet.peek().getPositionMatrix();
      drawGroundMark(consumer, feetPose);
      drawChainLinks(consumer, feetPose);

      MatrixStack head = new MatrixStack();
      head.multiplyPositionMatrix(basePose);
      playerModel.head.applyTransform(head);
      head.translate(0.0F, -0.015F, 0.0F);
      drawChinaHat(consumer, head.peek().getPositionMatrix());
   }

   private static void drawGroundMark(VertexConsumer consumer, Matrix4f pose) {
      float[][] square = {{-0.56F, 0.0F, -0.56F}, {0.56F, 0.0F, -0.56F}, {0.56F, 0.0F, 0.56F}, {-0.56F, 0.0F, 0.56F}};
      quad(consumer, pose, square[0], square[1], square[2], square[3], 0x8C5126C9);
      drawHorizontalBand(consumer, pose, 0.64F, 0.575F, 0.008F, 64, 0xEA7137ED);
      drawHorizontalBand(consumer, pose, 0.40F, 0.37F, 0.014F, 48, 0xD89053FA);
   }

   private static void drawChinaHat(VertexConsumer consumer, Matrix4f pose) {
      final int sides = 24;
      final float brimY = -0.50F;
      // Broad, layered brim and a short, thick crown give the traditional Chinese straw-hat silhouette.
      drawHorizontalBand(consumer, pose, 0.61F, 0.265F, brimY, sides, STRAW_LIGHT);
      drawHorizontalBand(consumer, pose, 0.61F, 0.265F, brimY - 0.035F, sides, STRAW_DARK);
      for (int i = 0; i < sides; i++) {
         float a0 = TAU * i / sides;
         float a1 = TAU * (i + 1) / sides;
         quad(consumer, pose,
            point(0.61F, brimY, a0), point(0.61F, brimY, a1),
            point(0.61F, brimY - 0.035F, a1), point(0.61F, brimY - 0.035F, a0), STRAW_DARK);
      }

      for (int band = 0; band < 6; band++) {
         float y0 = brimY - 0.015F - band * 0.055F;
         float y1 = y0 - 0.055F;
         float r0 = 0.265F * (1.0F - band * 0.145F);
         float r1 = 0.265F * (1.0F - (band + 1) * 0.145F);
         for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            int color = (i & 1) == 0 ? STRAW : STRAW_DARK;
            quad(consumer, pose,
               point(r0, y0, a0), point(r0, y0, a1), point(r1, y1, a1), point(r1, y1, a0), color);
         }
      }
   }

   private static void drawChainLinks(VertexConsumer consumer, Matrix4f pose) {
      long now = System.nanoTime();
      float phase = (now % 6_000_000_000L) / 6_000_000_000.0F * TAU;
      final int links = 18;
      for (int link = 0; link < links; link++) {
         float angle = TAU * link / links + phase;
         float cx = 0.82F * (float)Math.cos(angle);
         float cz = 0.82F * (float)Math.sin(angle);
         float tangentX = -(float)Math.sin(angle);
         float tangentZ = (float)Math.cos(angle);
         float radialX = (float)Math.cos(angle);
         float radialZ = (float)Math.sin(angle);
         boolean alternate = (link & 1) != 0;
         float ux = alternate ? radialX : tangentX;
         float uz = alternate ? radialZ : tangentZ;
         float wx = alternate ? -tangentX : radialX;
         float wz = alternate ? -tangentZ : radialZ;
         int color = alternate ? 0xE98157E5 : 0xE85C33BC;
         drawLink(consumer, pose, cx, 0.024F, cz, ux, uz, wx, wz, alternate, color);
      }
   }
   private static void drawLink(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz,
                                float ux, float uz, float wx, float wz, boolean alternate, int color) {
      final int around = 10;
      final int tube = 4;
      final float majorU = alternate ? 0.11F : 0.125F;
      final float majorV = 0.038F;
      final float radius = 0.012F;
      for (int i = 0; i < around; i++) {
         float a0 = TAU * i / around;
         float a1 = TAU * (i + 1) / around;
         for (int j = 0; j < tube; j++) {
            float b0 = TAU * j / tube;
            float b1 = TAU * (j + 1) / tube;
            quad(consumer, pose,
               linkPoint(cx, cy, cz, ux, uz, wx, wz, majorU, majorV, radius, a0, b0),
               linkPoint(cx, cy, cz, ux, uz, wx, wz, majorU, majorV, radius, a1, b0),
               linkPoint(cx, cy, cz, ux, uz, wx, wz, majorU, majorV, radius, a1, b1),
               linkPoint(cx, cy, cz, ux, uz, wx, wz, majorU, majorV, radius, a0, b1), color);
         }
      }
   }

   private static float[] linkPoint(float cx, float cy, float cz, float ux, float uz, float wx, float wz,
                                   float majorU, float majorV, float radius, float a, float b) {
      float ca = (float)Math.cos(a);
      float sa = (float)Math.sin(a);
      float cb = (float)Math.cos(b);
      float sb = (float)Math.sin(b);
      float alongU = majorU * ca + radius * cb * ca;
      float alongV = majorV * sa + radius * cb * sa;
      float across = radius * sb;
      return new float[]{cx + ux * alongU + wx * across, cy + alongV, cz + uz * alongU + wz * across};
   }

   private static void drawHorizontalBand(VertexConsumer consumer, Matrix4f pose, float outer, float inner,
                                          float y, int segments, int color) {
      for (int i = 0; i < segments; i++) {
         float a0 = TAU * i / segments;
         float a1 = TAU * (i + 1) / segments;
         quad(consumer, pose,
            point(outer, y, a0), point(outer, y, a1), point(inner, y, a1), point(inner, y, a0), color);
      }
   }

   private static float[] point(float radius, float y, float angle) {
      return new float[]{radius * (float)Math.cos(angle), y, radius * (float)Math.sin(angle)};
   }

   private static void quad(VertexConsumer consumer, Matrix4f pose,
                            float[] a, float[] b, float[] c, float[] d, int color) {
      float red = (color >> 16 & 255) / 255.0F;
      float green = (color >> 8 & 255) / 255.0F;
      float blue = (color & 255) / 255.0F;
      float alpha = (color >>> 24) / 255.0F;
      vertex(consumer, pose, a, red, green, blue, alpha);
      vertex(consumer, pose, b, red, green, blue, alpha);
      vertex(consumer, pose, c, red, green, blue, alpha);
      vertex(consumer, pose, d, red, green, blue, alpha);
   }

   private static void vertex(VertexConsumer consumer, Matrix4f pose, float[] point,
                               float red, float green, float blue, float alpha) {
      consumer.vertex(pose, point[0], point[1], point[2]).color(red, green, blue, alpha);
   }
}
