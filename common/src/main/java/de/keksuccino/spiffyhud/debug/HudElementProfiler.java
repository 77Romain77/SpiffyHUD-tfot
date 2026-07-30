package de.keksuccino.spiffyhud.debug;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates FancyMenu element rendering costs over one-second windows.
 */
public final class HudElementProfiler {

    private static final long SNAPSHOT_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MAX_TOP_TYPES = 4;

    private static final Map<Class<?>, MutableTypeStats> TYPE_STATS = new HashMap<>();

    private static boolean active = false;
    private static long windowStartedAt = System.nanoTime();
    private static long windowHudNanos = 0L;
    private static long windowElementNanos = 0L;
    private static int windowFrames = 0;
    private static Snapshot snapshot = Snapshot.EMPTY;

    private HudElementProfiler() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        reset();
    }

    public static void recordElement(@NotNull AbstractElement element, long elapsedNanos) {
        if (!active || elapsedNanos < 0L) return;

        MutableTypeStats stats = TYPE_STATS.computeIfAbsent(
                element.getClass(),
                ignored -> new MutableTypeStats()
        );
        stats.totalNanos += elapsedNanos;
        stats.calls++;
        windowElementNanos += elapsedNanos;
    }

    public static void recordFrame(long elapsedNanos) {
        if (!active) return;

        windowHudNanos += Math.max(0L, elapsedNanos);
        windowFrames++;

        long now = System.nanoTime();
        if (now - windowStartedAt >= SNAPSHOT_INTERVAL_NANOS) {
            createSnapshot();
            clearWindow(now);
        }
    }

    @NotNull
    public static Snapshot getSnapshot() {
        return snapshot;
    }

    private static void createSnapshot() {
        int frames = Math.max(1, windowFrames);
        double hudMsPerFrame = nanosToMs(windowHudNanos) / frames;
        double elementsMsPerFrame = nanosToMs(windowElementNanos) / frames;
        double otherMsPerFrame = Math.max(0.0D, hudMsPerFrame - elementsMsPerFrame);

        List<Map.Entry<Class<?>, MutableTypeStats>> sorted = new ArrayList<>(TYPE_STATS.entrySet());
        sorted.sort(Comparator.comparingLong(
                (Map.Entry<Class<?>, MutableTypeStats> entry) -> entry.getValue().totalNanos
        ).reversed());

        List<TypeSnapshot> topTypes = new ArrayList<>();
        int limit = Math.min(MAX_TOP_TYPES, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<Class<?>, MutableTypeStats> entry = sorted.get(i);
            MutableTypeStats stats = entry.getValue();
            topTypes.add(new TypeSnapshot(
                    formatTypeName(entry.getKey()),
                    nanosToMs(stats.totalNanos) / frames,
                    Math.max(1, Math.round((float) stats.calls / frames))
            ));
        }

        snapshot = new Snapshot(elementsMsPerFrame, otherMsPerFrame, List.copyOf(topTypes));
    }

    private static void reset() {
        snapshot = Snapshot.EMPTY;
        clearWindow(System.nanoTime());
    }

    private static void clearWindow(long now) {
        TYPE_STATS.clear();
        windowHudNanos = 0L;
        windowElementNanos = 0L;
        windowFrames = 0;
        windowStartedAt = now;
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0D;
    }

    @NotNull
    private static String formatTypeName(@NotNull Class<?> type) {
        String name = type.getSimpleName();
        if (name.endsWith("Element")) {
            name = name.substring(0, name.length() - "Element".length());
        }
        if (name.length() > 24) {
            name = name.substring(0, 23) + "~";
        }
        return name.isBlank() ? "Element inconnu" : name;
    }

    private static final class MutableTypeStats {
        private long totalNanos = 0L;
        private int calls = 0;
    }

    public static final class Snapshot {

        private static final Snapshot EMPTY = new Snapshot(0.0D, 0.0D, List.of());

        private final double elementsMsPerFrame;
        private final double otherMsPerFrame;
        @NotNull
        private final List<TypeSnapshot> topTypes;

        private Snapshot(
                double elementsMsPerFrame,
                double otherMsPerFrame,
                @NotNull List<TypeSnapshot> topTypes
        ) {
            this.elementsMsPerFrame = elementsMsPerFrame;
            this.otherMsPerFrame = otherMsPerFrame;
            this.topTypes = topTypes;
        }

        public double getElementsMsPerFrame() {
            return elementsMsPerFrame;
        }

        public double getOtherMsPerFrame() {
            return otherMsPerFrame;
        }

        @NotNull
        public List<TypeSnapshot> getTopTypes() {
            return topTypes;
        }
    }

    public static final class TypeSnapshot {

        @NotNull
        private final String name;
        private final double msPerFrame;
        private final int callsPerFrame;

        private TypeSnapshot(@NotNull String name, double msPerFrame, int callsPerFrame) {
            this.name = name;
            this.msPerFrame = msPerFrame;
            this.callsPerFrame = callsPerFrame;
        }

        @NotNull
        public String getName() {
            return name;
        }

        public double getMsPerFrame() {
            return msPerFrame;
        }

        public int getCallsPerFrame() {
            return callsPerFrame;
        }
    }
}
