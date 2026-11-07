package ru.pcmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import ru.pcmod.common.PacketAction;
import ru.pcmod.common.PacketNetConfig;
import ru.pcmod.common.PacketScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.pcmod.common.CommandBank;
import ru.pcmod.common.CommonProxy;
import ru.pcmod.common.ModBlockEntities;
import ru.pcmod.common.ModBlocks;
import ru.pcmod.common.ModItems;
import ru.pcmod.common.ModMenus;
import ru.pcmod.common.ModTabs;
import ru.pcmod.common.Net;
import ru.pcmod.common.Shop;
import ru.pcmod.common.TileComputer;

@Mod(PCMod.MODID)
public class PCMod {
    public static final String MODID = "pcmod";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static CommonProxy proxy;

    private static final String PROTOCOL = "1";
    /** Канал мода (было SimpleNetworkWrapper net). Обработчики выполняются в основном потоке через enqueueWork. */
    public static final SimpleChannel NET = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Клиент -> сервер. */
    public static void sendToServer(Object msg) {
        NET.sendToServer(msg);
    }

    public PCMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(bus);
        ModItems.register(bus);
        ModBlockEntities.register(bus);
        ModMenus.register(bus);
        ModTabs.register(bus);
        bus.addListener(this::commonSetup);
        // клиентская часть (этап 6): ClientProxy создаётся только на клиенте
        proxy = DistExecutor.safeRunForDist(() -> ru.pcmod.client.ClientProxy::new, () -> CommonProxy::new);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        Shop.setConfigDir(FMLPaths.CONFIGDIR.get().toFile());
        NET.registerMessage(0, PacketAction.class, PacketAction::encode, PacketAction::decode, PacketAction::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        NET.registerMessage(1, PacketScreen.class, PacketScreen::encode, PacketScreen::decode, PacketScreen::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        NET.registerMessage(2, PacketNetConfig.class, PacketNetConfig::encode, PacketNetConfig::decode, PacketNetConfig::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent e) {
        CommandBank.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void serverStarting(ServerStartingEvent e) {
        Net.clear();
        TileComputer.clearRegistry();
        Shop.load();
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent e) {
        Net.clear();
        TileComputer.clearRegistry();
    }
}
