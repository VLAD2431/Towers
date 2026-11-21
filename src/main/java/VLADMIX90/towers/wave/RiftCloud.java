package VLADMIX90.towers.wave;

import java.util.List;

/**
 * The particle cloud that follows the rift.
 *
 * <p>The cloud is sent to every player that is close enough to see it, so the rift can be
 * spotted long before the player reaches it. It stays visible while the rift exists.
 *
 * @param particles    the particles of one cloud
 * @param interval     the ticks between two clouds
 * @param viewDistance how far the cloud is shown, in blocks
 */
public record RiftCloud(List<RiftParticleSpec> particles, int interval, int viewDistance) {

    public RiftCloud {
        particles = particles == null ? List.of() : List.copyOf(particles);
    }

    /** A cloud that is never shown. */
    public static final RiftCloud DISABLED = new RiftCloud(List.of(), 1, 0);

    public boolean enabled() {
        return !particles.isEmpty() && interval > 0 && viewDistance > 0;
    }
}
