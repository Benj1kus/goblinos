package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}