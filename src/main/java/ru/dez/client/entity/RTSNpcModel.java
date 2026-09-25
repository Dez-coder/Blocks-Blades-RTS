package ru.dez.client.entity;

import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * One shared GeckoLib model for every RTS NPC (villagers and combatants).
 * The model/animation files are always the same; only the texture differs,
 * chosen by the given {@code texture} function.
 */
public class RTSNpcModel<T extends Entity & GeoAnimatable> extends GeoModel<T> {

    private final Function<T, ResourceLocation> texture;

    public RTSNpcModel(Function<T, ResourceLocation> texture) {
        this.texture = texture;
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return new ResourceLocation("blocks_blades", "geo/npc.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture.apply(animatable);
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return new ResourceLocation("blocks_blades", "animations/npc.animation.json");
    }
}
