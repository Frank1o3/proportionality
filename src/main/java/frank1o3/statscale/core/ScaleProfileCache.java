package frank1o3.statscale.core;

import frank1o3.statscale.storage.ServerScaleConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleProfileCache {

    private static final Map<UUID, AppliedProfileState> LAST_APPLIED_PROFILES = new ConcurrentHashMap<>();

    private ScaleProfileCache() {
    }

    public static String createConfigSignature(ServerScaleConfig config) {
        return String.join(":",
                Double.toString(config.exponentMaxHealth),
                Double.toString(config.exponentAttackDamage),
                Double.toString(config.exponentReach),
                Double.toString(config.exponentStepHeight),
                Double.toString(config.exponentJumpStrength),
                Double.toString(config.exponentMovementSpeed),
                Double.toString(config.exponentFallDistance),
                Double.toString(config.exponentKnockBackResistance));
    }

    public static boolean shouldReuseCachedProfile(AppliedProfileState previous, double scale, double maxScale,
            String configSignature) {
        if (previous == null) {
            return false;
        }
        return Double.compare(previous.scale(), scale) == 0
                && Double.compare(previous.maxScale(), maxScale) == 0
                && previous.configSignature().equals(configSignature);
    }

    public static AppliedProfileState get(UUID playerId) {
        return LAST_APPLIED_PROFILES.get(playerId);
    }

    public static void put(UUID playerId, AppliedProfileState state) {
        LAST_APPLIED_PROFILES.put(playerId, state);
    }

    /** Removes a disconnected player's cached profile. */
    public static void remove(UUID playerId) {
        LAST_APPLIED_PROFILES.remove(playerId);
    }

    public record AppliedProfileState(double scale, double maxScale, String configSignature) {
    }
}
