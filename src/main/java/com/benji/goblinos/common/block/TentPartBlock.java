package com.benji.goblinos.common.block;

import net.minecraft.world.level.material.FluidState;

import com.benji.goblinos.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TentPartBlock extends Block {
    static final IntegerProperty PART = IntegerProperty.create("part", 0, 49);

    public TentPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TentBlock.COLLAPSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TentBlock.ROTATION, PART, TentBlock.COLLAPSED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(TentBlock.COLLAPSED)) return Shapes.empty();
        return TentFootprint.cells(state.getValue(TentBlock.ROTATION)).getOrDefault(TentFootprint.offset(state.getValue(PART)), Shapes.empty());
    }

    private void removeTent(Level level, BlockPos pos, BlockState part, boolean drop) {
        BlockPos anchor = pos.subtract(TentFootprint.offset(part.getValue(PART)));
        BlockState tent = level.getBlockState(anchor);
        if (tent.is(ModBlocks.TENT.get()) && tent.getValue(TentBlock.ROTATION).equals(part.getValue(TentBlock.ROTATION))) {
            level.destroyBlock(anchor, drop);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !next.is(this)) removeTent(level, pos, state, true);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return false;
    }
}
