package frank1o3.statscale.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import frank1o3.statscale.client.mixin.accessors.InventoryScreenAccessor;

/**
 * Shared drawing helpers for Proportionality's client-side screens.
 *
 * <p>
 * Pulled out of {@link ScaleSlider} and the screen classes so the same
 * scrolling-text and entity-preview logic isn't duplicated between
 * {@code ScaleScreen} and the admin panel.
 */
@Environment(EnvType.CLIENT)
public final class ScaleGuiUtils {

    private static final float ENTITY_PREVIEW_Y_OFFSET = 0.0625F;

    private ScaleGuiUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public enum Justify {
        LEFT, CENTER
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    /**
     * Draws {@code text} between {@code left} and {@code right}, horizontally
     * scrolling it if it's wider than the available space, otherwise justifying
     * it per {@code justify}. Shared by {@link ScaleSlider} and any button/label
     * that needs the same "shrink to fit, then marquee" behaviour.
     */
    public static void drawFittedText(Justify justify, GuiGraphicsExtractor graphics, Font font,
            Component text, int left, int top, int right, int bottom, int color) {
        color = ARGB.opaque(color);
        int textWidth = font.width(text);
        int textY = (top + bottom - font.lineHeight) / 2;
        int available = right - left;

        if (textWidth <= available) {
            int x = switch (justify) {
                case CENTER -> left + (available - textWidth) / 2;
                case LEFT -> left;
            };
            graphics.text(font, text, x, textY, color, false);
            return;
        }

        // Oscillating scroll, matching vanilla's own scrollable-label behaviour.
        int overflow = textWidth - available;
        double millis = Util.getMillis() / 1000.0;
        double period = Math.max(overflow * 0.5, 3.0);
        double factor = Math.sin(Math.PI / 2.0 * Math.cos(Math.PI * 2.0 * millis / period)) / 2.0 + 0.5;
        int scrollX = (int) Mth.lerp(factor, 0.0, overflow);

        graphics.enableScissor(left, top, right, bottom);
        FormattedCharSequence sequence = text.getVisualOrderText();
        graphics.text(font, sequence, left - scrollX, textY, color, false);
        graphics.disableScissor();
    }

    // -------------------------------------------------------------------------
    // Entity preview
    // -------------------------------------------------------------------------

    /**
     * Renders {@code entity} inside the given screen-space box, facing the mouse
     * cursor, at its true current scale (unlike vanilla's inventory preview,
     * which normalises entity scale back to 1.0). This lets a scale screen show
     * a live, correctly-sized preview of the player as the slider moves.
     *
     * @param entity the entity to render — pass the local player for the
     *               self-service screen, or the resolved target player for the
     *               admin screen (only meaningful while they're online).
     */
    public static void drawScaledEntityPreview(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
            int size, float mouseX, float mouseY, LivingEntity entity) {
        float centerX = (x1 + x2) / 2.0F;
        float centerY = (y1 + y2) / 2.0F;
        float yaw = (float) Math.atan((centerX - mouseX) / 40.0F);
        float pitch = (float) Math.atan((centerY - mouseY) / 40.0F);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchRotation = new Quaternionf().rotateX(pitch * 20.0F * (float) (Math.PI / 180.0));
        rotation.mul(pitchRotation);

        EntityRenderState state = InventoryScreenAccessor.invokeExtractRenderState(entity);
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F + yaw * 20.0F;
            living.yRot = yaw * 20.0F;
            living.xRot = living.pose != Pose.FALL_FLYING ? -pitch * 20.0F : 0.0F;
            // Deliberately leave living.scale untouched: this is the one place we
            // WANT the real scale attribute to show, so the preview reflects the
            // slider's actual effect on the player's size.
        }

        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + ENTITY_PREVIEW_Y_OFFSET, 0.0F);
        graphics.entity(state, size, translation, rotation, pitchRotation, x1, y1, x2, y2);
    }
}