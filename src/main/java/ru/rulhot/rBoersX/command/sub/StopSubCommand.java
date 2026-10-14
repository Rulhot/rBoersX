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
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class StopSubCommand implements SubCommand {

    private static final String ALL = "all";

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;

    @Override
    public @NotNull String getName() {
        return "stop";
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
        if (args.length != 1) {
            config.send(sender, "admin.stop.usage");
            return true;
        }
        boolean all = args[0].equalsIgnoreCase(ALL);
        List<Boer> targets = all ? List.copyOf(boers.all()) : ListSubCommand.owned(boers, args[0]);
        int stopped = 0;
        for (Boer boer : targets) {
            if (boer.isRunning()) {
                boers.stop(boer, Boer.Status.STOPPED, false);
                stopped++;
            }
        }
        config.send(sender, all ? "admin.stop.all" : "admin.stop.player",
                Map.of("player", args[0], "count", stopped));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length == 1
                ? SubCommand.suggest(Stream.concat(Stream.of(ALL), ListSubCommand.owners(boers)), args)
                : List.of();
    }
}
