package ru.rulhot.rBoersX.hook;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.model.Settings;

import java.util.List;

public interface HologramHook {

    void show(@NotNull String name, @NotNull Location location, @NotNull List<String> lines,
              @NotNull Settings.HologramStyle style);

    void remove(@NotNull String name);
}
