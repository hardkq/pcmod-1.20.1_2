package ru.pcmod.common;

import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "pcmod");

    private static Block[] blocks(RegistryObject<Block>[] a) { return Arrays.stream(a).map(RegistryObject::get).toArray(Block[]::new); }

    public static final RegistryObject<BlockEntityType<TileComputer>> COMPUTER = TYPES.register("computer",
        () -> BlockEntityType.Builder.of(TileComputer::new, blocks(ModBlocks.COMPUTER)).build(null));
    public static final RegistryObject<BlockEntityType<TileRouter>> ROUTER = TYPES.register("router",
        () -> BlockEntityType.Builder.of(TileRouter::new, blocks(ModBlocks.ROUTER)).build(null));
    public static final RegistryObject<BlockEntityType<TileServer>> SERVER = TYPES.register("server",
        () -> BlockEntityType.Builder.of(TileServer::new, blocks(ModBlocks.SERVER)).build(null));
    public static final RegistryObject<BlockEntityType<TileMiner>> MINER = TYPES.register("miner",
        () -> BlockEntityType.Builder.of(TileMiner::new, blocks(ModBlocks.MINER)).build(null));
    public static final RegistryObject<BlockEntityType<TileFiber>> FIBER = TYPES.register("fiber",
        () -> BlockEntityType.Builder.of(TileFiber::new, ModBlocks.FIBER.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileConnector>> CABLE = TYPES.register("cable",
        () -> BlockEntityType.Builder.of(TileConnector::new, ModBlocks.CABLE.get(), ModBlocks.VIDEO_CABLE.get()).build(null));

    public static void register(IEventBus bus) { TYPES.register(bus); }
    private ModBlockEntities() {}
}
