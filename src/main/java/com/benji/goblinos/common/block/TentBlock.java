package com.benji.goblinos.common.block;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import com.benji.goblinos.Goblinos;

import com.benji.goblinos.common.block.entity.CleanGeoBlockEntity;
import com.benji.goblinos.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

@Mod.EventBusSubscriber(modid = Goblinos.MODID)
public final class TentBlock extends BaseEntityBlock {
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);

    public static final BooleanProperty COLLAPSED = BooleanProperty.create("collapsed");

    public TentBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ROTATION, 0).setValue(COLLAPSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, COLLAPSED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleanGeoBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        int rotation = Mth.floor(ctx.getRotation() / 45.0F + 0.5F) & 7;
        Level level = ctx.getLevel();
        for (var entry : TentFootprint.cells(rotation).entrySet()) {
            BlockPos pos = ctx.getClickedPos().offset(entry.getKey());
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
            if (!entry.getKey().equals(BlockPos.ZERO) && !level.isEmptyBlock(pos)) return null;
            if (!level.isUnobstructed(null, entry.getValue().move(pos.getX(), pos.getY(), pos.getZ()))) return null;
        }
        return defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level.isClientSide || old.is(this)) return;
        for (BlockPos offset : TentFootprint.cells(state.getValue(ROTATION)).keySet()) {
            if (offset.equals(BlockPos.ZERO)) continue;
            BlockPos cell = pos.offset(offset);
            if (level.isEmptyBlock(cell)) {
                level.setBlock(cell, ModBlocks.TENT_PART.get().defaultBlockState().setValue(ROTATION, state.getValue(ROTATION)).setValue(TentPartBlock.PART, TentFootprint.index(offset)), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !next.is(this)) {
            for (BlockPos offset : TentFootprint.cells(state.getValue(ROTATION)).keySet()) {
                BlockPos cell = pos.offset(offset);
                BlockState part = level.getBlockState(cell);
                if (part.is(ModBlocks.TENT_PART.get()) && part.getValue(TentPartBlock.PART) == TentFootprint.index(offset)) {
                    level.removeBlock(cell, false);
                }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(COLLAPSED) ? Shapes.empty() : Shapes.block();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TentBlock || state.getBlock() instanceof TentPartBlock)) return;
        event.setCanceled(true);
        Player player = event.getEntity();
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || player.isSpectator() || !player.getAbilities().mayBuild || !level.mayInteract(player, pos) || !player.canReach(pos, 1.0D))
            return;
        if (state.getBlock() instanceof TentPartBlock) {
            pos = pos.subtract(TentFootprint.offset(state.getValue(TentPartBlock.PART)));
        }
        if (level.getBlockEntity(pos) instanceof CleanGeoBlockEntity tent && level.mayInteract(player, pos)) {
            tent.hit(level);
        }
    }

    public static void collapse(ServerLevel level, BlockPos anchor, BlockState state) {
        int rotation = state.getValue(ROTATION);
        for (BlockPos offset : TentFootprint.cells(rotation).keySet()) {
            BlockPos pos = anchor.offset(offset);
            BlockState cell = level.getBlockState(pos);
            if (offset.equals(BlockPos.ZERO) || (cell.is(ModBlocks.TENT_PART.get()) && cell.getValue(TentPartBlock.PART) == TentFootprint.index(offset) && cell.getValue(ROTATION) == rotation)) {
                level.setBlock(pos, cell.setValue(COLLAPSED, true), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(COLLAPSED)) level.removeBlock(pos, false);
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
