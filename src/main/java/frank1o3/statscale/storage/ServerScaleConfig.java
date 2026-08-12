package frank1o3.statscale.storage;

import com.frank1o3.franklylib.config.ConfigEntry;

public class ServerScaleConfig {
    // Default configuration values
    @ConfigEntry
    public double maxScaleLimit = 16.0f;

    /** Days an inactive player's scale entry is retained; 0 disables cleanup. */
    @ConfigEntry
    public int scaleDataRetentionDays = 30;

    @ConfigEntry
    public double exponentMaxHealth = 1;

    @ConfigEntry
    public double exponentAttackDamage = 0.9;

    @ConfigEntry
    public double exponentReach = 0.8;

    @ConfigEntry
    public double exponentStepHeight = 0.85;

    @ConfigEntry
    public double exponentJumpStrength = 0.4;

    @ConfigEntry
    public double exponentMovementSpeed = 0.4;

    @ConfigEntry
    public double exponentFallDistance = 0.5;

    @ConfigEntry
    public double exponentKnockBackResistance = 0.25;
}
