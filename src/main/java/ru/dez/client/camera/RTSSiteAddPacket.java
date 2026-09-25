package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public class RTSSiteAddPacket {

    private final int id;
    private final String buildingId;
    private final BlockPos pos;
    private final int total;

    public RTSSiteAddPacket(int id, String buildingId, BlockPos pos, int total) {
        this.id = id;
        this.buildingId = buildingId;
        this.pos = pos;
        this.total = total;
    }

    public RTSSiteAddPacket(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.buildingId = buf.readUtf(32767);
        this.pos = buf.readBlockPos();
        this.total = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(id);
        buf.writeUtf(buildingId);
        buf.writeBlockPos(pos);
        buf.writeInt(total);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                RTSBuildClient.addSite(id, buildingId, pos);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}