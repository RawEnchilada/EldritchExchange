package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RiskTest {

    @Test
    void cheapItemsAreSafe() {
        assertEquals(0.0, Risk.chance(255));
        assertEquals(Risk.Event.NONE, Risk.roll(255, 0.0, 0.0));
    }

    @Test
    void chanceRisesWithValue() {
        assertEquals(0.05, Risk.chance(256));
        assertEquals(0.12, Risk.chance(1024));
        assertEquals(0.25, Risk.chance(4096));
    }

    @Test
    void chanceRollBelowThresholdTriggersEvent() {
        assertEquals(Risk.Event.LIGHTNING, Risk.roll(256, 0.01, 0.1));
        assertEquals(Risk.Event.WHISPERS, Risk.roll(256, 0.01, 0.3));
        assertEquals(Risk.Event.SWARM, Risk.roll(256, 0.01, 0.6));
        assertEquals(Risk.Event.CRUMBLE, Risk.roll(256, 0.01, 0.9));
    }

    @Test
    void chanceRollAboveThresholdIsSafe() {
        assertEquals(Risk.Event.NONE, Risk.roll(256, 0.5, 0.1));
    }
}
