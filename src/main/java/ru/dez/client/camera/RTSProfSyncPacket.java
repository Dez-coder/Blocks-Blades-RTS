package ru.dez.client.camera;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client packet that pushes villager professions and their personal
 * levels. Sent on login and refreshed periodically so the HUD stays current.
 */
public class RTSProfSyncPacket {

    private final Map<UUID, Integer> profs;
    private final Map<UUID, Integer> levels;

    public RTSProfSyncPacket(Map<UUID, Integer> profs, Map<UUID, Integer> levels) {
        this.profs = profs;
        this.levels = levels;
    }

    public RTSProfSyncPacket(FriendlyByteBuf buf) {
        this.profs = readMap(buf);
        this.levels = readMap(buf);
    }

    private static Map<UUID, Integer> readMap(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<UUID, Integer> map = new HashMap<>();

        for (int i = 0; i < size; i++) {
            map.put(buf.readUUID(), buf.readInt());
        }

        return map;
    }

    private static void writeMap(FriendlyByteBuf buf, Map<UUID, Integer> map) {
        buf.writeInt(map.size());

        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        writeMap(buf, profs);
        writeMap(buf, levels);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                return;
            }

            RTSProfClient.setAll(profs, levels);
        });

        ctx.get().setPacketHandled(true);
    }
}
