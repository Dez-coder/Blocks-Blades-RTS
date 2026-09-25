package ru.dez.client.camera;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public class RTSBuildClient {

    public static boolean buildPanelOpen = false;
    public static boolean placing = false;
    public static String selectedBuilding = null;
    public static BlockPos previewPos = null;

    public static int activeSiteId = -1;
    public static BlockPos activeSitePos = null;

    public static String message = null;
    public static long messageTime = 0L;

    public static final Map<Integer, ClientSite> sites = new LinkedHashMap<>();

    public static final List<RTSClientState.Rect> buildButtons = new java.util.ArrayList<>();
    public static final List<String> buildActions = new java.util.ArrayList<>();

    public static RTSClientState.Rect buildMenuButton = new RTSClientState.Rect(0, 0, 0, 0);

    public static void reset() {
        buildPanelOpen = false;
        placing = false;
        selectedBuilding = null;
        previewPos = null;

        activeSiteId = -1;
        activeSitePos = null;

        message = null;
        messageTime = 0L;

        sites.clear();
        buildButtons.clear();
        buildActions.clear();
        buildMenuButton = new RTSClientState.Rect(0, 0, 0, 0);
    }

    public static boolean canAfford(RTSBuilding building) {
        return RTSClientState.wood >= building.costWood
                && RTSClientState.food >= building.costFood
                && RTSClientState.ore >= building.costOre;
    }

    public static boolean tryStartPlacement(String buildingId) {
        RTSBuilding building = RTSBuildings.get(buildingId);

        if (building == null || !building.unlocked) {
            return false;
        }

        if (!canAfford(building)) {
            setMessage("Не хватает ресурсов");
            return false;
        }

        placing = true;
        selectedBuilding = buildingId;
        buildPanelOpen = false;
        previewPos = null;

        return true;
    }

    public static void cancelPlacement() {
        placing = false;
        selectedBuilding = null;
        previewPos = null;
    }

    public static void addSite(int id, String buildingId, BlockPos pos) {
        sites.put(id, new ClientSite(id, buildingId, pos));
        activeSiteId = id;
        activeSitePos = pos;

        placing = false;
        selectedBuilding = null;
        previewPos = null;
    }

    public static void removeSite(int id) {
        sites.remove(id);

        if (activeSiteId == id) {
            activeSiteId = -1;
            activeSitePos = null;
        }
    }

    public static void setMessage(String msg) {
        message = msg;
        messageTime = System.currentTimeMillis();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (message != null && System.currentTimeMillis() - messageTime > 3000L) {
            message = null;
        }

        if (!RTSCameraManager.isEnabled() || mc.level == null || mc.player == null) {
            cancelPlacement();
            buildPanelOpen = false;
            return;
        }

        if (!placing || selectedBuilding == null) {
            previewPos = null;
            return;
        }

        BlockHitResult hit = RTSCameraUtil.getMouseBlockHit(mc.gameRenderer.getMainCamera(), mc.level);

        if (hit != null) {
            previewPos = hit.getBlockPos().above();
        } else {
            previewPos = null;
        }
    }

    public static class ClientSite {
        public final int id;
        public final String buildingId;
        public final BlockPos pos;

        public ClientSite(int id, String buildingId, BlockPos pos) {
            this.id = id;
            this.buildingId = buildingId;
            this.pos = pos;
        }
    }
}