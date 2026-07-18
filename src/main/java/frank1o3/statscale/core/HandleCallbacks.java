package frank1o3.statscale.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import frank1o3.statscale.storage.ServerScaleConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Handles the application of scale-derived attribute profiles to
 * {@link ServerPlayer} instances.
 *
 * <p>
 * The core logic is exposed through two entry points:
 * <ul>
 * <li>{@link #MeSet} – the Brigadier command callback
 * ({@code /scale set <value>}).</li>
 * <li>{@link #applyScaleProfile} – called programmatically by the packet
 * handler and on
 * player login to restore a saved scale without going through the command
 * system.</li>
 * </ul>
 */
public class HandleCallbacks {

    // -------------------------------------------------------------------------
    // Modifier identifiers – one stable Identifier per attribute slot.
    // Using fixed IDs means previous modifiers are cleanly replaced on each
    // profile application without accumulating stale entries.
    // -------------------------------------------------------------------------

    private static final Identifier SCALE_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_scale");
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_health");
    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_speed");
    private static final Identifier JUMP_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_jump");
    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_damage");
    private static final Identifier STEP_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_step");
    private static final Identifier REACH_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_reach");
    private static final Identifier FALL_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_fall");

    // -------------------------------------------------------------------------
    // Command entry point
    // -------------------------------------------------------------------------

    /**
     * Brigadier command callback for {@code /scale set <value>}.
     *
     * <p>
     * Reads the {@code value} argument, resolves the safe attribute cap, then
     * delegates to {@link #applyScaleProfile}.
     */
    public static int MeSet(CommandContext<CommandSourceStack> context, ServerScaleConfig config)
            throws CommandSyntaxException {
        float inputScale = context.getArgument("value", float.class);
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        AttributeInstance scaleInstance = player.getAttribute(Attributes.SCALE);
        if (scaleInstance == null) {
            return 0;
        }

        double maxScale = getSafeMaxScale(scaleInstance.getAttribute());
        float finalScale = (float) Math.min(inputScale, maxScale);

        applyScaleProfile(player, finalScale, maxScale, config);

        player.sendSystemMessage(Component.literal(
                "Applied stat scaling profile for size: " + finalScale));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Core API
    // -------------------------------------------------------------------------

    /**
     * Calculates and applies a full {@link Scale.ScaleProfile} to every targeted
     * attribute on the given player.
     *
     * <p>
     * This is the single point of truth for attribute mutation. Both the command
     * callback and the network packet handler route through here, ensuring
     * identical
     * behaviour regardless of how the scale change was triggered.
     *
     * <p>
     * After applying attributes this method injects short-lived Regeneration and
     * Saturation effects so the player's health bar fills proportionally to the new
     * maximum rather than showing empty hearts.
     *
     * @param player   The server-side player whose attributes will be modified.
     * @param scale    The target logical scale value (e.g. 3.5). Must be positive.
     * @param maxScale The upper bound used by {@link Scale#calculate} to compute
     *                 multiplier curves. Typically the attribute's hard cap or the
     *                 server-configured maximum, whichever is smaller.
     */
    public static void applyScaleProfile(ServerPlayer player, double scale, double maxScale, ServerScaleConfig config) {
        Scale.ScaleProfile profile = Scale.calculate(scale, maxScale, config);
        float healthPercentage = player.getHealth() / player.getMaxHealth();

        applyModifier(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, profile.MAX_HEALTH());
        applyModifier(player.getAttribute(Attributes.SCALE), SCALE_MODIFIER_ID, profile.scale());
        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, profile.movementSpeed());
        applyModifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP_MODIFIER_ID, profile.jumpHeight());
        applyModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, profile.attackDamage());
        applyModifier(player.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER_ID, profile.stepHeight());
        applyModifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_MODIFIER_ID, profile.reach());
        applyModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_MODIFIER_ID, profile.fallDistance());

        float newMaxHeath = player.getMaxHealth();
        player.setHealth(newMaxHeath * healthPercentage);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Strips the previous iteration of a named modifier and adds a fresh one whose
     * {@code ADD_VALUE} amount drives the instance to exactly
     * {@code base * multiplier}.
     *
     * @param instance   The attribute instance to modify. Ignored if {@code null}.
     * @param id         The stable {@link Identifier} used to find and replace the
     *                   modifier across multiple invocations.
     * @param multiplier The desired ratio relative to the attribute's base value
     *                   (e.g. 2.0 = twice the vanilla default).
     */
    private static void applyModifier(AttributeInstance instance, Identifier id, double multiplier) {
        if (instance == null) {
            return;
        }

        // Remove the previous modifier so we don't stack deltas across calls.
        instance.removeModifier(id);

        double baseValue = instance.getBaseValue();
        double targetValue = baseValue * multiplier;
        double delta = targetValue - baseValue;

        AttributeModifier modifier = new AttributeModifier(id, delta, AttributeModifier.Operation.ADD_VALUE);
        instance.addPermanentModifier(modifier);
    }

    /**
     * Reads the declared maximum from the scale attribute's {@link RangedAttribute}
     * metadata
     * and caps it at 100 to prevent runaway values on modded servers that declare
     * extreme ranges.
     *
     * @param attributeHolder The {@code Holder<Attribute>} wrapping the scale
     *                        attribute.
     * @return A safe upper bound for use in scale profile calculations.
     */
    private static double getSafeMaxScale(Holder<Attribute> attributeHolder) {
        if (attributeHolder.value() instanceof RangedAttribute ranged) {
            double cap = ranged.getMaxValue();
            return cap > 100.0 ? 100.0 : cap;
        }
        return 16.0;
    }
}
