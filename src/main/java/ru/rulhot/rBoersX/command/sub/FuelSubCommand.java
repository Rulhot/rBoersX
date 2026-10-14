package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.Boer;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class FuelSubCommand implements SubCommand {

    private static final int MAX_MINUTES = 10_080;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;

    @Override
    public @NotNull String getName() {
        return "fuel";
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
            config.send(sender, "admin.fuel.usage");
            return true;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            minutes = 0;
        }
        if (minutes <= 0 || minutes > MAX_MINUTES) {
            config.send(sender, "invalid-number", Map.of("value", args[1], "max", MAX_MINUTES));
            return true;
        }
        List<Boer> owned = ListSubCommand.owned(boers, args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return true;
        }
        for (Boer boer : owned) {
            boer.addFuel(minutes * MILLIS_PER_MINUTE);
            boers.resume(boer);
        }
        config.send(sender, "admin.fuel.done", Map.of("player", owned.getFirst().ownerName(),
                "count", owned.size(), "time", config.duration(minutes * MILLIS_PER_MINUTE)));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> SubCommand.suggest(ListSubCommand.owners(boers), args);
            case 2 -> List.of("30", "60", "120");
            default -> List.of();
        };
    }
}
