package ru.rulhot.rBoersX.service;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.util.MessageUtil;

import java.util.Map;

public final class BoerItemService {

    private final @NotNull NamespacedKey typeKey;
    private final @NotNull NamespacedKey fuelKey;
    private final @NotNull ConfigManager config;

    public BoerItemService(@NotNull Plugin plugin, @NotNull ConfigManager config) {
        this.typeKey = new NamespacedKey(plugin, "boer");
        this.fuelKey = new NamespacedKey(plugin, "boer-fuel");
        this.config = config;
    }

    public @NotNull ItemStack create(@NotNull BoerType type, long fuelMillis, int amount) {
        Map<String, Object> placeholders = type.placeholders();
        placeholders.put("fuel", config.duration(fuelMillis));
        placeholders.put("fuel_max", config.duration(type.maxFuelMillis()));
        ItemStack item = type.item().render(MessageUtil.placeholders(placeholders), Map.of());
        item.setAmount(amount);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            if (fuelMillis > 0L) {
                meta.getPersistentDataContainer().set(fuelKey, PersistentDataType.LONG, fuelMillis);
            }
        });
        return item;
    }

    public @Nullable BoerType typeOf(@Nullable ItemStack item) {
        String id = typeId(item);
        return id == null ? null : config.settings().boers().get(id);
    }

    public @Nullable String typeId(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public long fuel(@NotNull ItemStack item) {
        if (item.isEmpty()) {
            return 0L;
        }
        PersistentDataContainerView data = item.getPersistentDataContainer();
        Long fuel = data.get(fuelKey, PersistentDataType.LONG);
        return fuel == null ? 0L : Math.max(0L, fuel);
    }
}
