package dev.eldritch.exchange.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Demand {

    public static final int MIN_LEARNED = 6;
    public static final int MIN_MADNESS = 50;
    public static final int MIN_SEALED = 3;
    public static final int MAX_SEALED = 6;

    /** Every demand expires after 20 minutes. */
    public static final long TIMEOUT_TICKS = 24000L;
    /** Favour at or above this reveals the entity's name. */
    public static final int NAME_FAVOUR = 3;
    /** Favour at or below this sometimes swaps Codex withdrawals for junk food. */
    public static final int LOW_FAVOUR = -2;
    public static final double SUBSTITUTE_CHANCE = 0.3;
    /** Chance that an item demand picks from the whole registry instead of the Codex. */
    public static final double OFF_CODEX_CHANCE = 0.40;

    public enum Kind {
        ITEM, ANIMAL, SELF, OTHER
    }

    private Demand() {
    }

    public static boolean eligible(int madness, int learnedCount, boolean hasActiveDemand) {
        return madness >= MIN_MADNESS && learnedCount >= MIN_LEARNED && !hasActiveDemand;
    }

    /** Animal sacrifice is most common, the player's own death less so, another player's death rare. */
    public static Kind pickKind(Random random) {
        int roll = random.nextInt(100);
        if (roll < 55) {
            return Kind.ITEM;
        }
        if (roll < 80) {
            return Kind.ANIMAL;
        }
        if (roll < 92) {
            return Kind.SELF;
        }
        return Kind.OTHER;
    }

    public static int countFor(Madness.Tier tier, Random random) {
        int base = tier == Madness.Tier.DEEP ? 2 : 1;
        return base + random.nextInt(2); // 1-3
    }

    /**
     * Builds the sealed set: the demanded item plus 3-5 other learned items. The
     * demanded item is always included so it cannot be withdrawn and re-burned.
     */
    public static List<String> buildSealed(String demanded, List<String> learned, Random random) {
        List<String> pool = new ArrayList<>(learned);
        pool.remove(demanded);
        Collections.shuffle(pool, random);
        int extra = MIN_SEALED - 1 + random.nextInt(MAX_SEALED - MIN_SEALED + 1);
        extra = Math.min(extra, pool.size());
        List<String> sealed = new ArrayList<>();
        sealed.add(demanded);
        for (int i = 0; i < extra && i < pool.size(); i++) {
            sealed.add(pool.get(i));
        }
        return sealed;
    }

    /** Sealed set for a non-item demand: 3-6 learned items, none of which is demanded. */
    public static List<String> pickBlocked(List<String> learned, Random random) {
        List<String> pool = new ArrayList<>(learned);
        Collections.shuffle(pool, random);
        int count = MIN_SEALED + random.nextInt(MAX_SEALED - MIN_SEALED + 1);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    public static String pickDemanded(List<String> learned, List<String> currentlySealed, Random random) {
        List<String> candidates = new ArrayList<>();
        for (String id : learned) {
            if (!currentlySealed.contains(id)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Picks the requested item. Mostly from the Codex, so it is always reachable, but sometimes
     * from anything in the registry. Returns null if neither pool has an option.
     */
    public static String pickItem(List<String> learned, List<String> anyItem, List<String> currentlySealed, Random random) {
        boolean offCodex = !anyItem.isEmpty() && (learned.isEmpty() || random.nextDouble() < OFF_CODEX_CHANCE);
        String picked = pickDemanded(offCodex ? anyItem : learned, currentlySealed, random);
        if (picked == null) {
            picked = pickDemanded(learned, currentlySealed, random);
        }
        return picked;
    }
}
