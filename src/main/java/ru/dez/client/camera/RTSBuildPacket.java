package ru.dez.client.camera;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class RTSBuildPacket {

    private final String id;
    private final BlockPos pos;

    public RTSBuildPacket(String id, BlockPos pos) {
        this.id = id;
        this.pos = pos;
    }

    public RTSBuildPacket(FriendlyByteBuf buf) {
        this.id = buf.readUtf(32767);
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            RTSBuilding building = RTSBuildings.get(id);

            if (building == null || building.schematic == null) {
                return;
            }

            UUID village = RTSTeamManager.villageId(player.getUUID());

            if (!RTSResourceManager.canAfford(village, building.costWood, building.costFood, building.costOre)) {
                player.sendSystemMessage(Component.literal("Не хватает ресурсов"));
                RTSResourceManager.sync(player);
                return;
            }

            RTSResourceManager.deduct(village, building.costWood, building.costFood, building.costOre);
            RTSResourceManager.sync(player);

            RTSConstructionManager.Site site = RTSConstructionManager.create(player.serverLevel(), id, pos, village);

            if (site != null) {
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RTSSiteAddPacket(site.id, site.buildingId, site.origin, (int) site.total)
                );
            }
        });

        ctx.get().setPacketHandled(true);
    }
}