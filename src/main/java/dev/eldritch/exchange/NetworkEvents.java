package dev.eldritch.exchange;

import dev.eldritch.exchange.network.SetCodexFilterPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Eldritch.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkEvents {

    private NetworkEvents() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playBidirectional(SetCodexFilterPayload.TYPE, SetCodexFilterPayload.STREAM_CODEC,
                SetCodexFilterPayload::handle);
    }
}