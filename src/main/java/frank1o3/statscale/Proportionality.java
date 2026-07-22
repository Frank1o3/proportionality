package frank1o3.statscale;

import frank1o3.statscale.network.ScalePacketHandler;
import frank1o3.statscale.network.packets.AdminScaleInfoPayload;
import frank1o3.statscale.network.packets.AdminScaleQueryPayload;
import frank1o3.statscale.network.packets.AdminScaleSetPayload;
import frank1o3.statscale.network.packets.RangeSyncPayload;
import frank1o3.statscale.network.packets.ScaleRequestPayload;
import frank1o3.statscale.network.packets.ScaleSyncPayload;
import frank1o3.statscale.storage.ScaleStorage;
import frank1o3.statscale.storage.ServerScaleConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side mod entry point for Proportionality.
 *
 * <h2>Initialisation order</h2>
 * <ol>
 * <li>Register custom packet payload types with Fabric's
 * {@link PayloadTypeRegistry}.</li>
 * <li>Register the C2S packet handler so the server can receive scale
 * requests.</li>
 * <li>Hook {@link ServerLifecycleEvents#SERVER_STARTED} to load
 * {@link ScaleStorage}.</li>
 * <li>Hook {@link ServerLifecycleEvents#SERVER_STOPPING} to flush storage to
 * disk.</li>
 * <li>Hook {@link ServerPlayConnectionEvents#JOIN} to apply saved scales on
 * login and push an S2C sync packet.</li>
 * <li>Register the {@code /scale} commands for players and operators.</li>
 * </ol>
 */
public class Proportionality implements ModInitializer {

    public static final String MOD_ID = "proportionality";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int AUTOSAVE_INTERVAL_TICKS = 20 * 60 * 5; // every 5 minutes
    private static int autosaveTicker = 0;
    private static double minScale;
    private static double maxScale;

    /**
     * Singleton storage instance, live for the duration of a server session.
     * Null before {@code SERVER_STARTED} fires and after {@code SERVER_STOPPING}
     * fires.
     *
     * <p>
     * Only accessed from the server tick thread; no synchronisation needed.
     */
    private static ScaleStorage storage;
    private static ServerScaleConfig config;

    // -------------------------------------------------------------------------
    // ModInitializer
    // -------------------------------------------------------------------------

    @Override
    public void onInitialize() {
        config = ServerScaleConfig.load();
        LOGGER.info("[Proportionality] Configuration loaded successfully.");

        registerPackets();
        registerLifecycleEvents();
        registerLoginSync();
        registerCommands();

        Holder<Attribute> attribute = Attributes.SCALE;
        if (attribute.value() instanceof RangedAttribute ranged) {
            minScale = ranged.getMinValue();
            maxScale = ranged.getMaxValue();
        }
    }

    public static ServerScaleConfig getConfig() {
        return config;
    }

    // -------------------------------------------------------------------------
    // Registration helpers
    // -------------------------------------------------------------------------

    /**
     * Registers both payload types with Fabric's {@link PayloadTypeRegistry}.
     * This must happen before any packet is sent or received on either side.
     */
    private static void registerPackets() {
        // Main C2S/S2C
        PayloadTypeRegistry.serverboundPlay().register(ScaleRequestPayload.TYPE, ScaleRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScaleSyncPayload.TYPE, ScaleSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RangeSyncPayload.TYPE, RangeSyncPayload.CODEC);

        // Admin C2S/S2C
        PayloadTypeRegistry.serverboundPlay().register(AdminScaleQueryPayload.TYPE, AdminScaleQueryPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AdminScaleSetPayload.TYPE, AdminScaleSetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminScaleInfoPayload.TYPE, AdminScaleInfoPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                ScaleRequestPayload.TYPE,
                (payload, context) -> ScalePacketHandler.handleScaleRequest(payload, context.player(), storage,
                        config, minScale, maxScale));

        ServerPlayNetworking.registerGlobalReceiver(
                AdminScaleQueryPayload.TYPE,
                (payload, context) -> ScalePacketHandler.handleAdminQuery(
                        payload, context.player(), storage, context.player().level().getServer(), maxScale));

        ServerPlayNetworking.registerGlobalReceiver(
                AdminScaleSetPayload.TYPE,
                (payload, context) -> ScalePacketHandler.handleAdminSet(
                        payload, context.player(), storage, config, context.player().level().getServer(), minScale,
                        maxScale));
    }

    /** Loads storage when the world is ready and flushes it on shutdown. */
    private static void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            storage = new ScaleStorage(server);
            storage.load();
            LOGGER.info("[Proportionality] Scale storage loaded.");
        });

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ScalePacketHandler.syncRange(listener.player, config, minScale, maxScale);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (storage == null)
                return;
            if (++autosaveTicker >= AUTOSAVE_INTERVAL_TICKS) {
                autosaveTicker = 0;
                storage.saveIfDirty();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[Proportionality] SERVER_STOPPING fired.");
            if (storage != null) {
                storage.save(); // unconditional — don't rely on the dirty flag during shutdown
                LOGGER.info("[Proportionality] Scale storage flushed to disk.");
            } else {
                LOGGER.warn("[Proportionality] Cannot save scale storage: storage is null!");
            }
        });
    }

    /**
     * Applies saved scale data and syncs it to the client whenever a player joins.
     * This is the mechanism that makes scale settings survive server restarts.
     */
    private static void registerLoginSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (storage == null) {
                LOGGER.warn("[Proportionality] Storage not ready when {} joined; skipping sync.",
                        handler.player.getName().getString());
                return;
            }
            ScalePacketHandler.syncPlayerScale(handler.player, storage, config, minScale, maxScale);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ScalePacketHandler.syncRange(newPlayer, config, minScale, maxScale);
            ScalePacketHandler.syncPlayerScale(newPlayer, storage, config, minScale, maxScale);
        });
    }

    /**
     * Registers all {@code /scale} sub-commands.
     *
     * <pre>
     * /scale set <value>               – set your own scale (any player)
     * /scale set <player> <value>      – set another player's scale (operator)
     * /scale reset                     – reset your own scale to 1.0 (any player)
     * /scale reset <player>            – reset another player's scale (operator)
     * /scale reload                    – reload config and reapply to all online players (operator)
     * </pre>
     */
    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess,
                environment) -> dispatcher.register(Commands.literal("scale")

                        // ── /scale reload ──────────────────────────────────────────
                        .then(Commands.literal("reload")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .executes(context -> {
                                    config = ServerScaleConfig.load();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("[Proportionality] Config reloaded."), true);

                                    if (context.getSource().getServer() != null) {
                                        context.getSource().getServer().getPlayerList().getPlayers()
                                                .forEach(p -> {
                                                    ScalePacketHandler.syncRange(p, config, minScale, maxScale);
                                                    ScalePacketHandler.syncPlayerScale(p, storage, config, minScale,
                                                            maxScale);
                                                });
                                    }
                                    return 1;
                                }))));
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Convenience factory for namespaced {@link Identifier}s using this mod's ID.
     *
     * @param path The path component (e.g. {@code "scale_request"}).
     * @return A fully-qualified {@code proportionality:<path>} identifier.
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Returns the active {@link ScaleStorage} for this server session.
     *
     * @return The storage instance, or {@code null} if the server has not yet
     *         started.
     */
    public static ScaleStorage getStorage() {
        return storage;
    }

    public static double getMinScale() {
        return minScale;
    }

    public static double getMaxcale() {
        return maxScale;
    }
}
