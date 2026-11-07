package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Последствия заражения. Вирус, пришедший с другого ПК (Virus.incoming, active), через 5 секунд после приёма
 * начинает вредить именно этому компьютеру; сила зависит от опасности и множителя воздействия вируса.
 * Всё происходит только внутри игры.
 *
 *  FC File Corrupted     - портит текстовые файлы в домашней папке (символы заменяются на мусор)
 *  PD Performance Drain  - часть нажатий клавиш теряется (лаги), ПК греется сильнее, ОС грузится дольше
 *  DS Disk Space         - засоряет диск файлами /tmp/vir_*.tmp, пока почти не кончится место
 *  NH Network Hijack     - сеть периодически пропадает, а когда есть - качает медленнее
 *  SI System Instability - сбои картинки на экране и случайные «kernel panic» с перезагрузкой
 *  ST Stealth            - прячет своё имя в списках и слегка тормозит ПК
 *
 * Лечение: антивирус (удаление вирусов) или 'virgen delete' на заражённом ПК. Испорченные файлы не восстанавливаются.
 */
public final class VirusEffects {
    /** Вредить вирус начинает через 5 с после приёма (антивирус успевает показать окно на 3-й секунде). */
    public static final int ONSET_TICKS = 100;
    private static final String JUNK_DIR = "/tmp", JUNK_PREFIX = "vir_";
    private static final String GARBAGE = "#@%&$*+=~^";
    private static final String GLITCH = "#@%&$*+=~^<>|/\\!;:";

    private VirusEffects() { }

    // ===================== сила эффекта =====================
    /** Сила (0..1) самого опасного действующего вируса данного типа; 0, если такого нет или он ещё не «проснулся». */
    public static double power(TileComputer t, int type) {
        Level w = t.getLevel();
        if (w == null) return 0;
        long now = w.getGameTime();
        double best = 0;
        for (Virus v : t.viruses()) {
            if (!v.incoming || !v.active || v.type != type || now - v.receivedAt < ONSET_TICKS) continue;
            double p = v.severity / 100.0 * Math.min(1.5, 0.6 + v.impactMultiplier / 3.0);
            best = Math.max(best, Math.max(0.15, Math.min(1.0, p)));
        }
        return best;
    }

    /** Есть ли на ПК хоть один действующий вирус (для общих индикаторов). */
    public static boolean infected(TileComputer t) {
        for (int i = 0; i <= 5; i++) {
            if (power(t, i) > 0) return true;
        }
        return false;
    }

    // ===================== «хуки» для остальной системы =====================
    /** Потерять ли это нажатие клавиши (лаги от PD и слабее от ST). */
    public static boolean dropKey(ComputerLogic l, char c) {
        if (c < 32) return false; // Enter/Backspace не теряем: ПК остаётся управляемым
        double p = 0.04 + 0.26 * power(l.t, Virus.TYPE_PERFORMANCE_DRAIN);
        if (power(l.t, Virus.TYPE_PERFORMANCE_DRAIN) == 0) {
            double st = power(l.t, Virus.TYPE_STEALTH);
            if (st == 0) return false;
            p = 0.02 + 0.06 * st;
        }
        return l.t.getLevel().getRandom().nextDouble() < p;
    }

    /** Во сколько раз сильнее нагрев процессора (PD греет заметно, ST чуть-чуть). */
    public static double heat(ComputerLogic l) {
        return 1.0 + 0.5 * power(l.t, Virus.TYPE_PERFORMANCE_DRAIN) + 0.1 * power(l.t, Virus.TYPE_STEALTH);
    }

    /** Во сколько раз дольше грузится ОС. */
    public static double bootFactor(ComputerLogic l) {
        return 1.0 + 0.8 * power(l.t, Virus.TYPE_PERFORMANCE_DRAIN);
    }

    /** Сеть «перехвачена»: циклически отключается на 4-14 секунд из каждых 20. */
    public static boolean netDown(ComputerLogic l) {
        double p = power(l.t, Virus.TYPE_NETWORK_HIJACK);
        if (p == 0) return false;
        int downSec = (int) (4 + 10 * p);
        long sec = l.t.getLevel().getGameTime() / 20L;
        return sec % 20L < downSec;
    }

    /** Множитель скорости загрузок (1.0 без вируса). */
    public static double netSpeed(ComputerLogic l) {
        return 1.0 - 0.6 * power(l.t, Virus.TYPE_NETWORK_HIJACK);
    }

    // ===================== тик =====================
    /** Вызывается каждый тик, пока ПК в терминале или на рабочем столе. */
    public static void tick(ComputerLogic l) {
        Level w = l.t.getLevel();
        if (w == null) return;
        long now = w.getGameTime();
        if (l.fxGlitchAt > 0 && now >= l.fxGlitchAt) {
            l.fxGlitchAt = 0;
            if (l.st == ComputerLogic.St.DESKTOP && l.desk != null) l.desk.draw();
        }
        if (now % 20L != 0L) return; // остальное - раз в секунду
        CompoundTag root = l.root();
        if (root == null) return;
        TileComputer t = l.t;
        Random r = new Random(w.getRandom().nextLong());

        announce(l, Virus.TYPE_FILE_CORRUPTED, "[!] Ошибки чтения: файлы на диске портятся.");
        announce(l, Virus.TYPE_PERFORMANCE_DRAIN, "[!] Система тормозит, процессор перегружен.");
        announce(l, Virus.TYPE_DISK_SPACE, "[!] Диск быстро заполняется неизвестными файлами.");
        announce(l, Virus.TYPE_NETWORK_HIJACK, "[!] Подозрительная сетевая активность.");
        announce(l, Virus.TYPE_SYSTEM_INSTABILITY, "[!] Система работает нестабильно.");

        // --- FC: порча файлов
        double pf = power(t, Virus.TYPE_FILE_CORRUPTED);
        if (pf > 0 && r.nextDouble() < 0.05 + 0.15 * pf) corruptFile(l, root, pf);

        // --- DS: мусор на диске
        double pd = power(t, Virus.TYPE_DISK_SPACE);
        if (pd > 0 && r.nextDouble() < 0.3 + 0.5 * pd) fillDisk(l, root, pd);

        // --- NH: смена состояния сети
        boolean down = netDown(l);
        if (down != l.fxNetDown) {
            l.fxNetDown = down;
            if (power(t, Virus.TYPE_NETWORK_HIJACK) > 0) {
                l.fxNotice(down ? "[!] Сеть: соединение потеряно." : "[!] Сеть: соединение восстановлено.");
                l.relink();
            }
        }

        // --- SI: сбои картинки и kernel panic
        double ps = power(t, Virus.TYPE_SYSTEM_INSTABILITY);
        if (ps > 0) {
            if (r.nextDouble() < 1.0 / (240.0 - 190.0 * ps)) {
                l.panic("повреждение памяти ядра (0x" + Integer.toHexString(0x1000 + r.nextInt(0xEFFF)).toUpperCase() + ")");
                return;
            }
            if (r.nextDouble() < 0.3 + 0.5 * ps) glitchScreen(l, ps);
        }
    }

    /** Одно предупреждение на каждый тип, когда вирус «проснулся». */
    private static void announce(ComputerLogic l, int type, String msg) {
        int bit = 1 << type;
        if ((l.fxSeen & bit) != 0 || power(l.t, type) == 0) return;
        l.fxSeen |= bit;
        l.fxNotice(msg);
    }

    private static void corruptFile(ComputerLogic l, CompoundTag root, double p) {
        String prefix = "/home/" + l.user + "/";
        List<String> cand = new ArrayList<String>();
        for (String f : VFS.files(root)) {
            if (f.startsWith(prefix) && !f.endsWith(".exe")) {
                String c = VFS.read(root, f);
                if (c != null && c.trim().length() > 0) cand.add(f);
            }
        }
        if (cand.isEmpty()) return;
        Random r = new Random(l.t.getLevel().getRandom().nextLong());
        String path = cand.get(r.nextInt(cand.size()));
        StringBuilder b = new StringBuilder(VFS.read(root, path));
        int n = 1 + (int) (p * 5);
        for (int i = 0; i < n; i++) {
            int at = r.nextInt(b.length());
            if (b.charAt(at) != '\n') b.setCharAt(at, GARBAGE.charAt(r.nextInt(GARBAGE.length())));
        }
        PartSpec sto = l.t.spec(TileComputer.STORAGE);
        if (sto != null && VFS.write(root, path, b.toString(), sto.storageKB)) {
            l.t.setChanged();
            l.fxNotice("[!] Файл повреждён: " + VFS.name(path));
        }
    }

    private static void fillDisk(ComputerLogic l, CompoundTag root, double p) {
        PartSpec sto = l.t.spec(TileComputer.STORAGE);
        if (sto == null) return;
        int cap = sto.storageKB;
        int chunk = 512 + (int) (1536 * p);
        // оставляем немного места, чтобы ОС продолжала работать
        if (VFS.free(root, cap) < chunk + 2048) {
            if ((l.fxSeen & (1 << 8)) == 0) {
                l.fxSeen |= 1 << 8;
                l.fxNotice("[!] Диск почти заполнен!");
            }
            return;
        }
        if (!VFS.isDir(root, JUNK_DIR)) VFS.mkdir(root, JUNK_DIR);
        String name = JUNK_DIR + "/" + JUNK_PREFIX + Integer.toHexString(l.t.getLevel().getRandom().nextInt() & 0xFFFFFF) + ".tmp";
        if (VFS.writeSized(root, name, "JUNK\n", chunk, cap)) l.t.setChanged();
    }

    private static void glitchScreen(ComputerLogic l, double p) {
        Screen s = l.s;
        Random r = new Random(l.t.getLevel().getRandom().nextLong());
        int n = 3 + (int) (12 * p);
        for (int i = 0; i < n; i++) {
            int y = r.nextInt(Screen.H - 1);
            if (l.st == ComputerLogic.St.SHELL && y == s.cy) continue; // строку ввода не трогаем
            s.put(r.nextInt(Screen.W), y, GLITCH.charAt(r.nextInt(GLITCH.length())), 1 + r.nextInt(15));
        }
        if (l.st == ComputerLogic.St.DESKTOP) l.fxGlitchAt = l.t.getLevel().getGameTime() + 8;
    }

    /** Антивирус удалил угрозы: убрать и мусорные файлы. */
    public static void cleanup(ComputerLogic l) {
        CompoundTag root = l.root();
        if (root == null) return;
        for (String f : VFS.files(root)) {
            if (f.startsWith(JUNK_DIR + "/" + JUNK_PREFIX)) VFS.remove(root, f);
        }
        if (VFS.isDir(root, JUNK_DIR)) VFS.remove(root, JUNK_DIR); // не удалится, если там лежат чужие файлы
        l.fxSeen = 0;
        l.t.setChanged();
    }
}
