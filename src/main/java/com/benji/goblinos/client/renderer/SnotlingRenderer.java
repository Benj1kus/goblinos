package com.benji.goblinos.client.renderer;

import com.benji.goblinos.client.model.SnotlingModel;
import com.benji.goblinos.common.entity.SnotlingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SnotlingRenderer extends GeoEntityRenderer<SnotlingEntity> {
    private static final ResourceLocation[] TALKING_FRAMES = {ResourceLocation.fromNamespaceAndPath("goblinos", "textures/particle/talking1.png"), ResourceLocation.fromNamespaceAndPath("goblinos", "textures/particle/talking2.png"), ResourceLocation.fromNamespaceAndPath("goblinos", "textures/particle/talking3.png"), ResourceLocation.fromNamespaceAndPath("goblinos", "textures/particle/talking4.png")};
    private static final ResourceLocation[] WARN_FRAMES = new ResourceLocation[7];

    static {
        for (int i = 0; i < WARN_FRAMES.length; i++) {
            WARN_FRAMES[i] = ResourceLocation.fromNamespaceAndPath("goblinos", "textures/particle/warn" + (i + 1) + ".png");
        }
    }

    private static final int FRAME_TICKS = 10;
    private static final float ICON_WIDTH = 0.5625F;
    private static final float ICON_HEIGHT = ICON_WIDTH * 7.0F / 9.0F;
    private static final float HEAD_GAP = 0.25F;

    public SnotlingRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SnotlingModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(SnotlingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        int warning = entity.warningFrame();
        if ((!entity.isTalking() && warning == 0) || !entity.isAlive() || entity.isInvisible()) return;

        int frame = (int) ((entity.level().getGameTime() / FRAME_TICKS) % TALKING_FRAMES.length);
        ResourceLocation sprite = warning > 0 ? WARN_FRAMES[warning - 1] : TALKING_FRAMES[frame];
        float width = warning > 0 ? ICON_HEIGHT * 4 / 7 : ICON_WIDTH;
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + HEAD_GAP, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(sprite));
        int alpha = warning > 0 ? 153 : 255;
        float halfWidth = width * 0.5F;
        vertex(buffer, pose, normal, -halfWidth, 0, 0, 1, alpha);
        vertex(buffer, pose, normal, halfWidth, 0, 1, 1, alpha);
        vertex(buffer, pose, normal, halfWidth, ICON_HEIGHT, 1, 0, alpha);
        vertex(buffer, pose, normal, -halfWidth, ICON_HEIGHT, 0, 0, alpha);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v, int alpha) {
        buffer.vertex(pose, x, y, 0.0F).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }

    @Override
    public boolean shouldRender(SnotlingEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }
}
