package ru.dez.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dez.client.camera.RTSVillagerTags;
import ru.dez.client.camera.RTSVillagerTaskManager;

/**
 * Vanilla villagers acquire a profession through the Brain system: when a
 * villager without a job stands near a workstation (POI) the
 * {@code AssignProfessionFromJobSite} activity calls
 * {@link Villager#setVillagerData(VillagerData)} to give it a profession, and
 * {@code ResetProfession} can clear it again. That would let our RTS villagers
 * silently pick up vanilla jobs (farmer, librarian, ...) and lose the job we
 * assigned them.
 *
 * This mixin blocks any profession change on villagers spawned by the RTS
 * system, unless the change is explicitly whitelisted by
 * {@link RTSVillagerTaskManager#setProfession(Villager, VillagerProfession)}.
 */
@Mixin(Villager.class)
public abstract class VillagerProfessionMixin {

    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    private void blocksBlades$blockVanillaProfession(VillagerData data, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;

        if (!RTSVillagerTags.isOwned(self)) {
            return;
        }

        if (RTSVillagerTaskManager.allowProfessionChange) {
            return;
        }

        VillagerProfession current = self.getVillagerData().getProfession();

        if (data.getProfession() != current) {
            ci.cancel();
        }
    }
}
