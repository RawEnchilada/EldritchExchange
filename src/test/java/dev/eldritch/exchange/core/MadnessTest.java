package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MadnessTest {

    @Test
    void tiersMatchThresholds() {
        assertEquals(Madness.Tier.CALM, Madness.tier(0));
        assertEquals(Madness.Tier.CALM, Madness.tier(24));
        assertEquals(Madness.Tier.WHISPER, Madness.tier(25));
        assertEquals(Madness.Tier.CRYPTIC, Madness.tier(50));
        assertEquals(Madness.Tier.DEEP, Madness.tier(75));
        assertEquals(Madness.Tier.DEEP, Madness.tier(100));
    }

    @Test
    void clampKeepsMeterInRange() {
        assertEquals(0, Madness.clamp(-5));
        assertEquals(100, Madness.clamp(250));
        assertEquals(42, Madness.clamp(42));
    }

    @Test
    void burnGainGrowsWithEssence() {
        assertEquals(1, Madness.gainForBurn(0));
        assertEquals(2, Madness.gainForBurn(256));
    }

    @Test
    void withdrawGainGrowsWithCost() {
        assertEquals(1, Madness.gainForWithdraw(0));
        assertEquals(3, Madness.gainForWithdraw(128));
    }

    @Test
    void decayStopsAtZero() {
        assertEquals(4, Madness.decay(5));
        assertEquals(0, Madness.decay(0));
    }
}
