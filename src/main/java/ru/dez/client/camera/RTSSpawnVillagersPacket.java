package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import ru.dez.entity.RTSNpcRegistry;
import net.minecraftforge.network.PacketDistributor;

public class RTSSpawnVillagersPacket {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;

    public RTSSpawnVillagersPacket(double x, double y, double z, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public RTSSpawnVillagersPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            ServerLevel level = player.serverLevel();
            RandomSource random = level.random;
            RTSProtectedData data = RTSProtectedData.get(level.getServer());
            java.util.UUID village = RTSTeamManager.villageId(player.getUUID());

            // Respect the village's housing capacity instead of always spawning
            // three villagers: 3 base slots plus 3 per completed house.
            int capacity = RTSVillagerTaskManager.getPopulationCap(data);
            int current = RTSVillagerTaskManager.countOwnedUnits(level.getServer(), data);
            int toSpawn = Math.max(0, Math.min(3, capacity - current));

            if (toSpawn <= 0) {
                player.sendSystemMessage(Component.literal("Нет места для новых жителей — постройте дома"));
                return;
            }

            double rad = Math.toRadians(yaw);
            Vec3 left = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
            double[] offsets = new double[]{-2.0, 0.0, 2.0};

            VillagerProfession[] professions = new VillagerProfession[]{
                    VillagerProfession.FARMER,
                    VillagerProfession.LIBRARIAN,
                    VillagerProfession.ARMORER,
                    VillagerProfession.WEAPONSMITH,
                    VillagerProfession.TOOLSMITH,
                    VillagerProfession.CLERIC,
                    VillagerProfession.BUTCHER,
                    VillagerProfession.LEATHERWORKER,
                    VillagerProfession.MASON,
                    VillagerProfession.SHEPHERD,
                    VillagerProfession.CARTOGRAPHER,
                    VillagerProfession.NITWIT
            };

            String[] names = new String[]{
                    "Иван",
                    "Пётр",
                    "Марк",
                    "Олег",
                    "Дмитрий",
                    "Алексей",
                    "Никита",
                    "Максим",
                    "Артём",
                    "Сергей"
            };

            for (int i = 0; i < toSpawn; i++) {
                Vec3 p = new Vec3(x, y, z).add(left.scale(offsets[i]));
                BlockPos pos = BlockPos.containing(p.x, p.y, p.z);

                if (!level.isEmptyBlock(pos)) {
                    pos = pos.above();
                }

                Villager villager = RTSNpcRegistry.create(level);

                VillagerProfession profession = professions[random.nextInt(professions.length)];

                RTSVillagerTaskManager.setProfession(villager, profession);
                villager.setCustomName(Component.literal(names[random.nextInt(names.length)] + " " + (i + 1)));
                villager.setCustomNameVisible(true);
                villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

                villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
                villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WOODEN_AXE));

                // Mark the villager as owned by the RTS system so it can be
                // told apart from vanilla villagers (selection + profession).
                RTSVillagerTags.markOwned(villager);
                RTSVillagerTags.markOwner(villager, village);

                level.addFreshEntity(villager);

                // Record the villager in the persistent owned set so the
                // client can be told which villagers it may select.
                data.setProf(villager.getUUID(), 0);
            }

            // Push the updated owned-villager set to every player so their
            // selection logic knows about the newly spawned villagers.
            RTSNetwork.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
            );
        });

        ctx.get().setPacketHandled(true);
    }
}