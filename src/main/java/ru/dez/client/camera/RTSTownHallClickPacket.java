package ru.dez.client.camera;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: the player clicked a block; open the hire panel if it is a town hall. */
public class RTSTownHallClickPacket {

    private final BlockPos pos;

    public RTSTownHallClickPacket(BlockPos pos) {
        this.pos = pos;
    }

    public RTSTownHallClickPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            ServerLevel level = player.serverLevel();
            RTSProtectedData data = RTSProtectedData.get(level.getServer());
            UUID village = RTSTeamManager.villageId(player.getUUID());

            RTSProtectedData.PlacedBuilding townHall =
                    data.findTownHall(level.dimension().location().toString(), pos);

            if (townHall != null) {
                player.sendSystemMessage(Component.literal("Мэрия: панель найма открыта"));
            }

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSTownHallPanelPacket(
                            townHall != null,
                            townHall == null ? -1 : data.getHireQueue(village),
                            RTSVillagerTaskManager.HIRE_COST
                    )
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
