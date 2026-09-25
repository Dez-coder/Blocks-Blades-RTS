package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Helper for marking villagers (and warriors converted from them) that were
 * spawned by the RTS system.
 *
 * Vanilla villagers and RTS villagers are both plain {@link Villager}
 * instances, so there is no way to tell them apart by type alone. We attach
 * a persistent scoreboard-style tag to every villager we spawn; the tag is
 * stored in the entity NBT and is available on the server, which lets the
 * server (profession handling) distinguish "our" villagers from vanilla ones.
 *
 * Note: entity tags are NOT synced to clients, so client-side selection must
 * use {@link RTSOwnedClient} instead, which is fed by {@link RTSSyncOwnedPacket}.
 */
public final class RTSVillagerTags {

    /** Tag applied to every villager spawned through the RTS system. */
    public static final String OWNED_TAG = "blocks_blades_owned";

    /** Tag prefix storing the UUID of the player that owns an entity. */
    public static final String OWNER_TAG_PREFIX = "blocks_blades_owner:";

    /** Tag applied to a villager that was promoted into a warrior. */
    public static final String WARRIOR_TAG = "blocks_blades_warrior";

    private RTSVillagerTags() {
    }

    /** Marks an entity as owned by the RTS system. */
    public static void markOwned(Entity entity) {
        if (entity != null) {
            entity.addTag(OWNED_TAG);
        }
    }

    /** Returns true if the entity is a villager spawned by the RTS system. */
    public static boolean isOwned(Entity entity) {
        return entity instanceof Villager && entity.getTags().contains(OWNED_TAG);
    }

    /** Marks an entity as an RTS warrior (converted from a villager). */
    public static void markWarrior(Entity entity) {
        if (entity != null) {
            entity.addTag(WARRIOR_TAG);
        }
    }

    /** Returns true if the entity is an RTS warrior. */
    public static boolean isWarrior(Entity entity) {
        return entity != null && entity.getTags().contains(WARRIOR_TAG);
    }

    /**
     * Records which player this entity belongs to. Entity tags are written to
     * the entity's NBT, so the owner survives world reloads and can be used to
     * re-create orders (for example a farmer's farm task) that only live in
     * memory.
     */
    public static void markOwner(Entity entity, UUID owner) {
        if (entity == null || owner == null) {
            return;
        }

        for (String tag : new ArrayList<>(entity.getTags())) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }

        entity.addTag(OWNER_TAG_PREFIX + owner);
    }

    /** Returns the owning player's UUID, or {@code null} if none was recorded. */
    public static UUID getOwner(Entity entity) {
        if (entity == null) {
            return null;
        }

        for (String tag : entity.getTags()) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }

        return null;
    }
}
