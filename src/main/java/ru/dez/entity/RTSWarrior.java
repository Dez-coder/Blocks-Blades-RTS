package ru.dez.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Melee warrior ("поборник"). Extends {@link Vindicator} so all existing RTS
 * combat logic keeps working, but is rendered with the mod's own GeckoLib model
 * and the {@code npc_warrior.png} skin.
 */
public class RTSWarrior extends Vindicator implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RTSWarrior(EntityType<? extends Vindicator> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(RTSRaiderAnims.controller(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
