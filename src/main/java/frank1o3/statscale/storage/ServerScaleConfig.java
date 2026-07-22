package frank1o3.statscale.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ServerScaleConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("proportionality");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "statscale.json");

    // Default configuration values
    public double maxScaleLimit = 16.0f;
    /** Days an inactive player's scale entry is retained; 0 disables cleanup. */
    public int scaleDataRetentionDays = 30;
    public double exponentMaxHealth = 1;
    public double exponentAttackDamage = 0.9;
    public double exponentReach = 0.8;
    public double exponentStepHeight = 0.85;
    public double exponentJumpStrength = 0.4;
    public double exponentMovementSpeed = 0.4;
    public double exponentFallDistance = 0.5;
    public double exponentKnockBackResistance = 0.25;

    public static ServerScaleConfig load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                ServerScaleConfig config = GSON.fromJson(reader, ServerScaleConfig.class);
                if (config != null) {
                    return config;
                }
                LOGGER.warn("[Proportionality] Config file was empty or malformed; using defaults.");
            } catch (Exception e) {
                LOGGER.error("[Proportionality] Failed to read config file: {}", e.getMessage());
            }
        }

        // File missing or unreadable — write defaults so the admin can edit it.
        ServerScaleConfig config = new ServerScaleConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            LOGGER.error("[Proportionality] Failed to write config file: {}", e.getMessage());
        }
    }
}
