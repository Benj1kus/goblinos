package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Goblinos.MODID);

    // public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> EXAMPLE_BE =
    //         BLOCK_ENTITIES.register("example", () -> BlockEntityType.Builder.of(ExampleBlockEntity::new, ModBlocks.EXAMPLE.get()).build(null));

    // public static final RegistryObject<BlockEntityType<ExampleSignBlockEntity>> EXAMPLE_SIGN_BE =
    //         BLOCK_ENTITIES.register("example_sign", () -> BlockEntityType.Builder.of(ExampleSignBlockEntity::new, ModBlocks.EXAMPLE_SIGN.get(), ModBlocks.EXAMPLE_WALL_SIGN.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}