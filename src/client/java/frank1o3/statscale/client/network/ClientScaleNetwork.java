package frank1o3.statscale.client.network;

import java.util.UUID;

import frank1o3.statscale.client.AdminScaleClientState;
import frank1o3.statscale.client.ScaleClientState;
import frank1o3.statscale.network.packets.AdminScaleInfoPayload;
import frank1o3.statscale.network.packets.AdminScaleQueryPayload;
import frank1o3.statscale.network.packets.AdminScaleSetPayload;
import frank1o3.statscale.network.packets.ScaleRequestPayload;
import frank1o3.statscale.network.packets.ScaleSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Manages all client-side networking for the Proportionality mod.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Register the S2C {@link ScaleSyncPayload} receiver so the client can
 * update {@link ScaleClientState} when the server pushes a sync.</li>
 * <li>Provide {@link #sendScaleRequest} as a thin wrapper around
 * {@link ClientPlayNetworking#send} so GUI code stays free of networking
 * details.</li>
 * <li>Provide {@link #sendResetRequest} as a convenience for resetting the
 * player's scale back to the default value of {@code 1.0}.</li>
 * </ul>
 *
 * <p>
 * {@link #register()} must be called from
 * {@link frank1o3.statscale.client.ProportionalityClient#onInitializeClient()}
 * before any packet is received.
 */
@Environment(EnvType.CLIENT)
public final class ClientScaleNetwork {

    /** The canonical default / reset scale value sent to the server. */
    private static final float RESET_SCALE = 1.0f;

    private ClientScaleNetwork() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the S2C packet receiver.
     * Must be called during client initialisation.
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ScaleSyncPayload.TYPE,
                (payload, context) -> {
                    // context.client().execute() marshals onto the render/main client thread.
                    context.client().execute(
                            () -> ScaleClientState.applySync(payload.currentScale(), payload.serverMaxScale()));
                });
        ClientPlayNetworking.registerGlobalReceiver(AdminScaleInfoPayload.TYPE,
                (payload, context) -> context.client().execute(() -> AdminScaleClientState.applyInfo(
                        payload.found(), payload.target(), payload.name(), payload.scale(), payload.serverMaxScale(),
                        payload.frozen())));
    }

    // -------------------------------------------------------------------------
    // Outbound packets
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link ScaleRequestPayload} to the server asking it to apply and
     * persist the given scale value for the local player.
     *
     * <p>
     * The client also optimistically updates
     * {@link ScaleClientState#setCurrentScale} so the GUI reflects the requested
     * value immediately. If the server clamps it, the subsequent
     * {@link ScaleSyncPayload} will correct the cached value.
     *
     * @param scale The desired scale. Should be within the range shown by the
     *              slider ({@code [0.1, serverMaxScale]}), but the server will
     *              clamp regardless.
     */
    public static void sendScaleRequest(double scale) {
        ScaleClientState.setCurrentScale(scale); // optimistic update
        ClientPlayNetworking.send(new ScaleRequestPayload(scale));
    }

    /**
     * Sends a {@link ScaleRequestPayload} with the default scale ({@code 1.0})
     * to the server, effectively resetting the player to their vanilla size.
     *
     * <p>
     * Internally this is identical to calling {@link #sendScaleRequest(double)}
     * with {@code 1.0f}; the server applies the same validation path and sends
     * back a {@link ScaleSyncPayload} confirming the reset.
     */
    public static void sendResetRequest() {
        sendScaleRequest(RESET_SCALE);
    }

    public static void sendAdminQuery(String targetName) {
        ClientPlayNetworking.send(new AdminScaleQueryPayload(targetName));
    }

    public static void sendAdminSet(UUID target, double scale, boolean frozen) {
        ClientPlayNetworking.send(new AdminScaleSetPayload(target, scale, frozen));
    }
}