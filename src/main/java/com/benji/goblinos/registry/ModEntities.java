package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.entity.SnotlingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.benji.goblinos.common.entity.DirtBallEntity;

import static com.benji.goblinos.Goblinos.MODID;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    // public static final RegistryObject<EntityType<HappinessEntity>> HAPPINESS = ENTITIES.register("happiness", () -> EntityType.Builder.of(HappinessEntity::new, MobCategory.CREATURE).sized(0.5f, 1.0625f).clientTrackingRange(8).build(ResourceLocation.fromNamespaceAndPath(MODID, "happiness").toString()));
    public static final RegistryObject<EntityType<SnotlingEntity>> SNOTLING = ENTITIES.register("snotling", () -> EntityType.Builder.of(SnotlingEntity::new, MobCategory.MONSTER).sized(0.625f, 1.125f).clientTrackingRange(8).build(ResourceLocation.fromNamespaceAndPath(MODID, "snotling").toString()));

    public static final RegistryObject<EntityType<DirtBallEntity>> DIRT_BALL = ENTITIES.register("dirt_ball", () -> EntityType.Builder.<DirtBallEntity>of(DirtBallEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(2).build(MODID + ":dirt_ball"));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
