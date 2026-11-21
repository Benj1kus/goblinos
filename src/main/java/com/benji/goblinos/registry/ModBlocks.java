package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.benji.goblinos.common.block.TentBlock;
import com.benji.goblinos.common.block.TentPartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Goblinos.MODID);

    //    public static final RegistryObject<Block> SANDSTONE_FLOORB = BLOCKS.register("sandstone_floorb", () -> new Block(BlockBehaviour.Properties.copy(Blocks.SANDSTONE).strength(2.0F).requiresCorrectToolForDrops()));
    //    public static final RegistryObject<Block> WINDMILL = BLOCKS.register("windmill", () -> new WindmillBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(100.0F).requiresCorrectToolForDrops().noOcclusion()));

    private static BlockBehaviour.Properties tentProperties() {
        return BlockBehaviour.Properties.copy(Blocks.WHITE_WOOL).sound(SoundType.WOOL).strength(1.0F).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistryObject<Block> TENT = BLOCKS.register("tent", () -> new TentBlock(tentProperties()));

    public static final RegistryObject<Block> TENT_PART = BLOCKS.register("tent_part", () -> new TentPartBlock(tentProperties().noLootTable()));

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}