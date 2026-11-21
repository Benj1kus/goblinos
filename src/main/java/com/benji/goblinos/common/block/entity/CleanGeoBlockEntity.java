package com.benji.goblinos.common.block.entity;

import com.benji.goblinos.common.block.TentBlock;
import com.benji.goblinos.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;

import com.benji.goblinos.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class CleanGeoBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay("hit");
    private static final RawAnimation HIT2 = RawAnimation.begin().thenPlay("hit2");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final int MAX_HITS = 5;
    private static final int FADE_DELAY = 60, FADE_TICKS = 20;
    private int hits, renderedHits = -1;
    private long hitAt;
    private boolean secondHit;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CleanGeoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEAN_GEO_BE.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tent", 0, state -> {
            if (hits != renderedHits) {
                state.getController().forceAnimationReset();
                renderedHits = hits;
            }
            if (hits >= MAX_HITS) return state.setAndContinue(DEATH);
            if (hits > 0 && level != null && level.getGameTime() - hitAt < (secondHit ? 9 : 7)) {
                return state.setAndContinue(secondHit ? HIT2 : HIT);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.0D, 3.0D, 3.0D);
    }

    public void hit(ServerLevel world) {
        if (hits >= MAX_HITS) return;
        hits++;
        hitAt = world.getGameTime();
        secondHit = world.random.nextBoolean();
        setChanged();
        world.playSound(null, worldPosition, SoundEvents.BAMBOO_WOOD_BREAK, SoundSource.BLOCKS, 0.85F, 0.8F + world.random.nextFloat() * 0.35F);
        if (hits == MAX_HITS) {
            TentBlock.collapse(world, worldPosition, getBlockState());
            world.scheduleTick(worldPosition, getBlockState().getBlock(), FADE_DELAY + FADE_TICKS);
            world.playSound(null, worldPosition, SoundEvents.BAMBOO_WOOD_DOOR_CLOSE, SoundSource.BLOCKS, 1F, 0.8F);
            for (int i = 0; i < 8; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2.0;
                double dx = Math.cos(angle), dz = Math.sin(angle);
                double radius = 0.5 + world.random.nextDouble() * 0.8;
                world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, worldPosition.getX() + 0.5 + dx * radius, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5 + dz * radius, 0, dx, 0.12, dz, 0.055);
            }
            var reward = switch (world.random.nextInt(3)) {
                case 0 -> ModItems.BOOTS.get();
                case 1 -> ModItems.DIRT_WORM.get();
                default -> ModItems.TENT_FABRIC.get();
            };
            Block.popResource(world, worldPosition, new ItemStack(reward, 1 + world.random.nextInt(3)));
        }
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public float opacity(float partialTick) {
        if (hits < MAX_HITS || level == null) return 1.0F;
        float elapsed = (level.getGameTime() - hitAt) + partialTick;
        return 1.0F - Mth.clamp((elapsed - FADE_DELAY) / FADE_TICKS, 0.0F, 1.0F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TentHits", hits);
        tag.putLong("TentHitAt", hitAt);
        tag.putBoolean("TentSecondHit", secondHit);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hits = tag.getInt("TentHits");
        hitAt = tag.getLong("TentHitAt");
        secondHit = tag.getBoolean("TentSecondHit");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
