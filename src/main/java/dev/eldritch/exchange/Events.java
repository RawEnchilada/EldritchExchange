package dev.eldritch.exchange;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Eldritch.MODID)
public final class Events {

    private Events() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) {
            ValueTable.refresh(server);
        }
        BurnerService.tick(server);
        MadnessService.tick(server);
        MassService.tick(server);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        BurnerService.onPlace(event);
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        MadnessService.onHurt(event);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        MadnessService.onDeath(event);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ChatMemory.record(event.getUsername(), event.getRawText(), event.getPlayer().server.getTickCount());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MadnessService.reset();
        ChatMemory.clear();
    }
}
