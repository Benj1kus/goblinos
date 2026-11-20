package com.benji.goblinos.common.entity;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.RawAnimation;

final class DirtAttackGoal extends Goal {
    enum Pose {
        NONE(null, false), RUN("run", false), MOCK("mockery", true), SEARCH("search", true), READY("attack_idle", false), READY2("attack_idle2", false), FORWARD("attack_walk", false), BACK("attack_walk_behind", false), THROW("attack", true);

        private static final Pose[] POSES = values();
        final RawAnimation clip;
        final boolean timed;

        Pose(String name, boolean timed) {
            this.timed = timed;
            if (name == null) clip = null;
            else if (name.equals("search") || name.equals("attack")) clip = RawAnimation.begin().thenPlayAndHold(name);
            else clip = RawAnimation.begin().thenLoop(name);
        }

        static Pose byId(int id) {
            return POSES[id];
        }
    }

    private enum Phase {RANGE, SEARCH, AIM, THROW, COOLDOWN, CELEBRATE}

    private static final int ATTACK_COOLDOWN_TICKS = 100;
    private long nextAttackTime;
    private static final int RELEASE_TICK = 6;
    private static final int THROW_TICKS = 20;
    private static final double SLOW_SPEED = 0.55;
    private static final double MIN_THROW_DISTANCE = 3.0D;
    private static final double MAX_THROW_DISTANCE = 6.0D;
    private final SnotlingEntity mob;
    private Player target;
    private Phase phase;
    private Pose ready;
    private int age, aimTicks, nextPathTick;
    private Path backstepDetour;

    DirtAttackGoal(SnotlingEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.getTarget() instanceof Player p) || !valid(p)) return false;
        target = p;
        return true;
    }

    private boolean valid(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == mob.level() && !mob.isInWaterOrBubble() && !mob.isPassenger() && mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && valid(target) && mob.getTarget() == target;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        enter(mob.level().getGameTime() < nextAttackTime ? Phase.COOLDOWN : Phase.RANGE);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.pose(Pose.NONE);
        mob.pendingDirt = null;
        mob.celebrateShot = null;
        target = null;
    }

    private void enter(Phase next) {
        phase = next;
        age = 0;
        nextPathTick = 0;
        backstepDetour = null;
        mob.getNavigation().stop();
        switch (next) {
            case RANGE -> mob.pose(Pose.NONE);
            case CELEBRATE -> mob.pose(Pose.MOCK);
            case SEARCH -> mob.pose(Pose.SEARCH);
            case AIM -> {
                ready = mob.getRandom().nextBoolean() ? Pose.READY : Pose.READY2;
                aimTicks = 12 + mob.getRandom().nextInt(29);
                mob.pose(ready);
            }
            case THROW -> mob.pose(Pose.THROW);
            case COOLDOWN -> mob.pose(Pose.NONE);
        }
    }

    @Override
    public void tick() {
        if (mob.isClimbing()) return;
        ++age;
        if (phase != Phase.COOLDOWN) mob.getLookControl().setLookAt(target, 60, 60);
        switch (phase) {
            case RANGE -> {
                if (positionForThrow(false)) {
                    enter(Phase.SEARCH);
                }
            }
            case SEARCH -> {
                if (age >= 30) enter(Phase.AIM);
            }
            case AIM -> {
                if (positionForThrow(true) && mob.hurtTime == 0 && --aimTicks <= 0) enter(Phase.THROW);
            }
            case THROW -> {
                if (mob.hurtTime > 0 && age <= RELEASE_TICK) {
                    enter(Phase.AIM);
                    return;
                }
                if (age == RELEASE_TICK) launch();
                if (age >= THROW_TICKS && (mob.celebrateShot != null || mob.level().getGameTime() >= nextAttackTime)) {
                    finishShot();
                }
            }
            case COOLDOWN -> {
                if (mob.level().getGameTime() >= nextAttackTime) {
                    enter(Phase.RANGE);
                } else {
                    walkAway();
                }
            }
            case CELEBRATE -> {
                if (age >= SnotlingEntity.MOCKERY_TICKS) {
                    nextAttackTime = mob.level().getGameTime() + ATTACK_COOLDOWN_TICKS;
                    enter(Phase.COOLDOWN);
                }
            }
        }
    }

    private void walkAway() {
        var navigation = mob.getNavigation();
        double gap = MAX_THROW_DISTANCE + 3.0D - mob.distanceTo(target);

        if (gap <= 0.5D) {
            navigation.stop();
            mob.pose(Pose.NONE);
            return;
        }

        if (mob.tickCount >= nextPathTick) {
            nextPathTick = mob.tickCount + 12;

            Path path = mob.retreatPath(target, Math.min(5.0D, gap));
            if (path != null) {
                navigation.moveTo(path, 1.45D);
            }
        }

        mob.pose(navigation.isDone() ? Pose.NONE : Pose.RUN);
    }

    private boolean positionForThrow(boolean armed) {
        double distance = mob.position().distanceTo(target.position());
        if (distance < MIN_THROW_DISTANCE) {
            if (backstepDetour != null && !mob.getNavigation().isDone()) {
                mob.pose(armed ? Pose.FORWARD : Pose.RUN);
                return false;
            }
            backstepDetour = null;
            if (mob.horizontalCollision && mob.tickCount >= nextPathTick) {
                nextPathTick = mob.tickCount + 10;
                backstepDetour = mob.retreatPath(target, (MIN_THROW_DISTANCE + MAX_THROW_DISTANCE) * 0.5D - distance);
                if (backstepDetour != null && mob.getNavigation().moveTo(backstepDetour, SLOW_SPEED)) {
                    mob.pose(armed ? Pose.FORWARD : Pose.RUN);
                    return false;
                }
            }
            mob.pose(Pose.BACK);
            mob.getNavigation().stop();
            double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
            float yaw = (float) (Mth.atan2(-dx, dz) * 180 / Math.PI);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.getMoveControl().strafe(-0.55F, 0);
            return false;
        }
        backstepDetour = null;
        if (distance > MAX_THROW_DISTANCE || !mob.hasLineOfSight(target)) {
            mob.pose(armed ? Pose.FORWARD : Pose.RUN);
            if (mob.tickCount >= nextPathTick) {
                mob.getNavigation().moveTo(target, armed ? SLOW_SPEED : 1.35);
                nextPathTick = mob.tickCount + 5;
            }
            return false;
        }
        mob.getNavigation().stop();
        if (armed) mob.pose(ready);
        return true;
    }

    private void launch() {
        DirtBallEntity ball = new DirtBallEntity(mob);
        Vec3 delta = target.getBoundingBox().getCenter().subtract(ball.position());
        ball.shoot(delta.x, delta.y + delta.horizontalDistance() * 0.025, delta.z, 1.1F, 2F);
        mob.pendingDirt = ball.getUUID();
        mob.celebrateShot = null;
        if (mob.level().addFreshEntity(ball)) {
            nextAttackTime = mob.level().getGameTime() + ATTACK_COOLDOWN_TICKS;
        } else {
            mob.dirtResult(ball.getUUID(), false);
        }
    }

    private void finishShot() {
        boolean celebrate = Boolean.TRUE.equals(mob.celebrateShot);
        mob.pendingDirt = null;
        mob.celebrateShot = null;
        enter(celebrate ? Phase.CELEBRATE : Phase.COOLDOWN);
    }
}
