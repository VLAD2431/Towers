package VLADMIX90.towers.wave;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * A rift display entity driven by keyframe animations.
 *
 * <p>Lifecycle: the rift spawns invisible and stays that way until it may open. With
 * {@link RiftOpenMode#DISTANCE} that happens when a player comes within
 * {@link RiftSpec#openDistance()} blocks ({@link Phase#WAIT}); with {@link RiftOpenMode#CHUNK} the
 * rift sleeps ({@link Phase#DORMANT}) until a player enters the chunk it stands in. Its particle
 * cloud is shown while the rift exists. The open keyframes then play once ({@link Phase#OPEN}),
 * followed by an endlessly looping pulse ({@link Phase#PULSE}). {@link #despawn()} plays the close
 * keyframes and removes the entity afterwards.
 */
public class Rift {

    private enum Phase { DORMANT, WAIT, OPEN, PULSE, IDLE, CLOSE, CLOSED }

    private static final int APPROACH_CHECK_INTERVAL = 5;

    private final ItemDisplay display;
    private final Location location;
    private final float scale;
    private final List<RiftFrame> openFrames;
    private final List<RiftFrame> pulseFrames;
    private final List<RiftFrame> closeFrames;
    private final int openDistance;
    private final double openDistanceSquared;
    private final RiftOpenMode openMode;
    private final RiftCloud cloud;
    /** Only this player may open a quest rift; null keeps standalone test rifts public. */
    private final UUID ownerId;

    private final BukkitTask task;

    private Phase phase = Phase.WAIT;
    private int ticksInPhase;
    private int nextFrameIndex;
    private boolean closed;
    /** Told once when the rift opens; the game starts the wave then. */
    private Runnable onOpened;
    private Runnable onInvalidated;

    private Rift(ItemDisplay display, Location location, JavaPlugin plugin, RiftSpec spec, UUID ownerId, Runnable onOpened, Runnable onInvalidated) {
        this.display = display;
        this.location = location.clone();
        this.scale = spec.scale();
        this.openFrames = spec.openFrames();
        this.pulseFrames = spec.pulseFrames();
        this.closeFrames = spec.closeFrames();
        this.openDistance = spec.openDistance();
        this.openDistanceSquared = (double) spec.openDistance() * spec.openDistance();
        this.openMode = spec.openMode() == null ? RiftOpenMode.DISTANCE : spec.openMode();
        this.cloud = spec.cloud() == null ? RiftCloud.DISABLED : spec.cloud();
        this.ownerId = ownerId;
        this.onOpened = onOpened;
        this.onInvalidated = onInvalidated;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        applyInitial();
    }

    /** Keeps the freshly spawned rift invisible until it may open. */
    private void applyInitial() {
        if (openMode == RiftOpenMode.CHUNK) {
            // The rift sleeps: it opens when a player enters its chunk.
            phase = Phase.DORMANT;
            apply(new RiftFrame(0, 0f, 0f, 0f), 0);
            return;
        }
        if (openDistance > 0) {
            apply(new RiftFrame(0, 0f, 0f, 0f), 0);
            return;
        }
        enterOpen();
    }

    public static Rift spawn(JavaPlugin plugin, Location center, ItemStack item, RiftSpec spec) {
        return spawn(plugin, center, item, spec, null, null, null);
    }

    /**
     * Spawns a standalone rift; every player may activate it.
     */
    public static Rift spawn(JavaPlugin plugin, Location center, ItemStack item, RiftSpec spec, Runnable onOpened) {
        return spawn(plugin, center, item, spec, null, onOpened, null);
    }

    /** Spawns an owner-bound quest rift. */
    public static Rift spawn(JavaPlugin plugin, Location center, ItemStack item, RiftSpec spec, UUID ownerId, Runnable onOpened) {
        return spawn(plugin, center, item, spec, ownerId, onOpened, null);
    }

    /** Spawns an owner-bound rift with callbacks for opening and unexpected display removal. */
    public static Rift spawn(JavaPlugin plugin, Location center, ItemStack item, RiftSpec spec, UUID ownerId,
                             Runnable onOpened, Runnable onInvalidated) {
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class);
        display.setItemStack(item);
        display.setBrightness(new Display.Brightness(clamp(spec.blockLight()), clamp(spec.skyLight())));
        display.setViewRange(spec.viewDistance());
        display.setPersistent(false);
        return new Rift(display, center, plugin, spec, ownerId, onOpened, onInvalidated);
    }

    private static int clamp(int light) {
        return Math.max(0, Math.min(15, light));
    }

    private void tick() {
        if (phase == Phase.CLOSED) return;
        if (!display.isValid()) {
            phase = Phase.CLOSED;
            task.cancel();
            notifyInvalidated();
            return;
        }
        showCloud();
        switch (phase) {
            case DORMANT -> {
                ticksInPhase++;
                if (ticksInPhase % APPROACH_CHECK_INTERVAL == 0 && playerInChunk()) enterOpen();
            }
            case WAIT -> {
                ticksInPhase++;
                if (ticksInPhase % APPROACH_CHECK_INTERVAL == 0 && playerNearby()) enterOpen();
            }
            case OPEN, PULSE, CLOSE -> {
                ticksInPhase++;
                advance();
            }
            case IDLE, CLOSED -> { }
        }
    }

    private boolean playerNearby() {
        for (Player player : location.getWorld().getPlayers()) {
            if (!mayOpen(player)) continue;
            if (player.getLocation().distanceSquared(location) <= openDistanceSquared) return true;
        }
        return false;
    }

    /** True when the authorized player stands in the chunk the rift is in; then the rift wakes up. */
    private boolean playerInChunk() {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (Player player : location.getWorld().getPlayers()) {
            if (!mayOpen(player)) continue;
            Location spot = player.getLocation();
            if (spot == null || spot.getWorld() == null || !spot.getWorld().equals(location.getWorld())) continue;
            if ((spot.getBlockX() >> 4) == chunkX && (spot.getBlockZ() >> 4) == chunkZ) return true;
        }
        return false;
    }

    private boolean mayOpen(Player player) {
        return ownerId == null || ownerId.equals(player.getUniqueId());
    }

    /** Shows the cloud of the rift to every player that is close enough to see it. */
    private void showCloud() {
        if (!cloud.enabled()) return;
        if (ticksInPhase % cloud.interval() != 0) return;
        double viewSquared = (double) cloud.viewDistance() * cloud.viewDistance();
        for (Player player : location.getWorld().getPlayers()) {
            Location spot = player.getLocation();
            if (spot == null || spot.getWorld() == null || !spot.getWorld().equals(location.getWorld())) continue;
            if (spot.distanceSquared(location) > viewSquared) continue;
            for (RiftParticleSpec spec : cloud.particles()) {
                if (spec.data() == null) {
                    player.spawnParticle(spec.type(), location, spec.count(),
                            spec.offsetX(), spec.offsetY(), spec.offsetZ(), spec.speed());
                } else {
                    player.spawnParticle(spec.type(), location, spec.count(),
                            spec.offsetX(), spec.offsetY(), spec.offsetZ(), spec.speed(), spec.data());
                }
            }
        }
    }

    private void enterOpen() {
        resetTo(Phase.OPEN);
        notifyOpened();
        if (openFrames.isEmpty()) { enterPulse(); return; }
        apply(openFrames.get(0), 0);
        if (openFrames.size() < 2) { enterPulse(); return; }
        apply(openFrames.get(1), segment(openFrames, 0, 1));
        nextFrameIndex = 2;
    }

    /** Tells the game that the rift opened; that happens only once. */
    private void notifyOpened() {
        if (onOpened == null) return;
        Runnable callback = onOpened;
        onOpened = null;
        callback.run();
    }

    private void notifyInvalidated() {
        if (closed || onInvalidated == null) return;
        Runnable callback = onInvalidated;
        onInvalidated = null;
        callback.run();
    }

    private void enterPulse() {
        resetTo(pulseFrames.size() < 2 ? Phase.IDLE : Phase.PULSE);
        if (phase != Phase.PULSE) return;
        apply(pulseFrames.get(0), 0);
        apply(pulseFrames.get(1), segment(pulseFrames, 0, 1));
        nextFrameIndex = 2;
    }

    private void enterClose() {
        resetTo(Phase.CLOSE);
        if (closeFrames.size() < 2) { remove(); return; }
        // No jump to the entry frame: the client blends from the current pose toward frame 1.
        apply(closeFrames.get(1), segment(closeFrames, 0, 1));
        nextFrameIndex = 2;
    }

    /** Ticks from frame {@code from} to frame {@code to} within the same phase. */
    private static int segment(List<RiftFrame> frames, int from, int to) {
        return Math.max(0, frames.get(to).tick() - frames.get(from).tick());
    }

    private void resetTo(Phase phase) {
        this.phase = phase;
        this.ticksInPhase = 0;
        this.nextFrameIndex = 0;
    }

    private void advance() {
        List<RiftFrame> frames = framesFor(phase);
        if (nextFrameIndex >= frames.size()) {
            // Wait for the blend toward the last frame to complete before ending the phase.
            if (ticksInPhase < frames.get(frames.size() - 1).tick()) return;
            switch (phase) {
                case OPEN -> enterPulse();
                case PULSE -> loopPulse(frames);
                default -> remove();
            }
            return;
        }
        // A frame is targeted one segment before its tick, so its pose is reached exactly on schedule.
        if (ticksInPhase < frames.get(nextFrameIndex - 1).tick()) return;
        apply(frames.get(nextFrameIndex), segment(frames, nextFrameIndex - 1, nextFrameIndex));
        nextFrameIndex++;
    }

    /** Restarts the pulse from its first frame, keeping the cycle period equal to the last frame's tick. */
    private void loopPulse(List<RiftFrame> frames) {
        RiftFrame last = frames.get(frames.size() - 1);
        int overflow = ticksInPhase - last.tick();
        resetTo(Phase.PULSE);
        ticksInPhase = overflow;
        apply(frames.get(0), 0);
        apply(frames.get(1), segment(frames, 0, 1));
        nextFrameIndex = 2;
    }

    private List<RiftFrame> framesFor(Phase phase) {
        return switch (phase) {
            case OPEN -> openFrames;
            case PULSE -> pulseFrames;
            case CLOSE -> closeFrames;
            default -> List.of();
        };
    }

    /** Sets the entity's scale target, letting the client interpolate to it over {@code durationTicks}. */
    private void apply(RiftFrame frame, int durationTicks) {
        display.setInterpolationDuration(durationTicks);
        display.setInterpolationDelay(-1);
        float x = frame.x() * scale;
        float y = frame.y() * scale;
        float z = frame.z() * scale;
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(x, y, z), new AxisAngle4f()));
    }

    private void remove() {
        phase = Phase.CLOSED;
        task.cancel();
        if (display.isValid()) display.remove();
    }

    public Location location() { return location.clone(); }

    public void despawn() {
        if (closed) return;
        closed = true;
        if (phase == Phase.DORMANT || phase == Phase.WAIT || phase == Phase.CLOSED) { task.cancel(); remove(); return; }
        if (closeFrames.size() < 2) { task.cancel(); remove(); return; }
        enterClose();
    }

    /**
     * Removes the rift immediately, skipping the close animation. Used when the server is shutting down
     * or the plugin is disabling, where the scheduler will not run another tick.
     */
    public void despawnNow() {
        if (closed) return;
        closed = true;
        remove();
    }
}
