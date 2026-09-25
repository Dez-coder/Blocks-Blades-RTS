package ru.dez.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ru.dez.entity.RTSNpc;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renders the RTS villager with the GeckoLib model and a per-profession skin. */
public class RTSNpcRenderer extends GeoEntityRenderer<RTSNpc> {

    public RTSNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new RTSNpcModel<>(RTSSkins::forVillager));
        this.shadowRadius = 0.4F;
    }
}
