package de.keksuccino.spiffyhud.util.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Lightweight internal profiler for the SpiffyHUD overlay.
 *
 * <p>Activation is intentionally hidden to avoid accidental use by players:
 * hold Ctrl + Shift + F10 for five seconds. Once enabled, pressing the same
 * three-key combination once disables it immediately. No chat or action-bar
 * message is emitted.</p>
 */
public final class SpiffyProfiler {

    private static final long HOLD_TO_ENABLE_NANOS = 5_000_000_000L;
    private static final int SAMPLE_WINDOW = 120;

    private static final long[] RENDER_SAMPLES = new long[SAMPLE_WINDOW];
    private static final long[] TICK_SAMPLES = new long[SAMPLE_WINDOW];

    private static int renderSampleIndex;
    private static int renderSampleCount;
    private static int tickSampleIndex;
    private static int tickSampleCount;

    private static boolean enabled;
    private static boolean waitingForComboRelease;
    private static boolean comboWasDown;
    private static long holdStartedAtNanos = -1L;

    private SpiffyProfiler() {
    }

    /**
     * Polls the hidden profiler shortcut. This must run once per client tick.
     */
    public static void tickShortcut() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) return;

        long window = minecraft.getWindow().getWindow();
        boolean controlDown = isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftDown = isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean comboDown = controlDown
                && shiftDown
                && isKeyDown(window, GLFW.GLFW_KEY_F10);

        if (waitingForComboRelease) {
            if (!comboDown) {
                waitingForComboRelease = false;
                comboWasDown = false;
            }
            return;
        }

        if (enabled) {
            if (comboDown && !comboWasDown) {
                enabled = false;
                waitingForComboRelease = true;
                resetSamples();
            }
            comboWasDown = comboDown;
            return;
        }

        if (comboDown) {
            long now = System.nanoTime();
            if (holdStartedAtNanos < 0L) {
                holdStartedAtNanos = now;
            } else if (now - holdStartedAtNanos >= HOLD_TO_ENABLE_NANOS) {
                enabled = true;
                holdStartedAtNanos = -1L;
                waitingForComboRelease = true;
                resetSamples();
            }
        } else {
            holdStartedAtNanos = -1L;
        }

        comboWasDown = comboDown;
    }

    public static long beginRenderSample() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void endRenderSample(long startedAtNanos) {
        if (!enabled || startedAtNanos == 0L) return;
        addRenderSample(System.nanoTime() - startedAtNanos);
    }

    public static long beginTickSample() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void endTickSample(long startedAtNanos) {
        if (!enabled || startedAtNanos == 0L) return;
        addTickSample(System.nanoTime() - startedAtNanos);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Renders profiler statistics directly, outside FancyMenu's layout system.
     */
    public static void render(@NotNull GuiGraphics graphics) {
        if (!enabled) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        String[] lines = new String[] {
                "SpiffyHUD profiler",
                String.format(Locale.ROOT, "Render avg: %.3f ms", averageMillis(RENDER_SAMPLES, renderSampleCount)),
                String.format(Locale.ROOT, "Render max: %.3f ms", maxMillis(RENDER_SAMPLES, renderSampleCount)),
                String.format(Locale.ROOT, "Tick avg:   %.3f ms", averageMillis(TICK_SAMPLES, tickSampleCount)),
                String.format(Locale.ROOT, "Tick max:   %.3f ms", maxMillis(TICK_SAMPLES, tickSampleCount))
        };

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int x = minecraft.getWindow().getGuiScaledWidth() - textWidth - 12;
        int y = 8;
        int lineHeight = 10;
        int panelBottom = y + lines.length * lineHeight + 3;

        graphics.fill(x - 4, y - 4, x + textWidth + 4, panelBottom, 0xB0000000);
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? 0xFFFFFFFF : 0xFFE0E0E0;
            graphics.drawString(font, lines[i], x, y + i * lineHeight, color, false);
        }
    }

    private static boolean isKeyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static void addRenderSample(long nanos) {
        RENDER_SAMPLES[renderSampleIndex] = Math.max(0L, nanos);
        renderSampleIndex = (renderSampleIndex + 1) % SAMPLE_WINDOW;
        renderSampleCount = Math.min(renderSampleCount + 1, SAMPLE_WINDOW);
    }

    private static void addTickSample(long nanos) {
        TICK_SAMPLES[tickSampleIndex] = Math.max(0L, nanos);
        tickSampleIndex = (tickSampleIndex + 1) % SAMPLE_WINDOW;
        tickSampleCount = Math.min(tickSampleCount + 1, SAMPLE_WINDOW);
    }

    private static double averageMillis(long[] samples, int count) {
        if (count <= 0) return 0.0D;
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += samples[i];
        }
        return total / (double) count / 1_000_000.0D;
    }

    private static double maxMillis(long[] samples, int count) {
        long max = 0L;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samples[i]);
        }
        return max / 1_000_000.0D;
    }

    private static void resetSamples() {
        renderSampleIndex = 0;
        renderSampleCount = 0;
        tickSampleIndex = 0;
        tickSampleCount = 0;
    }
}
