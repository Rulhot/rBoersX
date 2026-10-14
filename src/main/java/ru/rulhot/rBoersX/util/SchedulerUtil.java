package ru.rulhot.rBoersX.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.function.Consumer;

@RequiredArgsConstructor
public final class SchedulerUtil {

    private final @NotNull Plugin plugin;

    public @NotNull Task runAsync(@NotNull Runnable task) {
        return new Task(Bukkit.getAsyncScheduler().runNow(plugin, guarded(task)));
    }

    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, guarded(task));
    }

    public @NotNull Task runGlobalTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        return new Task(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, guarded(task),
                ticks(delayTicks), ticks(periodTicks)));
    }

    public void runAt(@NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, guarded(task));
    }

    public @NotNull Task runFor(@NotNull Entity entity, @NotNull Runnable task) {
        return new Task(entity.getScheduler().run(plugin, guarded(task), null));
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static long ticks(long ticks) {
        return Math.max(1L, ticks);
    }

    private static @NotNull Consumer<ScheduledTask> guarded(@NotNull Runnable task) {
        return scheduled -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                Logger.error("Ошибка в задаче", exception);
            }
        };
    }

    public record Task(@Nullable ScheduledTask handle) {

        public void cancel() {
            if (handle != null) {
                handle.cancel();
            }
        }
    }
}
