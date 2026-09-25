package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.PacketDistributor;

public class RTSConstructionManager {

    private static final Map<Integer, Site> SITES = new HashMap<>();

    public static Site create(ServerLevel level, String buildingId, BlockPos origin, UUID owner) {
        RTSBuilding building = RTSBuildings.get(buildingId);

        if (building == null || building.schematic == null) {
            return null;
        }

        RTSProtectedData data = RTSProtectedData.get(level.getServer());

        // Upgrading a mine/lumber camp replaces the previous level: demolish the
        // old structure of the same chain before the new site is placed.
        String previousId = previousLevel(buildingId);

        if (previousId != null) {
            demolishLast(level, data, previousId);
        }

        int id = data.reserveSiteId();

        Site site = new Site(id, level.dimension(), buildingId, origin, building.schematic.entries.size());
        site.owner = owner;
        SITES.put(site.id, site);

        data.addArea(level.dimension(), building.schematic, origin);
        data.upsertSite(site.id, level.dimension().location().toString(), buildingId,
                origin.getX(), origin.getY(), origin.getZ(),
                owner == null ? null : owner.toString(), site.built, site.progress);

        return site;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Returns the id of the level a building upgrades from, or null. */
    private static String previousLevel(String id) {
        if (id == null) {
            return null;
        }

        switch (id) {
            case "mine_2":
                return "mine";
            case "mine_3":
                return "mine_2";
            case "lumber_camp_2":
                return "lumber_camp";
            case "lumber_camp_3":
                return "lumber_camp_2";
            default:
                return null;
        }
    }

    /** Removes the most recently built structure of the given id. */
    private static void demolishLast(ServerLevel level, RTSProtectedData data, String id) {
        RTSProtectedData.PlacedBuilding placed = data.getLastPlacement(id, level.dimension().location().toString());

        if (placed == null) {
            return;
        }

        RTSBuilding building = RTSBuildings.get(id);

        if (building != null && building.schematic != null) {
            for (RTSSchematic.Entry entry : building.schematic.entries) {
                level.setBlockAndUpdate(placed.pos().offset(entry.x, entry.y, entry.z), Blocks.AIR.defaultBlockState());
            }
        }

        data.removePlacement(placed);
    }

    /**
     * Rebuilds every in-progress construction site from the world save. Must
     * be called once when the server starts (before any player can interact
     * with sites) so half-built buildings aren't silently abandoned/lost
     * whenever the world is closed and reopened.
     */
    public static void restore(MinecraftServer server) {
        SITES.clear();

        RTSProtectedData data = RTSProtectedData.get(server);

        for (RTSProtectedData.SiteRecord record : data.sites) {
            RTSBuilding building = RTSBuildings.get(record.buildingId);

            if (building == null || building.schematic == null) {
                continue;
            }

            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(record.dim));
            BlockPos origin = new BlockPos(record.x, record.y, record.z);

            Site site = new Site(record.id, dim, record.buildingId, origin, building.schematic.entries.size());
            site.owner = parseUuid(record.owner);
            site.built = Math.min(record.built, building.schematic.entries.size());
            site.progress = record.progress;

            SITES.put(site.id, site);
        }
    }

    /**
     * Sends every currently active construction site to a (re)joining player
     * so they see the build markers/preview again instead of the site
     * appearing to have vanished.
     */
    public static void resendTo(ServerPlayer player) {
        for (Site site : SITES.values()) {
            if (!site.dim.equals(player.level().dimension())) {
                continue;
            }

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSSiteAddPacket(site.id, site.buildingId, site.origin, site.total)
            );
        }
    }

    public static Site get(int id) {
        return SITES.get(id);
    }

    /** Snapshot of all active construction sites. */
    public static List<Site> getSites() {
        return new ArrayList<>(SITES.values());
    }

    /**
     * Instantly finishes a site that is already placed: the whole schematic is
     * dropped at once and the site is completed (completion bookkeeping, unlock
     * broadcast, etc. all handled by {@link #complete}).
     */
    public static void finishInstantly(ServerLevel level, Site site) {
        if (site == null || !SITES.containsKey(site.id)) {
            return;
        }

        RTSBuildings.place(level, site.buildingId, site.origin);
        complete(level, site);
    }

    public static Site getByPos(ResourceKey<Level> dim, BlockPos pos) {
        for (Site site : SITES.values()) {
            if (site.dim.equals(dim) && site.origin.equals(pos)) {
                return site;
            }
        }

        return null;
    }

    public static BlockPos getCurrentBlock(Site site) {
        RTSBuilding building = RTSBuildings.get(site.buildingId);

        if (building == null || building.schematic == null) {
            return null;
        }

        if (site.built >= building.schematic.entries.size()) {
            return null;
        }

        RTSSchematic.Entry entry = building.schematic.entries.get(site.built);

        return site.origin.offset(entry.x, entry.y, entry.z);
    }

    public static void addWork(ServerLevel level, Site site, float amount) {
        if (site == null || !SITES.containsKey(site.id)) {
            return;
        }

        RTSBuilding building = RTSBuildings.get(site.buildingId);

        if (building == null || building.schematic == null) {
            complete(level, site);
            return;
        }

        site.progress += amount;

        while (site.progress >= 1.0F && site.built < building.schematic.entries.size()) {
            site.progress -= 1.0F;

            RTSSchematic.Entry entry = building.schematic.entries.get(site.built);
            level.setBlockAndUpdate(site.origin.offset(entry.x, entry.y, entry.z), entry.state);
            site.built++;
        }

        RTSProtectedData.get(level.getServer()).upsertSite(site.id, level.dimension().location().toString(),
                site.buildingId, site.origin.getX(), site.origin.getY(), site.origin.getZ(),
                site.owner == null ? null : site.owner.toString(), site.built, site.progress);

        if (site.built >= building.schematic.entries.size()) {
            complete(level, site);
        }
    }

    public static void complete(ServerLevel level, Site site) {
        if (SITES.remove(site.id) == null) {
            return;
        }

        RTSBuildings.place(level, site.buildingId, site.origin);

        RTSProtectedData data = RTSProtectedData.get(level.getServer());
        data.removeSite(site.id);
        data.addCompleted(site.buildingId);
        data.incrementBuilt(site.buildingId);
        data.addPlacement(site.buildingId, level.dimension().location().toString(), site.origin,
                site.owner == null ? null : site.owner.toString());
        RTSBuildings.markCompleted(site.buildingId);

        if (site.buildingId.equals("town_hall")) {
            data.townHallLevel = Math.max(data.townHallLevel, 1);

            // Tell clients right away so the bottom-right upgrade button shows
            // up as soon as the town hall is finished (not only after a relog).
            for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> target),
                        new RTSUpgradeStatePacket(data.townHallLevel)
                );
            }
        }

        RTSBuilding building = RTSBuildings.get(site.buildingId);

        if (building != null && building.schematic != null && site.buildingId.equals("farm")) {
            data.addFarmZone(level.dimension(), building.schematic, site.origin, site.owner);
        }

        broadcastRemove(level, site.id);
        broadcastUnlock(level);
    }

    private static void broadcastRemove(ServerLevel level, int id) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RTSSiteRemovePacket(id)
                );
            }
        }
    }

    private static void broadcastUnlock(ServerLevel level) {
        List<String> list = new ArrayList<>(RTSBuildings.getCompleted());

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSUnlockPacket(list)
            );
        }
    }

    public static class Site {
        public final int id;
        public final ResourceKey<Level> dim;
        public final String buildingId;
        public final BlockPos origin;
        public final int total;
        public float progress;
        public int built;
        public UUID owner;

        public Site(int id, ResourceKey<Level> dim, String buildingId, BlockPos origin, int total) {
            this.id = id;
            this.dim = dim;
            this.buildingId = buildingId;
            this.origin = origin;
            this.total = total;
            this.progress = 0.0F;
            this.built = 0;
        }
    }
}