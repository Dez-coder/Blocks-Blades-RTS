package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side village/team registry.
 *
 * <p>A "village" is identified by the UUID of the player that founded it (the
 * leader). Every player that has no explicit team belongs to their own village
 * (their own UUID), so single-player behaviour is unchanged. When a player is
 * invited and accepts, their UUID is mapped to the leader's UUID and from then
 * on every ownership-keyed system (resources, orders, buildings, professions)
 * treats both players as the same village.
 *
 * <p>Teams can also declare war on each other; {@link #areAtWar(UUID, UUID)}
 * is consulted by the combat code so warriors attack enemy units and buildings.
 */
public final class RTSTeamManager {

    private RTSTeamManager() {
    }

    /** Village id (leader UUID) the player currently belongs to. */
    public static UUID villageId(UUID player) {
        MinecraftServer server = currentServer;

        if (server != null && player != null) {
            String team = RTSProtectedData.get(server).playerTeam.get(player);

            if (team != null) {
                try {
                    return UUID.fromString(team);
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the player's own village.
                }
            }
        }

        return player;
    }

    public static UUID villageId(ServerPlayer player) {
        return player == null ? null : villageId(player.getUUID());
    }

    /** Every UUID that belongs to the given village, leader first. */
    public static List<UUID> membersOf(MinecraftServer server, UUID villageId) {
        List<UUID> ids = new ArrayList<>();

        if (villageId == null) {
            return ids;
        }

        ids.add(villageId);

        for (Map.Entry<UUID, String> entry : RTSProtectedData.get(server).playerTeam.entrySet()) {
            if (entry.getValue().equals(villageId.toString())) {
                ids.add(entry.getKey());
            }
        }

        return ids;
    }

    public static boolean isSameTeam(UUID a, UUID b) {
        if (a == null || b == null) {
            return false;
        }

        return villageId(a).equals(villageId(b));
    }

    // === Invites ===

    /** Invites {@code target} into the inviter's village. */
    public static boolean invite(ServerPlayer inviter, ServerPlayer target) {
        if (inviter == null || target == null) {
            return false;
        }

        if (isSameTeam(inviter.getUUID(), target.getUUID())) {
            inviter.sendSystemMessage(Component.literal(target.getScoreboardName() + " уже в твоей деревне"));
            return false;
        }

        RTSProtectedData data = RTSProtectedData.get(inviter.getServer());
        data.invites.put(target.getUUID(), villageId(inviter).toString());
        data.setDirty();

        target.sendSystemMessage(Component.literal(inviter.getScoreboardName() + " приглашает тебя в свою деревню"));
        inviter.sendSystemMessage(Component.literal("Приглашение отправлено: " + target.getScoreboardName()));

        broadcast(inviter.getServer());
        return true;
    }

    /** Accepts the pending invite of the player, joining the inviting village. */
    public static boolean accept(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        RTSProtectedData data = RTSProtectedData.get(player.getServer());
        String team = data.invites.remove(player.getUUID());

        if (team == null) {
            player.sendSystemMessage(Component.literal("Нет активных приглашений"));
            return false;
        }

        data.playerTeam.put(player.getUUID(), team);
        data.setDirty();

        player.sendSystemMessage(Component.literal("Ты присоединился к деревне!"));
        broadcast(player.getServer());
        return true;
    }

    public static void decline(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RTSProtectedData data = RTSProtectedData.get(player.getServer());

        if (data.invites.remove(player.getUUID()) != null) {
            data.setDirty();
            broadcast(player.getServer());
        }
    }

    /**
     * Leaves the current village. If the player is the leader, every member is
     * detached back to their own village so no one points at an absent leader.
     */
    public static void leave(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RTSProtectedData data = RTSProtectedData.get(player.getServer());
        String own = player.getUUID().toString();

        if (data.playerTeam.remove(player.getUUID()) != null) {
            data.setDirty();
            player.sendSystemMessage(Component.literal("Ты покинул деревню"));
            broadcast(player.getServer());
            return;
        }

        // Player is a leader (or solo): detach everyone pointing at them.
        boolean changed = data.playerTeam.entrySet().removeIf(entry -> entry.getValue().equals(own));

        if (changed) {
            data.setDirty();
            player.sendSystemMessage(Component.literal("Твоя деревня распущена"));
            broadcast(player.getServer());
        }
    }

    // === Wars ===

    /** Declares war between the two players' villages (no-op if same team). */
    public static void declareWar(ServerPlayer player, ServerPlayer target) {
        if (player == null || target == null) {
            return;
        }

        UUID a = villageId(player);
        UUID b = villageId(target);

        if (a.equals(b)) {
            player.sendSystemMessage(Component.literal("Нельзя объявить войну своей деревне"));
            return;
        }

        RTSProtectedData data = RTSProtectedData.get(player.getServer());
        String pair = warPair(a, b);

        if (data.wars.contains(pair)) {
            data.wars.remove(pair);
            player.sendSystemMessage(Component.literal("Мир с деревней " + target.getScoreboardName()));
            target.sendSystemMessage(Component.literal("Ваша деревня заключила мир"));
        } else {
            data.wars.add(pair);
            player.sendSystemMessage(Component.literal("Война объявлена деревне " + target.getScoreboardName()));
            target.sendSystemMessage(Component.literal("Вам объявили войну!"));
        }

        data.setDirty();
        broadcast(player.getServer());
    }

    public static boolean areAtWar(UUID villageA, UUID villageB) {
        if (villageA == null || villageB == null || villageA.equals(villageB)) {
            return false;
        }

        if (currentServer == null) {
            return false;
        }

        return RTSProtectedData.get(currentServer).wars.contains(warPair(villageA, villageB));
    }

    /** Villages currently at war with the given village. */
    public static List<String> warsWith(MinecraftServer server, UUID villageId) {
        List<String> result = new ArrayList<>();

        if (villageId == null || server == null) {
            return result;
        }

        String key = villageId.toString();

        for (String pair : RTSProtectedData.get(server).wars) {
            String[] parts = pair.split("\\|");

            if (parts.length != 2) {
                continue;
            }

            if (parts[0].equals(key)) {
                result.add(parts[1]);
            } else if (parts[1].equals(key)) {
                result.add(parts[0]);
            }
        }

        return result;
    }

    private static String warPair(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();

        return sa.compareTo(sb) <= 0 ? sa + "|" + sb : sb + "|" + sa;
    }

    // === Sync ===

    /** Sends each online player their own team snapshot. */
    public static void broadcast(MinecraftServer server) {
        setServer(server);

        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendState(server, player);
        }
    }

    public static void sendState(MinecraftServer server, ServerPlayer player) {
        setServer(server);

        RTSProtectedData data = RTSProtectedData.get(server);
        UUID village = villageId(player.getUUID());
        List<UUID> memberIds = membersOf(server, village);
        List<String> memberNames = new ArrayList<>();

        for (UUID id : memberIds) {
            memberNames.add(nameOf(server, id));
        }

        List<UUID> onlineIds = new ArrayList<>();
        List<String> onlineNames = new ArrayList<>();
        List<String> onlineVillages = new ArrayList<>();

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            onlineIds.add(other.getUUID());
            onlineNames.add(other.getScoreboardName());
            onlineVillages.add(villageId(other.getUUID()).toString());
        }

        String inviteTeam = data.invites.get(player.getUUID());
        String inviteName = inviteTeam == null ? "" : nameOf(server, parse(inviteTeam));

        RTSNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RTSTeamStatePacket(
                        village.toString(),
                        nameOf(server, village),
                        memberIds,
                        memberNames,
                        onlineIds,
                        onlineNames,
                        onlineVillages,
                        inviteTeam == null ? "" : inviteTeam,
                        inviteName,
                        warsWith(server, village)
                )
        );
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        if (id == null) {
            return "?";
        }

        ServerPlayer online = server.getPlayerList().getPlayer(id);

        if (online != null) {
            return online.getScoreboardName();
        }

        return id.toString().substring(0, 8);
    }

    // The server instance is cached so the many static helpers that do not take
    // a MinecraftServer can still resolve team membership (e.g. combat code).
    private static MinecraftServer currentServer;

    public static void setServer(MinecraftServer server) {
        currentServer = server;
    }

    public static MinecraftServer getServer() {
        return currentServer;
    }
}
