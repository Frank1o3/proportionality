package frank1o3.statscale.client;

import frank1o3.statscale.client.network.ClientScaleNetwork;
import frank1o3.statscale.network.packets.AdminScaleInfoPayload;
import frank1o3.statscale.network.packets.RangeSyncPayload;
import frank1o3.statscale.network.packets.ScaleSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side entry point for Proportionality.
 *
 * <p>
 * Wires up, in order:
 * <ol>
 * <li>The S2C packet receiver ({@link ClientScaleNetwork#register()}) so the
 * client
 * can receive scale sync data from the server.</li>
 * <li>The keybind ({@link ScaleKeybind#register()}) that opens the scale
 * GUI.</li>
 * <li>A disconnect hook that resets {@link ScaleClientState} so stale data from
 * one
 * session does not bleed into the next.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public class ProportionalityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Register S2C receiver before any join event could fire.
        register();

        // 2. Register the keybind and its tick handler.
        ScaleKeybind.register();

        // 3. Reset client state on disconnect so slider defaults are sane next session.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScaleClientState.reset());
    }

    /**
     * Registers the S2C packet receiver.
     * Must be called during client initialisation.
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ScaleSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ScaleClientState.applySync(payload.currentScale(), payload.serverMaxScale())));
        ClientPlayNetworking.registerGlobalReceiver(AdminScaleInfoPayload.TYPE,
                (payload, context) -> context.client().execute(() -> AdminScaleClientState.applyInfo(
                        payload.found(), payload.target(), payload.name(), payload.scale(), payload.serverMaxScale(),
                        payload.frozen())));
        ClientPlayNetworking.registerGlobalReceiver(RangeSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    ScaleClientState.applyRange(payload.minScale(), payload.maxScale());
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientScaleNetwork.tickDebounce());
    }
}
