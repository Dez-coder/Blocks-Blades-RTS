package ru.dez.client.camera;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: pay for one queued villager at the town hall. */
public class RTSTownHallHirePacket {

    public RTSTownHallHirePacket() {
    }

    public RTSTownHallHirePacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            UUID village = RTSTeamManager.villageId(player.getUUID());

            if (!RTSResourceManager.canAfford(village, 0, RTSVillagerTaskManager.HIRE_COST, 0)) {
                player.sendSystemMessage(Component.literal("Не хватает еды для найма жителя"));
                RTSResourceManager.sync(player);
                RTSVillagerTaskManager.broadcastHire(
                        player.getServer(), RTSProtectedData.get(player.getServer()));
                return;
            }

            RTSResourceManager.deduct(village, 0, RTSVillagerTaskManager.HIRE_COST, 0);

            RTSProtectedData data = RTSProtectedData.get(player.getServer());
            data.addHire(village, 1);

            for (ServerPlayer member : player.getServer().getPlayerList().getPlayers()) {
                RTSResourceManager.sync(member);
            }

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSTownHallPanelPacket(false, data.getHireQueue(village), RTSVillagerTaskManager.HIRE_COST)
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
