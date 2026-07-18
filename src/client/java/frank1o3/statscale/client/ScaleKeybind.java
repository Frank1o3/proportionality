package frank1o3.statscale.client;

import frank1o3.statscale.Proportionality;
import frank1o3.statscale.client.gui.ScaleScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * Registers and processes the keybind that opens the {@link ScaleScreen}.
 *
 * <h2>Default binding</h2>
 * {@code K} – rebindable from the vanilla Controls screen under the
 * <em>"Proportionality"</em> category.
 *
 * <h2>Usage</h2>
 * Call {@link #register()} once from
 * {@link frank1o3.statscale.client.ProportionalityClient#onInitializeClient()}.
 */
@Environment(EnvType.CLIENT)
public final class ScaleKeybind {

    /**
     * Translation key used for the keybind category shown in the Controls screen.
     */
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(Proportionality.MOD_ID, "main"));

    /** Translation key for the keybind name shown in the Controls screen. */
    private static final String BINDING_KEY = Identifier.fromNamespaceAndPath(
            Proportionality.MOD_ID,
            "open_scale_screen").toLanguageKey("key");

    /** The registered {@link KeyMapping} instance. */
    private static KeyMapping openScreenKey;

    private ScaleKeybind() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the keybind with Fabric's {@link KeyBindingHelper} and subscribes
     * to the client tick event to poll for presses.
     *
     * <p>
     * Must be called during client initialisation, before the first client tick.
     */
    public static void register() {
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(BINDING_KEY, GLFW.GLFW_KEY_K, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick() returns true once per physical key press, preventing
            // the screen from opening multiple times if the key is held down.
            while (openScreenKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    // Only open if no other screen is currently shown.
                    client.gui.setScreen(new ScaleScreen(null));
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the registered {@link KeyMapping}.
     * May be used by other UI code to display the current binding to the player.
     *
     * @return The keybind, or {@code null} if {@link #register()} has not been
     *         called yet.
     */
    public static KeyMapping getOpenScreenKey() {
        return openScreenKey;
    }
}
