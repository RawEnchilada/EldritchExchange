package dev.eldritch.exchange;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Eldritch.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TentacleEntity>> TENTACLE =
            ENTITIES.register("tentacle", () -> EntityType.Builder.<TentacleEntity>of(TentacleEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.0F)
                    .clientTrackingRange(8)
                    .build("tentacle"));

    private ModEntities() {
    }
}