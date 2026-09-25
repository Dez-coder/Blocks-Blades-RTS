package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client list of every completed building (for hover outlines). */
public class RTSPlacementsPacket {

    private final List<RTSBuildingClient.Entry> entries = new ArrayList<>();

    public RTSPlacementsPacket(List<RTSBuildingClient.Entry> entries) {
        this.entries.addAll(entries);
    }

    public RTSPlacementsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();

        for (int i = 0; i < size; i++) {
            entries.add(new RTSBuildingClient.Entry(
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entries.size());

        for (RTSBuildingClient.Entry entry : entries) {
            buf.writeUtf(entry.id);
            buf.writeUtf(entry.dim);
            buf.writeInt(entry.x);
            buf.writeInt(entry.y);
            buf.writeInt(entry.z);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                return;
            }

            RTSBuildingClient.setAll(entries);
        });

        ctx.get().setPacketHandled(true);
    }
}
