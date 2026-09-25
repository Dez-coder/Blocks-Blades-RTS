package ru.dez.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSCameraRenderEvents {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RTSCameraManager.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (RTSCameraManager.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (RTSCameraManager.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (RTSCameraManager.isEnabled() && event.getNewScreen() instanceof InventoryScreen) {
            event.setCanceled(true);
        }
    }

    /** Hides the local player's body: in RTS mode it is just the chunk anchor. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        if (RTSCameraManager.isEnabled() && event.getEntity() == mc.player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (RTSCameraManager.isEnabled()) {
            float partialTick = (float) event.getPartialTick();
            event.setYaw(RTSCameraManager.getInterpolatedYaw(partialTick));
            event.setPitch(RTSCameraManager.getInterpolatedPitch(partialTick));
            event.setRoll(0.0F);
        }
    }
}