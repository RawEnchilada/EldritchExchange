package dev.eldritch.exchange.core;

public final class Risk {

    public enum Event {
        NONE, LIGHTNING, WHISPERS, SWARM, CRUMBLE
    }

    private Risk() {
    }

    public static double chance(long cost) {
        if (cost >= 4096) {
            return 0.25;
        }
        if (cost >= 1024) {
            return 0.12;
        }
        if (cost >= 256) {
            return 0.05;
        }
        return 0.0;
    }

    public static Event roll(long cost, double chanceRoll, double eventRoll) {
        if (chanceRoll >= chance(cost)) {
            return Event.NONE;
        }
        if (eventRoll < 0.25) {
            return Event.LIGHTNING;
        }
        if (eventRoll < 0.5) {
            return Event.WHISPERS;
        }
        if (eventRoll < 0.75) {
            return Event.SWARM;
        }
        return Event.CRUMBLE;
    }
}
