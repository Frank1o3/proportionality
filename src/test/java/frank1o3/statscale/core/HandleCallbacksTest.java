package frank1o3.statscale.core;

import frank1o3.statscale.storage.ServerScaleConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleCallbacksTest {

    @Test
    void reusesCachedProfileWhenScaleAndConfigMatch() {
        ServerScaleConfig config = new ServerScaleConfig();
        String signature = ScaleProfileCache.createConfigSignature(config);
        ScaleProfileCache.AppliedProfileState state = new ScaleProfileCache.AppliedProfileState(2.0, 4.0, signature);

        assertTrue(ScaleProfileCache.shouldReuseCachedProfile(state, 2.0, 4.0, signature));
    }

    @Test
    void doesNotReuseCachedProfileWhenInputsChange() {
        ServerScaleConfig config = new ServerScaleConfig();
        String signature = ScaleProfileCache.createConfigSignature(config);
        ScaleProfileCache.AppliedProfileState state = new ScaleProfileCache.AppliedProfileState(2.0, 4.0, signature);

        assertFalse(ScaleProfileCache.shouldReuseCachedProfile(state, 2.5, 4.0, signature));
        assertFalse(ScaleProfileCache.shouldReuseCachedProfile(state, 2.0, 3.5, signature));
        assertFalse(ScaleProfileCache.shouldReuseCachedProfile(state, 2.0, 4.0, "different"));
    }

    @Test
    void removesDisconnectedPlayerProfile() {
        UUID playerId = UUID.randomUUID();
        ScaleProfileCache.AppliedProfileState state = new ScaleProfileCache.AppliedProfileState(2.0, 4.0, "test");

        ScaleProfileCache.put(playerId, state);
        ScaleProfileCache.remove(playerId);

        assertTrue(ScaleProfileCache.get(playerId) == null);
    }

    @Test
    void floorsZeroScaleBeforeApplyingNegativeExponent() {
        ServerScaleConfig config = new ServerScaleConfig();
        config.exponentMaxHealth = -1.0;

        assertTrue(Double.isFinite(Scale.calculate(0.0, 4.0, config).MAX_HEALTH()));
    }
}
