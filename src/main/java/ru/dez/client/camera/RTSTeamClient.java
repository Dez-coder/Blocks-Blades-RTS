package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side snapshot of the player's village/team, fed by RTSTeamStatePacket. */
public final class RTSTeamClient {

    public static String villageId = "";
    public static String villageName = "";

    public static final List<UUID> memberIds = new ArrayList<>();
    public static final List<String> memberNames = new ArrayList<>();

    public static final List<UUID> onlineIds = new ArrayList<>();
    public static final List<String> onlineNames = new ArrayList<>();
    public static final List<String> onlineVillages = new ArrayList<>();

    public static String inviteVillageId = "";
    public static String inviteVillageName = "";

    public static final List<String> warsWith = new ArrayList<>();

    private RTSTeamClient() {
    }

    public static void set(String villageId, String villageName,
                           List<UUID> memberIds, List<String> memberNames,
                           List<UUID> onlineIds, List<String> onlineNames, List<String> onlineVillages,
                           String inviteVillageId, String inviteVillageName,
                           List<String> warsWith) {
        RTSTeamClient.villageId = villageId == null ? "" : villageId;
        RTSTeamClient.villageName = villageName == null ? "" : villageName;

        RTSTeamClient.memberIds.clear();
        RTSTeamClient.memberNames.clear();
        RTSTeamClient.onlineIds.clear();
        RTSTeamClient.onlineNames.clear();
        RTSTeamClient.onlineVillages.clear();
        RTSTeamClient.warsWith.clear();

        RTSTeamClient.memberIds.addAll(memberIds);
        RTSTeamClient.memberNames.addAll(memberNames);
        RTSTeamClient.onlineIds.addAll(onlineIds);
        RTSTeamClient.onlineNames.addAll(onlineNames);
        RTSTeamClient.onlineVillages.addAll(onlineVillages);
        RTSTeamClient.warsWith.addAll(warsWith);

        RTSTeamClient.inviteVillageId = inviteVillageId == null ? "" : inviteVillageId;
        RTSTeamClient.inviteVillageName = inviteVillageName == null ? "" : inviteVillageName;
    }

    public static boolean hasInvite() {
        return !inviteVillageId.isEmpty();
    }

    /** True when the given online player is already in the local village. */
    public static boolean isMember(int onlineIndex) {
        if (onlineIndex < 0 || onlineIndex >= onlineVillages.size()) {
            return false;
        }

        return onlineVillages.get(onlineIndex).equals(villageId);
    }

    public static void reset() {
        set("", "", new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "", "", new ArrayList<>());
    }
}
