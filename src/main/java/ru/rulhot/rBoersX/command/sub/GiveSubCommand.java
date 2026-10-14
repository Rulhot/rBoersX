package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.service.BoerItemService;
import ru.rulhot.rBoersX.util.SchedulerUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public final class GiveSubCommand implements SubCommand {

    private static final int MAX_AMOUNT = 64;

    private final @NotNull ConfigManager config;
    private final @NotNull BoerItemService items;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "give";
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
        if (args.length < 2 || args.length > 3) {
            config.send(sender, "give.usage");
            return true;
        }
        BoerType type = config.settings().boers().get(args[1].toLowerCase(Locale.ROOT));
        if (type == null) {
            config.send(sender, "give.unknown-type", Map.of("id", args[1]));
            return true;
        }
        Integer amount = args.length == 3 ? parse(args[2]) : Integer.valueOf(1);
        if (amount == null) {
            config.send(sender, "invalid-number", Map.of("value", args[2], "max", MAX_AMOUNT));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            config.send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        ItemStack boer = items.create(type, 0L, amount);
        Map<String, Object> placeholders = type.placeholders();
        placeholders.put("player", target.getName());
        placeholders.put("amount", amount);
        scheduler.runFor(target, () -> {
            target.getInventory().addItem(boer).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            config.send(target, "give.target", placeholders);
        });
        if (!sender.equals(target)) {
            config.send(sender, "give.sender", placeholders);
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        switch (args.length) {
            case 1: {
                return SubCommand.suggest(Bukkit.getOnlinePlayers().stream().map(Player::getName), args);
            }
            case 2: {
                return SubCommand.suggest(config.settings().boers().keySet().stream(), args);
            }
            default: {
                return List.of();
            }
        }
    }

    private @Nullable Integer parse(@NotNull String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 && number <= MAX_AMOUNT ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
