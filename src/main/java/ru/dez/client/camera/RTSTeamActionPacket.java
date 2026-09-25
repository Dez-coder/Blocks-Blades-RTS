package ru.dez.client.camera;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server team action. Supported actions:
 * "invite", "accept", "decline", "leave", "war".
 */
public class RTSTeamActionPacket {

    private final String action;
    private final UUID target;

    public RTSTeamActionPacket(String action, UUID target) {
        this.action = action;
        this.target = target;
    }

    public RTSTeamActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(32);
        this.target = buf.readBoolean() ? buf.readUUID() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action);
        buf.writeBoolean(target != null);

        if (target != null) {
            buf.writeUUID(target);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            RTSTeamManager.setServer(player.getServer());

            switch (action) {
                case "invite" -> {
                    if (target != null) {
                        ServerPlayer invited = player.getServer().getPlayerList().getPlayer(target);

                        if (invited != null) {
                            RTSTeamManager.invite(player, invited);
                        }
                    }
                }
                case "accept" -> RTSTeamManager.accept(player);
                case "decline" -> RTSTeamManager.decline(player);
                case "leave" -> RTSTeamManager.leave(player);
                case "war" -> {
                    if (target != null) {
                        ServerPlayer enemy = player.getServer().getPlayerList().getPlayer(target);

                        if (enemy != null) {
                            RTSTeamManager.declareWar(player, enemy);
                        }
                    }
                }
                default -> {
                }
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
