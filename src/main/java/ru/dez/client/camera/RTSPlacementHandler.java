package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public class RTSPlacementHandler {

    private static final List<Villager> phantoms = new ArrayList<>();
    private static Vec3 lastPos = null;

    public static void startPlacement() {
        RTSClientState.placementMode = true;
        RTSClientState.selectedVillagers.clear();
        lastPos = null;
    }

    public static void completePlacement() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null) {
            return;
        }

        Vec3 pos = lastPos;

        if (pos == null) {
            pos = RTSCameraUtil.getMouseWorldPosition(mc.gameRenderer.getMainCamera(), mc.level);
        }

        if (pos == null) {
            pos = mc.player.position();
        }

        float yaw = mc.gameRenderer.getMainCamera().getYRot();

        RTSNetwork.INSTANCE.sendToServer(new RTSSpawnVillagersPacket(pos.x, pos.y, pos.z, yaw));

        RTSClientState.hasVillagers = true;
        RTSClientState.placementMode = false;

        clearPhantoms();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (!RTSCameraManager.isEnabled() || mc.level == null || mc.player == null || !RTSClientState.placementMode) {
            clearPhantoms();

            if (!RTSClientState.placementMode) {
                lastPos = null;
            }

            return;
        }

        if (phantoms.isEmpty()) {
            createPhantoms(mc);
        }

        Vec3 pos = RTSCameraUtil.getMouseWorldPosition(mc.gameRenderer.getMainCamera(), mc.level);

        if (pos != null) {
            lastPos = pos;
        }

        if (lastPos == null) {
            lastPos = mc.player.position();
        }

        float yaw = mc.gameRenderer.getMainCamera().getYRot();
        double rad = Math.toRadians(yaw);

        Vec3 left = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
        double[] offsets = new double[]{-2.0, 0.0, 2.0};

        for (int i = 0; i < phantoms.size() && i < 3; i++) {
            Vec3 p = lastPos.add(left.scale(offsets[i]));
            Villager villager = phantoms.get(i);

            villager.setPos(p.x, p.y, p.z);
            villager.setYRot(yaw);
            villager.setYBodyRot(yaw);
            villager.setYHeadRot(yaw);
        }
    }

    public static void clearPhantoms() {
        for (Villager villager : phantoms) {
            RTSClientState.phantomIds.remove(villager.getUUID());

            if (villager.level() != null) {
                villager.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        phantoms.clear();
    }

    private static void createPhantoms(Minecraft mc) {
        for (int i = 0; i < 3; i++) {
            Villager villager = new Villager(EntityType.VILLAGER, mc.level);

            villager.setNoAi(true);
            villager.setNoGravity(true);
            villager.setInvulnerable(true);
            villager.setSilent(true);

            mc.level.addFreshEntity(villager);

            phantoms.add(villager);
            RTSClientState.phantomIds.add(villager.getUUID());
        }
    }
}