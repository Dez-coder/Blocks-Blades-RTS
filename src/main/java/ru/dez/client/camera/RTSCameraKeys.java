package ru.dez.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RTSCameraKeys {

    public static final String CATEGORY = "key.categories.blocks_blades";

    public static KeyMapping TOGGLE_RTS_CAMERA;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        TOGGLE_RTS_CAMERA = new KeyMapping(
                "key.blocks_blades.toggle_rts_camera",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_F12,
                CATEGORY
        );
        event.register(TOGGLE_RTS_CAMERA);
    }
}