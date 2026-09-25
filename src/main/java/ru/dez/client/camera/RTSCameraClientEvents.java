package ru.dez.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.dez.client.RTSSelectionHandler;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSCameraClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            RTSClientState.resetAll();
            RTSTaskClient.reset();
            RTSBuildClient.reset();
        }

        while (RTSCameraKeys.TOGGLE_RTS_CAMERA.consumeClick()) {
            RTSCameraManager.toggle();
        }

        RTSCameraManager.tick();
        RTSCameraManager.applyPlayerAnchor();
        RTSPlacementHandler.tick();
        RTSSelectionHandler.tick();
        RTSTaskClient.tick();
        RTSBuildClient.tick();
    }
}