package com.benji.goblinos.client.model;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.entity.SnotlingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

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
    public ResourceLocation getAnimationResource(SnotlingEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "animations/snotling.animation.json");
    }
}