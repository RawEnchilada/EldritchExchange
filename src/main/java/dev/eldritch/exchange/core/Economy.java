package dev.eldritch.exchange.core;

import java.util.UUID;

public final class Economy {

    public enum Result {
        OK, NOT_ENOUGH
    }

    private Economy() {
    }

    public static Result withdraw(LedgerData data, UUID player, long cost) {
        if (!data.spendEssence(player, cost)) {
            return Result.NOT_ENOUGH;
        }
        data.addMadness(player, Madness.gainForWithdraw(cost));
        return Result.OK;
    }

    public static boolean burn(LedgerData data, UUID owner, String itemId, long value, int count) {
        long essence = value * count;
        data.addEssence(owner, essence);
        data.addMadness(owner, Madness.gainForBurn(essence));
        return data.learn(itemId);
    }
}
