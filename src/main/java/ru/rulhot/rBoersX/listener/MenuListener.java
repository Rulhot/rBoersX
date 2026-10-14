package ru.rulhot.rBoersX.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.menu.MenuHolder;
import ru.rulhot.rBoersX.menu.MenuManager;

@RequiredArgsConstructor
public final class MenuListener implements Listener {

    private final @NotNull MenuManager menuManager;
    private final @NotNull BorderManager borders;

    @EventHandler(ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleClick(event, holder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleDrag(event, holder);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        menuManager.handleClose(player, holder);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        menuManager.forget(event.getPlayer().getUniqueId());
        borders.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(@NotNull PlayerPostRespawnEvent event) {
        borders.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        borders.forget(event.getPlayer().getUniqueId());
    }
}
