package dev.eldritch.exchange;

import dev.eldritch.exchange.core.LedgerData;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class Thalggor {

    public static final String HIDDEN_NAME = "The Bound Thing";
    public static final String REVEALED_NAME = "Thal'ggor";

    private Thalggor() {
    }

    public static boolean revealed(MinecraftServer server, UUID player) {
        return server != null && Ledger.get(server).data.account(player).nameRevealed();
    }

    public static String name(MinecraftServer server, UUID player) {
        return revealed(server, player) ? REVEALED_NAME : HIDDEN_NAME;
    }

    public static Component nameComponent(MinecraftServer server, UUID player) {
        return Component.literal(name(server, player));
    }
}