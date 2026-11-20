package com.benji.goblinos.client.renderer;

import com.benji.goblinos.client.model.SnotlingModel;
import com.benji.goblinos.common.entity.SnotlingEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SnotlingRenderer extends GeoEntityRenderer<SnotlingEntity> {
    public SnotlingRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SnotlingModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    public boolean shouldRender(SnotlingEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }
}