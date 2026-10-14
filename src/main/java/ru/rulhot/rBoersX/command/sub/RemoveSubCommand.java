package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
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
import ru.rulhot.rBoersX.util.SchedulerUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public final class RemoveSubCommand implements SubCommand {

    private static final double BLOCK_CENTER = 0.5D;

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull MenuManager menuManager;
    private final @NotNull BorderManager borders;
    private final @NotNull HologramManager holograms;
    private final @NotNull BoerItemService items;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "remove";
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
        if (args.length != 2) {
            config.send(sender, "admin.remove.usage");
            return true;
        }
        List<Boer> owned = ListSubCommand.owned(boers, args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return true;
        }
        int number;
        try {
            number = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            number = 0;
        }
        if (number < 1 || number > owned.size()) {
            config.send(sender, "invalid-number", Map.of("value", args[1], "max", owned.size()));
            return true;
        }
        Boer boer = owned.get(number - 1);
        Boer.Pos pos = boer.pos();
        World world = Bukkit.getWorld(pos.world());
        if (world == null) {
            config.send(sender, "admin.remove.world-unloaded");
            return true;
        }
        scheduler.runAt(world, pos.chunkX(), pos.chunkZ(),
                () -> remove(sender, boer, world.getBlockAt(pos.x(), pos.y(), pos.z())));
        return true;
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

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> SubCommand.suggest(ListSubCommand.owners(boers), args);
            case 2 -> SubCommand.suggest(IntStream.rangeClosed(1, ListSubCommand.owned(boers, args[0]).size())
                    .mapToObj(String::valueOf), args);
            default -> List.of();
        };
    }
}
