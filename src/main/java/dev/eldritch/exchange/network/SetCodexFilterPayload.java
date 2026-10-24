package dev.eldritch.exchange.network;

import dev.eldritch.exchange.CodexMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetCodexFilterPayload(int containerId, String query, int sortMode) implements CustomPacketPayload {

    public static final Type<SetCodexFilterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("eldritch", "set_codex_filter"));

    public static final StreamCodec<ByteBuf, SetCodexFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetCodexFilterPayload::containerId,
            ByteBufCodecs.STRING_UTF8, SetCodexFilterPayload::query,
            ByteBufCodecs.VAR_INT, SetCodexFilterPayload::sortMode,
            SetCodexFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetCodexFilterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.containerMenu instanceof CodexMenu menu && player.containerMenu.containerId == payload.containerId()) {
            menu.applyFilter(player, payload.query(), payload.sortMode());
        }
    }
}