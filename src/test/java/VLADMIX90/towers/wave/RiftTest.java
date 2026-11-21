package VLADMIX90.towers.wave;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiftTest {

    private static final float DELTA = 0.001f;

    private ItemDisplay display;
    private World world;
    private JavaPlugin plugin;
    private BukkitTask task;
    private Runnable ticker;

    private static final List<RiftFrame> OPEN = List.of(
            new RiftFrame(0, 0f, 0f, 0.2f),
            new RiftFrame(2, 1f, 0f, 0.2f),
            new RiftFrame(4, 1f, 0.2f, 0.2f),
            new RiftFrame(7, 1f, 0.2f, 1f),
            new RiftFrame(10, 1f, 1f, 1f));
    private static final List<RiftFrame> PULSE = List.of(
            new RiftFrame(0, 1f, 1f, 1f),
            new RiftFrame(2, 1f, 1.02f, 1.02f),
            new RiftFrame(4, 1.02f, 1.02f, 1f),
            new RiftFrame(6, 1f, 1f, 1f),
            new RiftFrame(8, 1f, 1.02f, 1.02f),
            new RiftFrame(10, 1.02f, 1.02f, 1f),
            new RiftFrame(12, 1f, 1f, 1f));
    private static final List<RiftFrame> CLOSE = List.of(
            new RiftFrame(0, 1f, 1f, 1f),
            new RiftFrame(3, 1f, 0.2f, 1f),
            new RiftFrame(6, 1f, 0.2f, 0.2f),
            new RiftFrame(8, 1f, 0f, 0.2f),
            new RiftFrame(10, 0f, 0f, 0.2f));

    @BeforeEach
    void setUp() {
        display = mock(ItemDisplay.class);
        when(display.isValid()).thenReturn(true);
        world = mock(World.class);
        when(world.spawn(any(Location.class), eq(ItemDisplay.class))).thenReturn(display);
        when(world.getPlayers()).thenReturn(List.of());

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        plugin = mock(JavaPlugin.class);
        when(plugin.getServer()).thenReturn(server);

        task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> { ticker = invocation.getArgument(1); return task; });
    }

    private RiftSpec spec(int openDistance, List<RiftFrame> open, List<RiftFrame> pulse, List<RiftFrame> close) {
        return new RiftSpec(Material.WOODEN_HOE, "wooden_hoe", "rift", 5f, 15, 64, 15, 15, 
                openDistance, RiftOpenMode.DISTANCE, RiftCloud.DISABLED, open, pulse, close);
    }

    /** The rift sleeps in its chunk until a player enters it. */
    private RiftSpec sleepingSpec(RiftCloud cloud) {
        return new RiftSpec(Material.WOODEN_HOE, "wooden_hoe", "rift", 5f, 15, 64, 15, 15, 
                32, RiftOpenMode.CHUNK, cloud, OPEN, PULSE, CLOSE);
    }

    /** A rift that opens immediately and keeps its cloud. */
    private RiftSpec openSpec(RiftCloud cloud) {
        return new RiftSpec(Material.WOODEN_HOE, "wooden_hoe", "rift", 5f, 15, 64, 15, 15, 
                0, RiftOpenMode.DISTANCE, cloud, OPEN, PULSE, CLOSE);
    }

    private Rift spawn(RiftSpec spec) {
        return Rift.spawn(plugin, new Location(world, 0.5, 80, 0.5), mock(ItemStack.class), spec);
    }

    private Rift spawn(RiftSpec spec, Runnable onOpened) {
        return Rift.spawn(plugin, new Location(world, 0.5, 80, 0.5), mock(ItemStack.class), spec, onOpened);
    }

    private Rift spawnOwned(RiftSpec spec, UUID ownerId, Runnable onOpened) {
        return Rift.spawn(plugin, new Location(world, 0.5, 80, 0.5), mock(ItemStack.class), spec, ownerId, onOpened);
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) ticker.run();
    }

    @Test void invalidDisplayNotifiesOwnerOnce() {
        AtomicBoolean invalidated = new AtomicBoolean();
        Rift rift = Rift.spawn(plugin, new Location(world, 0.5, 80, 0.5), mock(ItemStack.class),
                sleepingSpec(RiftCloud.DISABLED), UUID.randomUUID(), null, () -> invalidated.set(true));
        when(display.isValid()).thenReturn(false);
        ticker.run();
        assertTrue(invalidated.get());
        verify(task).cancel();
    }

    /** Last scale triple passed to setTransformation. */
    private float[] lastScale() {
        ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
        verify(display, atLeastOnce()).setTransformation(captor.capture());
        List<Transformation> all = captor.getAllValues();
        var scale = all.get(all.size() - 1).getScale();
        return new float[]{scale.x(), scale.y(), scale.z()};
    }

    /** Every interpolation duration passed to the display, in call order. */
    private List<Integer> durations() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(display, atLeastOnce()).setInterpolationDuration(captor.capture());
        return captor.getAllValues();
    }

    private void assertScale(float x, float y, float z) {
        float[] scale = lastScale();
        assertEquals(x * 5f, scale[0], DELTA);
        assertEquals(y * 5f, scale[1], DELTA);
        assertEquals(z * 5f, scale[2], DELTA);
    }

    @Test
    void staysInvisibleWhileWaiting() {
        spawn(spec(32, OPEN, PULSE, CLOSE));
        ticks(40);
        verify(display, times(1)).setTransformation(any(Transformation.class));
        assertScale(0f, 0f, 0f);
    }

    @Test
    void opensWhenPlayerApproaches() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 5, 80, 5));
        when(world.getPlayers()).thenReturn(List.of(player));
        spawn(spec(32, OPEN, PULSE, CLOSE));
        ticks(5);
        assertScale(1f, 0f, 0.2f);
    }

    @Test
    void farPlayerDoesNotTrigger() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 500, 80, 500));
        when(world.getPlayers()).thenReturn(List.of(player));
        spawn(spec(32, OPEN, PULSE, CLOSE));
        ticks(40);
        verify(display, times(1)).setTransformation(any(Transformation.class));
    }

    @Test
    void zeroOpenDistanceOpensImmediately() {
        spawn(spec(0, OPEN, PULSE, CLOSE));
        assertScale(1f, 0f, 0.2f);
    }

    @Test
    void cloudViewDistanceIsAppliedToTheRiftDisplay() {
        RiftCloud cloud = new RiftCloud(List.of(new RiftParticleSpec(Particle.FLAME, 1, 0, 0, 0, 0, null)), 1, 128);

        spawn(openSpec(cloud));

        verify(display).setViewRange(64f);
    }

    /** Each frame is targeted one segment before its tick, so its pose is reached exactly on schedule. */
    @Test
    void openFramesReachPosesOnSchedule() {
        spawn(spec(0, OPEN, PULSE, CLOSE));
        assertScale(1f, 0f, 0.2f);
        List<Integer> durations = durations();
        assertEquals(0, durations.get(0));
        assertEquals(2, durations.get(1));
        ticks(2);
        assertScale(1f, 0.2f, 0.2f);
        ticks(2);
        assertScale(1f, 0.2f, 1f);
        ticks(3);
        assertScale(1f, 1f, 1f);
    }

    @Test
    void pulseLoopsCyclically() {
        spawn(spec(0, OPEN, PULSE, CLOSE));
        ticks(10);
        // The last open frame lands on t10 and the pulse picks up from here.
        assertScale(PULSE.get(1).x(), PULSE.get(1).y(), PULSE.get(1).z());
        for (RiftFrame frame : PULSE.subList(2, PULSE.size())) {
            ticks(2);
            assertScale(frame.x(), frame.y(), frame.z());
        }
        // The wrap restarts the cycle at the same period (12 ticks) and returns to the first pulse pose.
        ticks(2);
        assertScale(PULSE.get(1).x(), PULSE.get(1).y(), PULSE.get(1).z());
        ticks(2);
        assertScale(PULSE.get(2).x(), PULSE.get(2).y(), PULSE.get(2).z());
    }

    @Test
    void noPulseFramesStaysIdle() {
        spawn(spec(0, OPEN, List.of(), CLOSE));
        // The open list applies 5 poses (frame 0 instantly, the rest as blend targets); nothing more.
        ticks(60);
        ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
        verify(display, times(5)).setTransformation(captor.capture());
    }

    @Test
    void despawnRunsCloseFramesThenRemoves() {
        Rift rift = spawn(spec(0, OPEN, PULSE, CLOSE));
        ticks(10);
        rift.despawn();
        assertScale(1f, 0.2f, 1f);
        verify(display, never()).remove();
        ticks(3);
        assertScale(1f, 0.2f, 0.2f);
        ticks(3);
        assertScale(1f, 0f, 0.2f);
        ticks(2);
        assertScale(0f, 0f, 0.2f);
        verify(display, never()).remove();
        ticks(2);
        verify(display, times(1)).remove();
        verify(task).cancel();
    }

    @Test
    void despawnWhileWaitingRemovesImmediately() {
        Rift rift = spawn(spec(32, OPEN, PULSE, CLOSE));
        rift.despawn();
        verify(display, times(1)).remove();
    }

    @Test
    void despawnMidOpenBlendsSmoothly() {
        Rift rift = spawn(spec(0, OPEN, PULSE, CLOSE));
        ticks(2);
        float[] before = lastScale();
        rift.despawn();
        // No snap to the close entry frame: the blend starts from the current pose.
        float[] after = lastScale();
        assertEquals(before[1], after[1], DELTA);
        assertScale(1f, 0.2f, 1f);
        verify(display, never()).remove();
        ticks(10);
        verify(display, times(1)).remove();
    }

    @Test
    void emptyCloseFramesRemovesImmediately() {
        Rift rift = spawn(spec(0, OPEN, PULSE, List.of()));
        ticks(12);
        rift.despawn();
        verify(display, times(1)).remove();
    }

    @Test
    void despawnTwiceRemovesOnce() {
        Rift rift = spawn(spec(32, OPEN, PULSE, CLOSE));
        rift.despawn();
        rift.despawn();
        verify(display, times(1)).remove();
    }

    @Test
    void despawnNowRemovesWithoutAnimation() {
        Rift rift = spawn(spec(0, OPEN, PULSE, CLOSE));
        ticks(10);
        rift.despawnNow();
        verify(display, times(1)).remove();
        verify(task).cancel();
    }

    @Test
    void tickCancelsWhenDisplayInvalid() {
        spawn(spec(0, OPEN, PULSE, CLOSE));
        when(display.isValid()).thenReturn(false);
        ticks(5);
        verify(task).cancel();
    }

    @Test
    void theSleepingRiftWaitsForItsChunk() {
        AtomicBoolean opened = new AtomicBoolean();
        spawn(sleepingSpec(RiftCloud.DISABLED), () -> opened.set(true));
        ticks(40);
        assertFalse(opened.get(), "the rift must not open while nobody stands in its chunk");
        assertScale(0f, 0f, 0f);
    }

    @Test
    void theSleepingRiftOpensWhenAPlayerEntersItsChunk() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 5, 80, 5));
        when(world.getPlayers()).thenReturn(List.of(player));
        AtomicBoolean opened = new AtomicBoolean();
        spawn(sleepingSpec(RiftCloud.DISABLED), () -> opened.set(true));
        ticks(6);
        assertTrue(opened.get(), "the rift has to open when a player enters its chunk");
    }

    @Test
    void ownerBoundRiftIgnoresAnotherPlayerInItsChunk() {
        Player owner = mock(Player.class);
        Player other = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenReturn(new Location(world, 40, 80, 40));
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getLocation()).thenReturn(new Location(world, 5, 80, 5));
        when(world.getPlayers()).thenReturn(List.of(owner, other));
        AtomicBoolean opened = new AtomicBoolean();

        spawnOwned(sleepingSpec(RiftCloud.DISABLED), ownerId, () -> opened.set(true));
        ticks(40);

        assertFalse(opened.get(), "only the quest owner may activate the rift");
    }

    @Test
    void ownerBoundRiftOpensForItsOwner() {
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenReturn(new Location(world, 5, 80, 5));
        when(world.getPlayers()).thenReturn(List.of(owner));
        AtomicBoolean opened = new AtomicBoolean();

        spawnOwned(sleepingSpec(RiftCloud.DISABLED), ownerId, () -> opened.set(true));
        ticks(6);

        assertTrue(opened.get(), "the quest owner must be able to activate the rift");
    }

    @Test
    void theSleepingRiftIgnoresPlayersOfOtherChunks() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 40, 80, 40));
        when(world.getPlayers()).thenReturn(List.of(player));
        AtomicBoolean opened = new AtomicBoolean();
        spawn(sleepingSpec(RiftCloud.DISABLED), () -> opened.set(true));
        ticks(40);
        assertFalse(opened.get(), "a player of another chunk must not wake the rift");
    }

    private static RiftCloud cloud(RiftParticleSpec... particles) {
        return new RiftCloud(List.of(particles), 1, 128);
    }

    @Test
    void showsBothConfiguredParticles() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 100, 80, 0));
        when(world.getPlayers()).thenReturn(List.of(player));

        RiftParticleSpec ominous = new RiftParticleSpec(Particle.OMINOUS_SPAWNING, 10,
                2.5, 1.5, 2.5, 0.2, null);
        RiftParticleSpec dust = new RiftParticleSpec(Particle.DUST_COLOR_TRANSITION, 5,
                2.5, 0.5, 2.5, 0,
                new Particle.DustTransition(org.bukkit.Color.fromRGB(0xE6FFFF),
                        org.bukkit.Color.fromRGB(0x2BA3D9), 3f));

        spawn(sleepingSpec(cloud(ominous, dust)));
        ticks(1);

        verify(player).spawnParticle(eq(Particle.OMINOUS_SPAWNING), any(Location.class),
                eq(10), eq(2.5), eq(1.5), eq(2.5), eq(0.2));
        verify(player).spawnParticle(eq(Particle.DUST_COLOR_TRANSITION), any(Location.class),
                eq(5), eq(2.5), eq(0.5), eq(2.5), eq(0.0),
                any(Particle.DustTransition.class));
    }

    @Test
    void hidesTheCloudFromFarPlayers() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 300, 80, 0));
        when(world.getPlayers()).thenReturn(List.of(player));

        spawn(sleepingSpec(cloud(new RiftParticleSpec(Particle.PORTAL, 20,
                5, 5, 5, 0, null))));
        ticks(3);

        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void theOpenedRiftKeepsShowingItsCloud() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 100, 80, 0));
        when(world.getPlayers()).thenReturn(List.of(player));

        spawn(sleepingSpec(cloud(new RiftParticleSpec(Particle.PORTAL, 20,
                5, 5, 5, 0, null))));
        ticks(3);
        verify(player, atLeastOnce()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // The player walks into the chunk of the rift: it opens, but the cloud stays.
        when(player.getLocation()).thenReturn(new Location(world, 5, 80, 5));
        ticks(6);
        org.mockito.Mockito.clearInvocations(player);
        ticks(6);

        verify(player, atLeastOnce()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void aRemovedRiftStopsShowingItsCloud() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 100, 80, 0));
        when(world.getPlayers()).thenReturn(List.of(player));

        Rift rift = spawn(sleepingSpec(cloud(new RiftParticleSpec(Particle.PORTAL, 20,
                5, 5, 5, 0, null))));
        ticks(1);
        rift.despawnNow();
        org.mockito.Mockito.clearInvocations(player);
        ticks(3);

        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void theCloudStaysDuringTheCloseAnimationAndStopsAfterIt() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 100, 80, 0));
        when(world.getPlayers()).thenReturn(List.of(player));

        Rift rift = spawn(openSpec(cloud(new RiftParticleSpec(Particle.PORTAL, 20,
                5, 5, 5, 0, null))));
        ticks(12); // the rift is open and pulsing now
        org.mockito.Mockito.clearInvocations(player);

        rift.despawn();
        ticks(1);
        verify(player, atLeastOnce()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());

        org.mockito.Mockito.clearInvocations(player);
        ticks(12); // the close animation is over and the entity is removed
        org.mockito.Mockito.clearInvocations(player);
        ticks(3);
        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
