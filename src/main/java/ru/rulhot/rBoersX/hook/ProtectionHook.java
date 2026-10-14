package ru.rulhot.rBoersX.hook;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface ProtectionHook {

    @NotNull ProtectionHook NONE = (owner, world, x, y, z) -> true;

    boolean canBreak(@NotNull UUID owner, @NotNull World world, int x, int y, int z);
}
