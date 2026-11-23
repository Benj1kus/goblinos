package com.benji.goblinos.common.entity;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.registry.NoiseParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Goblinos.MODID)
public final class SnotlingAwareness {
    private static final double HALF_CONE_COS = Math.cos(Math.toRadians(45));
    private static final double CLOSE_DETECTION_RANGE = 2.0D;
    private static final int HEARING_RADIUS = 15;
    private static final int SIGHT_RISE = 60;
    private static final int SIGHT_HOLD = 20;
    private static final int NOISE_MEMORY = 160;
    private static final int FRAME_TICKS = 3;

    private static class Suspicion {
        int sight, hold, sounds, peak = 1, frame = 1, nextFrame, lastSound = -1000;
        Vec3 source;

        boolean hear(Vec3 pos, int now) {
            if (lastSound == now) return false;
            lastSound = now;
            source = pos;
            if (sounds == 3 && (frame != 1 || peak != 1)) return true;
            sounds = Math.min(4, sounds + 1);
            peak = switch (sounds) {
                case 2 -> 4;
                case 3 -> 6;
                case 4 -> 7;
                default -> 1;
            };
            return true;
        }

        void update(boolean visible, int now) {
            if (visible && sight == SIGHT_RISE) hold++;
            else hold = 0;
            sight = Math.max(0, Math.min(SIGHT_RISE, sight + (visible ? 1 : -1)));
            if (now - lastSound > NOISE_MEMORY && sounds < 4) {
                sounds = 0;
                source = null;
                peak = 1;
            }
            if (now >= nextFrame) {
                if (frame < peak) frame++;
                else if (frame > peak) frame--;
                if (frame == peak && sounds < 4) peak = 1;
                nextFrame = now + FRAME_TICKS;
            }
        }

        int display() {
            return Math.max(sight > 0 ? 1 + sight / 10 : 0, sounds > 0 ? frame : 0);
        }
    }

    private final Map<UUID, Suspicion> players = new HashMap<>();
    private int closeWarningUntil;
    Player detected;
    Vec3 source;

    private static boolean eligible(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    void tick(SnotlingEntity mob) {
        detected = null;
        source = null;
        if (mob.getTarget() != null || mob.isStunned() || !mob.isAlive() || mob.isNoAi()) {
            players.clear();
            mob.showWarning(mob.getTarget() != null && mob.isAlive() && !mob.isStunned() && mob.tickCount < closeWarningUntil ? 7 : 0);
            return;
        }
        boolean alreadyAlert = mob.warningFrame() > 0;
        int frame = 0;
        int latest = Integer.MIN_VALUE;
        double nearest = Double.MAX_VALUE;
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        Vec3 origin = mob.getEyePosition();
        Vec3 forward = Vec3.directionFromRotation(mob.getXRot(), mob.getYHeadRot());
        for (Player p : mob.level().players()) {
            if (!eligible(p)) continue;
            double distance = mob.distanceToSqr(p);
            boolean visible = distance <= range * range && !p.isInvisible() && p.getEyePosition().subtract(origin).normalize().dot(forward) >= HALF_CONE_COS && mob.hasLineOfSight(p);
            Suspicion state = players.get(p.getUUID());
            if (state == null) {
                if (!visible) continue;
                state = new Suspicion();
                players.put(p.getUUID(), state);
            }
            state.update(visible, mob.tickCount);
            if (alreadyAlert && visible && distance <= CLOSE_DETECTION_RANGE * CLOSE_DETECTION_RANGE) {
                state.sight = SIGHT_RISE;
                state.hold = SIGHT_HOLD;
                closeWarningUntil = mob.tickCount + 6;
            }
            frame = Math.max(frame, state.display());
            if (state.source != null && state.lastSound > latest) {
                latest = state.lastSound;
                source = state.source;
            }
            if ((state.hold >= SIGHT_HOLD || state.sounds == 4 && state.frame == 7) && distance <= range * range && distance < nearest) {
                nearest = distance;
                detected = p;
            }
        }
        players.entrySet().removeIf(e -> {
            Player p = mob.level().getPlayerByUUID(e.getKey());
            return p == null || !eligible(p) || e.getValue().display() == 0 || mob.tickCount - e.getValue().lastSound > NOISE_MEMORY && e.getValue().sounds == 4;
        });
        mob.showWarning(frame);
    }

    private static void noise(ServerPlayer player, Vec3 pos, int radius) {
        if (!eligible(player)) return;
        AABB area = new AABB(BlockPos.containing(pos)).inflate(radius);
        boolean heard = false;
        for (SnotlingEntity mob : player.level().getEntitiesOfClass(SnotlingEntity.class, area, m -> m.isAlive() && !m.isNoAi() && !m.isStunned() && m.getTarget() == null && m.position().distanceToSqr(pos) <= radius * radius)) {
            heard |= mob.awareness.players.computeIfAbsent(player.getUUID(), id -> new Suspicion()).hear(pos, mob.tickCount);
        }
        if (heard) {
            player.serverLevel().sendParticles(NoiseParticles.WAVE, pos.x, pos.y + 0.65D, pos.z, 1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGameEvent(VanillaGameEvent event) {
        var cause = event.getCause();
        if (cause instanceof Projectile projectile) cause = projectile.getOwner();
        if (!(cause instanceof ServerPlayer player)) return;
        GameEvent type = event.getVanillaEvent();
        if (type == GameEvent.STEP) {
            if (!player.isSteppingCarefully()) noise(player, event.getEventPosition(), player.isSprinting() ? 12 : 6);
        } else if (type == GameEvent.BLOCK_PLACE || type == GameEvent.BLOCK_DESTROY || type == GameEvent.BLOCK_CHANGE || type == GameEvent.BLOCK_OPEN || type == GameEvent.BLOCK_CLOSE || type == GameEvent.BLOCK_ACTIVATE || type == GameEvent.BLOCK_DEACTIVATE || type == GameEvent.CONTAINER_OPEN || type == GameEvent.CONTAINER_CLOSE || type == GameEvent.PROJECTILE_SHOOT) {
            noise(player, event.getEventPosition(), HEARING_RADIUS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player)
            noise(player, Vec3.atCenterOf(event.getPos()), HEARING_RADIUS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            noise(player, Vec3.atCenterOf(event.getPos()), HEARING_RADIUS);
    }

    @SubscribeEvent
    public static void onOpenMenu(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) noise(player, player.position(), HEARING_RADIUS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockHit(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)
            noise(player, Vec3.atCenterOf(event.getPos()), HEARING_RADIUS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getAmount() > 0 && event.getSource().getEntity() instanceof ServerPlayer player)
            noise(player, event.getEntity().position(), HEARING_RADIUS);
    }
}
