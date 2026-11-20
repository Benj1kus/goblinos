package com.benji.goblinos.common.entity;

import java.util.EnumSet;
import com.benji.goblinos.Goblinos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;

import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mod.EventBusSubscriber(modid = Goblinos.MODID)
public class SnotlingEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double START_CLIMB_TICK = 0.75D * 20.0D;
    private static final double ON_BLOCK_TICK = 1.9167D * 20.0D;
    private static final int JUMP_DURATION_TICKS = 40;
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold("jump");

    private static final EntityDataAccessor<Integer> RUN_VARIANT = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TALK_VARIANT = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.INT);
    private static final double FLEE_DISTANCE = 5.0D;
    private static final double FLEE_SPEED = 1.45D;
    private static final double TALK_SEARCH_RADIUS = 8.0D;
    private static final int TALK_CYCLE_TICKS = 60;
    private static final EntityDataAccessor<Integer> COMBAT_POSE = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.INT);
    java.util.UUID pendingDirt;
    static final int MOCKERY_TICKS = 120;
    private static final double MOCKERY_RADIUS = 10.0D;
    Boolean celebrateShot;
    private Vec3 deathPosition;
    private long laughUntil;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        for (SnotlingEntity mob : player.level().getEntitiesOfClass(SnotlingEntity.class,
                player.getBoundingBox().inflate(MOCKERY_RADIUS),
                other -> other.isAlive() && other.distanceToSqr(player) <= MOCKERY_RADIUS * MOCKERY_RADIUS)) {
            mob.deathPosition = player.position();
            mob.laughUntil = mob.level().getGameTime() + MOCKERY_TICKS;
            mob.endConversation();
        }
    }

    private class DeathMockeryGoal extends Goal {
        DeathMockeryGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return deathPosition != null && level().getGameTime() < laughUntil;
        }

        @Override
        public void start() {
            getNavigation().stop();
            pose(DirtAttackGoal.Pose.MOCK);
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(deathPosition.x, deathPosition.y, deathPosition.z);
        }

        @Override
        public void stop() {
            deathPosition = null;
            pose(DirtAttackGoal.Pose.NONE);
        }
    }

    void dirtResult(java.util.UUID shot, boolean hit) {
        if (shot.equals(pendingDirt)) {
            pendingDirt = null;
            celebrateShot = hit && !level().getEntitiesOfClass(SnotlingEntity.class,
                    getBoundingBox().inflate(MOCKERY_RADIUS),
                    other -> other != this && other.isAlive() && distanceToSqr(other) <= MOCKERY_RADIUS * MOCKERY_RADIUS).isEmpty();
        }
    }

    void pose(DirtAttackGoal.Pose pose) {
        entityData.set(COMBAT_POSE, pose.ordinal());
    }

    private boolean hasFled;
    private int fleeRetryCooldown;
    private int socialCooldown;
    private int nextSocialSearch;
    private SnotlingEntity talkPartner;
    private boolean conversationLeader;
    private long approachDeadline;
    private long conversationEnd;
    private long nextTalkCycle;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 climbStart;
    private Vec3 climbEnd;
    private int climbStartedAt;
    private int climbCooldown;
    private float climbYaw;

    private int animationMode = -1;
    private RawAnimation locomotionSequence;

    public SnotlingEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new SnotlingMoveControl();
        this.socialCooldown = 100 + random.nextInt(201);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CLIMBING, false);
        this.entityData.define(COMBAT_POSE, 0);
        this.entityData.define(RUN_VARIANT, 0);
        this.entityData.define(TALK_VARIANT, 0);
    }

    public boolean isClimbing() {
        return this.entityData.get(CLIMBING);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.KNOCKBACK_RESISTANCE, 0.2D).add(Attributes.ATTACK_DAMAGE, 3.0D).add(Attributes.FOLLOW_RANGE, 15.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DeathMockeryGoal());
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, false));
        this.goalSelector.addGoal(1, new FleeOnceGoal());
        this.goalSelector.addGoal(2, new ConversationGoal());
        this.goalSelector.addGoal(3, new DirtAttackGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (climbCooldown > 0) --climbCooldown;
            if (fleeRetryCooldown > 0) --fleeRetryCooldown;
            if (socialCooldown > 0) --socialCooldown;
            if (talkPartner != null && (!canSocialize() || !talkPartner.canSocialize() || talkPartner.isRemoved() || talkPartner.talkPartner != this)) {
                endConversation();
            }
            if (isClimbing() && (!isAlive() || isNoAi() || isPassenger() || isInWaterOrBubble() || isInLava())) {
                finishClimb(true);
            }
        }
        super.tick();
        if (!level().isClientSide && isClimbing()) {
            tickClimb();
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (isClimbing()) {
            setDeltaMovement(Vec3.ZERO);
            setSpeed(0.0F);
            fallDistance = 0.0F;
            return;
        }
        super.travel(isTalking() ? Vec3.ZERO : input);
    }

    @Override
    protected void jumpFromGround() {
        if (!isClimbing() && (isInWaterOrBubble() || isInLava())) {
            super.jumpFromGround();
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !isClimbing() && !isFleeing() && talkPartner == null && super.doHurtTarget(target);
    }

    public boolean isTalking() {
        return entityData.get(TALK_VARIANT) != 0;
    }

    public boolean isFleeing() {
        return entityData.get(RUN_VARIANT) != 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && amount > 0 && !level().isClientSide && isAlive()) {
            endConversation();
            socialCooldown = 300 + random.nextInt(301);
            int variant = random.nextInt(3);
            triggerAnim("controller", variant == 0 ? "hit" : "hit" + (variant + 1));
        }
        return accepted;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SnotlingHasFled", hasFled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hasFled = tag.getBoolean("SnotlingHasFled");
    }

    private boolean canSocialize() {
        return level().getGameTime() >= laughUntil && isAlive() && !isRemoved() && !isNoAi() && !isPassenger() && !isClimbing() && !isFleeing() && !isInWaterOrBubble() && !isInLava() && getTarget() == null && hurtTime == 0;
    }

    private void endConversation() {
        SnotlingEntity partner = talkPartner;
        talkPartner = null;
        conversationLeader = false;
        entityData.set(TALK_VARIANT, 0);
        if (partner != null) {
            getNavigation().stop();
            socialCooldown = 300 + random.nextInt(301);
            if (partner.talkPartner == this) {
                partner.talkPartner = null;
                partner.conversationLeader = false;
                partner.entityData.set(TALK_VARIANT, 0);
                partner.getNavigation().stop();
                partner.socialCooldown = 300 + partner.random.nextInt(301);
            }
        }
    }

    private void facePartner() {
        if (talkPartner == null) return;
        getLookControl().setLookAt(talkPartner, 30.0F, 30.0F);
        if (isTalking()) {
            Vec3 direction = talkPartner.position().subtract(position());
            float yaw = (float) (Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI));
            setYRot(yaw);
            setYBodyRot(yaw);
        }
    }

    Path retreatPath(Player threat, double distance) {
        Vec3 away = position().subtract(threat.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 0.001D) away = new Vec3(1, 0, 0);
        away = away.normalize();
        double[] angles = {0, 0.55, -0.55, 1.0, -1.0};
        for (double angle : angles) {
            Vec3 direction = new Vec3(away.x * Math.cos(angle) - away.z * Math.sin(angle), 0, away.x * Math.sin(angle) + away.z * Math.cos(angle));
            Vec3 wanted = position().add(direction.scale(distance));
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos block = BlockPos.containing(wanted.x, getY() + dy, wanted.z);
                Path path = getNavigation().createPath(block, 0);
                if (path != null && path.canReach()) {
                    return path;
                }
            }
        }
        return null;
    }

    private class FleeOnceGoal extends Goal {
        private Path escapePath;
        private int remainingTicks;

        private FleeOnceGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (hasFled || fleeRetryCooldown > 0 || !isAlive() || isNoAi() || isPassenger() || isClimbing() || isInWaterOrBubble() || isInLava() || getHealth() >= getMaxHealth() * 0.5F)
                return false;
            Player threat = getLastHurtByMob() instanceof Player attacker ? attacker : getTarget() instanceof Player targetPlayer ? targetPlayer : level().getNearestPlayer(SnotlingEntity.this, 12.0D);
            if (threat == null || !threat.isAlive() || threat.isCreative() || threat.isSpectator() || distanceToSqr(threat) > 256.0D)
                return false;
            fleeRetryCooldown = 20;
            escapePath = retreatPath(threat, FLEE_DISTANCE);
            return escapePath != null;
        }

        @Override
        public void start() {
            endConversation();
            hasFled = true;
            remainingTicks = 120;
            entityData.set(RUN_VARIANT, 1 + random.nextInt(2));
            getNavigation().moveTo(escapePath, FLEE_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && !isPassenger() && remainingTicks > 0 && (isClimbing() || !getNavigation().isDone());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            --remainingTicks;
        }

        @Override
        public void stop() {
            entityData.set(RUN_VARIANT, 0);
            getNavigation().stop();
            escapePath = null;
        }
    }

    private class ConversationGoal extends Goal {
        private ConversationGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (talkPartner != null) return canSocialize();
            if (socialCooldown > 0 || tickCount < nextSocialSearch || !canSocialize()) return false;
            nextSocialSearch = tickCount + 40 + random.nextInt(81);
            for (SnotlingEntity other : level().getEntitiesOfClass(SnotlingEntity.class, getBoundingBox().inflate(TALK_SEARCH_RADIUS), other -> other != SnotlingEntity.this && other.talkPartner == null && other.socialCooldown == 0 && other.canSocialize() && distanceToSqr(other) <= TALK_SEARCH_RADIUS * TALK_SEARCH_RADIUS)) {
                if (!hasLineOfSight(other)) continue;
                Path path = getNavigation().createPath(other, 0);
                if (path == null || !path.canReach()) continue;
                talkPartner = other;
                other.talkPartner = SnotlingEntity.this;
                conversationLeader = true;
                other.conversationLeader = false;
                approachDeadline = level().getGameTime() + 200;
                other.approachDeadline = approachDeadline;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return talkPartner != null && talkPartner.talkPartner == SnotlingEntity.this && canSocialize() && talkPartner.canSocialize() && distanceToSqr(talkPartner) <= 100.0D && level().getGameTime() < (isTalking() ? conversationEnd : approachDeadline);
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (talkPartner == null) return;
            facePartner();
            if (!conversationLeader) return;
            long now = level().getGameTime();
            double distance = distanceToSqr(talkPartner);
            if (isTalking()) {
                if (distance > 12.25D || !hasLineOfSight(talkPartner)) {
                    endConversation();
                    return;
                }
                getNavigation().stop();
                talkPartner.getNavigation().stop();
                if (now >= nextTalkCycle) {
                    chooseTalkAnimations();
                    nextTalkCycle += TALK_CYCLE_TICKS;
                }
                return;
            }
            if (distance >= 1.44D && distance <= 5.29D && hasLineOfSight(talkPartner)) {
                getNavigation().stop();
                talkPartner.getNavigation().stop();
                conversationEnd = now + (2 + random.nextInt(3)) * TALK_CYCLE_TICKS;
                talkPartner.conversationEnd = conversationEnd;
                nextTalkCycle = now + TALK_CYCLE_TICKS;
                chooseTalkAnimations();
            } else if (tickCount % 10 == 0) {
                if (distance < 1.44D) {
                    Vec3 away = position().subtract(talkPartner.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 0.001D) away = new Vec3(1, 0, 0);
                    Vec3 spot = talkPartner.position().add(away.normalize().scale(1.8D));
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 0.8D);
                } else {
                    getNavigation().moveTo(talkPartner, 0.8D);
                }
            }
        }

        private void chooseTalkAnimations() {
            entityData.set(TALK_VARIANT, 1 + random.nextInt(3));
            talkPartner.entityData.set(TALK_VARIANT, 1 + talkPartner.random.nextInt(3));
        }

        @Override
        public void stop() {
            endConversation();
        }
    }

    private boolean tryStartClimb(double x, double y, double z) {
        if (level().isClientSide || isClimbing() || climbCooldown > 0 || !onGround() || isNoAi() || isPassenger() || isInWaterOrBubble() || isInLava()) {
            return false;
        }
        Vec3 destination = new Vec3(x, y, z);
        Vec3 delta = destination.subtract(position());
        if (delta.y <= maxUpStep() + 0.01D || delta.y > 1.05D || delta.horizontalDistanceSqr() > 1.0D || delta.horizontalDistanceSqr() < 0.01D) {
            return false;
        }
        AABB startBox = getBoundingBox().deflate(1.0E-5D);
        AABB raisedBox = startBox.move(0.0D, delta.y, 0.0D);
        AABB destinationBox = startBox.move(delta);
        if (!level().noCollision(this, startBox.expandTowards(0.0D, delta.y, 0.0D)) || !level().noCollision(this, raisedBox.expandTowards(delta.x, 0.0D, delta.z)) || !hasSupport(destinationBox)) {
            return false;
        }
        climbStart = position();
        climbEnd = destination;
        climbStartedAt = tickCount;
        climbYaw = (float) (Mth.atan2(-delta.x, delta.z) * (180.0D / Math.PI));
        setDeltaMovement(Vec3.ZERO);
        setJumping(false);
        this.entityData.set(CLIMBING, true);
        faceClimb();
        return true;
    }

    private boolean hasSupport(AABB destinationBox) {
        return level().getBlockCollisions(this, destinationBox.move(0.0D, -0.08D, 0.0D)).iterator().hasNext();
    }

    private void tickClimb() {
        if (climbStart == null || climbEnd == null) {
            finishClimb(true);
            return;
        }
        int age = tickCount - climbStartedAt;
        faceClimb();
        setJumping(false);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;

        double progress = Mth.clamp((age - START_CLIMB_TICK) / (ON_BLOCK_TICK - START_CLIMB_TICK), 0.0D, 1.0D);
        double vertical = smoothStep(Mth.clamp(progress / 0.60D, 0.0D, 1.0D));
        double horizontal = smoothStep(Mth.clamp((progress - 0.60D) / 0.40D, 0.0D, 1.0D));
        Vec3 next = new Vec3(Mth.lerp(horizontal, climbStart.x, climbEnd.x), Mth.lerp(vertical, climbStart.y, climbEnd.y), Mth.lerp(horizontal, climbStart.z, climbEnd.z));
        Vec3 movement = next.subtract(position());
        AABB landingBox = getBoundingBox().move(climbEnd.subtract(position())).deflate(1.0E-5D);
        if (!hasSupport(landingBox) || !level().noCollision(this, getBoundingBox().expandTowards(movement).deflate(1.0E-5D))) {
            finishClimb(true);
            return;
        }
        setPos(next.x, next.y, next.z);
        setOnGround(progress >= 1.0D || age <= START_CLIMB_TICK);
        if (age >= JUMP_DURATION_TICKS) {
            finishClimb(false);
        }
    }

    private void faceClimb() {
        setYRot(climbYaw);
        setYBodyRot(climbYaw);
        setYHeadRot(climbYaw);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private void finishClimb(boolean interrupted) {
        this.entityData.set(CLIMBING, false);
        climbStart = null;
        climbEnd = null;
        climbCooldown = interrupted ? 10 : 2;
        setDeltaMovement(Vec3.ZERO);
        setJumping(false);
        if (interrupted) {
            getNavigation().stop();
        } else {
            getNavigation().recomputePath();
        }
    }

    private class SnotlingMoveControl extends MoveControl {
        private SnotlingMoveControl() {
            super(SnotlingEntity.this);
        }

        @Override
        public void tick() {
            if (isClimbing()) {
                mob.setSpeed(0.0F);
                return;
            }
            if (this.operation == Operation.MOVE_TO && tryStartClimb(this.wantedX, this.wantedY, this.wantedZ)) {
                this.operation = Operation.WAIT;
                mob.setSpeed(0.0F);
                return;
            }
            super.tick();
        }
    }

    private RawAnimation newLocomotionSequence(boolean walking) {
        if (walking) {
            String walk = switch (random.nextInt(3)) {
                case 0 -> "walk";
                case 1 -> "walk2";
                default -> "walk3";
            };
            return RawAnimation.begin().thenLoop(walk);
        }

        RawAnimation sequence = RawAnimation.begin();
        int cycles = 1 + random.nextInt(3);
        for (int i = 0; i < cycles; ++i) {
            sequence.then("idle", Animation.LoopType.PLAY_ONCE);
        }
        sequence.then(random.nextBoolean() ? "idle2" : "idle3", Animation.LoopType.PLAY_ONCE);
        return sequence;
    }

    private static final int MOVEMENT_BLEND_TICKS = 3;
    private static final int HIT_BLEND_TICKS = 2;
    private static final int TALK_TO_IDLE_BLEND_TICKS = 6;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<SnotlingEntity> controller = new AnimationController<>(this, "controller", MOVEMENT_BLEND_TICKS, event -> {
            int mode = isClimbing() ? 2 : isInWaterOrBubble() ? 3 : isTalking() ? 10 + entityData.get(TALK_VARIANT) : isFleeing() ? 20 + entityData.get(RUN_VARIANT) : (event.isMoving() ? 1 : 0);
            int combat = entityData.get(COMBAT_POSE);
            if (combat != 0 && !isClimbing() && (combat == DirtAttackGoal.Pose.MOCK.ordinal() || (!isInWaterOrBubble() && !isFleeing()))) mode = 30 + combat;
            var ctrl = event.getController();

            if (mode != animationMode) {
                boolean wasTalking = animationMode >= 11 && animationMode <= 13;
                int blendTicks = MOVEMENT_BLEND_TICKS;

                if (mode == 0 && wasTalking) {
                    blendTicks = TALK_TO_IDLE_BLEND_TICKS;
                } else if (mode == 2 || (mode == 0 && animationMode != 1)) {
                    blendTicks = 0;
                }

                if (mode >= 30 && DirtAttackGoal.Pose.byId(combat).timed) blendTicks = 0;
                ctrl.transitionLength(blendTicks);
            } else if (mode == 0 && ctrl.getAnimationState() == AnimationController.State.RUNNING) {
                ctrl.transitionLength(0);
            }

            if (mode != animationMode) {
                animationMode = mode;
                event.getController().forceAnimationReset();
                locomotionSequence = mode >= 2 ? null : newLocomotionSequence(mode == 1);
            }
            if (mode >= 30) return event.setAndContinue(DirtAttackGoal.Pose.byId(combat).clip);
            if (mode == 2) {
                return event.setAndContinue(JUMP);
            }
            if (mode == 3) return event.setAndContinue(RawAnimation.begin().thenLoop("swim"));
            if (mode >= 11 && mode <= 13) {
                int variant = mode - 10;
                return event.setAndContinue(RawAnimation.begin().thenLoop(variant == 1 ? "talk" : "talk" + variant));
            }
            if (mode >= 21 && mode <= 22) {
                return event.setAndContinue(RawAnimation.begin().thenLoop(mode == 21 ? "run" : "run2"));
            }
            if (mode == 0 && event.getController().hasAnimationFinished()) {
                locomotionSequence = newLocomotionSequence(false);
                event.getController().forceAnimationReset();
            }
            return event.setAndContinue(locomotionSequence);
        }) {
            @Override
            public boolean tryTriggerAnimation(String name) {
                transitionLength(HIT_BLEND_TICKS);
                return super.tryTriggerAnimation(name);
            }
        };
        controller.triggerableAnim("hit", RawAnimation.begin().then("hit", Animation.LoopType.PLAY_ONCE));
        controller.triggerableAnim("hit2", RawAnimation.begin().then("hit2", Animation.LoopType.PLAY_ONCE));
        controller.triggerableAnim("hit3", RawAnimation.begin().then("hit3", Animation.LoopType.PLAY_ONCE));
        controller.setCustomInstructionKeyframeHandler(event -> {
        });
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
