package ru.dez.client.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import ru.dez.entity.RTSNpc;
import ru.dez.entity.RTSNpcRegistry;

public class RTSVillagerTaskManager {

    private static final double MOVE_SPEED = 0.5D;
    private static final float BUILD_WORK_PER_TICK = 0.1F;
    private static final int BLOCKED_TARGET_TICKS = 600;

    /** A completed farm passively produces this much food every interval. */
    private static final int FARM_INCOME_INTERVAL = 600; // 30 seconds
    private static final int FARM_INCOME_FOOD = 3;

    /** With a town hall standing, a new villager appears every interval. */
    private static final int GROWTH_INTERVAL = 6000; // 5 minutes

    /** Wood granted per chopped log block. */
    public static final int WOOD_PER_BLOCK = 3;

    /** Town-hall hiring: each queued villager costs food and spawns on a timer. */
    public static final int HIRE_COST = 12;
    private static final int HIRE_INTERVAL = 600; // 30 seconds

    private static final String[] GROWTH_NAMES = {
            "Иван", "Пётр", "Марк", "Олег", "Дмитрий",
            "Алексей", "Никита", "Максим", "Артём", "Сергей"
    };

    /** Every building id that counts as housing for the population cap. */
    private static final String[] HOUSE_IDS = {
            "house_small", "house", "house_large", "house_2f", "house_2f_large", "house_manor"
    };

    private static final int NEEDS_INTERVAL = 600; // 30s: eat / happiness
    private static final int STATE_SYNC_INTERVAL = 100; // 5s: xp to clients
    private static int needsTimer = 0;
    private static int stateSyncTimer = 0;

    private static int farmIncomeTimer = 0;
    private static int growthTimer = 0;
    private static int hireTimer = 0;

    private static final Map<UUID, Task> TASKS = new HashMap<>();

    /** Villagers currently hiding inside a shelter for the night. */
    private static final Set<UUID> SHELTERING = new HashSet<>();

    public static void setTask(Villager villager, int type, BlockPos target, UUID owner) {
        if (villager == null) {
            return;
        }

        if ((type == 0 || type == 1 || type == 5) && target == null) {
            return;
        }

        // Type 8 is the warrior-only "defend" order; villagers have no such task.
        if (type == 8) {
            return;
        }

        // A new order always interrupts an ongoing chat.
        if (villager instanceof RTSNpc talkingNpc) {
            talkingNpc.interruptSocialising();
        }

        if (type == 1 && villager.level() instanceof ServerLevel checkLevel && isProtected(checkLevel, target)) {
            ServerPlayer player = checkLevel.getServer().getPlayerList().getPlayer(owner);

            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Нельзя ломать постройку"));
            }

            return;
        }

        villager.goalSelector.removeAllGoals(g -> true);
        villager.targetSelector.removeAllGoals(g -> true);

        if (villager.level() instanceof ServerLevel serverLevel) {
            villager.getBrain().stopAll(serverLevel, villager);
        }

        Task task = new Task();
        task.dim = villager.level().dimension();
        task.type = type;
        task.target = target;
        task.owner = owner;
        task.progress = 0.0F;
        task.searchCooldown = 0;
        task.stuckTicks = 0;
        task.pathTarget = null;
        task.pathRetry = 0;
        task.siteId = -1;
        task.huntTarget = null;
        task.attackCooldown = 0;
        task.farmCooldown = 0;
        task.lastX = villager.getX();
        task.lastZ = villager.getZ();
        task.blockedPos = null;
        task.blockedTimer = 0;

        if (type == 5) {
            RTSConstructionManager.Site site = RTSConstructionManager.getByPos(villager.level().dimension(), target);

            if (site == null) {
                return;
            }

            task.siteId = site.id;
        }

        // Remember the miner's/lumberjack's tool level so the ore filter and the
        // assigned tool match the current upgrade level.
        if (villager.level() instanceof ServerLevel serverLevel) {
            RTSProtectedData data = RTSProtectedData.get(serverLevel.getServer());
            int prof = data.getProf(villager.getUUID());

            if (prof == 1) {
                task.toolLevel = upgradeLevel(data, "mine");
            } else if (prof == 2) {
                task.toolLevel = upgradeLevel(data, "lumber_camp");
            }
        }

        TASKS.put(villager.getUUID(), task);

        // Persist the order so it can be re-created if the villager is unloaded
        // and later re-loaded (chunk reload or world re-entry).
        if (villager.level() instanceof ServerLevel orderLevel) {
            RTSProtectedData.get(orderLevel.getServer()).setOrder(villager.getUUID(), type);
        }

        if (villager.getMainHandItem().isEmpty()) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        }
    }

    public static boolean isControlled(UUID id) {
        return TASKS.containsKey(id);
    }

    /** How long a warrior keeps obeying a move order before giving up (~30 s). */
    private static final int WARRIOR_ORDER_TIMEOUT = 600;

    /**
     * Maximum vertical difference to an enemy. Anything deeper/shallower is
     * ignored, otherwise warriors and Vexes endlessly dive into caves and dig
     * themselves into the ground chasing mobs they cannot properly reach.
     */
    private static final double MAX_TARGET_DY = 4.0D;

    /** Summoned Vex -> the summoner that created it, so it can be kept above ground. */
    private static final Map<UUID, UUID> VEX_SUMMONER = new HashMap<>();

    /** Player-issued movement orders for warriors: UUID -> destination. */
    private static final Map<UUID, BlockPos> WARRIOR_ORDERS = new HashMap<>();
    private static final Map<UUID, Integer> WARRIOR_ORDER_TICKS = new HashMap<>();

    private static int skinSyncCooldown = 0;

    public static void tick(MinecraftServer server) {
        tickPassiveIncome(server);
        tickVillageGrowth(server);
        tickHiring(server);
        tickShelter(server);
        tickWarriors(server);
        tickVexes(server);
        tickNeeds(server);
        tickStateSync(server);

        if (skinSyncCooldown <= 0) {
            syncUncontrolledProfessionSkins(server);
            skinSyncCooldown = 20;
        } else {
            skinSyncCooldown--;
        }

        Iterator<Map.Entry<UUID, Task>> iterator = TASKS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Task> entry = iterator.next();
            Task task = entry.getValue();

            ServerLevel level = server.getLevel(task.dim);

            if (level == null) {
                RTSProtectedData.get(server).removeOrder(entry.getKey());
                iterator.remove();
                continue;
            }

            Entity entity = level.getEntity(entry.getKey());

            if (entity == null) {
                // Chunk is not loaded: drop the in-memory task but keep the
                // saved order so it is restored when the chunk comes back.
                iterator.remove();
                continue;
            }

            if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                RTSProtectedData.get(server).removeOrder(entry.getKey());
                iterator.remove();
                continue;
            }

            syncProfessionSkin(level, villager);
            tickBlacklist(task);

            // Animated NPC: any order makes the villager "busy" (so it never
            // starts chatting mid-task), but the "rejected" work animation is
            // only played while it is actually in reach of the block it breaks.
            if (villager instanceof RTSNpc npc) {
                npc.setWorking(true);

                // Only mining/chopping tasks may play "rejected" - a plain
                // "move here" order must never trigger it just because the
                // villager arrived next to the target block.
                boolean breaking = isMiningTask(task.type) && isWithinReach(villager, task);

                npc.setBreaking(breaking);

                // While breaking a block, look straight at it (body + head).
                if (breaking && task.target != null) {
                    BlockPos look = task.target;
                    npc.lookAtPoint(look.getX() + 0.5D, look.getY() + 0.5D, look.getZ() + 0.5D);
                }
            }

            boolean done;

            if (task.type == 0) {
                done = tickMove(level, villager, task);
            } else if (task.type == 5) {
                done = tickBuild(level, villager, task);
            } else if (task.type == 6) {
                done = tickHunt(level, villager, task);
            } else if (task.type == 7) {
                done = tickFarm(level, villager, task);
            } else {
                done = tickMine(level, villager, task);
            }

            if (done) {
                if (villager instanceof RTSNpc npc) {
                    npc.setWorking(false);
                    npc.setBreaking(false);
                }

                RTSProtectedData.get(server).removeOrder(entry.getKey());
                iterator.remove();
            }
        }
    }

    /**
     * True when the villager is standing close enough that it is actually
     * hitting its target block (not merely walking towards it). Blocks above
     * the villager may be mined from far away, so they get a much larger reach.
     */
    /** Task types that actually break blocks (as opposed to walking/building). */
    private static boolean isMiningTask(int type) {
        return type == 1 || type == 2 || type == 3 || type == 4 || type == 9 || type == 10 || type == 11;
    }

    private static boolean isWithinReach(Villager villager, Task task) {
        BlockPos target = task == null ? null : task.target;

        if (target == null) {
            return false;
        }

        boolean above = target.getY() > villager.blockPosition().getY() + 2;
        double reach = above ? 4096.0D : (task.stuckTicks > 40 ? 100.0D : 36.0D);
        double dx = target.getX() + 0.5D - villager.getX();
        double dy = target.getY() + 0.5D - villager.getY();
        double dz = target.getZ() + 0.5D - villager.getZ();

        return dx * dx + dy * dy + dz * dz <= reach;
    }

    private static boolean tickMove(ServerLevel level, Villager villager, Task task) {
        if (task.target == null) {
            return true;
        }

        BlockPos dest = getDestination(level, task.target);
        Vec3 destVec = new Vec3(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);

        if (villager.distanceToSqr(destVec) < 2.25D) {
            stopMovement(villager);
            return true;
        }

        navigate(level, villager, task, dest);
        updateStuck(villager, task);

        if (task.stuckTicks == 40) {
            task.pathTarget = null;
        }

        if (task.stuckTicks > 160) {
            stopMovement(villager);
            return true;
        }

        return false;
    }

    // === СТРОИТЕЛЬСТВО: ОРИГИНАЛЬНАЯ ЛОГИКА ===
    private static boolean tickBuild(ServerLevel level, Villager villager, Task task) {
        RTSConstructionManager.Site site = RTSConstructionManager.get(task.siteId);

        if (site == null) {
            return true;
        }

        Vec3 center = new Vec3(site.origin.getX() + 0.5, site.origin.getY() + 1.0, site.origin.getZ() + 0.5);

        if (villager.distanceToSqr(center) > 36.0D) {
            navigate(level, villager, task, site.origin);
            updateStuck(villager, task);

            if (task.stuckTicks == 40) {
                task.pathTarget = null;
            }

            if (task.stuckTicks > 240) {
                stopMovement(villager);
                return true;
            }

            return false;
        }

        stopMovement(villager);

        BlockPos current = RTSConstructionManager.getCurrentBlock(site);

        if (current != null) {
            villager.getLookControl().setLookAt(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);
        }

        villager.swing(InteractionHand.MAIN_HAND);
        RTSConstructionManager.addWork(level, site, BUILD_WORK_PER_TICK * workSpeedFor(task.owner));

        if (villager.tickCount % 20 == 0) {
            grantXp(villager, 1);
        }

        return false;
    }

    private static boolean tickHunt(ServerLevel level, Villager villager, Task task) {
        Animal animal = null;

        if (task.huntTarget != null) {
            Entity entity = level.getEntity(task.huntTarget);

            if (entity instanceof Animal alive && alive.isAlive()) {
                animal = alive;
            } else {
                task.huntTarget = null;
            }
        }

        if (animal == null) {
            AABB box = villager.getBoundingBox().inflate(24.0, 16.0, 24.0);
            List<Animal> list = level.getEntitiesOfClass(Animal.class, box);

            double best = Double.MAX_VALUE;

            for (Animal candidate : list) {
                if (!candidate.isAlive()) {
                    continue;
                }

                double dist = candidate.distanceToSqr(villager);

                if (dist < best) {
                    best = dist;
                    animal = candidate;
                }
            }

            if (animal != null) {
                task.huntTarget = animal.getUUID();
            }
        }

        if (animal == null) {
            stopMovement(villager);
            return false;
        }

        if (villager.distanceToSqr(animal.position()) > 6.25D) {
            navigate(level, villager, task, animal.blockPosition());
            updateStuck(villager, task);

            if (task.stuckTicks == 40) {
                task.pathTarget = null;
            }

            if (task.stuckTicks > 200) {
                task.huntTarget = null;
                task.stuckTicks = 0;
                task.pathTarget = null;
            }

            return false;
        }

        stopMovement(villager);
        villager.getLookControl().setLookAt(animal);

        if (task.attackCooldown > 0) {
            task.attackCooldown--;
            return false;
        }

        task.attackCooldown = 20;
        villager.swing(InteractionHand.MAIN_HAND);
        playAnim(villager, "attack");
        animal.hurt(level.damageSources().mobAttack(villager), 3.0F);

        if (!animal.isAlive()) {
            grantXp(villager, 2);
            RTSResourceManager.add(task.owner, 0, 3, 0, 0);

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(task.owner);

            if (player != null) {
                RTSResourceManager.sync(player);
            }

            task.huntTarget = null;
        }

        return false;
    }

    private static boolean tickFarm(ServerLevel level, Villager villager, Task task) {
        RTSProtectedData data = RTSProtectedData.get(level.getServer());

        if (task.farmCooldown > 0) {
            task.farmCooldown--;
        }

        if (task.target == null || !isFarmSpot(level, task.target) || isBlacklisted(task, task.target)) {
            task.target = null;

            if (task.farmCooldown <= 0) {
                task.target = findFarmSpot(level, data, task.blockedPos);
                task.farmCooldown = 20;
            }

            if (task.target == null) {
                stopMovement(villager);
                return false;
            }
        }

        BlockPos target = task.target;
        Vec3 center = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (villager.distanceToSqr(center) > 16.0D) {
            navigate(level, villager, task, target);
            updateStuck(villager, task);

            if (task.stuckTicks == 40) {
                task.pathTarget = null;
            }

            if (task.stuckTicks > 200) {
                blacklistTarget(task, target);
                task.target = null;
                task.stuckTicks = 0;
                task.pathTarget = null;
            }

            return false;
        }

        stopMovement(villager);
        villager.getLookControl().setLookAt(center.x, center.y, center.z);

        if (task.attackCooldown > 0) {
            task.attackCooldown--;
            return false;
        }

        task.attackCooldown = 15;
        villager.swing(InteractionHand.MAIN_HAND);

        BlockState state = level.getBlockState(target);

        if (state.getBlock() == Blocks.WHEAT && state.getValue(BlockStateProperties.AGE_7) >= 7) {
            level.setBlockAndUpdate(target, Blocks.WHEAT.defaultBlockState());
            grantXp(villager, 1);

            RTSResourceManager.add(task.owner, 0, 1, 0, 0);

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(task.owner);

            if (player != null) {
                RTSResourceManager.sync(player);
            }
        } else if (state.isAir() && level.getBlockState(target.below()).getBlock() == Blocks.FARMLAND) {
            level.setBlockAndUpdate(target, Blocks.WHEAT.defaultBlockState());
        }

        task.target = null;

        return false;
    }

    private static boolean isFarmSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() == Blocks.WHEAT) {
            return state.getValue(BlockStateProperties.AGE_7) >= 7;
        }

        if (state.isAir()) {
            return level.getBlockState(pos.below()).getBlock() == Blocks.FARMLAND;
        }

        return false;
    }

    private static BlockPos findFarmSpot(ServerLevel level, RTSProtectedData data, BlockPos avoid) {
        String dimName = level.dimension().location().toString();

        for (RTSProtectedData.Area zone : data.farmZones) {
            if (!zone.dim.equals(dimName)) {
                continue;
            }

            BlockPos mature = scanZone(level, zone, true, avoid);

            if (mature != null) {
                return mature;
            }
        }

        for (RTSProtectedData.Area zone : data.farmZones) {
            if (!zone.dim.equals(dimName)) {
                continue;
            }

            BlockPos empty = scanZone(level, zone, false, avoid);

            if (empty != null) {
                return empty;
            }
        }

        return null;
    }

    private static BlockPos scanZone(ServerLevel level, RTSProtectedData.Area zone, boolean wantMature, BlockPos avoid) {
        for (int y = zone.minY; y <= zone.maxY + 1; y++) {
            for (int x = zone.minX; x <= zone.maxX; x++) {
                for (int z = zone.minZ; z <= zone.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (avoid != null && pos.equals(avoid)) {
                        continue;
                    }

                    if (!isFarmSpot(level, pos)) {
                        continue;
                    }

                    boolean mature = level.getBlockState(pos).getBlock() == Blocks.WHEAT;

                    if (mature == wantMature) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean tickMine(ServerLevel level, Villager villager, Task task) {
        if (task.type >= 2) {
            if (task.target == null || !isAutoTarget(level, task.type, task.target, task.toolLevel)) {
                task.target = null;

                if (task.searchCooldown <= 0) {
                    task.target = findTarget(level, villager.blockPosition(), task.type, task.blockedPos, task.toolLevel);
                    task.searchCooldown = 40;
                } else {
                    task.searchCooldown--;
                }

                if (task.target == null) {
                    return false;
                }
            }
        } else {
            if (task.target == null || !isValidTarget(level, task.type, task.target, task.toolLevel)) {
                BlockPos replacement = task.target != null
                        ? findNearbyReplacement(level, task.target, task.type, task.blockedPos, task.toolLevel)
                        : null;

                if (replacement == null) {
                    return true;
                }

                task.target = replacement;
                task.progress = 0.0F;
                task.pathTarget = null;
                task.stuckTicks = 0;
            }
        }

        BlockPos target = task.target;
        BlockState state = level.getBlockState(target);

        if (state.isAir() || state.getDestroySpeed(level, target) < 0.0F) {
            return task.type == 1;
        }

        // === РУБКА ДЕРЕВЬЕВ (type 4): отдельная логика с опорной точкой ===
        if (task.type == 4) {
            return tickMineTree(level, villager, task, target, state);
        }

        // === ШАХТЫ / РУЧНАЯ ДОБЫЧА (type 1, 2, 3): оригинальная логика ===
        Vec3 center = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        // Blocks above the villager (walls, ceilings, high stone) may be mined
        // from far away, so a villager never gets stuck unable to path up to them.
        boolean above = target.getY() > villager.blockPosition().getY() + 2;
        // On the surface the villager often cannot path right up to the block
        // (slopes, cliffs, walls), so it works from a comfortable distance and,
        // once it has been stuck for a moment, from even farther away - instead
        // of blacklisting the block and giving up (which looked like "he only
        // digs when he is already in a hole").
        double reachSq = above ? 1024.0D : (task.stuckTicks > 40 ? 100.0D : 36.0D);

        if (villager.distanceToSqr(center) > reachSq) {
            navigate(level, villager, task, target);
            task.progress = 0.0F;
            updateStuck(villager, task);

            if (task.stuckTicks == 40) {
                task.pathTarget = null;
            }

            if (task.stuckTicks > 200) {
                blacklistTarget(task, target);

                if (task.type >= 2) {
                    task.target = null;
                    task.stuckTicks = 0;
                    task.pathTarget = null;
                    return false;
                }

                BlockPos replacement = findNearbyReplacement(level, target, task.type, task.blockedPos, task.toolLevel);

                if (replacement == null) {
                    stopMovement(villager);
                    return true;
                }

                task.target = replacement;
                task.progress = 0.0F;
                task.stuckTicks = 0;
                task.pathTarget = null;
                return false;
            }

            return false;
        }

        stopMovement(villager);
        assignTool(level, villager, state);
        villager.getLookControl().setLookAt(center.x, center.y, center.z);
        villager.swing(InteractionHand.MAIN_HAND);

        float hardness = state.getDestroySpeed(level, target);
        float speed = villager.getMainHandItem().getDestroySpeed(state);

        if (speed <= 1.0F) {
            speed = 1.0F;
        }

        int prof = RTSProtectedData.get(level.getServer()).getProf(villager.getUUID());

        if (prof == 1 && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            speed *= 2.0F;
        }

        if (prof == 2 && (state.is(BlockTags.LOGS) || state.is(BlockTags.MINEABLE_WITH_AXE))) {
            speed *= 2.0F;
        }

        task.progress += speed * levelSpeed(villager) * workSpeedFor(task.owner) / Math.max(0.1F, hardness) / 20.0F;

        if (task.progress >= 1.0F) {
            addResource(level, task, state);
            level.destroyBlock(target, false);
            grantXp(villager, 1);
            task.progress = 0.0F;

            if (task.type == 1) {
                return true;
            }

            task.target = null;
            task.searchCooldown = 10;
        }

        return false;
    }

    /**
     * Рубка деревьев (type 4): житель ищет опорную точку рядом с деревом
     * и ломает брёвна снизу вверх, доставая до 2 блоков выше ног.
     */
    private static boolean tickMineTree(ServerLevel level, Villager villager, Task task, BlockPos target, BlockState state) {
        BlockPos standPos = findStandingPos(level, target);

        if (standPos == null) {
            blacklistTarget(task, target);
            task.target = null;
            task.searchCooldown = 10;
            return false;
        }

        Vec3 standCenter = new Vec3(standPos.getX() + 0.5, standPos.getY() + 0.0, standPos.getZ() + 0.5);

        if (villager.distanceToSqr(standCenter) > 9.0D) {
            navigateToStand(level, villager, task, standPos);
            task.progress = 0.0F;
            updateStuck(villager, task);

            if (task.stuckTicks == 40) {
                task.pathTarget = null;
            }

            if (task.stuckTicks > 200) {
                blacklistTarget(task, target);
                task.target = null;
                task.stuckTicks = 0;
                task.pathTarget = null;
                return false;
            }

            return false;
        }

        stopMovement(villager);
        assignTool(level, villager, state);

        Vec3 center = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        villager.getLookControl().setLookAt(center.x, center.y, center.z);
        villager.swing(InteractionHand.MAIN_HAND);

        float hardness = state.getDestroySpeed(level, target);
        float speed = villager.getMainHandItem().getDestroySpeed(state);

        if (speed <= 1.0F) {
            speed = 1.0F;
        }

        int prof = RTSProtectedData.get(level.getServer()).getProf(villager.getUUID());

        if (prof == 2) {
            speed *= 2.0F;
        }

        task.progress += speed * levelSpeed(villager) * workSpeedFor(task.owner) / Math.max(0.1F, hardness) / 20.0F;

        if (task.progress >= 1.0F) {
            addResource(level, task, state);
            level.destroyBlock(target, false);
            grantXp(villager, 1);
            task.progress = 0.0F;
            task.target = null;
            task.searchCooldown = 10;
        }

        return false;
    }

    // === ОРИГИНАЛЬНАЯ НАВИГАЦИЯ (для шахт, стройки, фермы, охоты, перемещения) ===
    private static void navigate(ServerLevel level, Villager villager, Task task, BlockPos dest) {
        PathNavigation nav = villager.getNavigation();
        nav.setCanFloat(true);

        if (task.pathRetry > 0) {
            task.pathRetry--;
        }

        boolean destChanged = task.pathTarget == null || !task.pathTarget.equals(dest);
        boolean pathGone = nav.getPath() == null || nav.getPath().isDone();

        if (destChanged) {
            task.pathTarget = dest;
            task.pathRetry = 0;

            if (!tryPath(nav, dest)) {
                task.pathRetry = 30;
            }
        } else if (pathGone && task.pathRetry == 0) {
            if (!tryPath(nav, dest)) {
                task.pathRetry = 30;
            }
        }

        boolean hasPath = nav.getPath() != null;

        if (hasPath && nav.getPath().isDone()) {
            villager.getMoveControl().setWantedPosition(dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, MOVE_SPEED);
        }

        if (hasPath && villager.horizontalCollision && villager.onGround()) {
            villager.setJumping(true);
        } else {
            villager.setJumping(false);
        }
    }

    // === НАВИГАЦИЯ ДЛЯ РУБКИ ДЕРЕВЬЕВ (не лезет по листве) ===
    private static void navigateToStand(ServerLevel level, Villager villager, Task task, BlockPos dest) {
        PathNavigation nav = villager.getNavigation();
        nav.setCanFloat(false);

        if (task.pathRetry > 0) {
            task.pathRetry--;
        }

        boolean destChanged = task.pathTarget == null || !task.pathTarget.equals(dest);
        boolean pathGone = nav.getPath() == null || nav.getPath().isDone();

        if (destChanged) {
            task.pathTarget = dest;
            task.pathRetry = 0;

            if (!tryPathTree(nav, dest)) {
                task.pathRetry = 30;
            }
        } else if (pathGone && task.pathRetry == 0) {
            if (!tryPathTree(nav, dest)) {
                task.pathRetry = 30;
            }
        }

        boolean hasPath = nav.getPath() != null;

        if (hasPath && task.stuckTicks > 10 && task.stuckTicks % 15 == 0 && villager.onGround()) {
            villager.setJumping(true);
        } else {
            villager.setJumping(false);
        }
    }

    // === ОРИГИНАЛЬНЫЙ ПОИСК ПУТИ (для шахт, стройки и т.д.) ===
    private static boolean tryPath(PathNavigation nav, BlockPos dest) {
        BlockPos[] candidates = new BlockPos[]{
                dest,
                dest.above(),
                dest.north(),
                dest.south(),
                dest.east(),
                dest.west(),
                dest.below()
        };

        for (BlockPos candidate : candidates) {
            if (nav.moveTo(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5, MOVE_SPEED)) {
                return true;
            }
        }

        return false;
    }

    // === ПОИСК ПУТИ ДЛЯ РУБКИ ДЕРЕВЬЕВ (без dest.above()) ===
    private static boolean tryPathTree(PathNavigation nav, BlockPos dest) {
        BlockPos[] candidates = new BlockPos[]{
                dest,
                dest.north(),
                dest.south(),
                dest.east(),
                dest.west(),
                dest.below()
        };

        for (BlockPos candidate : candidates) {
            if (nav.moveTo(candidate.getX() + 0.5, candidate.getY() + 0.0, candidate.getZ() + 0.5, MOVE_SPEED)) {
                return true;
            }
        }

        return false;
    }

    // === ОРИГИНАЛЬНАЯ ОСТАНОВКА ===
    private static void stopMovement(Villager villager) {
        villager.getNavigation().stop();
        villager.setJumping(false);
        villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0.0D);
    }

    private static BlockPos getDestination(ServerLevel level, BlockPos target) {
        BlockPos dest = target;
        int up = 0;

        while (!level.isEmptyBlock(dest) && up < 3) {
            dest = dest.above();
            up++;
        }

        return dest;
    }

    public static net.minecraft.world.entity.npc.VillagerProfession professionFor(int prof) {
        if (prof == 1) {
            return net.minecraft.world.entity.npc.VillagerProfession.MASON;
        }

        if (prof == 2) {
            return net.minecraft.world.entity.npc.VillagerProfession.FLETCHER;
        }

        if (prof == 3) {
            return net.minecraft.world.entity.npc.VillagerProfession.FARMER;
        }

        return net.minecraft.world.entity.npc.VillagerProfession.NONE;
    }

    public static void applyProfessionSkin(Villager villager, int prof) {
        net.minecraft.world.entity.npc.VillagerProfession wanted = professionFor(prof);

        if (villager.getVillagerData().getProfession() != wanted) {
            setProfession(villager, wanted);
        }
    }

    /**
     * Sets a villager's profession while allowing the change through the
     * {@link ru.dez.mixin.VillagerProfessionMixin} guard. Vanilla brain
     * activities (AssignProfessionFromJobSite / ResetProfession) call
     * {@code setVillagerData} directly and are blocked for owned villagers;
     * our own profession changes must go through this method instead.
     */
    public static void setProfession(Villager villager, net.minecraft.world.entity.npc.VillagerProfession profession) {
        allowProfessionChange = true;

        try {
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        } finally {
            allowProfessionChange = false;
        }
    }

    /**
     * When true, the next {@code setVillagerData} call on an owned villager is
     * allowed to change the profession. Used to whitelist our own profession
     * assignments while blocking vanilla ones.
     */
    public static boolean allowProfessionChange = false;

    private static void syncProfessionSkin(ServerLevel level, Villager villager) {
        int prof = RTSProtectedData.get(level.getServer()).getProf(villager.getUUID());
        applyProfessionSkin(villager, prof);
    }

    private static void syncUncontrolledProfessionSkins(MinecraftServer server) {
        RTSProtectedData data = RTSProtectedData.get(server);

        if (data.profs.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Integer> entry : data.profs.entrySet()) {
            UUID id = entry.getKey();

            if (TASKS.containsKey(id)) {
                continue;
            }

            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);

                if (entity == null) {
                    continue;
                }

                int prof = entry.getValue();

                // Combatants are raiders (not villagers) and must have their
                // goals re-applied after a reload, otherwise vanilla would make
                // them attack players and villagers again.
                if (prof >= 4) {
                    if (entity instanceof Monster warrior && RTSVillagerTags.isWarrior(warrior)) {
                        configureCombatant(warrior, prof);
                        break;
                    }

                    continue;
                }

                if (entity instanceof Villager villager) {
                    applyProfessionSkin(villager, prof);
                    applyProfessionTool(level, villager, prof);

                    // A farmer's farm task (type 7) is only created when the
                    // profession is assigned, and the task list lives in memory
                    // only. Re-create it here when the villager has no active
                    // task and there is a farm zone to work in, so farmers keep
                    // working after a reload instead of standing idle. The
                    // owning player is restored from the villager's NBT tag so
                    // harvested wheat is still credited to the right stockpile.
                    if (prof == 3 && hasFarmZone(data, level)) {
                        setTask(villager, 7, null, RTSVillagerTags.getOwner(villager));
                    }

                    break;
                }
            }
        }
    }

    private static boolean hasFarmZone(RTSProtectedData data, ServerLevel level) {
        String dimName = level.dimension().location().toString();

        for (RTSProtectedData.Area zone : data.farmZones) {
            if (zone.dim.equals(dimName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos) {
        return RTSProtectedData.get(level.getServer()).contains(level.dimension(), pos);
    }

    private static boolean hasLeavesNear(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAutoTarget(ServerLevel level, int type, BlockPos pos, int toolLevel) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (isProtected(level, pos)) {
            return false;
        }

        if (type == 2) {
            return (state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE)) && pos.getY() < level.getSeaLevel();
        }

        if (type == 3) {
            return isOre(state, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        }

        // Separate ore tasks, unlocked by the miner's tool level (index in UI).
        if (type == 9) {
            return toolLevel >= 1
                    && (isOre(state, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)
                        || isOre(state, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)
                        || isOre(state, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE));
        }

        if (type == 10) {
            return toolLevel >= 2
                    && (isOre(state, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE)
                        || isOre(state, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)
                        || isOre(state, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)
                        || state.is(Blocks.NETHER_QUARTZ_ORE));
        }

        if (type == 11) {
            return toolLevel >= 3
                    && (isOre(state, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
                        || state.is(Blocks.ANCIENT_DEBRIS));
        }

        if (type == 4) {
            boolean isLowestLog = state.is(BlockTags.LOGS)
                    && hasLeavesNear(level, pos)
                    && !level.getBlockState(pos.below()).is(BlockTags.LOGS);

            if (!isLowestLog) return false;

            return findStandingPos(level, pos) != null;
        }

        return false;
    }

    /**
     * Which ores a miner may target depends on the pickaxe they actually wield:
     * everyone digs coal; a level 1 miner (stone pickaxe) adds iron/copper/lapis;
     * level 2 (iron pickaxe) adds gold/redstone/diamond/emerald/quartz; level 3
     * (diamond pickaxe) also handles ancient debris.
     */
    private static boolean isMineableOre(BlockState state, int toolLevel) {
        if (isOre(state, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)) {
            return true;
        }

        if (toolLevel >= 1) {
            if (isOre(state, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)) return true;
            if (isOre(state, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)) return true;
            if (isOre(state, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        }

        if (toolLevel >= 2) {
            if (isOre(state, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE)) return true;
            if (isOre(state, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
            if (isOre(state, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
            if (isOre(state, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)) return true;
            if (state.is(Blocks.NETHER_QUARTZ_ORE)) return true;
        }

        if (toolLevel >= 3) {
            if (state.is(Blocks.ANCIENT_DEBRIS)) return true;
            if (state.is(Blocks.NETHER_GOLD_ORE)) return true;
        }

        return false;
    }

    private static boolean isOre(BlockState state, Block normal, Block deepslate) {
        return state.is(normal) || state.is(deepslate);
    }

    private static boolean isValidTarget(ServerLevel level, int type, BlockPos pos, int toolLevel) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (isProtected(level, pos)) {
            return false;
        }

        if (type == 1) {
            return state.getDestroySpeed(level, pos) >= 0.0F;
        }

        return isAutoTarget(level, type, pos, toolLevel);
    }

    private static BlockPos findTarget(ServerLevel level, BlockPos origin, int type, BlockPos avoid, int toolLevel) {
        int radius = 24;
        // No vertical limit: stone above/below the villager can be targeted at
        // any height (mines, walls, ceilings), not just within +-8 blocks.
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);

                    if (avoid != null && pos.equals(avoid)) {
                        continue;
                    }

                    if (isAutoTarget(level, type, pos, toolLevel)) {
                        double dist = pos.distSqr(origin);

                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private static BlockPos findNearbyReplacement(ServerLevel level, BlockPos origin, int type, BlockPos avoid, int toolLevel) {
        int radius = 12;
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius);

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);

                    if (pos.equals(origin) || (avoid != null && pos.equals(avoid))) {
                        continue;
                    }

                    if (isValidTarget(level, type, pos, toolLevel)) {
                        double dist = pos.distSqr(origin);

                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private static void addResource(ServerLevel level, Task task, BlockState state) {
        if (task.owner == null) {
            return;
        }

        int wood = 0;
        int food = 0;
        int ore = 0;
        int happiness = 0;

        if (state.is(BlockTags.LOGS)) {
            wood = WOOD_PER_BLOCK;
        } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            wood = 1;
        } else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            ore = 1;
        } else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            ore = 1;
        } else if (state.is(BlockTags.CROPS)) {
            food = 1;
        } else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            food = 1;
        }

        if (wood == 0 && food == 0 && ore == 0 && happiness == 0) {
            return;
        }

        RTSResourceManager.add(task.owner, wood, food, ore, happiness);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(task.owner);

        if (player != null) {
            RTSResourceManager.sync(player);
        }
    }

    private static void blacklistTarget(Task task, BlockPos pos) {
        task.blockedPos = pos;
        task.blockedTimer = BLOCKED_TARGET_TICKS;
    }

    private static void tickBlacklist(Task task) {
        if (task.blockedTimer > 0) {
            task.blockedTimer--;

            if (task.blockedTimer <= 0) {
                task.blockedPos = null;
            }
        }
    }

    private static boolean isBlacklisted(Task task, BlockPos pos) {
        return task.blockedPos != null && task.blockedTimer > 0 && task.blockedPos.equals(pos);
    }

    private static void updateStuck(Villager villager, Task task) {
        double dx = villager.getX() - task.lastX;
        double dz = villager.getZ() - task.lastZ;

        if (dx * dx + dz * dz < 0.0001) {
            task.stuckTicks++;
        } else {
            task.stuckTicks = 0;
        }

        task.lastX = villager.getX();
        task.lastZ = villager.getZ();
    }

    private static void assignTool(ServerLevel level, Villager villager, BlockState state) {
        RTSProtectedData data = RTSProtectedData.get(level.getServer());
        int prof = data.getProf(villager.getUUID());

        Item item;

        if (prof == 1) {
            item = minerTool(upgradeLevel(data, "mine"));
        } else if (prof == 2) {
            item = lumberTool(upgradeLevel(data, "lumber_camp"));
        } else {
            item = getToolFor(state);
        }

        ItemStack held = villager.getMainHandItem();

        if (held.getItem() != item) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        }
    }

    private static Item getToolFor(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return Items.WOODEN_AXE;
        }

        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return Items.WOODEN_AXE;
        }

        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return Items.WOODEN_SHOVEL;
        }

        return Items.WOODEN_PICKAXE;
    }

    // === УРОВНИ ПРОФЕССИЙ (ШАХТЁРЫ / ЛЕСОРУБЫ) ===

    /** Current upgrade level (0-3) of a base building such as "mine". */
    public static int upgradeLevel(RTSProtectedData data, String base) {
        if (data.getBuiltCount(base + "_3") > 0) {
            return 3;
        }

        if (data.getBuiltCount(base + "_2") > 0) {
            return 2;
        }

        if (data.getBuiltCount(base) > 0) {
            return 1;
        }

        return 0;
    }

    public static Item minerTool(int level) {
        if (level >= 3) {
            return Items.DIAMOND_PICKAXE;
        }

        if (level >= 2) {
            return Items.IRON_PICKAXE;
        }

        return Items.STONE_PICKAXE;
    }

    public static Item lumberTool(int level) {
        if (level >= 3) {
            return Items.DIAMOND_AXE;
        }

        if (level >= 2) {
            return Items.IRON_AXE;
        }

        return Items.STONE_AXE;
    }

    private static void applyProfessionTool(ServerLevel level, Villager villager, int prof) {
        RTSProtectedData data = RTSProtectedData.get(level.getServer());

        if (prof == 1) {
            setToolIfChanged(villager, minerTool(upgradeLevel(data, "mine")));
        } else if (prof == 2) {
            setToolIfChanged(villager, lumberTool(upgradeLevel(data, "lumber_camp")));
        }
    }

    private static void setToolIfChanged(Villager villager, Item item) {
        if (villager.getMainHandItem().getItem() != item) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        }
    }

    /**
     * Ищет опорную точку для рубки деревьев.
     * Проверяет соседей на уровне цели, на 1 ниже и на 2 ниже.
     * Житель может достать блок на 0, 1 или 2 блока выше своих ног.
     */
    private static BlockPos findStandingPos(ServerLevel level, BlockPos target) {
        BlockPos[] basePositions = new BlockPos[]{
                target,
                target.below(),
                target.below().below()
        };

        for (BlockPos basePos : basePositions) {
            BlockPos[] neighbors = new BlockPos[]{
                    basePos.north(), basePos.south(), basePos.east(), basePos.west()
            };

            for (BlockPos neighbor : neighbors) {
                BlockState neighborState = level.getBlockState(neighbor);
                BlockState belowState = level.getBlockState(neighbor.below());

                boolean canStand = neighborState.isAir() || neighborState.getCollisionShape(level, neighbor).isEmpty();
                boolean hasFloor = belowState.isFaceSturdy(level, neighbor.below(), Direction.UP);

                if (canStand && hasFloor) {
                    int yDiff = target.getY() - neighbor.getY();
                    if (yDiff >= 0 && yDiff <= 2) {
                        return neighbor;
                    }
                }
            }
        }

        return null;
    }

    // === ПАССИВНЫЙ ДОХОД С ФЕРМ ===

    /**
     * Every completed farm passively produces food for its owner. Two farms
     * therefore yield twice as much, which keeps the village fed without
     * requiring the player to micromanage a farmer.
     */
    private static void tickPassiveIncome(MinecraftServer server) {
        farmIncomeTimer++;

        if (farmIncomeTimer < FARM_INCOME_INTERVAL) {
            return;
        }

        farmIncomeTimer = 0;

        RTSProtectedData data = RTSProtectedData.get(server);

        if (data.farmZones.isEmpty()) {
            return;
        }

        Map<UUID, Integer> foodPerOwner = new HashMap<>();

        for (RTSProtectedData.Area zone : data.farmZones) {
            UUID owner = data.getFarmOwner(zone);

            if (owner != null) {
                foodPerOwner.merge(owner, FARM_INCOME_FOOD, Integer::sum);
            } else {
                // Farms built before the owner was tracked: credit everyone.
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    foodPerOwner.merge(player.getUUID(), FARM_INCOME_FOOD, Integer::sum);
                }
            }
        }

        for (Map.Entry<UUID, Integer> entry : foodPerOwner.entrySet()) {
            RTSResourceManager.add(entry.getKey(), 0, entry.getValue(), 0, 0);

            // Sync every online member of that village so teammates see the gain.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (RTSTeamManager.villageId(player.getUUID()).equals(entry.getKey())) {
                    RTSResourceManager.sync(player);
                }
            }
        }
    }

    // === РОСТ ДЕРЕВНИ ===

    /**
     * Once a town hall exists, a brand new villager moves into the village
     * every five minutes.
     */
    private static void tickVillageGrowth(MinecraftServer server) {
        if (!RTSBuildings.isCompleted("town_hall")) {
            growthTimer = 0;
            return;
        }

        growthTimer++;

        if (growthTimer < GROWTH_INTERVAL) {
            return;
        }

        growthTimer = 0;

        RTSProtectedData data = RTSProtectedData.get(server);

        // The village can only grow up to its housing capacity: 3 villagers by
        // default, plus 3 more for every house that has been built.
        if (countOwnedUnits(server, data) >= getPopulationCap(data)) {
            return;
        }

        spawnGrowthVillager(server);
    }

    /** Base population of 3, plus 3 slots for each completed house of any type. */
    public static int getPopulationCap(RTSProtectedData data) {
        int houses = 0;

        for (String id : HOUSE_IDS) {
            houses += data.getBuiltCount(id);
        }

        return 3 + houses * 3;
    }

    /** Counts every living owned unit (villagers and warriors) in the world. */
    public static int countOwnedUnits(MinecraftServer server, RTSProtectedData data) {
        int count = 0;

        for (UUID id : data.profs.keySet()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);

                if (entity instanceof Villager villager && villager.isAlive()) {
                    count++;
                    break;
                }

                if (entity instanceof Monster warrior && warrior.isAlive() && RTSVillagerTags.isWarrior(warrior)) {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    private static void spawnGrowthVillager(MinecraftServer server) {
        RTSProtectedData data = RTSProtectedData.get(server);

        List<Villager> owned = new ArrayList<>();

        for (UUID id : data.profs.keySet()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);

                if (entity instanceof Villager villager && villager.isAlive()) {
                    owned.add(villager);
                    break;
                }
            }
        }

        ServerLevel level;
        Vec3 base;
        UUID owner;

        if (!owned.isEmpty()) {
            // Spawn next to an existing villager so the newcomer appears inside
            // the village rather than wherever the player happens to stand.
            Villager anchor = owned.get(server.overworld().random.nextInt(owned.size()));
            level = (ServerLevel) anchor.level();
            base = anchor.position();
            owner = RTSVillagerTags.getOwner(anchor);
        } else {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();

            if (players.isEmpty()) {
                return;
            }

            ServerPlayer player = players.get(server.overworld().random.nextInt(players.size()));
            level = player.serverLevel();
            base = player.position();
            owner = RTSTeamManager.villageId(player.getUUID());
        }

        RandomSource random = level.random;
        BlockPos pos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(base.x + random.nextInt(7) - 3, base.y, base.z + random.nextInt(7) - 3)
        );

        Villager villager = RTSNpcRegistry.create(level);
        villager.setCustomName(Component.literal(
                GROWTH_NAMES[random.nextInt(GROWTH_NAMES.length)] + " " + (data.profs.size() + 1)));
        villager.setCustomNameVisible(true);
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        RTSVillagerTags.markOwned(villager);
        RTSVillagerTags.markOwner(villager, owner);

        level.addFreshEntity(villager);
        data.setProf(villager.getUUID(), 0);

        RTSNetwork.INSTANCE.send(
                PacketDistributor.ALL.noArg(),
                new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("В деревне появился новый житель!"));
        }
    }

    // === НАЁМ ЖИТЕЛЕЙ В МЭРИИ ===

    /**
     * Processes the paid hire queue: one villager per village is spawned every
     * interval, as long as there is housing capacity. The queue is filled from
     * the town hall panel (see RTSTownHallHirePacket).
     */
    private static void tickHiring(MinecraftServer server) {
        RTSProtectedData data = RTSProtectedData.get(server);

        if (data.hireQueue.isEmpty()) {
            hireTimer = 0;
            return;
        }

        hireTimer++;

        if (hireTimer < HIRE_INTERVAL) {
            return;
        }

        hireTimer = 0;

        int cap = getPopulationCap(data);

        for (UUID village : new ArrayList<>(data.hireQueue.keySet())) {
            if (data.getHireQueue(village) <= 0) {
                continue;
            }

            if (countOwnedUnits(server, data) >= cap) {
                break;
            }

            if (spawnVillagerForVillage(server, village, data)) {
                data.consumeHire(village);
            }
        }

        broadcastHire(server, data);
    }

    /** Spawns one villager belonging to the given village, near its villagers. */
    private static boolean spawnVillagerForVillage(MinecraftServer server, UUID village, RTSProtectedData data) {
        ServerLevel level;
        Vec3 base;

        Villager anchor = findVillagerOfVillage(server, village, data);

        if (anchor != null) {
            level = (ServerLevel) anchor.level();
            base = anchor.position();
        } else {
            ServerPlayer member = null;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (RTSTeamManager.villageId(player.getUUID()).equals(village)) {
                    member = player;
                    break;
                }
            }

            if (member == null) {
                return false;
            }

            level = member.serverLevel();
            base = member.position();
        }

        RandomSource random = level.random;
        BlockPos pos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(base.x + random.nextInt(7) - 3, base.y, base.z + random.nextInt(7) - 3)
        );

        Villager villager = RTSNpcRegistry.create(level);
        villager.setCustomName(Component.literal(
                GROWTH_NAMES[random.nextInt(GROWTH_NAMES.length)] + " " + (data.profs.size() + 1)));
        villager.setCustomNameVisible(true);
        villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WOODEN_AXE));
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        RTSVillagerTags.markOwned(villager);
        RTSVillagerTags.markOwner(villager, village);

        level.addFreshEntity(villager);
        data.setProf(villager.getUUID(), 0);

        RTSNetwork.INSTANCE.send(
                PacketDistributor.ALL.noArg(),
                new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RTSTeamManager.villageId(player.getUUID()).equals(village)) {
                player.sendSystemMessage(Component.literal("Из мэрии прибыл новый житель!"));
            }
        }

        return true;
    }

    private static Villager findVillagerOfVillage(MinecraftServer server, UUID village, RTSProtectedData data) {
        for (UUID id : data.profs.keySet()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);

                if (entity instanceof Villager villager && villager.isAlive()
                        && village.equals(RTSVillagerTags.getOwner(villager))) {
                    return villager;
                }
            }
        }

        return null;
    }

    /** Pushes the current hire queue to every online player (panel refresh). */
    public static void broadcastHire(MinecraftServer server, RTSProtectedData data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID village = RTSTeamManager.villageId(player.getUUID());

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSTownHallPanelPacket(false, data.getHireQueue(village), HIRE_COST)
            );
        }
    }

    // === ВОИНЫ (ПОБОРНИКИ) ===

    /**
     * Promotes a villager into a warrior by replacing it with a Vindicator
     * ("поборник") at the same spot. Returns the new entity's UUID, or null if
     * the villager could not be converted.
     */
    /**
     * Promotes a villager into a combatant by replacing it at the same spot:
     * 4 = warrior (melee), 5 = arbalest/crossbowman (ranged), 6 = summoner
     * (summons Vexes). Each is a GeckoLib-animated subclass of its vanilla
     * counterpart (Vindicator / Pillager / Evoker), so it gets its own skin.
     * Returns the new UUID.
     */
    public static UUID convertToCombatant(ServerLevel level, Villager villager, UUID owner, int prof) {
        if (villager == null || !villager.isAlive()) {
            return null;
        }

        TASKS.remove(villager.getUUID());

        Monster unit;

        if (prof == 5) {
            unit = RTSNpcRegistry.createArbalest(level);
        } else if (prof == 6) {
            unit = RTSNpcRegistry.createSummoner(level);
        } else {
            unit = RTSNpcRegistry.createWarrior(level);
        }

        unit.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        unit.setCustomName(villager.getCustomName());
        unit.setCustomNameVisible(villager.isCustomNameVisible());

        RTSVillagerTags.markOwned(unit);
        RTSVillagerTags.markWarrior(unit);
        RTSVillagerTags.markOwner(unit, owner);

        configureCombatant(unit, prof);

        // Войны создаются сразу с полным запасом здоровья (усиленные статы).
        unit.setHealth(unit.getMaxHealth());

        level.addFreshEntity(unit);
        villager.discard();

        return unit.getUUID();
    }

    /**
     * Rebuilds a combatant's AI so it does not run the vanilla raider behaviour
     * (which attacks players and villagers). Every goal is cleared first; the
     * actual combat is driven from {@link #tickWarriors(MinecraftServer)} so
     * villagers and allied units are never attacked.
     */
    public static void configureCombatant(Monster unit, int prof) {
        if (unit == null) {
            return;
        }

        unit.goalSelector.removeAllGoals(goal -> true);
        unit.targetSelector.removeAllGoals(goal -> true);

        if (unit instanceof Raider raider) {
            raider.setCanJoinRaid(false);
        }

        unit.setPersistenceRequired();

        if (prof == 5) {
            unit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        } else if (prof == 6) {
            unit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        } else {
            unit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }

        if (unit.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            unit.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0D);
        }

        // === УСИЛЕНИЕ ВОИНОВ ===
        // Поборники заметно крепче и сильнее обычных мобов: больше здоровья,
        // брони и урона, плюс сопротивление отбрасыванию.
        if (prof == 5) {
            // Арбалетчик: стреляет с дистанции, поэтому чуть менее живуч.
            setAttribute(unit, Attributes.MAX_HEALTH, 120.0D);
            setAttribute(unit, Attributes.ATTACK_DAMAGE, 10.0D);
            setAttribute(unit, Attributes.ARMOR, 6.0D);
            setAttribute(unit, Attributes.ARMOR_TOUGHNESS, 2.0D);
            setAttribute(unit, Attributes.KNOCKBACK_RESISTANCE, 0.4D);
            setAttribute(unit, Attributes.MOVEMENT_SPEED, 0.38D);
        } else if (prof == 6) {
            // Призыватель: держит дистанцию и командует вексами.
            setAttribute(unit, Attributes.MAX_HEALTH, 140.0D);
            setAttribute(unit, Attributes.ATTACK_DAMAGE, 12.0D);
            setAttribute(unit, Attributes.ARMOR, 7.0D);
            setAttribute(unit, Attributes.ARMOR_TOUGHNESS, 3.0D);
            setAttribute(unit, Attributes.KNOCKBACK_RESISTANCE, 0.5D);
            setAttribute(unit, Attributes.MOVEMENT_SPEED, 0.38D);
        } else {
            // Поборник (ближний бой): самый крепкий и сильный.
            setAttribute(unit, Attributes.MAX_HEALTH, 160.0D);
            setAttribute(unit, Attributes.ATTACK_DAMAGE, 14.0D);
            setAttribute(unit, Attributes.ARMOR, 8.0D);
            setAttribute(unit, Attributes.ARMOR_TOUGHNESS, 4.0D);
            setAttribute(unit, Attributes.KNOCKBACK_RESISTANCE, 0.6D);
            setAttribute(unit, Attributes.MOVEMENT_SPEED, 0.42D);
        }

        unit.goalSelector.addGoal(0, new FloatGoal(unit));

        // Only the melee warrior gets a melee goal. Ranged units (crossbowman)
        // and summoners keep their distance - their attacks are driven manually
        // from engage(), so a melee goal would drag them into close combat.
        if (prof == 4) {
            unit.goalSelector.addGoal(2, new MeleeAttackGoal(unit, 1.2D, false));
        }

        unit.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(unit, 0.6D));
        unit.goalSelector.addGoal(6, new LookAtPlayerGoal(unit, Player.class, 8.0F));
        unit.goalSelector.addGoal(7, new RandomLookAroundGoal(unit));

        // No vanilla target selector: it cannot tell allied Vexes/warriors from
        // enemies. Targeting is fully controlled by tickWarriors()/isEnemy().
    }

    /** Backwards-compatible wrapper used by the entity-join restore hook. */
    public static void configureWarrior(Vindicator warrior) {
        configureCombatant(warrior, 4);
    }

    // === ВОССТАНОВЛЕНИЕ СОХРАНЁННОГО СОСТОЯНИЯ ===

    /**
     * Re-applies the saved profession, skin and standing order to an owned
     * villager right after it loads. Called from {@code EntityJoinLevelEvent},
     * so it also covers villagers in chunks that were unloaded when the player
     * joined.
     */
    public static void restoreVillagerState(Villager villager) {
        if (villager == null || !RTSVillagerTags.isOwned(villager)) {
            return;
        }

        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        RTSProtectedData data = RTSProtectedData.get(level.getServer());
        UUID id = villager.getUUID();

        if (!data.profs.containsKey(id)) {
            return;
        }

        int prof = data.getProf(id);
        applyProfessionSkin(villager, prof);

        Integer order = data.getOrder(id);

        if (order != null) {
            if (isRestorableOrder(order) && (order != 7 || hasFarmZone(data, level))) {
                setTask(villager, order, null, RTSVillagerTags.getOwner(villager));
                return;
            }

            // Orders that need their original target (move/mine/build) cannot
            // be reconstructed, so forget them instead of leaving them stale.
            data.removeOrder(id);
        }

        if (prof == 3 && hasFarmZone(data, level)) {
            setTask(villager, 7, null, RTSVillagerTags.getOwner(villager));
        }
    }

    /** Orders that can be re-created without their original target block. */
    private static boolean isRestorableOrder(int type) {
        return type == 2 || type == 3 || type == 4 || type == 6 || type == 7
                || type == 9 || type == 10 || type == 11;
    }

    // === ПОТРЕБНОСТИ, ПРОКАЧКА И КВЕСТЫ ===

    /** Level 0-10 derived from a villager's experience. */
    public static int villagerLevel(int xp) {
        return Math.min(10, xp / 50);
    }

    private static void grantXp(Villager villager, int amount) {
        if (villager == null) {
            return;
        }

        if (villager.level() instanceof ServerLevel serverLevel) {
            RTSProtectedData.get(serverLevel.getServer()).addXp(villager.getUUID(), amount);
        }
    }

    /** Work speed multiplier from the villager's personal level. */
    private static float levelSpeed(Villager villager) {
        if (!(villager.level() instanceof ServerLevel serverLevel)) {
            return 1.0F;
        }

        int level = villagerLevel(RTSProtectedData.get(serverLevel.getServer()).getXp(villager.getUUID()));
        return 1.0F + level * 0.05F;
    }

    /** Work speed multiplier from the owner's happiness (0.5 .. 1.0). */
    private static float workSpeedFor(UUID owner) {
        if (owner == null) {
            return 1.0F;
        }

        int happiness = RTSResourceManager.get(owner).happiness;
        return 0.5F + 0.5F * (Math.max(0, Math.min(100, happiness)) / 100.0F);
    }

    /**
     * Villagers eat over time. Plenty of food raises happiness, hunger lowers it,
     * and a fully starved, miserable village starts losing villagers.
     */
    private static void tickNeeds(MinecraftServer server) {
        needsTimer++;

        if (needsTimer < NEEDS_INTERVAL) {
            return;
        }

        needsTimer = 0;

        RTSProtectedData data = RTSProtectedData.get(server);
        int population = countOwnedUnits(server, data);

        if (population <= 0) {
            return;
        }

        int foodNeeded = Math.max(1, population / 4);

        java.util.Set<UUID> villages = new java.util.HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            villages.add(RTSTeamManager.villageId(player.getUUID()));
        }

        for (UUID village : villages) {
            RTSResourceManager.Resources res = RTSResourceManager.get(village);

            if (res.food >= foodNeeded) {
                res.food -= foodNeeded;
                res.happiness = Math.min(100, res.happiness + 2);
            } else {
                res.food = 0;
                res.happiness = Math.max(0, res.happiness - 5);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (RTSTeamManager.villageId(player.getUUID()).equals(village)) {
                    RTSResourceManager.sync(player);
                }
            }
        }

        for (UUID village : villages) {
            if (RTSResourceManager.get(village).happiness <= 0 && makeVillagerLeave(server, data)) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (RTSTeamManager.villageId(player.getUUID()).equals(village)) {
                        player.sendSystemMessage(Component.literal("Житель покинул деревню из-за голода!"));
                    }
                }
            }
        }
    }

    private static boolean makeVillagerLeave(MinecraftServer server, RTSProtectedData data) {
        for (UUID id : new ArrayList<>(data.profs.keySet())) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);

                if (entity instanceof Villager villager && villager.isAlive()) {
                    TASKS.remove(id);
                    data.removeProf(id);
                    villager.discard();

                    RTSNetwork.INSTANCE.send(
                            PacketDistributor.ALL.noArg(),
                            new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
                    );

                    return true;
                }
            }
        }

        return false;
    }

    /** Pushes villager levels to all players every few seconds. */
    private static void tickStateSync(MinecraftServer server) {
        stateSyncTimer++;

        if (stateSyncTimer < STATE_SYNC_INTERVAL) {
            return;
        }

        stateSyncTimer = 0;

        RTSProtectedData data = RTSProtectedData.get(server);
        Map<UUID, Integer> levels = new HashMap<>();

        for (Map.Entry<UUID, Integer> entry : data.xp.entrySet()) {
            levels.put(entry.getKey(), villagerLevel(entry.getValue()));
        }

        RTSProfSyncPacket profPacket = new RTSProfSyncPacket(new HashMap<>(data.profs), levels);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RTSNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), profPacket);
        }
    }

    // === ВОИНЫ: АВТОБОЙ ===

    /**
     * Drives every owned combatant. Warriors (Vindicator) fight in melee,
     * crossbowmen (Pillager) shoot arrows and summoners (Evoker) cast at range
     * and summon Vexes. Each one first looks for a living enemy - a hostile
     * monster, or a unit/player of a village we are at war with - and, if there
     * is none, attacks an enemy building.
     */
    private static void tickWarriors(MinecraftServer server) {
        RTSTeamManager.setServer(server);
        RTSProtectedData data = RTSProtectedData.get(server);

        if (data.profs.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Integer> entry : data.profs.entrySet()) {
            int prof = entry.getValue();

            if (prof < 4) {
                continue;
            }

            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(entry.getKey());

                if (!(entity instanceof Monster unit) || !unit.isAlive()) {
                    continue;
                }

                if (!RTSVillagerTags.isWarrior(unit)) {
                    continue;
                }

                UUID owner = RTSVillagerTags.getOwner(unit);

                keepGolemsOffWarrior(level, unit);

                // A player-issued order always wins over automatic combat.
                if (followOrder(level, unit)) {
                    break;
                }

                LivingEntity target = findEnemy(level, unit, owner);

                if (target != null) {
                    engage(level, unit, target, prof, owner);
                } else if (!attackEnemyBuilding(level, unit, owner) && prof == 6 && unit.tickCount % 200 == 0) {
                    // An idle summoner still conjures a guard Vex now and then.
                    summonVex(level, unit, null, owner);
                }

                break;
            }
        }
    }

    /**
     * Moves a warrior towards a player-issued destination. While the order is
     * active the warrior ignores every enemy completely; the order ends when it
     * arrives, or after a timeout so a blocked path cannot freeze it forever.
     *
     * @return true while the order is still active (combat must be skipped)
     */
    private static boolean followOrder(ServerLevel level, Monster unit) {
        UUID id = unit.getUUID();
        BlockPos dest = WARRIOR_ORDERS.get(id);

        if (dest == null) {
            return false;
        }

        int ticks = WARRIOR_ORDER_TICKS.getOrDefault(id, 0) - 1;

        if (ticks <= 0) {
            clearWarriorOrder(id);
            return false;
        }

        WARRIOR_ORDER_TICKS.put(id, ticks);

        double distance = unit.distanceToSqr(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);

        if (distance <= 2.25D) {
            // Arrived: hand the warrior back to automatic combat.
            clearWarriorOrder(id);
            return false;
        }

        // Ignore enemies while obeying the order.
        unit.setTarget(null);

        PathNavigation nav = unit.getNavigation();

        if (nav.isDone()) {
            nav.moveTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D, 1.2D);
        }

        return true;
    }

    /** Gives a warrior a movement order that overrides automatic combat. */
    public static void orderWarriorMove(Monster warrior, BlockPos pos) {
        if (warrior == null || pos == null) {
            return;
        }

        WARRIOR_ORDERS.put(warrior.getUUID(), pos.immutable());
        WARRIOR_ORDER_TICKS.put(warrior.getUUID(), WARRIOR_ORDER_TIMEOUT);

        warrior.setTarget(null);
        warrior.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 1.2D);
    }

    /** Cancels a warrior's order (e.g. when the "defend" command is given). */
    public static void clearWarriorOrder(UUID id) {
        WARRIOR_ORDERS.remove(id);
        WARRIOR_ORDER_TICKS.remove(id);
    }

    /** Nearest valid enemy: a wild monster, an enemy unit or an enemy player. */
    private static LivingEntity findEnemy(ServerLevel level, Monster unit, UUID owner) {
        AABB box = unit.getBoundingBox().inflate(24.0D, MAX_TARGET_DY, 24.0D);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity candidate : list) {
            if (!candidate.isAlive() || candidate == unit || !isEnemy(unit, candidate, owner)) {
                continue;
            }

            double dist = candidate.distanceToSqr(unit);

            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean isEnemy(Monster unit, LivingEntity candidate, UUID owner) {
        if (candidate == unit) {
            return false;
        }

        // Do not chase enemies far below/above: underground mobs would drag the
        // warrior (or its Vexes) into the ground.
        if (Math.abs(candidate.getY() - unit.getY()) > MAX_TARGET_DY) {
            return false;
        }

        if (candidate instanceof Monster monster) {
            if (RTSVillagerTags.isWarrior(monster)) {
                UUID other = RTSVillagerTags.getOwner(monster);
                return other != null && RTSTeamManager.areAtWar(owner, other);
            }

            // A wild hostile monster is always a valid target.
            return true;
        }

        if (candidate instanceof Villager villager) {
            UUID other = RTSVillagerTags.getOwner(villager);
            return other != null && RTSTeamManager.areAtWar(owner, other);
        }

        if (candidate instanceof Player player) {
            UUID other = RTSTeamManager.villageId(player.getUUID());
            return RTSTeamManager.areAtWar(owner, other);
        }

        return false;
    }

    private static void engage(ServerLevel level, Monster unit, LivingEntity target, int prof, UUID owner) {
        unit.setTarget(target);
        unit.getLookControl().setLookAt(target);

        double dist = unit.distanceToSqr(target);

        if (prof == 5) {
            // Crossbowman: hold the line and shoot.
            if (dist > 144.0D) {
                unit.getNavigation().moveTo(target, 1.2D);
            } else {
                unit.getNavigation().stop();

                if (unit.tickCount % 15 == 0) {
                    playAnim(unit, "attack");
                    shootArrow(level, unit, target);
                }
            }
        } else if (prof == 6) {
            // Summoner: conjure a Vex and cast a magic bolt without closing in.
            if (unit.tickCount % 120 == 0) {
                summonVex(level, unit, target, owner);
            }

            if (dist > 100.0D) {
                unit.getNavigation().moveTo(target, 1.1D);
            } else {
                unit.getNavigation().stop();

                // Cast only every few seconds, so the one-shot "magic" animation
                // finishes and the summoner visibly returns to idle in between.
                if (unit.tickCount % 90 == 0) {
                    unit.swing(InteractionHand.MAIN_HAND);
                    playAnim(unit, "magic");
                    target.hurt(level.damageSources().magic(), attackDamage(unit) * 0.8F);
                }
            }
        } else {
            // Melee warrior.
            if (dist > 6.25D) {
                unit.getNavigation().moveTo(target, 1.3D);
            } else if (unit.tickCount % 16 == 0) {
                unit.swing(InteractionHand.MAIN_HAND);
                playAnim(unit, "attack");
                target.hurt(level.damageSources().mobAttack(unit), attackDamage(unit));
            }
        }
    }

    /** Plays a one-shot GeckoLib animation ("attack" / "magic") if supported. */
    private static void playAnim(Entity unit, String name) {
        if (unit instanceof GeoEntity animated) {
            animated.triggerAnim("main", name);
        }
    }

    /** Sets a base attribute value, ignoring units without that attribute. */
    private static void setAttribute(Monster unit, Attribute attribute, double value) {
        if (unit.getAttribute(attribute) != null) {
            unit.getAttribute(attribute).setBaseValue(value);
        }
    }

    /** Attack damage of a combatant, falling back to the old flat value. */
    private static float attackDamage(Monster unit) {
        if (unit.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            return (float) unit.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }

        return 14.0F;
    }

    private static void shootArrow(ServerLevel level, Monster unit, LivingEntity target) {
        Arrow arrow = new Arrow(level, unit);
        double dx = target.getX() - unit.getX();
        double dy = target.getEyeY() - unit.getEyeY();
        double dz = target.getZ() - unit.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        arrow.shoot(dx, dy + horiz * 0.2D, dz, 1.6F, 6.0F);
        level.addFreshEntity(arrow);
        unit.swing(InteractionHand.MAIN_HAND);
    }

    private static void summonVex(ServerLevel level, Monster unit, LivingEntity target, UUID owner) {
        Vex vex = new Vex(EntityType.VEX, level);
        vex.moveTo(unit.getX(), unit.getY() + 1.0D, unit.getZ(), unit.getYRot(), 0.0F);

        // Mark the Vex as ours so villagers don't panic and allies don't hit it.
        RTSVillagerTags.markOwned(vex);
        RTSVillagerTags.markWarrior(vex);
        RTSVillagerTags.markOwner(vex, owner);

        // A vanilla Vex only hunts players, villagers and iron golems and its
        // custom (private) charge goal fights any manually driven behaviour, so
        // strip every goal. Movement and attacks are driven entirely from
        // tickVexes() instead.
        vex.goalSelector.removeAllGoals(goal -> true);
        vex.targetSelector.removeAllGoals(goal -> true);

        // Strengthen the summon so it is a real threat.
        setAttribute(vex, Attributes.MAX_HEALTH, 24.0D);
        setAttribute(vex, Attributes.ATTACK_DAMAGE, 9.0D);
        setAttribute(vex, Attributes.ARMOR, 4.0D);
        vex.setHealth(vex.getMaxHealth());

        level.addFreshEntity(vex);
        VEX_SUMMONER.put(vex.getUUID(), unit.getUUID());
    }

    /**
     * Vexes have {@code noPhysics} in vanilla, so they phase straight through
     * blocks - a mob below the surface would drag them into the stone. Every
     * tick our summons are checked and pulled back to the surface / to the
     * summoner's level, and underground targets are dropped.
     */
    private static void tickVexes(MinecraftServer server) {
        Iterator<Map.Entry<UUID, UUID>> entries = VEX_SUMMONER.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry<UUID, UUID> entry = entries.next();

            Entity entity = null;

            for (ServerLevel level : server.getAllLevels()) {
                entity = level.getEntity(entry.getKey());

                if (entity != null) {
                    break;
                }
            }

            if (!(entity instanceof Vex vex) || !vex.isAlive()) {
                entries.remove();
                continue;
            }

            if (!(vex.level() instanceof ServerLevel level)) {
                entries.remove();
                continue;
            }

            // Player order: fly straight to the clicked spot (and attack
            // whatever enemy it finds there).
            BlockPos order = WARRIOR_ORDERS.get(vex.getUUID());

            if (order != null) {
                int orderTicks = WARRIOR_ORDER_TICKS.getOrDefault(vex.getUUID(), 0) - 1;
                boolean arrived = vex.distanceToSqr(order.getX() + 0.5D, order.getY(), order.getZ() + 0.5D) <= 4.0D;

                if (orderTicks <= 0 || arrived) {
                    clearWarriorOrder(vex.getUUID());
                    order = null;
                } else {
                    WARRIOR_ORDER_TICKS.put(vex.getUUID(), orderTicks);
                    vex.setTarget(null);
                    vex.getMoveControl().setWantedPosition(
                            order.getX() + 0.5D, order.getY() + 1.0D, order.getZ() + 0.5D, 1.0D);
                }
            }

            // A target deep below/above would pull the phasing Vex into stone.
            LivingEntity target = vex.getTarget();

            if (target != null && Math.abs(target.getY() - vex.getY()) > MAX_TARGET_DY) {
                vex.setTarget(null);
            }

            // Dig itself out if it ended up inside solid terrain.
            BlockPos pos = vex.blockPosition();

            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                for (int i = 1; i <= 12; i++) {
                    BlockPos above = pos.above(i);

                    if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                        vex.setPos(vex.getX(), above.getY(), vex.getZ());
                        vex.setDeltaMovement(0.0D, 0.0D, 0.0D);
                        vex.setTarget(null);
                        break;
                    }
                }
            }

            // Never sink far below the summoner that created it.
            Entity summoner = level.getEntity(entry.getValue());

            if (order == null && summoner instanceof LivingEntity alive && alive.isAlive()
                    && vex.getY() < alive.getY() - 3.0D) {
                vex.setPos(vex.getX(), alive.getY() + 1.0D, vex.getZ());
                vex.setDeltaMovement(0.0D, 0.0D, 0.0D);
                vex.setTarget(null);
            }

            // === АВТОАТАКА ВЕКСА ===
            // Ванильный ИИ векса выключен, поэтому врага ищем и атакуем сами:
            // летим к ближайшему врагу (живой цели) и бьём его. Если врагов
            // нет - держимся рядом с призывателем.
            if (order == null) {
                LivingEntity enemy = findEnemy(level, vex, RTSVillagerTags.getOwner(vex));

                if (enemy != null) {
                    vex.setTarget(enemy);

                    if (vex.distanceToSqr(enemy) > 3.0D) {
                        vex.getMoveControl().setWantedPosition(
                                enemy.getX(), enemy.getEyeY() - 0.5D, enemy.getZ(), 1.0D);
                    } else if (vex.tickCount % 20 == 0) {
                        vex.doHurtTarget(enemy);
                    }
                } else {
                    vex.setTarget(null);

                    if (summoner instanceof LivingEntity aliveSummoner && aliveSummoner.isAlive()
                            && vex.distanceToSqr(aliveSummoner) > 16.0D) {
                        vex.getMoveControl().setWantedPosition(
                                aliveSummoner.getX(), aliveSummoner.getY() + 1.5D, aliveSummoner.getZ(), 1.0D);
                    }
                }
            }
        }
    }

    /** Sends the unit to hit the nearest enemy building. Returns true if one exists. */
    private static boolean attackEnemyBuilding(ServerLevel level, Monster unit, UUID owner) {
        if (owner == null) {
            return false;
        }

        RTSProtectedData data = RTSProtectedData.get(level.getServer());
        String dim = level.dimension().location().toString();
        RTSProtectedData.PlacedBuilding best = null;
        double bestDist = Double.MAX_VALUE;

        for (RTSProtectedData.PlacedBuilding placed : data.placements) {
            if (!placed.dim.equals(dim)) {
                continue;
            }

            UUID buildingOwner = parseOwner(placed.owner);

            if (buildingOwner == null || !RTSTeamManager.areAtWar(owner, buildingOwner)) {
                continue;
            }

            BlockPos pos = placed.pos();
            double dist = unit.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

            if (dist < bestDist) {
                bestDist = dist;
                best = placed;
            }
        }

        if (best == null) {
            return false;
        }

        BlockPos pos = best.pos();

        if (bestDist > 9.0D) {
            unit.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 1.2D);
        } else if (unit.tickCount % 20 == 0) {
            unit.swing(InteractionHand.MAIN_HAND);
            playAnim(unit, "attack");
            damageBuilding(level, data, best, 12.0F);
        }

        return true;
    }

    private static void damageBuilding(ServerLevel level, RTSProtectedData data,
                                       RTSProtectedData.PlacedBuilding placed, float dmg) {
        if (placed.health < 0.0F) {
            placed.health = buildingMaxHealth(placed.id);
        }

        placed.health -= dmg;
        data.setDirty();

        if (placed.health > 0.0F) {
            return;
        }

        // Destroyed: clear the blocks and forget the building.
        RTSBuilding building = RTSBuildings.get(placed.id);

        if (building != null && building.schematic != null) {
            for (RTSSchematic.Entry entry : building.schematic.entries) {
                level.setBlockAndUpdate(placed.pos().offset(entry.x, entry.y, entry.z), Blocks.AIR.defaultBlockState());
            }
        }

        data.removePlacement(placed);

        int remaining = data.builtCounts.getOrDefault(placed.id, 1) - 1;

        if (remaining <= 0) {
            data.builtCounts.remove(placed.id);
        } else {
            data.builtCounts.put(placed.id, remaining);
        }

        data.setDirty();

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("Постройка разрушена врагом!"));
        }
    }

    /** Base hit points for a building id. */
    private static float buildingMaxHealth(String id) {
        if (id.startsWith("town_hall")) {
            return 400.0F;
        }
        if (id.startsWith("house")) {
            return 150.0F;
        }
        if (id.startsWith("mine") || id.startsWith("lumber")) {
            return 250.0F;
        }
        if (id.equals("barracks")) {
            return 300.0F;
        }
        if (id.equals("farm")) {
            return 120.0F;
        }
        if (id.equals("shelter")) {
            return 200.0F;
        }

        return 150.0F;
    }

    private static UUID parseOwner(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Iron golems target anything that is an Enemy - which includes our raider
     * combatants. Clear such targets so the village guard does not attack the
     * villagers' own protectors.
     */
    private static void keepGolemsOffWarrior(ServerLevel level, Monster warrior) {
        List<IronGolem> golems = level.getEntitiesOfClass(
                IronGolem.class, warrior.getBoundingBox().inflate(32.0, 16.0, 32.0));

        for (IronGolem golem : golems) {
            if (RTSVillagerTags.isWarrior(golem.getTarget())) {
                golem.setTarget(null);
            }
        }
    }

    // === УКРЫТИЕ (НОЧЬ) ===

    /**
     * Днём жители работают как обычно, но с наступлением ночи каждый житель
     * (кроме воинов) деревни, у которой есть укрытие, идёт внутрь и сидит там
     * до рассвета. Пока житель в укрытии, его приказ ставится на паузу (сам
     * приказ сохраняется в данных мира) и возобновляется утром, а проигрывается
     * анимация "sit".
     */
    private static void tickShelter(MinecraftServer server) {
        RTSProtectedData data = RTSProtectedData.get(server);

        for (Map.Entry<UUID, Integer> entry : data.profs.entrySet()) {
            // Воины (поборник / арбалетчик / призыватель) остаются снаружи.
            if (entry.getValue() >= 4) {
                SHELTERING.remove(entry.getKey());
                continue;
            }

            Villager villager = findVillager(server, entry.getKey());

            if (villager == null) {
                SHELTERING.remove(entry.getKey());
                continue;
            }

            if (!(villager.level() instanceof ServerLevel level)) {
                continue;
            }

            BlockPos spot = level.isNight()
                    ? findShelterSpot(level, data, villager, RTSVillagerTags.getOwner(villager))
                    : null;

            if (spot == null) {
                if (SHELTERING.remove(entry.getKey())) {
                    releaseFromShelter(villager, data);
                }

                continue;
            }

            // Укрытие важнее любой работы: снимаем активную задачу, чтобы
            // житель не убежал работать. Сам приказ сохранён в данных мира и
            // будет возобновлён утром.
            SHELTERING.add(entry.getKey());
            TASKS.remove(entry.getKey());

            sendToShelter(level, villager, spot);
        }
    }

    private static Villager findVillager(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);

            if (entity instanceof Villager villager && villager.isAlive()) {
                return villager;
            }
        }

        return null;
    }

    /**
     * Ближайшее укрытие, принадлежащее деревне жителя. Возвращает точку внутри
     * укрытия, куда нужно встать (небольшое смещение по UUID не даёт всем
     * жителям занять один и тот же блок).
     */
    private static BlockPos findShelterSpot(ServerLevel level, RTSProtectedData data, Villager villager, UUID owner) {
        String dim = level.dimension().location().toString();
        RTSProtectedData.PlacedBuilding best = null;
        double bestDist = Double.MAX_VALUE;

        for (RTSProtectedData.PlacedBuilding placed : data.placements) {
            if (!placed.id.equals("shelter") || !placed.dim.equals(dim)) {
                continue;
            }

            UUID buildingOwner = parseOwner(placed.owner);

            // Укрытием может пользоваться только построившая его деревня.
            if (owner != null && buildingOwner != null && !owner.equals(buildingOwner)) {
                continue;
            }

            double dist = villager.distanceToSqr(placed.x + 0.5D, placed.y + 0.5D, placed.z + 0.5D);

            if (dist < bestDist) {
                bestDist = dist;
                best = placed;
            }
        }

        if (best == null) {
            return null;
        }

        int hash = villager.getUUID().hashCode();
        int dx = Math.floorMod(hash, 3) - 1;
        int dz = Math.floorMod(hash / 3, 3) - 1;

        return new BlockPos(best.x + dx, best.y + 1, best.z + dz);
    }

    /** Ведёт жителя в укрытие; внутри он садится и остаётся на месте. */
    private static void sendToShelter(ServerLevel level, Villager villager, BlockPos spot) {
        if (villager instanceof RTSNpc npc) {
            npc.setSheltered(true);
            npc.setBreaking(false);
            npc.setWorking(true);
            npc.interruptSocialising();
        }

        // Ванильный мозг может увести жителя гулять - глушим его.
        villager.getBrain().stopAll(level, villager);

        Vec3 target = new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        PathNavigation nav = villager.getNavigation();

        if (villager.distanceToSqr(target) > 2.25D) {
            if (nav.isDone()) {
                nav.moveTo(target.x, target.y, target.z, MOVE_SPEED);
            }

            if (nav.getPath() == null) {
                // Путь не нашёлся (например, вход завален) - толкаем напрямую.
                villager.getMoveControl().setWantedPosition(target.x, target.y, target.z, MOVE_SPEED);
            }
        } else {
            stopMovement(villager);

            if (villager.getXRot() != 0.0F) {
                villager.setXRot(0.0F);
            }
        }
    }

    /** Будит жителя утром и возобновляет его дневной приказ, если он был. */
    private static void releaseFromShelter(Villager villager, RTSProtectedData data) {
        if (villager instanceof RTSNpc npc) {
            npc.setSheltered(false);
            npc.setWorking(false);
        }

        Integer order = data.getOrder(villager.getUUID());

        if (order == null) {
            return;
        }

        boolean farmOrder = order == 7;
        boolean hasFarm = villager.level() instanceof ServerLevel serverLevel && hasFarmZone(data, serverLevel);

        if (isRestorableOrder(order) && (!farmOrder || hasFarm)) {
            setTask(villager, order, null, RTSVillagerTags.getOwner(villager));
        } else {
            data.removeOrder(villager.getUUID());
        }
    }

    private static class Task {
        ResourceKey<Level> dim;
        int type;
        BlockPos target;
        UUID owner;
        float progress;
        int searchCooldown;
        int stuckTicks;
        BlockPos pathTarget;
        int pathRetry;
        int siteId;
        UUID huntTarget;
        int attackCooldown;
        int farmCooldown;
        int toolLevel;
        double lastX;
        double lastZ;
        BlockPos blockedPos;
        int blockedTimer;
    }
}