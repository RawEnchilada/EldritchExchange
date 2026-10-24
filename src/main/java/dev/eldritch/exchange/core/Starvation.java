package dev.eldritch.exchange.core;

import java.util.Random;

public final class Starvation {

    public static final long GRACE_TICKS = Madness.STARVATION_GRACE_TICKS;
    public static final long WARNING_INTERVAL_TICKS = Madness.STARVATION_WARNING_INTERVAL_TICKS;

    private Starvation() {
    }

    public static boolean shouldWarn(long lastFedTick, long lastWarningTick, long now) {
        if (lastFedTick <= 0) {
            return false;
        }
        long sinceFed = now - lastFedTick;
        if (sinceFed < GRACE_TICKS) {
            return false;
        }
        if (lastWarningTick <= 0) {
            return true;
        }
        return now - lastWarningTick >= WARNING_INTERVAL_TICKS;
    }

    public static int madnessForWarning(int warningIndex) {
        return warningIndex >= 3 ? 2 : 1;
    }

    public static int warningIndex(long lastFedTick, long now) {
        if (lastFedTick <= 0) {
            return 0;
        }
        long sinceFed = now - lastFedTick;
        if (sinceFed < GRACE_TICKS) {
            return 0;
        }
        return (int) ((sinceFed - GRACE_TICKS) / WARNING_INTERVAL_TICKS) + 1;
    }
}