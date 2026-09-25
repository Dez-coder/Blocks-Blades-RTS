package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "blocks_blades")
public class RTSServerEvents {

    private static int teamSyncTimer = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        RTSTeamManager.setServer(event.getServer());
        RTSVillagerTaskManager.tick(event.getServer());

        // Keep every client's team/invite panel and building list up to date.
        if (++teamSyncTimer >= 100) {
            teamSyncTimer = 0;
            RTSTeamManager.broadcast(event.getServer());
            RTSPlacementsManager.broadcast(event.getServer());
        }
    }

    /**
     * Re-applies the saved state of owned entities when they are (re)loaded
     * from disk. Chunks unload and reload constantly, so restoring here - rather
     * than only when a player logs in - is what keeps villager professions,
     * standing orders and warrior AI alive after a world reload.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Villager villager) {
            RTSVillagerTaskManager.restoreVillagerState(villager);
        } else if (event.getEntity() instanceof Monster warrior && RTSVillagerTags.isWarrior(warrior)) {
            RTSProtectedData data = RTSProtectedData.get(event.getLevel().getServer());
            RTSVillagerTaskManager.configureCombatant(warrior, data.getProf(warrior.getUUID()));
        }
    }

    /** Restores position/visibility if the player leaves while in RTS mode. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RTSCameraMode.exit(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RTSTeamManager.setServer(player.getServer());
            RTSProtectedData data = RTSProtectedData.get(player.getServer());

            RTSBuildings.setCompleted(data.completed);

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSUnlockPacket(new ArrayList<>(data.completed))
            );

            // Push the saved professions + levels so the HUD shows every
            // villager's job again right after re-entering the world.
            Map<UUID, Integer> levels = new HashMap<>();

            for (Map.Entry<UUID, Integer> entry : data.xp.entrySet()) {
                levels.put(entry.getKey(), RTSVillagerTaskManager.villagerLevel(entry.getValue()));
            }

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSProfSyncPacket(new HashMap<>(data.profs), levels)
            );

            // Restore the town hall tier so the upgrade button is right.
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSUpgradeStatePacket(data.townHallLevel)
            );

            // Restore the player's wood/food/ore/happiness from the world
            // save and push it to the client, so re-joining an existing
            // world doesn't reset the village's stockpile back to zero.
            RTSResourceManager.restore(player.getServer(), RTSTeamManager.villageId(player.getUUID()));
            RTSResourceManager.sync(player);

            // Send the player their village, online list and pending invite.
            RTSTeamManager.sendState(player.getServer(), player);

            // Re-send every construction site that was still in progress so
            // the player sees their build markers again instead of the
            // site appearing to have disappeared.
            RTSConstructionManager.resendTo(player);

            // Building list for hover outlines.
            RTSPlacementsManager.sendTo(player.getServer(), player);
        }
    }
}
