package ru.pcmod.common;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.pcmod.common.PartSpec.Cat;

/**
 * Предметы (не блоки). Список деталей и PartSpec взяты из ModItems 1.12.2 без изменений; id совпадают с именами
 * из прежнего registerItems (рецепты, модели, lang). Купюры - 7 отдельных предметов bill_1..bill_1000 (раньше метадата).
 * Предметы-блоки регистрирует {@link ModBlocks}. Вкладка творчества - {@link ModTabs}.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "pcmod");

    /** Детали по id в порядке регистрации. */
    public static final Map<String, RegistryObject<Item>> PART = new LinkedHashMap<String, RegistryObject<Item>>();
    /** Характеристики деталей по id. */
    public static final Map<String, PartSpec> SPEC = new LinkedHashMap<String, PartSpec>();

    public static final RegistryObject<Item> CIRCUIT = ITEMS.register("circuit", () -> new Item(new Item.Properties()));

    private static void reg(String id, PartSpec spec) {
        SPEC.put(id, spec);
        PART.put(id, ITEMS.register(id, () -> new ItemPart(new Item.Properties().stacksTo(1), spec)));
    }

    static {
        reg("mb_basic", new PartSpec(Cat.MOTHERBOARD, "PCM-A100").socket(1).ramType(1).ramSlots(2));
        reg("mb_plus", new PartSpec(Cat.MOTHERBOARD, "PCM-B200").socket(1).ramType(1).ramSlots(4));
        reg("mb_mid", new PartSpec(Cat.MOTHERBOARD, "PCM-X300").socket(2).ramType(2).ramSlots(2));
        reg("mb_pro", new PartSpec(Cat.MOTHERBOARD, "PCM-X500").socket(2).ramType(2).ramSlots(4));
        reg("mb_elite", new PartSpec(Cat.MOTHERBOARD, "PCM-Z900").socket(3).ramType(3).ramSlots(4));
        reg("cpu_solo", new PartSpec(Cat.CPU, "Solo S1").socket(1).cores(1).ghz(1.6).watts(35));
        reg("cpu_dual", new PartSpec(Cat.CPU, "Duo C2").socket(1).cores(2).ghz(2.0).watts(65));
        reg("cpu_tri", new PartSpec(Cat.CPU, "Tri T3").socket(1).cores(3).ghz(2.6).watts(80));
        reg("cpu_quad", new PartSpec(Cat.CPU, "Quad Q4").socket(1).cores(4).ghz(3.0).watts(95));
        reg("cpu_hexa", new PartSpec(Cat.CPU, "Hexa X6").socket(2).cores(6).ghz(3.6).watts(105));
        reg("cpu_octa", new PartSpec(Cat.CPU, "Octa X8").socket(2).cores(8).ghz(4.2).watts(125));
        reg("cpu_deca", new PartSpec(Cat.CPU, "Deca Z10").socket(3).cores(10).ghz(4.8).watts(140));
        reg("cpu_dodeca", new PartSpec(Cat.CPU, "Dodeca Z12").socket(3).cores(12).ghz(5.2).watts(170));
        reg("cooler_stock", new PartSpec(Cat.COOLER, "Stock Cooler").cooling(70));
        reg("cooler_tower", new PartSpec(Cat.COOLER, "Tower Cooler").cooling(160));
        reg("cooler_liquid", new PartSpec(Cat.COOLER, "Liquid Cooler").cooling(260));
        reg("ram_d3_1", new PartSpec(Cat.RAM, "DDR3 1 GB").ramType(1).memMB(1024));
        reg("ram_d3_2", new PartSpec(Cat.RAM, "DDR3 2 GB").ramType(1).memMB(2048));
        reg("ram_d3_4", new PartSpec(Cat.RAM, "DDR3 4 GB").ramType(1).memMB(4096));
        reg("ram_d4_2", new PartSpec(Cat.RAM, "DDR4 2 GB").ramType(2).memMB(2048));
        reg("ram_d4_4", new PartSpec(Cat.RAM, "DDR4 4 GB").ramType(2).memMB(4096));
        reg("ram_d4_8", new PartSpec(Cat.RAM, "DDR4 8 GB").ramType(2).memMB(8192));
        reg("ram_d4_16", new PartSpec(Cat.RAM, "DDR4 16 GB").ramType(2).memMB(16384));
        reg("ram_d5_8", new PartSpec(Cat.RAM, "DDR5 8 GB").ramType(3).memMB(8192));
        reg("ram_d5_16", new PartSpec(Cat.RAM, "DDR5 16 GB").ramType(3).memMB(16384));
        reg("ram_d5_32", new PartSpec(Cat.RAM, "DDR5 32 GB").ramType(3).memMB(32768));
        reg("gpu_1", new PartSpec(Cat.GPU, "GT-100").tier(1).vram(256).watts(40).hash(2));
        reg("gpu_300", new PartSpec(Cat.GPU, "GT-300").tier(2).vram(512).watts(60).hash(6));
        reg("gpu_2", new PartSpec(Cat.GPU, "GT-500").tier(2).vram(1024).watts(90).hash(12));
        reg("gpu_700", new PartSpec(Cat.GPU, "GT-700").tier(3).vram(2048).watts(120).hash(24));
        reg("gpu_3", new PartSpec(Cat.GPU, "GT-900").tier(3).vram(4096).watts(160).hash(40));
        reg("gpu_1200", new PartSpec(Cat.GPU, "GX-1200").tier(3).vram(8192).watts(220).hash(70));
        reg("gpu_2000", new PartSpec(Cat.GPU, "GX-2000").tier(3).vram(16384).watts(300).hash(120));
        reg("hdd_250", new PartSpec(Cat.STORAGE, "HDD 250").storageKB(48).speed(1).hdd());
        reg("hdd", new PartSpec(Cat.STORAGE, "HDD 500").storageKB(64).speed(1).hdd());
        reg("hdd_1000", new PartSpec(Cat.STORAGE, "HDD 1000").storageKB(128).speed(1).hdd());
        reg("hdd_2000", new PartSpec(Cat.STORAGE, "HDD 2000").storageKB(256).speed(1).hdd());
        reg("hdd_4000", new PartSpec(Cat.STORAGE, "HDD 4000").storageKB(512).speed(2).hdd());
        reg("hdd_8000", new PartSpec(Cat.STORAGE, "HDD 8000 Enterprise").storageKB(768).speed(2).hdd());
        reg("ssd", new PartSpec(Cat.STORAGE, "SSD 2000").storageKB(256).speed(3));
        reg("nvme", new PartSpec(Cat.STORAGE, "NVMe X").storageKB(1024).speed(6));
        reg("psu_300", new PartSpec(Cat.PSU, "PSU 300W").watts(300));
        reg("psu_500", new PartSpec(Cat.PSU, "PSU 500W").watts(500));
        reg("psu_750", new PartSpec(Cat.PSU, "PSU 750W").watts(750));
        reg("psu_1000", new PartSpec(Cat.PSU, "PSU 1000W").watts(1000));
        reg("install_disk", new PartSpec(Cat.MEDIA, "PCOS Installer"));
        reg("install_disk_pixel", new PartSpec(Cat.MEDIA, "PixelOS Installer").os("pixel"));
        reg("virus_disk", new PartSpec(Cat.DATA, "Virus Disk"));
        reg("wifi_module", new PartSpec(Cat.NET, "Wi-Fi Module"));
    }

    /** Купюры «Конс» по номиналам {@link Bank#DENOM}: bill_1, bill_5, ... bill_1000. */
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] BILLS = new RegistryObject[Bank.DENOM.length];
    static {
        for (int i = 0; i < Bank.DENOM.length; i++) {
            final int idx = i;
            BILLS[i] = ITEMS.register("bill_" + Bank.DENOM[i], () -> new ItemBill(new Item.Properties(), idx));
        }
    }

    // провода между коннекторами (провисают дугой, как в Create: Power Grid)
    public static final RegistryObject<Item> WIRE_NET     = ITEMS.register("wire_net",     () -> new ItemWire(new Item.Properties(), Ports.NET));
    public static final RegistryObject<Item> WIRE_VIDEO   = ITEMS.register("wire_video",   () -> new ItemWire(new Item.Properties(), Ports.VIDEO));
    public static final RegistryObject<Item> CABLE_PLIERS = ITEMS.register("cable_pliers", () -> new ItemPliers(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FIBER_SPOOL  = ITEMS.register("fiber_spool",  () -> new ItemFiberSpool(new Item.Properties().stacksTo(1)));

    /** Деталь по id (например "nvme", "mb_basic"). */
    public static Item part(String id) {
        return PART.get(id).get();
    }

    public static Item bill(int denomIndex) {
        return BILLS[Math.max(0, Math.min(BILLS.length - 1, denomIndex))].get();
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }
    private ModItems() {}
}
