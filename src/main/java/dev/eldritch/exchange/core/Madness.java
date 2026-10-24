package dev.eldritch.exchange.core;

public final class Madness {

    public static final int MAX = 100;

    /** Decay interval in ticks: one madness point every 20 seconds. */
    public static final int DECAY_INTERVAL_TICKS = 400;

    /** After a burn or withdrawal, decay is paused for 1 minute. */
    public static final int DECAY_PAUSE_TICKS = 1200;

    /** Starvation grace before the first warning, in ticks (10 minutes). */
    public static final long STARVATION_GRACE_TICKS = 12000L;

    /** Interval between starvation warnings, in ticks (3 minutes). */
    public static final long STARVATION_WARNING_INTERVAL_TICKS = 3600L;

    public enum Tier {
        CALM, WHISPER, CRYPTIC, DEEP
    }

    private Madness() {
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }

    public static int gainForBurn(long essence) {
        return 1 + (int) (essence / 256);
    }

    public static int gainForWithdraw(long cost) {
        return 1 + (int) (cost / 64);
    }

    public static int decay(int madness) {
        return madness > 0 ? madness - 1 : 0;
    }

    /**
     * Whether decay may fire for an account right now. A burn or withdrawal stamps
     * {@code lastGainTick}; decay is denied for {@link #DECAY_PAUSE_TICKS}.
     */
    public static boolean canDecay(long lastGainTick, long now) {
        if (lastGainTick <= 0) {
            return true;
        }
        return now - lastGainTick >= DECAY_PAUSE_TICKS;
    }

    public static Tier tier(int madness) {
        if (madness >= 75) {
            return Tier.DEEP;
        }
        if (madness >= 50) {
            return Tier.CRYPTIC;
        }
        if (madness >= 25) {
            return Tier.WHISPER;
        }
        return Tier.CALM;
    }
}