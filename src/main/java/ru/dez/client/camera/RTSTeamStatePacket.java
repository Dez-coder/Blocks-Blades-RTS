package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client snapshot of the player's village, online players and wars. */
public class RTSTeamStatePacket {

    private final String villageId;
    private final String villageName;
    private final List<UUID> memberIds = new ArrayList<>();
    private final List<String> memberNames = new ArrayList<>();
    private final List<UUID> onlineIds = new ArrayList<>();
    private final List<String> onlineNames = new ArrayList<>();
    private final List<String> onlineVillages = new ArrayList<>();
    private final String inviteVillageId;
    private final String inviteVillageName;
    private final List<String> warsWith = new ArrayList<>();

    public RTSTeamStatePacket(String villageId, String villageName,
                              List<UUID> memberIds, List<String> memberNames,
                              List<UUID> onlineIds, List<String> onlineNames, List<String> onlineVillages,
                              String inviteVillageId, String inviteVillageName,
                              List<String> warsWith) {
        this.villageId = villageId;
        this.villageName = villageName;
        this.memberIds.addAll(memberIds);
        this.memberNames.addAll(memberNames);
        this.onlineIds.addAll(onlineIds);
        this.onlineNames.addAll(onlineNames);
        this.onlineVillages.addAll(onlineVillages);
        this.inviteVillageId = inviteVillageId;
        this.inviteVillageName = inviteVillageName;
        this.warsWith.addAll(warsWith);
    }

    public RTSTeamStatePacket(FriendlyByteBuf buf) {
        this.villageId = buf.readUtf(64);
        this.villageName = buf.readUtf(64);

        int members = buf.readInt();

        for (int i = 0; i < members; i++) {
            memberIds.add(buf.readUUID());
            memberNames.add(buf.readUtf(64));
        }

        int online = buf.readInt();

        for (int i = 0; i < online; i++) {
            onlineIds.add(buf.readUUID());
            onlineNames.add(buf.readUtf(64));
            onlineVillages.add(buf.readUtf(64));
        }

        this.inviteVillageId = buf.readUtf(64);
        this.inviteVillageName = buf.readUtf(64);

        int wars = buf.readInt();

        for (int i = 0; i < wars; i++) {
            warsWith.add(buf.readUtf(64));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(villageId);
        buf.writeUtf(villageName);
        buf.writeInt(memberIds.size());

        for (int i = 0; i < memberIds.size(); i++) {
            buf.writeUUID(memberIds.get(i));
            buf.writeUtf(memberNames.get(i));
        }

        buf.writeInt(onlineIds.size());

        for (int i = 0; i < onlineIds.size(); i++) {
            buf.writeUUID(onlineIds.get(i));
            buf.writeUtf(onlineNames.get(i));
            buf.writeUtf(onlineVillages.get(i));
        }

        buf.writeUtf(inviteVillageId);
        buf.writeUtf(inviteVillageName);
        buf.writeInt(warsWith.size());

        for (String war : warsWith) {
            buf.writeUtf(war);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                return;
            }

            RTSTeamClient.set(villageId, villageName, memberIds, memberNames,
                    onlineIds, onlineNames, onlineVillages,
                    inviteVillageId, inviteVillageName, warsWith);
        });

        ctx.get().setPacketHandled(true);
    }
}
