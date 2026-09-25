package ru.dez.client.entity;

import java.util.function.Function;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Monster;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Shared GeckoLib renderer for the RTS combatants. The model and animations are
 * the same for every combatant; only the texture differs, supplied as a
 * function (so warrior / arbalest / summoner each get their own skin).
 */
public class RTSRaiderRenderer<T extends Monster & GeoAnimatable> extends GeoEntityRenderer<T> {

    public RTSRaiderRenderer(EntityRendererProvider.Context context, Function<T, ResourceLocation> texture) {
        super(context, new RTSNpcModel<>(texture));
        this.shadowRadius = 0.5F;
    }
}
