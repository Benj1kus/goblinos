package com.benji.goblinos.client.model;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.block.entity.CleanGeoBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TentModel extends GeoModel<CleanGeoBlockEntity> {
    @Override
    public ResourceLocation getModelResource(CleanGeoBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "geo/tent.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CleanGeoBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "textures/block/tent.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CleanGeoBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "animations/tent.animation.json");
    }
}