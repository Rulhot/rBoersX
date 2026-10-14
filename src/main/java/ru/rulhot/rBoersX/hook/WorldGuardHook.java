package ru.rulhot.rBoersX.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGuardHook implements ProtectionHook {

    private final @NotNull WorldGuardPlugin plugin = WorldGuardPlugin.inst();
    private final @NotNull RegionContainer regions = WorldGuard.getInstance().getPlatform().getRegionContainer();
    private final @NotNull Map<UUID, LocalPlayer> players = new ConcurrentHashMap<>();

    @Override
    public boolean canBreak(@NotNull UUID owner, @NotNull World world, int x, int y, int z) {
        LocalPlayer player = players.computeIfAbsent(owner,
                uuid -> plugin.wrapOfflinePlayer(Bukkit.getOfflinePlayer(uuid)));
        return regions.createQuery().testBuild(BukkitAdapter.adapt(new Location(world, x, y, z)), player);
    }
}
