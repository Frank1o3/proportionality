package frank1o3.statscale.network;

import frank1o3.statscale.core.HandleCallbacks;
import frank1o3.statscale.network.packets.AdminScaleInfoPayload;
import frank1o3.statscale.network.packets.AdminScaleQueryPayload;
import frank1o3.statscale.network.packets.AdminScaleSetPayload;
import frank1o3.statscale.network.packets.RangeSyncPayload;
import frank1o3.statscale.network.packets.ScaleRequestPayload;
import frank1o3.statscale.network.packets.ScaleSyncPayload;

import java.util.UUID;

import frank1o3.statscale.Proportionality;
import frank1o3.statscale.storage.ScaleStorage;
import frank1o3.statscale.storage.ServerScaleConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
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
 * scale, apply it via {@link HandleCallbacks#applyScaleProfile}, persist it in
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

    /**
     * Minimum value the server will ever apply (matches the command argument
     * minimum).
     */

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
     * @param config  The active {@link ServerScaleConfig} for this server session.
     */
    public static void handleScaleRequest(
            ScaleRequestPayload payload,
            ServerPlayer player,
            ScaleStorage storage,
            ServerScaleConfig config,
            double minScale,
            double maxScale) {

        double attributeMax = resolveAttributeMax(player, maxScale);
        double effectiveMax = Math.min(maxScale, attributeMax);

        // 0. Frozen players cannot change their own scale, full stop — regardless
        // of what value the client sends or how the request was triggered.
        if (storage.isFrozen(player.getUUID())) {
            Proportionality.LOGGER.debug(
                    "[Proportionality] Ignored scale request from {}: player is frozen.",
                    player.getName().getString());
            ServerPlayNetworking.send(player, new ScaleSyncPayload(storage.getScale(player.getUUID()), effectiveMax));
            return;
        }

        double requested = payload.scale();
        double clamped = Mth.clamp(requested, minScale, effectiveMax);

        HandleCallbacks.applyScaleProfile(player, clamped, effectiveMax, config);
        storage.setScale(player.getUUID(), clamped); // guaranteed to succeed; already checked frozen above
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
     * If the player has no saved scale the {@link ScaleStorage}
     * is used and applied so their attributes match the stored value.
     *
     * @param player  The joining player.
     * @param storage The active {@link ScaleStorage} instance.
     * @param config  The active {@link ServerScaleConfig} instance.
     */
    public static void syncPlayerScale(ServerPlayer player, ScaleStorage storage, ServerScaleConfig config,
            double maxScale) {
        ScaleStorage.PlayerScaleData saved = storage.get(player.getUUID());
        double attributeMax = resolveAttributeMax(player, maxScale);
        double effectiveMax = Math.min(maxScale, attributeMax);

        // Re-apply on login so attributes are correct even after a server restart.
        HandleCallbacks.applyScaleProfile(player, saved.scale(), effectiveMax, config);

        ServerPlayNetworking.send(player, new ScaleSyncPayload(saved.scale(), effectiveMax));

        Proportionality.LOGGER.debug(
                "[Proportionality] Synced scale {} (max={}) to {}",
                saved, effectiveMax, player.getName().getString());
    }

    public static void syncRange(ServerPlayer player, double minScale, double maxScale) {
        ServerPlayNetworking.send(player, new RangeSyncPayload(minScale));

        Proportionality.LOGGER.debug(
                "[Proportionality] Synced scale range (min={}) (max={}) to {}",
                minScale, maxScale, player.getName().getString());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the hard maximum of the {@code minecraft:scale} attribute for this
     * player. Falls back to maxScale if the attribute is absent
     * or unbounded.
     */
    private static double resolveAttributeMax(ServerPlayer player, double maxScale) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null && instance.getAttribute().value() instanceof RangedAttribute ranged) {
            double cap = ranged.getMaxValue();
            if (cap > 0 && cap < Float.MAX_VALUE) {
                return cap;
            }
        }
        return maxScale;
    }

    // -------------------------------------------------------------------------
    // Admin handlers
    // -------------------------------------------------------------------------

    /**
     * Handles an {@link AdminScaleQueryPayload}: resolves the named player (must
     * be online) and reports back their current scale/freeze state.
     *
     * <p>
     * Permission is re-checked here independently of anything the client's menu
     * gating decided — an unauthorised client sending this packet directly gets
     * silently ignored.
     */
    public static void handleAdminQuery(
            AdminScaleQueryPayload payload,
            ServerPlayer sender,
            ScaleStorage storage,
            MinecraftServer server,
            double maxScale) {

        if (!hasAdminPermission(sender)) {
            Proportionality.LOGGER.warn(
                    "[Proportionality] {} attempted an admin scale query without permission.",
                    sender.getName().getString());
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(payload.targetName());
        if (target == null) {
            ServerPlayNetworking.send(sender, new AdminScaleInfoPayload(
                    false, new UUID(0L, 0L), payload.targetName(), 0.0, 0.0, false));
            return;
        }

        ScaleStorage.PlayerScaleData data = storage.get(target.getUUID());
        double effectiveMax = Math.min(maxScale, resolveAttributeMax(target, maxScale));

        ServerPlayNetworking.send(sender, new AdminScaleInfoPayload(
                true, target.getUUID(), target.getName().getString(), data.scale(), effectiveMax, data.frozen()));
    }

    /**
     * Handles an {@link AdminScaleSetPayload}: applies an operator-issued scale
     * and freeze state to the target, bypassing the target's own freeze flag
     * (that flag only blocks the target's *own* requests, not admin overrides).
     */
    public static void handleAdminSet(
            AdminScaleSetPayload payload,
            ServerPlayer sender,
            ScaleStorage storage,
            ServerScaleConfig config,
            MinecraftServer server, double minScale, double maxScale) {

        if (!hasAdminPermission(sender)) {
            Proportionality.LOGGER.warn(
                    "[Proportionality] {} attempted an admin scale set without permission.",
                    sender.getName().getString());
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        double attributeMax = target != null ? resolveAttributeMax(target, maxScale) : maxScale;
        double effectiveMax = Math.min(maxScale, attributeMax);
        double clamped = Mth.clamp(payload.scale(), minScale, effectiveMax);

        storage.adminSetScale(payload.target(), clamped, payload.frozen());

        if (target != null) {
            HandleCallbacks.applyScaleProfile(target, clamped, effectiveMax, config);
            ServerPlayNetworking.send(target, new ScaleSyncPayload(clamped, effectiveMax));
        }

        Proportionality.LOGGER.info(
                "[Proportionality] {} set {}'s scale to {} (frozen={}).",
                sender.getName().getString(), payload.target(), clamped, payload.frozen());
    }

    // -------------------------------------------------------------------------
    // Permission helper
    // -------------------------------------------------------------------------

    private static boolean hasAdminPermission(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }
}