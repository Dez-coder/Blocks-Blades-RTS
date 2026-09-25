package ru.dez.client.camera;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server request to buy the next town hall tier. The upgrade is
 * applied instantly (no construction site) and also upgrades every built house
 * to the same tier.
 */
public class RTSUpgradeTownHallPacket {

    public RTSUpgradeTownHallPacket() {
    }

    public RTSUpgradeTownHallPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            RTSVillageUpgradeManager.upgradeTownHall(player.serverLevel(), player);
        });

        ctx.get().setPacketHandled(true);
    }
}
