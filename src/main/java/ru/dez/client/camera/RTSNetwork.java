package ru.dez.client.camera;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.MOD)
public class RTSNetwork {

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("blocks_blades", "rts"),
            () -> "1",
            "1"::equals,
            "1"::equals
    );

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            INSTANCE.registerMessage(0, RTSSpawnVillagersPacket.class, RTSSpawnVillagersPacket::encode, RTSSpawnVillagersPacket::new, RTSSpawnVillagersPacket::handle);
            INSTANCE.registerMessage(1, RTSVillagerCommandPacket.class, RTSVillagerCommandPacket::encode, RTSVillagerCommandPacket::new, RTSVillagerCommandPacket::handle);
            INSTANCE.registerMessage(2, RTSResourcePacket.class, RTSResourcePacket::encode, RTSResourcePacket::new, RTSResourcePacket::handle);
            INSTANCE.registerMessage(3, RTSBuildPacket.class, RTSBuildPacket::encode, RTSBuildPacket::new, RTSBuildPacket::handle);
            INSTANCE.registerMessage(4, RTSSiteAddPacket.class, RTSSiteAddPacket::encode, RTSSiteAddPacket::new, RTSSiteAddPacket::handle);
            INSTANCE.registerMessage(5, RTSSiteRemovePacket.class, RTSSiteRemovePacket::encode, RTSSiteRemovePacket::new, RTSSiteRemovePacket::handle);
            INSTANCE.registerMessage(6, RTSUnlockPacket.class, RTSUnlockPacket::encode, RTSUnlockPacket::new, RTSUnlockPacket::handle);
            INSTANCE.registerMessage(7, RTSProfPacket.class, RTSProfPacket::encode, RTSProfPacket::new, RTSProfPacket::handle);
            INSTANCE.registerMessage(8, RTSSyncOwnedPacket.class, RTSSyncOwnedPacket::encode, RTSSyncOwnedPacket::new, RTSSyncOwnedPacket::handle);
            INSTANCE.registerMessage(9, RTSProfSyncPacket.class, RTSProfSyncPacket::encode, RTSProfSyncPacket::new, RTSProfSyncPacket::handle);
            INSTANCE.registerMessage(10, RTSRequestSitesPacket.class, RTSRequestSitesPacket::encode, RTSRequestSitesPacket::new, RTSRequestSitesPacket::handle);
            INSTANCE.registerMessage(11, RTSUpgradeTownHallPacket.class, RTSUpgradeTownHallPacket::encode, RTSUpgradeTownHallPacket::new, RTSUpgradeTownHallPacket::handle);
            INSTANCE.registerMessage(12, RTSUpgradeStatePacket.class, RTSUpgradeStatePacket::encode, RTSUpgradeStatePacket::new, RTSUpgradeStatePacket::handle);
            INSTANCE.registerMessage(13, RTSTeamStatePacket.class, RTSTeamStatePacket::encode, RTSTeamStatePacket::new, RTSTeamStatePacket::handle);
            INSTANCE.registerMessage(14, RTSTeamActionPacket.class, RTSTeamActionPacket::encode, RTSTeamActionPacket::new, RTSTeamActionPacket::handle);
            INSTANCE.registerMessage(15, RTSTownHallPanelPacket.class, RTSTownHallPanelPacket::encode, RTSTownHallPanelPacket::new, RTSTownHallPanelPacket::handle);
            INSTANCE.registerMessage(16, RTSTownHallClickPacket.class, RTSTownHallClickPacket::encode, RTSTownHallClickPacket::new, RTSTownHallClickPacket::handle);
            INSTANCE.registerMessage(17, RTSTownHallHirePacket.class, RTSTownHallHirePacket::encode, RTSTownHallHirePacket::new, RTSTownHallHirePacket::handle);
            INSTANCE.registerMessage(18, RTSModePacket.class, RTSModePacket::encode, RTSModePacket::new, RTSModePacket::handle);
            INSTANCE.registerMessage(19, RTSPlacementsPacket.class, RTSPlacementsPacket::encode, RTSPlacementsPacket::new, RTSPlacementsPacket::handle);
        });
    }
}