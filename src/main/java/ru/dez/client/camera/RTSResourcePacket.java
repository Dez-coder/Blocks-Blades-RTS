package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public class RTSResourcePacket {

    private final int wood;
    private final int food;
    private final int ore;
    private final int happiness;

    public RTSResourcePacket(int wood, int food, int ore, int happiness) {
        this.wood = wood;
        this.food = food;
        this.ore = ore;
        this.happiness = happiness;
    }

    public RTSResourcePacket(FriendlyByteBuf buf) {
        this.wood = buf.readInt();
        this.food = buf.readInt();
        this.ore = buf.readInt();
        this.happiness = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(wood);
        buf.writeInt(food);
        buf.writeInt(ore);
        buf.writeInt(happiness);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                RTSClientState.wood = wood;
                RTSClientState.food = food;
                RTSClientState.ore = ore;
                RTSClientState.happiness = happiness;
            }
        });

        ctx.get().setPacketHandled(true);
    }
}