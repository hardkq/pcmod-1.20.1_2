package ru.pcmod.common;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Заменяет GuiHandler: id GUI -> MenuType. Данные (BlockPos) идут в FriendlyByteBuf; фабрики menu(id, inv, buf) — этап 6. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "pcmod");

    public static final RegistryObject<MenuType<ContainerComputer>> COMPUTER = MENUS.register("computer", () -> IForgeMenuType.create(ContainerComputer::new));
    public static final RegistryObject<MenuType<ContainerScreen>>   SCREEN   = MENUS.register("screen",   () -> IForgeMenuType.create(ContainerScreen::new));
    public static final RegistryObject<MenuType<ContainerMiner>>    MINER    = MENUS.register("miner",    () -> IForgeMenuType.create(ContainerMiner::new));
    public static final RegistryObject<MenuType<ContainerServer>>   SERVER   = MENUS.register("server",   () -> IForgeMenuType.create(ContainerServer::new));
    public static final RegistryObject<MenuType<ContainerBlank>>    NET      = MENUS.register("net",      () -> IForgeMenuType.create(ContainerBlank::new));

    public static void register(IEventBus bus) { MENUS.register(bus); }
    private ModMenus() {}
}
