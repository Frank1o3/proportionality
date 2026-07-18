package frank1o3.statscale.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Persists player scale values in a single JSON file located at
 * {@code <world>/data/proportionality_scales.json}.
 *
 * <p>
 * Layout on disk:
 * 
 * <pre>
 * {
 *   "550e8400-e29b-41d4-a716-446655440000": 3.5,
 *   "6ba7b810-9dad-11d1-80b4-00c04fd430c8": 1.0
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

    /** Default scale applied to any player whose UUID is not in the file. */
    public static final double DEFAULT_SCALE = 1.0f;

    /** File name written inside the world's {@code data/} directory. */
    private static final String FILE_NAME = "proportionality_scales.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** In-memory mirror of the on-disk JSON. Keyed by player UUID. */
    private final Map<UUID, Double> scales = new HashMap<>();

    /** Absolute path to the JSON file, resolved once at construction time. */
    private final Path filePath;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link ScaleStorage} bound to the given server's world directory.
     * Call {@link #load()} immediately after construction to populate the in-memory
     * map.
     *
     * @param server The running {@link MinecraftServer} used to locate the world
     *               data folder.
     */
    public ScaleStorage(MinecraftServer server) {
        // getLevelStorageAccess().getLevelDirectory().path() gives us <world>/
        // We place our file in <world>/data/ alongside other Minecraft data files.
        Path dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
        this.filePath = dataDir.resolve(FILE_NAME);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Reads the JSON file from disk into the in-memory map.
     * Called once when the server starts (world loads).
     * Safe to call even if the file does not yet exist.
     */
    public void load() {
        scales.clear();

        if (!Files.exists(filePath)) {
            Proportionality.LOGGER.info("[Proportionality] No scale data file found at {}; starting fresh.", filePath);
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    double scale = entry.getValue().getAsDouble();
                    scales.put(uuid, scale);
                } catch (IllegalArgumentException e) {
                    Proportionality.LOGGER.warn(
                            "[Proportionality] Skipping malformed entry '{}' in scale data file.", entry.getKey());
                }
            });
            Proportionality.LOGGER.info("[Proportionality] Loaded scale data for {} player(s).", scales.size());
        } catch (IOException e) {
            Proportionality.LOGGER.error("[Proportionality] Failed to read scale data file: {}", e.getMessage());
        }
    }

    /**
     * Writes the current in-memory map back to disk.
     * Called automatically by {@link #setScale(UUID, float)}, and again when the
     * server stops.
     */
    public void save() {
        // Ensure the data directory exists (it always should on a running server, but
        // be safe).
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            Proportionality.LOGGER.error("[Proportionality] Could not create data directory: {}", e.getMessage());
            return;
        }

        JsonObject root = new JsonObject();
        scales.forEach((uuid, scale) -> root.addProperty(uuid.toString(), scale));

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
     * Returns the saved scale for the given player, or {@link #DEFAULT_SCALE} if no
     * entry exists (first-time player, or data was cleared).
     *
     * @param uuid The player's unique identifier.
     * @return The stored scale value, always positive and at least
     *         {@link #DEFAULT_SCALE}.
     */
    public double getScale(UUID uuid) {
        return scales.getOrDefault(uuid, DEFAULT_SCALE);
    }

    /**
     * Persists a new scale for the given player.
     * Immediately writes the updated map to disk so no data is lost on a crash.
     *
     * @param uuid  The player's unique identifier.
     * @param scale The scale value to store. Should already be validated/clamped by
     *              the caller.
     */
    public void setScale(UUID uuid, double scale) {
        scales.put(uuid, scale);
        save(); // write-through: every change is immediately durable
    }
}
