package ru.dez.client.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.ForgeRegistries;
import ru.dez.client.camera.RTSProfClient;
import ru.dez.entity.RTSNpc;

/**
 * Maps entities to their skin texture.
 *
 * <p>Villagers (and their professions):
 * <ul>
 *   <li>miner    -> {@code npc_miner.png}</li>
 *   <li>lumber   -> {@code npc_lumberjack.png}</li>
 *   <li>farmer   -> {@code npc_farmer.png}</li>
 *   <li>vanilla profession -> {@code npc_<profession>.png}, e.g. npc_librarian.png</li>
 *   <li>no profession -> {@code npc.png}</li>
 * </ul>
 *
 * <p>Combatants:
 * <ul>
 *   <li>warrior  -> {@code npc_warrior.png}</li>
 *   <li>arbalest -> {@code npc_arbalest.png}</li>
 *   <li>summoner -> {@code npc_summoner.png}</li>
 * </ul>
 *
 * All files live in {@code assets/blocks_blades/textures/entity/}.
 */
public final class RTSSkins {

    private RTSSkins() {
    }

    public static ResourceLocation tex(String name) {
        return new ResourceLocation("blocks_blades", "textures/entity/" + name + ".png");
    }

    /** Texture for a villager NPC (by RTS profession, then vanilla profession). */
    public static ResourceLocation forVillager(RTSNpc npc) {
        int prof = RTSProfClient.get(npc.getUUID());

        if (prof == 1) {
            return tex("npc_miner");
        }

        if (prof == 2) {
            return tex("npc_lumberjack");
        }

        if (prof == 3) {
            return tex("npc_farmer");
        }

        VillagerProfession profession = npc.getVillagerData().getProfession();
        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);

        if (key != null) {
            String path = key.getPath();

            if (!path.equals("none") && !path.equals("nitwit")) {
                return tex("npc_" + path);
            }
        }

        return tex("npc");
    }

    public static ResourceLocation forWarrior() {
        return tex("npc_warrior");
    }

    public static ResourceLocation forArbalest() {
        return tex("npc_arbalest");
    }

    public static ResourceLocation forSummoner() {
        return tex("npc_summoner");
    }
}
