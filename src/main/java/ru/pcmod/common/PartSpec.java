package ru.pcmod.common;

/** Характеристики компонента ПК. */
public class PartSpec {
    public enum Cat { MOTHERBOARD, CPU, COOLER, RAM, GPU, STORAGE, PSU, MEDIA, NET, DATA }

    public final Cat cat;
    public final String label;
    public int tier, socket, ramType, ramSlots, memMB, cores, watts, cooling, storageKB, speed, vram;
    /** Хешрейт видеокарты в МХ/с для майнер-фермы (0 = не майнит). */
    public int hash;
    public double ghz;
    /** Жёсткий диск (HDD): такие накопители можно ставить в отсеки сервера. */
    public boolean hdd;
    /** Для установочных дисков: какую ОС ставит носитель ("pcos" или "pixel"). */
    public String os = "pcos";

    public PartSpec(Cat cat, String label) {
        this.cat = cat;
        this.label = label;
    }

    public PartSpec tier(int v) { tier = v; return this; }
    public PartSpec socket(int v) { socket = v; return this; }
    public PartSpec ramType(int v) { ramType = v; return this; }
    public PartSpec ramSlots(int v) { ramSlots = v; return this; }
    public PartSpec memMB(int v) { memMB = v; return this; }
    public PartSpec cores(int v) { cores = v; return this; }
    public PartSpec watts(int v) { watts = v; return this; }
    public PartSpec cooling(int v) { cooling = v; return this; }
    public PartSpec storageKB(int v) { storageKB = v; return this; }
    public PartSpec speed(int v) { speed = v; return this; }
    public PartSpec vram(int v) { vram = v; return this; }
    public PartSpec hash(int v) { hash = v; return this; }
    public PartSpec ghz(double v) { ghz = v; return this; }
    public PartSpec hdd() { hdd = true; return this; }
    public PartSpec os(String v) { os = v; return this; }

    public static String socketName(int s) { return "SK-" + s; }
    public static String ramName(int t) { return t == 1 ? "DDR3" : t == 2 ? "DDR4" : "DDR5"; }
}
