package ru.pcmod.common;

/** Модели мониторов (данные для отрисовки экрана). */
public class MonitorType {
    public final String name;
    /** Монохромный: показывает только свой цвет, даже с цветной видеокартой. */
    public final boolean mono;
    public final int monoBright, monoDim, bezel;
    public final boolean scanlines, rgb;
    /** Частота обновления экрана, Гц (частота кадров монитора). */
    public final int hz;

    private MonitorType(String name, boolean mono, int monoBright, int monoDim, int bezel, boolean scanlines, boolean rgb, int hz) {
        this.name = name;
        this.mono = mono;
        this.monoBright = monoBright;
        this.monoDim = monoDim;
        this.bezel = bezel;
        this.scanlines = scanlines;
        this.rgb = rgb;
        this.hz = hz;
    }

    public static final MonitorType[] ALL = {
            new MonitorType("Green Monochrome M9", true, 0x33FF33, 0x1E9E1E, 0xFF58584E, true, false, 50),
            new MonitorType("Amber Monochrome A12", true, 0xFFB000, 0xA87400, 0xFF4E463C, true, false, 60),
            new MonitorType("Color CRT C14", false, 0, 0, 0xFF60605A, true, false, 75),
            new MonitorType("LCD Monitor L19", false, 0, 0, 0xFF1E1E24, false, false, 60),
            new MonitorType("Gaming Monitor G24", false, 0, 0, 0xFF101016, false, true, 144)
    };

    /** Название и частота кадров, например «LCD Monitor L19, 60 Hz». */
    public String describe() {
        return name + ", " + hz + " Hz";
    }

    public static MonitorType get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }
}
