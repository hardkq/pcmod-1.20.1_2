package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Программа для генерации вирусов в системе PCOS/PixelOS.
 * Скорость генерации зависит от производительности компьютера:
 *   CPU: +20% ускорения за каждое ядро
 *   RAM: чем больше памяти — тем быстрее
 * Минимальное время (лучшее железо): 300 тиков = 15 секунд
 * Максимальное время (слабое железо): 2400 тиков = 120 секунд
 */
public class VirusGenerator {

    /** Параметры конфигурации генератора */
    public static class GenerationParams {
        public int targetType;
        public int targetSeverity;
        public int propagationAttempts;
        public double complexityFactor;
        public boolean enableStealth;

        public GenerationParams(int type, int severity) {
            this.targetType = type;
            this.targetSeverity = severity;
            this.propagationAttempts = 1;
            this.complexityFactor = 1.0;
            this.enableStealth = false;
        }
    }

    /** Состояние текущей генерации */
    public static class GenerationState {
        public GenerationParams params;
        public long startTime;
        public long estimatedEndTime;
        public float progress;
        public boolean isComplete;
        public Virus generatedVirus;

        public GenerationState(GenerationParams params) {
            this.params = params;
            this.progress = 0.0f;
            this.isComplete = false;
        }
    }

    /**
     * Рассчитать время генерации в тиках на основе спецификаций компьютера.
     *
     * @param cpuCores  количество ядер CPU
     * @param totalRamMB суммарный объём RAM в МБ (сумма всех планок)
     * @param params    параметры генерации
     * @return время в тиках (20 тиков = 1 секунда)
     */
    public static int calculateGenerationTime(int cpuCores, int totalRamMB, GenerationParams params) {
        int baseTime = 2400;

        // === CPU: каждое ядро ускоряет на 20% ===
        float cpuMul = 1.0f / (1.0f + cpuCores * 0.2f);

        // === RAM: переводим МБ → "условные ГБ" (1024 МБ = 1 ГБ) ===
        float ramGB = totalRamMB / 1024.0f;
        float ramMul = Math.max(0.15f, 1.0f - (ramGB / 50.0f));

        // === Сложность (задаётся пользователем) ===
        float complexMul = (float) params.complexityFactor;

        // === Опасность: более сильный вирус дольше собирается ===
        float severityMul = 1.0f + (params.targetSeverity / 200.0f);

        // === Попытки распространения: +15% за каждую ===
        float propMul = 1.0f + (params.propagationAttempts * 0.15f);

        int result = Math.round(baseTime * cpuMul * ramMul * complexMul * severityMul * propMul);
        return Math.max(300, Math.min(2400, result));
    }

    /** Начать генерацию. */
    public static GenerationState beginGeneration(PartSpec cpu, int totalRamMB,
                                                  GenerationParams params, long currentTick) {
        GenerationState state = new GenerationState(params);
        state.startTime = currentTick;

        int cores = (cpu != null) ? cpu.cores : 1;
        int ticks = calculateGenerationTime(cores, totalRamMB, params);
        state.estimatedEndTime = currentTick + ticks;
        return state;
    }

    /** Обновить прогресс. Возвращает true когда генерация завершена. */
    public static boolean updateGeneration(GenerationState state, long currentTick) {
        if (state.isComplete) return true;

        long elapsed = currentTick - state.startTime;
        long total   = state.estimatedEndTime - state.startTime;
        state.progress = Math.min(1.0f, (float) elapsed / total);

        if (currentTick >= state.estimatedEndTime) {
            state.isComplete = true;
            state.generatedVirus = finalizeVirus(state);
            return true;
        }
        return false;
    }

    private static Virus finalizeVirus(GenerationState state) {
        GenerationParams p = state.params;
        Virus v = new Virus(p.targetType, p.targetSeverity, state.startTime);
        v.propagationRate = Math.min(100, p.propagationAttempts * 10);
        if (p.enableStealth) {
            v.signature = "HIDDEN:" + v.signature;
            v.severity  = Math.max(0, v.severity - 10);
        }
        v.impactMultiplier *= p.complexityFactor;
        return v;
    }

    /** Проверить валидность параметров. */
    public static boolean validateParams(GenerationParams params) {
        if (params.targetType < 0 || params.targetType > 5)           return false;
        if (params.targetSeverity < 0 || params.targetSeverity > 100) return false;
        if (params.propagationAttempts < 1 || params.propagationAttempts > 10) return false;
        if (params.complexityFactor < 0.1 || params.complexityFactor > 2.0)   return false;
        return true;
    }

    /** Описание процесса для вывода в терминал. */
    public static String describeGenerationProcess(PartSpec cpu, int totalRamMB, GenerationParams params) {
        int cores = (cpu != null) ? cpu.cores : 1;
        int ticks = calculateGenerationTime(cores, totalRamMB, params);

        return "=== Virus Generation Process ===\n"
             + "Type: "       + Virus.getTypeName(params.targetType)          + "\n"
             + "Severity: "   + params.targetSeverity                         + "/100\n"
             + "CPU Cores: "  + cores                                         + "\n"
             + "RAM: "        + totalRamMB                                    + " MB\n"
             + "Complexity: " + String.format("%.2f", params.complexityFactor) + "x\n"
             + "Propagation: "+ params.propagationAttempts                    + "\n"
             + "Stealth: "    + (params.enableStealth ? "Yes" : "No")         + "\n"
             + "Est. time: "  + (ticks / 20)                                  + " seconds\n"
             + "================================";
    }
}
