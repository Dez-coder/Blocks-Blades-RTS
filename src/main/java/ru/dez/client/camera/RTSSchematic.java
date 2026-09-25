package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RTSSchematic {

    public final List<Entry> entries = new ArrayList<>();

    public int minX = 0;
    public int minY = 0;
    public int minZ = 0;
    public int maxX = 0;
    public int maxY = 0;
    public int maxZ = 0;

    private boolean boundsInit = false;

    public void set(int x, int y, int z, Block block) {
        if (block == null) {
            return;
        }

        set(x, y, z, block.defaultBlockState());
    }

    public void set(int x, int y, int z, BlockState state) {
        if (state == null || state.isAir()) {
            return;
        }

        if (!boundsInit) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
            boundsInit = true;
        } else {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        entries.add(new Entry(x, y, z, state));
    }

    public static class Entry {
        public final int x;
        public final int y;
        public final int z;
        public final BlockState state;
        public final Block block;

        public Entry(int x, int y, int z, BlockState state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.block = state.getBlock();
        }
    }
}
