package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.manager.HologramManager;
import ru.rulhot.rBoersX.menu.MenuManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.service.BoerItemService;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.SchedulerUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class AdminSubCommand implements SubCommand {

    private static final String LIST = "list";
    private static final String REMOVE = "remove";
    private static final String FUEL = "fuel";
    private static final String STOP = "stop";
    private static final String ALL = "all";
    private static final int MAX_FUEL_MINUTES = 10_080;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final double BLOCK_CENTER = 0.5D;
    private static final List<String> FUEL_SUGGESTIONS = List.of("30", "60", "120");
    private static final ClickCallback.Options TELEPORT_CLICK = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .build();

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull MenuManager menuManager;
    private final @NotNull BorderManager borders;
    private final @NotNull HologramManager holograms;
    private final @NotNull BoerItemService items;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "admin";
    }

    @Override
    public @NotNull String getPermission() {
        return BoerCommand.ADMIN_PERMISSION;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            config.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            config.send(sender, "admin.usage");
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case LIST -> list(sender, rest);
            case REMOVE -> remove(sender, rest);
            case FUEL -> fuel(sender, rest);
            case STOP -> stop(sender, rest);
            default -> config.send(sender, "admin.usage");
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            return SubCommand.suggest(Stream.of(LIST, REMOVE, FUEL, STOP), args);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            Stream<String> owners = owners();
            return SubCommand.suggest(action.equals(STOP) ? Stream.concat(Stream.of(ALL), owners) : owners, args);
        }
        if (args.length != 3) {
            return List.of();
        }
        return switch (action) {
            case REMOVE -> SubCommand.suggest(IntStream.rangeClosed(1, owned(args[1]).size())
                    .mapToObj(String::valueOf), args);
            case FUEL -> SubCommand.suggest(FUEL_SUGGESTIONS.stream(), args);
            default -> List.of();
        };
    }

    private void list(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) {
            config.send(sender, "admin.list.usage");
            return;
        }
        List<Boer> owned = owned(args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return;
        }
        config.send(sender, "admin.list.header", Map.of("player", owned.getFirst().ownerName(),
                "count", owned.size()));
        for (int index = 0; index < owned.size(); index++) {
            Boer boer = owned.get(index);
            Boer.Pos pos = boer.pos();
            World world = Bukkit.getWorld(pos.world());
            BoerType type = boers.type(boer);
            Map<String, Object> values = new HashMap<>();
            values.put("name", type == null ? Component.text(boer.typeId()) : MessageUtil.parseText(type.name()));
            values.put("number", index + 1);
            values.put("world", world == null ? "?" : world.getName());
            values.put("x", pos.x());
            values.put("y", pos.y());
            values.put("z", pos.z());
            values.put("status", config.message("status." + boer.status().id()));
            if (world == null || !(sender instanceof Player)) {
                config.send(sender, "admin.list.line", values);
                continue;
            }
            Location target = new Location(world, pos.x() + BLOCK_CENTER, pos.y() + 1.0D, pos.z() + BLOCK_CENTER);
            sender.sendMessage(config.message("admin.list.line", values)
                    .hoverEvent(HoverEvent.showText(config.message("admin.list.hover")))
                    .clickEvent(ClickEvent.callback(audience -> teleport(audience, target), TELEPORT_CLICK)));
        }
    }

    private void teleport(@NotNull Audience audience, @NotNull Location target) {
        if (audience instanceof Player player && player.hasPermission(getPermission())) {
            player.teleportAsync(target);
        }
    }

    private void remove(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 2) {
            config.send(sender, "admin.remove.usage");
            return;
        }
        List<Boer> owned = owned(args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return;
        }
        int number = parse(args[1]);
        if (number < 1 || number > owned.size()) {
            config.send(sender, "invalid-number", Map.of("value", args[1], "max", owned.size()));
            return;
        }
        Boer boer = owned.get(number - 1);
        Boer.Pos pos = boer.pos();
        World world = Bukkit.getWorld(pos.world());
        if (world == null) {
            config.send(sender, "admin.remove.world-unloaded");
            return;
        }
        scheduler.runAt(world, pos.chunkX(), pos.chunkZ(),
                () -> remove(sender, boer, world.getBlockAt(pos.x(), pos.y(), pos.z())));
    }

    private void remove(@NotNull CommandSender sender, @NotNull Boer boer, @NotNull Block block) {
        if (boer.isRemoved()) {
            return;
        }
        boers.remove(boer);
        menuManager.closeFor(boer);
        borders.hide(boer);
        holograms.remove(boer);
        World world = block.getWorld();
        Location center = block.getLocation().add(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        for (ItemStack stack : boer.storagePage(0, boer.storageStackCount())) {
            boer.take(stack.asOne(), stack.getAmount());
            world.dropItemNaturally(center, stack);
        }
        boer.takeFuelItems().forEach(fuel -> world.dropItemNaturally(center, fuel));
        BoerType type = boers.type(boer);
        if (type != null) {
            world.dropItemNaturally(center, items.create(type, boer.fuelMillis(), 1));
        }
        block.setType(Material.AIR);
        config.send(sender, "admin.remove.done", Map.of("owner", boer.ownerName()));
    }

    private void fuel(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 2) {
            config.send(sender, "admin.fuel.usage");
            return;
        }
        int minutes = parse(args[1]);
        if (minutes <= 0 || minutes > MAX_FUEL_MINUTES) {
            config.send(sender, "invalid-number", Map.of("value", args[1], "max", MAX_FUEL_MINUTES));
            return;
        }
        List<Boer> owned = owned(args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return;
        }
        for (Boer boer : owned) {
            boer.addFuel(minutes * MILLIS_PER_MINUTE);
            boers.resume(boer);
        }
        config.send(sender, "admin.fuel.done", Map.of("player", owned.getFirst().ownerName(),
                "count", owned.size(), "time", config.duration(minutes * MILLIS_PER_MINUTE)));
    }

    private void stop(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) {
            config.send(sender, "admin.stop.usage");
            return;
        }
        boolean all = args[0].equalsIgnoreCase(ALL);
        List<Boer> targets = all ? List.copyOf(boers.all()) : owned(args[0]);
        int stopped = 0;
        for (Boer boer : targets) {
            if (boer.isRunning()) {
                boers.stop(boer, Boer.Status.STOPPED, false);
                stopped++;
            }
        }
        config.send(sender, all ? "admin.stop.all" : "admin.stop.player",
                Map.of("player", args[0], "count", stopped));
    }

    private @NotNull List<Boer> owned(@NotNull String owner) {
        return boers.all().stream()
                .filter(boer -> boer.ownerName().equalsIgnoreCase(owner))
                .sorted(Comparator.comparing((Boer boer) -> boer.pos().world())
                        .thenComparingInt(boer -> boer.pos().x())
                        .thenComparingInt(boer -> boer.pos().y())
                        .thenComparingInt(boer -> boer.pos().z()))
                .toList();
    }

    private @NotNull Stream<String> owners() {
        return boers.all().stream().map(Boer::ownerName).distinct();
    }

    private static int parse(@NotNull String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
