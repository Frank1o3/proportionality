package frank1o3.statscale.client;

import frank1o3.statscale.client.network.ClientScaleNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client-side entry point for Proportionality.
 *
 * <p>Wires up, in order:
 * <ol>
 *   <li>The S2C packet receiver ({@link ClientScaleNetwork#register()}) so the client
 *       can receive scale sync data from the server.</li>
 *   <li>The keybind ({@link ScaleKeybind#register()}) that opens the scale GUI.</li>
 *   <li>A disconnect hook that resets {@link ScaleClientState} so stale data from one
 *       session does not bleed into the next.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public class ProportionalityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Register S2C receiver before any join event could fire.
        ClientScaleNetwork.register();

        // 2. Register the keybind and its tick handler.
        ScaleKeybind.register();

        // 3. Reset client state on disconnect so slider defaults are sane next session.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ScaleClientState.reset());
    }
}
