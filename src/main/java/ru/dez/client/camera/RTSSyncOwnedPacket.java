package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client packet that pushes the full set of RTS-owned villager UUIDs.
 *
 * Entity tags are not synced to clients, so this packet is how the client
 * learns which villagers it is allowed to select. It is sent whenever the set
 * of owned villagers changes (spawn) and when a player logs in.
 */
public class RTSSyncOwnedPacket {

    private final List<UUID> ids;

    public RTSSyncOwnedPacket(List<UUID> ids) {
        this.ids = ids;
    }

    public RTSSyncOwnedPacket(FriendlyByteBuf buf) {
        this.ids = new ArrayList<>();

        int size = buf.readInt();

        for (int i = 0; i < size; i++) {
            this.ids.add(buf.readUUID());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(ids.size());

        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                RTSOwnedClient.setAll(ids);

                // A non-empty owned set means the village already exists, so
                // the HUD has to show the resource/job UI instead of the
                // initial "spawn 3 villagers" button after rejoining a world.
                RTSClientState.hasVillagers = !ids.isEmpty();
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
