package ru.rulhot.rBoersX.menu;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.service.FuelService;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.SchedulerUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class MenuManager {

    private static final long PERCENT = 100L;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull FuelService fuel;
    private final @NotNull BorderManager borders;

    private final @NotNull Map<UUID, MenuHolder> viewers = new ConcurrentHashMap<>();
    private @Nullable SchedulerUtil.Task refreshTask;

    public void start() {
        stop();
        boers.all().forEach(this::attachFuel);
        long period = config.settings().menuRefreshTicks();
        refreshTask = scheduler.runGlobalTimer(this::refreshViewers, period, period);
    }

    public void attachFuel(@NotNull Boer boer) {
        MenuConfig menu = config.menu(MenuHolder.View.FUEL);
        MenuHolder holder = new MenuHolder(MenuHolder.View.FUEL, menu, boer, title(menu, boers.type(boer)));
        Inventory inventory = holder.getInventory();
        List<Integer> slots = menu.contentSlots();
        List<ItemStack> items = boer.attachFuel(inventory, slots);
        for (int index = 0; index < Math.min(items.size(), slots.size()); index++) {
            inventory.setItem(slots.get(index), items.get(index));
        }
        Map<Material, Long> burnTimes = config.settings().fuel().items();
        for (ItemStack overflow : items.subList(Math.min(items.size(), slots.size()), items.size())) {
            Long millis = fuel.isFuel(overflow) ? burnTimes.get(overflow.getType()) : null;
            if (millis == null) {
                Logger.warn("Бур на " + boer.pos() + ": " + overflow.getType() + " x" + overflow.getAmount()
                        + " не влез в меню топлива и не горит, удаляю");
                continue;
            }
            boer.addFuel(millis * overflow.getAmount());
        }
        refresh(holder);
    }

    public void openMain(@NotNull Player player, @NotNull Boer boer) {
        open(player, MenuHolder.View.MAIN, boer);
    }

    public void handleClick(@NotNull InventoryClickEvent event, @NotNull MenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Boer boer = holder.getBoer();
        if (boer.isRemoved() || !boers.canAccess(player, boer)) {
            event.setCancelled(true);
            scheduler.runFor(player, () -> player.closeInventory());
            return;
        }
        MenuConfig menu = holder.getConfig();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.size()) {
            clickOwnInventory(player, holder, event);
            return;
        }
        boolean content = menu.contentSlots().contains(slot);
        if (content && holder.getView() == MenuHolder.View.FUEL) {
            scheduler.runFor(player, () -> afterFuelEdit(player, holder));
            return;
        }
        event.setCancelled(true);
        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick() || !holder.acceptClick()) {
            return;
        }
        if (content) {
            clickContent(player, holder, slot, click, event.getCursor());
            return;
        }
        MenuConfig.Button button = menu.buttons().get(slot);
        if (button == null || button.actions().isEmpty()) {
            return;
        }
        Sound sound = config.settings().clickSound();
        if (sound != null) {
            player.playSound(sound);
        }
        for (MenuConfig.Click action : button.actions()) {
            if (action.matches(click.isLeftClick())) {
                run(player, holder, action);
            }
        }
    }

    private void run(@NotNull Player player, @NotNull MenuHolder holder, @NotNull MenuConfig.Click click) {
        Boer boer = holder.getBoer();
        switch (click.action()) {
            case TOGGLE -> toggle(player, holder);
            case FUEL -> switchTo(player, holder, MenuHolder.View.FUEL);
            case BUY_FUEL -> buyFuel(player, holder, click.value());
            case STORAGE -> switchTo(player, holder, MenuHolder.View.STORAGE);
            case BLOCKS -> switchTo(player, holder, MenuHolder.View.BLOCKS);
            case ZONE -> switchZone(player, holder);
            case TOGGLE_OTHER -> {
                boer.toggleMineOther();
                boer.resetCursor();
                refresh(holder);
            }
            case ENABLE_ALL -> setBlocks(holder, true);
            case DISABLE_ALL -> setBlocks(holder, false);
            case PREVIOUS -> changePage(holder, -1);
            case NEXT -> changePage(holder, 1);
            case BACK -> switchTo(player, holder, MenuHolder.View.MAIN);
            case CLOSE -> scheduler.runFor(player, () -> player.closeInventory());
        }
    }

    public void handleDrag(@NotNull InventoryDragEvent event, @NotNull MenuHolder holder) {
        MenuConfig menu = holder.getConfig();
        MenuHolder.View view = holder.getView();
        boolean touchesTop = false;
        boolean onlyContent = true;
        for (int slot : event.getRawSlots()) {
            if (slot < menu.size()) {
                touchesTop = true;
                onlyContent &= menu.contentSlots().contains(slot);
            }
        }
        boolean free = view != MenuHolder.View.MAIN && view != MenuHolder.View.BLOCKS;
        if (!touchesTop && free) {
            return;
        }
        if (view == MenuHolder.View.FUEL && onlyContent && event.getWhoClicked() instanceof Player player) {
            scheduler.runFor(player, () -> afterFuelEdit(player, holder));
            return;
        }
        event.setCancelled(true);
    }

    public void handleClose(@NotNull Player player, @NotNull MenuHolder holder) {
        viewers.remove(player.getUniqueId(), holder);
    }

    public void forget(@NotNull UUID uuid) {
        viewers.remove(uuid);
    }

    public void closeFor(@NotNull Boer boer) {
        viewers.forEach((uuid, holder) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (holder.getBoer() == boer && player != null) {
                scheduler.runFor(player, () -> close(player));
            }
        });
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                close(player);
            } else {
                scheduler.runFor(player, () -> close(player));
            }
        }
    }

    public void shutdown() {
        stop();
        Bukkit.getOnlinePlayers().forEach(this::close);
        viewers.clear();
    }

    private void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void close(@NotNull Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        handleClose(player, holder);
        player.closeInventory();
    }

    private void open(@NotNull Player player, @NotNull MenuHolder.View view, @NotNull Boer boer) {
        if (boer.isRemoved()) {
            return;
        }
        if (!boers.canAccess(player, boer)) {
            config.send(player, "boer.not-owner", Map.of("owner", boer.ownerName()));
            return;
        }
        BoerType type = boers.type(boer);
        if (type == null) {
            config.send(player, "boer.unknown-type");
            return;
        }
        MenuHolder holder = holder(view, boer, type);
        if (holder == null) {
            return;
        }
        refresh(holder);
        viewers.put(player.getUniqueId(), holder);
        if (player.openInventory(holder.getInventory()) == null) {
            viewers.remove(player.getUniqueId(), holder);
        }
    }

    private @Nullable MenuHolder holder(@NotNull MenuHolder.View view, @NotNull Boer boer, @NotNull BoerType type) {
        if (view != MenuHolder.View.FUEL) {
            MenuConfig menu = config.menu(view);
            return new MenuHolder(view, menu, boer, title(menu, type));
        }
        Inventory inventory = boer.fuelInventory();
        return inventory != null && inventory.getHolder(false) instanceof MenuHolder shared ? shared : null;
    }

    private void switchTo(@NotNull Player player, @NotNull MenuHolder from, @NotNull MenuHolder.View view) {
        scheduler.runFor(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) == from) {
                open(player, view, from.getBoer());
            }
        });
    }

    private void refreshViewers() {
        viewers.forEach((uuid, holder) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                viewers.remove(uuid, holder);
                return;
            }
            scheduler.runFor(player, () -> refreshOpen(player, holder));
        });
    }

    private void refreshOpen(@NotNull Player player, @NotNull MenuHolder holder) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) != holder) {
            return;
        }
        if (holder.getBoer().isRemoved()) {
            player.closeInventory();
            return;
        }
        if (holder.getView() != MenuHolder.View.BLOCKS) {
            refresh(holder);
        }
    }

    private void refresh(@NotNull MenuHolder holder) {
        Boer boer = holder.getBoer();
        BoerType type = boers.type(boer);
        if (type == null) {
            return;
        }
        Inventory inventory = holder.getInventory();
        MenuConfig menu = holder.getConfig();
        Settings settings = config.settings();
        int pages = pageCount(holder);
        holder.setPage(Math.clamp(holder.getPage(), 0, pages - 1));
        TagResolver resolver = MessageUtil.placeholders(placeholders(holder, type, pages));
        Map<String, List<Component>> lists = new HashMap<>();
        lists.put("fuel_list", fuelLines(menu, settings.fuel()));

        menu.buttons().forEach((slot, button) -> {
            MenuConfig.State state = button.state();
            ItemTemplate icon = state != null && isActive(state.condition(), boer) ? state.icon() : button.icon();
            inventory.setItem(slot, icon.render(resolver, lists));
        });
        holder.getContent().clear();
        holder.getStacks().clear();
        switch (holder.getView()) {
            case BLOCKS -> renderBlocks(holder, resolver, settings.filters().blocks());
            case STORAGE -> renderStorage(holder);
            case MAIN, FUEL -> {
            }
        }
    }

    private void renderBlocks(@NotNull MenuHolder holder, @NotNull TagResolver resolver,
                              @NotNull List<Material> catalogue) {
        MenuConfig menu = holder.getConfig();
        Boer boer = holder.getBoer();
        Inventory inventory = holder.getInventory();
        List<Integer> slots = menu.contentSlots();
        int offset = holder.getPage() * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (offset + index >= catalogue.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Material material = catalogue.get(offset + index);
            boolean enabled = boer.isBlockEnabled(material);
            ItemStack icon = icon(menu, material, TagResolver.resolver(resolver, MessageUtil.placeholders(Map.of(
                    "item_name", Component.translatable(material.translationKey()),
                    "state", state(menu, enabled)))));
            if (menu.glowWhenEnabled() && enabled) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            inventory.setItem(slot, icon);
            holder.getContent().put(slot, material);
        }
    }

    private void renderStorage(@NotNull MenuHolder holder) {
        Inventory inventory = holder.getInventory();
        List<Integer> slots = holder.getConfig().contentSlots();
        List<ItemStack> stacks = holder.getBoer().storagePage(holder.getPage() * slots.size(), slots.size());
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (index >= stacks.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            ItemStack stack = stacks.get(index);
            inventory.setItem(slot, stack);
            holder.getStacks().put(slot, stack);
        }
    }

    private static @NotNull ItemStack icon(@NotNull MenuConfig menu, @NotNull Material material,
                                           @NotNull TagResolver resolver) {
        ItemStack icon = new ItemStack(material.isItem() ? material : Material.BARRIER);
        ItemTemplate template = menu.contentItem();
        return template == null ? icon : template.apply(icon, resolver, Map.of());
    }

    private @NotNull Map<String, Object> placeholders(@NotNull MenuHolder holder, @NotNull BoerType type,
                                                       int pages) {
        Boer boer = holder.getBoer();
        MenuConfig menu = holder.getConfig();
        Settings.Money money = config.settings().fuel().money();
        Boer.Pos pos = boer.pos();
        long cursor = Math.min(boer.cursor(), type.totalBlocks());
        Map<String, Object> values = type.placeholders();
        values.put("owner", boer.ownerName());
        values.put("status", config.message("status." + boer.status().id()));
        values.put("fuel", config.duration(fuel.total(boer)));
        values.put("fuel_max", config.duration(type.maxFuelMillis()));
        values.put("fuel_price", MessageUtil.number(money.pricePerMinute()));
        values.put("x", pos.x());
        values.put("y", pos.y());
        values.put("z", pos.z());
        values.put("progress", cursor * PERCENT / type.totalBlocks());
        values.put("mined", MessageUtil.number(boer.mined()));
        values.put("stored", MessageUtil.number(boer.stored()));
        values.put("storage_limit", type.storageLimit() > 0L
                ? Component.text(MessageUtil.number(type.storageLimit()))
                : config.message("storage.unlimited"));
        values.put("other_state", state(menu, boer.mineOther()));
        values.put("zone", MessageUtil.parseItemText(boer.forwardZone()
                ? menu.format("zone-forward")
                : menu.format("zone-center")));
        values.put("page", holder.getPage() + 1);
        values.put("pages", pages);
        return values;
    }

    private @NotNull List<Component> fuelLines(@NotNull MenuConfig menu, @NotNull Settings.Fuel settings) {
        List<Component> lines = new ArrayList<>();
        String format = menu.format("fuel-line");
        settings.items().forEach((material, millis) -> lines.add(MessageUtil.parseItemText(format,
                MessageUtil.placeholders(Map.of(
                        "item", Component.translatable(material.translationKey()),
                        "time", config.duration(millis))))));
        if (lines.isEmpty()) {
            lines.add(MessageUtil.parseItemText(menu.format("fuel-none")));
        }
        return List.copyOf(lines);
    }

    private static @NotNull Component title(@NotNull MenuConfig menu, @Nullable BoerType type) {
        return MessageUtil.parseText(menu.title(),
                MessageUtil.placeholders(type == null ? Map.of() : type.placeholders()));
    }

    private int pageCount(@NotNull MenuHolder holder) {
        int perPage = holder.getConfig().contentSlots().size();
        int size = switch (holder.getView()) {
            case BLOCKS -> config.settings().filters().blocks().size();
            case STORAGE -> holder.getBoer().storageStackCount();
            case MAIN, FUEL -> 0;
        };
        return perPage == 0 || size == 0 ? 1 : (size + perPage - 1) / perPage;
    }

    private static boolean isActive(@NotNull MenuConfig.Condition condition, @NotNull Boer boer) {
        return switch (condition) {
            case RUNNING -> boer.isRunning();
        };
    }

    private static @NotNull Component state(@NotNull MenuConfig menu, boolean enabled) {
        return MessageUtil.parseItemText(enabled
                ? menu.format("state-on")
                : menu.format("state-off"));
    }

    private void clickOwnInventory(@NotNull Player player, @NotNull MenuHolder holder,
                                   @NotNull InventoryClickEvent event) {
        MenuHolder.View view = holder.getView();
        boolean free = view != MenuHolder.View.MAIN && view != MenuHolder.View.BLOCKS;
        boolean collect = event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        if (free && !event.isShiftClick() && (!collect || view == MenuHolder.View.FUEL)) {
            if (view == MenuHolder.View.FUEL) {
                scheduler.runFor(player, () -> afterFuelEdit(player, holder));
            }
            return;
        }
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.isEmpty() || !event.isShiftClick()
                || event.getClickedInventory() != player.getInventory()) {
            return;
        }
        if (view == MenuHolder.View.FUEL) {
            event.setCurrentItem(moveFuel(player, holder, item));
            afterFuelEdit(player, holder);
        }
    }

    private void clickContent(@NotNull Player player, @NotNull MenuHolder holder, int slot,
                              @NotNull ClickType click, @Nullable ItemStack cursor) {
        Boer boer = holder.getBoer();
        boolean holding = cursor != null && !cursor.isEmpty();
        switch (holder.getView()) {
            case BLOCKS -> {
                Material material = holder.getContent().get(slot);
                if (material != null) {
                    boer.toggleBlock(material);
                    boer.resetCursor();
                    refresh(holder);
                }
            }
            case STORAGE -> {
                ItemStack shown = holder.getStacks().get(slot);
                if (shown != null && (click.isShiftClick() || !holding)) {
                    takeStack(player, holder, shown, click);
                }
            }
            case MAIN, FUEL -> {
            }
        }
    }

    private void takeStack(@NotNull Player player, @NotNull MenuHolder holder, @NotNull ItemStack shown,
                           @NotNull ClickType click) {
        Boer boer = holder.getBoer();
        ItemStack key = shown.asOne();
        boolean half = click.isRightClick() && !click.isShiftClick();
        int taken = boer.take(key, half ? (shown.getAmount() + 1) / 2 : shown.getAmount());
        if (taken > 0 && click.isShiftClick()) {
            int rejected = 0;
            for (ItemStack leftover : player.getInventory().addItem(key.asQuantity(taken)).values()) {
                rejected += leftover.getAmount();
            }
            boer.restore(key, rejected);
            if (rejected > 0) {
                config.send(player, "storage.inventory-full");
            }
        } else if (taken > 0) {
            player.setItemOnCursor(key.asQuantity(taken));
        }
        if (boers.resume(boer)) {
            config.send(player, "boer.resumed");
        }
        refresh(holder);
    }

    private @Nullable ItemStack moveFuel(@NotNull Player player, @NotNull MenuHolder holder, @NotNull ItemStack item) {
        if (!fuel.isFuel(item)) {
            config.send(player, "fuel.not-fuel");
            return item;
        }
        Inventory inventory = holder.getInventory();
        int remaining = item.getAmount();
        int max = item.getMaxStackSize();
        for (int slot : holder.getConfig().contentSlots()) {
            if (remaining <= 0) {
                break;
            }
            ItemStack present = inventory.getItem(slot);
            if (present == null || present.isEmpty()) {
                int moved = Math.min(max, remaining);
                inventory.setItem(slot, item.asQuantity(moved));
                remaining -= moved;
            } else if (present.isSimilar(item) && present.getAmount() < max) {
                int moved = Math.min(max - present.getAmount(), remaining);
                inventory.setItem(slot, present.asQuantity(present.getAmount() + moved));
                remaining -= moved;
            }
        }
        if (remaining == item.getAmount()) {
            config.send(player, "fuel.full");
        }
        return remaining > 0 ? item.asQuantity(remaining) : null;
    }

    private void afterFuelEdit(@NotNull Player player, @NotNull MenuHolder holder) {
        Boer boer = holder.getBoer();
        Inventory inventory = holder.getInventory();
        boolean rejected = false;
        for (int slot : holder.getConfig().contentSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.isEmpty() || fuel.isFuel(item)) {
                continue;
            }
            inventory.setItem(slot, null);
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            rejected = true;
        }
        if (rejected) {
            config.send(player, "fuel.not-fuel");
        }
        boer.markDirty();
        if (boers.resume(boer)) {
            config.send(player, "boer.resumed");
        }
        refresh(holder);
    }

    private void setBlocks(@NotNull MenuHolder holder, boolean enabled) {
        if (holder.getView() == MenuHolder.View.BLOCKS) {
            Boer boer = holder.getBoer();
            boer.setBlocks(config.settings().filters().blocks(), enabled);
            boer.resetCursor();
            refresh(holder);
        }
    }

    private void changePage(@NotNull MenuHolder holder, int delta) {
        int page = holder.getPage() + delta;
        if (page < 0 || page >= pageCount(holder)) {
            return;
        }
        holder.setPage(page);
        refresh(holder);
    }

    private void toggle(@NotNull Player player, @NotNull MenuHolder holder) {
        Boer boer = holder.getBoer();
        if (boer.isRunning()) {
            boers.stop(boer, Boer.Status.STOPPED, false);
            config.send(player, "boer.toggle-off");
            refresh(holder);
            return;
        }
        String key = switch (boers.launch(boer)) {
            case BoerManager.StartResult.Started started -> "boer.toggle-on";
            case BoerManager.StartResult.AlreadyRunning running -> "boer.toggle-on";
            case BoerManager.StartResult.UnknownType unknown -> "boer.unknown-type";
            case BoerManager.StartResult.NoFuel noFuel -> "boer.start-no-fuel";
            case BoerManager.StartResult.StorageFull full -> "boer.start-storage-full";
            case BoerManager.StartResult.Finished finished -> "boer.start-finished";
        };
        config.send(player, key);
        refresh(holder);
    }

    private void switchZone(@NotNull Player player, @NotNull MenuHolder holder) {
        Boer boer = holder.getBoer();
        switch (boers.switchZone(boer)) {
            case BoerManager.ZoneResult.Switched switched -> {
                borders.hide(boer);
                borders.update(player);
                config.send(player, switched.forward() ? "boer.zone-forward" : "boer.zone-center");
            }
            case BoerManager.ZoneResult.Overlap overlap -> config.send(player, "boer.overlap");
            case BoerManager.ZoneResult.RegionDenied denied -> config.send(player, "boer.region-denied");
            case BoerManager.ZoneResult.Unavailable unavailable -> config.send(player, "boer.unknown-type");
        }
        refresh(holder);
    }

    private void buyFuel(@NotNull Player player, @NotNull MenuHolder holder, int minutes) {
        Boer boer = holder.getBoer();
        BoerType type = boers.type(boer);
        if (type == null) {
            return;
        }
        fuel.buy(player, boer, type, minutes, result -> {
            switch (result) {
                case FuelService.Result.Bought bought -> config.send(player, "fuel.bought", Map.of(
                        "time", config.duration(bought.millis()),
                        "price", MessageUtil.number(bought.price()),
                        "fuel", config.duration(fuel.total(boer))));
                case FuelService.Result.Full full -> config.send(player, "fuel.bought-full");
                case FuelService.Result.Unavailable unavailable -> config.send(player, "fuel.money-unavailable");
                case FuelService.Result.NotEnoughMoney notEnough -> config.send(player, "fuel.not-enough-money",
                        Map.of("price", MessageUtil.number(notEnough.price())));
                case FuelService.Result.EconomyFailed failed -> config.send(player, "fuel.economy-error");
            }
            if (boers.resume(boer)) {
                config.send(player, "boer.resumed");
            }
            refresh(holder);
        });
    }
}
