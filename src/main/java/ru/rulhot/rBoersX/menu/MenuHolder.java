package ru.rulhot.rBoersX.menu;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.model.Boer;

import java.util.HashMap;
import java.util.Map;

public final class MenuHolder implements InventoryHolder {

    private static final long CLICK_COOLDOWN_MILLIS = 150L;

    @Getter
    private final @NotNull View view;
    @Getter
    private final @NotNull MenuConfig config;
    @Getter
    private final @NotNull Boer boer;
    @Getter
    @Setter
    private int page;
    @Getter
    private final @NotNull Map<Integer, Material> content = new HashMap<>();
    @Getter
    private final @NotNull Map<Integer, ItemStack> stacks = new HashMap<>();
    private final @NotNull Inventory inventory;
    private long lastClick;

    public MenuHolder(@NotNull View view, @NotNull MenuConfig config, @NotNull Boer boer,
                      @NotNull Component title) {
        this.view = view;
        this.config = config;
        this.boer = boer;
        this.inventory = Bukkit.createInventory(this, config.size(), title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public boolean acceptClick() {
        long now = System.currentTimeMillis();
        if (now - lastClick < CLICK_COOLDOWN_MILLIS) {
            return false;
        }
        lastClick = now;
        return true;
    }

    public enum View {
        MAIN,
        FUEL,
        BLOCKS,
        STORAGE
    }
}
