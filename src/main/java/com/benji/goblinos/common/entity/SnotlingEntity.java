package com.benji.goblinos.common.entity;

import java.util.EnumSet;

import com.benji.goblinos.Goblinos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
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
    final SnotlingAwareness awareness = new SnotlingAwareness();
    private static final EntityDataAccessor<Integer> WARN_FRAME = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ALERT_MOTION = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation ANXIOUS_IDLE = RawAnimation.begin().thenLoop("anxiety");
    private static final RawAnimation ANXIOUS_IDLE_2 = RawAnimation.begin().thenLoop("anxiety2");
    private int alertVariant;
    private int movingUntil;

    private static final RawAnimation ANXIOUS_WALK = RawAnimation.begin().thenLoop("anxiety_walk");
    private static final RawAnimation ANXIOUS_WALK_2 = RawAnimation.begin().thenLoop("anxiety_walk2");

    public int warningFrame() {
        return entityData.get(WARN_FRAME);
    }

    void showWarning(int frame) {
        if (frame > 0 && warningFrame() == 0) alertVariant = 1 + random.nextInt(2);
        entityData.set(WARN_FRAME, frame);
    }

    private class InvestigateNoiseGoal extends Goal {
        private int nextPath;

        InvestigateNoiseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return awareness.source != null && getTarget() == null && !isStunned() && !isPassenger() && !isInWaterOrBubble() && insideCamp(position());
        }

        @Override
        public void start() {
            nextPath = 0;
        }

        @Override
        public void tick() {
            Vec3 sound = awareness.source;
            if (sound == null || getTarget() != null) return;
            getLookControl().setLookAt(sound.x, sound.y + 0.5, sound.z, 12, 10);
            if (position().distanceToSqr(sound) < 2.25) {
                getNavigation().stop();
                return;
            }
            if (tickCount < nextPath) return;
            nextPath = tickCount + 16;
            Vec3 destination = sound;
            if (!insideCamp(destination)) {
                Vec3 center = Vec3.atBottomCenterOf(homeTent);
                destination = center.add(sound.subtract(center).normalize().scale(TENT_RADIUS - 1));
            }
            Path path = getNavigation().createPath(BlockPos.containing(destination), 1);
            if (pathInsideCamp(path)) getNavigation().moveTo(path, 0.55D);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> STUNNED = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FALLING = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FALL = RawAnimation.begin().thenPlayAndHold("fall");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation STUN_SIT = RawAnimation.begin().thenPlayAndHold("stun_sit");
    private static final RawAnimation STUN = RawAnimation.begin().thenLoop("stun");
    private static final EntityDataAccessor<Boolean> LOOKING_AROUND = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation ALERT_END = RawAnimation.begin().thenPlayAndHold("anxiety2");
    private static final int ALERT_END_MODE = 17;
    private static final int ALERT_END_TICKS = 40;
    private static final int TO_IDLE_BLEND_TICKS = 5;
    private int lookUntil;
    private int landingIdleUntil;
    private boolean hadTarget;
    private static final int STUN_SIT_MODE = 7;
    private static final int STUN_LOOP_MODE = 4;
    private static final int STUN_SIT_TO_LOOP_BLEND_TICKS = 6;
    private static final int STUN_SIT_TICKS = 23;
    private static final int DEATH_CLIP_TICKS = 18;
    private static final int DEATH_BLEND_TICKS = 2;
    private static final int STUN_CYCLE_TICKS = 60;
    static final int ACTION_BLEND_TICKS = 4;
    private static final EntityDataAccessor<Boolean> STUN_LOOP = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);
    private int stunPhaseTicks;
    private int stunCycles;
    private float stunYaw, stunBodyYaw, stunHeadYaw, stunPitch;

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

        for (SnotlingEntity mob : player.level().getEntitiesOfClass(SnotlingEntity.class, player.getBoundingBox().inflate(MOCKERY_RADIUS), other -> other.isAlive() && other.distanceToSqr(player) <= MOCKERY_RADIUS * MOCKERY_RADIUS)) {
            mob.deathPosition = player.position();
            mob.laughUntil = mob.level().getGameTime() + MOCKERY_TICKS;
            mob.endConversation();
        }
    }

    private static final int TENT_RADIUS = 10;
    private static final int TENT_FORGET_DISTANCE = 48;
    private static final ResourceLocation TENT_ID = ResourceLocation.fromNamespaceAndPath("goblinos", "tent");
    private static final ResourceLocation TENT_PART_ID = ResourceLocation.fromNamespaceAndPath("goblinos", "tent_part");
    private BlockPos homeTent;
    private int nextTentCheck;

    private boolean isTent(BlockPos pos) {
        var id = BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos).getBlock());
        return TENT_ID.equals(id) || TENT_PART_ID.equals(id);
    }

    private void updateTent() {
        if (homeTent != null && tentDistanceSqr(position()) > TENT_FORGET_DISTANCE * TENT_FORGET_DISTANCE) {
            homeTent = null;
        }
        if (tickCount < nextTentCheck) return;
        nextTentCheck = tickCount + 60 + random.nextInt(41);
        if (homeTent != null && (!level().hasChunkAt(homeTent) || isTent(homeTent))) return;

        homeTent = null;
        BlockPos here = blockPosition();
        double best = TENT_RADIUS * TENT_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-TENT_RADIUS, -4, -TENT_RADIUS), here.offset(TENT_RADIUS, 4, TENT_RADIUS))) {
            double distance = position().distanceToSqr(Vec3.atBottomCenterOf(pos));
            if (distance <= best && level().hasChunkAt(pos) && isTent(pos)) {
                best = distance;
                homeTent = pos.immutable();
            }
        }
    }

    private double tentDistanceSqr(Vec3 pos) {
        return pos.distanceToSqr(Vec3.atBottomCenterOf(homeTent));
    }

    private boolean insideCamp(Vec3 pos) {
        return homeTent == null || tentDistanceSqr(pos) <= TENT_RADIUS * TENT_RADIUS;
    }

    private boolean pathInsideCamp(Path path) {
        if (path == null) return false;
        if (homeTent == null) return true;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            if (!insideCamp(path.getEntityPosAtNode(this, i))) return false;
        }
        return true;
    }

    private class CampStrollGoal extends WaterAvoidingRandomStrollGoal {
        CampStrollGoal() {
            super(SnotlingEntity.this, 0.8D);
        }

        @Override
        protected Vec3 getPosition() {
            if (homeTent == null) return super.getPosition();
            if (!insideCamp(position())) return null;
            for (int i = 0; i < 4; i++) {
                Vec3 spot = super.getPosition();
                if (spot != null && insideCamp(spot) && pathInsideCamp(getNavigation().createPath(BlockPos.containing(spot), 0)))
                    return spot;
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && insideCamp(position()) && (homeTent == null || pathInsideCamp(getNavigation().getPath()));
        }
    }

    private class ReturnToTentGoal extends Goal {
        private int nextAttempt;

        ReturnToTentGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !insideCamp(position()) && !isPassenger();
        }

        @Override
        public void tick() {
            if (homeTent == null || getTarget() != null || tickCount < nextAttempt || !getNavigation().isDone()) return;
            nextAttempt = tickCount + 20;
            Path path = getNavigation().createPath(homeTent, 5);
            if (path != null && path.getEndNode() != null && tentDistanceSqr(Vec3.atBottomCenterOf(path.getEndNode().asBlockPos())) < tentDistanceSqr(position()) && getNavigation().moveTo(path, 1.0D))
                return;
            Vec3 step = DefaultRandomPos.getPosTowards(SnotlingEntity.this, 10, 5, Vec3.atBottomCenterOf(homeTent), Math.PI / 3);
            if (step != null) getNavigation().moveTo(step.x, step.y, step.z, 1.0D);
        }

        @Override
        public void stop() {
            getNavigation().stop();
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
            celebrateShot = hit && !level().getEntitiesOfClass(SnotlingEntity.class, getBoundingBox().inflate(MOCKERY_RADIUS), other -> other != this && other.isAlive() && distanceToSqr(other) <= MOCKERY_RADIUS * MOCKERY_RADIUS).isEmpty();
        }
    }

    private boolean aimingOrCancelling() {
        return switch (DirtAttackGoal.Pose.byId(entityData.get(COMBAT_POSE))) {
            case READY, READY2, FORWARD, BACK, CANCEL -> true;
            default -> false;
        };
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
        entityData.define(KICK_HELD, false);
        entityData.define(KICK_FACING, Float.NaN);
        entityData.define(WARN_FRAME, 0);
        entityData.define(ALERT_MOTION, 0);
        this.entityData.define(FALLING, false);
        this.entityData.define(LOOKING_AROUND, false);
        this.entityData.define(STUNNED, false);
        this.entityData.define(STUN_LOOP, false);
        this.entityData.define(CLIMBING, false);
        this.entityData.define(COMBAT_POSE, 0);
        this.entityData.define(RUN_VARIANT, 0);
        this.entityData.define(TALK_VARIANT, 0);
    }

    private static final EntityDataAccessor<Boolean> KICK_HELD = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation HELD_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final EntityDataAccessor<Float> KICK_FACING = SynchedEntityData.defineId(SnotlingEntity.class, EntityDataSerializers.FLOAT);
    private int kickFlightTicks;

    public boolean kickHeld() { return entityData.get(KICK_HELD); }

    public void holdForKick(boolean held) {
        entityData.set(KICK_HELD, held);
        if (held) {
            endConversation();
            goalSelector.getRunningGoals().toList().forEach(Goal::stop);
            getNavigation().stop();
            pose(DirtAttackGoal.Pose.NONE);
            setDeltaMovement(Vec3.ZERO);
            setJumping(false);
        }
    }

    public void knockIntoStun(Vec3 away) {
        if (isClimbing()) finishClimb(true);
        float yaw = (float)Math.toDegrees(Math.atan2(away.x, -away.z));
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        entityData.set(KICK_FACING, yaw);
        beginStun();
        stunYaw = stunBodyYaw = stunHeadYaw = yaw;
        launchFromKick(away);
    }

    public void launchFromKick(Vec3 direction) {
        setOnGround(false);
        setDeltaMovement(direction.scale(0.43).add(0, 0.32, 0));
        kickFlightTicks = 10;
        hurtMarked = true;
    }

    public boolean isStunned() {
        return entityData.get(STUNNED);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if ((STUNNED.equals(key) && isStunned()) || (KICK_HELD.equals(key) && kickHeld())) {
            stunYaw = getYRot();
            stunBodyYaw = yBodyRot;
            stunHeadYaw = yHeadRot;
            stunPitch = getXRot();
        }
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        double drop = fallDistance - Math.min(0.0D, dy);
        if (!level().isClientSide && landed && fallDistance > 0 && drop > 1.00001D && !isStunned() && isAlive() && !isPassenger() && !isInWaterOrBubble() && !isInLava()) {
            beginStun();
        }
        super.checkFallDamage(dy, landed, state, pos);
    }

    private void beginStun() {
        entityData.set(STUN_LOOP, false);
        stunPhaseTicks = ACTION_BLEND_TICKS + STUN_SIT_TICKS;
        stunCycles = 2 + random.nextInt(2);
        entityData.set(STUNNED, true);
        endConversation();
        goalSelector.getRunningGoals().toList().forEach(Goal::stop);
        getNavigation().stop();
        setJumping(false);
        pose(DirtAttackGoal.Pose.NONE);
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !isStunned() && !kickHeld();
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
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Player>(this, Player.class, true, false) {
            @Override
            public boolean canUse() {
                this.target = awareness.detected;
                return this.target != null;
            }
        });
        this.goalSelector.addGoal(1, new FleeOnceGoal());
        this.goalSelector.addGoal(1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
            @Override public boolean canUse() {
                return entityData.get(LOOKING_AROUND) && getTarget() == null && !aimingOrCancelling();
            }
            @Override public void start() { getNavigation().stop(); }
        });
        this.goalSelector.addGoal(2, new ConversationGoal());
        this.goalSelector.addGoal(3, new DirtAttackGoal(this));
        this.goalSelector.addGoal(4, new InvestigateNoiseGoal());
        this.goalSelector.addGoal(4, new ReturnToTentGoal());
        this.goalSelector.addGoal(5, new CampStrollGoal());
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (stunPhaseTicks > 0 && --stunPhaseTicks == 0) {
                if (entityData.get(STUN_LOOP)) {
                    entityData.set(STUNNED, false);
                    entityData.set(KICK_FACING, Float.NaN);
                    entityData.set(STUN_LOOP, false);
                } else {
                    entityData.set(STUN_LOOP, true);
                    stunPhaseTicks = STUN_SIT_TO_LOOP_BLEND_TICKS + stunCycles * STUN_CYCLE_TICKS;
                }
            }
            updateTent();
            boolean wasAlert = warningFrame() > 0;
            awareness.tick(this);
            boolean hasTarget = getTarget() != null;
            if (hasTarget || aimingOrCancelling() || warningFrame() > 0 || !onGround() || isNoAi() || !isAlive()
                    || isStunned() || kickHeld() || isPassenger() || isClimbing() || isInWaterOrBubble() || isInLava() || isFleeing()) {
                lookUntil = 0;
            } else if (wasAlert || hadTarget) {
                lookUntil = tickCount + ALERT_END_TICKS + ACTION_BLEND_TICKS;
                endConversation();
                getNavigation().stop();
                setSpeed(0);
            }
            hadTarget = hasTarget;
            entityData.set(LOOKING_AROUND, tickCount < lookUntil);
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
        double beforeX = getX();
        double beforeZ = getZ();
        super.tick();
        if (kickFlightTicks > 0) kickFlightTicks--;
        if (!level().isClientSide) {
            int motion = 0;
            if (warningFrame() > 0) {
                double dx = getX() - beforeX;
                double dz = getZ() - beforeZ;
                if (dx * dx + dz * dz > 0.000004D) movingUntil = tickCount + 4;
                motion = tickCount < movingUntil ? alertVariant : -alertVariant;
            } else {
                movingUntil = 0;
            }
            entityData.set(ALERT_MOTION, motion);
            entityData.set(FALLING, isAlive() && !onGround() && (fallDistance > 0.0F || getDeltaMovement().y < -0.01D) && !isClimbing() && !isPassenger() && !isInWaterOrBubble() && !isInLava());
        }
        if (isStunned() || kickHeld()) {
            float kickFacing = entityData.get(KICK_FACING);
            if (isStunned() && Float.isFinite(kickFacing)) stunYaw = stunBodyYaw = stunHeadYaw = kickFacing;
            setYRot(stunYaw);
            setXRot(stunPitch);
            yRotO = stunYaw;
            xRotO = stunPitch;
            yBodyRotO = yBodyRot = stunBodyYaw;
            yHeadRotO = yHeadRot = stunHeadYaw;
        }
        if (!level().isClientSide && isClimbing()) {
            tickClimb();
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (kickHeld()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (kickFlightTicks > 0) {
            Vec3 velocity = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, velocity);
            if (onGround()) {
                kickFlightTicks = 0;
                setDeltaMovement(Vec3.ZERO);
            } else {
                Vec3 moved = getDeltaMovement();
                setDeltaMovement(moved.x * 0.91D, (moved.y - 0.08D) * 0.98D, moved.z * 0.91D);
            }
            return;
        }
        if (isStunned()) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(0, Math.min(0, motion.y), 0);
            if (!onGround()) {
                move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
                if (!isNoGravity()) setDeltaMovement(0, (getDeltaMovement().y - 0.08D) * 0.98D, 0);
            }
            return;
        }
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
        if (accepted && !kickHeld() && !isStunned() && amount > 0 && !level().isClientSide && isAlive()) {
            endConversation();
            socialCooldown = 300 + random.nextInt(301);
            int variant = random.nextInt(3);
            triggerAnim("controller", variant == 0 ? "hit" : "hit" + (variant + 1));
        }
        return accepted;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide) {
            triggerAnim("controller", "death");
        }
    }

    @Override
    protected void tickDeath() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (++deathTime >= DEATH_CLIP_TICKS + DEATH_BLEND_TICKS + 1 && !isRemoved()) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.45D, getZ(), 20, getBbWidth() * 0.35D, getBbHeight() * 0.22D, getBbWidth() * 0.35D, 0.025D);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homeTent != null) tag.putLong("SnotlingTent", homeTent.asLong());
        tag.putBoolean("SnotlingHasFled", hasFled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        homeTent = tag.contains("SnotlingTent") ? BlockPos.of(tag.getLong("SnotlingTent")) : null;
        hasFled = tag.getBoolean("SnotlingHasFled");
    }

    private boolean canSocialize() {
        return !aimingOrCancelling() && !entityData.get(LOOKING_AROUND) && awareness.source == null && !isStunned() && insideCamp(position()) && level().getGameTime() >= laughUntil && isAlive() && !isRemoved() && !isNoAi() && !isPassenger() && !isClimbing() && !isFleeing() && !isInWaterOrBubble() && !isInLava() && getTarget() == null && hurtTime == 0;
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
            Player threat = getLastHurtByMob() instanceof Player attacker ? attacker : getTarget() instanceof Player targetPlayer ? targetPlayer : null;
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
                if (!insideCamp(other.position()) || !hasLineOfSight(other)) continue;
                Path path = getNavigation().createPath(other, 0);
                if (path == null || !path.canReach() || !pathInsideCamp(path)) continue;
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
                    approachForConversation(spot);
                } else {
                    approachForConversation(talkPartner.position());
                }
            }
        }

        private void approachForConversation(Vec3 spot) {
            Path path = getNavigation().createPath(BlockPos.containing(spot), 0);
            if (insideCamp(spot) && pathInsideCamp(path)) getNavigation().moveTo(path, 0.8D);
            else endConversation();
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
            if (mode <= 1) {
                mode = switch (entityData.get(ALERT_MOTION)) {
                    case 1 -> 8;
                    case 2 -> 9;
                    case -1 -> 14;
                    case -2 -> 15;
                    default -> mode;
                };
            }
            int combat = entityData.get(COMBAT_POSE);
            if (combat != 0 && !isClimbing() && (combat == DirtAttackGoal.Pose.MOCK.ordinal() || (!isInWaterOrBubble() && !isFleeing())))
                mode = 30 + combat;
            if (entityData.get(LOOKING_AROUND) && mode <= 1) mode = ALERT_END_MODE;
            if (entityData.get(FALLING)) {
                mode = 5;
            } else if ((mode == 0 || mode == 1) && onGround()) {
                if (animationMode == 5 || animationMode == ALERT_END_MODE) landingIdleUntil = tickCount + TO_IDLE_BLEND_TICKS;
                if (tickCount < landingIdleUntil) mode = 0;
            }
            if (isStunned()) mode = entityData.get(STUN_LOOP) ? STUN_LOOP_MODE : STUN_SIT_MODE;
            if (kickHeld()) mode = 16;
            if (isDeadOrDying()) mode = 6;
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
                int cancelMode = 30 + DirtAttackGoal.Pose.CANCEL.ordinal();
                if (mode == STUN_SIT_MODE || animationMode == STUN_LOOP_MODE || mode == cancelMode || animationMode == cancelMode) {
                    blendTicks = ACTION_BLEND_TICKS;
                }
                if (animationMode == STUN_SIT_MODE && mode == STUN_LOOP_MODE) {
                    blendTicks = STUN_SIT_TO_LOOP_BLEND_TICKS;
                }
                if (mode == 5) blendTicks = 1;
                if (mode == ALERT_END_MODE) blendTicks = ACTION_BLEND_TICKS;
                if (mode == 0 && (animationMode == 5 || animationMode == ALERT_END_MODE)) blendTicks = TO_IDLE_BLEND_TICKS;
                if (mode == 6) blendTicks = DEATH_BLEND_TICKS;
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
            if (mode == 14) return event.setAndContinue(ANXIOUS_IDLE);
            if (mode == 15) return event.setAndContinue(ANXIOUS_IDLE_2);
            if (mode == 8) return event.setAndContinue(ANXIOUS_WALK);
            if (mode == 9) return event.setAndContinue(ANXIOUS_WALK_2);
            if (mode == ALERT_END_MODE) return event.setAndContinue(ALERT_END);
            if (mode == 16) return event.setAndContinue(HELD_IDLE);
            if (mode == 6) return event.setAndContinue(DEATH);
            if (mode == 5) return event.setAndContinue(FALL);
            if (mode == STUN_SIT_MODE) return event.setAndContinue(STUN_SIT);
            if (mode == STUN_LOOP_MODE) return event.setAndContinue(STUN);
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
                if (!name.equals("death") && (kickHeld() || isDeadOrDying() || isStunned() || entityData.get(FALLING)))
                    return false;
                transitionLength(name.equals("death") ? DEATH_BLEND_TICKS : HIT_BLEND_TICKS);
                return super.tryTriggerAnimation(name);
            }
        };
        controller.triggerableAnim("death", DEATH);
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
