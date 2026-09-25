package ru.dez.client.camera;

import java.util.ArrayList;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "blocks_blades")
public class RTSCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("rts")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(RTSCommands::unlock)))
                        .then(Commands.literal("protect")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(RTSCommands::protect))))))
                        // Test helpers: hand out resources / set the town hall tier.
                        .then(Commands.literal("give")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(RTSCommands::give)))
                        .then(Commands.literal("resources")
                                .then(Commands.argument("wood", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("food", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("ore", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("happiness", IntegerArgumentType.integer(0))
                                                                .executes(RTSCommands::setResources))))))
                        .then(Commands.literal("townhall")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                        .executes(RTSCommands::setTownHall)))
                        // Finishes every construction site that is already placed.
                        .then(Commands.literal("buildall")
                                .executes(RTSCommands::finishAll))
                        .then(Commands.literal("finish")
                                .executes(RTSCommands::finishAll))
                        // Spawns a fresh grid with every schematic (debug helper).
                        .then(Commands.literal("spawnall")
                                .executes(RTSCommands::buildAll))
        );
    }

    /** Instantly completes every currently placed construction site. */
    private static int finishAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int done = 0;

        for (RTSConstructionManager.Site site : RTSConstructionManager.getSites()) {
            ServerLevel level = server.getLevel(site.dim);

            if (level == null) {
                continue;
            }

            RTSConstructionManager.finishInstantly(level, site);
            done++;
        }

        int finished = done;

        ctx.getSource().sendSuccess(() -> Component.literal("Мгновенно достроено строек: " + finished), false);
        return 1;
    }

    /**
     * Instantly places every schematic in the mod in a grid around the player
     * (both normal buildings and the level 2/3 variants) so progression can be
     * inspected without waiting for construction.
     */
    private static int buildAll(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            RTSProtectedData data = RTSProtectedData.get(level.getServer());
            BlockPos base = player.blockPosition();

            int spacing = 20;
            int cols = 6;
            int index = 0;

            for (RTSBuilding building : RTSBuildings.BUILDINGS) {
                if (building.schematic == null) {
                    continue;
                }

                int col = index % cols;
                int row = index / cols;
                BlockPos pos = base.offset(col * spacing, 0, (row + 1) * spacing);
                index++;

                RTSBuildings.place(level, building.id, pos);
                data.addArea(level.dimension(), building.schematic, pos);
                data.addPlacement(building.id, level.dimension().location().toString(), pos,
                        RTSTeamManager.villageId(player.getUUID()).toString());
                data.incrementBuilt(building.id);
                data.addCompleted(building.id);
                RTSBuildings.markCompleted(building.id);

                if (building.id.equals("farm")) {
                    data.addFarmZone(level.dimension(), building.schematic, pos, RTSTeamManager.villageId(player.getUUID()));
                }
            }

            data.townHallLevel = 3;
            data.setDirty();

            for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> target),
                        new RTSUpgradeStatePacket(data.townHallLevel)
                );
                RTSNetwork.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> target),
                        new RTSUnlockPacket(new ArrayList<>(data.completed))
                );
            }

            int built = index;

            ctx.getSource().sendSuccess(() -> Component.literal("Построено схем: " + built), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Команду нужно выполнять от имени игрока"));
            return 0;
        }
    }

    private static int give(CommandContext<CommandSourceStack> ctx) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            RTSResourceManager.add(RTSTeamManager.villageId(player.getUUID()), amount, amount, amount, amount);
            RTSResourceManager.sync(player);

            ctx.getSource().sendSuccess(() -> Component.literal("Выдано по " + amount + " дерева/еды/руды/счастья"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Команду нужно выполнять от имени игрока"));
            return 0;
        }
    }

    private static int setResources(CommandContext<CommandSourceStack> ctx) {
        int wood = IntegerArgumentType.getInteger(ctx, "wood");
        int food = IntegerArgumentType.getInteger(ctx, "food");
        int ore = IntegerArgumentType.getInteger(ctx, "ore");
        int happiness = IntegerArgumentType.getInteger(ctx, "happiness");

        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            MinecraftServer server = ctx.getSource().getServer();

            java.util.UUID village = RTSTeamManager.villageId(player.getUUID());
            RTSProtectedData.get(server).setResources(village, wood, food, ore, happiness);
            RTSResourceManager.restore(server, village);
            RTSResourceManager.sync(player);

            ctx.getSource().sendSuccess(() -> Component.literal("Ресурсы установлены: " + wood + "/" + food + "/" + ore + "/" + happiness), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Команду нужно выполнять от имени игрока"));
            return 0;
        }
    }

    private static int setTownHall(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        MinecraftServer server = ctx.getSource().getServer();
        RTSProtectedData data = RTSProtectedData.get(server);

        data.townHallLevel = level;

        if (level >= 1) {
            data.addCompleted("town_hall");
        }
        if (level >= 2) {
            data.addCompleted("town_hall_2");
        }
        if (level >= 3) {
            data.addCompleted("town_hall_3");
        }

        data.setDirty();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSUpgradeStatePacket(level)
            );
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSUnlockPacket(new ArrayList<>(data.completed))
            );
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Уровень мэрии установлен: " + level), false);
        return 1;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();

        RTSProtectedData.get(server).addCompleted(id);
        RTSBuildings.markCompleted(id);

        ctx.getSource().sendSuccess(() -> Component.literal("Открыто: " + id), false);

        return 1;
    }

    private static int protect(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        MinecraftServer server = ctx.getSource().getServer();
        RTSBuilding building = RTSBuildings.get(id);

        if (building == null || building.schematic == null) {
            ctx.getSource().sendFailure(Component.literal("Неизвестная постройка: " + id));
            return 0;
        }

        RTSProtectedData data = RTSProtectedData.get(server);
        data.addCompleted(id);
        data.addArea(server.overworld().dimension(), building.schematic, new BlockPos(x, y, z));

        RTSBuildings.markCompleted(id);

        ctx.getSource().sendSuccess(() -> Component.literal("Постройка защищена и открыта: " + id), false);

        return 1;
    }
}   