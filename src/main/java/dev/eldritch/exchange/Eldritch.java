package dev.eldritch.exchange;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Eldritch.MODID)
public final class Eldritch {

    public static final String MODID = "eldritch";

    public Eldritch(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
    }
}
