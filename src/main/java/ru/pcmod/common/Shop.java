package ru.pcmod.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Интернет-магазин. Общий пул товаров лежит в config/pcmod_shop.cfg (создаётся при первом запуске).
 * Каталог меняется: каждый период ротации из пула детерминированно выбирается несколько товаров.
 * У каждого товара есть лимит на игрока и общий запас на период; счётчики сбрасываются вместе с каталогом.
 */
public final class Shop {
    private static final Logger LOG = LogManager.getLogger("pcmod");
    public static final int MAX_SLOTS = 10;

    public static class Offer {
        public String key = "";
        public Item item;
        public int price, limit, stock;
        /** Название для экрана (ASCII). */
        public String name = "";
    }

    private static final List<Offer> POOL = new ArrayList<Offer>();
    /** Длительность периода (тики) и число товаров в каталоге. */
    public static long rotationTicks = 24000L;
    public static int slots = 8;

    private static File file;
    private static long cachedEpoch = Long.MIN_VALUE;
    private static List<Offer> cached = new ArrayList<Offer>();

    private Shop() { }

    /** Каталог конфигов: FMLPaths.CONFIGDIR.get().toFile() (вызывается из PCMod). */
    public static void setConfigDir(File dir) {
        file = new File(dir, "pcmod_shop.cfg");
    }

    // ===================== конфиг =====================
    static final String DEFAULTS =
            "# PC Mod: shop config. One offer per line:  <item registry name> <price in kons> <limit per player> <stock for everybody>\n"
            + "# Limit and stock are per catalog period; 0 = unlimited. Lines with unknown items are skipped.\n"
            + "# rotation = minutes of game time between catalog changes (20 min = 1 Minecraft day), slots = offers shown at once (1-10).\n"
            + "# Use /pcshop reload after editing (op), /pcshop refresh to force a new catalog.\n"
            + "rotation 20\n"
            + "slots 8\n"
            + "minecraft:coal 2 64 0\n"
            + "minecraft:redstone 3 64 0\n"
            + "minecraft:lapis_lazuli 3 64 0\n"
            + "minecraft:glowstone_dust 4 32 0\n"
            + "minecraft:gunpowder 5 32 0\n"
            + "minecraft:bread 3 32 0\n"
            + "minecraft:cooked_beef 5 32 0\n"
            + "minecraft:quartz 6 32 0\n"
            + "minecraft:slime_ball 8 16 0\n"
            + "minecraft:obsidian 12 16 0\n"
            + "minecraft:iron_ingot 14 32 0\n"
            + "minecraft:gold_ingot 14 16 0\n"
            + "minecraft:ender_pearl 20 8 0\n"
            + "minecraft:blaze_rod 18 8 0\n"
            + "minecraft:name_tag 30 2 10\n"
            + "minecraft:experience_bottle 40 4 24\n"
            + "minecraft:emerald 45 8 0\n"
            + "minecraft:golden_apple 60 3 0\n"
            + "minecraft:diamond 75 4 0\n"
            + "minecraft:saddle 90 1 5\n"
            + "pcmod:circuit 12 16 0\n"
            + "pcmod:install_disk 15 4 0\n"
            + "pcmod:cable 8 16 0\n"
            + "pcmod:cooler_stock 40 2 6\n"
            + "pcmod:hdd_250 35 2 6\n"
            + "pcmod:hdd 50 2 6\n"
            + "pcmod:cpu_solo 40 2 6\n"
            + "pcmod:ram_d3_1 30 2 8\n"
            + "pcmod:ram_d3_2 55 2 6\n"
            + "pcmod:psu_300 60 2 5\n"
            + "pcmod:mb_basic 80 2 5\n"
            + "pcmod:wifi_module 90 2 5\n"
            + "pcmod:gpu_1 70 2 5\n"
            + "pcmod:cpu_dual 90 2 4\n"
            + "pcmod:mb_plus 130 1 4\n"
            + "pcmod:psu_500 130 1 4\n"
            + "pcmod:ram_d4_4 120 2 4\n"
            + "pcmod:gpu_300 140 1 3\n"
            + "pcmod:router0 150 1 3\n"
            + "pcmod:ssd 160 1 3\n"
            + "pcmod:computer 180 1 3\n"
            + "pcmod:cpu_quad 220 1 3\n"
            + "pcmod:gpu_2 260 1 2\n"
            + "pcmod:nvme 400 1 2\n";

    private static void writeDefaults() {
        if (file == null) return;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                w.write(DEFAULTS);
            } finally {
                w.close();
            }
        } catch (Exception e) {
            LOG.warn("[pcmod] cannot write shop config: {}", e.toString());
        }
    }

    /** (Пере)читать конфиг. Вызывается при старте сервера и командой /pcshop reload. */
    public static synchronized void load() {
        POOL.clear();
        cachedEpoch = Long.MIN_VALUE;
        rotationTicks = 24000L;
        slots = 8;
        if (file != null && !file.exists()) writeDefaults();
        List<String> lines = new ArrayList<String>();
        try {
            if (file != null && file.exists()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                try {
                    String ln;
                    while ((ln = r.readLine()) != null) lines.add(ln);
                } finally {
                    r.close();
                }
            }
        } catch (Exception e) {
            LOG.warn("[pcmod] cannot read shop config: {}", e.toString());
        }
        if (lines.isEmpty()) {
            for (String ln : DEFAULTS.split("\n")) lines.add(ln);
        }
        for (String raw : lines) {
            String ln = raw.trim();
            if (ln.length() == 0 || ln.charAt(0) == '#') continue;
            String[] t = ln.split("\\s+");
            try {
                if (t[0].equals("rotation") && t.length >= 2) {
                    rotationTicks = Math.max(1, Math.min(10000, Integer.parseInt(t[1]))) * 1200L;
                } else if (t[0].equals("slots") && t.length >= 2) {
                    slots = Math.max(1, Math.min(MAX_SLOTS, Integer.parseInt(t[1])));
                } else if (t.length >= 3) {
                    addOffer(t);
                }
            } catch (NumberFormatException e) {
                LOG.warn("[pcmod] shop config: bad line '{}'", ln);
            }
        }
    }

    private static void addOffer(String[] t) {
        String name = t[0];
        // 1.12.2 допускал "name@meta"; в 1.20 меты нет: суффикс отбрасывается (старый конфиг с dye@4 нужно поправить вручную)
        int at = name.indexOf('@');
        if (at >= 0) name = name.substring(0, at);
        ResourceLocation rl = ResourceLocation.tryParse(name);
        Item o = rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
        if (o == null || o == Items.AIR) {
            LOG.warn("[pcmod] shop config: unknown item '{}' (skipped)", name);
            return;
        }
        Offer f = new Offer();
        f.item = o;
        f.key = t[0];
        f.price = Math.max(1, Integer.parseInt(t[1]));
        f.limit = Math.max(0, Integer.parseInt(t[2]));
        f.stock = t.length >= 4 ? Math.max(0, Integer.parseInt(t[3])) : 0;
        f.name = ascii(displayName(f));
        POOL.add(f);
    }

    private static String displayName(Offer f) {
        try {
            String n = new ItemStack(f.item).getHoverName().getString();
            if (n != null && n.length() > 0) return n;
        } catch (Exception e) {
            // имя возьмём из реестра
        }
        String n = f.key;
        int c = n.indexOf(':');
        return c >= 0 ? n.substring(c + 1) : n;
    }

    /**
     * Название товара для экрана ПК: убираем коды цвета (§x) и управляющие символы.
     * Буквы любых языков (кириллица, диакритика) остаются: экран передаёт их как есть, поэтому
     * раньше русские названия предметов превращались в строки из '?'.
     */
    private static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') {
                i++;
                continue;
            }
            if (c < 32 || (c >= 127 && c < 160) || Character.isSurrogate(c)) continue;
            b.append(c);
        }
        return b.toString().trim();
    }

    public static int poolSize() {
        return POOL.size();
    }

    // ===================== время и каталог =====================
    private static ServerLevel main() {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        return srv == null ? null : srv.overworld();
    }

    /** Номер текущего периода ротации. */
    public static long epoch() {
        ServerLevel w = main();
        return w == null ? 0L : w.getGameTime() / rotationTicks;
    }

    /** Идентификатор текущего каталога (период + принудительные обновления). Клиентский экран хранит его при показе. */
    public static long stamp() {
        return epoch() * 1000L + (salt % 1000L);
    }

    /** Сколько секунд осталось до смены каталога. */
    public static int secondsLeft() {
        ServerLevel w = main();
        if (w == null) return 0;
        long left = rotationTicks - (w.getGameTime() % rotationTicks);
        return (int) ((left + 19L) / 20L);
    }

    /** Принудительно перейти к следующему каталогу (команда /pcshop refresh): сдвигает «соль» выбора. */
    private static long salt;

    public static synchronized void reroll() {
        salt++;
        cachedEpoch = Long.MIN_VALUE;
        Bank b = Bank.get();
        if (b != null) b.roll(-1L - salt);
    }

    /** Текущий каталог. Для одного и того же периода всегда одинаков (зависит от сида мира). */
    public static synchronized List<Offer> catalog() {
        long ep = epoch();
        if (ep == cachedEpoch) return cached;
        cachedEpoch = ep;
        ServerLevel w = main();
        List<Offer> idx = new ArrayList<Offer>(POOL);
        long seed = (w == null ? 0L : w.getSeed()) ^ (ep * 0x9E3779B97F4A7C15L) ^ (salt * 0x632BE59BD9B4E019L);
        Collections.shuffle(idx, new Random(seed));
        List<Offer> out = new ArrayList<Offer>(idx.subList(0, Math.min(slots, idx.size())));
        Collections.sort(out, new Comparator<Offer>() {
            @Override
            public int compare(Offer a, Offer b) {
                if (a.price != b.price) return a.price < b.price ? -1 : 1;
                return a.name.compareTo(b.name);
            }
        });
        cached = out;
        Bank bank = Bank.get();
        if (bank != null) bank.roll(ep);
        return cached;
    }

    /** Сколько ещё может купить этот игрок (Integer.MAX_VALUE = без лимита). */
    public static int playerLeft(Bank b, String id, Offer o) {
        if (o.limit == 0) return Integer.MAX_VALUE;
        return Math.max(0, o.limit - b.boughtBy(id, o.key));
    }

    /** Остаток общего запаса (Integer.MAX_VALUE = без ограничения). */
    public static int stockLeft(Bank b, Offer o) {
        if (o.stock == 0) return Integer.MAX_VALUE;
        return Math.max(0, o.stock - b.soldTotal(o.key));
    }

    // ===================== покупка =====================
    /**
     * Покупка товара с номером index из каталога с отпечатком epochSeen (см. stamp()). qty <= 0 — максимум.
     * Всё проверяется на сервере: каталог, лимит игрока, запас, баланс.
     */
    public static synchronized Bank.Res buy(Player p, int index, int qty, long epochSeen) {
        Bank b = Bank.get();
        if (b == null) return new Bank.Res(false, "Bank is unavailable");
        List<Offer> cat = catalog();
        if (epochSeen != stamp()) return new Bank.Res(false, "Catalog has just changed - check it again");
        if (index < 0 || index >= cat.size()) return new Bank.Res(false, "Select an item first");
        Offer o = cat.get(index);
        String id = p.getUUID().toString();
        b.rename(id, p.getGameProfile().getName());
        int pl = playerLeft(b, id, o), st = stockLeft(b, o);
        if (st <= 0) return new Bank.Res(false, "Sold out until the next catalog");
        if (pl <= 0) return new Bank.Res(false, "Your limit for this item is reached");
        long bal = b.balance(id);
        int canPay = (int) Math.min(1000000L, bal / o.price);
        int allowed = Math.min(Math.min(pl, st), 1000000);
        if (qty <= 0) {
            qty = Math.min(allowed, canPay);
            if (qty <= 0) return new Bank.Res(false, "Not enough kons (need " + Bank.fmt(o.price) + ")");
        } else {
            if (qty > allowed) qty = allowed;
            if (canPay < qty) return new Bank.Res(false, "Not enough kons (need " + Bank.fmt((long) o.price * qty) + ")");
        }
        long cost = (long) o.price * qty;
        b.setBalance(id, bal - cost);
        b.addPurchase(id, o.key, qty);
        b.log(id, "-" + Bank.fmt(cost) + " " + qty + "x " + o.name);
        giveItems(p, o, qty);
        return new Bank.Res(true, "Bought " + qty + "x " + o.name + " for " + Bank.fmt(cost));
    }

    static void giveItems(Player p, Offer o, int qty) {
        int max = Math.max(1, new ItemStack(o.item).getMaxStackSize());
        while (qty > 0) {
            int c = Math.min(qty, max);
            Bank.give(p, new ItemStack(o.item, c));
            qty -= c;
        }
    }
}
