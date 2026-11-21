package VLADMIX90.towers.wave;

import java.util.List;

public record WaveTypeSpec(String id, int chance, List<WaveMobSpec> mobs, List<String> lootIds, int waves) {
    public WaveTypeSpec(String id, int chance, List<WaveMobSpec> mobs, List<String> lootIds) {
        this(id, chance, mobs, lootIds, -1);
    }
}
