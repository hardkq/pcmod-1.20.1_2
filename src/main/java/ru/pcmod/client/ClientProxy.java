package ru.pcmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.pcmod.common.CommonProxy;
import ru.pcmod.common.ModBlockEntities;
import ru.pcmod.common.ModMenus;

/** Клиентская часть: экраны меню, рендереры блок-сущностей, предпросмотр провода. Создаётся в PCMod только на клиенте. */
public class ClientProxy extends CommonProxy {
    public ClientProxy() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::clientSetup);
        bus.addListener(this::registerRenderers);
        // предпросмотр провода (RenderLevelStageEvent и оверлей прицела)
        MinecraftForge.EVENT_BUS.register(new WirePreview());
    }

    private void clientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            MenuScreens.register(ModMenus.COMPUTER.get(), GuiHardware::new);
            MenuScreens.register(ModMenus.SCREEN.get(), GuiMonitor::new);
            MenuScreens.register(ModMenus.MINER.get(), GuiMiner::new);
            MenuScreens.register(ModMenus.SERVER.get(), GuiNetDevice::new);
            MenuScreens.register(ModMenus.NET.get(), GuiNetDevice::new);
        });
    }

    /** Экран монитора, светодиод, железо и стекло корпуса (RenderComputer), провода (WireRenderer). */
    private void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ModBlockEntities.COMPUTER.get(), ctx -> new RenderComputer());
        e.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), ctx -> new WireRenderer());
    }

    @Override
    public Level clientWorld() {
        return Minecraft.getInstance().level;
    }

    /** Пакеты приходят в сетевом потоке; всё, что трогает мир и экран, выполняем в главном потоке клиента. */
    @Override
    public void runOnClientThread(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
