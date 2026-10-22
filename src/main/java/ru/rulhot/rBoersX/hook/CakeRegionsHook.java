package ru.rulhot.rBoersX.hook;

import dev.cakestudio.cakeregionsapi.CakeRegionsAPI;
import dev.cakestudio.cakeregionsapi.api.IRegionsAPI;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class CakeRegionsHook implements ProtectionHook {

    private final @NotNull IRegionsAPI api = CakeRegionsAPI.getApi();

    @Override
    public boolean canBreak(@NotNull UUID owner, @NotNull World world, int x, int y, int z) {
        return api.getRegionAt(new Location(world, x, y, z))
                .map(region -> region.canBuild(owner))
                .orElse(true);
    }
}
