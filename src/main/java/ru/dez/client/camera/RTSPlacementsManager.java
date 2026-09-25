package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Sends the list of completed buildings to clients so they can be outlined. */
public final class RTSPlacementsManager {

    private RTSPlacementsManager() {
    }

    public static List<RTSBuildingClient.Entry> build(RTSProtectedData data) {
        List<RTSBuildingClient.Entry> list = new ArrayList<>();

        for (RTSProtectedData.PlacedBuilding placed : data.placements) {
            list.add(new RTSBuildingClient.Entry(placed.id, placed.dim, placed.x, placed.y, placed.z));
        }

        return list;
    }

    public static void broadcast(MinecraftServer server) {
        List<RTSBuildingClient.Entry> list = build(RTSProtectedData.get(server));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSPlacementsPacket(list)
            );
        }
    }

    public static void sendTo(MinecraftServer server, ServerPlayer player) {
        RTSNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RTSPlacementsPacket(build(RTSProtectedData.get(server)))
        );
    }
}
