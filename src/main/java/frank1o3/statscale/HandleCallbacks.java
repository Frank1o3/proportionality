package frank1o3.statscale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier; // The class replaces ResourceLocation
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public class HandleCallbacks {

    // 1. Unified Identifier instantiation using fromNamespaceAndPath
    private static final Identifier SCALE_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_scale");
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_health");
    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_speed");
    private static final Identifier JUMP_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_jump");
    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_damage");
    private static final Identifier STEP_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_step");
    private static final Identifier REACH_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_reach");
    private static final Identifier FALL_MODIFIER_ID = Identifier.fromNamespaceAndPath("statscale", "player_fall");

    public static int MeSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float inputScale = context.getArgument("value", float.class).floatValue();
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        // 2. Fetch Scale Setup & Boundaries
        AttributeInstance scaleInstance = player.getAttribute(Attributes.SCALE);
        if (scaleInstance == null)
            return 0;

        double maxScale = getSafeMaxScale(scaleInstance.getAttribute());
        double finalTargetScale = Math.min((double) inputScale, maxScale);

        // 3. Generate your Scale Multiplier Profile from your Scale class
        Scale.ScaleProfile profile = Scale.calculate(finalTargetScale, maxScale);

        // 4. Apply Multipliers to every targeted Attribute without touching the base
        // defaults
        applyScaleModifier(player.getAttribute(Attributes.SCALE), SCALE_MODIFIER_ID, profile.scale());
        applyScaleModifier(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, profile.MAX_HEALTH());
        applyScaleModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, profile.movementSpeed());
        applyScaleModifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP_MODIFIER_ID, profile.jumpHeight());
        applyScaleModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, profile.attackDamage());
        applyScaleModifier(player.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER_ID, profile.stepHeight());
        applyScaleModifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_MODIFIER_ID,
                profile.reach());
        applyScaleModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_MODIFIER_ID,
                profile.fallDistance());

        // 5. Proportionally scale current health so the player doesn't have empty
        // baseline heart slots
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 255));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 30, 1));

        player.sendSystemMessage(Component.literal("Applied stat scaling profile for size: " + finalTargetScale));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Safely reads the baseValue reference, computes the multiplier delta, and
     * applies the fresh dynamic Identifier modifier.
     */
    private static void applyScaleModifier(AttributeInstance instance, Identifier modifierId, double multiplier) {
        if (instance == null)
            return;

        // Strip previous iterations to completely refresh the profile layout
        instance.removeModifier(modifierId);

        // Pristine baseline number read (Vanilla Default, e.g., 1.0 or 0.1)
        double baseValue = instance.getBaseValue();

        // Absolute target value math
        double targetValue = baseValue * multiplier;

        // Calculate the needed difference relative to the base value
        double amountToAdd = targetValue - baseValue;

        // Pass the updated Identifier instance alongside the new Operation keyword
        // mappings
        AttributeModifier modifier = new AttributeModifier(
                modifierId,
                amountToAdd,
                AttributeModifier.Operation.ADD_VALUE // Maps to the modern 'add_value' format
        );
        instance.addPermanentModifier(modifier);
    }

    private static double getSafeMaxScale(Holder<Attribute> attributeHolder) {
        if (attributeHolder.value() instanceof RangedAttribute rangedAttribute) {
            double maxCap = rangedAttribute.getMaxValue();
            if (maxCap > 100.0d) {
                return 100.0d;
            }
            return maxCap;
        }
        return 16.0d;
    }
}
