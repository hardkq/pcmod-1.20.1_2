package ru.pcmod.common;

import net.minecraft.nbt.CompoundTag;

/**
 * Представляет вирус в системе PCOS/PixelOS.
 * Вирусы существуют только в контексте игры и могут быть сгенерированы программой VirusGenerator.
 * Каждый вирус имеет уникальный идентификатор, тип, уровень опасности и воздействие на систему.
 */
public class Virus {
    
    /** Типы вирусов */
    public static final int TYPE_FILE_CORRUPTED = 0;      // Повреждает файлы на диске
    public static final int TYPE_PERFORMANCE_DRAIN = 1;   // Замедляет систему
    public static final int TYPE_DISK_SPACE = 2;          // Занимает место на диске
    public static final int TYPE_NETWORK_HIJACK = 3;      // Перехватывает сетевую активность
    public static final int TYPE_SYSTEM_INSTABILITY = 4;  // Вызывает нестабильность системы
    public static final int TYPE_STEALTH = 5;             // Скрывает свое присутствие
    
    /** Уникальный идентификатор вируса */
    public String virusId;
    /** Тип вируса */
    public int type;
    /** Уровень опасности (0-100) */
    public int severity;
    /** Строка сигнатуры для идентификации */
    public String signature;
    /** Время создания вируса (в тиках) */
    public long createdTime;
    /** Активен ли вирус */
    public boolean active;
    /** Множитель воздействия (0.1 - 5.0) */
    public double impactMultiplier;
    /** Скорость распространения (для сетевых вирусов) */
    public int propagationRate;
    /** Размер вируса в байтах */
    public int sizeBytes;
    /** 9-значный номер компьютера, на котором вирус был создан (0 = неизвестно). */
    public int originId;
    /** Пришёл с другого ПК (сеть, дискета): именно такие вирусы находит антивирус. Выставляется при приёме. */
    public boolean incoming;
    /** Пользователь выбрал в антивирусе «Ждать»: повторного окна для этого вируса не будет. */
    public boolean ignored;
    /** Игровое время (тики) приёма вируса этим ПК: от него идут 3 секунды до окна антивируса. */
    public long receivedAt;
    
    public Virus(int type, int severity, long createdTime) {
        this.type = type;
        this.severity = severity;
        this.createdTime = createdTime;
        this.active = true;
        
        // Генерируем уникальный ID на основе типа, уровня и времени
        this.virusId = generateVirusId(type, severity, createdTime);
        
        // Генерируем сигнатуру вируса (для анализа и отслеживания)
        this.signature = generateSignature(type, severity);
        
        // Рассчитываем параметры на основе типа и уровня
        this.impactMultiplier = 0.5 + (severity / 100.0) * 4.5; // 0.5 - 5.0
        this.propagationRate = severity / 10;
        this.sizeBytes = 512 + (severity * 5); // 512 - ~1 KB для максимальной опасности
    }
    
    /**
     * Генерирует уникальный ID вируса.
     * Процесс отслеживаемый для понимания механики.
     */
    private static String generateVirusId(int type, int severity, long createdTime) {
        // Хеширование параметров вируса для создания уникального ID
        long hash = 0x811c9dc5L; // FNV-1a offset basis
        
        // Обработка типа
        hash ^= type;
        hash = (hash * 0x01000193L) & 0xffffffffL;
        
        // Обработка уровня опасности
        hash ^= severity;
        hash = (hash * 0x01000193L) & 0xffffffffL;
        
        // Обработка времени создания
        hash ^= (createdTime >> 32) & 0xffffffffL;
        hash = (hash * 0x01000193L) & 0xffffffffL;
        hash ^= createdTime & 0xffffffffL;
        hash = (hash * 0x01000193L) & 0xffffffffL;
        
        // Преобразуем в шестнадцатеричную строку
        return String.format("VIR_%06X_%s", hash & 0xffffff, getTypePrefix(type));
    }
    
    /**
     * Генерирует сигнатуру вируса для анализа.
     * Сигнатура - это уникальная байтовая последовательность, позволяющая идентифицировать вирус.
     */
    private static String generateSignature(int type, int severity) {
        StringBuilder sig = new StringBuilder();
        
        // Магическое число (сигнатура начинается с типа-зависимого prefix)
        sig.append(getTypePrefix(type)).append(":");
        
        // Хеш опасности
        int severityHash = 0xDEADBEEF;
        for (int i = 0; i < severity; i++) {
            severityHash = ((severityHash << 5) + severityHash) ^ i;
        }
        sig.append(String.format("%08X", severityHash & 0xffffffff)).append(":");
        
        // Случайный компонент (в реальности зависит от параметров генератора)
        int randomComponent = ((type * 31) + severity * 17) & 0xffff;
        sig.append(String.format("%04X", randomComponent));
        
        return sig.toString();
    }
    
    /** Получить префикс типа вируса */
    public static String getTypePrefix(int type) {
        switch (type) {
            case TYPE_FILE_CORRUPTED: return "FC";
            case TYPE_PERFORMANCE_DRAIN: return "PD";
            case TYPE_DISK_SPACE: return "DS";
            case TYPE_NETWORK_HIJACK: return "NH";
            case TYPE_SYSTEM_INSTABILITY: return "SI";
            case TYPE_STEALTH: return "ST";
            default: return "XX";
        }
    }
    
    /** Получить человекочитаемое имя типа */
    public static String getTypeName(int type) {
        switch (type) {
            case TYPE_FILE_CORRUPTED: return "File Corrupted";
            case TYPE_PERFORMANCE_DRAIN: return "Performance Drain";
            case TYPE_DISK_SPACE: return "Disk Space";
            case TYPE_NETWORK_HIJACK: return "Network Hijack";
            case TYPE_SYSTEM_INSTABILITY: return "System Instability";
            case TYPE_STEALTH: return "Stealth";
            default: return "Unknown";
        }
    }
    
    /** Получить описание вируса */
    public String getDescription() {
        return String.format("Type: %s | Severity: %d | ID: %s",
                getTypeName(type), severity, virusId);
    }
    
    /** Сохранить вирус в NBT (для хранения в системе) */
    public void writeToNBT(CompoundTag tag) {
        tag.putString("id", virusId);
        tag.putInt("type", type);
        tag.putInt("severity", severity);
        tag.putString("sig", signature);
        tag.putLong("time", createdTime);
        tag.putBoolean("active", active);
        tag.putDouble("impact", impactMultiplier);
        tag.putInt("prop_rate", propagationRate);
        tag.putInt("size", sizeBytes);
        tag.putInt("origin", originId);
        tag.putBoolean("incoming", incoming);
        tag.putBoolean("ignored", ignored);
        tag.putLong("recv", receivedAt);
    }

    /** Чистая копия для передачи на другой ПК или дискету (метки антивируса не переносятся). */
    public Virus copy() {
        CompoundTag c = new CompoundTag();
        writeToNBT(c);
        Virus v = readFromNBT(c);
        v.incoming = false;
        v.ignored = false;
        v.receivedAt = 0;
        return v;
    }

    /** Один и тот же вирус (для защиты от дублей при копировании на диск и обратно). */
    public boolean sameAs(Virus o) {
        return o != null && virusId.equals(o.virusId) && signature.equals(o.signature)
                && createdTime == o.createdTime && originId == o.originId;
    }

    /** Короткая строка для списков в терминале. */
    public String shortLine() {
        return virusId + " " + getTypeName(type) + " sev " + severity
                + (originId > 0 ? " from " + originId : "");
    }
    
    /** Загрузить вирус из NBT. Значения ограничиваются допустимыми диапазонами (диск могли подделать). */
    public static Virus readFromNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("id") || !tag.contains("type")) return null;

        int type = Math.max(0, Math.min(5, tag.getInt("type")));
        int severity = Math.max(0, Math.min(100, tag.getInt("severity")));
        long createdTime = tag.getLong("time");

        Virus virus = new Virus(type, severity, createdTime);
        // сохранённые значения важнее пересчитанных: id и сигнатуру нельзя менять при переносе
        String id = tag.getString("id");
        if (id.length() > 0 && id.length() <= 64) virus.virusId = id;
        String sig = tag.getString("sig");
        if (sig.length() > 0 && sig.length() <= 96) virus.signature = sig;
        virus.active = tag.getBoolean("active");
        double impact = tag.getDouble("impact");
        if (impact >= 0.05 && impact <= 10.0) virus.impactMultiplier = impact;
        virus.propagationRate = Math.max(0, Math.min(100, tag.getInt("prop_rate")));
        virus.sizeBytes = Math.max(0, Math.min(1 << 20, tag.getInt("size")));
        int origin = tag.getInt("origin");
        virus.originId = ComputerIds.valid(origin) ? origin : 0;
        virus.incoming = tag.getBoolean("incoming");
        virus.ignored = tag.getBoolean("ignored");
        virus.receivedAt = tag.getLong("recv");

        return virus;
    }
}
