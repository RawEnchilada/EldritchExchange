package dev.eldritch.exchange;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.eldritch.exchange.core.Demand;
import java.util.List;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Eldritch.MODID)
public final class AdminCommands {

    private static final SuggestionProvider<CommandSourceStack> TRICKS =
            (context, builder) -> SharedSuggestionProvider.suggest(MadnessService.trickNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> DEMAND_KINDS =
            (context, builder) -> SharedSuggestionProvider.suggest(List.of("item", "animal", "self", "other"), builder);

    private AdminCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eldritch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("trick")
                        .then(Commands.literal("list")
                                .executes(AdminCommands::listTricks))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TRICKS)
                                .executes(context -> trick(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> trick(context, EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("grab")
                        .executes(context -> grab(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> grab(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("tentacle")
                        .executes(context -> harass(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> harass(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("demand")
                        .executes(context -> demand(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> demand(context, EntityArgument.getPlayer(context, "player")))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(DEMAND_KINDS)
                                        .executes(AdminCommands::demandKind))))
                .then(Commands.literal("favour")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-10, 10))
                                        .executes(AdminCommands::setFavour))))
                .then(Commands.literal("madness")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(AdminCommands::setMadness)))));
    }

    private static int listTricks(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        for (String line : MadnessService.describeTricks()) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int trick(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String name = StringArgumentType.getString(context, "name");
        if (!MadnessService.runTrick(player, name)) {
            context.getSource().sendFailure(Component.literal("Unknown trick '" + name + "'. Use /eldritch trick list."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Ran " + name + " on " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int grab(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player) {
        MadnessService.adminGrab(player);
        context.getSource().sendSuccess(() -> Component.literal("Tentacle grab on " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int harass(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player) {
        MadnessService.adminHarass(player);
        context.getSource().sendSuccess(() -> Component.literal("Tentacle spawned near " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int demand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player) {
        return issueDemand(context, player, null);
    }

    private static int demandKind(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String word = StringArgumentType.getString(context, "kind");
        try {
            return issueDemand(context, player, Demand.Kind.valueOf(word.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Unknown kind '" + word + "'. Use item, animal, self or other."));
            return 0;
        }
    }

    private static int issueDemand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                   ServerPlayer player, Demand.Kind kind) {
        if (!MadnessService.adminDemand(player, kind)) {
            context.getSource().sendFailure(Component.literal("Nothing to demand yet (needs learned items)."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Demand issued to " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int setFavour(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        Ledger ledger = Ledger.get(context.getSource().getServer());
        ledger.data.account(player.getUUID()).setFavour(value);
        ledger.setDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "Favour of " + player.getGameProfile().getName() + " set to " + value), true);
        return 1;
    }

    private static int setMadness(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        Ledger ledger = Ledger.get(context.getSource().getServer());
        ledger.data.setMadness(player.getUUID(), value);
        ledger.setDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "Madness of " + player.getGameProfile().getName() + " set to " + value), true);
        return 1;
    }
}
