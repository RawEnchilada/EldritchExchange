package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FavourTest {

    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void favourIsClampedToTheProgressionRange() {
        LedgerData.Account account = new LedgerData().account(player);
        account.addFavour(50);
        assertEquals(10, account.favour());
        account.addFavour(-50);
        assertEquals(-10, account.favour());
        account.setFavour(99);
        assertEquals(10, account.favour());
    }

    @Test
    void highFavourRevealsTheNameAndTheRevealStaysAfterFavourDrops() {
        LedgerData.Account account = new LedgerData().account(player);
        account.addFavour(Demand.NAME_FAVOUR - 1);
        assertFalse(account.nameRevealed());
        account.addFavour(1);
        assertTrue(account.nameRevealed());
        account.addFavour(-10);
        assertTrue(account.nameRevealed());
    }

    @Test
    void forgetKeepsEachItemOnce() {
        LedgerData.Account account = new LedgerData().account(player);
        account.forget(List.of("codex:a", "codex:b"));
        account.forget(List.of("codex:b", "codex:c"));
        assertTrue(account.isForgotten("codex:a"));
        assertTrue(account.isForgotten("codex:c"));
        assertEquals(3, account.forgotten().size());
        assertFalse(account.isForgotten("codex:d"));
    }

    @Test
    void forgettingIsPerPlayer() {
        LedgerData data = new LedgerData();
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000003");
        data.account(player).forget(List.of("codex:a"));
        assertFalse(data.account(other).isForgotten("codex:a"));
    }

    @Test
    void issuingAnAnimalDemandHasNoItemAndClearingResetsKind() {
        LedgerData.Account account = new LedgerData().account(player);
        account.issueDemand(Demand.Kind.ANIMAL, null, 1, 0L, Demand.TIMEOUT_TICKS, List.of("codex:a", "codex:b", "codex:c"));
        assertEquals(Demand.Kind.ANIMAL, account.demandKind());
        assertNull(account.demandItemId());
        assertEquals(3, account.sealed().size());
        assertTrue(account.isSealed("codex:b"));
        account.clearDemand();
        assertNull(account.demandKind());
        assertEquals("NONE", account.demandState());
        assertTrue(account.sealed().isEmpty());
    }

    @Test
    void legacyItemDemandIssueDefaultsToItemKind() {
        LedgerData.Account account = new LedgerData().account(player);
        account.issueDemand("codex:iron", 2, 0L, 100L, List.of("codex:iron"));
        assertEquals(Demand.Kind.ITEM, account.demandKind());
        assertEquals("codex:iron", account.demandItemId());
    }
}
