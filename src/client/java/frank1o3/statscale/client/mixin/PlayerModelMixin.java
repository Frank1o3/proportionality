package frank1o3.statscale.client.mixin;

import frank1o3.statscale.client.ScaleClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<HumanoidRenderState> {

    protected PlayerModelMixin() {
        super(null);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"), cancellable = false)
    private void proportionality$adjustWalkAnimation(AvatarRenderState state, CallbackInfo ci) {
        if (!(state instanceof HumanoidRenderState renderState)) {
            return;
        }

        float factor = ScaleClientState.getAnimationScaleFactor();
        if (factor == 1.0f) {
            return;
        }

        renderState.walkAnimationSpeed *= factor;
        renderState.walkAnimationPos *= factor;
    }
}
