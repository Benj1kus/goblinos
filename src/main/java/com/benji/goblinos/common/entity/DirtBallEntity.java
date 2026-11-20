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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class DirtBallEntity extends ThrowableItemProjectile {
    public DirtBallEntity(EntityType<? extends DirtBallEntity> type, Level level) {
        super(type, level);
    }

    public DirtBallEntity(LivingEntity owner) {
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

        if (!(result instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            finish(false);
            return;
        }

        var owner = getOwner();
        boolean hitPlayer = target instanceof ServerPlayer;

        if (hitPlayer || owner instanceof Player) {
            if (target.hurt(damageSources().thrown(this, owner), 3.0F)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), owner);

                var velocity = getDeltaMovement();
                if (velocity.horizontalDistanceSqr() > 1.0E-6D) {
                    target.knockback(0.45D, -velocity.x, -velocity.z);
                    target.hurtMarked = true;
                }
            }
        }

        if (target instanceof ServerPlayer player) {
            DirtNetwork.splash(player);
        }

        finish(hitPlayer);
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
