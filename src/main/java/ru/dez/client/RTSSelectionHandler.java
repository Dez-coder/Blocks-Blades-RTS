package ru.dez.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import ru.dez.client.camera.RTSBuildClient;
import ru.dez.client.camera.RTSBuildPacket;
import ru.dez.client.camera.RTSBuilding;
import ru.dez.client.camera.RTSBuildings;
import ru.dez.client.camera.RTSCameraManager;
import ru.dez.client.camera.RTSClientState;
import ru.dez.client.camera.RTSNetwork;
import ru.dez.client.camera.RTSPlacementHandler;
import ru.dez.client.camera.RTSProfPacket;
import ru.dez.client.camera.RTSTeamActionPacket;
import ru.dez.client.camera.RTSTownHallClickPacket;
import ru.dez.client.camera.RTSTownHallHirePacket;
import ru.dez.client.camera.RTSUpgradeTownHallPacket;
import ru.dez.client.camera.RTSTaskClient;
import ru.dez.client.camera.RTSCameraUtil;
import ru.dez.client.camera.RTSVillagerCommandPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSSelectionHandler {

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraManager.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }

        int button = event.getButton();
        int action = event.getAction();
        Vector2d mouse = RTSCameraUtil.getMousePos();

        if (action == 1) {
            if (button == 0) {
                if (RTSBuildClient.placing) {
                    RTSBuildClient.cancelPlacement();
                    event.setCanceled(true);
                    return;
                }

                for (int i = 0; i < RTSBuildClient.buildButtons.size(); i++) {
                    RTSClientState.Rect rect = RTSBuildClient.buildButtons.get(i);

                    if (rect.contains(mouse.x, mouse.y)) {
                        handleUiAction(RTSBuildClient.buildActions.get(i));
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSBuildClient.buildMenuButton.contains(mouse.x, mouse.y)) {
                    RTSBuildClient.buildPanelOpen = !RTSBuildClient.buildPanelOpen;
                    event.setCanceled(true);
                    return;
                }

                for (int i = 0; i < RTSClientState.teamButtons.size(); i++) {
                    if (RTSClientState.teamButtons.get(i).contains(mouse.x, mouse.y)) {
                        handleUiAction(RTSClientState.teamActions.get(i));
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSClientState.teamButton.contains(mouse.x, mouse.y)) {
                    RTSClientState.teamMenuOpen = !RTSClientState.teamMenuOpen;
                    event.setCanceled(true);
                    return;
                }

                for (int i = 0; i < RTSClientState.townHallButtons.size(); i++) {
                    if (RTSClientState.townHallButtons.get(i).contains(mouse.x, mouse.y)) {
                        handleUiAction(RTSClientState.townHallActions.get(i));
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSClientState.placementMode) {
                    RTSClientState.placementMode = false;
                    RTSPlacementHandler.clearPhantoms();
                    event.setCanceled(true);
                    return;
                }

                boolean ctrl = isCtrl(mc);

                for (int i = 0; i < RTSClientState.unitButtons.size(); i++) {
                    RTSClientState.Rect rect = RTSClientState.unitButtons.get(i);

                    if (rect.contains(mouse.x, mouse.y)) {
                        UUID id = RTSClientState.unitIds.get(i);

                        if (ctrl) {
                            if (!RTSClientState.selectedVillagers.remove(id)) {
                                RTSClientState.selectedVillagers.add(id);
                            }
                        } else {
                            RTSClientState.selectedVillagers.clear();
                            RTSClientState.selectedVillagers.add(id);
                        }

                        event.setCanceled(true);
                        return;
                    }
                }

                for (int i = 0; i < RTSClientState.profButtons.size(); i++) {
                    RTSClientState.Rect rect = RTSClientState.profButtons.get(i);

                    if (rect.contains(mouse.x, mouse.y)) {
                        handleUiAction(RTSClientState.profActions.get(i));
                        event.setCanceled(true);
                        return;
                    }
                }

                for (int i = 0; i < RTSTaskClient.taskButtons.size(); i++) {
                    RTSClientState.Rect rect = RTSTaskClient.taskButtons.get(i);

                    if (rect.contains(mouse.x, mouse.y)) {
                        handleUiAction(RTSTaskClient.taskActions.get(i));
                        event.setCanceled(true);
                        return;
                    }
                }

                if (!RTSClientState.hasVillagers && RTSClientState.villagerButton.contains(mouse.x, mouse.y)) {
                    RTSPlacementHandler.startPlacement();
                    event.setCanceled(true);
                    return;
                }

                if (RTSClientState.hasVillagers && RTSClientState.professionButton.contains(mouse.x, mouse.y)) {
                    RTSClientState.professionPanelOpen = !RTSClientState.professionPanelOpen;
                    event.setCanceled(true);
                    return;
                }

                RTSClientState.selecting = true;
                RTSClientState.selectionStart = mouse;
                event.setCanceled(true);
                return;
            }

            if (button == 1) {
                if (RTSBuildClient.placing) {
                    if (RTSBuildClient.previewPos != null && RTSBuildClient.selectedBuilding != null) {
                        RTSBuilding building = RTSBuildings.get(RTSBuildClient.selectedBuilding);

                        if (building != null && RTSBuildClient.canAfford(building)) {
                            RTSNetwork.INSTANCE.sendToServer(new RTSBuildPacket(RTSBuildClient.selectedBuilding, RTSBuildClient.previewPos));
                            RTSBuildClient.cancelPlacement();
                        } else {
                            RTSBuildClient.setMessage("Не хватает ресурсов");
                        }
                    }

                    event.setCanceled(true);
                    return;
                }

                for (RTSClientState.Rect rect : RTSBuildClient.buildButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSBuildClient.buildMenuButton.contains(mouse.x, mouse.y)) {
                    event.setCanceled(true);
                    return;
                }

                for (RTSClientState.Rect rect : RTSClientState.teamButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSClientState.teamButton.contains(mouse.x, mouse.y)) {
                    event.setCanceled(true);
                    return;
                }

                for (RTSClientState.Rect rect : RTSClientState.townHallButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                if (RTSClientState.placementMode) {
                    RTSPlacementHandler.completePlacement();
                    event.setCanceled(true);
                    return;
                }

                for (RTSClientState.Rect rect : RTSClientState.unitButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                for (RTSClientState.Rect rect : RTSClientState.profButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                for (RTSClientState.Rect rect : RTSTaskClient.taskButtons) {
                    if (rect.contains(mouse.x, mouse.y)) {
                        event.setCanceled(true);
                        return;
                    }
                }

                if (!RTSClientState.selectedVillagers.isEmpty()) {
                    BlockHitResult hit = RTSCameraUtil.getMouseBlockHit(mc.gameRenderer.getMainCamera(), mc.level);

                    if (hit != null) {
                        sendCommand(0, hit.getBlockPos());
                        RTSTaskClient.setMoveTarget(hit.getBlockPos());
                        event.setCanceled(true);
                        return;
                    }
                }

                RTSClientState.selectedVillagers.clear();
                event.setCanceled(true);
            }
        } else if (action == 0) {
            if (button == 0 && RTSClientState.selecting) {
                Vector2d start = RTSClientState.selectionStart;
                boolean simpleClick = start != null && start.distanceSquared(mouse) < 16.0;

                finishSelection(mc, mouse);
                RTSClientState.selecting = false;
                RTSClientState.selectionStart = null;
                event.setCanceled(true);

                // A simple click (not a drag) may be a click on the town hall.
                // No client-side "is it built" gate: the server decides.
                if (simpleClick) {
                    BlockHitResult hit = RTSCameraUtil.getMouseBlockHit(mc.gameRenderer.getMainCamera(), mc.level);

                    if (hit != null) {
                        RTSNetwork.INSTANCE.sendToServer(new RTSTownHallClickPacket(hit.getBlockPos()));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();

        if (RTSClientState.selecting && RTSClientState.selectionStart != null) {
            Vector2d end = RTSCameraUtil.getMousePos();

            int x1 = (int) Math.min(RTSClientState.selectionStart.x, end.x);
            int y1 = (int) Math.min(RTSClientState.selectionStart.y, end.y);
            int x2 = (int) Math.max(RTSClientState.selectionStart.x, end.x);
            int y2 = (int) Math.max(RTSClientState.selectionStart.y, end.y);

            graphics.fill(x1, y1, x2, y2, 0x3000E676);
            graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, 0xFF00E676);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        List<LivingEntity> units = RTSCameraUtil.getAllUnits(mc.level);

        for (UUID id : RTSClientState.selectedVillagers) {
            LivingEntity unit = null;

            for (LivingEntity candidate : units) {
                if (candidate.getUUID().equals(id)) {
                    unit = candidate;
                    break;
                }
            }

            if (unit == null || !unit.isAlive()) {
                continue;
            }

            Vec3 center = unit.position().add(0.0, unit.getBbHeight() * 0.5, 0.0);
            Vector3d p = RTSCameraUtil.project(center, camera);

            if (p.z <= 0.0) {
                continue;
            }

            int sx = (int) p.x;
            int sy = (int) p.y;
            int size = (int) (220.0 / p.z);

            if (size < 7) {
                size = 7;
            }

            if (size > 24) {
                size = 24;
            }

            int len = Math.max(3, size / 3);
            int color = 0xFF00E676;

            graphics.fill(sx - size, sy - size, sx - size + len, sy - size + 1, color);
            graphics.fill(sx - size, sy - size, sx - size + 1, sy - size + len, color);

            graphics.fill(sx + size - len, sy - size, sx + size, sy - size + 1, color);
            graphics.fill(sx + size - 1, sy - size, sx + size, sy - size + len, color);

            graphics.fill(sx - size, sy + size - 1, sx - size + len, sy + size, color);
            graphics.fill(sx - size, sy + size - len, sx - size + 1, sy + size, color);

            graphics.fill(sx + size - len, sy + size - 1, sx + size, sy + size, color);
            graphics.fill(sx + size - 1, sy + size - len, sx + size, sy + size, color);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (!RTSCameraManager.isEnabled() || mc.screen != null) {
            RTSClientState.selecting = false;
            RTSClientState.selectionStart = null;

            if (!RTSCameraManager.isEnabled()) {
                RTSClientState.selectedVillagers.clear();
                RTSClientState.unitButtons.clear();
                RTSClientState.unitIds.clear();
                RTSTaskClient.reset();
                RTSBuildClient.reset();
            }
        }
    }

    private static void finishSelection(Minecraft mc, Vector2d mouse) {
        if (RTSClientState.selectionStart == null) {
            return;
        }

        Vector2d start = RTSClientState.selectionStart;
        Vector2d end = mouse;
        boolean ctrl = isCtrl(mc);

        if (Math.abs(end.x - start.x) < 4.0 && Math.abs(end.y - start.y) < 4.0) {
            LivingEntity clicked = getClickedUnit(mc, mouse);

            if (clicked != null) {
                if (ctrl) {
                    if (!RTSClientState.selectedVillagers.remove(clicked.getUUID())) {
                        RTSClientState.selectedVillagers.add(clicked.getUUID());
                    }
                } else {
                    RTSClientState.selectedVillagers.clear();
                    RTSClientState.selectedVillagers.add(clicked.getUUID());
                }

                return;
            }

            if (!ctrl && !RTSClientState.selectedVillagers.isEmpty()) {
                BlockHitResult hit = RTSCameraUtil.getMouseBlockHit(mc.gameRenderer.getMainCamera(), mc.level);

                if (hit != null) {
                    sendCommand(1, hit.getBlockPos());
                    RTSTaskClient.setMineTarget(hit.getBlockPos());
                    return;
                }
            }

            if (!ctrl) {
                RTSClientState.selectedVillagers.clear();
            }
        } else {
            selectRect(mc, start, end, ctrl);
        }
    }

    private static LivingEntity getClickedUnit(Minecraft mc, Vector2d mouse) {
        Camera camera = mc.gameRenderer.getMainCamera();

        LivingEntity best = null;
        double bestDist = 100.0;

        for (LivingEntity unit : RTSCameraUtil.getAllUnits(mc.level)) {
            if (!unit.isAlive()) {
                continue;
            }

            Vec3 center = unit.position().add(0.0, unit.getBbHeight() * 0.5, 0.0);
            Vector3d p = RTSCameraUtil.project(center, camera);

            if (p.z <= 0.0) {
                continue;
            }

            double dx = p.x - mouse.x;
            double dy = p.y - mouse.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 12.0 && dist < bestDist) {
                bestDist = dist;
                best = unit;
            }
        }

        return best;
    }

    private static void selectRect(Minecraft mc, Vector2d start, Vector2d end, boolean ctrl) {
        Camera camera = mc.gameRenderer.getMainCamera();

        double minX = Math.min(start.x, end.x);
        double minY = Math.min(start.y, end.y);
        double maxX = Math.max(start.x, end.x);
        double maxY = Math.max(start.y, end.y);

        if (!ctrl) {
            RTSClientState.selectedVillagers.clear();
        }

        for (LivingEntity unit : RTSCameraUtil.getAllUnits(mc.level)) {
            if (!unit.isAlive()) {
                continue;
            }

            Vec3 center = unit.position().add(0.0, unit.getBbHeight() * 0.5, 0.0);
            Vector3d p = RTSCameraUtil.project(center, camera);

            if (p.z <= 0.0) {
                continue;
            }

            if (p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY) {
                RTSClientState.selectedVillagers.add(unit.getUUID());
            }
        }
    }

    private static void sendCommand(int command, BlockPos pos) {
        if (RTSClientState.selectedVillagers.isEmpty()) {
            return;
        }

        List<UUID> ids = new ArrayList<>(RTSClientState.selectedVillagers);

        RTSNetwork.INSTANCE.sendToServer(new RTSVillagerCommandPacket(command, pos, ids));
    }

    private static void handleUiAction(String action) {
        if (action.equals("hire")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTownHallHirePacket());
        } else if (action.equals("close")) {
            RTSClientState.townHallPanelOpen = false;
        } else if (action.equals("accept")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTeamActionPacket("accept", null));
        } else if (action.equals("decline")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTeamActionPacket("decline", null));
        } else if (action.equals("leave")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTeamActionPacket("leave", null));
        } else if (action.startsWith("invite:")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTeamActionPacket("invite", UUID.fromString(action.substring(7))));
        } else if (action.startsWith("war:")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSTeamActionPacket("war", UUID.fromString(action.substring(4))));
        } else if (action.equals("toggle_task")) {
            RTSTaskClient.taskPanelOpen = !RTSTaskClient.taskPanelOpen;

            if (!RTSTaskClient.taskPanelOpen) {
                RTSTaskClient.miningPanelOpen = false;
            }
        } else if (action.equals("mine_menu")) {
            RTSTaskClient.miningPanelOpen = !RTSTaskClient.miningPanelOpen;
        } else if (action.equals("build_task")) {
            if (RTSBuildClient.activeSitePos == null) {
                RTSBuildClient.setMessage("Нет стройплощадки");
            } else if (RTSClientState.selectedVillagers.isEmpty()) {
                RTSBuildClient.setMessage("Нет выбранных жителей");
            } else {
                sendCommand(5, RTSBuildClient.activeSitePos);
            }
        } else if (action.equals("auto_cobble")) {
            sendCommand(2, null);
        } else if (action.equals("auto_coal")) {
            sendCommand(3, null);
        } else if (action.equals("auto_iron")) {
            sendCommand(9, null);
        } else if (action.equals("auto_gold")) {
            sendCommand(10, null);
        } else if (action.equals("auto_diamond")) {
            sendCommand(11, null);
        } else if (action.equals("defend")) {
            sendCommand(8, null);
        } else if (action.equals("upgrade_town_hall")) {
            RTSNetwork.INSTANCE.sendToServer(new RTSUpgradeTownHallPacket());
        } else if (action.equals("auto_wood")) {
            sendCommand(4, null);
        } else if (action.equals("auto_hunt")) {
            sendCommand(6, null);
        } else if (action.startsWith("prof_")) {
            int prof = action.equals("prof_miner") ? 1
                    : action.equals("prof_lumber") ? 2
                    : action.equals("prof_farmer") ? 3
                    : action.equals("prof_arbalest") ? 5
                    : action.equals("prof_summoner") ? 6
                    : 4;

            if (RTSClientState.selectedVillagers.isEmpty()) {
                RTSBuildClient.setMessage("Выбери жителей");
            } else {
                RTSNetwork.INSTANCE.sendToServer(new RTSProfPacket(prof, new ArrayList<>(RTSClientState.selectedVillagers)));
            }
        } else if (action.startsWith("build:")) {
            String id = action.substring(6);
            RTSBuildClient.tryStartPlacement(id);
        }
    }

    private static boolean isCtrl(Minecraft mc) {
        long window = mc.getWindow().getWindow();

        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}