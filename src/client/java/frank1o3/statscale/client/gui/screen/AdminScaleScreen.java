package frank1o3.statscale.client.gui.screen;

import frank1o3.statscale.client.AdminScaleClientState;
import frank1o3.statscale.client.network.ClientScaleNetwork;
import frank1o3.statscale.client.gui.BaseScaleScreen;
import frank1o3.statscale.client.gui.ScaleButton;
import frank1o3.statscale.client.gui.ScaleSlider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

/**
 * Operator-only screen for inspecting and overriding a connected player's
 * scale, including freezing it against further self-service changes.
 *
 * <p>
 * Client-side gating (the button that opens this screen) is UX only — the
 * server independently re-checks permission on every {@code AdminScaleSet}
 * and {@code AdminScaleQuery} packet, so this screen being open never grants
 * any capability by itself.
 */
@Environment(EnvType.CLIENT)
public class AdminScaleScreen extends BaseScaleScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 140;

    private EditBox nameBox;
    private @Nullable ScaleSlider slider;
    private @Nullable ScaleButton freezeToggle;
    private boolean frozen;
    private @Nullable String resolvedName;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int SUGGESTION_ROW_HEIGHT = 14;

    private List<PlayerInfo> suggestions = List.of();

    public AdminScaleScreen(@Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("gui.proportionality.admin.title"), parent, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        buildBaseWidgets();
        buildResultWidgets(); // shows a cached result immediately if one exists from a prior lookup
        AdminScaleClientState.setOnUpdate(this::rebuildResultWidgets);
    }

    /** Name box + lookup button. Always present, never depends on query results. */
    private void buildBaseWidgets() {
        int py = panelY();
        int cx = width / 2;

        nameBox = new EditBox(font, cx - 90, py + 26, 140, 18,
                Component.translatable("gui.proportionality.admin.player"));
        nameBox.setHint(Component.translatable("gui.proportionality.admin.player_hint"));
        nameBox.setResponder(this::updateSuggestions);
        addRenderableWidget(nameBox);

        addRenderableWidget(ScaleButton.builder()
                .bounds(cx + 54, py + 26, 60, 18)
                .message(Component.translatable("gui.proportionality.admin.lookup"))
                .onPress(btn -> {
                    ClientScaleNetwork.sendAdminQuery(nameBox.getValue());
                    suggestions = List.of();
                })
                .build());
    }

    /**
     * Called once from {@link #init()} to render a cached result (if any), and
     * again as the {@link AdminScaleClientState} update callback whenever a new
     * {@code AdminScaleInfoPayload} arrives from the server.
     */
    private void buildResultWidgets() {
        var result = AdminScaleClientState.getLastResult();
        if (result == null || !result.found()) {
            resolvedName = null;
            slider = null;
            freezeToggle = null;
            return;
        }

        resolvedName = result.name();
        frozen = result.frozen();

        int py = panelY();
        int cx = width / 2;

        slider = ScaleSlider.builder()
                .bounds(cx - 90, py + 56, 180, 20)
                .range(0.1, result.maxScale())
                .step(0.1)
                .initialValue(result.scale())
                .label(Component.literal(result.name()))
                .formatter(v -> Component.literal(String.format("%.1fx", v)))
                .build();
        addRenderableWidget(slider);

        freezeToggle = ScaleButton.builder()
                .bounds(cx - 90, py + 82, 86, 18)
                .message(frozenLabel())
                .onPress(btn -> {
                    frozen = !frozen;
                    btn.setMessage(frozenLabel());
                })
                .build();
        addRenderableWidget(freezeToggle);

        addRenderableWidget(ScaleButton.builder()
                .bounds(cx + 4, py + 82, 86, 18)
                .message(Component.translatable("gui.proportionality.admin.apply"))
                .onPress(btn -> {
                    if (slider != null) {
                        ClientScaleNetwork.sendAdminSet(result.target(), slider.getValue(), frozen);
                    }
                })
                .build());
    }

    private void updateSuggestions(String text) {
        if (text.isBlank() || minecraft == null || minecraft.getConnection() == null) {
            suggestions = List.of();
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        suggestions = minecraft.getConnection().getOnlinePlayers().stream() // adjust to your mapping's
                .filter(plrInfo -> plrInfo.getProfile().name().toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(name -> !name.getProfile().name().equalsIgnoreCase(text))
                .sorted()
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }

    /**
     * Invoked by {@link AdminScaleClientState} whenever a fresh query response
     * arrives. Rebuilds the full widget set from scratch — clearing first, then
     * re-adding the static base widgets plus whatever the new result implies —
     * without going through {@link #init()} again.
     */
    private void rebuildResultWidgets() {
        clearWidgets();
        buildBaseWidgets();
        buildResultWidgets();
    }

    private Component frozenLabel() {
        return Component
                .translatable(frozen ? "gui.proportionality.admin.frozen" : "gui.proportionality.admin.unfrozen");
    }

    @Override
    protected void renderPanelContent(GuiGraphicsExtractor graphics, int panelX, int panelY, int mouseX, int mouseY,
            float delta) {
        if (!suggestions.isEmpty() && nameBox.isFocused()) {
            int rowX = nameBox.getX();
            int rowY = nameBox.getY() + nameBox.getHeight() + 1;
            int rowWidth = nameBox.getWidth();

            for (int i = 0; i < suggestions.size(); i++) {
                int y = rowY + i * SUGGESTION_ROW_HEIGHT;
                boolean hovered = mouseX >= rowX && mouseX <= rowX + rowWidth
                        && mouseY >= y && mouseY <= y + SUGGESTION_ROW_HEIGHT;
                graphics.fill(rowX, y, rowX + rowWidth, y + SUGGESTION_ROW_HEIGHT, hovered ? 0xCC_444466 : 0xCC_1A1A2E);
                graphics.text(font, Component.literal(suggestions.get(i).getProfile().name()),
                        rowX + 3, y + 3, 0xFF_FFFFFF, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!suggestions.isEmpty() && nameBox.isFocused()) {
            double mouseX = event.x();
            double mouseY = event.y();
            int rowX = nameBox.getX();
            int rowY = nameBox.getY() + nameBox.getHeight() + 1;
            int rowWidth = nameBox.getWidth();

            for (int i = 0; i < suggestions.size(); i++) {
                int y = rowY + i * SUGGESTION_ROW_HEIGHT;
                if (mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= y && mouseY <= y + SUGGESTION_ROW_HEIGHT) {
                    nameBox.setValue(suggestions.get(i).getProfile().name());
                    suggestions = List.of();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        AdminScaleClientState.setOnUpdate(null); // avoid a stale callback firing after this screen closes
    }
}