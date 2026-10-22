package ru.rulhot.rBoersX.hook;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class GriefPreventionHook implements ProtectionHook {

    private final @NotNull GriefPrevention plugin = GriefPrevention.instance;

    @Override
    public boolean canBreak(@NotNull UUID owner, @NotNull World world, int x, int y, int z) {
        Claim claim = plugin.dataStore.getClaimAt(new Location(world, x, y, z), false, null);
        return claim == null || claim.checkPermission(owner, ClaimPermission.Build, null) == null;
    }
}
