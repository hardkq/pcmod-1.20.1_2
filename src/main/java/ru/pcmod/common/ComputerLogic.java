package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/**
 * "Процессор" компьютера: питание, POST, BIOS, загрузка, установщик ОС, логин и оболочка.
 * Работает только на сервере; клавиши приходят пакетами, экран уходит клиенту.
 */
public class ComputerLogic {
    public enum St { OFF, POST, BIOS, NOBOOT, INSTALL, BOOT, CLEAN, PANIC, LOGIN, SHELL, DESKTOP, HALTED }

    static final int K_BACK = 14, K_ENTER = 28, K_NENTER = 156, K_F2 = 60, K_F10 = 68,
            K_UP = 200, K_LEFT = 203, K_RIGHT = 205, K_DOWN = 208, K_DEL = 211;
    static final String[] TABS = {"Main", "Boot", "Monitor", "Exit"};

    final TileComputer t;
    final Screen s;
    Desktop desk;
    /** Мессенджер PCOS (общий для окна Chat и команды chat). */
    public final ChatClient chat = new ChatClient(this);
    private NetLink link;
    private long linkAt = -1000L;
    private int chatTimer;
    St st = St.OFF;
    int timer, step, thermal, memRow, total, lastPct;

    // ввод строки
    final StringBuilder lb = new StringBuilder();
    boolean lmask;
    int lmax;

    // BIOS
    int tab, sel, tmpOrder;
    boolean tmpFast;

    // установщик
    String instHost = "", instUser = "", instPass = "", instErr = "";
    /** Какую ОС ставит установщик ("pcos" или "pixel") и чего не хватает ПК для неё. */
    String instOs = "pcos";
    final List<String> instProblems = new ArrayList<String>();
    /** Почему установленная PixelOS не стартует на этом ПК. */
    final List<String> bootProblems = new ArrayList<String>();

    // требования PixelOS (заметно выше, чем у рабочего стола PCOS)
    static final int PIX_CORES = 6, PIX_RAM_MB = 8192, PIX_VRAM_MB = 2048, PIX_GPU_TIER = 3, PIX_DISK_KB = 128;

    // сессия ОС
    String user = "", host = "pcos", cwd = "/", loginUser = "";
    int loginStage, attempts, lock;
    long bootTime;
    String editPath;
    boolean editOverwrite;
    StringBuilder editBuf;

    // ===================== кто за клавиатурой =====================
    /** Игрок, чьё действие обрабатывается прямо сейчас (null вне обработки). */
    public net.minecraft.world.entity.player.Player actor;
    /** Последний игрок, работавший за ПК: его счёт показывают Bank и Shop. */
    public String actorId = "", actorName = "";

    public void setActor(net.minecraft.world.entity.player.Player p) {
        actor = p;
        if (p != null) {
            actorId = p.getUUID().toString();
            actorName = p.getGameProfile().getName();
        }
    }

    public void clearActor() {
        actor = null;
    }

    public ComputerLogic(TileComputer t) {
        this.t = t;
        this.s = t.screen;
    }

    public boolean isOn() {
        return st != St.OFF;
    }

    CompoundTag root() {
        return VFS.root(t.getItem(TileComputer.STORAGE));
    }

    /** Установлена ли на накопителе PixelOS. */
    boolean isPixel() {
        return VFS.osId(root()).equals("pixel");
    }

    /** Название ОС на накопителе. */
    String osName() {
        return VFS.osName(root());
    }

    /** Название ОС, которую ставит установщик. */
    String instName() {
        return instOs.equals("pixel") ? "PixelOS" : "PCOS";
    }

    /** Чего не хватает ПК для PixelOS (пустой список - всё в порядке). needDisk - проверять размер накопителя (для установки). */
    List<String> pixelProblems(boolean needDisk) {
        List<String> p = new ArrayList<String>();
        PartSpec cpu = t.spec(TileComputer.CPU), gpu = t.spec(TileComputer.GPU), sto = t.spec(TileComputer.STORAGE);
        int cores = cpu == null ? 0 : cpu.cores;
        if (cores < PIX_CORES) p.add("CPU: " + PIX_CORES + "+ cores (have " + cores + ")");
        int ram = t.totalRamMB();
        if (ram < PIX_RAM_MB) p.add("RAM: " + PIX_RAM_MB + " MB (have " + ram + ")");
        if (gpu == null || gpu.tier < PIX_GPU_TIER || gpu.vram < PIX_VRAM_MB)
            p.add("GPU: " + PIX_VRAM_MB + " MB VRAM, GT-700 or better");
        if (needDisk && (sto == null || sto.storageKB < PIX_DISK_KB))
            p.add("Drive: " + PIX_DISK_KB + " KB or more (HDD 1000+)");
        return p;
    }

    // ===================== последствия вирусов =====================
    /** Какие предупреждения уже показаны (бит на тип вируса, бит 8 - «диск заполнен»). */
    int fxSeen;
    boolean fxNetDown;
    long fxGlitchAt;
    /** Всплывающее красное сообщение на рабочем столе. */
    String fxToast = "";
    long fxToastUntil;

    /** Сообщение о проблеме: на рабочем столе красная полоса на 5 секунд, в терминале строка при следующем вводе. */
    void fxNotice(String msg) {
        if (st == St.DESKTOP && desk != null) {
            fxToast = msg;
            fxToastUntil = t.getLevel().getGameTime() + 100;
            desk.draw();
        } else {
            netNotice(msg);
        }
    }

    /** Критический сбой (kernel panic): экран ошибки, затем перезагрузка. Несохранённые окна пропадают. */
    void panic(String why) {
        chat.logout();
        link = null;
        st = St.PANIC;
        timer = 0;
        desk = null;
        editPath = null;
        avAlertShown = false;
        s.color = 7;
        s.clear();
        s.text(0, 0, pad(" KERNEL PANIC", Screen.W), 15 | 16);
        s.text(2, 2, "Критическая ошибка системы.", 12);
        s.text(2, 3, "Причина: " + (why.length() > 50 ? why.substring(0, 50) : why), 14);
        s.text(2, 5, "Несохранённые данные потеряны.", 7);
        s.text(2, 7, "Перезагрузка через 3 секунды...", 7);
        s.dirty = true;
    }

    // ===================== антивирус =====================
    /** Окно антивируса уже показано (повторно не открываем, пока угроза не обработана). */
    boolean avAlertShown;
    private int avTimer;

    /** Антивирус нашёл вирус: окно на рабочем столе или сообщение в терминале. */
    void avRaise() {
        if (st == St.DESKTOP && desk != null) {
            desk.openAvAlert();
        } else {
            netNotice("[AV] Неизвестная программа была найдена у вас в компьютере.");
            netNotice("[AV] av remove - удалить (перезапуск), av wait - ждать");
        }
    }

    /** Экран удаления угроз после перезапуска (не дольше 11 секунд). */
    void startClean() {
        st = St.CLEAN;
        step = 0;
        timer = 0;
        total = Antivirus.cleanTicks(Antivirus.threats(t, true));
        s.clear();
        drawClean();
    }

    private void drawClean() {
        s.clear();
        s.text(0, 0, pad(" Антивирус - удаление угроз", Screen.W), 15 | 16);
        if (step == 0) {
            int pct = Math.min(100, timer * 100 / Math.max(1, total));
            java.util.List<Virus> vs = t.viruses();
            String name = "программа";
            int k = 0;
            for (Virus v : vs) {
                if (!v.incoming) continue;
                if (k++ == (timer / 20) % Math.max(1, Antivirus.threats(t, true))) {
                    name = v.virusId;
                    break;
                }
            }
            s.text(2, 2, "Обнаружена вредоносная программа.", 12);
            s.text(2, 3, "Не выключайте компьютер!", 14);
            s.text(2, 5, pad("Удаляется: " + name, Screen.W - 4), 7);
            s.text(2, 7, bar(pct, 40) + " " + pct + "%  ", 10);
            int left = Math.max(0, (total - timer + 19) / 20);
            s.text(2, 9, pad("Осталось: " + left + " сек", Screen.W - 4), 7);
        } else {
            s.text(2, 2, "Удаление завершено.", 10);
            s.text(2, 4, "Удалено угроз: " + cleanRemoved, 7);
            s.text(2, 6, "Загрузка системы...", 7);
        }
    }

    private int cleanRemoved;

    private void tickClean() {
        timer++;
        if (step == 0) {
            if (timer % 2 == 0) drawClean();
            if (timer >= total) {
                cleanRemoved = Antivirus.finishClean(this);
                step = 1;
                timer = 0;
                drawClean();
            }
        } else if (timer >= 30) {
            startLogin();
        }
    }

    // ===================== входящие вирусы =====================
    private final List<String> notices = new ArrayList<String>();
    private int mailTimer;

    /** Сообщение от сети; выводится в терминал, как только пользователь не печатает команду. */
    public void netNotice(String msg) {
        if (notices.size() >= 5) notices.remove(0);
        notices.add(msg);
    }

    private void flushNotices() {
        if (notices.isEmpty() || st != St.SHELL || editPath != null || lb.length() > 0) return;
        s.println("");
        s.color = 14;
        for (String n : notices) s.println(n);
        s.color = 7;
        notices.clear();
        prompt();
    }

    // ===================== сеть =====================
    /** Текущее подключение ПК (кабель или Wi-Fi); кэшируется на секунду. У выключенного ПК сети нет. */
    public NetLink link() {
        if (st == St.OFF || t.getLevel() == null) return new NetLink();
        if (VirusEffects.netDown(this)) {
            NetLink down = new NetLink();
            down.status = "Сеть перехвачена вирусом";
            return down;
        }
        long now = t.getLevel().getGameTime();
        if (link == null || now - linkAt >= 20L || now < linkAt) {
            link = Net.resolve(t, root());
            linkAt = now;
        }
        return link;
    }

    /** Сбросить кэш подключения (после смены настроек Wi-Fi). */
    public void relink() {
        link = null;
        t.setChanged();
    }

    // ===================== питание =====================
    public void power() {
        if (st == St.OFF) startUp();
        else shutDown(null);
    }

    public void reset() {
        if (st != St.OFF) startUp();
    }

    void startUp() {
        avAlertShown = false;
        fxSeen = 0;
        fxNetDown = false;
        fxToastUntil = 0;
        fxGlitchAt = 0;
        chat.logout();
        link = null;
        List<String> err = t.errors();
        s.on = true;
        s.noSignal = false;
        s.gpuTier = t.gpuTier();
        s.cursor = false;
        s.led = "";
        s.color = 7;
        s.clear();
        editPath = null;
        desk = null;
        if (!err.isEmpty()) {
            st = St.HALTED;
            s.noSignal = true;
            s.led = err.get(0);
            s.dirty = true;
            return;
        }
        st = St.POST;
        step = 0;
        timer = 0;
    }

    void shutDown(String reason) {
        chat.logout();
        link = null;
        st = St.OFF;
        s.on = false;
        s.noSignal = false;
        s.cursor = false;
        s.color = 7;
        s.clear();
        s.led = reason == null ? "" : reason;
        editPath = null;
        s.dirty = true;
    }

    void updateThermal() {
        double target = 25;
        PartSpec cpu = t.spec(TileComputer.CPU);
        if (st != St.OFF && cpu != null) {
            PartSpec cool = t.spec(TileComputer.COOLER);
            int cooling = cool != null ? cool.cooling : 10;
            double load = (st == St.POST || st == St.BOOT || st == St.INSTALL) ? 1.0
                    : (st == St.DESKTOP && isPixel()) ? 0.85 : 0.5;
            if (st == St.SHELL || st == St.DESKTOP) load *= VirusEffects.heat(this);
            target = 25 + cpu.watts * 45.0 / cooling * load;
        }
        t.temp += (target - t.temp) * 0.05;
        if (st != St.OFF && t.temp >= 100) {
            shutDown("Thermal shutdown: CPU hit 100 C. Use a better cooler.");
        }
    }

    /** Обновляет состояние для светодиода на корпусе (передаётся клиенту вместе с экраном). */
    private void updateStage() {
        int g;
        switch (st) {
            case OFF: g = 0; break;
            case POST: case BIOS: case BOOT: case CLEAN: case INSTALL: g = 1; break;
            case LOGIN: case SHELL: case DESKTOP: g = 2; break;
            default: g = 3; break; // NOBOOT, HALTED
        }
        if (g != s.stage) {
            s.stage = g;
            s.dirty = true;
        }
    }

    public void tick() {
        updateStage();
        if (++thermal >= 10) {
            thermal = 0;
            updateThermal();
        }
        if (st == St.OFF) return;
        if ((st == St.LOGIN || st == St.SHELL || st == St.DESKTOP) && root() == null) {
            shutDown("Drive lost.");
            return;
        }
        switch (st) {
            case POST: tickPost(); break;
            case BOOT: tickBoot(); break;
            case CLEAN: tickClean(); break;
            case PANIC:
                if (++timer >= 60) startUp();
                break;
            case INSTALL: tickInstall(); break;
            case BIOS:
                if (tab == 2 && ++timer % 10 == 0) drawBios();
                break;
            case LOGIN:
                if (lock > 0 && --lock == 0) promptLogin();
                break;
            case DESKTOP:
                if (desk != null) {
                    desk.fast();
                    if (++timer % 10 == 0) desk.tick();
                }
                break;
            default:
                break;
        }
        if ((st == St.SHELL || st == St.DESKTOP) && chat.nick != null && ++chatTimer >= 10) {
            chatTimer = 0;
            chat.poll();
        }
        if (st == St.SHELL) {
            VirusGeneratorCommand.tick(this);
        }
        if (st == St.SHELL || st == St.DESKTOP) {
            VirusEffects.tick(this);
        }
        if (st == St.SHELL || st == St.DESKTOP) {
            if (++avTimer >= 2) {
                avTimer = 0;
                Antivirus.tick(this);
            }
            if (++mailTimer >= 40) {
                mailTimer = 0;
                VirusGeneratorCommand.pollMail(this);
            }
            flushNotices();
        }
        boolean cur = st == St.SHELL || (st == St.DESKTOP && desk != null && desk.caret) || (st == St.LOGIN && lock == 0) || (st == St.INSTALL && step >= 3 && step <= 5);
        if (cur != s.cursor) {
            s.cursor = cur;
            s.dirty = true;
        }
    }

    // ===================== клавиатура =====================
    public void onKey(char c, int key) {
        if ((st == St.SHELL || st == St.DESKTOP) && VirusEffects.dropKey(this, c)) return; // лаги от вируса
        switch (st) {
            case POST:
                if (key == K_DEL || key == K_F2) enterBios();
                break;
            case NOBOOT:
                if (key == K_DEL || key == K_F2) enterBios();
                else attemptBoot();
                break;
            case BIOS: biosKey(key); break;
            case INSTALL: installKey(c, key); break;
            case LOGIN: loginKey(c, key); break;
            case SHELL: shellKey(c, key); break;
            case DESKTOP:
                if (desk != null) desk.key(c, key);
                break;
            default: break;
        }
    }

    /** Клик мышью по клетке экрана (cx, cy). */
    public void onClick(int cx, int cy) {
        if (st == St.DESKTOP && desk != null) desk.click(cx, cy);
    }

    /** Курсор мыши над клеткой экрана (cx, cy); cx == 0xFFFF - курсор вне экрана. */
    public void onHover(int cx, int cy) {
        if (st == St.DESKTOP && desk != null) desk.hover(cx == 0xFFFF ? -1 : cx, cy);
    }

    void beginLine(boolean mask) {
        lb.setLength(0);
        lmask = mask;
        lmax = Math.max(1, Screen.W - 1 - s.cx);
    }

    /** true, если нажат Enter (строка в lb). */
    boolean lineKey(char c, int key) {
        if (key == K_ENTER || key == K_NENTER) {
            s.printChar('\n');
            return true;
        }
        if (key == K_BACK) {
            if (lb.length() > 0) {
                lb.setLength(lb.length() - 1);
                s.backspace();
            }
            return false;
        }
        if (c >= 32 && c < 127 && lb.length() < lmax) {
            lb.append(c);
            s.printChar(lmask ? '*' : c);
        }
        return false;
    }

    // ===================== POST =====================
    void tickPost() {
        timer++;
        PartSpec cpu = t.spec(TileComputer.CPU), mb = t.spec(TileComputer.MB), gpu = t.spec(TileComputer.GPU);
        boolean fast = t.fastBoot;
        if (step == 0) {
            s.clear();
            s.color = 15;
            s.println("PCM BIOS v1.0   (C) PCMod Systems");
            s.color = 7;
            s.println("");
            s.println("CPU    : " + cpu.label + " @ " + cpu.ghz + " GHz, " + cpu.cores + " cores");
            s.println("Board  : " + mb.label + " (" + PartSpec.socketName(mb.socket) + ")");
            s.println("Video  : " + gpu.label + ", " + gpu.vram + " MB");
            s.println("");
            memRow = s.cy;
            s.println("Memory : 0 MB");
            step = 1;
            timer = 0;
        } else if (step == 1) {
            int tot = t.totalRamMB();
            int need = fast ? 4 : 12 + Math.min(tot / 512, 40);
            int shown = (int) Math.min((long) tot, (long) tot * timer / need);
            s.text(9, memRow, pad(shown + " MB", 20), 7);
            if (timer >= need) {
                s.text(9, memRow, pad(tot + " MB OK", 20), 10);
                step = 2;
                timer = 0;
            }
        } else if (step == 2) {
            if (timer == 1) {
                s.println("");
                s.println("Detecting drives...");
            }
            if (timer >= (fast ? 2 : 10)) {
                PartSpec sto = t.spec(TileComputer.STORAGE), med = t.spec(TileComputer.MEDIA);
                s.println("  Storage : " + (sto == null ? "none" : sto.label + " (" + sto.storageKB + " KB)"));
                s.println("  Media   : " + (med == null ? "none" : med.label));
                s.println("");
                s.println("Press DEL or F2 to enter SETUP");
                step = 3;
                timer = 0;
            }
        } else if (step == 3) {
            if (timer >= (fast ? 6 : 24)) attemptBoot();
        }
    }

    // ===================== BIOS =====================
    void enterBios() {
        st = St.BIOS;
        tab = 0;
        sel = 0;
        timer = 0;
        tmpOrder = t.bootOrder;
        tmpFast = t.fastBoot;
        drawBios();
    }

    void saveBios() {
        t.bootOrder = tmpOrder;
        t.fastBoot = tmpFast;
        t.setChanged();
    }

    void biosKey(int key) {
        if (key == K_LEFT) { tab = (tab + 3) % 4; sel = 0; }
        else if (key == K_RIGHT) { tab = (tab + 1) % 4; sel = 0; }
        else if (key == K_UP) { sel = Math.max(0, sel - 1); }
        else if (key == K_DOWN) { sel = Math.min(maxSel() - 1, sel + 1); }
        else if (key == K_F10) { saveBios(); startUp(); return; }
        else if (key == K_ENTER || key == K_NENTER) {
            if (tab == 1) {
                if (sel == 0) tmpOrder = 1 - tmpOrder;
                else tmpFast = !tmpFast;
            } else if (tab == 3) {
                if (sel == 0) { saveBios(); startUp(); return; }
                else if (sel == 1) { startUp(); return; }
                else { tmpOrder = 0; tmpFast = false; }
            }
        }
        if (st == St.BIOS) drawBios();
    }

    int maxSel() {
        return tab == 1 ? 2 : tab == 3 ? 3 : 1;
    }

    void drawBios() {
        s.clear();
        s.text(0, 0, pad(" PCM BIOS SETUP UTILITY v1.0", Screen.W), 15 | 16);
        int x = 1;
        for (int i = 0; i < 4; i++) {
            String n = " " + TABS[i] + " ";
            s.text(x, 1, n, i == tab ? (14 | 16) : 7);
            x += n.length() + 1;
        }
        s.text(0, 2, rep('-', Screen.W), 8);
        PartSpec cpu = t.spec(TileComputer.CPU), mb = t.spec(TileComputer.MB), gpu = t.spec(TileComputer.GPU),
                sto = t.spec(TileComputer.STORAGE), med = t.spec(TileComputer.MEDIA),
                cool = t.spec(TileComputer.COOLER), psu = t.spec(TileComputer.PSU);
        int y = 4;
        if (tab == 0) {
            s.text(2, y++, "CPU          : " + lab(cpu) + " (" + PartSpec.socketName(cpu.socket) + ")", 7);
            s.text(2, y++, "Cores/Clock  : " + cpu.cores + " x " + cpu.ghz + " GHz", 7);
            s.text(2, y++, "Motherboard  : " + lab(mb) + " (" + mb.ramSlots + " x " + PartSpec.ramName(mb.ramType) + ")", 7);
            s.text(2, y++, "Memory       : " + t.totalRamMB() + " MB", 7);
            for (int i = TileComputer.RAM0; i <= TileComputer.RAM3; i++) {
                PartSpec r = t.spec(i);
                if (r != null) s.text(4, y++, "DIMM" + (i - TileComputer.RAM0 + 1) + ": " + r.label, 8);
            }
            s.text(2, y++, "Video        : " + lab(gpu) + ", " + gpu.vram + " MB", 7);
            String os = !t.getItem(TileComputer.STORAGE).isEmpty() && VFS.hasOS(t.getItem(TileComputer.STORAGE))
                    ? VFS.osName(root()) + " installed" : "no OS";
            s.text(2, y++, "Storage      : " + lab(sto) + (sto != null ? " [" + os + "]" : ""), 7);
            s.text(2, y++, "Boot media   : " + lab(med), 7);
            s.text(2, y++, "PSU          : " + lab(psu), 7);
            s.text(2, y++, "Network      : " + (t.spec(TileComputer.NET) == null ? "Ethernet only" : "Ethernet + " + lab(t.spec(TileComputer.NET))), 7);
            s.text(2, y++, "Time         : " + clock(), 7);
        } else if (tab == 1) {
            s.text(2, y, (sel == 0 ? "> " : "  ") + "1st boot device : " + (tmpOrder == 0 ? "[Storage drive]" : "[Boot media]"), sel == 0 ? 14 : 7);
            s.text(2, y + 1, (sel == 1 ? "> " : "  ") + "Fast boot       : " + (tmpFast ? "[Enabled]" : "[Disabled]"), sel == 1 ? 14 : 7);
            s.text(2, y + 3, "Enter toggles the selected option.", 8);
            s.text(2, y + 4, "Changes apply after Save & Exit (F10).", 8);
        } else if (tab == 2) {
            int temp = (int) t.temp;
            int mon = t.monitorType();
            s.text(2, y++, "Display         : " + (mon < 0 ? "none" : MonitorType.get(mon).describe()), 7);
            s.text(2, y++, "CPU temperature : " + temp + " C", temp >= 85 ? 12 : 10);
            s.text(2, y++, "CPU thermal limit: 100 C (auto shutdown)", 8);
            s.text(2, y++, "Cooler          : " + lab(cool) + (cool != null ? " (rating " + cool.cooling + ")" : ""), 7);
            s.text(2, y++, "PSU load        : " + t.requiredWatts() + " W / " + psu.watts + " W", 7);
            for (String w : t.warnings()) s.text(2, y++, "! " + w, 14);
        } else {
            s.text(2, y, (sel == 0 ? "> " : "  ") + "Save changes and exit", sel == 0 ? 14 : 7);
            s.text(2, y + 1, (sel == 1 ? "> " : "  ") + "Discard changes and exit", sel == 1 ? 14 : 7);
            s.text(2, y + 2, (sel == 2 ? "> " : "  ") + "Load setup defaults", sel == 2 ? 14 : 7);
        }
        s.text(0, Screen.H - 1, pad(" Arrows: move   Enter: select   F10: save & exit", Screen.W), 7 | 16);
    }

    String clock() {
        long tt = t.getLevel().getDayTime();
        long day = tt / 24000L;
        long h = ((tt % 24000L) / 1000L + 6L) % 24L;
        long m = (tt % 1000L) * 60L / 1000L;
        return "Day " + day + ", " + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    // ===================== выбор загрузочного устройства =====================
    void attemptBoot() {
        ItemStack d = t.getItem(TileComputer.STORAGE);
        boolean os = !d.isEmpty() && VFS.hasOS(d);
        boolean media = t.spec(TileComputer.MEDIA) != null;
        int first = t.bootOrder;
        bootProblems.clear();
        for (int i = 0; i < 2; i++) {
            int dev = i == 0 ? first : 1 - first;
            if (dev == 0 && os) {
                if (isPixel()) {
                    bootProblems.addAll(pixelProblems(false));
                    if (!bootProblems.isEmpty()) continue;
                }
                startBoot();
                return;
            }
            if (dev == 1 && media) { startInstaller(); return; }
        }
        st = St.NOBOOT;
        s.clear();
        s.color = 12;
        s.println("No bootable device found.");
        s.color = 7;
        s.println("");
        if (!bootProblems.isEmpty()) {
            s.color = 14;
            s.println("PixelOS cannot start on this PC. Needs:");
            s.color = 7;
            for (String x : bootProblems) s.println("  - " + x);
            s.println("");
        }
        s.println("Insert a drive with an installed OS or an");
        s.println("installer disk, power off and try again.");
        s.println("");
        s.println("Press any key to retry, DEL/F2 for SETUP.");
    }

    // ===================== загрузка ОС =====================
    void startBoot() {
        PartSpec sto = t.spec(TileComputer.STORAGE), cpu = t.spec(TileComputer.CPU);
        boolean px = isPixel();
        int base = (px ? 150 : 70) / Math.max(1, sto.speed);
        if (cpu.cores < (px ? 8 : 4)) base = base * 13 / 10;
        if (t.fastBoot) base /= 2;
        total = Math.max(8, (int) (base * VirusEffects.bootFactor(this)));
        timer = 0;
        lastPct = -1;
        st = St.BOOT;
        s.clear();
        if (px) {
            s.sprite(1, 1, Sprites.LOGO);
            s.text(6, 1, "PixelOS", 15);
            s.text(6, 2, "bootloader 1.0", 7);
        } else {
            s.color = 15;
            s.println("PCOS bootloader 1.0");
            s.color = 7;
        }
    }

    void tickBoot() {
        timer++;
        int pct = Math.min(100, timer * 100 / total);
        if (pct != lastPct) {
            lastPct = pct;
            boolean px = isPixel();
            String msg = pct < 30 ? (px ? "Loading pixel kernel..." : "Loading kernel...")
                    : pct < 60 ? (px ? "Mounting pixel filesystem..." : "Mounting root filesystem...")
                    : pct < 90 ? (px ? "Starting compositor and icons..." : "Starting services...") : "Starting login...";
            int r0 = px ? 5 : 2;
            s.text(0, r0, pad(msg, Screen.W), 7);
            s.text(0, r0 + 2, bar(pct, 40) + " " + pct + "%  ", 10);
        }
        if (timer >= total) {
            if (Antivirus.cleanPending(root())) startClean();
            else startLogin();
        }
    }

    // ===================== логин =====================
    void startLogin() {
        CompoundTag r = root();
        host = r.getString("host");
        loginStage = 0;
        attempts = 0;
        lock = 0;
        st = St.LOGIN;
        s.color = 7;
        s.clear();
        if (isPixel()) {
            s.sprite(1, 1, Sprites.LOGO);
            s.text(6, 1, "PixelOS 1.0", 15);
            s.text(6, 2, host + " - tty1", 8);
            s.cx = 0;
            s.cy = 5;
        } else {
            s.println("PCOS 1.0 (" + host + ") tty1");
            s.println("");
        }
        promptLogin();
    }

    void promptLogin() {
        s.print(loginStage == 0 ? "login: " : "password: ");
        beginLine(loginStage == 1);
    }

    void loginKey(char c, int key) {
        if (lock > 0) return;
        if (!lineKey(c, key)) return;
        String v = lb.toString();
        if (loginStage == 0) {
            loginUser = v;
            loginStage = 1;
            promptLogin();
            return;
        }
        CompoundTag r = root();
        if (loginUser.equals(r.getString("user")) && VFS.hash(v).equals(r.getString("pass"))) {
            startSession();
        } else {
            s.color = 12;
            s.println("Login incorrect");
            s.color = 7;
            loginStage = 0;
            attempts++;
            if (attempts >= 3) {
                s.println("Too many attempts, wait 5 seconds.");
                attempts = 0;
                lock = 100;
            } else {
                promptLogin();
            }
        }
    }

    // ===================== рабочий стол =====================
    /** null, если ПК тянет рабочий стол; иначе причина. */
    String desktopReason() {
        PartSpec cpu = t.spec(TileComputer.CPU), gpu = t.spec(TileComputer.GPU);
        if (gpu == null || gpu.tier < 2) return "needs a color video card (GT-300 or better)";
        if (t.totalRamMB() < 2048) return "needs at least 2048 MB of RAM";
        if (cpu == null || cpu.cores < 2) return "needs a CPU with 2+ cores";
        return null;
    }

    void startSession() {
        user = loginUser;
        cwd = "/home/" + user;
        bootTime = t.getLevel().getGameTime();
        CompoundTag r = root();
        if (r != null && !r.getBoolean("nodesktop") && (isPixel() || desktopReason() == null)) startDesktop();
        else startShell();
    }

    void startDesktop() {
        desk = new Desktop(this);
        st = St.DESKTOP;
        timer = 0;
        editPath = null;
        desk.draw();
    }

    /** Из рабочего стола в полноэкранный терминал. */
    void enterTerminal() {
        chat.viewing = null;
        st = St.SHELL;
        editPath = null;
        s.color = 7;
        s.clear();
        s.println(osName() + " terminal. Type 'exit' to return to the desktop.");
        prompt();
    }

    // ===================== оболочка =====================
    void startShell() {
        st = St.SHELL;
        user = loginUser;
        cwd = "/home/" + user;
        bootTime = t.getLevel().getGameTime();
        s.println("");
        s.println("Welcome, " + user + "! Type 'help' for commands.");
        prompt();
    }

    void prompt() {
        s.color = 10;
        s.print(user + "@" + host);
        s.color = 7;
        s.print(":");
        s.color = 9;
        s.print(cwd);
        s.color = 7;
        s.print("$ ");
        beginLine(false);
    }

    void shellKey(char c, int key) {
        if (editPath != null) {
            editKey(c, key);
            return;
        }
        if (!lineKey(c, key)) return;
        Shell.exec(this, lb.toString().trim());
        if (st == St.SHELL && editPath == null) prompt();
    }

    void beginEdit(String path, boolean overwrite) {
        editPath = path;
        editOverwrite = overwrite;
        editBuf = new StringBuilder();
        s.println("Editing " + path + (overwrite ? " (overwrite)" : " (append)"));
        s.println("Type lines. '.' alone saves, ':q' cancels.");
        s.print("> ");
        beginLine(false);
    }

    void editKey(char c, int key) {
        if (!lineKey(c, key)) return;
        String v = lb.toString();
        if (v.equals(".")) {
            CompoundTag r = root();
            String old = (!editOverwrite && VFS.isFile(r, editPath)) ? VFS.read(r, editPath) : "";
            int cap = t.spec(TileComputer.STORAGE).storageKB;
            if (VFS.write(r, editPath, old + editBuf, cap)) {
                s.println("Saved.");
            } else {
                s.color = 12;
                s.println("Cannot save (no space or bad path).");
                s.color = 7;
            }
            editPath = null;
            prompt();
        } else if (v.equals(":q")) {
            s.println("Cancelled.");
            editPath = null;
            prompt();
        } else {
            editBuf.append(v).append('\n');
            s.print("> ");
            beginLine(false);
        }
    }

    // ===================== установщик =====================
    void startInstaller() {
        PartSpec med = t.spec(TileComputer.MEDIA);
        instOs = med != null && "pixel".equals(med.os) ? "pixel" : "pcos";
        instProblems.clear();
        if (instOs.equals("pixel")) instProblems.addAll(pixelProblems(true));
        st = St.INSTALL;
        step = instProblems.isEmpty() ? 0 : 8;
        instErr = "";
        drawInstall();
    }

    void drawInstall() {
        s.clear();
        s.color = 7;
        s.text(0, 0, pad(" " + instName() + " Setup 1.0", Screen.W), 15 | 16);
        s.cx = 0;
        s.cy = 2;
        PartSpec sto = t.spec(TileComputer.STORAGE);
        if (instErr.length() > 0 && step >= 3 && step <= 5) {
            s.color = 12;
            s.println(instErr);
            s.color = 7;
            s.println("");
        }
        instErr = "";
        switch (step) {
            case 0:
                if (instOs.equals("pixel")) {
                    s.sprite(1, 2, Sprites.LOGO);
                    s.text(6, 2, "PixelOS 1.0", 15);
                    s.text(6, 3, "Pixel-art desktop for powerful PCs", 8);
                    s.cx = 0;
                    s.cy = 5;
                }
                s.println("Welcome to " + instName() + " setup.");
                s.println("");
                if (instOs.equals("pixel")) {
                    s.color = 10;
                    s.println("System check: OK");
                    s.color = 7;
                    s.println("");
                }
                s.println("This wizard installs the " + instName() + " operating system");
                s.println("onto a drive of this computer. Afterwards the");
                s.println("computer boots straight from that drive.");
                s.println("");
                s.println("Press ENTER to continue.");
                break;
            case 1:
                s.println("Select the target drive:");
                s.println("");
                if (sto == null) {
                    s.color = 12;
                    s.println("  No drive found!");
                    s.color = 7;
                    s.println("");
                    s.println("Power off, install a drive and try again.");
                } else {
                    s.color = 14;
                    s.println("  > " + sto.label + " (" + sto.storageKB + " KB)");
                    s.color = 7;
                    s.println("");
                    s.println("Press ENTER to select.");
                }
                break;
            case 2:
                s.color = 14;
                s.println("WARNING: all data on " + sto.label + " will be erased.");
                s.color = 7;
                s.println("");
                s.println("Continue? (Y/N)");
                break;
            case 3:
                s.println("Computer name (a-z, 0-9, -). Empty = " + defHost() + ":");
                s.print("> ");
                beginLine(false);
                break;
            case 4:
                s.println("Create your user account.");
                s.print("Username: ");
                beginLine(false);
                break;
            case 5:
                s.println("Create your user account.");
                s.print("Password for " + instUser + ": ");
                beginLine(true);
                break;
            case 6:
                s.color = 14;
                s.println("Installing " + instName() + "... do not power off.");
                s.color = 7;
                break;
            case 7:
                s.color = 10;
                s.println("Installation complete!");
                s.color = 7;
                s.println("");
                s.println(instName() + " is now on the drive. Press ENTER to");
                s.println("restart. By default the computer boots from");
                s.println("the drive first (change it in BIOS > Boot).");
                break;
            case 8:
                s.color = 12;
                s.println("This PC does not meet PixelOS requirements:");
                s.color = 7;
                s.println("");
                for (String x : instProblems) s.println("  - " + x);
                s.println("");
                s.println("Upgrade the hardware, then try again.");
                s.println("Press ENTER to restart.");
                break;
            default:
                break;
        }
    }

    String defHost() {
        return instOs.equals("pixel") ? "pixel" : "pcos";
    }

    void installKey(char c, int key) {
        boolean enter = key == K_ENTER || key == K_NENTER;
        if (step == 0) {
            if (enter) { step = 1; drawInstall(); }
        } else if (step == 1) {
            if (enter && t.spec(TileComputer.STORAGE) != null) { step = 2; drawInstall(); }
        } else if (step == 2) {
            if (c == 'y' || c == 'Y') { step = 3; drawInstall(); }
            else if (c == 'n' || c == 'N') startUp();
        } else if (step >= 3 && step <= 5) {
            if (!lineKey(c, key)) return;
            String v = lb.toString().trim();
            if (step == 3) {
                v = v.toLowerCase();
                if (v.length() == 0) v = defHost();
                if (!v.matches("[a-z0-9-]{1,16}")) {
                    instErr = "Invalid name (a-z, 0-9, -, max 16).";
                } else {
                    instHost = v;
                    step = 4;
                }
            } else if (step == 4) {
                v = v.toLowerCase();
                if (!v.matches("[a-z][a-z0-9_]{0,15}")) {
                    instErr = "Invalid user name (a-z, 0-9, _, max 16).";
                } else {
                    instUser = v;
                    step = 5;
                }
            } else {
                String pw = lb.toString();
                if (pw.length() == 0) {
                    instErr = "Password must not be empty.";
                } else {
                    instPass = pw;
                    PartSpec sto = t.spec(TileComputer.STORAGE), cpu = t.spec(TileComputer.CPU);
                    boolean px = instOs.equals("pixel");
                    int base = (px ? 900 : 400) / Math.max(1, sto.speed);
                    if (cpu.cores < (px ? 8 : 4)) base = base * 13 / 10;
                    total = Math.max(40, base);
                    timer = 0;
                    lastPct = -1;
                    step = 6;
                }
            }
            drawInstall();
        } else if (step == 7 || step == 8) {
            if (enter) startUp();
        }
    }

    void tickInstall() {
        if (step != 6) return;
        timer++;
        int pct = Math.min(100, timer * 100 / total);
        if (pct != lastPct) {
            lastPct = pct;
            s.text(0, 4, pad("Formatting drive ........... " + (pct >= 10 ? "done" : "..."), Screen.W), 7);
            s.text(0, 5, pad("Creating filesystem ........ " + (pct >= 25 ? "done" : pct >= 10 ? "..." : ""), Screen.W), 7);
            s.text(0, 6, pad("Copying system files ....... " + (pct >= 90 ? "done" : pct >= 25 ? pct + "%" : ""), Screen.W), 7);
            s.text(0, 7, pad("Configuring user ........... " + (pct >= 100 ? "done" : ""), Screen.W), 7);
            s.text(0, 9, bar(pct, 40) + " " + pct + "%  ", 10);
        }
        if (timer >= total) {
            ItemStack d = t.getItem(TileComputer.STORAGE);
            VFS.format(d);
            VFS.install(d, instUser, instPass, instHost, instOs);
            instPass = "";
            t.setChanged();
            step = 7;
            drawInstall();
        }
    }

    // ===================== утилиты =====================
    static String lab(PartSpec p) {
        return p == null ? "none" : p.label;
    }

    static String pad(String x, int n) {
        StringBuilder b = new StringBuilder(x);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static String rep(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    static String bar(int pct, int w) {
        int f = pct * w / 100;
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < w; i++) b.append(i < f ? '=' : ' ');
        return b.append(']').toString();
    }
}
