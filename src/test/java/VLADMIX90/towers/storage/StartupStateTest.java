package VLADMIX90.towers.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartupStateTest {
    @Test void stopAndFailureCannotBecomeReadyFromLateInitialization() {
        StartupState state = new StartupState();
        assertFalse(state.ready()); assertTrue(state.markReady()); assertTrue(state.ready());
        state.stop(); assertFalse(state.ready()); assertFalse(state.markReady());
        StartupState failed = new StartupState(); failed.fail();
        assertFalse(failed.markReady()); assertEquals(StartupState.Phase.FAILED, failed.phase());
    }
}
