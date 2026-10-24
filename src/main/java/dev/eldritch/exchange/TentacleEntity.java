package dev.eldritch.exchange;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class TentacleEntity extends Mob {

    private static final EntityDataAccessor<Integer> GRASP_TARGET =
            SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);

    private int lifeTicks = 240;
    private int graspHits = 0;

    public TentacleEntity(EntityType<? extends TentacleEntity> type, Level level) {
        super(type, level);
        setSilent(true);
        setNoAi(true);
        setInvulnerable(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0D).add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GRASP_TARGET, 0);
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

    public void registerHit() {
        graspHits++;
        if (graspHits >= 3) {
            release();
        }
    }

    public void release() {
        setGraspTarget(0);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (--lifeTicks <= 0) {
                discard();
                return;
            }
            if (isGrasping()) {
                // Grasp logic handled by service; entity just counts down.
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player) {
            registerHit();
            return true;
        }
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