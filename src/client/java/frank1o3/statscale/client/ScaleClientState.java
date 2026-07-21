package frank1o3.statscale.client;

import frank1o3.statscale.network.packets.ScaleSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-side singleton that caches the player's current scale and the
 * server-advertised
 * maximum scale received via {@link ScaleSyncPayload}.
 *
 * <p>
 * This class is intentionally simple: it is only written on the client thread
 * (Fabric's networking callbacks marshal onto the render thread for client
 * receivers)
 * and only read by GUI code that also runs on the render thread, so no
 * synchronisation
 * is needed.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 * <li>Populated when the server sends a {@link ScaleSyncPayload} (on login and
 * after
 * each committed scale change).</li>
 * <li>Reset to defaults when the client disconnects.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ScaleClientState {

    /** Default scale used before the server has sent its first sync. */
    public static final double DEFAULT_SCALE = 1.0f;
    /**
     * Default server max used before the server has sent its first sync
     * (singleplayer / offline).
     */
    public static final double DEFAULT_MAX_SCALE = 16.0f;

    public static double minScale;
    public static double maxScale;

    private static double currentScale = DEFAULT_SCALE;
    private static double serverMaxScale = DEFAULT_MAX_SCALE;

    private ScaleClientState() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the player's last-known committed scale value.
     * Defaults to {@link #DEFAULT_SCALE} until the server sends a sync packet.
     */
    public static double getCurrentScale() {
        return currentScale;
    }

    /**
     * Returns the maximum scale the current server allows.
     * Used to cap the slider range in
     * {@link frank1o3.statscale.client.gui.screen.ScaleScreen}.
     * Defaults to {@link #DEFAULT_MAX_SCALE} until the server sends a sync packet.
     */
    public static double getServerMaxScale() {
        return serverMaxScale;
    }

    // -------------------------------------------------------------------------
    // Mutation (called from network handler only)
    // -------------------------------------------------------------------------

    /**
     * Updates both values from a received {@link ScaleSyncPayload}.
     * Called on the client thread by
     * {@link frank1o3.statscale.network.ClientScaleNetwork}.
     *
     * @param scale    The player's active (persisted) scale value.
     * @param maxScale The server's configured maximum scale.
     */
    public static void applySync(double scale, double maxScale) {
        currentScale = scale;
        serverMaxScale = maxScale;
    }

    /**
     * Optimistically updates the local current scale to match a committed value
     * before the server confirmation arrives. If the server clamps the value,
     * the next {@link ScaleSyncPayload} will correct it.
     *
     * @param scale The scale value just sent to the server.
     */
    public static void setCurrentScale(double scale) {
        currentScale = scale;
    }

    /**
     * Resets state to defaults. Called when the client disconnects from a server
     * so stale data from a previous session does not bleed into the next.
     */
    public static void reset() {
        currentScale = DEFAULT_SCALE;
        serverMaxScale = DEFAULT_MAX_SCALE;
    }
}
