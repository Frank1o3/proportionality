package frank1o3.statscale.client.gui.screen;

import frank1o3.statscale.client.ScaleClientState;
import frank1o3.statscale.client.gui.ScaleButton;
import frank1o3.statscale.client.gui.ScaleSlider;
import frank1o3.statscale.client.network.ClientScaleNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import org.jetbrains.annotations.Nullable;

/**
 * The Proportionality scale selection screen.
 *
 * <p>
 * Opened via the keybind registered in
 * {@link frank1o3.statscale.client.ScaleKeybind}.
 * Displays a {@link ScaleSlider} for choosing a scale, a Done button to close
 * the screen, and a Reset button to snap the player back to the default scale
 * of {@code 1.0}.
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
 *
 *  Player clicks Reset
 *       │
 *       ▼
 *  {@link ClientScaleNetwork#sendResetRequest} ──► server (scale = 1.0)
 *       │
 *       ▼  server sends ScaleSyncPayload back
 *  slider snaps to 1.0, ScaleClientState updated
 * </pre>
 *
 * <p>
 * The slider range is driven entirely by the bounds advertised by the server.
 */
@Environment(EnvType.CLIENT)
public class ScaleScreen extends Screen {

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    /** Width of the panel background in pixels. */
    private static final int PANEL_WIDTH = 220;
    /** Height of the panel background in pixels. */
    private static final int PANEL_HEIGHT = 120;

    /** Width of the slider widget. Fits neatly inside PANEL_WIDTH with padding. */
    private static final int SLIDER_WIDTH = 180;
    /** Height of the slider widget. */
    private static final int SLIDER_HEIGHT = 20;

    /** Vertical gap between the panel top edge and the slider top edge. */
    private static final int SLIDER_TOP_OFFSET = 28;

    /**
     * Width of each bottom button (Done / Reset).
     * Both buttons share the available width with a small gap between them.
     */
    private static final int BUTTON_WIDTH = 86;
    /** Height of each bottom button. */
    private static final int BUTTON_HEIGHT = 20;
    /** Horizontal gap between the two bottom buttons. */
    private static final int BUTTON_GAP = 8;
    /** Vertical gap from the bottom of the slider to the top of the buttons. */
    private static final int BUTTONS_TOP_OFFSET = 10;

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
    private double SCALE_MIN;
    /** Step size: 0.1 increments, matching the Scale.java exponent resolution. */
    private double SCALE_STEP = 0.1f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The screen to return to when this screen is closed. May be null. */
    private final @Nullable Screen parent;

    /**
     * Reference kept so the Reset button can snap the slider position to 1.0
     * without the player having to drag it there manually.
     */
    private ScaleSlider slider;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link ScaleScreen}.
     *
     * @param parent The screen to return to on close, or {@code null} to return
     *               to the game.
     */
    public ScaleScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.proportionality.scale.title"));
        this.parent = parent;
        this.SCALE_MIN = ScaleClientState.getMinScale();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        int panelY = cy - PANEL_HEIGHT / 2;

        // Slider centred horizontally inside the panel
        int sliderX = cx - SLIDER_WIDTH / 2;
        int sliderY = panelY + SLIDER_TOP_OFFSET;

        double serverMax = ScaleClientState.getMaxScale();

        slider = ScaleSlider.builder()
                .bounds(sliderX, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT)
                .range(SCALE_MIN, serverMax)
                .step(SCALE_STEP)
                .initialValue(ScaleClientState.getCurrentScale())
                .label(Component.translatable("gui.proportionality.scale.label"))
                .formatter(v -> Component.literal(String.format("%.1fx", v)))
                .onValueChanged(v -> {
                }) // live label update is handled inside ScaleSlider
                .onValueCommitted(v -> ClientScaleNetwork.sendScaleRequest(v.floatValue()))
                .build();

        addRenderableWidget(slider);

        // ── Bottom buttons (Done | Reset) ────────────────────────────────────
        int buttonsY = sliderY + SLIDER_HEIGHT + BUTTONS_TOP_OFFSET;

        // Total width occupied by both buttons + gap, centred under the slider.
        int totalButtonsWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int doneX = cx - totalButtonsWidth / 2;
        int resetX = doneX + BUTTON_WIDTH + BUTTON_GAP;

        // Done
        addRenderableWidget(ScaleButton.builder()
                .bounds(doneX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .message(Component.translatable("gui.done"))
                .onPress(btn -> onClose())
                .build());

        // Reset — sends scale 1.0 to the server and snaps the slider visually.
        addRenderableWidget(ScaleButton.builder()
                .bounds(resetX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .message(Component.translatable("gui.proportionality.scale.reset"))
                .onPress(btn -> {
                    ClientScaleNetwork.sendResetRequest();
                    slider.setValue(1.0);
                })
                .build());

        if (minecraft.player != null && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            addRenderableWidget(ScaleButton.builder()
                    .bounds(cx - 40, buttonsY + BUTTON_HEIGHT + 6, 80, 18)
                    .message(Component.translatable("gui.proportionality.admin.open"))
                    .onPress(btn -> minecraft.gui.setScreen(new AdminScaleScreen(this, SCALE_MIN)))
                    .build());
        }
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

        // 1. Dimmed screen background overlay
        graphics.fill(0, 0, this.width, this.height, OVERLAY_COLOR);

        // 2. Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_COLOR);

        // 3. Panel border
        renderPanelBorder(graphics, panelX, panelY);

        // 4. Centred title text
        int titleY = panelY + 8;
        graphics.text(this.font, this.getTitle(),
                cx - this.font.width(this.getTitle()) / 2, titleY, TITLE_COLOR, false);

        // 5. Pass layout data downstream to child widgets
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

    /** Do not pause the game while this screen is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
