package frank1o3.statscale.core;

import frank1o3.statscale.storage.ServerScaleConfig;
import org.junit.jupiter.api.Test;

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
}
