package VLADMIX90.towers.storage;

import java.util.concurrent.atomic.AtomicReference;

/** Prevents late initialization from reviving a failed or disabled plugin. */
public final class StartupState {
    public enum Phase { STARTING, READY, FAILED, STOPPING }
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.STARTING);
    public Phase phase() { return phase.get(); }
    public boolean ready() { return phase() == Phase.READY; }
    public boolean markReady() { return phase.compareAndSet(Phase.STARTING, Phase.READY); }
    public void fail() { phase.compareAndSet(Phase.STARTING, Phase.FAILED); }
    public void stop() { phase.set(Phase.STOPPING); }
}
