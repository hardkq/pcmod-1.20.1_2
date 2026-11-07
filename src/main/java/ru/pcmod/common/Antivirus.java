package ru.pcmod.common;

import net.minecraft.nbt.CompoundTag;

/**
 * Игровой антивирус. Скачивается в браузере (сайт av.pc), ставится из Files и работает на накопителе ПК.
 *
 * Механика: когда на ПК попадает вирус, созданный на другом компьютере (по сети или с дискеты),
 * через 3 секунды антивирус показывает окно «Неизвестная программа была найдена у вас в компьютере».
 * Решения два:
 *  1) удалить программу: ПК перезапускается и после загрузки показывается экран удаления угрозы
 *     (не дольше 11 секунд), затем найденные вирусы стираются из библиотеки ПК;
 *  2) ждать: вирус остаётся, окно больше не повторяется, а значок AV в трее остаётся красным.
 */
public final class Antivirus {
    public static final String DIR = "/programs/antivirus", FILE = DIR + "/av.sys";
    public static final String SETUP_FILE = "antivirus_setup.exe";
    public static final String SETUP_MAGIC = "MZ\nAV-SETUP 1.0\n";
    public static final String CORE_TEXT = "AV-CORE 1.0\n";
    /** Размер установщика и установленной программы на диске (байт). */
    public static final int SETUP_SIZE = 8 * 1024, INSTALLED_SIZE = 6 * 1024;
    /** 3 секунды от появления вируса до окна. */
    public static final int DETECT_TICKS = 60;
    /** Экран удаления: 3 с + 2 с на каждую угрозу, но не дольше 11 с. */
    public static final int CLEAN_MIN_TICKS = 60, CLEAN_MAX_TICKS = 11 * 20;
    /** Ключ в корне ОС: после перезапуска нужно показать экран удаления. */
    private static final String CLEAN_KEY = "av_clean";

    private Antivirus() { }

    public static boolean installed(CompoundTag root) {
        return root != null && VFS.isFile(root, FILE);
    }

    /** Угрозы в библиотеке ПК: includeIgnored = считать и те, для которых выбрали «Ждать». */
    public static int threats(TileComputer t, boolean includeIgnored) {
        int n = 0;
        for (Virus v : t.viruses()) {
            if (v.incoming && (includeIgnored || !v.ignored)) n++;
        }
        return n;
    }

    public static boolean cleanPending(CompoundTag root) {
        return root != null && root.getBoolean(CLEAN_KEY);
    }

    public static int cleanTicks(int threats) {
        return Math.max(CLEAN_MIN_TICKS, Math.min(CLEAN_MAX_TICKS, CLEAN_MIN_TICKS + 40 * threats));
    }

    /** Стереть все входящие вирусы. Возвращает, сколько стёрто. */
    public static int purge(TileComputer t) {
        int n = 0;
        for (int i = t.viruses().size() - 1; i >= 0; i--) {
            if (t.viruses().get(i).incoming) {
                t.removeVirus(i);
                n++;
            }
        }
        return n;
    }

    /** Антивирус только что поставили: уже лежащие вирусы засчитываются как найденные сейчас. */
    public static void onInstalled(ComputerLogic l) {
        long now = l.t.getLevel().getGameTime();
        for (Virus v : l.t.viruses()) {
            if (v.incoming) {
                v.receivedAt = now;
                v.ignored = false;
            }
        }
        l.avAlertShown = false;
        l.t.setChanged();
    }

    /** Вызывается из ComputerLogic каждые пару тиков, пока ПК в терминале или на рабочем столе. */
    public static void tick(ComputerLogic l) {
        if (l.avAlertShown) {
            if (threats(l.t, false) == 0) l.avAlertShown = false;
            return;
        }
        long first = Long.MAX_VALUE;
        for (Virus v : l.t.viruses()) {
            if (v.incoming && !v.ignored && v.receivedAt < first) first = v.receivedAt;
        }
        if (first == Long.MAX_VALUE) return;
        if (!installed(l.root())) return;
        long now = l.t.getLevel().getGameTime();
        if (now - first < DETECT_TICKS) return;
        l.avAlertShown = true;
        l.avRaise();
    }

    /** Решение 1: пометить удаление и перезапустить ПК. */
    public static void actionRemove(ComputerLogic l) {
        CompoundTag r = l.root();
        if (!installed(r)) return;
        r.putBoolean(CLEAN_KEY, true);
        l.t.setChanged();
        l.avAlertShown = false;
        l.startUp();
    }

    /** Решение 2: ждать. Вирусы остаются, окно не повторяется. */
    public static void actionWait(ComputerLogic l) {
        for (Virus v : l.t.viruses()) {
            if (v.incoming) v.ignored = true;
        }
        l.avAlertShown = false;
        l.t.setChanged();
    }

    /** Экран удаления закончился: стереть угрозы и снять метку. */
    public static int finishClean(ComputerLogic l) {
        CompoundTag r = l.root();
        if (r != null) r.remove(CLEAN_KEY);
        int n = purge(l.t);
        VirusEffects.cleanup(l);
        l.avAlertShown = false;
        l.t.setChanged();
        return n;
    }

    /** Команда оболочки: av [status|remove|wait]. */
    public static void shell(ComputerLogic l, String[] a) {
        Screen s = l.s;
        String sub = a.length > 0 ? a[0] : "status";
        int all = threats(l.t, true), fresh = threats(l.t, false);
        if (sub.equals("status")) {
            s.color = 10;
            s.println("Антивирус: защита включена");
            s.color = 7;
            s.println("Найдено угроз: " + all + " (ждут решения: " + fresh + ")");
            if (all > 0) s.println("av remove - удалить (ПК перезапустится), av wait - ждать");
        } else if (sub.equals("remove")) {
            if (all == 0) {
                s.println("Угроз нет.");
            } else {
                s.println("Удаление угроз: ПК перезапускается...");
                actionRemove(l);
            }
        } else if (sub.equals("wait")) {
            if (all == 0) {
                s.println("Угроз нет.");
            } else {
                actionWait(l);
                s.println("Хорошо, ждём. Угрозы остаются на ПК: av remove удалит их.");
            }
        } else {
            s.println("usage: av [status|remove|wait]");
        }
    }
}
