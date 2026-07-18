package frank1o3.statscale.core;

import frank1o3.statscale.storage.ServerScaleConfig;

public final class Scale {

    private Scale() {
    }

    /**
     * Calculates all scaling values.
     *
     * @param scale    Current entity scale
     * @param maxScale Maximum allowed scale
     */
    public static ScaleProfile calculate(double scale, double maxScale, ServerScaleConfig config) {

        return new ScaleProfile(
                scale,
                calculate(scale, maxScale, AttributeType.MAX_HEALTH, config),
                calculate(scale, maxScale, AttributeType.MOVEMENT_SPEED, config),
                calculate(scale, maxScale, AttributeType.JUMP_STRENGTH, config),
                calculate(scale, maxScale, AttributeType.ATTACK_DAMAGE, config),
                calculate(scale, maxScale, AttributeType.REACH, config),
                calculate(scale, maxScale, AttributeType.STEP_HEIGHT, config),
                calculate(scale, maxScale, AttributeType.FALL_DISTANCE, config));
    }

    private static double calculate(
            double scale,
            double maxScale,
            AttributeType type,
            ServerScaleConfig config) {

        if (scale <= 1.0) {
            return 1.0;
        }

        double exponent = switch (type) {
            case MAX_HEALTH -> config.exponentMaxHealth;
            case ATTACK_DAMAGE -> config.exponentAttackDamage;
            case REACH -> config.exponentReach;
            case STEP_HEIGHT -> config.exponentStepHeight;
            case JUMP_STRENGTH -> config.exponentJumpStrength;
            case MOVEMENT_SPEED -> config.exponentMovementSpeed;
            case FALL_DISTANCE -> config.exponentFallDistance;
        };

        return Math.pow(scale, exponent);
    }

    public enum AttributeType {
        MAX_HEALTH,
        MOVEMENT_SPEED,
        JUMP_STRENGTH,
        ATTACK_DAMAGE,
        REACH,
        STEP_HEIGHT,
        FALL_DISTANCE
    }

    public record ScaleProfile(
            double scale,
            double MAX_HEALTH,
            double movementSpeed,
            double jumpHeight,
            double attackDamage,
            double reach,
            double stepHeight,
            double fallDistance) {
    }
}