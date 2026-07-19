package frank1o3.statscale.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class AdminScaleClientState {

    public record Result(boolean found, UUID target, String name, double scale, double maxScale, boolean frozen) {
    }

    private static @Nullable Result lastResult;
    private static @Nullable Runnable onUpdate; // set by the open screen to refresh itself

    private AdminScaleClientState() {
    }

    public static @Nullable Result getLastResult() {
        return lastResult;
    }

    public static void setOnUpdate(@Nullable Runnable callback) {
        onUpdate = callback;
    }

    public static void applyInfo(boolean found, UUID target, String name, double scale, double maxScale,
            boolean frozen) {
        lastResult = new Result(found, target, name, scale, maxScale, frozen);
        if (onUpdate != null)
            onUpdate.run();
    }
}