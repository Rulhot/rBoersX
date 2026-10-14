package ru.rulhot.rBoersX.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.service.BoerItemService;

import java.util.List;

@RequiredArgsConstructor
public final class BoerProtectionListener implements Listener {

    private final @NotNull BoerManager boers;
    private final @NotNull BoerItemService items;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        if (boers.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(@NotNull BlockBurnEvent event) {
        if (boers.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(@NotNull BlockFadeEvent event) {
        if (boers.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        if (containsBoer(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        if (containsBoer(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(@NotNull InventoryMoveItemEvent event) {
        if (boers.isEmpty()) {
            return;
        }
        if (isBoer(event.getSource()) || isBoer(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(@NotNull PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (items.typeId(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private boolean isBoer(@NotNull Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof Container container) {
            return boers.at(container.getBlock()) != null;
        }
        if (!(holder instanceof DoubleChest chest)) {
            return false;
        }
        return chest.getLeftSide(false) instanceof Container left && boers.at(left.getBlock()) != null
                || chest.getRightSide(false) instanceof Container right && boers.at(right.getBlock()) != null;
    }

    private void protect(@NotNull List<Block> blocks) {
        if (boers.isEmpty()) {
            return;
        }
        blocks.removeIf(block -> boers.at(block) != null);
    }

    private boolean containsBoer(@NotNull List<Block> blocks) {
        if (boers.isEmpty()) {
            return false;
        }
        for (Block block : blocks) {
            if (boers.at(block) != null) {
                return true;
            }
        }
        return false;
    }
}
