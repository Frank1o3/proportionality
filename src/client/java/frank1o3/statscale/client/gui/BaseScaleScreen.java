package frank1o3.statscale.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Common chrome shared by every Proportionality screen: a dimmed background
 * overlay, a bordered panel centred on screen, and a title drawn at its top.
 *
 * <p>
 * {@link frank1o3.statscale.client.gui.screen.ScaleScreen} and the operator-only
 * scale-management screen both extend this so the panel look stays identical
 * without copy-pasting the fill/border/title code between them.
 */
@Environment(EnvType.CLIENT)
public abstract class BaseScaleScreen extends Screen {

    private static final int OVERLAY_COLOR = 0x88_000000;
    private static final int PANEL_COLOR = 0xCC_1A1A2E;
    private static final int BORDER_COLOR = 0xFF_3A3A5E;
    private static final int TITLE_COLOR = 0xFF_FFFFFF;

    protected final @Nullable Screen parent;
    protected final int panelWidth;
    protected final int panelHeight;

    protected BaseScaleScreen(Component title, @Nullable Screen parent, int panelWidth, int panelHeight) {
        super(title);
        this.parent = parent;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    /**
     * Left edge of the centred panel, in screen pixels. Valid only after init().
     */
    protected int panelX() {
        return width / 2 - panelWidth / 2;
    }

    /** Top edge of the centred panel, in screen pixels. Valid only after init(). */
    protected int panelY() {
        return height / 2 - panelHeight / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, OVERLAY_COLOR);

        int px = panelX();
        int py = panelY();
        graphics.fill(px, py, px + panelWidth, py + panelHeight, PANEL_COLOR);
        drawPanelBorder(graphics, px, py);

        Component title = getTitle();
        int titleY = py + 8;
        graphics.text(font, title, width / 2 - font.width(title) / 2, titleY, TITLE_COLOR, false);

        // Subclasses can draw extra content (previews, labels) before widgets.
        renderPanelContent(graphics, px, py, mouseX, mouseY, delta);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /**
     * Hook for subclass-specific content drawn inside the panel, after the
     * background/border/title but before child widgets. Default is a no-op.
     */
    protected void renderPanelContent(GuiGraphicsExtractor graphics, int panelX, int panelY,
            int mouseX, int mouseY, float delta) {
    }

    private void drawPanelBorder(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + panelWidth, y + 1, BORDER_COLOR);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + panelHeight, BORDER_COLOR);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, BORDER_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}