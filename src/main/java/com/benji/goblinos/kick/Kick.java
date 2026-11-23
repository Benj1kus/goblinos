package com.benji.goblinos.kick;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.entity.SnotlingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Goblinos.MODID)
public final class Kick {
    public static final int FRAME_TICKS = 6, WINDOW_END = 8 * FRAME_TICKS, DONE_TICKS = 30;
    public static final int START = 1, DONE = 2, END = 0;
    private static final int IMPACT = 6;
    private static final Map<UUID, Session> active = new HashMap<>();

    private static class Session {
        final SnotlingEntity mob;
        final Vec3 anchor, direction;
        final float yaw;
        int phase = START;
        long since;
        boolean impacted;

        Session(ServerPlayer p, SnotlingEntity mob) {
            this.mob = mob;
            anchor = p.position();
            direction = mob.position().subtract(anchor).multiply(1, 0, 1).normalize();
            yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            since = p.level().getGameTime();
        }
    }

    public static boolean canStart(Player player, SnotlingEntity mob) {
        if (!player.isShiftKeyDown() || player.isSpectator() || !player.isAlive() || player.isPassenger() || !mob.isAlive() || !mob.onGround() || mob.isPassenger() || mob.kickHeld() || mob.isStunned() || mob.isClimbing() || mob.isInWaterOrBubble() || player.distanceToSqr(mob) > 2.6 * 2.6 || !player.hasLineOfSight(mob))
            return false;
        Vec3 toPlayer = player.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
        return toPlayer.dot(Vec3.directionFromRotation(0, mob.getYRot())) < -0.5;
    }

    static void click(ServerPlayer p, KickNet.Click click) {
        Session s = active.get(p.getUUID());
        if (s == null) {
            if (click.phase() != START || !(p.level().getEntity(click.mob()) instanceof SnotlingEntity mob) || !canStart(p, mob))
                return;
            Vec3 eye = p.getEyePosition();
            if (mob.getBoundingBox().inflate(0.15).clip(eye, eye.add(p.getLookAngle().scale(4))).isEmpty()) return;
            s = new Session(p, mob);
            active.put(p.getUUID(), s);
            mob.holdForKick(true);
            publish(p, s);
        } else if (s.phase == START && click.phase() == DONE && click.mob() == s.mob.getId() && click.start() == s.since) {
            long age = p.level().getGameTime() - s.since;
            int grace = Math.min(6, Math.max(1, (p.latency + 49) / 50 + 1));
            if (age < 5 * FRAME_TICKS || age >= WINDOW_END + grace || click.age() < 5 * FRAME_TICKS || click.age() >= WINDOW_END || Math.abs(age - click.age()) > grace + 2) {
                finish(p, s);
                return;
            }
            s.phase = DONE;
            s.since = p.level().getGameTime();
            publish(p, s);
        }
    }

    private static KickNet.State state(ServerPlayer p, Session s, int phase) {
        return new KickNet.State(p.getId(), s.mob.getId(), phase, s.since, s.yaw);
    }

    private static void publish(ServerPlayer p, Session s) {
        KickNet.broadcast(p, state(p, s, s.phase));
    }

    private static void finish(ServerPlayer p, Session s) {
        if (!s.impacted && s.mob.kickHeld()) s.mob.holdForKick(false);
        active.remove(p.getUUID());
        KickNet.broadcast(p, state(p, s, END));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Session s = active.get(p.getUUID());
        if (s == null) return;
        long age = p.level().getGameTime() - s.since;
        if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.level() != s.mob.level() || p.distanceToSqr(s.anchor) > 0.55 * 0.55 || (!s.impacted && (!s.mob.isAlive() || !s.mob.kickHeld())) || age >= (s.phase == START ? WINDOW_END + 6 : DONE_TICKS)) {
            finish(p, s);
            return;
        }
        if (s.phase != DONE || s.impacted || age < IMPACT) return;
        s.impacted = true;
        SnotlingEntity mob = s.mob;
        mob.holdForKick(false);
        for (var other : p.level().getEntitiesOfClass(SnotlingEntity.class, mob.getBoundingBox().inflate(5), m -> m != mob && m.isAlive() && !m.kickHeld() && m.distanceToSqr(mob) <= 25)) {
            Vec3 away = other.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            other.knockIntoStun(away.lengthSqr() < 0.01 ? s.direction : away);
        }
        mob.invulnerableTime = 0;
        mob.hurt(p.damageSources().playerAttack(p), Float.MAX_VALUE);
        mob.launchFromKick(s.direction);
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor) {
            Session s = active.get(actor.getUUID());
            if (s != null) KickNet.send(viewer, state(actor, s, s.phase));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Session s = active.remove(p.getUUID());
            if (s != null && !s.impacted) s.mob.holdForKick(false);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent e) {
        active.clear();
    }
}
