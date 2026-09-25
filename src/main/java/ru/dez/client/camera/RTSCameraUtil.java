package ru.dez.client.camera;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2d;
import org.joml.Vector3d;
import ru.dez.entity.RTSArbalest;
import ru.dez.entity.RTSNpc;
import ru.dez.entity.RTSSummoner;
import ru.dez.entity.RTSWarrior;

public class RTSCameraUtil {

    public static Vector2d getMousePos() {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();

        if (scale <= 0.0) {
            return new Vector2d(0.0, 0.0);
        }

        return new Vector2d(mc.mouseHandler.xpos() / scale, mc.mouseHandler.ypos() / scale);
    }

    public static int getGuiWidth() {
        return (int) Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int getGuiHeight() {
        return (int) Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public static Vec3 getForward(Camera camera) {
        float yaw = (float) Math.toRadians(camera.getYRot());
        float pitch = (float) Math.toRadians(camera.getXRot());

        return new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        );
    }

    public static Vec3 getRight(Camera camera) {
        float yaw = (float) Math.toRadians(camera.getYRot());

        return new Vec3(
                -Math.cos(yaw),
                0.0,
                -Math.sin(yaw)
        );
    }

    public static Vec3 getUp(Camera camera) {
        return getRight(camera).cross(getForward(camera)).normalize();
    }

    public static Vector3d project(Vec3 world, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        int width = getGuiWidth();
        int height = getGuiHeight();

        if (width <= 0 || height <= 0) {
            return new Vector3d(-1000.0, -1000.0, -1.0);
        }

        Vec3 rel = world.subtract(camera.getPosition());
        Vec3 forward = getForward(camera);
        Vec3 right = getRight(camera);
        Vec3 up = right.cross(forward).normalize();

        double x = rel.dot(right);
        double y = rel.dot(up);
        double z = rel.dot(forward);

        if (z <= 0.01) {
            return new Vector3d(-1000.0, -1000.0, -1.0);
        }

        double fov = mc.options.fov().get();
        double scale = height / (2.0 * Math.tan(Math.toRadians(fov) / 2.0));

        return new Vector3d(
                width / 2.0 + x / z * scale,
                height / 2.0 - y / z * scale,
                z
        );
    }

    public static BlockHitResult getMouseBlockHit(Camera camera, Level level) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || level == null) {
            return null;
        }

        int width = getGuiWidth();
        int height = getGuiHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        Vector2d mouse = getMousePos();

        double ndcX = mouse.x / width * 2.0 - 1.0;
        double ndcY = 1.0 - mouse.y / height * 2.0;

        double fov = mc.options.fov().get();
        double aspect = (double) width / (double) height;

        double halfH = Math.tan(Math.toRadians(fov) / 2.0);
        double halfW = halfH * aspect;

        Vec3 forward = getForward(camera);
        Vec3 right = getRight(camera);
        Vec3 up = right.cross(forward).normalize();

        Vec3 dir = forward
                .add(right.scale(ndcX * halfW))
                .add(up.scale(ndcY * halfH))
                .normalize();

        Vec3 start = camera.getPosition();
        Vec3 end = start.add(dir.scale(80.0));

        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            return hit;
        }

        return null;
    }

    public static Vec3 getMouseWorldPosition(Camera camera, Level level) {
        BlockHitResult hit = getMouseBlockHit(camera, level);

        if (hit != null) {
            return hit.getLocation();
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || level == null) {
            return null;
        }

        int width = getGuiWidth();
        int height = getGuiHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        Vector2d mouse = getMousePos();

        double ndcX = mouse.x / width * 2.0 - 1.0;
        double ndcY = 1.0 - mouse.y / height * 2.0;

        double fov = mc.options.fov().get();
        double aspect = (double) width / (double) height;

        double halfH = Math.tan(Math.toRadians(fov) / 2.0);
        double halfW = halfH * aspect;

        Vec3 forward = getForward(camera);
        Vec3 right = getRight(camera);
        Vec3 up = right.cross(forward).normalize();

        Vec3 dir = forward
                .add(right.scale(ndcX * halfW))
                .add(up.scale(ndcY * halfH))
                .normalize();

        Vec3 start = camera.getPosition();

        if (dir.y < -0.0001) {
            double t = (0.0 - start.y) / dir.y;

            if (t > 0.0 && t < 200.0) {
                return start.add(dir.scale(t));
            }
        }

        return null;
    }

    public static List<Villager> getAllVillagers(Level level) {
        Minecraft mc = Minecraft.getInstance();

        if (level == null || mc.player == null) {
            return Collections.emptyList();
        }

        AABB box = mc.player.getBoundingBox().inflate(1024.0, 256.0, 1024.0);

        return level.getEntitiesOfClass(
                Villager.class,
                box,
                villager -> isRtsVillager(villager)
                        && !RTSClientState.phantomIds.contains(villager.getUUID())
        );
    }

    /**
     * An RTS villager: our animated {@link RTSNpc} (always ours) or any villager
     * the server has marked as owned. The animated NPC is accepted directly so
     * selection keeps working even if the owned-set sync has not arrived yet.
     */
    private static boolean isRtsVillager(Villager villager) {
        return villager instanceof RTSNpc || RTSOwnedClient.isOwned(villager.getUUID());
    }

    /** Any selectable RTS unit: villagers and every combatant kind. */
    private static boolean isRtsUnit(LivingEntity entity) {
        // Summoned Vexes are ours by definition, so they can be selected and
        // ordered around without waiting for the owned-set sync.
        if (entity instanceof RTSNpc || entity instanceof RTSWarrior
                || entity instanceof RTSArbalest || entity instanceof RTSSummoner
                || entity instanceof Vex) {
            return true;
        }

        return (entity instanceof Villager || entity instanceof Vindicator
                || entity instanceof Pillager || entity instanceof Evoker || entity instanceof Vex)
                && RTSOwnedClient.isOwned(entity.getUUID());
    }

    /**
     * Like {@link #getAllVillagers(Level)} but also returns RTS warriors
     * (Vindicators), so they can be selected alongside villagers.
     */
    public static List<LivingEntity> getAllUnits(Level level) {
        Minecraft mc = Minecraft.getInstance();

        if (level == null || mc.player == null) {
            return Collections.emptyList();
        }

        AABB box = mc.player.getBoundingBox().inflate(1024.0, 256.0, 1024.0);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> isRtsUnit(entity)
                        && !RTSClientState.phantomIds.contains(entity.getUUID())
        );
    }

    public static Villager getVillager(Level level, UUID id) {
        Minecraft mc = Minecraft.getInstance();

        if (level == null || id == null || mc.player == null) {
            return null;
        }

        AABB box = mc.player.getBoundingBox().inflate(1024.0, 256.0, 1024.0);

        List<Villager> list = level.getEntitiesOfClass(
                Villager.class,
                box,
                villager -> villager.getUUID().equals(id)
                        && isRtsVillager(villager)
                        && !RTSClientState.phantomIds.contains(id)
        );

        return list.isEmpty() ? null : list.get(0);
    }
}