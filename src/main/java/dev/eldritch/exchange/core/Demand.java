package dev.eldritch.exchange.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class Demand {

    public static final int MIN_LEARNED = 6;
    public static final int MIN_MADNESS = 50;
    public static final int MIN_SEALED = 3;
    public static final int MAX_SEALED = 6;

    private Demand() {
    }

    public static boolean eligible(int madness, int learnedCount, boolean hasActiveDemand) {
        return madness >= MIN_MADNESS && learnedCount >= MIN_LEARNED && !hasActiveDemand;
    }

    public static long cooldownTicks(Madness.Tier tier) {
        return switch (tier) {
            case DEEP -> 7200L;   // 6 min
            case CRYPTIC -> 14400L; // 12 min
            default -> 24000L;     // 20 min
        };
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
}