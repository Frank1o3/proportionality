package frank1o3.statscale.network;

import frank1o3.statscale.HandleCallbacks;
import frank1o3.statscale.Proportionality;
import frank1o3.statscale.storage.ScaleStorage;
import frank1o3.statscale.storage.ServerScaleConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Handles all server-side packet logic for the Proportionality mod.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Receive {@link ScaleRequestPayload} from a client, validate the requested
 * scale,
 * apply it via {@link HandleCallbacks#applyScaleProfile}, persist it in
 * {@link ScaleStorage}, and confirm the result back to the client via
 * {@link ScaleSyncPayload}.</li>
 * <li>Expose {@link #syncPlayerScale} so the server can push saved scale data
 * to a player on login.</li>
 * </ul>
 *
 * <p>
 * All methods execute on the server tick thread (Fabric's networking callbacks
 * already marshal onto the main thread when {@code PacketSender} is used inside
 * {@link ServerPlayNetworking}).
 */
public final class ScalePacketHandler {

    /**
     * The server-wide maximum scale cap.
     *
     * <p>
     * Individual servers can raise or lower this by changing this constant.
     * In the future this could be loaded from a server config file; for now it
     * mirrors the hard cap enforced in {@link HandleCallbacks}.
     */
    public static final float SERVER_MAX_SCALE = 16.0f;

    /**
     * Minimum value the server will ever apply (matches the command argument
     * minimum).
     */
    private static final float SERVER_MIN_SCALE = 0.1f;

    private ScalePacketHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // C2S handler
    // -------------------------------------------------------------------------

    /**
     * Processes a {@link ScaleRequestPayload} received from a connected client.
     *
     * <p>
     * Steps:
     * <ol>
     * <li>Clamp the requested scale to {@code [SERVER_MIN_SCALE, SERVER_MAX_SCALE]}
     * and to the attribute's own hard cap.</li>
     * <li>Apply the full attribute profile via
     * {@link HandleCallbacks#applyScaleProfile}.</li>
     * <li>Persist the result via {@link ScaleStorage#setScale}.</li>
     * <li>Send a {@link ScaleSyncPayload} back so the client GUI reflects the
     * actual committed value (which may differ from the requested one).</li>
     * </ol>
     *
     * @param payload The inbound payload carrying the client's requested scale.
     * @param player  The player who sent the packet.
     * @param storage The active {@link ScaleStorage} instance for this server
     *                session.
     */
    public static void handleScaleRequest(
            ScaleRequestPayload payload,
            ServerPlayer player,
            ScaleStorage storage, ServerScaleConfig config) {

        // 1. Validate + clamp (never trust the client)
        float requested = payload.scale();
        double attributeMax = resolveAttributeMax(player);
        double effectiveMax = Math.min(SERVER_MAX_SCALE, attributeMax);
        double clamped = Mth.clamp(requested, SERVER_MIN_SCALE, effectiveMax);

        Proportionality.LOGGER.debug(
                "[Proportionality] {} requested scale {:.2f}; clamped to {:.2f} (max={})",
                player.getName().getString(), requested, clamped, effectiveMax);

        // 2. Apply the full attribute profile
        HandleCallbacks.applyScaleProfile(player, clamped, effectiveMax, config);

        // 3. Persist
        storage.setScale(player.getUUID(), clamped);

        // 4. Confirm back to client
        ServerPlayNetworking.send(player, new ScaleSyncPayload(clamped, effectiveMax));
    }

    // -------------------------------------------------------------------------
    // S2C sync helper
    // -------------------------------------------------------------------------

    /**
     * Sends the player's saved scale (and the server maximum) to the client.
     * Called from the {@code PlayerJoin} event so the client GUI always opens
     * with accurate values regardless of what the default slider range would be.
     *
     * <p>
     * If the player has no saved scale the {@link ScaleStorage#DEFAULT_SCALE}
     * is used and applied so their attributes match the stored value.
     *
     * @param player  The joining player.
     * @param storage The active {@link ScaleStorage} instance.
     */
    public static void syncPlayerScale(ServerPlayer player, ScaleStorage storage, ServerScaleConfig config) {
        double saved = storage.getScale(player.getUUID());
        double attributeMax = resolveAttributeMax(player);
        double effectiveMax = Math.min(SERVER_MAX_SCALE, attributeMax);

        // Re-apply on login so attributes are correct even after a server restart.
        HandleCallbacks.applyScaleProfile(player, saved, effectiveMax, config);

        ServerPlayNetworking.send(player, new ScaleSyncPayload(saved, effectiveMax));

        Proportionality.LOGGER.debug(
                "[Proportionality] Synced scale {:.2f} (max={}) to {}",
                saved, effectiveMax, player.getName().getString());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the hard maximum of the {@code minecraft:scale} attribute for this
     * player.
     * Falls back to {@link #SERVER_MAX_SCALE} if the attribute is absent or
     * unbounded.
     */
    private static double resolveAttributeMax(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null && instance.getAttribute().value() instanceof RangedAttribute ranged) {
            double cap = ranged.getMaxValue();
            if (cap > 0 && cap < Float.MAX_VALUE) {
                return cap;
            }
        }
        return SERVER_MAX_SCALE;
    }
}
