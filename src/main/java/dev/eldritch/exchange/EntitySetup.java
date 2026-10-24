package dev.eldritch.exchange;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = Eldritch.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EntitySetup {

    private EntitySetup() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TENTACLE.get(), TentacleEntity.createAttributes().build());
    }
}