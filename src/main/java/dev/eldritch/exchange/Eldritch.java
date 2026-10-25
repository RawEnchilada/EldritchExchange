package dev.eldritch.exchange;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Eldritch.MODID)
public final class Eldritch {

    public static final String MODID = "eldritch_exchange";

    public Eldritch(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, EldritchConfig.SPEC);
    }
}
