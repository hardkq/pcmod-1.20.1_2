package ru.pcmod.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Команда 'virgen' — генератор игровых вирусов для PCOS/PixelOS.
 *
 * Использование:
 *   virgen start TYPE SEVERITY [--complexity X] [--propagate N] [--stealth]
 *   virgen list    — доступные типы вирусов
 *   virgen status  — статус текущей генерации
 *   virgen info    — параметры оборудования и формулы времени
 *   virgen cancel  — отменить генерацию
 *   virgen id      — 9-значный номер этого компьютера
 *   virgen lib     — вирусы, хранящиеся на этом компьютере
 *   virgen disk    — вирусы на дискете в слоте Data
 *   virgen save N|all   — записать вирус(ы) из библиотеки на дискету
 *   virgen load N|all   — загрузить вирус(ы) с дискеты в библиотеку
 *   virgen delete N|all — удалить из библиотеки
 *   virgen wipe    — стереть дискету
 *   virgen hosts   — ПК, достижимые по сети прямо сейчас
 *   virgen send PCID N|all — отправить копию вируса(ов) на ПК по номеру через сеть
 *   virgen help    — справка
 */
public class VirusGeneratorCommand {

    /**
     * Активные генерации: по одной на каждый TileComputer.
     * Ключ — 9-значный номер компьютера (уникален в мире).
     */
    private static final Map<Integer, VirusGenerator.GenerationState> activeGenerations =
            new HashMap<Integer, VirusGenerator.GenerationState>();

    /** Точка входа — вызывается из Shell.exec(). */
    public static void execute(ComputerLogic l, String[] args) {
        Screen s = l.s;

        if (args.length == 0 || args[0].equals("help")) {
            printHelp(s);
            return;
        }

        String sub = args[0];
        if      (sub.equals("list"))   printVirusTypes(s);
        else if (sub.equals("info"))   printInfo(s, l);
        else if (sub.equals("status")) printStatus(s, l);
        else if (sub.equals("start"))  startGeneration(l, args);
        else if (sub.equals("cancel")) cancelGeneration(l);
        else if (sub.equals("id"))     s.println("Computer ID: " + l.t.getComputerId());
        else if (sub.equals("lib"))    printLibrary(l);
        else if (sub.equals("disk"))   printDisk(l);
        else if (sub.equals("save"))   saveToDisk(l, args);
        else if (sub.equals("load"))   loadFromDisk(l, args);
        else if (sub.equals("delete")) deleteFromLibrary(l, args);
        else if (sub.equals("wipe"))   wipeDisk(l);
        else if (sub.equals("hosts"))  printHosts(l);
        else if (sub.equals("send"))   sendOverNet(l, args);
        else err(s, "Unknown subcommand: " + sub + ". Type 'virgen help'.");
    }

    // ─────────────────────────── tick ───────────────────────────
    /**
     * Вызывается каждый тик логики компьютера (из ComputerLogic).
     * Обновляет прогресс и выводит результат при завершении.
     */
    public static void tick(ComputerLogic l) {
        int key = tileKey(l);
        VirusGenerator.GenerationState state = activeGenerations.get(key);
        if (state == null) return;

        long now = l.t.getLevel().getGameTime();
        boolean done = VirusGenerator.updateGeneration(state, now);

        if (done) {
            activeGenerations.remove(key);
            Virus v = state.generatedVirus;
            l.s.color = 10;
            l.s.println(">>> Вирус сгенерирован! <<<");
            l.s.color = 7;
            l.s.println("ID:       " + v.virusId);
            l.s.println("Type:     " + Virus.getTypeName(v.type));
            l.s.println("Severity: " + v.severity + "/100");
            l.s.println("Impact:   " + String.format("%.2f", v.impactMultiplier) + "x");
            l.s.println("Sig:      " + v.signature);
            l.s.println("Size:     " + v.sizeBytes + " bytes");
            v.originId = l.t.getComputerId();
            if (l.t.addVirus(v)) {
                l.s.println("Saved to library (" + l.t.viruses().size() + "/" + TileComputer.VIRUS_LIBRARY_MAX
                        + "). 'virgen save " + l.t.viruses().size() + "' writes it to a disk.");
            } else {
                err(l.s, "Library full: virus discarded. Use 'virgen delete'.");
            }
            l.prompt();
        }
    }

    // ─────────────────────────── subcommands ───────────────────────────
    private static void printHelp(Screen s) {
        s.println("virgen — генератор игровых вирусов");
        s.println("virgen start TYPE SEV  создать вирус");
        s.println("  --complexity X (0.1-2.0, def 1.0)");
        s.println("  --propagate N  (1-10, def 1)");
        s.println("  --stealth      скрытый режим");
        s.println("virgen list    типы вирусов");
        s.println("virgen info    параметры системы");
        s.println("virgen status  прогресс генерации");
        s.println("virgen cancel  отменить генерацию");
        s.println("--- диски и перенос ---");
        s.println("virgen id      номер этого ПК (9 цифр)");
        s.println("virgen lib     вирусы на этом ПК");
        s.println("virgen disk    вирусы на дискете (слот Data)");
        s.println("virgen save N|all   записать на дискету");
        s.println("virgen load N|all   загрузить с дискеты");
        s.println("virgen delete N|all удалить с этого ПК");
        s.println("virgen wipe    стереть дискету");
        s.println("--- сеть (кабель / роутер / Wi-Fi) ---");
        s.println("virgen hosts   ПК, видимые в сети сейчас");
        s.println("virgen send PCID N|all  копию на ПК по номеру");
        s.println("Выключенному ПК вирус придёт через сервер сети.");
    }

    private static void printVirusTypes(Screen s) {
        s.println("Типы вирусов:");
        s.println("0 FC  File Corrupted    - повреждает файлы");
        s.println("1 PD  Performance Drain - замедляет систему");
        s.println("2 DS  Disk Space        - заполняет диск");
        s.println("3 NH  Network Hijack    - перехват сети");
        s.println("4 SI  System Instability- нестабильность");
        s.println("5 ST  Stealth           - скрытый вирус");
    }

    private static void printInfo(Screen s, ComputerLogic l) {
        TileComputer t = l.t;
        PartSpec cpu = t.spec(TileComputer.CPU);
        int ramMB = t.totalRamMB();

        s.println("=== Virus Generator Info ===");
        s.println("Computer ID: " + t.getComputerId());
        s.println("Library: " + t.viruses().size() + "/" + TileComputer.VIRUS_LIBRARY_MAX
                + "   Disk: " + (t.dataDisk() == null ? "none"
                : VirusDisk.count(t.dataDisk()) + "/" + VirusDisk.CAPACITY));
        s.println("CPU: " + (cpu != null ? cpu.label + " " + cpu.cores + " cores" : "none"));
        s.println("RAM: " + ramMB + " MB total");
        s.println("Время генерации (min): 15 сек");
        s.println("Время генерации (max): 120 сек");
        s.println("Ускорение: +20% за каждое ядро CPU");
        s.println("Пример: virgen start 0 50");
    }

    private static void printStatus(Screen s, ComputerLogic l) {
        VirusGenerator.GenerationState state = activeGenerations.get(tileKey(l));
        if (state == null) {
            s.println("Нет активной генерации.");
            return;
        }
        long now = l.t.getLevel().getGameTime();
        VirusGenerator.updateGeneration(state, now);

        int pct  = (int)(state.progress * 100);
        long rem = Math.max(0, (state.estimatedEndTime - now) / 20);
        s.println("=== Статус генерации ===");
        s.println("Прогресс: " + pct + "%");
        s.println("Осталось: " + rem + " сек");
        s.println("Тип:      " + Virus.getTypeName(state.params.targetType));
        s.println("Опасность:" + state.params.targetSeverity + "/100");
    }

    private static void startGeneration(ComputerLogic l, String[] args) {
        Screen s = l.s;

        if (activeGenerations.containsKey(tileKey(l))) {
            err(s, "Генерация уже идёт. Используй 'virgen status'.");
            return;
        }
        if (args.length < 3) {
            err(s, "Использование: virgen start TYPE SEVERITY");
            return;
        }
        if (l.t.viruses().size() >= TileComputer.VIRUS_LIBRARY_MAX) {
            err(s, "Библиотека вирусов полна. Освободи место: 'virgen delete'.");
            return;
        }

        int type, severity;
        try {
            type     = Integer.parseInt(args[1]);
            severity = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            err(s, "TYPE и SEVERITY должны быть целыми числами.");
            return;
        }

        VirusGenerator.GenerationParams params = new VirusGenerator.GenerationParams(type, severity);

        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("--stealth")) {
                params.enableStealth = true;
            } else if (args[i].equals("--complexity") && i + 1 < args.length) {
                try { params.complexityFactor = Double.parseDouble(args[++i]); }
                catch (NumberFormatException e) { err(s, "complexity: нужно число."); return; }
            } else if (args[i].equals("--propagate") && i + 1 < args.length) {
                try { params.propagationAttempts = Integer.parseInt(args[++i]); }
                catch (NumberFormatException e) { err(s, "propagate: нужно число."); return; }
            }
        }

        if (!VirusGenerator.validateParams(params)) {
            err(s, "Неверные параметры. TYPE 0-5, SEVERITY 0-100.");
            return;
        }

        TileComputer t  = l.t;
        PartSpec cpu    = t.spec(TileComputer.CPU);
        int ramMB       = t.totalRamMB();
        long now        = t.getLevel().getGameTime();

        VirusGenerator.GenerationState state = VirusGenerator.beginGeneration(cpu, ramMB, params, now);
        activeGenerations.put(tileKey(l), state);

        s.color = 10;
        s.println(">>> Начало генерации <<<");
        s.color = 7;
        String desc = VirusGenerator.describeGenerationProcess(cpu, ramMB, params);
        for (String line : desc.split("\n")) s.println(line);
    }

    private static void cancelGeneration(ComputerLogic l) {
        if (activeGenerations.remove(tileKey(l)) != null) {
            l.s.color = 12;
            l.s.println("Генерация отменена.");
            l.s.color = 7;
        } else {
            l.s.println("Нет активной генерации.");
        }
    }

    // ─────────────────────────── диск и перенос ───────────────────────────
    private static String line(int n, Virus v) {
        // Stealth на заражённом ПК прячет своё имя (маскируется звёздочками)
        String id = v.incoming && v.type == Virus.TYPE_STEALTH
                ? "VIR_******_" + Virus.getTypePrefix(v.type) : v.virusId;
        return String.format("%2d %s %-18s %3d %s", n, id, Virus.getTypeName(v.type), v.severity,
                v.originId > 0 ? String.valueOf(v.originId) : "-");
    }

    private static void printList(Screen s, List<Virus> list) {
        s.println("#  ID                Type               Sev Origin PC");
        for (int i = 0; i < list.size(); i++) s.println(line(i + 1, list.get(i)));
    }

    private static void printLibrary(ComputerLogic l) {
        Screen s = l.s;
        List<Virus> lib = l.t.viruses();
        s.println("Computer " + l.t.getComputerId() + " - library " + lib.size() + "/" + TileComputer.VIRUS_LIBRARY_MAX);
        if (lib.isEmpty()) s.println("(empty) Create one: virgen start 0 50");
        else printList(s, lib);
    }

    /** Дискета в слоте Data или null (с сообщением об ошибке). */
    private static ItemStack needDisk(ComputerLogic l) {
        ItemStack d = l.t.dataDisk();
        if (d == null) {
            err(l.s, "Нет вирусной дискеты в слоте Data (панель железа: ПКМ по корпусу с дискетой в руке).");
        }
        return d;
    }

    private static void printDisk(ComputerLogic l) {
        ItemStack d = needDisk(l);
        if (d == null) return;
        List<Virus> list = VirusDisk.read(d);
        l.s.println("Disk: " + list.size() + "/" + VirusDisk.CAPACITY + " viruses");
        if (list.isEmpty()) l.s.println("(empty) Write one: virgen save 1");
        else printList(l.s, list);
    }

    /** "all" -> все индексы; число -> один индекс (1-based). null — ошибка (уже выведена). */
    private static List<Integer> pickIndexes(Screen s, String[] args, int size) {
        List<Integer> out = new ArrayList<Integer>();
        if (args.length < 2) {
            err(s, "Укажи номер или 'all': virgen " + args[0] + " N|all");
            return null;
        }
        if (args[1].equals("all")) {
            for (int i = 0; i < size; i++) out.add(i);
            return out;
        }
        try {
            int n = Integer.parseInt(args[1]);
            if (n < 1 || n > size) {
                err(s, "Нет вируса с номером " + n + " (есть 1-" + size + ").");
                return null;
            }
            out.add(n - 1);
            return out;
        } catch (NumberFormatException e) {
            err(s, "Номер должен быть числом или 'all'.");
            return null;
        }
    }

    /** Библиотека -> дискета. */
    private static void saveToDisk(ComputerLogic l, String[] args) {
        Screen s = l.s;
        ItemStack d = needDisk(l);
        if (d == null) return;
        List<Virus> lib = l.t.viruses();
        List<Integer> pick = pickIndexes(s, args, lib.size());
        if (pick == null) return;
        List<Virus> disk = VirusDisk.read(d);
        int added = 0, dup = 0, full = 0;
        for (int i : pick) {
            Virus v = lib.get(i);
            if (VirusDisk.contains(disk, v)) { dup++; continue; }
            if (disk.size() >= VirusDisk.CAPACITY) { full++; continue; }
            disk.add(v.copy());
            added++;
        }
        if (added > 0) {
            VirusDisk.write(d, disk);
            l.t.setChanged();
        }
        s.color = added > 0 ? 10 : 14;
        s.println("Записано на дискету: " + added + "  (на диске " + disk.size() + "/" + VirusDisk.CAPACITY + ")");
        s.color = 7;
        if (dup > 0) s.println("Уже были на дискете: " + dup);
        if (full > 0) err(s, "Дискета заполнена, не поместилось: " + full);
        if (added > 0) s.println("Вынь дискету и вставь в слот Data другого ПК: 'virgen load'.");
    }

    /** Дискета -> библиотека. */
    private static void loadFromDisk(ComputerLogic l, String[] args) {
        Screen s = l.s;
        ItemStack d = needDisk(l);
        if (d == null) return;
        List<Virus> disk = VirusDisk.read(d);
        List<Integer> pick = pickIndexes(s, args, disk.size());
        if (pick == null) return;
        List<Virus> lib = l.t.viruses();
        int added = 0, dup = 0, full = 0;
        for (int i : pick) {
            Virus v = disk.get(i);
            if (VirusDisk.contains(lib, v)) { dup++; continue; }
            if (!l.t.receiveVirus(v)) { full++; continue; }
            added++;
        }
        s.color = added > 0 ? 10 : 14;
        s.println("Загружено с дискеты: " + added + "  (в библиотеке " + lib.size() + "/" + TileComputer.VIRUS_LIBRARY_MAX + ")");
        s.color = 7;
        if (dup > 0) s.println("Уже есть на этом ПК: " + dup);
        if (full > 0) err(s, "Библиотека полна, не загружено: " + full);
    }

    private static void deleteFromLibrary(ComputerLogic l, String[] args) {
        Screen s = l.s;
        List<Virus> lib = l.t.viruses();
        List<Integer> pick = pickIndexes(s, args, lib.size());
        if (pick == null) return;
        // удаляем с конца, чтобы номера не сдвигались
        for (int k = pick.size() - 1; k >= 0; k--) l.t.removeVirus(pick.get(k));
        s.println("Удалено из библиотеки: " + pick.size());
    }

    private static void wipeDisk(ComputerLogic l) {
        ItemStack d = needDisk(l);
        if (d == null) return;
        int n = VirusDisk.count(d);
        VirusDisk.write(d, new ArrayList<Virus>());
        l.t.setChanged();
        l.s.println("Дискета стёрта (" + n + " вирусов).");
    }

    // ─────────────────────────── передача по сети ───────────────────────────
    private static String via(NetLink k) {
        return k.type == NetLink.WIFI ? "Wi-Fi" : "cable";
    }

    private static void printHosts(ComputerLogic l) {
        Screen s = l.s;
        NetLink k = l.link();
        if (!k.up()) {
            err(s, "Нет сети: " + k.status);
            return;
        }
        s.println("Мой номер: " + l.t.getComputerId() + " (" + via(k) + ")");
        int n = 0;
        for (TileComputer pc : TileComputer.loaded()) {
            ComputerLogic dl = pc.logic();
            if (dl == null || !dl.isOn() || pc == l.t) continue;
            NetLink dk = dl.link();
            if (!Net.sameNetwork(l.t, k, pc, dk)) continue;
            s.println(pc.getComputerId() + "  " + dl.host + "  (" + via(dk) + ")");
            n++;
        }
        if (n == 0) s.println("Других включённых ПК в сети нет.");
        int srv = 0;
        for (TileServer sv : k.servers) if (!sv.isRemoved()) srv++;
        s.println("Серверов в сети: " + srv + (srv > 0 ? " (доставка выключенным ПК)" : ""));
    }

    /** Сервер сети с местом в почтовом ящике или null. */
    private static TileServer mailServer(NetLink k) {
        for (TileServer sv : k.servers) {
            if (!sv.isRemoved() && sv.isUp() && sv.mailCount() < sv.mailCapacity()) return sv;
        }
        return null;
    }

    /** virgen send PCID N|all: копия вируса уходит по сети напрямую или через почтовый ящик сервера. */
    private static void sendOverNet(ComputerLogic l, String[] args) {
        Screen s = l.s;
        if (args.length < 3) {
            err(s, "Использование: virgen send PCID N|all");
            return;
        }
        int target;
        try {
            target = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            err(s, "PCID — это 9-значный номер ПК (см. 'virgen hosts').");
            return;
        }
        if (!ComputerIds.valid(target)) {
            err(s, "PCID должен состоять из 9 цифр.");
            return;
        }
        if (target == l.t.getComputerId()) {
            err(s, "Это номер самого этого ПК.");
            return;
        }
        NetLink k = l.link();
        if (!k.up()) {
            err(s, "Нет сети: " + k.status + ". Подключи кабель к роутеру или Wi-Fi.");
            return;
        }
        List<Virus> lib = l.t.viruses();
        List<Integer> pick = pickIndexes(s, new String[]{"send", args[2]}, lib.size());
        if (pick == null) return;
        if (pick.isEmpty()) {
            s.println("Библиотека пуста.");
            return;
        }
        if (!ComputerIds.get(l.t.getLevel()).has(target)) {
            err(s, "Неизвестный хост: ПК " + target + " не существует.");
            return;
        }

        TileComputer dst = TileComputer.byId(target);
        ComputerLogic dl = dst == null ? null : dst.logic();
        boolean direct = dl != null && dl.isOn() && Net.sameNetwork(l.t, k, dst, dl.link());
        if (!direct && mailServer(k) == null) {
            boolean anyServer = !k.servers.isEmpty();
            err(s, anyServer ? "Почтовый ящик сервера полон." :
                    "Нет маршрута до " + target + ": ПК выключен, не в этой сети или не загружен. "
                    + "Поставь в сеть сервер - он передаст вирус, когда ПК выйдет на связь.");
            return;
        }

        int direct_n = 0, queued = 0, dup = 0, fail = 0;
        String srvName = "";
        for (int i : pick) {
            Virus v = lib.get(i);
            if (dst != null && VirusDisk.contains(dst.viruses(), v)) { dup++; continue; }
            if (direct && dst.receiveVirus(v)) {
                direct_n++;
                dl.netNotice("[net] Входящий вирус от ПК " + l.t.getComputerId() + ": " + v.virusId
                        + ". Список: virgen lib");
                continue;
            }
            TileServer sv = mailServer(k);
            if (sv != null && sv.queueVirus(target, v.copy())) {
                queued++;
                srvName = sv.domain;
            } else {
                fail++;
            }
        }
        s.color = (direct_n + queued) > 0 ? 10 : 14;
        s.println("Отправка на ПК " + target + " (" + via(k) + "):");
        s.color = 7;
        if (direct_n > 0) s.println("  доставлено напрямую: " + direct_n);
        if (queued > 0) s.println("  на сервере " + srvName + ", придёт при выходе ПК в сеть: " + queued);
        if (dup > 0) s.println("  уже есть на этом ПК: " + dup);
        if (fail > 0) err(s, "  не доставлено (библиотека ПК / ящик сервера полны): " + fail);
    }

    /** Раз в пару секунд: забрать вирусы, ожидающие этот ПК на серверах его сети. */
    public static void pollMail(ComputerLogic l) {
        NetLink k = l.link();
        if (!k.up() || k.servers.isEmpty()) return;
        int id = l.t.getComputerId();
        for (TileServer sv : k.servers) {
            if (sv.isRemoved() || !sv.isUp() || !sv.hasMailFor(id)) continue;
            List<Virus> mail = sv.takeMail(id);
            int got = 0, dup = 0;
            List<Virus> back = new ArrayList<Virus>();
            for (Virus v : mail) {
                if (VirusDisk.contains(l.t.viruses(), v)) dup++;
                else if (l.t.receiveVirus(v)) got++;
                else back.add(v);
            }
            for (Virus v : back) sv.queueVirus(id, v); // библиотека полна - остаётся на сервере
            if (got > 0) l.netNotice("[net] С сервера " + sv.domain + " получено вирусов: " + got + ". Список: virgen lib");
            else if (!back.isEmpty()) l.netNotice("[net] Библиотека полна: " + back.size() + " вирусов ждут на сервере " + sv.domain);
        }
    }

    // ─────────────────────────── helpers ───────────────────────────
    private static void err(Screen s, String msg) {
        s.color = 12;
        s.println("Error: " + msg);
        s.color = 7;
    }

    private static int tileKey(ComputerLogic l) {
        return l.t.getComputerId();
    }
}
