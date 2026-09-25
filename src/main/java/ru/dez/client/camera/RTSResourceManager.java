package ru.dez.client.camera;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class RTSResourceManager {

    private static final Map<UUID, Resources> RESOURCES = new HashMap<>();

    public static Resources get(UUID id) {
        return RESOURCES.computeIfAbsent(id, k -> new Resources());
    }

    /**
     * Pulls saved wood/food/ore/happiness out of the world save (if any) into
     * the live in-memory cache. Called when a player (re)joins so a village's
     * stockpile survives a world reload instead of resetting to zero.
     */
    public static void restore(MinecraftServer server, UUID id) {
        int[] saved = RTSProtectedData.get(server).getResources(id);

        if (saved == null) {
            return;
        }

        Resources resources = get(id);
        resources.wood = saved[0];
        resources.food = saved[1];
        resources.ore = saved[2];
        resources.happiness = saved[3];
    }

    public static void add(UUID id, int wood, int food, int ore, int happiness) {
        Resources resources = get(id);

        resources.wood += wood;
        resources.food += food;
        resources.ore += ore;
        resources.happiness += happiness;
    }

    public static boolean canAfford(UUID id, int wood, int food, int ore) {
        Resources resources = get(id);

        return resources.wood >= wood
                && resources.food >= food
                && resources.ore >= ore;
    }

    public static boolean deduct(UUID id, int wood, int food, int ore) {
        if (!canAfford(id, wood, food, ore)) {
            return false;
        }

        Resources resources = get(id);

        resources.wood -= wood;
        resources.food -= food;
        resources.ore -= ore;

        return true;
    }

    public static void sync(ServerPlayer player) {
        // Resources belong to the whole village, so teammates share one pool.
        UUID village = RTSTeamManager.villageId(player.getUUID());
        Resources resources = get(village);

        RTSProtectedData.get(player.getServer())
                .setResources(village, resources.wood, resources.food, resources.ore, resources.happiness);

        RTSNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RTSResourcePacket(resources.wood, resources.food, resources.ore, resources.happiness)
        );
    }

    public static class Resources {
        public int wood = 0;
        public int food = 0;
        public int ore = 0;
        public int happiness = 100;
    }
}