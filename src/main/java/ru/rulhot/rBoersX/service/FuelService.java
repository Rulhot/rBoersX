package ru.rulhot.rBoersX.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.economy.EconomyProvider;
import ru.rulhot.rBoersX.economy.EconomyRegistry;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.util.logger.Logger;

@RequiredArgsConstructor
public final class FuelService {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final double MONEY_SCALE = 100.0D;

    private final @NotNull Server server;
    private final @NotNull ConfigManager config;
    private volatile @Nullable EconomyProvider economy;

    public void reload() {
        economy = null;
        Settings.Money money = config.settings().fuel().money();
        if (!money.enabled()) {
            return;
        }
        if (provider() == null) {
            Logger.warn("fuel.yml: экономика " + money.provider() + " не найдена, покупка топлива отключена");
        }
    }

    public boolean isFuel(@Nullable ItemStack item) {
        return item != null && !item.isEmpty() && !item.hasItemMeta()
                && config.settings().fuel().items().containsKey(item.getType());
    }

    public boolean hasFuel(@NotNull Boer boer) {
        return total(boer) > 0L;
    }

    public long total(@NotNull Boer boer) {
        long total = boer.fuelMillis();
        Inventory inventory = boer.fuelInventory();
        if (inventory == null) {
            return total;
        }
        for (int slot : boer.fuelSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (isFuel(item)) {
                total += config.settings().fuel().items().get(item.getType()) * item.getAmount();
            }
        }
        return total;
    }

    public boolean burn(@NotNull Boer boer) {
        Inventory inventory = boer.fuelInventory();
        if (inventory == null) {
            return false;
        }
        for (int slot : boer.fuelSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (!isFuel(item)) {
                continue;
            }
            long millis = config.settings().fuel().items().get(item.getType());
            inventory.setItem(slot, item.getAmount() > 1 ? item.asQuantity(item.getAmount() - 1) : null);
            boer.addFuel(millis);
            return true;
        }
        return false;
    }

    public @NotNull Result buy(@NotNull Player player, @NotNull Boer boer, @NotNull BoerType type,
                               int requestedMinutes) {
        Settings.Money money = config.settings().fuel().money();
        EconomyProvider provider = money.enabled() ? provider() : null;
        if (provider == null) {
            return new Result.Unavailable();
        }
        long minutes = Math.min(requestedMinutes, (type.maxFuelMillis() - boer.fuelMillis()) / MILLIS_PER_MINUTE);
        if (minutes <= 0L) {
            return new Result.Full();
        }
        double price = provider.normalize(Math.round(minutes * money.pricePerMinute() * MONEY_SCALE) / MONEY_SCALE);
        if (price > 0.0D && !provider.has(player, price)) {
            return new Result.NotEnoughMoney(price);
        }
        if (price > 0.0D && !provider.withdraw(player, price)) {
            Logger.warn(provider.name() + " не списала " + price + " у " + player.getName());
            return new Result.EconomyFailed();
        }
        long millis = minutes * MILLIS_PER_MINUTE;
        boer.addFuel(millis);
        return new Result.Bought(millis, price);
    }

    private @Nullable EconomyProvider provider() {
        EconomyProvider current = economy;
        if (current != null) {
            return current;
        }
        EconomyProvider created = EconomyRegistry.create(config.settings().fuel().money(), server);
        economy = created;
        return created;
    }

    public sealed interface Result {

        record Bought(long millis, double price) implements Result {
        }

        record Full() implements Result {
        }

        record Unavailable() implements Result {
        }

        record NotEnoughMoney(double price) implements Result {
        }

        record EconomyFailed() implements Result {
        }
    }
}
