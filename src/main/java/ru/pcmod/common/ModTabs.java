package ru.pcmod.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "pcmod");

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("pcmod", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.pcmod"))
        .icon(() -> new ItemStack(ModBlocks.COMPUTER[0].get()))
        .displayItems((params, out) -> {
            ModBlocks.BLOCK_ITEMS.getEntries().forEach(e -> out.accept(e.get()));
            ModItems.ITEMS.getEntries().forEach(e -> out.accept(e.get()));
        }).build());

    public static void register(IEventBus bus) { TABS.register(bus); }
    private ModTabs() {}
}
