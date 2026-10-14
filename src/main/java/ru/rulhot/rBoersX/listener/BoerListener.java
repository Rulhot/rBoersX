package ru.rulhot.rBoersX.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.manager.HologramManager;
import ru.rulhot.rBoersX.menu.MenuManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.service.BoerItemService;

import java.util.Map;

@RequiredArgsConstructor
public final class BoerListener implements Listener {

    private static final double BLOCK_CENTER = 0.5D;

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull MenuManager menuManager;
    private final @NotNull BorderManager borders;
    private final @NotNull HologramManager holograms;
    private final @NotNull BoerItemService items;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (items.typeId(item) == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(BoerManager.PERMISSION_USE)) {
            deny(event, player, "no-permission", Map.of());
            return;
        }

        BoerType type = items.typeOf(item);
        if (type == null) {
            deny(event, player, "boer.unknown-type", Map.of());
            return;
        }

        Block block = event.getBlockPlaced();
        switch (boers.place(player, block, type, items.fuel(item))) {
            case BoerManager.PlaceResult.Placed placed -> {
                if (block.getType() != type.block()) {
                    block.setType(type.block());
                }
                borders.update(player);
                config.send(player, "boer.placed", type.placeholders());
            }
            case BoerManager.PlaceResult.NotLoaded notLoaded -> deny(event, player, "data-not-loaded", Map.of());
            case BoerManager.PlaceResult.WorldDenied denied -> deny(event, player, "boer.world-denied", Map.of());
            case BoerManager.PlaceResult.LimitReached reached ->
                    deny(event, player, "boer.limit", Map.of("limit", reached.limit()));
            case BoerManager.PlaceResult.Overlap overlap -> deny(event, player, "boer.overlap", Map.of());
            case BoerManager.PlaceResult.RegionDenied region -> deny(event, player, "boer.region-denied", Map.of());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceCancelled(@NotNull BlockPlaceEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Boer boer = boers.at(event.getBlockPlaced());
        if (boer == null || items.typeId(event.getItemInHand()) == null) {
            return;
        }
        boers.remove(boer);
        borders.hide(boer);
        holograms.remove(boer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!boers.isLoaded() && config.settings().boerBlocks().contains(block.getType())) {
            event.setCancelled(true);
            config.send(player, "data-not-loaded");
            return;
        }

        Boer boer = boers.at(block);
        if (boer == null) {
            return;
        }
        if (config.settings().pickup() == Settings.Pickup.OWNER && !boers.canAccess(player, boer)) {
            event.setCancelled(true);
            config.send(player, "boer.not-owner", Map.of("owner", boer.ownerName()));
            return;
        }
        if (!boer.isStorageEmpty()) {
            event.setCancelled(true);
            config.send(player, "boer.storage-not-empty");
            return;
        }

        BoerType type = boers.type(boer);
        if (type == null) {
            event.setCancelled(true);
            config.send(player, "boer.unknown-type");
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        boers.remove(boer);
        menuManager.closeFor(boer);
        borders.hide(boer);
        holograms.remove(boer);
        Location center = block.getLocation().add(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        boer.takeFuelItems().forEach(fuel -> block.getWorld().dropItemNaturally(center, fuel));
        block.getWorld().dropItemNaturally(center, items.create(type, boer.fuelMillis(), 1));
        config.send(player, "boer.removed", Map.of("fuel", config.duration(boer.fuelMillis())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Boer boer = boers.at(block);
        if (boer == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }

        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            menuManager.openMain(player, boer);
        }
    }

    private void deny(@NotNull BlockPlaceEvent event, @NotNull Player player, @NotNull String key,
                      @NotNull Map<String, ?> placeholders) {
        event.setCancelled(true);
        config.send(player, key, placeholders);
    }
}
