package ru.dez.client.camera;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side registry of villagers that belong to the RTS system.
 *
 * Entity scoreboard tags are not synced to clients, so the client cannot use
 * {@link RTSVillagerTags} to tell RTS villagers apart from vanilla ones. The
 * server therefore pushes the set of owned villager UUIDs to every client via
 * {@link RTSSyncOwnedPacket}, and this class stores it for selection logic.
 */
public final class RTSOwnedClient {

    private static final Set<UUID> OWNED = new LinkedHashSet<>();

    private RTSOwnedClient() {
    }

    public static boolean isOwned(UUID id) {
        return id != null && OWNED.contains(id);
    }

    public static void setAll(Iterable<UUID> ids) {
        OWNED.clear();

        for (UUID id : ids) {
            OWNED.add(id);
        }
    }

    public static void add(UUID id) {
        if (id != null) {
            OWNED.add(id);
        }
    }

    public static void clear() {
        OWNED.clear();
    }
}
