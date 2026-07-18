package frank1o3.statscale.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ServerScaleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "statscale.json");

    // Default configuration values
    public float maxScaleLimit = 16.0f;
    public double exponentMaxHealth = 1.0;
    public double exponentAttackDamage = 0.9;
    public double exponentReach = 0.85;
    public double exponentStepHeight = 0.7;
    public double exponentJumpStrength = 0.3;
    public double exponentMovementSpeed = 0.35;
    public double exponentFallDistance = 0.01;

    public static ServerScaleConfig load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                ServerScaleConfig config = GSON.fromJson(reader, ServerScaleConfig.class);
                // Return fallback if parsing fails completely
                return config != null ? config : new ServerScaleConfig();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // If file doesn't exist, generate default values and save it
        ServerScaleConfig config = new ServerScaleConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
