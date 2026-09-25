package ru.dez.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dez.client.camera.RTSVillagerTags;

/**
 * Villagers panic near anything the VillagerHostilesSensor considers hostile,
 * and that includes Vindicators. RTS warriors are converted villagers, so they
 * must not terrify the village they protect. This makes the sensor ignore any
 * entity carrying the RTS warrior tag.
 */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {

    @Inject(method = "isClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void blocksBlades$ignoreWarriorClose(LivingEntity villager, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (RTSVillagerTags.isWarrior(target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isHostile", at = @At("HEAD"), cancellable = true, require = 0)
    private void blocksBlades$ignoreWarriorHostile(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (RTSVillagerTags.isWarrior(target)) {
            cir.setReturnValue(false);
        }
    }
}
