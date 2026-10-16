package ru.rulhot.rBoersX.util.logger;

import lombok.experimental.UtilityClass;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.util.logger.impl.LegacyLogger;

@UtilityClass
public class Logger {

    private volatile ILogger delegate;

    public void setup(@NotNull JavaPlugin plugin) {
        delegate = new LegacyLogger(plugin.getComponentLogger());
    }

    public void info(@NotNull String message) {
        delegate.info(message);
    }

    public void warn(@NotNull String message) {
        delegate.warn(message);
    }

    public void error(@NotNull String message) {
        delegate.error(message);
    }

    public void error(@NotNull String message, @NotNull Throwable throwable) {
        delegate.error(message, throwable);
    }
}
