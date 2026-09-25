package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client state of the town hall hire panel.
 *
 * <p>{@code open} tells the client the player just clicked a town hall and the
 * panel should pop up; a negative {@code queue} closes it (the click missed).
 * Plain refreshes use {@code open == false} so teammates who did not open the
 * panel do not get it forced on them.
 */
public class RTSTownHallPanelPacket {

    private final boolean open;
    private final int queue;
    private final int cost;

    public RTSTownHallPanelPacket(boolean open, int queue, int cost) {
        this.open = open;
        this.queue = queue;
        this.cost = cost;
    }

    public RTSTownHallPanelPacket(FriendlyByteBuf buf) {
        this.open = buf.readBoolean();
        this.queue = buf.readInt();
        this.cost = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeInt(queue);
        buf.writeInt(cost);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                return;
            }

            if (queue < 0) {
                RTSClientState.townHallPanelOpen = false;
                return;
            }

            if (open) {
                RTSClientState.townHallPanelOpen = true;
            }

            if (RTSClientState.townHallPanelOpen) {
                RTSClientState.hireQueue = queue;
                RTSClientState.hireCost = cost;
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
