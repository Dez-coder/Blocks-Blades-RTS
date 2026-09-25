package ru.dez.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.dez.blocks_blades;

/**
 * Registers the animated RTS entities (villager NPCs and the three combatants).
 * Each one extends its vanilla counterpart, so all existing RTS logic keeps
 * working; only the rendering (GeckoLib model + per-profession skin) changes.
 */
public final class RTSNpcRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, blocks_blades.MODID);

    /** Animated villager. */
    public static final RegistryObject<EntityType<RTSNpc>> NPC =
            ENTITIES.register("villager_npc", () -> EntityType.Builder
                    .<RTSNpc>of(RTSNpc::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("villager_npc"));

    /** Melee warrior (Vindicator). */
    public static final RegistryObject<EntityType<RTSWarrior>> WARRIOR =
            ENTITIES.register("warrior_npc", () -> EntityType.Builder
                    .<RTSWarrior>of(RTSWarrior::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("warrior_npc"));

    /** Ranged crossbowman (Pillager). */
    public static final RegistryObject<EntityType<RTSArbalest>> ARBALEST =
            ENTITIES.register("arbalest_npc", () -> EntityType.Builder
                    .<RTSArbalest>of(RTSArbalest::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("arbalest_npc"));

    /** Summoner (Evoker). */
    public static final RegistryObject<EntityType<RTSSummoner>> SUMMONER =
            ENTITIES.register("summoner_npc", () -> EntityType.Builder
                    .<RTSSummoner>of(RTSSummoner::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("summoner_npc"));

    private RTSNpcRegistry() {
    }

    public static void init(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(RTSNpcRegistry::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), Villager.createAttributes().build());
        event.put(WARRIOR.get(), Vindicator.createAttributes().build());
        event.put(ARBALEST.get(), Pillager.createAttributes().build());
        event.put(SUMMONER.get(), Evoker.createAttributes().build());
    }

    /** Creates a new animated villager at the given level. */
    public static RTSNpc create(Level level) {
        return new RTSNpc(NPC.get(), level);
    }

    /** Creates a new melee warrior. */
    public static RTSWarrior createWarrior(Level level) {
        return new RTSWarrior(WARRIOR.get(), level);
    }

    /** Creates a new ranged arbalest. */
    public static RTSArbalest createArbalest(Level level) {
        return new RTSArbalest(ARBALEST.get(), level);
    }

    /** Creates a new summoner. */
    public static RTSSummoner createSummoner(Level level) {
        return new RTSSummoner(SUMMONER.get(), level);
    }
}
