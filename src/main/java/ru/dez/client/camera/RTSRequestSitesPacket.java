package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server packet asking the server to re-send every active
 * construction site.
 *
 * The client's site list lives in memory only, so a half-built schematic could
 * silently disappear from the preview (even though the site still exists on the
 * server and the resources were already spent) whenever the client's list is
 * cleared. Sending this when the player (re)enters RTS mode guarantees the
 * preview is always restored.
 */
public class RTSRequestSitesPacket {

    public RTSRequestSitesPacket() {
    }

    public RTSRequestSitesPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            RTSConstructionManager.resendTo(player);
        });

        ctx.get().setPacketHandled(true);
    }
}
