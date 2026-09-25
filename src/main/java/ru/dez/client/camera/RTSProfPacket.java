package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class RTSProfPacket {

    private final int prof;
    private final List<UUID> ids;

    public RTSProfPacket(int prof, List<UUID> ids) {
        this.prof = prof;
        this.ids = ids;
    }

    public RTSProfPacket(FriendlyByteBuf buf) {
        this.prof = buf.readInt();
        this.ids = new ArrayList<>();

        int size = buf.readInt();

        for (int i = 0; i < size; i++) {
            this.ids.add(buf.readUUID());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(prof);
        buf.writeInt(ids.size());

        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            // getSender() is null only on the receiving client; on the server
            // it is the player who sent the packet. We must NOT use
            // FMLEnvironment.dist here: on an integrated (singleplayer) server
            // the physical dist is CLIENT, so a client->server packet would
            // take this client branch and the profession would never actually
            // be assigned (no skin change, no farmer task).
            if (player == null) {
                for (UUID id : ids) {
                    RTSProfClient.set(id, prof);
                }

                return;
            }

            ServerLevel level = player.serverLevel();
            RTSProtectedData data = RTSProtectedData.get(level.getServer());
            UUID village = RTSTeamManager.villageId(player.getUUID());

            boolean warriorsChanged = false;

            for (UUID id : ids) {
                Entity entity = level.getEntity(id);

                if (!(entity instanceof Villager villager)) {
                    continue;
                }

                if (prof >= 4) {
                    // 4 = warrior (Vindicator), 5 = crossbowman (Pillager),
                    // 6 = summoner (Evoker). The villager is replaced by the
                    // matching combat mob at the same spot.
                    UUID unitId = RTSVillagerTaskManager.convertToCombatant(level, villager, village, prof);

                    if (unitId != null) {
                        data.removeProf(id);
                        data.setProf(unitId, prof);
                        warriorsChanged = true;
                    }

                    continue;
                }

                data.setProf(id, prof);
                // Remember which village owns this villager so a farm task can
                // be re-created after a reload (tags persist in entity NBT).
                RTSVillagerTags.markOwner(villager, village);
                RTSVillagerTaskManager.applyProfessionSkin(villager, prof);

                if (prof == 1) {
                    // Tool tier follows the mine's upgrade level (stone/iron/diamond).
                    villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                            RTSVillagerTaskManager.minerTool(RTSVillagerTaskManager.upgradeLevel(data, "mine"))));
                    villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE_SHOVEL));
                } else if (prof == 2) {
                    villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                            RTSVillagerTaskManager.lumberTool(RTSVillagerTaskManager.upgradeLevel(data, "lumber_camp"))));
                } else if (prof == 3) {
                    villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_HOE));
                    RTSVillagerTaskManager.setTask(villager, 7, null, village);
                }
            }

            if (warriorsChanged) {
                // Warriors get brand new UUIDs, so the client's owned set has
                // to be refreshed for them to be recognised.
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.ALL.noArg(),
                        new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
                );
            }

            for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> target),
                        new RTSProfPacket(prof, ids)
                );
            }
        });

        ctx.get().setPacketHandled(true);
    }
}