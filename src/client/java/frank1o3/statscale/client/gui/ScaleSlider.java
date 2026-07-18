package frank1o3.statscale.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A generic, reusable floating-point slider widget targeting Minecraft 26.2 /
 * Fabric.
 *
 * <p>
 * Uses {@link GuiGraphicsExtractor} and the event-based input API
 * ({@link MouseButtonEvent},
 * {@link KeyEvent}) introduced in Minecraft 26.x, matching the patterns used by
 * {@code WildfireSlider} in the Wildfire mod.
 *
 * <p>
 * The widget knows nothing about game-specific logic. Construct via the fluent
 * {@link Builder}:
 * 
 * <pre>{@code
 * ScaleSlider slider = ScaleSlider.builder()
 *         .bounds(x, y, 200, 20)
 *         .range(0.1, 16.0)
 *         .step(0.1)
 *         .initialValue(1.0)
 *         .label(Component.literal("Scale"))
 *         .formatter(v -> Component.literal(String.format("%.1fx", v)))
 *         .onValueChanged(v -> {
 *         })
 *         .onValueCommitted(v -> {
 *         })
 *         .build();
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public final class ScaleSlider extends AbstractWidget {

    // -------------------------------------------------------------------------
    // Visual constants
    // -------------------------------------------------------------------------

    /** ARGB colour of the track background. */
    private static final int COLOR_TRACK_BG = 0x88_222222;
    /** ARGB colour of the filled track portion (normal). */
    private static final int COLOR_FILL_ACTIVE = 0xB4_222266;
    /** ARGB colour of the filled track portion when disabled. */
    private static final int COLOR_FILL_DISABLED = 0xB4_111133;
    /** ARGB colour of the thumb indicator. */
    private static final int COLOR_THUMB = 0x78_FFFFFF;
    /**
     * Text colour when hovered, focused, or value has changed since last commit.
     */
    private static final int COLOR_TEXT_HIGHLIGHT = 0xFF_FFFF55;
    /** Default text colour. */
    private static final int COLOR_TEXT_NORMAL = 0xFF_FFFFFF;
    /** Text colour when disabled. */
    private static final int COLOR_TEXT_DISABLED = 0xFF_666666;

    /**
     * Inset from the widget left/right edges used to define the draggable track
     * area.
     * Matches WildfireSlider's convention of 4 px each side.
     */
    private static final int TRACK_INSET = 4;
    /** Half-width of the thumb rectangle in pixels. */
    private static final int THUMB_HALF_W = 1;
    /** Vertical inset applied to the filled area and thumb (1 px each side). */
    private static final int FILL_V_INSET = 1;

    // -------------------------------------------------------------------------
    // Configuration (set once at construction time)
    // -------------------------------------------------------------------------

    private final double min;
    private final double max;
    private final double step;
    private final Component label;
    private final Function<Double, Component> formatter;
    private final Consumer<Double> onValueChanged;
    private final Consumer<Double> onValueCommitted;

    // -------------------------------------------------------------------------
    // Mutable state
    // -------------------------------------------------------------------------

    /**
     * Normalised position in {@code [0, 1]}.
     * The logical value is derived on demand; this is the only mutable field
     * for the slider position, matching WildfireSlider's single-field design.
     */
    private double normalized;

    /**
     * True after the first {@link #onValueChanged} call that produced a different
     * value from the initial one; cleared when the value is committed.
     * Drives the text highlight colour to signal unsaved changes.
     */
    private boolean hasUncommittedChange;

    // -------------------------------------------------------------------------
    // Constructor (private – use Builder)
    // -------------------------------------------------------------------------

    private ScaleSlider(Builder b) {
        super(b.x, b.y, b.width, b.height, Component.empty());

        this.min = b.min;
        this.max = b.max;
        this.step = b.step;
        this.label = b.label;
        this.formatter = b.formatter != null ? b.formatter
                : v -> Component.literal(String.format("%.2f", v));
        this.onValueChanged = b.onValueChanged != null ? b.onValueChanged : v -> {
        };
        this.onValueCommitted = b.onValueCommitted != null ? b.onValueCommitted : v -> {
        };

        // Set initial normalised position (clamped + snapped).
        setNormalized(toNormalized(snapToStep(Mth.clamp(b.initialValue, min, max))));
        // Synchronise the widget message so narration works from the start.
        refreshMessage();
    }

    // =========================================================================
    // Public builder entry-point
    // =========================================================================

    /** Returns a new {@link Builder} for constructing a {@link ScaleSlider}. */
    public static Builder builder() {
        return new Builder();
    }

    // =========================================================================
    // Value API
    // =========================================================================

    /**
     * Returns the current logical value, snapped and clamped to {@code [min, max]}.
     */
    public double getValue() {
        return snapToStep(toLogical(normalized));
    }

    /**
     * Sets the slider to the given logical value.
     * The value is clamped and snapped automatically.
     */
    public void setValue(double value) {
        setNormalized(toNormalized(snapToStep(Mth.clamp(value, min, max))));
        refreshMessage();
    }

    /**
     * Returns the current normalised position in {@code [0, 1]}.
     */
    public double getNormalized() {
        return normalized;
    }

    /**
     * Sets the slider from a normalised {@code [0, 1]} value.
     * The underlying logical value is snapped to the step grid.
     */
    public void setNormalized(double norm) {
        double clamped = Mth.clamp(norm, 0.0, 1.0);
        this.normalized = toNormalized(snapToStep(toLogical(clamped)));
    }

    // =========================================================================
    // Rendering (MC 26.2 – uses extractWidgetRenderState + GuiGraphicsExtractor)
    // =========================================================================

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }

        Font font = Minecraft.getInstance().font;

        // 1. Track background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_TRACK_BG);

        // 2. Filled portion
        int fillRight = getX() + TRACK_INSET + (int) (normalized * (width - TRACK_INSET));
        graphics.fill(
                getX() + FILL_V_INSET, getY() + FILL_V_INSET,
                fillRight - FILL_V_INSET, getY() + height - FILL_V_INSET,
                active ? COLOR_FILL_ACTIVE : COLOR_FILL_DISABLED);

        // 3. Thumb (only when active so the disabled state looks flat)
        if (active) {
            int thumbCx = getX() + TRACK_INSET + (int) (normalized * (width - TRACK_INSET * 2));
            graphics.fill(
                    thumbCx - THUMB_HALF_W, getY() + FILL_V_INSET,
                    thumbCx + THUMB_HALF_W, getY() + height - FILL_V_INSET,
                    COLOR_THUMB);
        }

        // 4. Label text centred in the track
        int textColor = resolveTextColor();
        int textLeft = getX() + TRACK_INSET;
        int textRight = getX() + width - TRACK_INSET;
        drawCentredScrollableText(graphics, font, getMessage(), textLeft, textRight, textColor);
    }

    // =========================================================================
    // Mouse input (MC 26.2 event-based API)
    // =========================================================================

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        applyMouseX(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        applyMouseX(event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        commit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!active || !isHovered()) {
            return false;
        }
        nudge(Math.signum(vertical));
        commit();
        return true;
    }

    // =========================================================================
    // Keyboard input (MC 26.2 event-based API)
    // =========================================================================

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT) {
            nudge(-1.0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            nudge(1.0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            applyNormAndNotify(0.0);
            commit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            applyNormAndNotify(1.0);
            commit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            commit();
            return true;
        }
        return super.keyReleased(event);
    }

    // =========================================================================
    // Narration
    // =========================================================================

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.slider", getMessage());
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
        if (active) {
            if (isFocused()) {
                output.add(NarratedElementType.USAGE,
                        Component.translatable("narration.slider.usage.focused"));
            } else {
                output.add(NarratedElementType.USAGE,
                        Component.translatable("narration.slider.usage.hovered"));
            }
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Translates a raw mouse X coordinate into a normalised slider position,
     * snaps it, fires {@link #onValueChanged}, and refreshes the label.
     */
    private void applyMouseX(double mouseX) {
        double trackWidth = width - TRACK_INSET * 2;
        if (trackWidth <= 0) {
            return;
        }
        double raw = (mouseX - (getX() + TRACK_INSET)) / trackWidth;
        applyNormAndNotify(raw);
    }

    /**
     * Moves the slider by {@code direction} discrete steps, fires
     * {@link #onValueChanged}, and refreshes the label.
     *
     * @param direction positive to increase, negative to decrease
     */
    private void nudge(double direction) {
        // Work in normalised space to avoid float precision issues when step is small.
        double normStep = step / (max - min);
        applyNormAndNotify(normalized + direction * normStep);
    }

    /**
     * Clamps + snaps {@code norm} into the slider position, fires
     * {@link #onValueChanged}, and refreshes the label.
     */
    private void applyNormAndNotify(double norm) {
        setNormalized(norm);
        hasUncommittedChange = true;
        refreshMessage();
        onValueChanged.accept(getValue());
    }

    /**
     * Fires {@link #onValueCommitted} and clears the uncommitted-change flag.
     */
    private void commit() {
        hasUncommittedChange = false;
        onValueCommitted.accept(getValue());
    }

    /**
     * Rebuilds the widget's internal message component from the formatter + label.
     */
    private void refreshMessage() {
        Component formatted = formatter.apply(getValue());
        if (label != null) {
            setMessage(Component.empty().copy().append(label).append(": ").append(formatted));
        } else {
            setMessage(formatted);
        }
    }

    /** Chooses the correct text colour given the current widget state. */
    private int resolveTextColor() {
        if (!active) {
            return COLOR_TEXT_DISABLED;
        }
        if ((isHoveredOrFocused()) || hasUncommittedChange) {
            return COLOR_TEXT_HIGHLIGHT;
        }
        return COLOR_TEXT_NORMAL;
    }

    // -------------------------------------------------------------------------
    // Value conversion helpers
    // -------------------------------------------------------------------------

    private double toLogical(double norm) {
        return Mth.lerp(norm, min, max);
    }

    private double toNormalized(double logical) {
        double range = max - min;
        return range == 0.0 ? 0.0 : Mth.clamp((logical - min) / range, 0.0, 1.0);
    }

    private double snapToStep(double value) {
        if (step <= 0.0) {
            return Mth.clamp(value, min, max);
        }
        double steps = Math.round((value - min) / step);
        return Mth.clamp(min + steps * step, min, max);
    }

    // -------------------------------------------------------------------------
    // Text rendering helper
    // -------------------------------------------------------------------------

    /**
     * Draws text centred between {@code left} and {@code right}, with horizontal
     * scrolling if the text is wider than the available space.
     * This replicates the pattern used in WildfireSlider / GuiUtils without
     * importing Wildfire classes directly.
     */
    private void drawCentredScrollableText(
            GuiGraphicsExtractor graphics, Font font, Component text,
            int left, int right, int color) {
        int textWidth = font.width(text);
        int available = right - left;
        int centeredX = left + (available - textWidth) / 2;
        int textY = getY() + (height - font.lineHeight) / 2;

        if (textWidth <= available) {
            graphics.text(font, text, centeredX, textY, color, false);
        } else {
            // Oscillating scroll using system time – identical to Minecraft's own approach.
            double millis = net.minecraft.util.Util.getMillis() / 1000.0;
            double period = Math.max((textWidth - available) * 0.5, 3.0);
            double factor = Math.sin(Math.PI / 2.0 * Math.cos(Math.PI * 2.0 * millis / period)) / 2.0 + 0.5;
            int scrollX = (int) Mth.lerp(factor, 0.0, textWidth - available);

            graphics.enableScissor(left, getY(), right, getY() + height);
            graphics.text(font, text, left - scrollX, textY, color, false);
            graphics.disableScissor();
        }
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for {@link ScaleSlider}.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {

        private int x, y;
        private int width = 200;
        private int height = 20;

        private double min = 0.0;
        private double max = 1.0;
        private double step = 0.01;
        private double initialValue = 0.0;

        private Component label;
        private Function<Double, Component> formatter;

        private Consumer<Double> onValueChanged;
        private Consumer<Double> onValueCommitted;

        private Builder() {
        }

        /**
         * Sets the on-screen position and size.
         *
         * @param x      left edge in screen pixels
         * @param y      top edge in screen pixels
         * @param width  total widget width
         * @param height total widget height
         */
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Sets the logical value range.
         *
         * @param min inclusive lower bound
         * @param max inclusive upper bound; must be &gt; {@code min}
         */
        public Builder range(double min, double max) {
            if (max <= min) {
                throw new IllegalArgumentException(
                        "max (" + max + ") must be > min (" + min + ")");
            }
            this.min = min;
            this.max = max;
            return this;
        }

        /**
         * Sets the discrete step size.
         * Pass {@code 0} or a negative value to disable snapping.
         */
        public Builder step(double step) {
            this.step = step;
            return this;
        }

        /** Sets the value shown when the slider is first rendered. */
        public Builder initialValue(double initialValue) {
            this.initialValue = initialValue;
            return this;
        }

        /**
         * Sets the prefix label prepended to the formatted value.
         * Pass {@code null} to show only the formatted value.
         */
        public Builder label(Component label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the function that converts the current value to the displayed
         * {@link Component}.
         */
        public Builder formatter(Function<Double, Component> formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * Convenience overload that accepts a {@code String}-returning function.
         */
        public Builder formatterString(Function<Double, String> formatter) {
            this.formatter = v -> Component.literal(formatter.apply(v));
            return this;
        }

        /**
         * Registers a listener called continuously while dragging or holding a key.
         */
        public Builder onValueChanged(Consumer<Double> callback) {
            this.onValueChanged = callback;
            return this;
        }

        /**
         * Registers a listener called once when the user commits a value
         * (mouse release, Home/End/Enter, or mouse wheel).
         */
        public Builder onValueCommitted(Consumer<Double> callback) {
            this.onValueCommitted = callback;
            return this;
        }

        /** Constructs the {@link ScaleSlider}. */
        public ScaleSlider build() {
            return new ScaleSlider(this);
        }
    }
}