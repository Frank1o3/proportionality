package frank1o3.statscale.client.gui.screen;

import com.frank1o3.franklylib.client.gui.BaseFranklyScreen;
import com.frank1o3.franklylib.client.gui.FranklyButton;
import com.frank1o3.franklylib.client.gui.FranklySlider;
import com.frank1o3.franklylib.client.gui.FranklyTabBar;
import com.frank1o3.franklylib.client.gui.FranklyTextBox;
import com.frank1o3.franklylib.client.gui.style.FranklyUiStyle;
import frank1o3.statscale.client.AdminScaleClientState;
import frank1o3.statscale.client.ScaleClientState;
import frank1o3.statscale.client.network.ClientScaleNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Proportionality scale screen. Regular players just see the scale
 * slider. Operators additionally get a {@link FranklyTabBar} that switches
 * between the Scale tab and an Admin tab (formerly its own screen,
 * {@code AdminScaleScreen}) — the tab bar is never even constructed for
 * non-ops, so there's no way to reach admin controls client-side, and the
 * server independently re-checks permission on every admin packet regardless.
 */
@Environment(EnvType.CLIENT)
public class ScaleScreen extends BaseFranklyScreen {

    private enum Section {
        SCALE(Component.translatable("gui.proportionality.scale.title")),
        ADMIN(Component.translatable("gui.proportionality.admin.title"));

        final Component label;

        Section(Component label) {
            this.label = label;
        }
    }

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT_BASE = 120;
    private static final int PANEL_HEIGHT_WITH_TABS = 178;

    private static final int SLIDER_WIDTH = 180;
    private static final int SLIDER_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTONS_TOP_OFFSET = 10;
    private static final int TAB_BAR_HEIGHT = 18;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int SUGGESTION_ROW_HEIGHT = 14;
    private static final Identifier PANEL_STYLE = Identifier.fromNamespaceAndPath("proportionality", "scale_studio");
    private static final Identifier CONTROL_STYLE = Identifier.fromNamespaceAndPath("proportionality", "control");
    private static final Identifier ACCENT_STYLE = Identifier.fromNamespaceAndPath("proportionality", "accent");
    private static final Identifier HOVER_ANIMATION = Identifier.fromNamespaceAndPath("proportionality", "soft_lift");

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final boolean isOp;
    private final double scaleMin;
    private final double scaleStep = 0.1;

    private Section currentSection = Section.SCALE;
    private @Nullable FranklyTabBar<Section> tabBar;

    // Scale tab
    private FranklySlider slider;

    // Admin tab
    private FranklyTextBox adminNameBox;
    private @Nullable FranklySlider adminSlider;
    private @Nullable FranklyButton freezeToggle;
    private boolean frozen;
    private List<PlayerInfo> suggestions = List.of();

    public ScaleScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.proportionality.scale.title"), parent,
                PANEL_WIDTH, resolveIsOp() ? PANEL_HEIGHT_WITH_TABS : PANEL_HEIGHT_BASE);
        this.isOp = resolveIsOp();
        this.scaleMin = ScaleClientState.getMinScale();
        setUiStyle(PANEL_STYLE);
    }

    private static boolean resolveIsOp() {
        var player = Minecraft.getInstance().player;
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        buildTabBar();
        buildSectionWidgets();
        AdminScaleClientState.setOnUpdate(this::onAdminStateUpdate);
    }

    @Override
    public void removed() {
        AdminScaleClientState.setOnUpdate(null);
    }

    private void onAdminStateUpdate() {
        // Only rebuild if the admin tab is actually the one being looked at —
        // an update arriving while on the Scale tab shouldn't yank widgets away.
        if (currentSection == Section.ADMIN) {
            rebuildContent();
        }
    }

    private void rebuildContent() {
        clearWidgets();
        buildTabBar();
        buildSectionWidgets();
    }

    private void buildSectionWidgets() {
        if (currentSection == Section.ADMIN) {
            buildAdminWidgets();
        } else {
            buildScaleWidgets();
        }
    }

    private int contentTop() {
        return panelY() + (isOp ? 50 : 28);
    }

    // -------------------------------------------------------------------------
    // Tab bar (op only)
    // -------------------------------------------------------------------------

    private void buildTabBar() {
        if (!isOp) {
            return;
        }
        int cx = width / 2;
        tabBar = FranklyTabBar.<Section>builder()
                .bounds(cx - 100, panelY() + 24, 200, TAB_BAR_HEIGHT)
                .tabs(List.of(Section.SCALE, Section.ADMIN))
                .labelMapper(section -> section.label)
                .current(currentSection)
                .onSelect(section -> {
                    currentSection = section;
                    rebuildContent();
                })
                .style(CONTROL_STYLE)
                .build();
        addRenderableWidget(tabBar);
    }

    // -------------------------------------------------------------------------
    // Scale tab
    // -------------------------------------------------------------------------

    private void buildScaleWidgets() {
        int cx = width / 2;
        int top = contentTop();

        slider = FranklySlider.builder()
                .bounds(cx - SLIDER_WIDTH / 2, top, SLIDER_WIDTH, SLIDER_HEIGHT)
                .range(scaleMin, ScaleClientState.getMaxScale())
                .step(scaleStep)
                .initialValue(ScaleClientState.getCurrentScale())
                .label(Component.translatable("gui.proportionality.scale.label"))
                .formatter(v -> Component.literal(String.format("%.1fx", v)))
                .onValueCommitted(v -> ClientScaleNetwork.sendScaleRequest(v.floatValue()))
                .style(CONTROL_STYLE)
                .animation(HOVER_ANIMATION)
                .build();
        addRenderableWidget(slider);

        int buttonsY = top + SLIDER_HEIGHT + BUTTONS_TOP_OFFSET;
        int totalButtonsWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int doneX = cx - totalButtonsWidth / 2;
        int resetX = doneX + BUTTON_WIDTH + BUTTON_GAP;

        addRenderableWidget(FranklyButton.builder()
                .bounds(doneX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .message(Component.translatable("gui.done"))
                .onPress(btn -> onClose())
                .style(ACCENT_STYLE)
                .animation(HOVER_ANIMATION)
                .build());

        addRenderableWidget(FranklyButton.builder()
                .bounds(resetX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .message(Component.translatable("gui.proportionality.scale.reset"))
                .onPress(btn -> {
                    ClientScaleNetwork.sendResetRequest();
                    slider.setValue(1.0);
                })
                .style(CONTROL_STYLE)
                .animation(HOVER_ANIMATION)
                .build());
    }

    // -------------------------------------------------------------------------
    // Admin tab (ported from the old AdminScaleScreen)
    // -------------------------------------------------------------------------

    private void buildAdminWidgets() {
        int cx = width / 2;
        int top = contentTop();

        adminNameBox = FranklyTextBox.builder()
                .bounds(cx - 90, top, 140, 18)
                .onChanged(this::updateSuggestions)
                .style(CONTROL_STYLE)
                .build();
        addRenderableWidget(adminNameBox);

        addRenderableWidget(FranklyButton.builder()
                .bounds(cx + 54, top, 60, 18)
                .message(Component.translatable("gui.proportionality.admin.lookup"))
                .onPress(btn -> {
                    ClientScaleNetwork.sendAdminQuery(adminNameBox.getValue());
                    suggestions = List.of();
                })
                .style(CONTROL_STYLE)
                .animation(HOVER_ANIMATION)
                .build());

        var result = AdminScaleClientState.getLastResult();
        if (result == null || !result.found()) {
            adminSlider = null;
            freezeToggle = null;
            return;
        }

        frozen = result.frozen();

        adminSlider = FranklySlider.builder()
                .bounds(cx - 90, top + 30, 180, 20)
                .range(scaleMin, result.maxScale())
                .step(0.1)
                .initialValue(result.scale())
                .label(Component.literal(result.name()))
                .formatter(v -> Component.literal(String.format("%.1fx", v)))
                .style(CONTROL_STYLE)
                .animation(HOVER_ANIMATION)
                .build();
        addRenderableWidget(adminSlider);

        freezeToggle = FranklyButton.builder()
                .bounds(cx - 90, top + 56, 86, 18)
                .message(frozenLabel())
                .onPress(btn -> {
                    frozen = !frozen;
                    btn.setMessage(frozenLabel());
                })
                .style(CONTROL_STYLE)
                .animation(HOVER_ANIMATION)
                .build();
        addRenderableWidget(freezeToggle);

        addRenderableWidget(FranklyButton.builder()
                .bounds(cx + 4, top + 56, 86, 18)
                .message(Component.translatable("gui.proportionality.admin.apply"))
                .onPress(btn -> {
                    if (adminSlider != null) {
                        ClientScaleNetwork.sendAdminSet(result.target(), adminSlider.getValue(), frozen);
                    }
                })
                .style(ACCENT_STYLE)
                .animation(HOVER_ANIMATION)
                .build());
    }

    private void updateSuggestions(String text) {
        if (text.isBlank() || minecraft == null || minecraft.getConnection() == null) {
            suggestions = List.of();
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        suggestions = minecraft.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getProfile().name().toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(info -> !info.getProfile().name().equalsIgnoreCase(text))
                .sorted()
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }

    private Component frozenLabel() {
        return Component
                .translatable(frozen ? "gui.proportionality.admin.frozen" : "gui.proportionality.admin.unfrozen");
    }

    // -------------------------------------------------------------------------
    // Suggestion dropdown (admin tab only)
    // -------------------------------------------------------------------------

    @Override
    protected void renderPanelContent(GuiGraphicsExtractor graphics, int panelX, int panelY, int mouseX, int mouseY,
            float delta) {
        FranklyUiStyle.drawRoundedRect(graphics, panelX + 8, panelY + 7, PANEL_WIDTH - 16, 29, 0x99131A31, 6);
        graphics.fill(panelX + 18, panelY + 15, panelX + 52, panelY + 17, 0xFFB98AFF);
        graphics.fill(panelX + 56, panelY + 15, panelX + 72, panelY + 17, 0xFF688EDC);
        if (currentSection != Section.ADMIN || adminNameBox == null) {
            return;
        }
        if (!suggestions.isEmpty() && adminNameBox.isFocused()) {
            int rowX = adminNameBox.getX();
            int rowY = adminNameBox.getY() + adminNameBox.getHeight() + 1;
            int rowWidth = adminNameBox.getWidth();

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
        if (currentSection == Section.ADMIN && adminNameBox != null
                && !suggestions.isEmpty() && adminNameBox.isFocused()) {
            double mouseX = event.x();
            double mouseY = event.y();
            int rowX = adminNameBox.getX();
            int rowY = adminNameBox.getY() + adminNameBox.getHeight() + 1;
            int rowWidth = adminNameBox.getWidth();

            for (int i = 0; i < suggestions.size(); i++) {
                int y = rowY + i * SUGGESTION_ROW_HEIGHT;
                if (mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= y && mouseY <= y + SUGGESTION_ROW_HEIGHT) {
                    adminNameBox.setValue(suggestions.get(i).getProfile().name());
                    suggestions = List.of();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
