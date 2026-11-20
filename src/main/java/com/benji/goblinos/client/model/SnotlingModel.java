package com.benji.goblinos.client.model;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.entity.SnotlingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;

public class SnotlingModel extends GeoModel<SnotlingEntity> {
    @Override
    public ResourceLocation getModelResource(SnotlingEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "geo/snotling.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SnotlingEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "textures/entity/snotling.png");
    }

    @Override
    public void setCustomAnimations(SnotlingEntity entity, long instanceId, AnimationState<SnotlingEntity> state) {
        var headLook = getAnimationProcessor().getBone("head_look");
        if (headLook == null) return;

        var look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float yaw = Mth.clamp(look.netHeadYaw(), -60.0F, 60.0F);
        float pitch = Mth.clamp(look.headPitch(), -45.0F, 45.0F);

        headLook.setRotY(yaw * Mth.DEG_TO_RAD);
        headLook.setRotX(pitch * Mth.DEG_TO_RAD);
    }

    @Override
    public ResourceLocation getAnimationResource(SnotlingEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "animations/snotling.animation.json");
    }
}