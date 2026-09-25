package ru.dez;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import ru.dez.client.RTSSelectionHandler;
import ru.dez.client.camera.RTSBuildings;
import ru.dez.client.camera.RTSConstructionManager;
import ru.dez.client.camera.RTSNetwork;
import ru.dez.client.camera.RTSCameraMode;
import ru.dez.client.camera.RTSProtectedData;
import ru.dez.client.camera.RTSSyncOwnedPacket;
import ru.dez.entity.RTSNpcRegistry;

import java.util.ArrayList;

@Mod(blocks_blades.MODID)
public class blocks_blades {

    public static final String MODID = "blocks_blades";
    private static final Logger LOGGER = LogUtils.getLogger();

    public blocks_blades() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        MinecraftForge.EVENT_BUS.register(new RTSSelectionHandler());

        modEventBus.addListener(this::commonSetup);

        // Animated GeckoLib villager entity.
        RTSNpcRegistry.init(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Restore everything that was saved to disk: unlocked buildings,
        // villager jobs, stockpiled resources and any construction site that
        // was still in progress when the world was last closed. Without
        // this the whole village would have to be rebuilt from scratch
        // every time the world is reloaded.
        RTSProtectedData data = RTSProtectedData.get(event.getServer());
        RTSBuildings.setCompleted(data.completed);
        RTSConstructionManager.restore(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Entity tags are not synced to clients, so a joining player has no
        // way of knowing which villagers belong to the RTS system. Push the
        // full owned-villager set so selection works immediately after login.
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // If the world was left while the RTS camera was active, the player
            // was saved parked high above the focus - put them back on the ground.
            RTSCameraMode.restoreOnLogin(player);

            RTSProtectedData data = RTSProtectedData.get(player.server);

            RTSNetwork.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RTSSyncOwnedPacket(new ArrayList<>(data.profs.keySet()))
            );
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }
}
