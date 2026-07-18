package frank1o3.statscale;

import frank1o3.statscale.network.ScaleRequestPayload;
import frank1o3.statscale.network.ScaleSyncPayload;
import frank1o3.statscale.core.HandleCallbacks;
import frank1o3.statscale.network.ScalePacketHandler;
import frank1o3.statscale.storage.ScaleStorage;
import frank1o3.statscale.storage.ServerScaleConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
 * login and push an S2C sync packet.</li>
 * <li>Register the {@code /scale} commands for players and operators.</li>
 * </ol>
 */
public class Proportionality implements ModInitializer {

    public static final String MOD_ID = "proportionality";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Default scale used when resetting a player. */
    private static final float DEFAULT_SCALE = 1.0f;

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
                    ScalePacketHandler.handleScaleRequest(payload, context.player(), storage, config);
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
            ScalePacketHandler.syncPlayerScale(handler.player, storage, config);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ScalePacketHandler.syncPlayerScale(newPlayer, storage, config);
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

                        // ── /scale set ─────────────────────────────────────────────
                        .then(Commands.literal("set")

                                // /scale set <value> (self, any player)
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 32.0f))
                                        .requires(source -> source.permissions()
                                                .hasPermission(Permissions.CHAT_SEND_COMMANDS))
                                        .executes(context -> HandleCallbacks.MeSet(context, config)))

                                // /scale set <player> <value> (operator)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.permissions()
                                                .hasPermission(Permissions.COMMANDS_MODERATOR))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 32.0f))
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    float value = FloatArgumentType.getFloat(context, "value");
                                                    return applyScaleToPlayer(context.getSource().getServer() != null
                                                            ? context.getSource()
                                                            : context.getSource(),
                                                            target, value, context);
                                                }))))

                        // ── /scale reset ───────────────────────────────────────────
                        .then(Commands.literal("reset")

                                // /scale reset (self, any player)
                                .requires(source -> source.permissions().hasPermission(Permissions.CHAT_SEND_COMMANDS))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return resetPlayerScale(player, context.getSource());
                                })

                                // /scale reset <player> (operator)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.permissions()
                                                .hasPermission(Permissions.COMMANDS_MODERATOR))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            return resetPlayerScale(target, context.getSource());
                                        })))

                        // ── /scale reload ──────────────────────────────────────────
                        .then(Commands.literal("reload")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .executes(context -> {
                                    config = ServerScaleConfig.load();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("[Proportionality] Config reloaded."), true);

                                    if (context.getSource().getServer() != null) {
                                        context.getSource().getServer().getPlayerList().getPlayers()
                                                .forEach(p -> ScalePacketHandler.syncPlayerScale(p, storage, config));
                                    }
                                    return 1;
                                }))));
    }

    // -------------------------------------------------------------------------
    // Command helpers
    // -------------------------------------------------------------------------

    /**
     * Applies {@code scale} to {@code target}, persists it, syncs to client,
     * and sends feedback to the command source.
     */
    private static int applyScaleToPlayer(
            net.minecraft.commands.CommandSourceStack source,
            ServerPlayer target,
            float scale,
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {

        double attributeMax = ScalePacketHandler.SERVER_MAX_SCALE;
        double clamped = Math.min(scale, attributeMax);

        HandleCallbacks.applyScaleProfile(target, clamped, attributeMax, config);
        storage.setScale(target.getUUID(), clamped);
        ServerPlayNetworking.send(target, new ScaleSyncPayload(clamped, attributeMax));

        source.sendSuccess(() -> Component.literal(
                "[Proportionality] Set " + target.getName().getString() + "'s scale to " + clamped + "."), true);
        return 1;
    }

    /**
     * Resets {@code target}'s scale to {@link #DEFAULT_SCALE}, persists it,
     * syncs to the client, and sends feedback to the command source.
     */
    private static int resetPlayerScale(
            ServerPlayer target,
            net.minecraft.commands.CommandSourceStack source) {

        double attributeMax = ScalePacketHandler.SERVER_MAX_SCALE;

        HandleCallbacks.applyScaleProfile(target, DEFAULT_SCALE, attributeMax, config);
        storage.setScale(target.getUUID(), DEFAULT_SCALE);
        ServerPlayNetworking.send(target, new ScaleSyncPayload(DEFAULT_SCALE, attributeMax));

        boolean isSelf = source.isPlayer() && source.getPlayer() != null
                && source.getPlayer().getUUID().equals(target.getUUID());

        if (isSelf) {
            source.sendSuccess(() -> Component.literal("[Proportionality] Your scale has been reset to 1.0."), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "[Proportionality] Reset " + target.getName().getString() + "'s scale to 1.0."), true);
            target.sendSystemMessage(Component.literal("[Proportionality] Your scale has been reset to 1.0."));
        }
        return 1;
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