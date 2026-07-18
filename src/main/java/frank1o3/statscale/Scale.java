package frank1o3.statscale;

public final class Scale {

    private Scale() {
    }

    /**
     * Calculates all scaling values.
     *
     * @param scale    Current entity scale
     * @param maxScale Maximum allowed scale
     */
    public static ScaleProfile calculate(double scale, double maxScale) {

        return new ScaleProfile(
                scale,
                calculate(scale, maxScale, AttributeType.MAX_HEALTH),
                calculate(scale, maxScale, AttributeType.MOVEMENT_SPEED),
                calculate(scale, maxScale, AttributeType.JUMP_STRENGTH),
                calculate(scale, maxScale, AttributeType.ATTACK_DAMAGE),
                calculate(scale, maxScale, AttributeType.REACH),
                calculate(scale, maxScale, AttributeType.STEP_HEIGHT),
                calculate(scale, maxScale, AttributeType.FALL_DISTANCE));
    }

    private static double calculate(
            double scale,
            double maxScale,
            AttributeType type) {

        if (scale <= 1.0) {
            return 1.0;
        }

        double exponent = switch (type) {
            case MAX_HEALTH -> 1.0;
            case ATTACK_DAMAGE -> 0.9;
            case REACH -> 0.85;
            case STEP_HEIGHT -> 0.7;
            case JUMP_STRENGTH -> 0.3;
            case MOVEMENT_SPEED -> 0.35;
            case FALL_DISTANCE -> 0.01;
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