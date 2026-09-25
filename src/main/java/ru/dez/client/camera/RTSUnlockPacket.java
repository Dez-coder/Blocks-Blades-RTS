package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public class RTSUnlockPacket {

    private final List<String> completed;

    public RTSUnlockPacket(List<String> completed) {
        this.completed = completed;
    }

    public RTSUnlockPacket(FriendlyByteBuf buf) {
        this.completed = new ArrayList<>();

        int size = buf.readInt();

        for (int i = 0; i < size; i++) {
            this.completed.add(buf.readUtf(32767));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(completed.size());

        for (String id : completed) {
            buf.writeUtf(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                RTSBuildings.setCompleted(completed);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}