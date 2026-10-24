package dev.eldritch.exchange;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the most recent player chat so madness can replay it later. Only the
 * server uses this; nothing is persisted across restarts.
 */
public final class ChatMemory {

    public record Line(String name, String text, long at) {
    }

    private static final int MAX_LINES = 80;
    private static final Deque<Line> lines = new ArrayDeque<>();

    private ChatMemory() {
    }

    public static void record(String name, String text, long now) {
        if (text == null || text.isBlank()) {
            return;
        }
        lines.addLast(new Line(name, text, now));
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    public static List<Line> snapshot() {
        return new ArrayList<>(lines);
    }

    public static void clear() {
        lines.clear();
    }
}
