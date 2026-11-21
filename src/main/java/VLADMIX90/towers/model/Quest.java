package VLADMIX90.towers.model;

import java.util.UUID;

public record Quest(UUID id, String name, String description, QuestState state, int towerId, String waveType) {
    public Quest withState(QuestState next) {
        return new Quest(id, name, description, next, towerId, waveType);
    }
}
