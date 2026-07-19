package frank1o3.statscale.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import frank1o3.statscale.Proportionality;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists player scale values (and freeze state) in a single JSON file
 * located at {@code <world>/data/proportionality_scales.json}.
 *
 * <p>
 * Layout on disk:
 *
 * <pre>
 * {
 *   "550e8400-e29b-41d4-a716-446655440000": { "scale": 3.5, "frozen": false },
 *   "6ba7b810-9dad-11d1-80b4-00c04fd430c8": { "scale": 1.0, "frozen": true }
 * }
 * </pre>
 *
 * <p>
 * All public methods are called from the server thread. No additional
 * synchronisation is required for a single-threaded Minecraft server tick loop.
 */
public final class ScaleStorage {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final double DEFAULT_SCALE = 1.0;
    private static final String FILE_NAME = "proportionality_scales.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -------------------------------------------------------------------------
    // Data record
    // -------------------------------------------------------------------------

    /**
     * A player's persisted scale plus whether an operator has frozen it against
     * further self-service changes via {@code /scale set} or the scale screen.
     */
    public record PlayerScaleData(double scale, boolean frozen) {
        public static final PlayerScaleData DEFAULT = new PlayerScaleData(DEFAULT_SCALE, false);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Map<UUID, PlayerScaleData> data = new HashMap<>();
    private final Path filePath;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ScaleStorage(MinecraftServer server) {
        Path dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
        this.filePath = dataDir.resolve(FILE_NAME);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Reads the JSON file from disk into the in-memory map. Called once when the
     * server starts. Safe to call even if the file does not yet exist.
     *
     * <p>
     * Also transparently migrates the old pre-freeze format, where each entry
     * was a bare number instead of an object, so existing worlds don't lose
     * their saved scales when upgrading.
     */
    public void load() {
        data.clear();

        if (!Files.exists(filePath)) {
            Proportionality.LOGGER.info("[Proportionality] No scale data file found at {}; starting fresh.", filePath);
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonElement value = entry.getValue();

                    PlayerScaleData parsed;
                    if (value.isJsonPrimitive()) {
                        // Legacy format: bare number, never frozen.
                        parsed = new PlayerScaleData(value.getAsDouble(), false);
                    } else {
                        JsonObject obj = value.getAsJsonObject();
                        double scale = obj.has("scale") ? obj.get("scale").getAsDouble() : DEFAULT_SCALE;
                        boolean frozen = obj.has("frozen") && obj.get("frozen").getAsBoolean();
                        parsed = new PlayerScaleData(scale, frozen);
                    }
                    data.put(uuid, parsed);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    Proportionality.LOGGER.warn(
                            "[Proportionality] Skipping malformed entry '{}' in scale data file.", entry.getKey());
                }
            });
            Proportionality.LOGGER.info("[Proportionality] Loaded scale data for {} player(s).", data.size());
        } catch (IOException e) {
            Proportionality.LOGGER.error("[Proportionality] Failed to read scale data file: {}", e.getMessage());
        }
    }

    /** Writes the current in-memory map back to disk. */
    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            Proportionality.LOGGER.error("[Proportionality] Could not create data directory: {}", e.getMessage());
            return;
        }

        JsonObject root = new JsonObject();
        data.forEach((uuid, value) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("scale", value.scale());
            entry.addProperty("frozen", value.frozen());
            root.add(uuid.toString(), entry);
        });

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            Proportionality.LOGGER.error("[Proportionality] Failed to write scale data file: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Data access
    // -------------------------------------------------------------------------

    /**
     * Returns the saved scale + freeze state for the given player, defaulting if
     * absent.
     */
    public PlayerScaleData get(UUID uuid) {
        return data.getOrDefault(uuid, PlayerScaleData.DEFAULT);
    }

    public double getScale(UUID uuid) {
        return get(uuid).scale();
    }

    public boolean isFrozen(UUID uuid) {
        return get(uuid).frozen();
    }

    /**
     * Persists a new scale for the given player via the normal (non-admin) path.
     *
     * <p>
     * <b>This is the actual security boundary for freezing:</b> if the player is
     * currently frozen, the request is silently dropped rather than applied.
     * Callers (the packet handler, commands) should still bail out early on
     * their own if they want to skip network round-trips, but this method is
     * the last line of defence regardless of what any client sends.
     *
     * @return {@code true} if the scale was actually applied, {@code false} if
     *         it was rejected because the player is frozen.
     */
    public boolean setScale(UUID uuid, double scale) {
        if (isFrozen(uuid)) {
            return false;
        }
        data.put(uuid, new PlayerScaleData(scale, false));
        save();
        return true;
    }

    /**
     * Operator-only path: sets both the scale and the freeze flag directly,
     * bypassing the freeze check above. Callers of this method are responsible
     * for having already verified operator/moderator permission.
     */
    public void adminSetScale(UUID uuid, double scale, boolean frozen) {
        data.put(uuid, new PlayerScaleData(scale, frozen));
        save();
    }

    /** Operator-only: toggles freeze without changing the stored scale. */
    public void setFrozen(UUID uuid, boolean frozen) {
        PlayerScaleData current = get(uuid);
        data.put(uuid, new PlayerScaleData(current.scale(), frozen));
        save();
    }
}