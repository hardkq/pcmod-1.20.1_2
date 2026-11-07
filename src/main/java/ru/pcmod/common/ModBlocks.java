package ru.pcmod.common;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Метаданные 1.12.2 (computer0-4, monitor0-4, router0-1, server0-1, miner0-1) -> отдельные блоки; type = бывшая meta. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "pcmod");
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "pcmod");

    public static final RegistryObject<Block>[] COMPUTER = arr(5, i -> "computer" + (i == 0 ? "" : i), (t) -> new BlockComputer(t));
    public static final RegistryObject<Block>[] MONITOR  = arr(5, i -> "monitor" + i, (t) -> new BlockMonitor(t));
    public static final RegistryObject<Block>[] ROUTER   = arr(2, i -> "router" + i,  (t) -> new BlockRouter(t));
    public static final RegistryObject<Block>[] SERVER   = arr(2, i -> "server" + i,  (t) -> new BlockServer(t));
    public static final RegistryObject<Block>[] MINER    = arr(2, i -> "miner" + i,   (t) -> new BlockMiner(t));

    public static final RegistryObject<Block> CABLE        = reg("cable",        () -> new BlockCable());
    public static final RegistryObject<Block> VIDEO_CABLE  = reg("video_cable",  () -> new BlockVideoCable());
    public static final RegistryObject<Block> FIBER        = reg("fiber0",       () -> new BlockFiber());
    public static final RegistryObject<Block> CHAMBER_WALL  = reg("chamber_wall",  () -> new BlockChamberWall());
    public static final RegistryObject<Block> CHAMBER_GLASS = reg("chamber_glass", () -> new BlockChamberGlass());
    public static final RegistryObject<Block> CHILLER       = reg("chiller",       () -> new BlockChiller());

    private static RegistryObject<Block> reg(String name, Supplier<Block> s) {
        RegistryObject<Block> b = BLOCKS.register(name, s);
        BLOCK_ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    @SuppressWarnings("unchecked")
    private static RegistryObject<Block>[] arr(int n, java.util.function.IntFunction<String> name, Function<Integer, Block> f) {
        RegistryObject<Block>[] a = new RegistryObject[n];
        for (int i = 0; i < n; i++) { final int t = i; a[i] = reg(name.apply(i), () -> f.apply(t)); }
        return a;
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); BLOCK_ITEMS.register(bus); }
    private ModBlocks() {}
}
