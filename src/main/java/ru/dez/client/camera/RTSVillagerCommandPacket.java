package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.network.NetworkEvent;

public class RTSVillagerCommandPacket {

    private final int command;
    private final BlockPos pos;
    private final List<UUID> ids;

    public RTSVillagerCommandPacket(int command, BlockPos pos, List<UUID> ids) {
        this.command = command;
        this.pos = pos;
        this.ids = ids;
    }

    public RTSVillagerCommandPacket(FriendlyByteBuf buf) {
        this.command = buf.readByte();
        this.pos = buf.readBoolean() ? buf.readBlockPos() : null;

        int size = buf.readInt();
        this.ids = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            this.ids.add(buf.readUUID());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(command);
        buf.writeBoolean(pos != null);

        if (pos != null) {
            buf.writeBlockPos(pos);
        }

        buf.writeInt(ids.size());

        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            ServerLevel level = player.serverLevel();
            UUID village = RTSTeamManager.villageId(player.getUUID());

            for (UUID id : ids) {
                Entity entity = level.getEntity(id);

                if (entity instanceof Villager villager) {
                    // Command 8 is the warrior-only "defend" order.
                    if (command != 8) {
                        RTSVillagerTaskManager.setTask(villager, command, pos, village);
                    }
                } else if (entity instanceof Monster warrior && RTSVillagerTags.isWarrior(warrior)) {
                    if (command == 0 && pos != null) {
                        // Move order: it overrides automatic combat until the
                        // warrior actually reaches the destination.
                        RTSVillagerTaskManager.orderWarriorMove(warrior, pos);
                    } else if (command == 8) {
                        // "Защита": drop any move order and resume combat.
                        RTSVillagerTaskManager.clearWarriorOrder(warrior.getUUID());
                        // "Защита": re-apply the combat AI.
                        int prof = RTSProtectedData.get(level.getServer()).getProf(warrior.getUUID());
                        RTSVillagerTaskManager.configureCombatant(warrior, prof);
                    }
                }
            }
        });

        ctx.get().setPacketHandled(true);
    }
}