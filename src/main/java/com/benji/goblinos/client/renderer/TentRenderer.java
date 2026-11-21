package com.benji.goblinos.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.core.object.Color;

import com.benji.goblinos.client.model.TentModel;
import com.benji.goblinos.common.block.TentBlock;
import com.benji.goblinos.common.block.entity.CleanGeoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class TentRenderer extends GeoBlockRenderer<CleanGeoBlockEntity> {
    public TentRenderer(BlockEntityRendererProvider.Context context) {
        super(new TentModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack stack) {
        int rotation = getAnimatable().getBlockState().getValue(TentBlock.ROTATION);
        stack.mulPose(Axis.YP.rotationDegrees(-45.0F * rotation));
    }

    @Override
    public RenderType getRenderType(CleanGeoBlockEntity tent, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return tent.opacity(partialTick) < 1.0F ? RenderType.entityTranslucent(texture) : super.getRenderType(tent, texture, buffers, partialTick);
    }

    @Override
    public Color getRenderColor(CleanGeoBlockEntity tent, float partialTick, int packedLight) {
        return Color.ofRGBA(1F, 1F, 1F, tent.opacity(partialTick));
    }
}
