package frank1o3.statscale.client.mixin.accessors;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code InventoryScreen#extractRenderState}, which is private/static
 * and used internally by vanilla's inventory entity preview. Proportionality
 * reuses it in {@link frank1o3.statscale.client.gui.ScaleGuiUtils} to render a
 * live, correctly-scaled player preview in the scale screens.
 *
 * <p>
 * This is a pure accessor — it does not alter the method's behaviour in any
 * way, so it's safe to coexist with any other mod that also accesses this
 * method via its own accessor mixin.
 */
@Mixin(InventoryScreen.class)
public interface InventoryScreenAccessor {

    @Invoker("extractRenderState")
    static EntityRenderState invokeExtractRenderState(LivingEntity entity) {
        throw new AssertionError("Mixin injection failed: InventoryScreenAccessor");
    }
}