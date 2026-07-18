package frank1o3.statscale.client.gui;

import frank1o3.statscale.client.ScaleClientState;
import frank1o3.statscale.client.network.ClientScaleNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Proportionality scale selection screen.
 *
 * <p>
 * Opened via the keybind registered in
 * {@link frank1o3.statscale.client.ScaleKeybind}.
 * Displays a single {@link ScaleSlider} that lets the player choose their
 * desired scale.
 *
 * <h2>Data flow</h2>
 * 
 * <pre>
 *  Player moves slider
 *       │
 *       ▼  onValueChanged callback (live preview – no packet)
 *  slider label updates
 *
 *  Player releases / presses Enter
 *       │
 *       ▼  onValueCommitted callback
 *  {@link ClientScaleNetwork#sendScaleRequest} ──► server
 *       │
 *       ▼  server sends ScaleSyncPayload back
 *  {@link ScaleClientState#setCurrentScale} updates local cache
 * </pre>
 *
 * <p>
 * The slider range is driven by {@link ScaleClientState}: the minimum is always
 * {@code 0.1}, and the maximum is whatever the server advertised on login
 * (defaulting
 * to {@code 16.0} for singleplayer / offline use).
 */
@Environment(EnvType.CLIENT)
public class ScaleScreen extends Screen {

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    /** Width of the panel background in pixels. */
    private static final int PANEL_WIDTH = 220;
    /** Height of the panel background in pixels. */
    private static final int PANEL_HEIGHT = 80;

    /** Width of the slider widget. Fits neatly inside PANEL_WIDTH with padding. */
    private static final int SLIDER_WIDTH = 180;
    /** Height of the slider widget. */
    private static final int SLIDER_HEIGHT = 20;

    /** Vertical gap between the panel top edge and the slider top edge. */
    private static final int SLIDER_TOP_OFFSET = 28;

    /** Width of the Done button. */
    private static final int BUTTON_WIDTH = 80;
    /** Height of the Done button. */
    private static final int BUTTON_HEIGHT = 20;
    /** Vertical gap from the bottom of the slider to the top of the button. */
    private static final int BUTTON_GAP = 10;

    /** Colour of the dimmed screen overlay drawn behind the panel. */
    private static final int OVERLAY_COLOR = 0x88_000000;
    /** Colour of the panel background. */
    private static final int PANEL_COLOR = 0xCC_1A1A2E;
    /** Colour of the panel border. */
    private static final int BORDER_COLOR = 0xFF_3A3A5E;
    /** Colour of the title text. */
    private static final int TITLE_COLOR = 0xFF_FFFFFF;

    /**
     * Minimum scale the slider can represent. Matches the command argument floor.
     */
    private static final float SCALE_MIN = 0.1f;
    /** Step size: 0.1 increments, matching the Scale.java exponent resolution. */
    private static final float SCALE_STEP = 0.1f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The screen to return to when this screen is closed. May be null. */
    private final @Nullable Screen parent;

    /**
     * The pending scale value while the slider is being dragged.
     * Updated by {@code onValueChanged} but not sent to the server until committed.
     */
    private float pendingScale;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link ScaleScreen}.
     *
     * @param parent The screen to return to on close, or {@code null} to return to
     *               the game.
     */
    public ScaleScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.proportionality.scale.title"));
        this.parent = parent;
        this.pendingScale = ScaleClientState.getCurrentScale();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        // Top-left corner of the centred panel
        int panelX = cx - PANEL_WIDTH / 2;
        int panelY = cy - PANEL_HEIGHT / 2;

        // Slider centred horizontally inside the panel
        int sliderX = cx - SLIDER_WIDTH / 2;
        int sliderY = panelY + SLIDER_TOP_OFFSET;

        float serverMax = ScaleClientState.getServerMaxScale();

        ScaleSlider slider = ScaleSlider.builder()
                .bounds(sliderX, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT)
                .range(SCALE_MIN, serverMax)
                .step(SCALE_STEP)
                .initialValue(ScaleClientState.getCurrentScale())
                .label(Component.translatable("gui.proportionality.scale.label"))
                .formatter(v -> Component.literal(String.format("%.1fx", v)))
                .onValueChanged(v -> pendingScale = v.floatValue())
                .onValueCommitted(v -> ClientScaleNetwork.sendScaleRequest(v.floatValue()))
                .build();

        addRenderableWidget(slider);

        // Done button – centred below the slider
        int buttonX = cx - BUTTON_WIDTH / 2;
        int buttonY = sliderY + SLIDER_HEIGHT + BUTTON_GAP;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> onClose())
                .bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    // -------------------------------------------------------------------------
    // 26.2 Render & Extraction Architecture
    // -------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        int panelX = cx - PANEL_WIDTH / 2;
        int panelY = cy - PANEL_HEIGHT / 2;

        // 1. Draw your Dimmed screen background overlay
        graphics.fill(0, 0, this.width, this.height, OVERLAY_COLOR);

        // 2. Custom Panel background geometry
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_COLOR);

        // 3. Panel border frame line
        this.renderPanelBorder(graphics, panelX, panelY);

        // 4. Centered Mod Screen Title text
        int titleY = panelY + 8;
        graphics.text(this.font, this.getTitle(), cx - this.font.width(this.getTitle()) / 2, titleY, TITLE_COLOR,
                false);

        // 5. Passes layout data downstream to child elements (Slider and Button)
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /** Draws the four one-pixel border edges around the panel rectangle. */
    private void renderPanelBorder(GuiGraphicsExtractor graphics, int x, int y) {
        // Top
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, BORDER_COLOR);
        // Bottom
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);
        // Left
        graphics.fill(x, y, x + 1, y + PANEL_HEIGHT, BORDER_COLOR);
        // Right
        graphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);
    }

    // -------------------------------------------------------------------------
    // Screen behaviour
    // -------------------------------------------------------------------------

    /**
     * Do not pause the game while this screen is open (consistent with Wildfire
     * screens).
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}