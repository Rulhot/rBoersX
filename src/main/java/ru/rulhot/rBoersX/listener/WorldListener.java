package ru.rulhot.rBoersX.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BoerManager;

@RequiredArgsConstructor
public final class WorldListener implements Listener {

    private final @NotNull BoerManager boers;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(@NotNull WorldLoadEvent event) {
        boers.onWorldLoad(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(@NotNull WorldUnloadEvent event) {
        boers.onWorldUnload(event.getWorld());
    }
}
