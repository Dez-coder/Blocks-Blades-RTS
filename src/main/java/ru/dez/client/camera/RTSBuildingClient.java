package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/** Client-side list of completed buildings, used to outline them on hover. */
public final class RTSBuildingClient {

    public static final List<Entry> BUILDINGS = new ArrayList<>();

    private RTSBuildingClient() {
    }

    public static void setAll(List<Entry> list) {
        BUILDINGS.clear();
        BUILDINGS.addAll(list);
    }

    public static void reset() {
        BUILDINGS.clear();
    }

    /** Returns the building whose structure contains the given block, or null. */
    public static Entry find(String dim, BlockPos pos) {
        for (Entry entry : BUILDINGS) {
            if (!entry.dim.equals(dim)) {
                continue;
            }

            RTSBuilding building = RTSBuildings.get(entry.id);

            if (building == null || building.schematic == null) {
                continue;
            }

            // Schematics are centred, so bounds run from origin+min to origin+max.
            if (pos.getX() >= entry.x + building.schematic.minX && pos.getX() <= entry.x + building.schematic.maxX
                    && pos.getY() >= entry.y + building.schematic.minY && pos.getY() <= entry.y + building.schematic.maxY
                    && pos.getZ() >= entry.z + building.schematic.minZ && pos.getZ() <= entry.z + building.schematic.maxZ) {
                return entry;
            }
        }

        return null;
    }

    /** A placed building's id, dimension and origin block. */
    public static class Entry {
        public final String id;
        public final String dim;
        public final int x;
        public final int y;
        public final int z;

        public Entry(String id, String dim, int x, int y, int z) {
            this.id = id;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
