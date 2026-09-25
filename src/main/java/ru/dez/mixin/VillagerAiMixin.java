package ru.dez.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dez.client.camera.RTSVillagerTaskManager;

/**
 * Vanilla villagers run most of their movement/behaviour through the Brain
 * system (wandering, walking to a workstation/bed, gossiping, etc). That
 * Brain keeps ticking every frame no matter what the goalSelector/targetSelector
 * contain, which is why a villager given an RTS order could suddenly veer off
 * and walk wherever the vanilla brain wanted instead of following the order.
 *
 * While a villager has an active RTS task (see RTSVillagerTaskManager) we
 * cancel the vanilla AI step entirely, so only our own navigation/moveControl
 * calls are able to move the entity.
 */
@Mixin(Villager.class)
public abstract class VillagerAiMixin {

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void blocksBlades$suppressVanillaAiWhenControlled(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;

        if (RTSVillagerTaskManager.isControlled(self.getUUID())) {
            ci.cancel();
        }
    }
}
