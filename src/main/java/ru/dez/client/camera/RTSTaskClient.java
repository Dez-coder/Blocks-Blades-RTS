package ru.dez.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class RTSTaskClient {

    public static boolean taskPanelOpen = false;
    public static boolean miningPanelOpen = false;

    public static final List<RTSClientState.Rect> taskButtons = new ArrayList<>();
    public static final List<String> taskActions = new ArrayList<>();

    public static BlockPos moveTarget = null;
    public static BlockPos mineTarget = null;
    public static long targetTime = 0L;

    public static void reset() {
        taskPanelOpen = false;
        miningPanelOpen = false;
        taskButtons.clear();
        taskActions.clear();
        moveTarget = null;
        mineTarget = null;
        targetTime = 0L;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            reset();
            return;
        }

        if (mineTarget != null && mc.level.getBlockState(mineTarget).isAir()) {
            mineTarget = null;
        }

        if (moveTarget != null && System.currentTimeMillis() - targetTime > 8000L) {
            moveTarget = null;
        }
    }

    public static void setMoveTarget(BlockPos pos) {
        moveTarget = pos;
        targetTime = System.currentTimeMillis();
    }

    public static void setMineTarget(BlockPos pos) {
        mineTarget = pos;
        targetTime = System.currentTimeMillis();
    }
}