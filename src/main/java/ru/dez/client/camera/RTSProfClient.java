package ru.dez.client.camera;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RTSProfClient {

    public static final Map<UUID, Integer> PROF = new HashMap<>();
    /** Personal villager levels (0-10), synced with the professions. */
    public static final Map<UUID, Integer> LEVELS = new HashMap<>();

    public static int get(UUID id) {
        return PROF.getOrDefault(id, 0);
    }

    public static int getLevel(UUID id) {
        return LEVELS.getOrDefault(id, 0);
    }

    public static void set(UUID id, int prof) {
        PROF.put(id, prof);
    }

    /** Replaces both the profession and level maps (used when (re)joining). */
    public static void setAll(Map<UUID, Integer> profs, Map<UUID, Integer> levels) {
        PROF.clear();
        PROF.putAll(profs);

        LEVELS.clear();
        LEVELS.putAll(levels);
    }
}
