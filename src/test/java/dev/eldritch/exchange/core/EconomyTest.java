package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyTest {

    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void burnAddsEssenceAndLearnsItem() {
        LedgerData data = new LedgerData();
        boolean isNew = Economy.burn(data, player, "minecraft:iron_ingot", 64, 3);
        assertTrue(isNew);
        assertEquals(192L, data.account(player).essence());
        assertEquals(1, data.account(player).madness());
    }

    @Test
    void secondBurnOfSameItemIsNotNew() {
        LedgerData data = new LedgerData();
        Economy.burn(data, player, "minecraft:iron_ingot", 64, 1);
        assertFalse(Economy.burn(data, player, "minecraft:iron_ingot", 64, 1));
        assertEquals(1, data.learned().size());
    }

    @Test
    void withdrawNeedsEnoughEssence() {
        LedgerData data = new LedgerData();
        data.addEssence(player, 50);
        assertEquals(Economy.Result.NOT_ENOUGH, Economy.withdraw(data, player, 64));
        assertEquals(50L, data.account(player).essence());
    }

    @Test
    void withdrawSpendsEssenceAndRaisesMadness() {
        LedgerData data = new LedgerData();
        data.addEssence(player, 200);
        assertEquals(Economy.Result.OK, Economy.withdraw(data, player, 128));
        assertEquals(72L, data.account(player).essence());
        assertEquals(3, data.account(player).madness());
    }

    @Test
    void madnessNeverExceedsMax() {
        LedgerData data = new LedgerData();
        data.setMadness(player, 99);
        data.addMadness(player, 50);
        assertEquals(Madness.MAX, data.account(player).madness());
    }

    @Test
    void bindingBurnerReplacesOldBinding() {
        LedgerData data = new LedgerData();
        data.bindBurner(player, new LedgerData.Burner(1, 64, 1));
        data.bindBurner(player, new LedgerData.Burner(5, 64, 5));
        assertEquals(new LedgerData.Burner(5, 64, 5), data.burner(player));
        assertEquals(1, data.burners().size());
    }
}
