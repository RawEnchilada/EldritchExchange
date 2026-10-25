package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DemandTest {

    private static List<String> ids(String prefix, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(prefix + i);
        }
        return ids;
    }

    @Test
    void timeoutIsTwentyMinutes() {
        assertEquals(20 * 60 * 20L, Demand.TIMEOUT_TICKS);
    }

    @Test
    void eligibilityNeedsMadnessLearnedAndNoActiveDemand() {
        assertTrue(Demand.eligible(50, 6, false));
        assertFalse(Demand.eligible(49, 6, false));
        assertFalse(Demand.eligible(50, 5, false));
        assertFalse(Demand.eligible(50, 6, true));
    }

    @Test
    void kindsAreWeightedTowardItemsAndAnimals() {
        Random random = new Random(42);
        Map<Demand.Kind, Integer> counts = new EnumMap<>(Demand.Kind.class);
        int trials = 20000;
        for (int i = 0; i < trials; i++) {
            counts.merge(Demand.pickKind(random), 1, Integer::sum);
        }
        assertEquals(4, counts.size(), "every kind should be reachable");
        assertTrue(counts.get(Demand.Kind.ITEM) > counts.get(Demand.Kind.ANIMAL));
        assertTrue(counts.get(Demand.Kind.ANIMAL) > counts.get(Demand.Kind.SELF));
        assertTrue(counts.get(Demand.Kind.SELF) > counts.get(Demand.Kind.OTHER));
        assertEquals(0.55, counts.get(Demand.Kind.ITEM) / (double) trials, 0.02);
    }

    @Test
    void itemPickUsesRegistryAboutFortyPercentOfTheTime() {
        List<String> learned = ids("codex:", 10);
        List<String> anyItem = ids("registry:", 500);
        Random random = new Random(7);
        int offCodex = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            if (Demand.pickItem(learned, anyItem, List.of(), random).startsWith("registry:")) {
                offCodex++;
            }
        }
        assertEquals(Demand.OFF_CODEX_CHANCE, offCodex / (double) trials, 0.03);
    }

    @Test
    void itemPickFallsBackToCodexWhenRegistryIsEmpty() {
        String picked = Demand.pickItem(ids("codex:", 3), List.of(), List.of(), new Random(1));
        assertTrue(picked.startsWith("codex:"));
    }

    @Test
    void itemPickSkipsSealedItemsAndReturnsNullWhenNothingIsLeft() {
        List<String> learned = ids("codex:", 2);
        assertNull(Demand.pickItem(learned, List.of(), learned, new Random(1)));
    }

    @Test
    void blockedSetHasThreeToSixDistinctLearnedItems() {
        List<String> learned = ids("codex:", 20);
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            List<String> blocked = Demand.pickBlocked(learned, random);
            assertTrue(blocked.size() >= Demand.MIN_SEALED && blocked.size() <= Demand.MAX_SEALED);
            assertEquals(blocked.size(), blocked.stream().distinct().count());
            assertTrue(learned.containsAll(blocked));
        }
    }

    @Test
    void blockedSetIsCappedByLearnedCount() {
        assertEquals(2, Demand.pickBlocked(ids("codex:", 2), new Random(1)).size());
    }

    @Test
    void itemBlockedSetAlwaysContainsTheDemandedItem() {
        List<String> learned = ids("codex:", 8);
        for (int seed = 0; seed < 50; seed++) {
            List<String> sealed = Demand.buildSealed("codex:3", learned, new Random(seed));
            assertTrue(sealed.contains("codex:3"));
            assertTrue(sealed.size() >= Demand.MIN_SEALED && sealed.size() <= Demand.MAX_SEALED);
        }
    }

    @Test
    void countForDeepIsAtLeastOneAndAtMostThree() {
        Random random = new Random(5);
        for (int i = 0; i < 100; i++) {
            int count = Demand.countFor(Madness.Tier.DEEP, random);
            assertTrue(count >= 2 && count <= 3);
            assertTrue(Demand.countFor(Madness.Tier.CRYPTIC, random) <= 2);
        }
    }
}
