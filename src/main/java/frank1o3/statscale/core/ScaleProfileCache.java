package frank1o3.statscale.core;

import frank1o3.statscale.storage.ServerScaleConfig;

public final class ScaleProfileCache {

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

    public record AppliedProfileState(double scale, double maxScale, String configSignature) {
    }
}
