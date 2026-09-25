package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public class RTSSiteRemovePacket {

    private final int id;

    public RTSSiteRemovePacket(int id) {
        this.id = id;
    }

    public RTSSiteRemovePacket(FriendlyByteBuf buf) {
        this.id = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(id);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                RTSBuildClient.removeSite(id);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}