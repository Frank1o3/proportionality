package frank1o3.statscale;

import frank1o3.statscale.network.ScaleRequestPayload;
import frank1o3.statscale.network.ScaleSyncPayload;
import frank1o3.statscale.network.ScalePacketHandler;
import frank1o3.statscale.storage.ScaleStorage;
import frank1o3.statscale.storage.ServerScaleConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.FloatArgumentType;

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
 * login
 * and push an S2C sync packet.</li>
 * <li>Register the {@code /scale set} command as a fallback for server
 * operators.</li>
 * </ol>
 */
public class Proportionality implements ModInitializer {

    public static final String MOD_ID = "proportionality";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
        LOGGER.info("[Proportionality] Configuration rules loaded successfully.");

        registerPackets();
        registerLifecycleEvents();
        registerLoginSync();
        registerCommand();
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
        // C2S – client requests a scale change
        PayloadTypeRegistry.serverboundPlay().register(ScaleRequestPayload.TYPE, ScaleRequestPayload.CODEC);

        // S2C – server pushes current scale + server max to the client
        PayloadTypeRegistry.clientboundPlay().register(ScaleSyncPayload.TYPE, ScaleSyncPayload.CODEC);

        // Handle incoming scale requests on the server
        ServerPlayNetworking.registerGlobalReceiver(
                ScaleRequestPayload.TYPE,
                (payload, context) -> {
                    // context.player() is already on the server thread via Fabric's executor
                    ScalePacketHandler.handleScaleRequest(payload, context.player(), storage, getConfig());
                });
    }

    /** Loads storage when the world is ready and flushes it on shutdown. */
    private static void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            storage = new ScaleStorage(server);
            storage.load();
            LOGGER.info("[Proportionality] Scale storage loaded.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[Proportionality] SERVER_STOPPING fired.");

            if (storage != null) {
                LOGGER.info("[Proportionality] Saving scale storage...");
                storage.save();
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
            ScalePacketHandler.syncPlayerScale(handler.player, storage, getConfig());
        });
    }

    /** Registers the {@code /scale set <value>} command for server operators. */
    private static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess,
                environment) -> dispatcher.register(Commands.literal("scale")
                        .requires(source -> source.permissions().hasPermission(Permissions.CHAT_SEND_COMMANDS))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 32.0f))
                                        .executes(context -> HandleCallbacks.MeSet(context, getConfig()))))
                        .then(Commands.literal("reload")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .executes(context -> {
                                    // 1. Reload file into memory
                                    config = ServerScaleConfig.load();
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                            .literal("StatScale config reloaded from file!"), true);

                                    // 2. Refresh attribute scales live for all active players online
                                    if (context.getSource().getServer() != null) {
                                        context.getSource().getServer().getPlayerList().getPlayers().forEach(player -> {
                                            // This recalculates attributes using the new JSON parameters and syncs them
                                            ScalePacketHandler.syncPlayerScale(player, storage, getConfig());
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
}
