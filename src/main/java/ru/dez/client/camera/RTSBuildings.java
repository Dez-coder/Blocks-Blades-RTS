package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

public class RTSBuildings {

    public static final List<RTSBuilding> BUILDINGS = new ArrayList<>();

    /** Base (level 1) house ids; upgrades are generated per tier. */
    private static final String[] HOUSE_FAMILIES = {
            "house_small", "house", "house_large", "house_2f", "house_2f_large", "house_manor"
    };

    /** Returns the base family id for any house id (level 1/2/3), or null. */
    public static String houseFamily(String id) {
        if (id == null) {
            return null;
        }

        String base = id;

        if (base.endsWith("_2") || base.endsWith("_3")) {
            base = base.substring(0, base.length() - 2);
        }

        for (String family : HOUSE_FAMILIES) {
            if (family.equals(base)) {
                return family;
            }
        }

        return null;
    }

    private static final Set<String> COMPLETED = new HashSet<>();

    static {
        BUILDINGS.add(new RTSBuilding("town_hall", "Мэрия", Items.OAK_DOOR, null, createTownHall(), 32, 0, 16));

        // A wide range of housing. Each finished house adds 3 population slots.
        BUILDINGS.add(new RTSBuilding("house_small", "Домик", Items.SPRUCE_PLANKS, "town_hall",
                createHouseVariant(2, 2, 1, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS), 8, 0, 4));
        BUILDINGS.add(new RTSBuilding("house", "Дом", Items.OAK_PLANKS, "town_hall", createHouse(), 16, 0, 8));
        BUILDINGS.add(new RTSBuilding("house_large", "Большой дом", Items.OAK_DOOR, "town_hall",
                createHouseVariant(4, 3, 1, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS), 28, 0, 12));
        BUILDINGS.add(new RTSBuilding("house_2f", "Двухэтажный дом", Items.SPRUCE_DOOR, "town_hall",
                createHouseVariant(3, 3, 2, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS), 40, 8, 20));
        BUILDINGS.add(new RTSBuilding("house_2f_large", "Большой 2-этажный дом", Items.DARK_OAK_DOOR, "town_hall",
                createHouseVariant(4, 4, 2, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS), 56, 16, 28));
        BUILDINGS.add(new RTSBuilding("house_manor", "Усадьба", Items.BRICKS, "town_hall",
                createHouseVariant(5, 4, 3, Blocks.STONE_BRICKS, Blocks.BRICKS, Blocks.DARK_OAK_LOG, Blocks.BRICK_STAIRS, Blocks.BRICKS), 90, 32, 50));
        BUILDINGS.add(new RTSBuilding("farm", "Ферма", Items.WHEAT, "town_hall", createFarm(), 12, 8, 0));
        BUILDINGS.add(new RTSBuilding("mine", "Шахта", Items.IRON_PICKAXE, "town_hall", createMine(), 20, 0, 20));
        BUILDINGS.add(new RTSBuilding("lumber_camp", "Лесопилка", Items.STONE_AXE, "town_hall", createLumberCamp(), 18, 0, 4));
        BUILDINGS.add(new RTSBuilding("barracks", "Казарма", Items.IRON_SWORD, "town_hall", createBarracks(), 24, 12, 12));

        // Укрытие. Чисто гражданская постройка: как только наступает ночь,
        // все жители (кроме воинов) идут внутрь и сидят там до рассвета.
        // Логика укрытия живёт в RTSVillagerTaskManager#tickShelter.
        BUILDINGS.add(new RTSBuilding("shelter", "Укрытие", Items.SHIELD, "town_hall", createShelter(), 20, 10, 6));

        // Upgrade levels for the mine and lumber camp. Level 2 upgrades tools to
        // iron, level 3 to diamond; level 3 is deliberately very expensive.
        BUILDINGS.add(new RTSBuilding("mine_2", "Шахта ур.2", Items.IRON_PICKAXE, "mine",
                createMineLevel(3, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.IRON_BLOCK), 48, 24, 32, true));
        BUILDINGS.add(new RTSBuilding("mine_3", "Шахта ур.3", Items.DIAMOND_PICKAXE, "mine_2",
                createMineLevel(4, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.DIAMOND_BLOCK), 640, 384, 512, true));
        BUILDINGS.add(new RTSBuilding("lumber_camp_2", "Лесопилка ур.2", Items.IRON_AXE, "lumber_camp",
                createLumberCampLevel(4, 3, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.IRON_BLOCK), 48, 16, 24, true));
        BUILDINGS.add(new RTSBuilding("lumber_camp_3", "Лесопилка ур.3", Items.DIAMOND_AXE, "lumber_camp_2",
                createLumberCampLevel(5, 4, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DIAMOND_BLOCK), 768, 256, 384, true));

        // Town hall tiers - bought instantly with the upgrade button, never built
        // manually. Level 3 is deliberately absurdly expensive.
        BUILDINGS.add(new RTSBuilding("town_hall_2", "Мэрия ур.2", Items.EMERALD_BLOCK, "town_hall",
                createTownHallLevel(5, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.EMERALD_BLOCK), 240, 120, 180, true));
        BUILDINGS.add(new RTSBuilding("town_hall_3", "Мэрия ур.3", Items.DIAMOND_BLOCK, "town_hall_2",
                createTownHallLevel(6, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DIAMOND_BLOCK), 8000, 5000, 8000, true));

        // Two upgrade stages for every house family. They are applied instantly
        // together with the town hall upgrade, so they never appear in the menu.
        for (String family : HOUSE_FAMILIES) {
            for (int tier = 2; tier <= 3; tier++) {
                BUILDINGS.add(new RTSBuilding(family + "_" + tier, family + " ур." + tier, Items.BRICKS, "town_hall",
                        houseUpgrade(family, tier), 0, 0, 0, true));
            }
        }

        refresh();
    }

    public static RTSBuilding get(String id) {
        if (id == null) {
            return null;
        }

        for (RTSBuilding building : BUILDINGS) {
            if (building.id.equals(id)) {
                return building;
            }
        }

        return null;
    }

    public static boolean isCompleted(String id) {
        return COMPLETED.contains(id);
    }

    public static void markCompleted(String id) {
        COMPLETED.add(id);
        refresh();
    }

    public static void setCompleted(Collection<String> list) {
        COMPLETED.clear();
        COMPLETED.addAll(list);
        refresh();
    }

    public static Set<String> getCompleted() {
        return COMPLETED;
    }

    public static void refresh() {
        for (RTSBuilding building : BUILDINGS) {
            building.unlocked = building.requires == null || COMPLETED.contains(building.requires);
        }
    }

    public static void place(ServerLevel level, String id, BlockPos origin) {
        RTSBuilding building = get(id);

        if (building == null || building.schematic == null) {
            return;
        }

        for (RTSSchematic.Entry entry : building.schematic.entries) {
            BlockPos pos = origin.offset(entry.x, entry.y, entry.z);
            level.setBlockAndUpdate(pos, entry.state);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static BlockState stairState(Block stairs, Direction facing) {
        return stairs.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM);
    }

    /**
     * Builds a simple stepped pyramid/hip roof: each layer's outer ring is
     * made of stairs sloping outward and the layer shrinks by one block on
     * every side that still has room, until it tapers to a single ridge
     * block. Rectangular footprints naturally end up with a short flat
     * ridge line once the shorter axis runs out first.
     */
    private static void addPyramidRoof(RTSSchematic s, int minX, int maxX, int minZ, int maxZ, int baseY, Block stairs, Block ridge) {
        int x0 = minX;
        int x1 = maxX;
        int z0 = minZ;
        int z1 = maxZ;
        int y = baseY;

        while (true) {
            boolean xDone = x1 <= x0;
            boolean zDone = z1 <= z0;

            if (xDone && zDone) {
                s.set(x0, y, z0, ridge);
                break;
            }

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edgeN = !zDone && z == z0;
                    boolean edgeS = !zDone && z == z1;
                    boolean edgeW = !xDone && x == x0;
                    boolean edgeE = !xDone && x == x1;

                    if (edgeN && !edgeW && !edgeE) {
                        s.set(x, y, z, stairState(stairs, Direction.NORTH));
                    } else if (edgeS && !edgeW && !edgeE) {
                        s.set(x, y, z, stairState(stairs, Direction.SOUTH));
                    } else if (edgeW && !edgeN && !edgeS) {
                        s.set(x, y, z, stairState(stairs, Direction.WEST));
                    } else if (edgeE && !edgeN && !edgeS) {
                        s.set(x, y, z, stairState(stairs, Direction.EAST));
                    } else if (edgeN || edgeS || edgeW || edgeE) {
                        Direction facing = edgeN ? Direction.NORTH : edgeS ? Direction.SOUTH : edgeW ? Direction.WEST : Direction.EAST;
                        s.set(x, y, z, stairState(stairs, facing));
                    } else if (xDone || zDone) {
                        s.set(x, y, z, ridge);
                    }
                }
            }

            if (!xDone) {
                x0++;
                x1--;
            }

            if (!zDone) {
                z0++;
                z1--;
            }

            y++;
        }
    }

    // ------------------------------------------------------------------
    // Town Hall - the grand civic building, unlocks everything else
    // ------------------------------------------------------------------

    private static RTSSchematic createTownHall() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                s.set(x, 0, z, Blocks.STONE_BRICKS);
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean ex = x == -4 || x == 4;
                    boolean ez = z == -4 || z == 4;

                    if (!ex && !ez) continue;
                    if (z == 4 && x >= -1 && x <= 1 && y <= 2) continue;

                    if (ex && ez) {
                        s.set(x, y, z, Blocks.DARK_OAK_LOG);
                        continue;
                    }

                    if (y == 1) {
                        s.set(x, y, z, Blocks.STONE_BRICKS);
                        continue;
                    }

                    if (y == 2 && ((ex && z > -3 && z < 3) || (ez && x > -3 && x < 3))) {
                        s.set(x, y, z, Blocks.GLASS_PANE);
                        continue;
                    }

                    s.set(x, y, z, Blocks.DARK_OAK_PLANKS);
                }
            }
        }

        s.set(-4, 4, -4, Blocks.LANTERN);
        s.set(-4, 4, 4, Blocks.LANTERN);
        s.set(4, 4, -4, Blocks.LANTERN);
        s.set(4, 4, 4, Blocks.LANTERN);

        s.set(-2, 1, 5, Blocks.LANTERN);
        s.set(2, 1, 5, Blocks.LANTERN);

        addPyramidRoof(s, -4, 4, -4, 4, 5, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS);

        return s;
    }

    // ------------------------------------------------------------------
    // House - a small cottage for villagers to live in
    // ------------------------------------------------------------------

    private static RTSSchematic createHouse() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                s.set(x, 0, z, Blocks.COBBLESTONE);
            }
        }

        for (int y = 1; y <= 2; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean ex = x == -3 || x == 3;
                    boolean ez = z == -3 || z == 3;

                    if (!ex && !ez) continue;
                    if (z == 3 && x == 0) continue;

                    if (ex && ez) {
                        s.set(x, y, z, Blocks.SPRUCE_LOG);
                        continue;
                    }

                    if (y == 1) {
                        s.set(x, y, z, Blocks.COBBLESTONE);
                        continue;
                    }

                    if (y == 2 && ((ex && z == 0) || (ez && (x == -2 || x == 2)))) {
                        s.set(x, y, z, Blocks.GLASS_PANE);
                        continue;
                    }

                    s.set(x, y, z, Blocks.SPRUCE_PLANKS);
                }
            }
        }

        s.set(-3, 3, -3, Blocks.LANTERN);
        s.set(-3, 3, 3, Blocks.LANTERN);
        s.set(3, 3, -3, Blocks.LANTERN);
        s.set(3, 3, 3, Blocks.LANTERN);
        s.set(1, 1, 4, Blocks.LANTERN);

        addPyramidRoof(s, -3, 3, -3, 3, 4, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);

        return s;
    }

    /**
     * Generic multi-storey house: a foundation, {@code floors} storeys (each a
     * 2-block interior with a sloped roof on top), corner log posts, glass
     * windows, a ground-floor doorway and interior lanterns.
     */
    private static RTSSchematic createHouseVariant(int rx, int rz, int floors, Block floorBlock, Block wallBlock, Block logBlock, Block stairs, Block roofMat) {
        RTSSchematic s = new RTSSchematic();

        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                s.set(x, 0, z, floorBlock);
            }
        }

        for (int floor = 0; floor < floors; floor++) {
            int y0 = 1 + floor * 3;

            for (int y = y0; y <= y0 + 1; y++) {
                for (int x = -rx; x <= rx; x++) {
                    for (int z = -rz; z <= rz; z++) {
                        boolean ex = x == -rx || x == rx;
                        boolean ez = z == -rz || z == rz;

                        if (!ex && !ez) continue;

                        // Two-block-tall doorway on the ground floor.
                        if (floor == 0 && z == rz && x == 0) continue;

                        if (ex && ez) {
                            s.set(x, y, z, logBlock);
                            continue;
                        }

                        if (y == y0 + 1 && ((ex && z == 0) || (ez && (x == -1 || x == 1)))) {
                            s.set(x, y, z, Blocks.GLASS_PANE);
                            continue;
                        }

                        s.set(x, y, z, wallBlock);
                    }
                }
            }

            // The ceiling doubles as the slab for the storey above.
            for (int x = -rx; x <= rx; x++) {
                for (int z = -rz; z <= rz; z++) {
                    s.set(x, y0 + 2, z, floorBlock);
                }
            }

            s.set(-rx + 1, y0, -rz + 1, Blocks.LANTERN);
        }

        int top = 1 + floors * 3;
        addPyramidRoof(s, -rx, rx, -rz, rz, top, stairs, roofMat);

        return s;
    }

    private static int[] houseBase(String family) {
        switch (family) {
            case "house_small":
                return new int[]{2, 2, 1};
            case "house":
                return new int[]{3, 3, 1};
            case "house_large":
                return new int[]{4, 3, 1};
            case "house_2f":
                return new int[]{3, 3, 2};
            case "house_2f_large":
                return new int[]{4, 4, 2};
            case "house_manor":
                return new int[]{5, 4, 3};
            default:
                return null;
        }
    }

    /** Bigger, better-material variant of a house for the given tier (2 or 3). */
    private static RTSSchematic houseUpgrade(String family, int tier) {
        int[] base = houseBase(family);

        if (base == null) {
            return null;
        }

        int rx = base[0] + (tier - 1);
        int rz = base[1] + (tier - 1);
        int floors = Math.min(4, base[2] + (tier - 1));

        Block floorB;
        Block wallB;
        Block logB;
        Block stairs;
        Block roof;

        if (tier <= 2) {
            floorB = Blocks.STONE_BRICKS;
            wallB = Blocks.SPRUCE_PLANKS;
            logB = Blocks.SPRUCE_LOG;
            stairs = Blocks.SPRUCE_STAIRS;
            roof = Blocks.SPRUCE_PLANKS;
        } else {
            floorB = Blocks.POLISHED_DEEPSLATE;
            wallB = Blocks.BRICKS;
            logB = Blocks.DARK_OAK_LOG;
            stairs = Blocks.BRICK_STAIRS;
            roof = Blocks.DIAMOND_BLOCK;
        }

        return createHouseVariant(rx, rz, floors, floorB, wallB, logB, stairs, roof);
    }

    /** Grand civic building used for town hall tiers 2 and 3. */
    private static RTSSchematic createTownHallLevel(int r, Block floorB, Block wallB, Block logB, Block stairs, Block roofMat, Block accent) {
        RTSSchematic s = new RTSSchematic();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                s.set(x, 0, z, floorB);
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean ex = x == -r || x == r;
                    boolean ez = z == -r || z == r;

                    if (!ex && !ez) continue;
                    if (z == r && x >= -1 && x <= 1 && y <= 2) continue;

                    if (ex && ez) {
                        s.set(x, y, z, y == 2 ? accent : logB);
                        continue;
                    }

                    if (y == 2 && ((ex && z > -r + 1 && z < r - 1) || (ez && x > -r + 1 && x < r - 1))) {
                        s.set(x, y, z, Blocks.GLASS_PANE);
                        continue;
                    }

                    s.set(x, y, z, y == 1 ? floorB : wallB);
                }
            }
        }

        s.set(-2, 1, r + 1, Blocks.LANTERN);
        s.set(2, 1, r + 1, Blocks.LANTERN);

        addPyramidRoof(s, -r, r, -r, r, 4, stairs, roofMat);

        s.set(-r, 4, -r, Blocks.LANTERN);
        s.set(r, 4, -r, Blocks.LANTERN);
        s.set(-r, 4, r, Blocks.LANTERN);
        s.set(r, 4, r, Blocks.LANTERN);

        return s;
    }

    // ------------------------------------------------------------------
    // Farm - a fenced field with a pond, unlocks farmer hiring
    // ------------------------------------------------------------------

    private static RTSSchematic createFarm() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x == 0 && z == 0) {
                    s.set(x, 0, z, Blocks.WATER);
                } else {
                    s.set(x, 0, z, Blocks.FARMLAND);
                }
            }
        }

        for (int y = 1; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean ex = x == -4 || x == 4;
                    boolean ez = z == -4 || z == 4;

                    if (!ex && !ez) continue;
                    if (z == 4 && x == 0) continue;

                    s.set(x, y, z, Blocks.OAK_FENCE);
                }
            }
        }

        s.set(-4, 3, -4, Blocks.HAY_BLOCK);
        s.set(-4, 3, 4, Blocks.HAY_BLOCK);
        s.set(4, 3, -4, Blocks.HAY_BLOCK);
        s.set(4, 3, 4, Blocks.HAY_BLOCK);

        s.set(-1, 1, 4, Blocks.LANTERN);
        s.set(1, 1, 4, Blocks.LANTERN);
        s.set(3, 1, -3, Blocks.COMPOSTER);

        return s;
    }

    // ------------------------------------------------------------------
    // Mine - a torch-lit stone entrance, unlocks miner hiring
    // ------------------------------------------------------------------

    private static RTSSchematic createMine() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                s.set(x, 0, z, Blocks.COBBLESTONE);
            }
        }

        for (int y = 1; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean ex = x == -2 || x == 2;
                    boolean ez = z == -2 || z == 2;

                    if (!ex && !ez) continue;
                    if (z == 2 && x == 0) continue;

                    if (ex && ez) {
                        s.set(x, y, z, Blocks.COBBLESTONE);
                        continue;
                    }

                    if (y == 2 && z == 2 && (x == -1 || x == 1)) {
                        s.set(x, y, z, Blocks.IRON_BARS);
                        continue;
                    }

                    s.set(x, y, z, Blocks.DEEPSLATE_BRICKS);
                }
            }
        }

        s.set(-1, 1, 1, Blocks.TORCH);
        s.set(1, 1, 1, Blocks.TORCH);

        addPyramidRoof(s, -2, 2, -2, 2, 3, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE);

        return s;
    }

    // ------------------------------------------------------------------
    // Lumber Camp - open timber yard, unlocks lumberjack hiring
    // ------------------------------------------------------------------

    private static RTSSchematic createLumberCamp() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                s.set(x, 0, z, Blocks.DIRT_PATH);
            }
        }

        int[] postX = {-3, 3};
        int[] postZ = {-2, 2};

        for (int px : postX) {
            for (int pz : postZ) {
                for (int y = 1; y <= 3; y++) {
                    s.set(px, y, pz, Blocks.STRIPPED_OAK_LOG);
                }
            }
        }

        for (int px : postX) {
            s.set(px, 3, 0, Blocks.STRIPPED_OAK_LOG);
        }

        for (int pz : postZ) {
            s.set(0, 3, pz, Blocks.STRIPPED_OAK_LOG);
        }

        addPyramidRoof(s, -3, 3, -2, 2, 4, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS);

        s.set(-3, 1, 0, Blocks.LANTERN);
        s.set(3, 1, 0, Blocks.LANTERN);

        s.set(-2, 1, -2, Blocks.CRAFTING_TABLE);
        s.set(-1, 1, -2, Blocks.STONECUTTER);

        for (int i = -2; i <= 2; i += 4) {
            s.set(i, 1, 2, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
            s.set(i, 2, 2, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        }

        s.set(0, 1, -1, Blocks.HAY_BLOCK);

        return s;
    }

    // ------------------------------------------------------------------
    // Barracks - a small stone keep with a crenellated parapet
    // ------------------------------------------------------------------

    private static RTSSchematic createBarracks() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                s.set(x, 0, z, Blocks.STONE_BRICKS);
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean ex = x == -4 || x == 4;
                    boolean ez = z == -3 || z == 3;

                    if (!ex && !ez) continue;
                    if (z == 3 && x >= -1 && x <= 1) continue;

                    if (ex && ez) {
                        s.set(x, y, z, Blocks.SPRUCE_LOG);
                        continue;
                    }

                    if (y == 2 && ((ex && z == 0) || (ez && (x == -2 || x == 2)))) {
                        s.set(x, y, z, Blocks.IRON_BARS);
                        continue;
                    }

                    s.set(x, y, z, Blocks.STONE_BRICKS);
                }
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean ex = x == -4 || x == 4;
                boolean ez = z == -3 || z == 3;

                if (!ex && !ez) continue;

                s.set(x, 4, z, Blocks.STONE_BRICKS);

                if ((x + z) % 2 == 0) {
                    s.set(x, 5, z, Blocks.STONE_BRICK_WALL);
                }
            }
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                s.set(x, 4, z, Blocks.SPRUCE_PLANKS);
            }
        }

        s.set(-4, 5, -3, Blocks.TORCH);
        s.set(4, 5, -3, Blocks.TORCH);
        s.set(-4, 5, 3, Blocks.TORCH);
        s.set(4, 5, 3, Blocks.TORCH);

        return s;
    }

    // ------------------------------------------------------------------
    // Shelter - a small sealed dugout the villagers hide in at night
    // ------------------------------------------------------------------

    /**
     * A compact 5x5 stone shelter with a solid flat roof and a single
     * two-block doorway. The interior is left open so villagers can path in
     * and sit there until the sun comes up.
     */
    private static RTSSchematic createShelter() {
        RTSSchematic s = new RTSSchematic();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                s.set(x, 0, z, Blocks.STONE_BRICKS);
            }
        }

        for (int y = 1; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean ex = x == -2 || x == 2;
                    boolean ez = z == -2 || z == 2;

                    if (!ex && !ez) continue;

                    // Two-block-tall doorway on the south wall.
                    if (z == 2 && x == 0) continue;

                    if (ex && ez) {
                        s.set(x, y, z, Blocks.DARK_OAK_LOG);
                        continue;
                    }

                    if (y == 2 && ((ex && z == 0) || (ez && (x == -1 || x == 1)))) {
                        s.set(x, y, z, Blocks.GLASS_PANE);
                        continue;
                    }

                    s.set(x, y, z, Blocks.STONE_BRICKS);
                }
            }
        }

        // Solid roof, so the shelter is actually enclosed.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                s.set(x, 3, z, Blocks.SPRUCE_PLANKS);
            }
        }

        // Lanterns hanging from the ceiling + one beside the entrance.
        s.set(0, 2, -1, Blocks.LANTERN);
        s.set(0, 2, 1, Blocks.LANTERN);
        s.set(1, 2, 3, Blocks.LANTERN);

        return s;
    }

    // ------------------------------------------------------------------
    // Upgrade levels: a larger, reinforced mine shaft
    // ------------------------------------------------------------------

    private static RTSSchematic createMineLevel(int r, Block wall, Block floor, Block trim) {
        RTSSchematic s = new RTSSchematic();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                s.set(x, 0, z, floor);
            }
        }

        for (int y = 1; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean ex = x == -r || x == r;
                    boolean ez = z == -r || z == r;

                    if (!ex && !ez) continue;
                    if (z == r && x == 0) continue;

                    if (ex && ez) {
                        s.set(x, y, z, trim);
                        continue;
                    }

                    if (y == 2 && z == r && (x == -1 || x == 1)) {
                        s.set(x, y, z, Blocks.IRON_BARS);
                        continue;
                    }

                    s.set(x, y, z, wall);
                }
            }
        }

        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                s.set(dx * r, r + 1, dz * r, Blocks.LANTERN);
            }
        }

        s.set(-1, 1, 1, Blocks.TORCH);
        s.set(1, 1, 1, Blocks.TORCH);

        addPyramidRoof(s, -r, r, -r, r, r + 1, Blocks.COBBLESTONE_STAIRS, wall);

        return s;
    }

    // ------------------------------------------------------------------
    // Upgrade levels: a larger, reinforced timber yard
    // ------------------------------------------------------------------

    private static RTSSchematic createLumberCampLevel(int rx, int rz, Block post, Block stairs, Block trim) {
        RTSSchematic s = new RTSSchematic();

        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                s.set(x, 0, z, Blocks.DIRT_PATH);
            }
        }

        int[] postX = {-rx, rx};
        int[] postZ = {-rz, rz};

        for (int px : postX) {
            for (int pz : postZ) {
                for (int y = 1; y <= 3; y++) {
                    s.set(px, y, pz, post);
                }

                s.set(px, 1, 0, trim);
            }
        }

        for (int px : postX) {
            s.set(px, 3, 0, post);
        }

        for (int pz : postZ) {
            s.set(0, 3, pz, post);
        }

        addPyramidRoof(s, -rx, rx, -rz, rz, 4, stairs, Blocks.OAK_PLANKS);

        s.set(-rx, 1, 0, Blocks.LANTERN);
        s.set(rx, 1, 0, Blocks.LANTERN);

        s.set(-rx + 1, 1, -rz, Blocks.CRAFTING_TABLE);
        s.set(-rx + 2, 1, -rz, Blocks.STONECUTTER);
        s.set(rx - 1, 1, -rz, Blocks.ANVIL);

        for (int i = -2; i <= 2; i += 4) {
            s.set(i, 1, rz, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
            s.set(i, 2, rz, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        }

        return s;
    }
}
