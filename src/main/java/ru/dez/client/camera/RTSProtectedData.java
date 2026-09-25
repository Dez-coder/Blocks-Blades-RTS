package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class RTSProtectedData extends SavedData {

    private static final String NAME = "rts_protected";

    public final List<Area> areas = new ArrayList<>();
    public final List<Area> farmZones = new ArrayList<>();
    public final List<String> completed = new ArrayList<>();
    public final Map<String, Integer> builtCounts = new HashMap<>();
    public final Map<UUID, Integer> profs = new HashMap<>();
    public final Map<UUID, Integer> orders = new HashMap<>();
    /** Per-villager experience for the individual progression system. */
    public final Map<UUID, Integer> xp = new HashMap<>();
    public final Map<UUID, int[]> resources = new HashMap<>();
    public final List<SiteRecord> sites = new ArrayList<>();
    public final List<PlacedBuilding> placements = new ArrayList<>();
    public int nextSiteId = 1;
    /** Town hall tier (0 = not built, 1..3). Houses share this tier. */
    public int townHallLevel = 0;

    /** Village/team id (leader UUID string) each player currently belongs to. */
    public final Map<UUID, String> playerTeam = new HashMap<>();
    /** Pending invites: invited player -> team id that invited them. */
    public final Map<UUID, String> invites = new HashMap<>();
    /** Active wars, stored as normalised "a>b" village-id pairs (a < b). */
    public final List<String> wars = new ArrayList<>();
    /** Pending paid villager hires per village (town hall queue). */
    public final Map<UUID, Integer> hireQueue = new HashMap<>();

    public RTSProtectedData() {
    }

    public RTSProtectedData(CompoundTag tag) {
        ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            areas.add(readArea(list.getCompound(i)));
        }

        ListTag farmList = tag.getList("farmZones", Tag.TAG_COMPOUND);

        for (int i = 0; i < farmList.size(); i++) {
            farmZones.add(readArea(farmList.getCompound(i)));
        }

        ListTag completedList = tag.getList("completed", Tag.TAG_STRING);

        for (int i = 0; i < completedList.size(); i++) {
            completed.add(completedList.getString(i));
        }

        ListTag builtList = tag.getList("builtCounts", Tag.TAG_COMPOUND);

        for (int i = 0; i < builtList.size(); i++) {
            CompoundTag compound = builtList.getCompound(i);
            builtCounts.put(compound.getString("id"), compound.getInt("count"));
        }

        // Older saves only tracked which building types were completed, not how
        // many of each were built. Seed every known type with one so the
        // population cap does not drop below what was already built.
        for (String id : completed) {
            builtCounts.putIfAbsent(id, 1);
        }

        ListTag profList = tag.getList("profs", Tag.TAG_COMPOUND);

        for (int i = 0; i < profList.size(); i++) {
            CompoundTag compound = profList.getCompound(i);
            profs.put(UUID.fromString(compound.getString("uuid")), compound.getInt("prof"));
        }

        ListTag orderList = tag.getList("orders", Tag.TAG_COMPOUND);

        for (int i = 0; i < orderList.size(); i++) {
            CompoundTag compound = orderList.getCompound(i);
            orders.put(UUID.fromString(compound.getString("uuid")), compound.getInt("type"));
        }

        ListTag xpList = tag.getList("xp", Tag.TAG_COMPOUND);

        for (int i = 0; i < xpList.size(); i++) {
            CompoundTag compound = xpList.getCompound(i);
            xp.put(UUID.fromString(compound.getString("uuid")), compound.getInt("xp"));
        }

        ListTag resList = tag.getList("resources", Tag.TAG_COMPOUND);

        for (int i = 0; i < resList.size(); i++) {
            CompoundTag compound = resList.getCompound(i);
            resources.put(UUID.fromString(compound.getString("uuid")), new int[]{
                    compound.getInt("wood"),
                    compound.getInt("food"),
                    compound.getInt("ore"),
                    compound.getInt("happiness")
            });
        }

        ListTag siteList = tag.getList("sites", Tag.TAG_COMPOUND);

        for (int i = 0; i < siteList.size(); i++) {
            CompoundTag compound = siteList.getCompound(i);

            sites.add(new SiteRecord(
                    compound.getInt("id"),
                    compound.getString("dim"),
                    compound.getString("buildingId"),
                    compound.getInt("x"),
                    compound.getInt("y"),
                    compound.getInt("z"),
                    compound.contains("owner") ? compound.getString("owner") : null,
                    compound.getInt("built"),
                    compound.getFloat("progress")
            ));
        }

        ListTag placeList = tag.getList("placements", Tag.TAG_COMPOUND);

        for (int i = 0; i < placeList.size(); i++) {
            CompoundTag compound = placeList.getCompound(i);

            placements.add(new PlacedBuilding(
                    compound.getString("buildingId"),
                    compound.getString("dim"),
                    compound.getInt("x"),
                    compound.getInt("y"),
                    compound.getInt("z"),
                    compound.contains("owner") ? compound.getString("owner") : null,
                    compound.contains("health") ? compound.getFloat("health") : -1.0F
            ));
        }

        ListTag teamList = tag.getList("playerTeam", Tag.TAG_COMPOUND);

        for (int i = 0; i < teamList.size(); i++) {
            CompoundTag compound = teamList.getCompound(i);
            playerTeam.put(UUID.fromString(compound.getString("uuid")), compound.getString("team"));
        }

        ListTag inviteList = tag.getList("invites", Tag.TAG_COMPOUND);

        for (int i = 0; i < inviteList.size(); i++) {
            CompoundTag compound = inviteList.getCompound(i);
            invites.put(UUID.fromString(compound.getString("uuid")), compound.getString("team"));
        }

        ListTag warList = tag.getList("wars", Tag.TAG_STRING);

        for (int i = 0; i < warList.size(); i++) {
            wars.add(warList.getString(i));
        }

        ListTag hireList = tag.getList("hireQueue", Tag.TAG_COMPOUND);

        for (int i = 0; i < hireList.size(); i++) {
            CompoundTag compound = hireList.getCompound(i);
            hireQueue.put(UUID.fromString(compound.getString("uuid")), compound.getInt("count"));
        }

        nextSiteId = tag.contains("nextSiteId") ? tag.getInt("nextSiteId") : 1;
        townHallLevel = tag.contains("townHallLevel") ? tag.getInt("townHallLevel") : 0;
    }

    public static RTSProtectedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RTSProtectedData::new, RTSProtectedData::new, NAME);
    }

    private static Area readArea(CompoundTag compound) {
        return new Area(
                compound.getString("dim"),
                compound.getInt("minX"),
                compound.getInt("minY"),
                compound.getInt("minZ"),
                compound.getInt("maxX"),
                compound.getInt("maxY"),
                compound.getInt("maxZ"),
                compound.contains("owner") ? compound.getString("owner") : null
        );
    }

    private static CompoundTag writeArea(Area area) {
        CompoundTag compound = new CompoundTag();

        compound.putString("dim", area.dim);
        compound.putInt("minX", area.minX);
        compound.putInt("minY", area.minY);
        compound.putInt("minZ", area.minZ);
        compound.putInt("maxX", area.maxX);
        compound.putInt("maxY", area.maxY);
        compound.putInt("maxZ", area.maxZ);

        if (area.owner != null) {
            compound.putString("owner", area.owner);
        }

        return compound;
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        String dimName = dim.location().toString();

        for (Area area : areas) {
            if (area.dim.equals(dimName)
                    && pos.getX() >= area.minX && pos.getX() <= area.maxX
                    && pos.getY() >= area.minY && pos.getY() <= area.maxY
                    && pos.getZ() >= area.minZ && pos.getZ() <= area.maxZ) {
                return true;
            }
        }

        return false;
    }

    public void addArea(ResourceKey<Level> dim, RTSSchematic schematic, BlockPos origin) {
        areas.add(new Area(
                dim.location().toString(),
                origin.getX() + schematic.minX,
                origin.getY() + schematic.minY,
                origin.getZ() + schematic.minZ,
                origin.getX() + schematic.maxX,
                origin.getY() + schematic.maxY,
                origin.getZ() + schematic.maxZ
        ));

        setDirty();
    }

    public void addFarmZone(ResourceKey<Level> dim, RTSSchematic schematic, BlockPos origin) {
        addFarmZone(dim, schematic, origin, null);
    }

    /**
     * Registers a completed farm as a passive income zone. The owner is stored
     * so the food generated by the farm can be credited to the player that
     * built it.
     */
    public void addFarmZone(ResourceKey<Level> dim, RTSSchematic schematic, BlockPos origin, UUID owner) {
        farmZones.add(new Area(
                dim.location().toString(),
                origin.getX() + schematic.minX,
                origin.getY() + schematic.minY,
                origin.getZ() + schematic.minZ,
                origin.getX() + schematic.maxX,
                origin.getY() + schematic.maxY,
                origin.getZ() + schematic.maxZ,
                owner == null ? null : owner.toString()
        ));

        setDirty();
    }

    public void addCompleted(String id) {
        if (!completed.contains(id)) {
            completed.add(id);
        }

        setDirty();
    }

    public int getProf(UUID id) {
        return profs.getOrDefault(id, 0);
    }

    public void setProf(UUID id, int prof) {
        profs.put(id, prof);
        setDirty();
    }

    public void removeProf(UUID id) {
        if (profs.remove(id) != null) {
            setDirty();
        }
    }

    public UUID getFarmOwner(Area zone) {
        if (zone.owner == null) {
            return null;
        }

        try {
            return UUID.fromString(zone.owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Records that one more building of the given type was finished. */
    public void incrementBuilt(String id) {
        builtCounts.merge(id, 1, Integer::sum);
        setDirty();
    }

    /** Returns how many buildings of the given type have been completed. */
    public int getBuiltCount(String id) {
        return builtCounts.getOrDefault(id, 0);
    }

    /** Remembers the standing order (task type) of a villager. */
    public void setOrder(UUID id, int type) {
        orders.put(id, type);
        setDirty();
    }

    public void removeOrder(UUID id) {
        if (orders.remove(id) != null) {
            setDirty();
        }
    }

    public Integer getOrder(UUID id) {
        return orders.get(id);
    }

    public int getXp(UUID id) {
        return xp.getOrDefault(id, 0);
    }

    public void addXp(UUID id, int amount) {
        xp.merge(id, amount, Integer::sum);
        setDirty();
    }

    public void addPlacement(String buildingId, String dim, BlockPos pos) {
        addPlacement(buildingId, dim, pos, null);
    }

    public void addPlacement(String buildingId, String dim, BlockPos pos, String owner) {
        placements.add(new PlacedBuilding(buildingId, dim, pos.getX(), pos.getY(), pos.getZ(), owner, -1.0F));
        setDirty();
    }

    /** Returns the most recently completed building of the given type, or null. */
    public PlacedBuilding getLastPlacement(String buildingId, String dim) {
        for (int i = placements.size() - 1; i >= 0; i--) {
            PlacedBuilding placed = placements.get(i);

            if (placed.id.equals(buildingId) && placed.dim.equals(dim)) {
                return placed;
            }
        }

        return null;
    }

    public void removePlacement(PlacedBuilding placed) {
        if (placements.remove(placed)) {
            setDirty();
        }
    }

    /** Swaps a placed building's id in-place (used for instant upgrades). */
    public void replacePlacement(PlacedBuilding old, String newId) {
        int index = placements.indexOf(old);

        if (index >= 0) {
            placements.set(index, new PlacedBuilding(newId, old.dim, old.x, old.y, old.z, old.owner, -1.0F));
            setDirty();
        }
    }

    public int[] getResources(UUID id) {
        return resources.get(id);
    }

    public void setResources(UUID id, int wood, int food, int ore, int happiness) {
        resources.put(id, new int[]{wood, food, ore, happiness});
        setDirty();
    }

    // === Town hall hiring ===

    /** How many paid villagers are waiting in the village's hire queue. */
    public int getHireQueue(UUID village) {
        return village == null ? 0 : hireQueue.getOrDefault(village, 0);
    }

    public void addHire(UUID village, int amount) {
        if (village != null) {
            hireQueue.merge(village, amount, Integer::sum);
            setDirty();
        }
    }

    /** Removes one villager from the queue (called when it is spawned). */
    public void consumeHire(UUID village) {
        int current = getHireQueue(village);

        if (current <= 0) {
            return;
        }

        if (current == 1) {
            hireQueue.remove(village);
        } else {
            hireQueue.put(village, current - 1);
        }

        setDirty();
    }

    /**
     * Returns the town hall whose structure contains the given block, so a
     * world click on the town hall can open the hire panel.
     */
    public PlacedBuilding findTownHall(String dim, BlockPos pos) {
        PlacedBuilding nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (PlacedBuilding placed : placements) {
            if (!placed.id.startsWith("town_hall") || !placed.dim.equals(dim)) {
                continue;
            }

            RTSBuilding building = RTSBuildings.get(placed.id);

            if (building == null || building.schematic == null) {
                continue;
            }

            // Schematics are centred, so the structure spans min..max offsets.
            int lx = placed.x + building.schematic.minX;
            int ly = placed.y + building.schematic.minY;
            int lz = placed.z + building.schematic.minZ;
            int hx = placed.x + building.schematic.maxX;
            int hy = placed.y + building.schematic.maxY;
            int hz = placed.z + building.schematic.maxZ;

            // Direct hit, with a generous margin: any block of the town hall
            // (or just beside it) counts, so it is clickable anywhere.
            if (pos.getX() >= lx - 4 && pos.getX() <= hx + 4
                    && pos.getY() >= ly - 10 && pos.getY() <= hy + 10
                    && pos.getZ() >= lz - 4 && pos.getZ() <= hz + 4) {
                return placed;
            }

            // Remember the closest hall; a click near it (ground beside it) works too.
            double cx = (lx + hx) / 2.0;
            double cz = (lz + hz) / 2.0;
            double dx = pos.getX() + 0.5 - cx;
            double dz = pos.getZ() + 0.5 - cz;
            double dist = dx * dx + dz * dz;

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = placed;
            }
        }

        // Fallback: within ~16 blocks horizontally of a town hall.
        if (nearest != null && nearestDist <= 256.0) {
            return nearest;
        }

        return null;
    }

    public int reserveSiteId() {
        int id = nextSiteId++;
        setDirty();
        return id;
    }

    public void upsertSite(int id, String dim, String buildingId, int x, int y, int z, String owner, int built, float progress) {
        for (SiteRecord record : sites) {
            if (record.id == id) {
                record.built = built;
                record.progress = progress;
                setDirty();
                return;
            }
        }

        sites.add(new SiteRecord(id, dim, buildingId, x, y, z, owner, built, progress));
        setDirty();
    }

    public void removeSite(int id) {
        if (sites.removeIf(record -> record.id == id)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (Area area : areas) {
            list.add(writeArea(area));
        }

        tag.put("areas", list);

        ListTag farmList = new ListTag();

        for (Area area : farmZones) {
            farmList.add(writeArea(area));
        }

        tag.put("farmZones", farmList);

        ListTag completedList = new ListTag();

        for (String id : completed) {
            completedList.add(StringTag.valueOf(id));
        }

        tag.put("completed", completedList);

        ListTag builtList = new ListTag();

        for (Map.Entry<String, Integer> entry : builtCounts.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("id", entry.getKey());
            compound.putInt("count", entry.getValue());
            builtList.add(compound);
        }

        tag.put("builtCounts", builtList);

        ListTag profList = new ListTag();

        for (Map.Entry<UUID, Integer> entry : profs.entrySet()) {
            CompoundTag compound = new CompoundTag();

            compound.putString("uuid", entry.getKey().toString());
            compound.putInt("prof", entry.getValue());

            profList.add(compound);
        }

        tag.put("profs", profList);

        ListTag orderList = new ListTag();

        for (Map.Entry<UUID, Integer> entry : orders.entrySet()) {
            CompoundTag compound = new CompoundTag();

            compound.putString("uuid", entry.getKey().toString());
            compound.putInt("type", entry.getValue());

            orderList.add(compound);
        }

        tag.put("orders", orderList);

        ListTag xpList = new ListTag();

        for (Map.Entry<UUID, Integer> entry : xp.entrySet()) {
            CompoundTag compound = new CompoundTag();

            compound.putString("uuid", entry.getKey().toString());
            compound.putInt("xp", entry.getValue());

            xpList.add(compound);
        }

        tag.put("xp", xpList);

        ListTag resList = new ListTag();

        for (Map.Entry<UUID, int[]> entry : resources.entrySet()) {
            CompoundTag compound = new CompoundTag();
            int[] value = entry.getValue();

            compound.putString("uuid", entry.getKey().toString());
            compound.putInt("wood", value[0]);
            compound.putInt("food", value[1]);
            compound.putInt("ore", value[2]);
            compound.putInt("happiness", value[3]);

            resList.add(compound);
        }

        tag.put("resources", resList);

        ListTag siteList = new ListTag();

        for (SiteRecord record : sites) {
            CompoundTag compound = new CompoundTag();

            compound.putInt("id", record.id);
            compound.putString("dim", record.dim);
            compound.putString("buildingId", record.buildingId);
            compound.putInt("x", record.x);
            compound.putInt("y", record.y);
            compound.putInt("z", record.z);

            if (record.owner != null) {
                compound.putString("owner", record.owner);
            }

            compound.putInt("built", record.built);
            compound.putFloat("progress", record.progress);

            siteList.add(compound);
        }

        tag.put("sites", siteList);

        ListTag hireList = new ListTag();

        for (Map.Entry<UUID, Integer> entry : hireQueue.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("uuid", entry.getKey().toString());
            compound.putInt("count", entry.getValue());
            hireList.add(compound);
        }

        tag.put("hireQueue", hireList);

        ListTag placeList = new ListTag();

        for (PlacedBuilding placed : placements) {
            CompoundTag compound = new CompoundTag();

            compound.putString("buildingId", placed.id);
            compound.putString("dim", placed.dim);
            compound.putInt("x", placed.x);
            compound.putInt("y", placed.y);
            compound.putInt("z", placed.z);

            if (placed.owner != null) {
                compound.putString("owner", placed.owner);
            }

            if (placed.health >= 0.0F) {
                compound.putFloat("health", placed.health);
            }

            placeList.add(compound);
        }

        tag.put("placements", placeList);

        ListTag teamList = new ListTag();

        for (Map.Entry<UUID, String> entry : playerTeam.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("uuid", entry.getKey().toString());
            compound.putString("team", entry.getValue());
            teamList.add(compound);
        }

        tag.put("playerTeam", teamList);

        ListTag inviteList = new ListTag();

        for (Map.Entry<UUID, String> entry : invites.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("uuid", entry.getKey().toString());
            compound.putString("team", entry.getValue());
            inviteList.add(compound);
        }

        tag.put("invites", inviteList);

        ListTag warList = new ListTag();

        for (String pair : wars) {
            warList.add(StringTag.valueOf(pair));
        }

        tag.put("wars", warList);

        tag.putInt("nextSiteId", nextSiteId);
        tag.putInt("townHallLevel", townHallLevel);

        return tag;
    }

    public static class PlacedBuilding {
        public final String id;
        public final String dim;
        public final int x;
        public final int y;
        public final int z;
        /** Village id (leader UUID string) that owns this building, or null. */
        public final String owner;
        /** Remaining hit points; -1 means "not yet initialised / undamaged". */
        public float health = -1.0F;

        public PlacedBuilding(String id, String dim, int x, int y, int z) {
            this(id, dim, x, y, z, null, -1.0F);
        }

        public PlacedBuilding(String id, String dim, int x, int y, int z, String owner, float health) {
            this.id = id;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.health = health;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class SiteRecord {
        public final int id;
        public final String dim;
        public final String buildingId;
        public final int x;
        public final int y;
        public final int z;
        public final String owner;
        public int built;
        public float progress;

        public SiteRecord(int id, String dim, String buildingId, int x, int y, int z, String owner, int built, float progress) {
            this.id = id;
            this.dim = dim;
            this.buildingId = buildingId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.built = built;
            this.progress = progress;
        }
    }

    public static class Area {
        public final String dim;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;
        public final String owner;

        public Area(String dim, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(dim, minX, minY, minZ, maxX, maxY, maxZ, null);
        }

        public Area(String dim, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String owner) {
            this.dim = dim;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.owner = owner;
        }
    }
}