package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server: the player entered or left the RTS camera mode. The server
 * hides the player (so it does not get attacked / seen while it is parked at
 * the camera focus) and restores everything when the mode is left.
 */
public class RTSModePacket {

    private final boolean enabled;

    public RTSModePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public RTSModePacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            if (enabled) {
                RTSCameraMode.enter(player);
            } else {
                RTSCameraMode.exit(player);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
