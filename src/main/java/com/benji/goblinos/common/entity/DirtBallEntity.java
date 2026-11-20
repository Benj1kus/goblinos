package com.benji.goblinos.common.entity;

import com.benji.goblinos.network.DirtNetwork;
import com.benji.goblinos.registry.ModEntities;
import com.benji.goblinos.registry.ModItems;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

public class DirtBallEntity extends ThrowableItemProjectile {
    public DirtBallEntity(EntityType<? extends DirtBallEntity> type, Level level) {
        super(type, level);
    }

    public DirtBallEntity(SnotlingEntity owner) {
        super(ModEntities.DIRT_BALL.get(), owner, owner.level());
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DIRT_BALL.get();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer player) {
            boolean damaged = player.hurt(damageSources().thrown(this, getOwner()), 3.0F);

            if (damaged) {
                var velocity = getDeltaMovement();
                if (velocity.horizontalDistanceSqr() > 1.0E-6D) {
                    player.knockback(0.45D, -velocity.x, -velocity.z);
                    player.hurtMarked = true;
                }
            }

            DirtNetwork.splash(player);
            finish(true);
        } else finish(false);
    }

    private void finish(boolean hit) {
        if (getOwner() instanceof SnotlingEntity owner) owner.dirtResult(getUUID(), hit);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved() && tickCount >= 80) finish(false);
    }
}
