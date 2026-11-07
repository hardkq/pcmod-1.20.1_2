package ru.pcmod.common;

import net.minecraft.world.level.Level;

/**
 * Заменяет SidedProxy: клиентская реализация - ru.pcmod.client.ClientProxy (этап 6), выбирается в PCMod через DistExecutor.
 * Методы preInit/init(FML-события) убраны: инициализация идёт в конструкторе PCMod и событиях шины.
 */
public class CommonProxy {
    /** Клиентский мир; на сервере null. */
    public Level clientWorld() {
        return null;
    }

    /**
     * Выполнить задачу в главном потоке клиента. В 1.20 пакеты обрабатываются через NetworkEvent.Context#enqueueWork,
     * метод оставлен для совместимости со старым кодом (на сервере не вызывается).
     */
    public void runOnClientThread(Runnable r) {
    }
}
