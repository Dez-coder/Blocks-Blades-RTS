package ru.dez.client.camera;

import java.util.ArrayList;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.PacketDistributor;

/**
 * Handles the instant town hall upgrade.
 *
 * Unlike normal buildings this never creates a construction site: the old town
 * hall structure is removed and the next tier is placed in the same spot, and
 * every built house is upgraded to the matching tier at the same time.
 */
public final class RTSVillageUpgradeManager {

    private RTSVillageUpgradeManager() {
    }

    public static void upgradeTownHall(ServerLevel level, ServerPlayer player) {
        RTSProtectedData data = RTSProtectedData.get(level.getServer());
        int current = data.townHallLevel;

        if (current < 1) {
            player.sendSystemMessage(Component.literal("Сначала постройте мэрию"));
            return;
        }

        if (current >= 3) {
            player.sendSystemMessage(Component.literal("Мэрия уже максимального уровня"));
            return;
        }

        int target = current + 1;
        RTSBuilding upgrade = RTSBuildings.get("town_hall_" + target);

        if (upgrade == null) {
            return;
        }

        java.util.UUID village = RTSTeamManager.villageId(player.getUUID());

        if (!RTSResourceManager.canAfford(village, upgrade.costWood, upgrade.costFood, upgrade.costOre)) {
            player.sendSystemMessage(Component.literal("Не хватает ресурсов для улучшения мэрии"));
            RTSResourceManager.sync(player);
            return;
        }

        RTSResourceManager.deduct(village, upgrade.costWood, upgrade.costFood, upgrade.costOre);
        RTSResourceManager.sync(player);

        String dim = level.dimension().location().toString();

        // Replace the town hall itself.
        String oldHallId = current == 1 ? "town_hall" : "town_hall_" + current;
        RTSProtectedData.PlacedBuilding hall = data.getLastPlacement(oldHallId, dim);

        if (hall != null) {
            replaceStructure(level, data, hall, oldHallId, "town_hall_" + target);
        }

        // Instantly upgrade every built house (no preview, no construction).
        for (RTSProtectedData.PlacedBuilding placed : new ArrayList<>(data.placements)) {
            String family = RTSBuildings.houseFamily(placed.id);

            if (family == null) {
                continue;
            }

            replaceStructure(level, data, placed, placed.id, family + "_" + target);
        }

        data.townHallLevel = target;
        data.setDirty();

        data.addCompleted("town_hall_" + target);
        RTSBuildings.markCompleted("town_hall_" + target);

        for (ServerPlayer target2 : level.getServer().getPlayerList().getPlayers()) {
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> target2),
                    new RTSUpgradeStatePacket(target)
            );
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> target2),
                    new RTSUnlockPacket(new ArrayList<>(data.completed))
            );
        }

        player.sendSystemMessage(Component.literal("Мэрия улучшена до уровня " + target + ". Все дома обновлены!"));
    }

    private static void replaceStructure(ServerLevel level, RTSProtectedData data,
                                         RTSProtectedData.PlacedBuilding placed, String oldId, String newId) {
        RTSBuilding oldBuilding = RTSBuildings.get(oldId);
        RTSBuilding newBuilding = RTSBuildings.get(newId);

        if (oldBuilding != null && oldBuilding.schematic != null) {
            for (RTSSchematic.Entry entry : oldBuilding.schematic.entries) {
                level.setBlockAndUpdate(placed.pos().offset(entry.x, entry.y, entry.z), Blocks.AIR.defaultBlockState());
            }
        }

        if (newBuilding != null && newBuilding.schematic != null) {
            RTSBuildings.place(level, newId, placed.pos());
        }

        data.replacePlacement(placed, newId);
    }
}
