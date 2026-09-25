package ru.dez.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ranged crossbowman ("арбалетчик"). Extends {@link Pillager} so all existing
 * RTS combat logic keeps working, but is rendered with the mod's own GeckoLib
 * model and the {@code npc_arbalest.png} skin.
 */
public class RTSArbalest extends Pillager implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RTSArbalest(EntityType<? extends Pillager> type, Level level) {
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
