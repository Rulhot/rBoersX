package ru.rulhot.rBoersX.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.manager.HologramManager;
import ru.rulhot.rBoersX.menu.MenuManager;
import ru.rulhot.rBoersX.util.SchedulerUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public final class ReloadService {

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull FuelService fuel;
    private final @NotNull MenuManager menuManager;
    private final @NotNull BorderManager borders;
    private final @NotNull HologramManager holograms;

    private final @NotNull AtomicBoolean reloading = new AtomicBoolean();

    public void reload(@NotNull CommandSender sender) {
        if (!reloading.compareAndSet(false, true)) {
            config.send(sender, "reload.busy");
            return;
        }
        scheduler.runAsync(() -> {
            ConfigManager.Bundle bundle;
            try {
                bundle = config.readAll();
            } catch (RuntimeException exception) {
                reloading.set(false);
                Logger.error("Не удалось прочитать конфиги, оставляю прежние", exception);
                config.send(sender, "reload.failed");
                return;
            }
            scheduler.runGlobal(() -> {
                try {
                    apply(bundle);
                    config.send(sender, "reload.done");
                } catch (RuntimeException exception) {
                    Logger.error("Не удалось применить конфиги", exception);
                    config.send(sender, "reload.failed");
                } finally {
                    reloading.set(false);
                }
            });
        });
    }

    private void apply(@NotNull ConfigManager.Bundle bundle) {
        menuManager.closeAll();
        config.apply(bundle);
        fuel.reload();
        boers.reload();
        menuManager.start();
        borders.start();
        holograms.start();
    }
}
