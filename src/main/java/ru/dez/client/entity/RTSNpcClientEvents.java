package ru.dez.client.entity;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.dez.blocks_blades;
import ru.dez.entity.RTSNpcRegistry;

/** Registers the GeckoLib renderers for the animated villagers and combatants. */
@Mod.EventBusSubscriber(modid = blocks_blades.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RTSNpcClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RTSNpcRegistry.NPC.get(), RTSNpcRenderer::new);

        event.registerEntityRenderer(RTSNpcRegistry.WARRIOR.get(),
                context -> new RTSRaiderRenderer<>(context, entity -> RTSSkins.forWarrior()));

        event.registerEntityRenderer(RTSNpcRegistry.ARBALEST.get(),
                context -> new RTSRaiderRenderer<>(context, entity -> RTSSkins.forArbalest()));

        event.registerEntityRenderer(RTSNpcRegistry.SUMMONER.get(),
                context -> new RTSRaiderRenderer<>(context, entity -> RTSSkins.forSummoner()));
    }
}
