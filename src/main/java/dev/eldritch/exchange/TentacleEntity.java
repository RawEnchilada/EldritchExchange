package dev.eldritch.exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TentacleEntity extends Mob {

    public static final int EMERGE_TICKS = 24;
    public static final int RETRACT_TICKS = 18;
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_ATTACK = 1;
    public static final int ANIM_HIT = 2;
    public static final int ANIM_GRAB = 3;
    public static final int ATTACK_TICKS = 22;
    public static final int ATTACK_IMPACT = 12;
    public static final int HIT_TICKS = 12;

    private static final int LIFE_TICKS = 260;
    private static final int FLESH_DROP_ODDS = 10;
    private static final int ATTACK_COOLDOWN = 50;
    private static final double ATTACK_RANGE = 3.5;
    private static final float ATTACK_DAMAGE = 2.0F;

    private static final EntityDataAccessor<Integer> GRASP_TARGET =
            SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RETRACT_AT =
            SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_STATE =
            SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_START =
            SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);

    private int lifeTicks = LIFE_TICKS;
    private int attackCooldown = 0;
    private Vec3 anchor;
    private Vec3 holdOffset;

    public TentacleEntity(EntityType<? extends TentacleEntity> type, Level level) {
        super(type, level);
        setSilent(true);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0D).add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GRASP_TARGET, 0);
        builder.define(RETRACT_AT, -1);
        builder.define(ANIM_STATE, ANIM_IDLE);
        builder.define(ANIM_START, 0);
    }

    public int graspTargetId() {
        return entityData.get(GRASP_TARGET);
    }

    public void setGraspTarget(int entityId) {
        entityData.set(GRASP_TARGET, entityId);
    }

    public boolean isGrasping() {
        return graspTargetId() != 0;
    }

    public boolean isRetracting() {
        return entityData.get(RETRACT_AT) >= 0;
    }

    public int animState() {
        return entityData.get(ANIM_STATE);
    }

    /** Ticks since the current animation started, including the partial tick on the client. */
    public float animTicks(float partialTick) {
        return tickCount + partialTick - entityData.get(ANIM_START);
    }

    public void setAnim(int state) {
        entityData.set(ANIM_STATE, state);
        entityData.set(ANIM_START, tickCount);
    }

    /** 0 while rising out of the ground, then 1. Works on both sides from the entity's own age. */
    public float emergeProgress(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / EMERGE_TICKS);
    }

    /** 0 while attached, rising to 1 as it sinks back into the ground. */
    public float retractProgress(float partialTick) {
        if (!isRetracting()) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, (tickCount + partialTick - entityData.get(RETRACT_AT)) / RETRACT_TICKS));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (random.nextInt(FLESH_DROP_ODDS) == 0) {
            spawnAtLocation(ModItems.TENTACLE_FLESH.get());
        }
    }

    /**
     * Keeps the tentacle at its spawn offset from a grabbed player, so it follows them without
     * overlapping. The offset is taken from the first call, which is the spawn position.
     */
    public void holdAt(Vec3 playerPos) {
        if (holdOffset == null) {
            holdOffset = position().subtract(playerPos);
        }
        Vec3 spot = playerPos.add(holdOffset);
        anchor = spot;
        setPos(spot.x, spot.y, spot.z);
    }

    public void release() {
        setGraspTarget(0);
        if (!isRetracting()) {
            entityData.set(RETRACT_AT, tickCount);
            lifeTicks = RETRACT_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            setDeltaMovement(Vec3.ZERO);
            if (anchor == null) {
                anchor = snapToGround();
                setPos(anchor.x, anchor.y, anchor.z);
            }
            if (anchor.distanceToSqr(position()) > 1.0E-6) {
                setPos(anchor.x, anchor.y, anchor.z);
            }
            serverTick();
        }
    }

    private void serverTick() {
        if (!isGrasping() && --lifeTicks <= 0) {
            discard();
            return;
        }
        if (!isAlive()) {
            if (isGrasping()) {
                setGraspTarget(0);
            }
            return;
        }
        if (isRetracting()) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (isGrasping()) {
            if (animState() != ANIM_GRAB) {
                setAnim(ANIM_GRAB);
            }
            return;
        }

        int state = animState();
        int age = tickCount - entityData.get(ANIM_START);
        if (state == ANIM_GRAB) {
            setAnim(ANIM_IDLE);
        } else if (state == ANIM_HIT && age >= HIT_TICKS) {
            setAnim(ANIM_IDLE);
        } else if (state == ANIM_ATTACK) {
            if (age == ATTACK_IMPACT) {
                strike();
            }
            if (age >= ATTACK_TICKS) {
                setAnim(ANIM_IDLE);
            }
        } else if (state == ANIM_IDLE && attackCooldown == 0) {
            Player target = level().getNearestPlayer(this, ATTACK_RANGE);
            if (target != null && !target.isSpectator()) {
                faceTowards(target);
                setAnim(ANIM_ATTACK);
                attackCooldown = ATTACK_COOLDOWN;
            }
        }
    }

    private void strike() {
        Player target = level().getNearestPlayer(this, ATTACK_RANGE + 1.0);
        if (target != null && !target.isSpectator()) {
            target.hurt(damageSources().mobAttack(this), ATTACK_DAMAGE);
        }
    }

    /** Drops the anchor onto the first solid block below, so the tentacle never hangs in the air. */
    private Vec3 snapToGround() {
        BlockPos pos = blockPosition();
        for (int i = 0; i < 12 && level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty(); i++) {
            pos = pos.below();
        }
        return new Vec3(getX(), pos.getY(), getZ());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player)) {
            return false;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && !level().isClientSide) {
            setAnim(ANIM_HIT);
            faceTowards(source.getEntity());
        }
        return hit;
    }

    /** Turns the body, head, and model to face the attacker. Same yaw convention as spawning. */
    private void faceTowards(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LifeTicks", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LifeTicks")) {
            lifeTicks = tag.getInt("LifeTicks");
        }
    }
}
