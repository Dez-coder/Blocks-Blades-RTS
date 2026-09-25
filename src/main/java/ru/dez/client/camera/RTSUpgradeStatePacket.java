package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client packet carrying the current town hall tier, so the HUD can
 * show the correct upgrade button (and hide it once maxed out).
 */
public class RTSUpgradeStatePacket {

    private final int townHallLevel;

    public RTSUpgradeStatePacket(int townHallLevel) {
        this.townHallLevel = townHallLevel;
    }

    public RTSUpgradeStatePacket(FriendlyByteBuf buf) {
        this.townHallLevel = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(townHallLevel);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                return;
            }

            RTSClientState.townHallLevel = townHallLevel;
        });

        ctx.get().setPacketHandled(true);
    }
}
